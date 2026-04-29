package com.cmt.e2e.framework.source;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;

/**
 * Source database for an E2E migration test — wraps container lifecycle
 * and seed application behind a single {@link #start()} call.
 *
 * <p>Tests obtain instances through {@link Sources} factory methods.
 * The lifecycle contract:
 *
 * <ol>
 *   <li>{@code Source} is constructed (cheap, no Docker / no JDBC).</li>
 *   <li>{@link #start()} brings up the container and applies seed.
 *       Idempotent — repeat calls are no-ops.</li>
 *   <li>{@link #connection()} is read by {@code Migration} and the
 *       verify layer.</li>
 *   <li>{@link #close()} stops the container.</li>
 * </ol>
 *
 * <p>Implementations stay package-private; only {@link Sources} exposes
 * them to tests.
 */
public interface Source extends AutoCloseable {

    /**
     * Brings up the container and applies seed (idempotent).
     *
     * @throws RuntimeException on container start or seed failure
     */
    void start();

    /**
     * The connection identity CMT uses to introspect this source. Must be
     * called only after {@link #start()}.
     */
    ConnectionConfig connection();

    /** Engine identifier — drives JDBC driver lookup and CMT db.conf type. */
    DB type();

    @Override
    void close();
}
