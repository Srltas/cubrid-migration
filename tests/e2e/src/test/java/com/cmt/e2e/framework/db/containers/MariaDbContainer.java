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
 * MariaDB 11 Testcontainer (image {@code mariadb:11.4}). JDBC URL:
 * {@code jdbc:mariadb://host:port/dbName}. {@code ref_schema} is absent —
 * see SEED_SPEC §1 anti-coverage.
 */
public class MariaDbContainer implements DatabaseContainer {

    private static final DockerImageName IMAGE = DockerImageName.parse("mariadb:11.4");

    private static final int    MARIADB_PORT  = 3306;
    private static final String ROOT_USER     = "root";
    private static final String ROOT_PASSWORD = "cmt";
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
            // Allow main_user (non-SUPER) to CREATE FUNCTION; otherwise V5
            // routines fail with ER_BINLOG_UNSAFE_ROUTINE.
            .withCommand("mariadbd", "--log-bin-trust-function-creators=ON")
            // MariaDB emits "ready for connections" twice — once for the
            // bootstrap mariadbd that runs init scripts on a Unix socket,
            // once for the final TCP listener on port 3306. Unlike MySQL,
            // MariaDB splits the message and the port onto separate lines,
            // so we count occurrences rather than matching the port inline.
            // No X Plugin, so count=2 is unambiguous.
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

    public static MariaDbContainer withEmptyDb()  { return new MariaDbContainer(false); }
    public static MariaDbContainer withMainUser() { return new MariaDbContainer(true); }

    @Override public String  getHost()         { return container.getHost(); }
    @Override public Integer getDatabasePort() { return container.getMappedPort(MARIADB_PORT); }
    @Override public DB      getDbType()       { return DB.MARIADB; }
    @Override public GenericContainer<?> getContainer() { return container; }

    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format("jdbc:mariadb://%s:%d/%s",
            getHost(), getDatabasePort(), dbName);
    }

    @Override public void start() { container.start(); }
    @Override public void stop()  { container.stop(); }
    @Override public Set<Startable> getDependencies() { return container.getDependencies(); }

    public String getMainUser()     { return MAIN_USER; }
    public String getMainPassword() { return MAIN_PASSWORD; }
    public String getMainDatabase() { return MAIN_DATABASE; }
    public String getRootUser()     { return ROOT_USER; }
    public String getRootPassword() { return ROOT_PASSWORD; }
}
