package com.cmt.e2e.framework.db.containers;

import java.time.Duration;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** MariaDB 11 Testcontainer ({@code jdbc:mariadb://host:port/dbName}). */
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
            // Required for non-SUPER user to CREATE FUNCTION (else ER_BINLOG_UNSAFE_ROUTINE).
            .withCommand("mariadbd", "--log-bin-trust-function-creators=ON")
            // mariadbd boots twice (bootstrap socket + final TCP); count=2
            // anchors on the post-init listener.
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

    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format("jdbc:mariadb://%s:%d/%s",
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
