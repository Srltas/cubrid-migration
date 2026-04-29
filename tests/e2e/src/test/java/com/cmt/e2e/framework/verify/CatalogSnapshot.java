package com.cmt.e2e.framework.verify;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import com.cmt.e2e.framework.source.ConnectionConfig;

/**
 * Catalog snapshot entry — fluent verification of CUBRID
 * target-side metadata after migration.
 *
 * <p>Usage from a test method:
 * <pre>{@code
 * @Test void allObjectsMigrated() {
 *     var c = run().catalog();
 *     c.matchesSnapshot("classes");
 *     c.matchesSnapshot("indexes");
 * }
 * }</pre>
 *
 * <p>Each {@link #matchesSnapshot(String)} call:
 * <ol>
 *   <li>Looks up the SQL by name from {@link CatalogQueries}.</li>
 *   <li>Opens a JDBC connection to the (CUBRID online) target.</li>
 *   <li>Formats the {@link ResultSet} with {@link Tabulator}.</li>
 *   <li>Hands the text to {@link SnapshotStore#match(Path, String)}.</li>
 * </ol>
 *
 * <p>Snapshot path:
 * {@code src/test/resources/snapshots/<scenario>/<name>.txt}.
 */
public final class CatalogSnapshot {

    /** Resource root — relative because tests run from the e2e module dir. */
    static final Path SNAPSHOT_ROOT =
        Paths.get("src", "test", "resources", "snapshots");

    private final ConnectionConfig connection;
    private final String scenarioName;

    public CatalogSnapshot(ConnectionConfig connection, String scenarioName) {
        if (connection.type() != DB.CUBRID) {
            throw new IllegalArgumentException(
                "CatalogSnapshot is CUBRID-specific (got " + connection.type() + ")");
        }
        this.connection = connection;
        this.scenarioName = scenarioName;
    }

    /**
     * Run the named catalog query and compare its result against the
     * checked-in snapshot {@code snapshots/<scenario>/<name>.txt}.
     */
    public CatalogSnapshot matchesSnapshot(String name) {
        String sql = CatalogQueries.byName(name);
        String actual = runQueryAsTable(sql);
        Path snapshotPath = SNAPSHOT_ROOT.resolve(scenarioName).resolve(name + ".txt");
        SnapshotStore.match(snapshotPath, actual);
        return this;
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private String runQueryAsTable(String sql) {
        String url = connection.cubridJdbcUrl();
        try (Connection conn = DriverManager.getConnection(url);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return Tabulator.format(rs);
        } catch (SQLException e) {
            throw new RuntimeException(
                "Catalog query failed:\n  url: " + url + "\n  sql: " + sql, e);
        }
    }
}
