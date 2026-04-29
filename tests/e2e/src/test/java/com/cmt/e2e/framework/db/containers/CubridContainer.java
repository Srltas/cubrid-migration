package com.cmt.e2e.framework.db.containers;

import java.time.Duration;

import com.cmt.e2e.framework.db.driver.Drivers.DB;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class CubridContainer implements DatabaseContainer {
    private static final DockerImageName IMAGE = DockerImageName.parse("cubriddmkim/cubrid_demodb:11.4");
    private static final int CUBRID_PORT = 33000;

    private final GenericContainer<?> container;

    private CubridContainer() {
        this.container = new GenericContainer<>(IMAGE)
            .withPrivilegedMode(true)
            .withEnv("CUBRID_COMPONENTS", "ALL")
            .withExposedPorts(CUBRID_PORT)
            .waitingFor(Wait.forLogMessage(".*cubrid server start: success.*", 1))
            .withStartupTimeout(Duration.ofMinutes(8));
    }

    public static CubridContainer withEmptyDb() {
        return new CubridContainer();
    }

    @Override public String  getHost()         { return container.getHost(); }
    @Override public Integer getDatabasePort() { return container.getMappedPort(CUBRID_PORT); }
    @Override public DB      getDbType()       { return DB.CUBRID; }

    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format("jdbc:cubrid:%s:%d:%s:%s::",
            getHost(), getDatabasePort(), dbName, user);
    }

    @Override public void start() { container.start(); }
    @Override public void stop()  { container.stop(); }
}
