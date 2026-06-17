package com.cmt.e2e.framework.db.containers;

import java.time.Duration;
import java.util.Set;

import com.cmt.e2e.framework.db.driver.Drivers.DB;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * MySQL 8.0 Testcontainer (official Oracle-published image).
 *
 * <h2>Image</h2>
 * {@code mysql:8.0.39}
 * - patch version is pinned for E2E reproducibility. The 8.0.x line is GA
 *   and supported through April 2026; future bumps should land here as a
 *   single-line change.
 *
 * <h2>Connection Info</h2>
 * <pre>
 *   Port      : 3306
 *   Database  : main_schema  (lowercase per MySQL Linux default)
 *   Main user : main_user / cmt   (full privileges on main_schema.*)
 *   Root      : root / cmt        (always present; init / DBA tasks)
 * </pre>
 *
 * <p>ref_schema / ref_user are intentionally absent — see SEED_SPEC §1
 * anti-coverage: CMT 's MySQL fetcher does not implement
 * {@code buildGrant} / {@code buildSynonym}, so cross-schema scenarios
 * cannot be exercised end-to-end through this container.
 *
 * <h2>JDBC URL Format</h2>
 * {@code jdbc:mysql://host:port/dbName}
 *
 * <h2>Wait Strategy</h2>
 * The {@code mysql:8.0} entrypoint starts mysqld twice on first boot: once on
 * a local Unix socket (port=0) to apply init scripts under
 * {@code /docker-entrypoint-initdb.d/}, then again on the published TCP port
 * after init completes. Both phases log "ready for connections", so a naive
 * pattern unblocks too early. We instead match on the explicit
 * {@code "ready for connections.*port: 3306"} qualifier, which only the final
 * networked mysqld emits — guaranteeing that the init script has finished and
 * {@code main_user}/{@code ref_user} exist when the test connects.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Empty MySQL (root only, no init script)
 * MySqlContainer mysql = MySqlContainer.withEmptyDb();
 *
 * // main_schema database + main_user pre-created via init script
 * MySqlContainer mysql = MySqlContainer.withMainUser();
 * }</pre>
 *
 * @see <a href="https://hub.docker.com/_/mysql">Docker Hub: mysql official image</a>
 * @see <a href="file:../../../../../../resources/db/mysql/init/00_prepare_database.sql">db/mysql/init/00_prepare_database.sql</a>
 */
public class MySqlContainer implements DatabaseContainer {

    private static final DockerImageName IMAGE =
        DockerImageName.parse("mysql:8.0.39");

    private static final int    MYSQL_PORT    = 3306;
    private static final String ROOT_USER     = "root";
    private static final String ROOT_PASSWORD = "cmt";

    /**
     * Main migration target user. The corresponding database is
     * {@link #MAIN_DATABASE}. Schema role name per
     * {@code tests/e2e/docs/seed/COMMON_SEED_CONTRACT.md} §2.1; MySQL
     * uses the lowercase form because Linux default {@code lower_case_table_names=0}
     * makes identifiers case-sensitive.
     */
    private static final String MAIN_USER     = "main_user";
    private static final String MAIN_PASSWORD = "cmt";
    private static final String MAIN_DATABASE = "main_schema";

    /**
     * Init-script extension point provided by the official mysql image.
     * Any {@code *.sql} mounted here runs as root during first startup,
     * before the entrypoint restarts the server on the published port.
     */
    private static final String INIT_CLASSPATH =
        "db/mysql/init/00_prepare_database.sql";
    private static final String INIT_CONTAINER_PATH =
        "/docker-entrypoint-initdb.d/00_prepare_database.sql";

    private final GenericContainer<?> container;

