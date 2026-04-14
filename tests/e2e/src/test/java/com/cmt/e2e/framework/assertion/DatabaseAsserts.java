package com.cmt.e2e.framework.assertion;

import java.sql.*;

import static org.assertj.core.api.Assertions.assertThat;

import com.cmt.e2e.framework.db.containers.DatabaseContainer;

public class DatabaseAsserts {

    private DatabaseAsserts() {}

    public static void assertRecordCount(DatabaseContainer dbContainer, String dbName, String userName, String tableName, int expectedCount) {
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
}
