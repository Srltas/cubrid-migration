package com.cubrid.cubridmigration.tibero.export.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Column;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

@DisplayName("TiberoIntervalYMTypeHandler")
class TiberoIntervalYMTypeHandlerTest {

    private TiberoIntervalYMTypeHandler handler;
    private ResultSet rs;
    private Column column;

    @BeforeEach
    void setUp() {
        handler = new TiberoIntervalYMTypeHandler();
        rs = mock(ResultSet.class);
        column = new Column();
        column.setName("TENURE_COL");
    }

    @Test
    @DisplayName("정상 값 → getString() 결과 반환")
    void normal_returnsStringValue() throws SQLException {
        when(rs.getString("TENURE_COL")).thenReturn("2-3");

        assertThat(handler.getJdbcObject(rs, column)).isEqualTo("2-3");
    }

    @Test
    @DisplayName("null 값 → null 반환")
    void nullValue_returnsNull() throws SQLException {
        when(rs.getString("TENURE_COL")).thenReturn(null);

        assertThat(handler.getJdbcObject(rs, column)).isNull();
    }

    @Test
    @DisplayName("SQLException → 그대로 재전파")
    void sqlException_rethrown() throws SQLException {
        when(rs.getString("TENURE_COL")).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> handler.getJdbcObject(rs, column))
                .isInstanceOf(SQLException.class)
                .hasMessage("DB error");
    }

    @Test
    @DisplayName("비-SQL RuntimeException → SQLException으로 래핑, 컬럼명 포함")
    void runtimeException_wrappedInSqlException() throws SQLException {
        when(rs.getString("TENURE_COL")).thenThrow(new RuntimeException("unexpected"));

        assertThatThrownBy(() -> handler.getJdbcObject(rs, column))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("INTERVAL YEAR TO MONTH")
                .hasMessageContaining("TENURE_COL");
    }
}