    private MySqlContainer(boolean withInit) {
        // Wait for the FINAL mysqld instance — the one that listens on TCP
        // port 3306 — to log "ready for connections". The bootstrap instance
        // that runs init scripts uses a Unix socket and logs port=0, so a
        // simple "ready for connections" pattern would race the init script.
        // The "port: 3306" qualifier in this regex matches only after the
        // entrypoint restarts mysqld on the published port with the init
        // script applied (main_user/ref_user/databases all created).
        GenericContainer<?> c = new GenericContainer<>(IMAGE)
            .withExposedPorts(MYSQL_PORT)
            .withEnv("MYSQL_ROOT_PASSWORD", ROOT_PASSWORD)
            // mysqld args: keep binary logging enabled (matches typical production)
            // but trust non-SUPER users to create stored functions. Without this,
            // CREATE FUNCTION as main_user fails with ER_BINLOG_UNSAFE_ROUTINE
            // (error 1419) regardless of DETERMINISTIC / READS SQL DATA hints.
            // The args inherit through both bootstrap and final mysqld phases
            // because the entrypoint uses "$@" verbatim for both spawns.
            .withCommand("mysqld", "--log-bin-trust-function-creators=ON")
            // Three log signals contain "ready for connections" during boot:
            //   1. bootstrap mysqld     -> "mysqld: ready for connections ... port: 0"
            //   2. final X Plugin       -> "X Plugin ready for connections ... port: 33060"
            //   3. final mysqld (TCP)   -> "mysqld: ready for connections ... port: 3306 "
            // Only #3 means the TCP listener is up. Match on the "mysqld:" prefix
            // (excludes #2) and a trailing space after 3306 (excludes the substring
            // match against 33060).
            .waitingFor(Wait.forLogMessage(".*mysqld: ready for connections.*port: 3306 .*", 1))
            .withStartupTimeout(Duration.ofMinutes(3));

        if (withInit) {
            c.withCopyFileToContainer(
                MountableFile.forClasspathResource(INIT_CLASSPATH),
                INIT_CONTAINER_PATH
            );
        }

        this.container = c;
    }

    /**
     * Creates an empty MySQL 8.0 instance with only the {@code root} user
     * (password {@value #ROOT_PASSWORD}). No databases or app users are
     * created automatically — tests that use this factory must bootstrap
     * their own schema and users via JDBC after startup.
     */
    public static MySqlContainer withEmptyDb() {
        return new MySqlContainer(false);
    }

    /**
     * Creates a MySQL 8.0 instance with the {@code main_schema} database and
     * {@code main_user@%} created automatically by
     * {@code db/mysql/init/00_prepare_database.sql} (mounted at
     * {@code /docker-entrypoint-initdb.d/} and executed by the image entrypoint
     * as root during first startup).
     *
     * <h2>Initialization Order</h2>
     * <pre>
     * 1. Container startup: image entrypoint creates root and applies the
     *    init script (creates main_schema database + main_user).
     * 2. MysqlDatabaseInitializer.migrate("mysql/main_schema") — runs as main_user
     * </pre>
     *
     * <p>This is the standard factory for E2E tests. {@code ref_schema} is
     * intentionally absent (SEED_SPEC §1 anti-coverage).
     */
    public static MySqlContainer withMainUser() {
        return new MySqlContainer(true);
    }

    @Override public String  getHost()         { return container.getHost(); }
    @Override public Integer getDatabasePort() { return container.getMappedPort(MYSQL_PORT); }
    @Override public DB      getDbType()       { return DB.MYSQL; }
    @Override public GenericContainer<?> getContainer() { return container; }

    /**
     * Builds a MySQL JDBC URL of the form
     * {@code jdbc:mysql://host:port/dbName}. The {@code user} parameter is
     * accepted for interface symmetry but not embedded in the URL — credentials
     * are passed separately to {@link java.sql.DriverManager#getConnection}.
     */
    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format("jdbc:mysql://%s:%d/%s",
            getHost(), getDatabasePort(), dbName);
    }

    @Override public void start() { container.start(); }
    @Override public void stop()  { container.stop(); }
    @Override public Set<Startable> getDependencies() { return container.getDependencies(); }

    /** Main migration user used by Flyway seeding and tests. */
    public String getMainUser()     { return MAIN_USER; }
    /** Password for {@link #getMainUser()}. */
    public String getMainPassword() { return MAIN_PASSWORD; }
    /** MySQL database owned by {@link #getMainUser()}. */
    public String getMainDatabase() { return MAIN_DATABASE; }

    /** DBA user ({@code root}). */
    public String getRootUser()     { return ROOT_USER; }
    /** DBA password. */
    public String getRootPassword() { return ROOT_PASSWORD; }
}
