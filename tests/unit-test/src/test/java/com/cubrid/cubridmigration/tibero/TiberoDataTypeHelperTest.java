package com.cubrid.cubridmigration.tibero;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TiberoDataTypeHelper")
public class TiberoDataTypeHelperTest {

    @Nested
    @DisplayName("getTiberoDataTypeKey()")
    class GetTiberoDataTypeKey {

        @Test
        @DisplayName("null -> emply String")
        void null_returnEmptyString() {
            assertThat(TiberoDataTypeHelper.getTiberoDataTypeKey(null)).isEmpty();
        }

        @Test
        @DisplayName("empty string -> empty string")
        void emptyString_returnEmptyString() {
            assertThat(TiberoDataTypeHelper.getTiberoDataTypeKey("")).isEmpty();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" → \"{1}\"")
        @CsvSource({
            // No pattern: keep as is with uppercase normalization.
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

            // TIMESTAMP: strip precision.
            "TIMESTAMP,            TIMESTAMP",
            "TIMESTAMP(0),         TIMESTAMP",
            "TIMESTAMP(6),         TIMESTAMP",
            "TIMESTAMP(9),         TIMESTAMP",

            // TIME: strip precision.
            "TIME,                 TIME",
            "TIME(0),              TIME",
            "TIME(6),              TIME",

            // TIMESTAMP WITH TIME ZONE pattern.
            "TIMESTAMP WITH TIME ZONE,              TIMESTAMP WITH TIME ZONE",
            "TIMESTAMP(6) WITH TIME ZONE,           TIMESTAMP WITH TIME ZONE",
            "TIMESTAMP(0) WITH TIME ZONE,           TIMESTAMP WITH TIME ZONE",

            // TIMESTAMP WITH LOCAL TIME ZONE pattern.
            "TIMESTAMP WITH LOCAL TIME ZONE,        TIMESTAMP WITH LOCAL TIME ZONE",
            "TIMESTAMP(6) WITH LOCAL TIME ZONE,     TIMESTAMP WITH LOCAL TIME ZONE",
            "TIMESTAMP(3) WITH LOCAL TIME ZONE,     TIMESTAMP WITH LOCAL TIME ZONE",

            // INTERVAL DAY TO SECOND pattern.
            "INTERVAL DAY TO SECOND,                INTERVAL DAY TO SECOND",
            "INTERVAL DAY(2) TO SECOND(6),          INTERVAL DAY TO SECOND",
            "INTERVAL DAY(5) TO SECOND(9),          INTERVAL DAY TO SECOND",
            "INTERVAL DAY(0) TO SECOND(0),          INTERVAL DAY TO SECOND",

            // INTERVAL YEAR TO MONTH pattern.
            "INTERVAL YEAR TO MONTH,                INTERVAL YEAR TO MONTH",
            "INTERVAL YEAR(4) TO MONTH,             INTERVAL YEAR TO MONTH",
            "INTERVAL YEAR(2) TO MONTH,             INTERVAL YEAR TO MONTH",

            // Special cases.
            "JSON,                 JSON",
            "XMLTYPE,              XMLTYPE",
        })
        void variousTypes_returnsNormalizedKey(String input, String expected) {
            assertThat(TiberoDataTypeHelper.getTiberoDataTypeKey(input.trim()))
                    .isEqualTo(expected.trim());
        }

        @Test
        @DisplayName("lowercase input -> uppercase")
        void lowercaseInput_returnUppercase() {
            assertThat(TiberoDataTypeHelper.getTiberoDataTypeKey("varchar2"))
                    .isEqualTo("VARCHAR2");
        }

        @Test
        @DisplayName("extra spaces -> single space")
        void extraWhitespace_normalizedToSingleSpace() {
            assertThat(TiberoDataTypeHelper.getTiberoDataTypeKey("TIMESTAMP  WITH  TIME  ZONE"))
                    .isEqualTo("TIMESTAMP WITH TIME ZONE");
        }
    }

    @Nested
    @DisplayName("isBinary()")
    class IsBianry {

        @Test
        @DisplayName("blob lowercase -> true")
        void blob_lowercase_returnsTrue() {
            assertThat(TiberoDataTypeHelper.getInstance(null).isBinary("blob")).isTrue();
        }

        @Test
        @DisplayName("BLOB uppercase -> true")
        void blob_uppercase_returnTrue() {
            assertThat(TiberoDataTypeHelper.getInstance(null).isBinary("BLOB")).isTrue();
        }

        @Test
        @DisplayName("CLOB -> false for text type")
        void clob_returnFalse() {
            assertThat(TiberoDataTypeHelper.getInstance(null).isBinary("CLOB")).isFalse();
        }

        @Test
        @DisplayName("RAW -> false in Tibero")
        void raw_returnsFalse() {
            assertThat(TiberoDataTypeHelper.getInstance(null).isBinary("RAW")).isFalse();
        }
    }

    @Nested
    @DisplayName("getInstance()")
    class GetInstance {

        @Test
        @DisplayName("singleton returns same instance")
        void singleton_returnSameInstance() {
            assertThat(TiberoDataTypeHelper.getInstance(null))
                    .isSameAs(TiberoDataTypeHelper.getInstance("1.0"));
        }
    }
}
