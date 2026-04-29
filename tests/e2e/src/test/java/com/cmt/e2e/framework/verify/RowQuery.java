package com.cmt.e2e.framework.verify;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import com.cmt.e2e.framework.source.ConnectionConfig;

/**
 * Single arbitrary SQL query — captures its result as a snapshot, or
 * extracts a scalar for inline assertions (handy for L4 regression tests).
 *
 * <p>Caller is responsible for query determinism: use {@code ORDER BY}
 * when ordering matters, and stay schema-qualified
 * ({@code "OWNER"."TABLE"}) when introspecting as {@code dba}.
 */
public final class RowQuery {

    private final String sql;
    private final ConnectionConfig connection;
    private final String scenarioName;

    public RowQuery(String sql, ConnectionConfig connection, String scenarioName) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("sql must not be blank");
        }
        if (connection.type() != DB.CUBRID) {
            throw new IllegalArgumentException(
                "RowQuery is CUBRID-specific (got " + connection.type() + ")");
        }
        this.sql = sql;
        this.connection = connection;
        this.scenarioName = scenarioName;
    }

    /** Snapshot the full result table at {@code snapshots/<scenario>/<name>.txt}. */
    public RowQuery matchesSnapshot(String name) {
        Path snap = CatalogSnapshot.SNAPSHOT_ROOT.resolve(scenarioName).resolve(name + ".txt");
        SnapshotStore.match(snap, runAsTable());
        return this;
    }

    /** Single scalar — first column of first row. {@code null} if no rows. */
    public String firstColumn() {
        try (Connection conn = DriverManager.getConnection(connection.cubridJdbcUrl());
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) return null;
            return rs.getString(1);
        } catch (SQLException e) {
            throw new RuntimeException("Query failed: " + sql, e);
        }
    }

    private String runAsTable() {
        try (Connection conn = DriverManager.getConnection(connection.cubridJdbcUrl());
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return Tabulator.format(rs);
        } catch (SQLException e) {
            throw new RuntimeException("Query failed: " + sql, e);
        }
    }
}
