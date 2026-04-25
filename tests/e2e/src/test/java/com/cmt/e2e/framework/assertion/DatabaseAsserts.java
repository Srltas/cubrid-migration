package com.cmt.e2e.framework.assertion;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

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

    private DatabaseAsserts() {}

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
     * DatabaseAsserts.expectRecords(db, "cubdb", "CMT_TEST", Map.ofEntries(
     *     Map.entry("ora_cov_customer",  3),
     *     Map.entry("ora_cov_orders",    3),
     *     Map.entry("ora_cov_order_line", 4)
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
}
