package com.cubrid.cubridmigration.tibero;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("TiberoDataTypeHelper")
class TiberoDataTypeHelperTest {

    // ------------------------------------------------------------------ //
    // getTiberoDataTypeKey()
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getTiberoDataTypeKey()")
    class GetTiberoDataTypeKey {

        @Test
        @DisplayName("null → 빈 문자열")
        void null_returnsEmptyString() {
            assertThat(TiberoDataTypeHelper.getTiberoDataTypeKey(null)).isEmpty();
        }

        @Test
        @DisplayName("빈 문자열 → 빈 문자열")
        void emptyString_returnsEmptyString() {
            assertThat(TiberoDataTypeHelper.getTiberoDataTypeKey("")).isEmpty();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" → \"{1}\"")
        @CsvSource({
            // 패턴 없는 타입: 그대로 반환 (대문자 정규화)
            "VARCHAR2,             VARCHAR2",
            "NUMBER,               NUMBER",
            "CLOB,                 CLOB",
            "BLOB,                 BLOB",
            "CHAR,                 CHAR",
            "NCHAR,                NCHAR",
            "NVARCHAR2,            NVARCHAR2",
            "DATE,                 DATE",
            "ROWID,                ROWID",
            "RAW,                  RAW",
            "LONG,                 LONG",
            "LONG RAW,             LONG RAW",
            "BINARY_FLOAT,         BINARY_FLOAT",
            "BINARY_DOUBLE,        BINARY_DOUBLE",

            // TIMESTAMP 패턴: precision 제거
            "TIMESTAMP,            TIMESTAMP",
            "TIMESTAMP(0),         TIMESTAMP",
            "TIMESTAMP(6),         TIMESTAMP",
            "TIMESTAMP(9),         TIMESTAMP",

            // TIME 패턴: precision 제거
            "TIME,                 TIME",
            "TIME(0),              TIME",
            "TIME(6),              TIME",

            // TIMESTAMP WITH TIME ZONE 패턴
            "TIMESTAMP WITH TIME ZONE,              TIMESTAMP WITH TIME ZONE",
            "TIMESTAMP(6) WITH TIME ZONE,           TIMESTAMP WITH TIME ZONE",
            "TIMESTAMP(0) WITH TIME ZONE,           TIMESTAMP WITH TIME ZONE",

            // TIMESTAMP WITH LOCAL TIME ZONE 패턴
            "TIMESTAMP WITH LOCAL TIME ZONE,        TIMESTAMP WITH LOCAL TIME ZONE",
            "TIMESTAMP(6) WITH LOCAL TIME ZONE,     TIMESTAMP WITH LOCAL TIME ZONE",
            "TIMESTAMP(3) WITH LOCAL TIME ZONE,     TIMESTAMP WITH LOCAL TIME ZONE",

            // INTERVAL DAY TO SECOND 패턴
            "INTERVAL DAY TO SECOND,                INTERVAL DAY TO SECOND",
            "INTERVAL DAY(2) TO SECOND(6),          INTERVAL DAY TO SECOND",
            "INTERVAL DAY(5) TO SECOND(9),          INTERVAL DAY TO SECOND",
            "INTERVAL DAY(0) TO SECOND(0),          INTERVAL DAY TO SECOND",

            // INTERVAL YEAR TO MONTH 패턴
            "INTERVAL YEAR TO MONTH,                INTERVAL YEAR TO MONTH",
            "INTERVAL YEAR(4) TO MONTH,             INTERVAL YEAR TO MONTH",
            "INTERVAL YEAR(2) TO MONTH,             INTERVAL YEAR TO MONTH",

            // 특수 처리 타입
            "JSON,                 JSON",
            "XMLTYPE,              XMLTYPE",
        })
        void variousTypes_returnsNormalizedKey(String input, String expected) {
            assertThat(TiberoDataTypeHelper.getTiberoDataTypeKey(input.trim()))
                    .isEqualTo(expected.trim());
        }

        @Test
        @DisplayName("소문자 입력 → 대문자 정규화")
        void lowercaseInput_returnsUppercase() {
            assertThat(TiberoDataTypeHelper.getTiberoDataTypeKey("varchar2"))
                    .isEqualTo("VARCHAR2");
        }

        @Test
        @DisplayName("중간 공백 여러 개 → 단일 공백으로 정규화")
        void extraWhitespace_normalizedToSingleSpace() {
            assertThat(TiberoDataTypeHelper.getTiberoDataTypeKey("TIMESTAMP  WITH  TIME  ZONE"))
                    .isEqualTo("TIMESTAMP WITH TIME ZONE");
        }
    }

    // ------------------------------------------------------------------ //
    // isBinary()
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("isBinary()")
    class IsBinary {

        @Test
        @DisplayName("blob (소문자) → true")
        void blob_lowercase_returnsTrue() {
            assertThat(TiberoDataTypeHelper.getInstance(null).isBinary("blob")).isTrue();
        }

        @Test
        @DisplayName("BLOB (대문자) → true")
        void blob_uppercase_returnsTrue() {
            assertThat(TiberoDataTypeHelper.getInstance(null).isBinary("BLOB")).isTrue();
        }

        @Test
        @DisplayName("CLOB → false (텍스트 타입)")
        void clob_returnsFalse() {
            assertThat(TiberoDataTypeHelper.getInstance(null).isBinary("CLOB")).isFalse();
        }

        @Test
        @DisplayName("RAW → false (바이너리지만 Tibero에서는 false)")
        void raw_returnsFalse() {
            assertThat(TiberoDataTypeHelper.getInstance(null).isBinary("RAW")).isFalse();
        }
    }

    // ------------------------------------------------------------------ //
    // isCollection()
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("isCollection()")
    class IsCollection {

        @Test
        @DisplayName("Tibero는 컬렉션 타입 미지원 → 항상 false")
        void anyType_alwaysReturnsFalse() {
            TiberoDataTypeHelper helper = TiberoDataTypeHelper.getInstance(null);
            assertThat(helper.isCollection("VARCHAR2")).isFalse();
            assertThat(helper.isCollection("NUMBER")).isFalse();
            assertThat(helper.isCollection("CLOB")).isFalse();
        }
    }

    // ------------------------------------------------------------------ //
    // getInstance()
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getInstance()")
    class GetInstance {

        @Test
        @DisplayName("싱글턴: 동일 인스턴스 반환")
        void singleton_returnsSameInstance() {
            assertThat(TiberoDataTypeHelper.getInstance(null))
                    .isSameAs(TiberoDataTypeHelper.getInstance("1.0"));
        }
    }
}
