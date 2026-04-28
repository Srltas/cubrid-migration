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
 * MariaDB 11 Testcontainer (official image).
 *
 * <h2>Image</h2>
 * {@code mariadb:11.4}
 * - 11.4 is the current stable line; bumps land here as a single-line change.
 *
 * <h2>Connection Info</h2>
 * <pre>
 *   Port      : 3306
 *   Database  : main_schema  (lowercase per MariaDB Linux default)
 *   Main user : main_user / cmt   (full privileges on main_schema.*)
 *   Root      : root / cmt        (always present; init / DBA tasks)
 * </pre>
 *
 * <p>ref_schema / ref_user are intentionally absent — see SEED_SPEC §1
 * anti-coverage: CMT 's MariaDB fetcher does not implement
 * {@code buildGrant} / {@code buildSynonym}.
 *
 * <h2>JDBC URL Format</h2>
 * {@code jdbc:mariadb://host:port/dbName}
 *
 * <h2>Wait Strategy</h2>
 * MariaDB 's startup sequence is similar to MySQL 's: a temporary mariadbd
 * applies {@code /docker-entrypoint-initdb.d/} init scripts on a Unix
 * socket, then the final mariadbd binds to the TCP port. Each phase
 * emits a "mariadbd: ready for connections." log line — the bootstrap
 * phase with port 0, the final phase with port 3306. Unlike MySQL,
 * MariaDB splits "ready for connections" and "Version: ... port: ..."
 * onto separate lines, so a single-line regex cannot pin down the
 * port-3306 instance. MariaDB also has no X Plugin (no port: 33060
 * decoy), so counting the message twice cleanly identifies the final
 * TCP-listening server.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Empty (root only, no init script)
 * MariaDbContainer mariadb = MariaDbContainer.withEmptyDb();
 *
 * // main_schema database + main_user pre-created via init script
 * MariaDbContainer mariadb = MariaDbContainer.withMainUser();
 * }</pre>
 *
 * @see <a href="https://hub.docker.com/_/mariadb">Docker Hub: mariadb official image</a>
 * @see <a href="file:../../../../../../resources/db/mariadb/init/00_prepare_database.sql">db/mariadb/init/00_prepare_database.sql</a>
 */
public class MariaDbContainer implements DatabaseContainer {

    private static final DockerImageName IMAGE =
        DockerImageName.parse("mariadb:11.4");

    private static final int    MARIADB_PORT  = 3306;
    private static final String ROOT_USER     = "root";
    private static final String ROOT_PASSWORD = "cmt";

    /**
     * Main migration target user. Schema role name per
     * {@code tests/e2e/docs/seed/COMMON_SEED_CONTRACT.md} §2.1.
     * MariaDB on Linux defaults to case-sensitive identifiers, so the
     * lowercase form is used throughout.
     */
    private static final String MAIN_USER     = "main_user";
    private static final String MAIN_PASSWORD = "cmt";
    private static final String MAIN_DATABASE = "main_schema";

    private static final String INIT_CLASSPATH =
        "db/mariadb/init/00_prepare_database.sql";
    private static final String INIT_CONTAINER_PATH =
        "/docker-entrypoint-initdb.d/00_prepare_database.sql";

    private final GenericContainer<?> container;

    private MariaDbContainer(boolean withInit) {
        GenericContainer<?> c = new GenericContainer<>(IMAGE)
            .withExposedPorts(MARIADB_PORT)
            .withEnv("MARIADB_ROOT_PASSWORD", ROOT_PASSWORD)
            // Keep binary logging enabled (production-like) but trust non-SUPER
            // users to create stored functions. Without this, V5 routines fail
            // with ER_BINLOG_UNSAFE_ROUTINE on main_user. Inherits across both
            // bootstrap and final mariadbd phases via "$@" in the entrypoint.
            .withCommand("mariadbd", "--log-bin-trust-function-creators=ON")
            // count=2 — see Wait Strategy section in the class javadoc. The
            // first "ready for connections" is the bootstrap server (port 0,
            // socket-only); the second is the final TCP listener on port 3306.
            // MariaDB has no X Plugin, so count=2 is unambiguous.
            .waitingFor(Wait.forLogMessage(".*mariadbd: ready for connections.*", 2))
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
     * Empty MariaDB instance (root only). Use when the test bootstraps
     * users and databases through JDBC after startup.
     */
    public static MariaDbContainer withEmptyDb() {
        return new MariaDbContainer(false);
    }

    /**
     * MariaDB instance with the {@code main_schema} database and
     * {@code main_user@%} created automatically by
     * {@code db/mariadb/init/00_prepare_database.sql} mounted at
     * {@code /docker-entrypoint-initdb.d/}.
     *
     * <h2>Initialization Order</h2>
     * <pre>
     * 1. Container startup: image entrypoint creates root and applies the
     *    init script (creates main_schema database + main_user).
     * 2. MariadbDatabaseInitializer.migrate("mariadb/main_schema") — runs as main_user
     * </pre>
     *
     * <p>This is the standard factory for E2E tests. {@code ref_schema} is
     * intentionally absent (SEED_SPEC §1 anti-coverage).
     */
    public static MariaDbContainer withMainUser() {
        return new MariaDbContainer(true);
    }

    @Override public String  getHost()         { return container.getHost(); }
    @Override public Integer getDatabasePort() { return container.getMappedPort(MARIADB_PORT); }
    @Override public DB      getDbType()       { return DB.MARIADB; }
    @Override public GenericContainer<?> getContainer() { return container; }

    /**
     * Builds a MariaDB JDBC URL of the form
     * {@code jdbc:mariadb://host:port/dbName}. The {@code user} parameter
     * is accepted for interface symmetry but not embedded in the URL —
     * credentials go through {@link java.sql.DriverManager#getConnection}.
     */
    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format("jdbc:mariadb://%s:%d/%s",
            getHost(), getDatabasePort(), dbName);
    }

    @Override public void start() { container.start(); }
    @Override public void stop()  { container.stop(); }
    @Override public Set<Startable> getDependencies() { return container.getDependencies(); }

    /** Main migration user used by Flyway seeding and tests. */
    public String getMainUser()     { return MAIN_USER; }
    /** Password for {@link #getMainUser()}. */
    public String getMainPassword() { return MAIN_PASSWORD; }
    /** MariaDB database owned by {@link #getMainUser()}. */
    public String getMainDatabase() { return MAIN_DATABASE; }

    /** DBA user ({@code root}). */
    public String getRootUser()     { return ROOT_USER; }
    /** DBA password. */
    public String getRootPassword() { return ROOT_PASSWORD; }
}
