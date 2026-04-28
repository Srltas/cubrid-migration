package com.cmt.e2e.framework.db.containers;

import java.time.Duration;
import java.util.Set;

import com.cmt.e2e.framework.db.driver.Drivers.DB;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Microsoft SQL Server 2022 Testcontainer (official image).
 *
 * <h2>Image</h2>
 * {@code mcr.microsoft.com/mssql/server:2022-latest}
 * - Developer Edition (free, full feature parity with Enterprise).
 * - <b>Apple Silicon caveat</b>: this image is amd64-only; on ARM Macs
 *   it runs under Rosetta/QEMU emulation and startup typically takes
 *   3-5 minutes. The {@code withStartupTimeout(Duration.ofMinutes(8))}
 *   below covers that.
 *
 * <h2>Connection Info</h2>
 * <pre>
 *   Port      : 1433
 *   Database  : e2e_db (created by init script)
 *   sa        : sa / CmtSa#2026  (server administrator)
 *   Main user : main_user / CmtMain#2026   (owns schema main_schema)
 *   Ref user  : ref_user  / CmtRef#2026    (owns schema ref_schema; only
 *                                            reachable in two-schema mode)
 * </pre>
 *
 * <h2>JDBC URL Format</h2>
 * {@code jdbc:sqlserver://host:port;databaseName=...;encrypt=false;trustServerCertificate=true}
 * - {@code encrypt=false;trustServerCertificate=true} disables TLS
 *   verification because SQL Server 2022 ships a self-signed cert that
 *   Connector/jdbc would otherwise reject.
 *
 * <h2>Init Script Strategy</h2>
 * Unlike MySQL/MariaDB which auto-run scripts under
 * {@code /docker-entrypoint-initdb.d/}, the official MSSQL image has
 * no entrypoint hook. We mount {@code db/mssql/init/00_prepare_database.sql}
 * to {@code /tmp/init.sql} and, after the server is ready, invoke
 * {@code /opt/mssql-tools18/bin/sqlcmd} via {@code exec} to run it.
 * sqlcmd handles the {@code GO} batch separator natively.
 *
 * <h2>Wait Strategy</h2>
 * The first phase boot prints {@code SQL Server is now ready for client
 * connections.} once the listener binds 1433. We wait on that single
 * line and then run the init script.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Empty (only sa, no e2e_db)
 * MsSqlContainer mssql = MsSqlContainer.withEmptyDb();
 *
 * // e2e_db + main_user + ref_user + main_schema + ref_schema applied
 * MsSqlContainer mssql = MsSqlContainer.withMainUser();
 * }</pre>
 *
 * @see <a href="https://hub.docker.com/_/microsoft-mssql-server">Docker Hub: mssql/server</a>
 * @see <a href="file:../../../../../../resources/db/mssql/init/00_prepare_database.sql">db/mssql/init/00_prepare_database.sql</a>
 */
public class MsSqlContainer implements DatabaseContainer {

    private static final DockerImageName IMAGE =
        DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest");

    private static final int    MSSQL_PORT  = 1433;
    private static final String SA_USER     = "sa";
    private static final String SA_PASSWORD = "CmtSa#2026";

    private static final String DATABASE_NAME = "e2e_db";

    /**
     * Main migration target user. Owns schema {@link #MAIN_SCHEMA} in
     * {@link #DATABASE_NAME}. Schema role name per SEED_SPEC §2.
     */
    private static final String MAIN_USER     = "main_user";
    private static final String MAIN_PASSWORD = "CmtMain#2026";
    private static final String MAIN_SCHEMA   = "main_schema";

    /**
     * Cross-schema reference user (multi-schema mode). Owns schema
     * {@link #REF_SCHEMA} in {@link #DATABASE_NAME}.
     */
    private static final String REF_USER     = "ref_user";
    private static final String REF_PASSWORD = "CmtRef#2026";
    private static final String REF_SCHEMA   = "ref_schema";

    private static final String INIT_CLASSPATH =
        "db/mssql/init/00_prepare_database.sql";
    private static final String INIT_CONTAINER_PATH = "/tmp/init.sql";

    private final GenericContainer<?> container;
    private final boolean runInit;

