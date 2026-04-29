package com.cmt.e2e.framework.db.containers;

import java.time.Duration;

import com.cmt.e2e.framework.db.Drivers.DB;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * SQL Server 2022 Developer Edition Testcontainer (image
 * {@code mcr.microsoft.com/mssql/server:2022-latest}).
 *
 * <p>JDBC URL appends {@code encrypt=false;trustServerCertificate=true}
 * because SQL Server 2022 ships a self-signed cert. Image is amd64-only
 * so on Apple Silicon it runs under emulation (~3-5 min startup).
 */
public class MsSqlContainer implements DatabaseContainer {

    private static final DockerImageName IMAGE =
        DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest");

    private static final int    MSSQL_PORT  = 1433;
    private static final String SA_USER     = "sa";
    private static final String SA_PASSWORD = "CmtSa#2026";
    private static final String DATABASE_NAME = "e2e_db";

    private static final String MAIN_USER     = "main_user";
    private static final String MAIN_PASSWORD = "CmtMain#2026";
    private static final String MAIN_SCHEMA   = "main_schema";
    private static final String REF_USER      = "ref_user";
    private static final String REF_PASSWORD  = "CmtRef#2026";
    private static final String REF_SCHEMA    = "ref_schema";

    private static final String INIT_CLASSPATH      = "db/mssql/init/00_prepare_database.sql";
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

    public static MsSqlContainer withEmptyDb()  { return new MsSqlContainer(false); }
    public static MsSqlContainer withMainUser() { return new MsSqlContainer(true); }

    @Override
    public void start() {
        container.start();
        if (runInit) {
            try {
                // Official MSSQL image has no entrypoint init hook (unlike
                // mysql/mariadb), so we run sqlcmd manually after the server
                // is ready. sqlcmd handles the GO batch separator natively.
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

    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format(
            "jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false;trustServerCertificate=true",
            getHost(), getDatabasePort(), dbName);
    }

    public String getDatabaseName() { return DATABASE_NAME; }
    public String getMainUser()     { return MAIN_USER; }
    public String getMainPassword() { return MAIN_PASSWORD; }
    public String getMainSchema()   { return MAIN_SCHEMA; }
    public String getRefUser()      { return REF_USER; }
    public String getRefPassword()  { return REF_PASSWORD; }
    public String getRefSchema()    { return REF_SCHEMA; }
    public String getSaUser()       { return SA_USER; }
    public String getSaPassword()   { return SA_PASSWORD; }
}
