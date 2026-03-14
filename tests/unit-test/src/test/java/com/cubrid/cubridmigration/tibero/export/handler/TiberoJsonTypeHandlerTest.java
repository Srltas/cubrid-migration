package com.cubrid.cubridmigration.tibero.export.handler;

import com.cubrid.cubridmigration.core.dbobject.Column;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("TiberoJsonTypeHandler")
public class TiberoJsonTypeHandlerTest {

    private static final TiberoJsonTypeHandler HANDLER = new TiberoJsonTypeHandler();

    private ResultSet rs;
    private Column column;

    @BeforeEach
    void setUp() {
        rs = mock(ResultSet.class);
        column = new Column();
        column.setName("JSON_COL");
    }

    @Test
    @DisplayName("getTypeNameForError() → \"JSON\"")
    void getTypeNameForError_returnsJson() {
        assertThat(HANDLER.getTypeNameForError()).isEqualTo("JSON");
    }

    @Test
    @DisplayName("null value -> null")
    void nullValue_returnsNull() throws SQLException {
        when(rs.getObject("JSON_COL")).thenReturn(null);

        assertThat(HANDLER.getJdbcObject(rs, column)).isNull();
    }

    @Test
    @DisplayName("String object -> return as is via readTextValue")
    void stringValue_returnedAsIs() throws SQLException {
        when(rs.getObject("JSON_COL")).thenReturn("{\"key\":\"value\"}");

        assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("{\"key\":\"value\"}");
    }

    @Nested
    @DisplayName("byte[] handling")
    class ByteArrayDispatch {

        @Test
        @DisplayName("byte[] with getString -> prefer getString")
        void byteArray_getStringSucceeds_returnsString() throws SQLException {
            when(rs.getObject("JSON_COL")).thenReturn("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
            when(rs.getString("JSON_COL")).thenReturn("{\"a\":1}");

            assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("{\"a\":1}");
        }

        @Test
        @DisplayName("byte[] with null getString -> decodeBinary as UTF-8")
        void byteArray_getStringNull_decodesBytes() throws SQLException {
            byte[] bytes = "{\"b\":2}".getBytes(StandardCharsets.UTF_8);
            when(rs.getObject("JSON_COL")).thenReturn(bytes);
            when(rs.getString("JSON_COL")).thenReturn(null);

            assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("{\"b\":2}");
        }
    }

    @Nested
    @DisplayName("InputStream handling")
    class InputStreamDispatch {

        @Test
        @DisplayName("InputStream with getString -> prefer getString")
        void inputStream_getStringSucceeds_returnsString() throws SQLException {
            ByteArrayInputStream stream = new ByteArrayInputStream("{\"c\":3}".getBytes(StandardCharsets.UTF_8));
            when(rs.getObject("JSON_COL")).thenReturn(stream);
            when(rs.getString("JSON_COL")).thenReturn("{\"c\":3}");

            assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("{\"c\":3}");
        }

        @Test
        @DisplayName("InputStream with null getString -> read from stream")
        void inputStream_getStringNull_readsStream() throws SQLException {
            byte[] data = "{\"d\":4}".getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream stream = new ByteArrayInputStream(data);
            when(rs.getObject("JSON_COL")).thenReturn(stream);
            when(rs.getString("JSON_COL")).thenReturn(null);

            assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("{\"d\":4}");
        }
    }

    @Test
    @DisplayName("Blob with null getString -> read from binaryStream")
    void blobValue_readFromBlobStream() throws Exception {
        Blob blob = mock(Blob.class);
        byte[] data = "{\"e\":5}".getBytes(StandardCharsets.UTF_8);
        when(rs.getObject("JSON_COL")).thenReturn(blob);
        when(rs.getString("JSON_COL")).thenReturn(null);
        when(blob.getBinaryStream()).thenReturn(new ByteArrayInputStream(data));

        assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("{\"e\":5}");
    }

    @Test
    @DisplayName("SQLException -> rethrow")
    void sqlException_rethrown() throws SQLException {
        when(rs.getObject("JSON_COL")).thenThrow(new SQLException("json error"));

        assertThatThrownBy(() -> HANDLER.getJdbcObject(rs, column))
                .isInstanceOf(SQLException.class)
                .hasMessage("json error");
    }

    @Test
    @DisplayName("non-SQL exception -> wrap to SQLException with column name")
    void runtimeException_wrappedWithColName() throws SQLException {
        when(rs.getObject("JSON_COL")).thenThrow(new RuntimeException("unexpected"));

        assertThatThrownBy(() -> HANDLER.getJdbcObject(rs, column))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("JSON_COL");
    }
}
