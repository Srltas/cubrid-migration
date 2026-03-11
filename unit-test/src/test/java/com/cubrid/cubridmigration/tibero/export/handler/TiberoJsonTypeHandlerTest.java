package com.cubrid.cubridmigration.tibero.export.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

@DisplayName("TiberoJsonTypeHandler")
class TiberoJsonTypeHandlerTest {

    private TiberoJsonTypeHandler handler;
    private ResultSet rs;
    private Column column;

    @BeforeEach
    void setUp() {
        handler = new TiberoJsonTypeHandler();
        rs = mock(ResultSet.class);
        column = new Column();
        column.setName("JSON_COL");
    }

    @Test
    @DisplayName("getTypeNameForError() → \"JSON\"")
    void getTypeNameForError_returnsJson() {
        assertThat(handler.getTypeNameForError()).isEqualTo("JSON");
    }

    // ------------------------------------------------------------------ //
    // null
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("null 값 → null 반환")
    void nullValue_returnsNull() throws SQLException {
        when(rs.getObject("JSON_COL")).thenReturn(null);

        assertThat(handler.getJdbcObject(rs, column)).isNull();
    }

    // ------------------------------------------------------------------ //
    // String dispatch
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("String 객체 → readTextValue를 통해 그대로 반환")
    void stringValue_returnedAsIs() throws SQLException {
        when(rs.getObject("JSON_COL")).thenReturn("{\"key\":\"value\"}");

        assertThat(handler.getJdbcObject(rs, column)).isEqualTo("{\"key\":\"value\"}");
    }

    // ------------------------------------------------------------------ //
    // byte[] dispatch
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("byte[] 처리")
    class ByteArrayDispatch {

        @Test
        @DisplayName("byte[] + getString 가능 → getString 우선 반환")
        void byteArray_getStringSucceeds_returnsString() throws SQLException {
            when(rs.getObject("JSON_COL")).thenReturn("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
            when(rs.getString("JSON_COL")).thenReturn("{\"a\":1}");

            assertThat(handler.getJdbcObject(rs, column)).isEqualTo("{\"a\":1}");
        }

        @Test
        @DisplayName("byte[] + getString null → decodeBinary로 UTF-8 디코딩")
        void byteArray_getStringNull_decodesBytes() throws SQLException {
            byte[] bytes = "{\"b\":2}".getBytes(StandardCharsets.UTF_8);
            when(rs.getObject("JSON_COL")).thenReturn(bytes);
            when(rs.getString("JSON_COL")).thenReturn(null);

            assertThat(handler.getJdbcObject(rs, column)).isEqualTo("{\"b\":2}");
        }
    }

    // ------------------------------------------------------------------ //
    // InputStream dispatch
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("InputStream 처리")
    class InputStreamDispatch {

        @Test
        @DisplayName("InputStream + getString 가능 → getString 우선 반환, stream 닫힘")
        void inputStream_getStringSucceeds_returnsString() throws SQLException {
            ByteArrayInputStream stream = new ByteArrayInputStream("{\"c\":3}".getBytes(StandardCharsets.UTF_8));
            when(rs.getObject("JSON_COL")).thenReturn(stream);
            when(rs.getString("JSON_COL")).thenReturn("{\"c\":3}");

            assertThat(handler.getJdbcObject(rs, column)).isEqualTo("{\"c\":3}");
        }

        @Test
        @DisplayName("InputStream + getString null → stream에서 읽어 반환")
        void inputStream_getStringNull_readsStream() throws SQLException {
            byte[] data = "{\"d\":4}".getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream stream = new ByteArrayInputStream(data);
            when(rs.getObject("JSON_COL")).thenReturn(stream);
            when(rs.getString("JSON_COL")).thenReturn(null);

            assertThat(handler.getJdbcObject(rs, column)).isEqualTo("{\"d\":4}");
        }
    }

    // ------------------------------------------------------------------ //
    // Blob dispatch
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("Blob + getString null → Blob binaryStream에서 읽기")
    void blobValue_readFromBlobStream() throws Exception {
        Blob blob = mock(Blob.class);
        byte[] data = "{\"e\":5}".getBytes(StandardCharsets.UTF_8);
        when(rs.getObject("JSON_COL")).thenReturn(blob);
        when(rs.getString("JSON_COL")).thenReturn(null);
        when(blob.getBinaryStream()).thenReturn(new ByteArrayInputStream(data));

        assertThat(handler.getJdbcObject(rs, column)).isEqualTo("{\"e\":5}");
    }

    // ------------------------------------------------------------------ //
    // 예외 처리
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("SQLException → 재전파")
    void sqlException_rethrown() throws SQLException {
        when(rs.getObject("JSON_COL")).thenThrow(new SQLException("json error"));

        assertThatThrownBy(() -> handler.getJdbcObject(rs, column))
                .isInstanceOf(SQLException.class)
                .hasMessage("json error");
    }

    @Test
    @DisplayName("비-SQL Exception → SQLException으로 래핑, 컬럼명 포함")
    void runtimeException_wrappedWithColName() throws SQLException {
        when(rs.getObject("JSON_COL")).thenThrow(new RuntimeException("unexpected"));

        assertThatThrownBy(() -> handler.getJdbcObject(rs, column))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("JSON_COL");
    }
}
