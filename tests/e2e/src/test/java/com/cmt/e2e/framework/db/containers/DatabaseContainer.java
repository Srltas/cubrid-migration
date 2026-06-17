package com.cmt.e2e.framework.db.containers;

import com.cmt.e2e.framework.db.driver.Drivers.DB;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startable;

public interface DatabaseContainer extends Startable {

    /**
     * Returns the host name where the container is running.
     */
    String getHost();

    /**
     * Returns the externally exposed database port.
     */
    Integer getDatabasePort();

    /**
     * Returns the database type of this container.
     */
    DB getDbType();

    /**
     * Returns the underlying Testcontainers {@link GenericContainer} instance.
     */
    GenericContainer<?> getContainer();

    /**
     * Builds a JDBC URL for this database type.
     *
     * @param dbName database name
     * @param user connecting user
     * @return JDBC URL string
     */
    String getJdbcUrl(String dbName, String user);
}
