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

@DisplayName("TiberoIntervalDSTypeHandler")
public class TiberoIntervalDSTypeHandlerTest {

    private static final TiberoIntervalDSTypeHandler HANDLER = new TiberoIntervalDSTypeHandler();

    private ResultSet rs;
    private Column column;

    @BeforeEach
    void setUp() {
        rs = mock(ResultSet.class);
        column = new Column();
        column.setName("DURATION_COL");
    }

    @Test
    @DisplayName("normal value -> return getString() result")
    void normal_returnsStringValue() throws SQLException {
        when(rs.getString("DURATION_COL")).thenReturn("1 2:03:04.5");

        Object result = HANDLER.getJdbcObject(rs, column);

        assertThat(result).isEqualTo("1 2:03:04.5");
    }

    @Test
    @DisplayName("null value -> null")
    void nullValue_returnsNull() throws SQLException {
        when(rs.getString("DURATION_COL")).thenReturn(null);

        assertThat(HANDLER.getJdbcObject(rs, column)).isNull();
    }

    @Test
    @DisplayName("SQLException -> rethrow")
    void sqlException_rethrown() throws SQLException {
        when(rs.getString("DURATION_COL")).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> HANDLER.getJdbcObject(rs, column))
                .isInstanceOf(SQLException.class)
                .hasMessage("DB error");
    }

    @Test
    @DisplayName("non-SQL RuntimeException -> wrap to SQLException with column name")
    void runtimeException_wrappedInSqlException() throws SQLException {
        when(rs.getString("DURATION_COL")).thenThrow(new RuntimeException("unexpected"));

        assertThatThrownBy(() -> HANDLER.getJdbcObject(rs, column))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("INTERVAL DAY TO SECOND")
                .hasMessageContaining("DURATION_COL");
    }
}
