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

@DisplayName("TiberoXmlTypeHandler")
class TiberoXmlTypeHandlerTest {

    private TiberoXmlTypeHandler handler;
    private ResultSet rs;
    private Column column;

    @BeforeEach
    void setUp() {
        handler = new TiberoXmlTypeHandler();
        rs = mock(ResultSet.class);
        column = new Column();
        column.setName("XML_COL");
    }

    @Test
    @DisplayName("getTypeNameForError() → \"XMLTYPE\"")
    void getTypeNameForError_returnsXmlType() {
        assertThat(handler.getTypeNameForError()).isEqualTo("XMLTYPE");
    }

    @Test
    @DisplayName("String 값 → 그대로 반환")
    void stringValue_returnedAsIs() throws SQLException {
        when(rs.getObject("XML_COL")).thenReturn("<root><id>1</id></root>");

        Object result = handler.getJdbcObject(rs, column);

        assertThat(result).isEqualTo("<root><id>1</id></root>");
    }

    @Test
    @DisplayName("null 값 → null 반환")
    void nullValue_returnsNull() throws SQLException {
        when(rs.getObject("XML_COL")).thenReturn(null);

        assertThat(handler.getJdbcObject(rs, column)).isNull();
    }

    @Test
    @DisplayName("기타 객체 → rs.getString() 위임")
    void otherObject_delegatesToGetString() throws SQLException {
        when(rs.getObject("XML_COL")).thenReturn(new Object());
        when(rs.getString("XML_COL")).thenReturn("<data/>");

        assertThat(handler.getJdbcObject(rs, column)).isEqualTo("<data/>");
    }

    @Test
    @DisplayName("SQLException → 재전파")
    void sqlException_rethrown() throws SQLException {
        when(rs.getObject("XML_COL")).thenThrow(new SQLException("xml read error"));

        assertThatThrownBy(() -> handler.getJdbcObject(rs, column))
                .isInstanceOf(SQLException.class)
                .hasMessage("xml read error");
    }

    @Test
    @DisplayName("비-SQL Exception → SQLException으로 래핑, XMLTYPE 언급")
    void runtimeException_wrappedWithTypeName() throws SQLException {
        when(rs.getObject("XML_COL")).thenThrow(new RuntimeException("unexpected"));

        assertThatThrownBy(() -> handler.getJdbcObject(rs, column))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("XML_COL");
    }
}
