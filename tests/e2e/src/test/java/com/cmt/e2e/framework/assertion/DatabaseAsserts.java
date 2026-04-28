package com.cmt.e2e.framework.assertion;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cmt.e2e.framework.db.containers.DatabaseContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin helper for validating database state, especially row counts.
 * Use {@link #assertRecordCount} for a single table and
 * {@link #expectRecords} to validate multiple tables in one call.
 */
public class DatabaseAsserts {
    private static final Logger log = LoggerFactory.getLogger(DatabaseAsserts.class);
    private static final String NULL_VALUE = "<NULL>";

    private DatabaseAsserts() {}

    public record QueryExpectation(String label, String sql, List<List<String>> expectedRows) {
        public QueryExpectation {
            if (label == null || label.isBlank()) {
                throw new IllegalArgumentException("Query expectation label must not be blank.");
            }
            if (sql == null || sql.isBlank()) {
                throw new IllegalArgumentException("Query expectation SQL must not be blank.");
            }
            expectedRows = expectedRows.stream()
                .map(List::copyOf)
                .toList();
        }

        @SafeVarargs
        public static QueryExpectation of(String label, String sql, List<String>... expectedRows) {
            return new QueryExpectation(label, sql, List.of(expectedRows));
        }
    }

    public static List<String> row(String... values) {
        return Arrays.stream(values)
            .map(value -> Objects.requireNonNullElse(value, NULL_VALUE))
            .toList();
    }

    /**
     * Validates the row count of a single table.
     */
    public static void assertRecordCount(DatabaseContainer dbContainer, String dbName, String userName,
                                         String tableName, int expectedCount) {
        String jdbcUrl = dbContainer.getJdbcUrl(dbName, userName);

        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {

            rs.next();
            int actualCount = rs.getInt(1);
            assertThat(actualCount)
                .as("Record count of table '%s'", tableName)
                .isEqualTo(expectedCount);
        } catch (Exception e) {
            throw new AssertionError(String.format("Failed to verify record count for table '%s'.", tableName), e);
        }
    }

    /**
     * Validates row counts for multiple tables in a single call.
     *
     * <pre>
     * DatabaseAsserts.expectRecords(db, "cubdb", "MAIN_SCHEMA", Map.ofEntries(
     *     Map.entry("e2e_customer",   4),
     *     Map.entry("e2e_order",      4),
     *     Map.entry("e2e_order_line", 4)
     * ));
     * </pre>
     *
     * Even on failure, this checks every table and throws one {@link AssertionError}
     * containing the full mismatch set so a single run shows the complete picture.
     */
    public static void expectRecords(DatabaseContainer dbContainer, String dbName, String userName,
                                     Map<String, Integer> expectedCounts) {
        StringBuilder failures = new StringBuilder();
        String jdbcUrl = dbContainer.getJdbcUrl(dbName, userName);

        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {

            for (Map.Entry<String, Integer> entry : expectedCounts.entrySet()) {
                String table = entry.getKey();
                int expected = entry.getValue();
                try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    rs.next();
                    int actual = rs.getInt(1);
                    if (actual != expected) {
                        failures.append(String.format("  %-32s expected: %d, actual: %d%n",
                            table, expected, actual));
                    }
                } catch (Exception e) {
                    failures.append(String.format("  %-32s query error: %s%n", table, e.getMessage()));
                }
            }
        } catch (Exception e) {
            log.info("Failed to open JDBC connection for {} / {}. Full JDBC URL: {}", dbName, userName, jdbcUrl, e);
            throw new AssertionError("Failed to open JDBC connection for data verification", e);
        }

        if (failures.length() > 0) {
            log.info("Record count mismatches for {} / {}:{}{}", dbName, userName, System.lineSeparator(), failures);
            throw new AssertionError("Record count verification failed");
        }
    }

    /**
     * Executes scenario-specific queries and compares their rows as strings.
     *
     * <p>This helper intentionally does not generate SQL or hide database
     * dialect differences. Tests should write explicit queries that return
     * stable, comparable values for the database under verification.
     */
    public static void expectQueryResults(
        DatabaseContainer dbContainer,
        String dbName,
        String userName,
        List<QueryExpectation> expectations
    ) {
        StringBuilder failures = new StringBuilder();
        String jdbcUrl = dbContainer.getJdbcUrl(dbName, userName);

        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {

            for (QueryExpectation expectation : expectations) {
                try (ResultSet rs = statement.executeQuery(expectation.sql())) {
                    List<List<String>> actualRows = readRows(rs);
                    if (!actualRows.equals(expectation.expectedRows())) {
                        failures.append(String.format(
                            "  [%s]%n    SQL: %s%n    expected: %s%n    actual:   %s%n",
                            expectation.label(),
                            singleLineSql(expectation.sql()),
                            expectation.expectedRows(),
                            actualRows));
                    }
                } catch (Exception e) {
                    failures.append(String.format(
                        "  [%s]%n    SQL: %s%n    query error: %s%n",
                        expectation.label(),
                        singleLineSql(expectation.sql()),
                        e.getMessage()));
                }
            }
        } catch (Exception e) {
            log.info("Failed to open JDBC connection for {} / {}. Full JDBC URL: {}", dbName, userName, jdbcUrl, e);
            throw new AssertionError("Failed to open JDBC connection for query result verification", e);
        }

        if (failures.length() > 0) {
            log.info("Query result mismatches for {} / {}:{}{}", dbName, userName, System.lineSeparator(), failures);
            throw new AssertionError("Query result verification failed");
        }
    }

    private static List<List<String>> readRows(ResultSet rs) throws Exception {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<List<String>> rows = new ArrayList<>();

        while (rs.next()) {
            List<String> row = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                String value = rs.getString(i);
                row.add(value == null ? NULL_VALUE : value);
            }
            rows.add(List.copyOf(row));
        }
        return List.copyOf(rows);
    }

    private static String singleLineSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
