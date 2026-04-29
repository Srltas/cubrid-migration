package com.cmt.e2e.framework.db.containers;

import com.cmt.e2e.framework.db.driver.Drivers.DB;
import org.testcontainers.lifecycle.Startable;

/**
 * Common surface for source/target database containers used by E2E tests.
 *
 * <p>Implementations wrap a Testcontainers {@code GenericContainer} but
 * intentionally hide it — tests interact through host/port/jdbcUrl only.
 */
public interface DatabaseContainer extends Startable {

    /** Host where the mapped database port is reachable. */
    String getHost();

    /** Externally exposed (host-side) database port. */
    Integer getDatabasePort();

    /** Engine identifier — drives JDBC driver lookup and template substitution. */
    DB getDbType();

    /**
     * Builds a JDBC URL for this container.
     *
     * <p>{@code dbName} and {@code user} semantics are engine-specific:
     * <ul>
     *   <li><b>CUBRID</b> embeds both into the URL
     *       ({@code jdbc:cubrid:host:port:db:user:::}) — both must be supplied.</li>
     *   <li><b>MySQL / MariaDB / MSSQL / Informix</b> use {@code dbName} as
     *       the URL path component but ignore {@code user}; auth is passed
     *       separately to {@code DriverManager}.</li>
     *   <li><b>Oracle</b> ignores both — the SID is fixed in the URL — and
     *       callers may pass {@code (null, null)}.</li>
     * </ul>
     */
    String getJdbcUrl(String dbName, String user);
}
