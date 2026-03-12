package com.cubrid.cubridmigration.tibero;

import static com.cubrid.cubridmigration.tibero.fixture.ColumnFixture.createCharColumn;
import static com.cubrid.cubridmigration.tibero.fixture.ColumnFixture.createColumn;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("TiberoTypeFormatter")
class TiberoTypeFormatterTest {

    // 실제 TiberoDataTypeHelper를 사용 (isString/isNString 등을 위해)
    private TiberoDataTypeHelper helper;

    @BeforeEach
    void setUp() {
        helper = TiberoDataTypeHelper.getInstance(null);
    }

    // ------------------------------------------------------------------ //
    // NUMBER 포맷
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NUMBER 타입 포맷")
    class NumberFormat {

        @Test
        @DisplayName("NUMBER (precision=null) → \"NUMBER\"")
        void numberNoPrecision_returnsNumber() {
            assertThat(TiberoTypeFormatter.format(createColumn("NUMBER"), helper))
                    .isEqualTo("NUMBER");
        }

        @Test
        @DisplayName("NUMBER (precision=0) → \"NUMBER\"")
        void numberPrecision0_returnsNumber() {
            assertThat(TiberoTypeFormatter.format(createColumn("NUMBER", 0, null), helper))
                    .isEqualTo("NUMBER");
        }

        @Test
        @DisplayName("NUMBER(10) → \"NUMBER(10,0)\" (Column.getScale()은 null→0 반환)")
        void numberPrecisionOnly_returnsNumberWithPrecision() {
            assertThat(TiberoTypeFormatter.format(createColumn("NUMBER", 10, null), helper))
                    .isEqualTo("NUMBER(10,0)");
        }

        @Test
        @DisplayName("NUMBER(10,2) → \"NUMBER(10,2)\"")
        void numberPrecisionAndScale_returnsNumberWithBoth() {
            assertThat(TiberoTypeFormatter.format(createColumn("NUMBER", 10, 2), helper))
                    .isEqualTo("NUMBER(10,2)");
        }

        @Test
        @DisplayName("NUMBER(38,15) → \"NUMBER(38,15)\" (최대 정밀도)")
        void numberMaxPrecision_returnsFormattedNumber() {
            assertThat(TiberoTypeFormatter.format(createColumn("NUMBER", 38, 15), helper))
                    .isEqualTo("NUMBER(38,15)");
        }
    }

    // ------------------------------------------------------------------ //
    // 문자열 타입 포맷 (VARCHAR2, CHAR, NCHAR, NVARCHAR2)
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("문자열 타입 포맷")
    class StringTypeFormat {

        @ParameterizedTest(name = "[{index}] {0}({1}) → \"{0}({1})\"")
        @CsvSource({
            "VARCHAR2, 100",
            "VARCHAR2, 4000",
            "CHAR,     10",
            "NCHAR,    10",
            "NVARCHAR2,200",
        })
        void stringTypesWithPrecision_returnsTypeWithPrecision(String dataType, int precision) {
            assertThat(TiberoTypeFormatter.format(createColumn(dataType, precision, null), helper))
                    .isEqualTo(dataType + "(" + precision + ")");
        }

        @Test
        @DisplayName("VARCHAR2 (precision=null) → \"VARCHAR2\" (괄호 없음)")
        void varchar2NoPrecision_returnsTypeOnly() {
            assertThat(TiberoTypeFormatter.format(createColumn("VARCHAR2"), helper))
                    .isEqualTo("VARCHAR2");
        }

        @Test
        @DisplayName("VARCHAR2 (precision=0) → \"VARCHAR2\" (괄호 없음)")
        void varchar2Precision0_returnsTypeOnly() {
            assertThat(TiberoTypeFormatter.format(createColumn("VARCHAR2", 0, null), helper))
                    .isEqualTo("VARCHAR2");
        }

        @Test
        @DisplayName("CHAR(10 CHAR) → charUsed='C'이면 CHAR 접미사")
        void charWithCharSemantics_appendsCharSuffix() {
            assertThat(TiberoTypeFormatter.format(createCharColumn("CHAR", 10, "C"), helper))
                    .isEqualTo("CHAR(10 CHAR)");
        }

        @Test
        @DisplayName("CHAR(10) → charUsed='B'이면 괄호만")
        void charWithByteSemantics_noCHARSuffix() {
            assertThat(TiberoTypeFormatter.format(createCharColumn("CHAR", 10, "B"), helper))
                    .isEqualTo("CHAR(10)");
        }
    }

    // ------------------------------------------------------------------ //
    // RAW 타입 포맷
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("RAW 타입 포맷")
    class RawTypeFormat {

        @Test
        @DisplayName("RAW(100) → \"RAW(100)\"")
        void rawWithPrecision_returnsWithPrecision() {
            assertThat(TiberoTypeFormatter.format(createColumn("RAW", 100, null), helper))
                    .isEqualTo("RAW(100)");
        }

        @Test
        @DisplayName("RAW (precision=null) → \"RAW\"")
        void rawNoPrecision_returnsTypeOnly() {
            assertThat(TiberoTypeFormatter.format(createColumn("RAW"), helper))
                    .isEqualTo("RAW");
        }
    }

    // ------------------------------------------------------------------ //
    // 패스스루 타입 (수정 없이 그대로 반환)
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("패스스루 타입 (있는 그대로 반환)")
    class PassThroughTypes {

        @ParameterizedTest(name = "[{index}] {0} → {0}")
        @CsvSource({
            "TIMESTAMP",
            "DATE",
            "CLOB",
            "BLOB",
            "XMLTYPE",
            "JSON",
            "ROWID",
            "INTERVAL DAY TO SECOND",
            "INTERVAL YEAR TO MONTH",
            "TIMESTAMP WITH TIME ZONE",
            "TIMESTAMP WITH LOCAL TIME ZONE",
            "BINARY_FLOAT",
            "BINARY_DOUBLE",
        })
        void passThroughTypes_returnedUnchanged(String dataType) {
            assertThat(TiberoTypeFormatter.format(createColumn(dataType), helper))
                    .isEqualTo(dataType);
        }
    }

    // ------------------------------------------------------------------ //
    // 엣지 케이스
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("dataType=null → 빈 문자열")
        void nullDataType_returnsEmptyString() {
            assertThat(TiberoTypeFormatter.format(createColumn(null), helper))
                    .isEmpty();
        }
    }
}
