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

@DisplayName("TiberoXmlTypeHandler")
public class TiberoXmlTypeHandlerText {

    private static final TiberoXmlTypeHandler HANDLER = new TiberoXmlTypeHandler();

    private ResultSet rs = mock(ResultSet.class);
    private Column column = new Column();

    @BeforeEach
    void setUp() {
        rs = mock(ResultSet.class);
        column = new Column();
        column.setName("XML_COL");
    }

    @Test
    @DisplayName("getTypeNameForError() → \"XMLTYPE\"")
    void getTypeNameForError_returnsXmlType() {
        assertThat(HANDLER.getTypeNameForError()).isEqualTo("XMLTYPE");
    }

    @Test
    @DisplayName("String value -> return as is")
    void stringValue_returnedAsIs() throws SQLException {
        when(rs.getObject("XML_COL")).thenReturn("<root><id>1</id></root>");

        Object result = HANDLER.getJdbcObject(rs, column);

        assertThat(result).isEqualTo("<root><id>1</id></root>");
    }

    @Test
    @DisplayName("null value -> null")
    void nullValue_returnsNull() throws SQLException {
        when(rs.getObject("XML_COL")).thenReturn(null);

        assertThat(HANDLER.getJdbcObject(rs, column)).isNull();
    }

    @Test
    @DisplayName("other object -> delegate to rs.getString()")
    void otherObject_delegatesToGetString() throws SQLException {
        when(rs.getObject("XML_COL")).thenReturn(new Object());
        when(rs.getString("XML_COL")).thenReturn("<data/>");

        assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("<data/>");
    }

    @Test
    @DisplayName("SQLException -> rethrow")
    void sqlException_rethrown() throws SQLException {
        when(rs.getObject("XML_COL")).thenThrow(new SQLException("xml read error"));

        assertThatThrownBy(() -> HANDLER.getJdbcObject(rs, column))
                .isInstanceOf(SQLException.class)
                .hasMessage("xml read error");
    }

    @Test
    @DisplayName("non-SQL exception -> wrap to SQLException")
    void runtimeException_wrappedWithTypeName() throws SQLException {
        when(rs.getObject("XML_COL")).thenThrow(new RuntimeException("unexpected"));

        assertThatThrownBy(() -> HANDLER.getJdbcObject(rs, column))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("XML_COL");
    }
}
