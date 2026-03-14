package com.cubrid.cubridmigration.tibero.export.handler;

import com.cubrid.cubridmigration.core.dbobject.Column;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("TiberoIntervalYMTypeHandler")
public class TiberoIntervalYMTypeHandlerTest {

    private TiberoIntervalYMTypeHandler HANDLER = new TiberoIntervalYMTypeHandler();

    private ResultSet rs;
    private Column column;

    @BeforeEach
    void setUp() {
        rs = mock(ResultSet.class);
        column = new Column();
        column.setName("TENURE_COL");
    }

    @Test
    @DisplayName("normal value -> return getString() result")
    void normal_returnsStringValue() throws SQLException {
        when(rs.getString("TENURE_COL")).thenReturn("2-3");

        assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("2-3");
    }

    @Test
    @DisplayName("null value -> null")
    void nullValue_returnsNull() throws SQLException {
        when(rs.getString("TENURE_COL")).thenReturn(null);

        assertThat(HANDLER.getJdbcObject(rs, column)).isNull();
    }

    @Test
    @DisplayName("SQLException -> rethrow")
    void sqlException_rethrown() throws SQLException {
        when(rs.getString("TENURE_COL")).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> HANDLER.getJdbcObject(rs, column))
                .isInstanceOf(SQLException.class)
                .hasMessage("DB error");
    }

    @Test
    @DisplayName("non-SQL RuntimeException -> wrap to SQLException with column name")
    void runtimeException_wrappedInSqlException() throws SQLException {
        when(rs.getString("TENURE_COL")).thenThrow(new RuntimeException("unexpected"));

        assertThatThrownBy(() -> HANDLER.getJdbcObject(rs, column))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("INTERVAL YEAR TO MONTH")
                .hasMessageContaining("TENURE_COL");
    }
}
