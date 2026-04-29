package com.cmt.e2e.framework.db.containers;

import java.time.Duration;

import com.cmt.e2e.framework.db.driver.Drivers.DB;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * MySQL 8.0 Testcontainer (image {@code mysql:8.0.39}). JDBC URL format:
 * {@code jdbc:mysql://host:port/dbName}. {@code ref_schema} is absent —
 * see SEED_SPEC §1 anti-coverage.
 */
public class MySqlContainer implements DatabaseContainer {

    private static final DockerImageName IMAGE = DockerImageName.parse("mysql:8.0.39");

    private static final int    MYSQL_PORT    = 3306;
    private static final String ROOT_USER     = "root";
    private static final String ROOT_PASSWORD = "cmt";
    private static final String MAIN_USER     = "main_user";
    private static final String MAIN_PASSWORD = "cmt";
    private static final String MAIN_DATABASE = "main_schema";

    private static final String INIT_CLASSPATH =
        "db/mysql/init/00_prepare_database.sql";
    private static final String INIT_CONTAINER_PATH =
        "/docker-entrypoint-initdb.d/00_prepare_database.sql";

    private final GenericContainer<?> container;

    private MySqlContainer(boolean withInit) {
        GenericContainer<?> c = new GenericContainer<>(IMAGE)
            .withExposedPorts(MYSQL_PORT)
            .withEnv("MYSQL_ROOT_PASSWORD", ROOT_PASSWORD)
            // Allow main_user (non-SUPER) to CREATE FUNCTION; without this,
            // routine seed scripts fail with ER_BINLOG_UNSAFE_ROUTINE (1419).
            .withCommand("mysqld", "--log-bin-trust-function-creators=ON")
            // The mysql:8.0 entrypoint starts mysqld twice. Both phases log
            // "ready for connections", so we anchor on the "mysqld:" prefix
            // (excludes "X Plugin") plus a trailing space after 3306
            // (excludes the 33060 substring match) to wait for the final
            // TCP listener after init scripts have run.
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

    public static MySqlContainer withEmptyDb()  { return new MySqlContainer(false); }
    public static MySqlContainer withMainUser() { return new MySqlContainer(true); }

    @Override public String  getHost()         { return container.getHost(); }
    @Override public Integer getDatabasePort() { return container.getMappedPort(MYSQL_PORT); }
    @Override public DB      getDbType()       { return DB.MYSQL; }

    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format("jdbc:mysql://%s:%d/%s",
            getHost(), getDatabasePort(), dbName);
    }

    @Override public void start() { container.start(); }
    @Override public void stop()  { container.stop(); }

    public String getMainUser()     { return MAIN_USER; }
    public String getMainPassword() { return MAIN_PASSWORD; }
    public String getMainDatabase() { return MAIN_DATABASE; }
    public String getRootUser()     { return ROOT_USER; }
    public String getRootPassword() { return ROOT_PASSWORD; }
}
