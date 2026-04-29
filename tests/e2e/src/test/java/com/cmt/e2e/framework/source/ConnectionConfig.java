package com.cmt.e2e.framework.source;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;

/**
 * Self-describing JDBC connection identity that CMT and the verify layer
 * need.
 *
 * <p>{@code timezone} may be {@code null} when the engine doesn't expose
 * a meaningful default (most engines other than Oracle).
 */
public record ConnectionConfig(
    DB type,
    String host,
    int port,
    String dbname,
    String user,
    String password,
    String charset,
    String timezone
) {
    public ConnectionConfig {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (host == null || host.isBlank()) throw new IllegalArgumentException("host must not be blank");
        if (port <= 0) throw new IllegalArgumentException("port must be positive");
        if (dbname == null || dbname.isBlank()) throw new IllegalArgumentException("dbname must not be blank");
        if (user == null) throw new IllegalArgumentException("user must not be null");
        if (password == null) throw new IllegalArgumentException("password must not be null (use \"\" for none)");
        if (charset == null || charset.isBlank()) throw new IllegalArgumentException("charset must not be blank");
        // timezone may be null
    }

    /**
     * CUBRID JDBC URL for this connection. Used by the verify layer to
     * introspect target catalog / row data.
     *
     * <p>The CUBRID JDBC driver rejects an empty password slot
     * ({@code ...:user:::}) as "invalid URL". When {@code password} is
     * empty we therefore use the slot-less form {@code ...:user::} —
     * PoC convention.
     *
     * @throws IllegalStateException if {@link #type()} is not CUBRID
     */
    public String cubridJdbcUrl() {
        if (type != DB.CUBRID) {
            throw new IllegalStateException(
                "cubridJdbcUrl() is CUBRID-specific (got " + type + ")");
        }
        if (password.isEmpty()) {
            return String.format("jdbc:cubrid:%s:%d:%s:%s::",
                host, port, dbname, user);
        }
        return String.format("jdbc:cubrid:%s:%d:%s:%s:%s::",
            host, port, dbname, user, password);
    }
}