    private MsSqlContainer(boolean withInit) {
        this.runInit = withInit;
        GenericContainer<?> c = new GenericContainer<>(IMAGE)
            .withExposedPorts(MSSQL_PORT)
            .withEnv("ACCEPT_EULA", "Y")
            .withEnv("MSSQL_SA_PASSWORD", SA_PASSWORD)
            .withEnv("MSSQL_PID", "Developer")
            .waitingFor(Wait.forLogMessage(".*SQL Server is now ready for client connections.*", 1))
            .withStartupTimeout(Duration.ofMinutes(8));

        if (withInit) {
            c.withCopyFileToContainer(
                MountableFile.forClasspathResource(INIT_CLASSPATH),
                INIT_CONTAINER_PATH
            );
        }

        this.container = c;
    }

    /**
     * Empty SQL Server instance (sa only). Use when the test needs a raw
     * server and bootstraps everything via JDBC.
     */
    public static MsSqlContainer withEmptyDb() {
        return new MsSqlContainer(false);
    }

    /**
     * SQL Server instance with {@code e2e_db} created and both
     * {@code main_user}/{@code main_schema} and {@code ref_user}/{@code ref_schema}
     * provisioned by {@code db/mssql/init/00_prepare_database.sql} (executed
     * via sqlcmd inside the container after the server reports ready).
     *
     * <h2>Initialization Order</h2>
     * <pre>
     * 1. Container boots; sa account exists.
     * 2. start() waits for "ready for client connections", then runs
     *    sqlcmd -i /tmp/init.sql to create database, logins, users, schemas,
     *    and cross-schema GRANT.
     * 3. MssqlDatabaseInitializer.migrateRef("mssql/ref_schema")  — runs as ref_user
     * 4. MssqlDatabaseInitializer.migrateMain("mssql/main_schema") — runs as main_user
     * </pre>
     */
    public static MsSqlContainer withMainUser() {
        return new MsSqlContainer(true);
    }

    @Override
    public void start() {
        container.start();
        if (runInit) {
            try {
                Container.ExecResult result = container.execInContainer(
                    "/opt/mssql-tools18/bin/sqlcmd",
                    "-S", "localhost",
                    "-U", SA_USER,
                    "-P", SA_PASSWORD,
                    "-C",                       // trust self-signed cert
                    "-b",                       // exit on error
                    "-i", INIT_CONTAINER_PATH
                );
                if (result.getExitCode() != 0) {
                    throw new IllegalStateException(
                        "MSSQL init script failed (exit " + result.getExitCode() + ")\n"
                            + "stdout:\n" + result.getStdout() + "\n"
                            + "stderr:\n" + result.getStderr());
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to run MSSQL init script", e);
            }
        }
    }

    @Override public void stop()  { container.stop(); }
    @Override public String  getHost()         { return container.getHost(); }
    @Override public Integer getDatabasePort() { return container.getMappedPort(MSSQL_PORT); }
    @Override public DB      getDbType()       { return DB.MSSQL; }
    @Override public GenericContainer<?> getContainer() { return container; }

    /**
     * Builds an MSSQL JDBC URL pointing at the named database with TLS
     * verification disabled (self-signed dev cert).
     */
    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format(
            "jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false;trustServerCertificate=true",
            getHost(), getDatabasePort(), dbName);
    }

    @Override public Set<Startable> getDependencies() { return container.getDependencies(); }

    /** Database name created by the init script. */
    public String getDatabaseName() { return DATABASE_NAME; }

    /** Main migration user. */
    public String getMainUser()     { return MAIN_USER; }
    /** Password for {@link #getMainUser()}. */
    public String getMainPassword() { return MAIN_PASSWORD; }
    /** Schema owned by {@link #getMainUser()}. */
    public String getMainSchema()   { return MAIN_SCHEMA; }

    /** Reference-schema user (multi-schema mode). */
    public String getRefUser()     { return REF_USER; }
    /** Password for {@link #getRefUser()}. */
    public String getRefPassword() { return REF_PASSWORD; }
    /** Schema owned by {@link #getRefUser()}. */
    public String getRefSchema()   { return REF_SCHEMA; }

    /** SA (server administrator) user. */
    public String getSaUser()     { return SA_USER; }
    /** SA password. */
    public String getSaPassword() { return SA_PASSWORD; }
}
