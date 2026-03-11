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
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * AbstractTiberoTextTypeHandler의 내부 로직을 TestTextTypeHandler (구체 서브클래스)를 통해 검증.
 */
@DisplayName("AbstractTiberoTextTypeHandler")
class AbstractTiberoTextTypeHandlerTest {

    /** 테스트용 구체 서브클래스 */
    static class TestTextTypeHandler extends AbstractTiberoTextTypeHandler {
        @Override
        protected String getTypeNameForError() {
            return "TEST";
        }
    }

    private TestTextTypeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TestTextTypeHandler();
    }

    // ------------------------------------------------------------------ //
    // getTypeNameForError() / buildReadErrorMessage()
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("buildReadErrorMessage()")
    class BuildReadErrorMessage {

        @Test
        @DisplayName("\"Failed to read Tibero TEST value: COL\" 형식 반환")
        void returnsFormattedMessage() {
            assertThat(handler.buildReadErrorMessage("MY_COL"))
                    .isEqualTo("Failed to read Tibero TEST value: MY_COL");
        }

        @Test
        @DisplayName("컬럼명이 메시지에 포함됨")
        void colNameIncludedInMessage() {
            assertThat(handler.buildReadErrorMessage("JSON_DATA"))
                    .contains("JSON_DATA");
        }
    }

    // ------------------------------------------------------------------ //
    // resolveCharsetByBom()
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("resolveCharsetByBom()")
    class ResolveCharsetByBom {

        @Test
        @DisplayName("UTF-8 BOM (EF BB BF) → UTF-8, offset=3")
        void utf8Bom_returnsUtf8Offset3() throws SQLException {
            byte[] bytes = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'H', 'i'};

            String result = handler.decodeBinary(bytes);

            assertThat(result).isEqualTo("Hi");
        }

        @Test
        @DisplayName("UTF-16BE BOM (FE FF) → UTF-16BE, offset=2")
        void utf16BeBom_returnsUtf16Be() throws SQLException {
            // "A" in UTF-16BE is 0x00 0x41
            byte[] bytes = new byte[]{(byte) 0xFE, (byte) 0xFF, 0x00, 0x41};

            String result = handler.decodeBinary(bytes);

            assertThat(result).isEqualTo("A");
        }

        @Test
        @DisplayName("UTF-16LE BOM (FF FE) → UTF-16LE, offset=2")
        void utf16LeBom_returnsUtf16Le() throws SQLException {
            // "A" in UTF-16LE is 0x41 0x00
            byte[] bytes = new byte[]{(byte) 0xFF, (byte) 0xFE, 0x41, 0x00};

            String result = handler.decodeBinary(bytes);

            assertThat(result).isEqualTo("A");
        }

        @Test
        @DisplayName("BOM 없음 → UTF-8 기본값, offset=0")
        void noBom_defaultsToUtf8() throws SQLException {
            byte[] bytes = "Hello".getBytes(StandardCharsets.UTF_8);

            String result = handler.decodeBinary(bytes);

            assertThat(result).isEqualTo("Hello");
        }
    }

    // ------------------------------------------------------------------ //
    // decodeBinary()
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("decodeBinary()")
    class DecodeBinary {

        @Test
        @DisplayName("null → null")
        void nullBytes_returnsNull() throws SQLException {
            assertThat(handler.decodeBinary(null)).isNull();
        }

        @Test
        @DisplayName("빈 배열 → 빈 문자열")
        void emptyBytes_returnsEmptyString() throws SQLException {
            assertThat(handler.decodeBinary(new byte[0])).isEmpty();
        }

        @Test
        @DisplayName("한글 UTF-8 인코딩 디코딩")
        void koreanUtf8_decodedCorrectly() throws SQLException {
            byte[] bytes = "안녕".getBytes(StandardCharsets.UTF_8);

            assertThat(handler.decodeBinary(bytes)).isEqualTo("안녕");
        }
    }

    // ------------------------------------------------------------------ //
    // readFromInputStream()
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("readFromInputStream()")
    class ReadFromInputStream {

        @Test
        @DisplayName("null InputStream → null 반환")
        void nullInputStream_returnsNull() throws SQLException {
            assertThat(handler.readFromInputStream(null)).isNull();
        }

        @Test
        @DisplayName("InputStream에서 텍스트 읽기")
        void inputStream_readsText() throws SQLException {
            byte[] data = "test data".getBytes(StandardCharsets.UTF_8);

            String result = handler.readFromInputStream(new ByteArrayInputStream(data));

            assertThat(result).isEqualTo("test data");
        }

        @Test
        @DisplayName("2048 바이트 이상 데이터도 전부 읽음")
        void largeInputStream_readsAll() throws SQLException {
            String longStr = "X".repeat(5000);
            byte[] data = longStr.getBytes(StandardCharsets.UTF_8);

            String result = handler.readFromInputStream(new ByteArrayInputStream(data));

            assertThat(result).hasSize(5000);
        }
    }

    // ------------------------------------------------------------------ //
    // readTextValue(Object, ResultSet, String) — 타입 분기
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("readTextValue(Object, ResultSet, String)")
    class ReadTextValueObjectDispatch {

        @Test
        @DisplayName("null → null 반환")
        void nullValue_returnsNull() throws SQLException {
            ResultSet rs = mock(ResultSet.class);
            assertThat(handler.readTextValue(null, rs, "COL")).isNull();
        }

        @Test
        @DisplayName("String → 그대로 반환")
        void stringValue_returnedAsIs() throws SQLException {
            ResultSet rs = mock(ResultSet.class);
            assertThat(handler.readTextValue("hello", rs, "COL")).isEqualTo("hello");
        }

        @Test
        @DisplayName("기타 객체 → rs.getString() 위임")
        void otherObject_delegatesToGetString() throws SQLException {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("COL")).thenReturn("from_rs");

            assertThat(handler.readTextValue(new Object(), rs, "COL")).isEqualTo("from_rs");
        }
    }

    // ------------------------------------------------------------------ //
    // readTextValue(ResultSet, Column) — 예외 처리
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("readTextValue(ResultSet, Column) 예외 처리")
    class ReadTextValueExceptionHandling {

        @Test
        @DisplayName("SQLException → 그대로 재전파")
        void sqlException_rethrown() throws SQLException {
            ResultSet rs = mock(ResultSet.class);
            Column col = new Column();
            col.setName("COL");
            when(rs.getObject("COL")).thenThrow(new SQLException("DB error"));

            assertThatThrownBy(() -> handler.readTextValue(rs, col))
                    .isInstanceOf(SQLException.class)
                    .hasMessage("DB error");
        }

        @Test
        @DisplayName("비-SQL Exception → SQLException으로 래핑, 컬럼명 포함")
        void nonSqlException_wrappedWithColName() throws SQLException {
            ResultSet rs = mock(ResultSet.class);
            Column col = new Column();
            col.setName("MY_COL");
            when(rs.getObject("MY_COL")).thenThrow(new RuntimeException("unexpected"));

            assertThatThrownBy(() -> handler.readTextValue(rs, col))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("MY_COL");
        }
    }
}
