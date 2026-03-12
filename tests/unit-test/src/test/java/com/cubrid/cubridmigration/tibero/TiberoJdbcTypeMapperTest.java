package com.cubrid.cubridmigration.tibero;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Types;
import java.util.stream.Stream;

@DisplayName("TiberoJdbcTypeMapper")
class TiberoJdbcTypeMapperTest {

    // ------------------------------------------------------------------ //
    // isUnsupportedJdbcType()
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("isUnsupportedJdbcType()")
    class IsUnsupportedJdbcType {

        @Test
        @DisplayName("ROWID → true (마이그레이션 불가 타입)")
        void rowid_returnsTrue() {
            assertThat(TiberoJdbcTypeMapper.isUnsupportedJdbcType("ROWID")).isTrue();
        }

        @Test
        @DisplayName("rowid (소문자) → true (대소문자 무관)")
        void rowid_lowercase_returnsTrue() {
            assertThat(TiberoJdbcTypeMapper.isUnsupportedJdbcType("rowid")).isTrue();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" → false (지원 타입)")
        @ValueSource(strings = {"VARCHAR2", "NUMBER", "CLOB", "BLOB", "DATE", "TIMESTAMP",
                "BINARY_FLOAT", "BINARY_DOUBLE", "CHAR", "NCHAR"})
        void supportedTypes_returnsFalse(String dataType) {
            assertThat(TiberoJdbcTypeMapper.isUnsupportedJdbcType(dataType)).isFalse();
        }
    }

    // ------------------------------------------------------------------ //
    // getFixedJdbcTypeId()
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getFixedJdbcTypeId()")
    class GetFixedJdbcTypeId {

        @Test
        @DisplayName("BINARY_FLOAT → Types.FLOAT")
        void binaryFloat_returnsFloat() {
            assertThat(TiberoJdbcTypeMapper.getFixedJdbcTypeId("BINARY_FLOAT"))
                    .isEqualTo(Types.FLOAT);
        }

        @Test
        @DisplayName("BINARY_DOUBLE → Types.DOUBLE")
        void binaryDouble_returnsDouble() {
            assertThat(TiberoJdbcTypeMapper.getFixedJdbcTypeId("BINARY_DOUBLE"))
                    .isEqualTo(Types.DOUBLE);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" → null (고정 타입 아님)")
        @ValueSource(strings = {"VARCHAR2", "NUMBER", "CLOB", "BLOB", "DATE", "TIMESTAMP", "ROWID"})
        void nonFixedTypes_returnsNull(String dataType) {
            assertThat(TiberoJdbcTypeMapper.getFixedJdbcTypeId(dataType)).isNull();
        }
    }

    // ------------------------------------------------------------------ //
    // getNumberType()
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getNumberType()")
    class GetNumberType {

        @Test
        @DisplayName("precision=null, scale=null → NUMERIC (정밀도 미지정)")
        void nullPrecisionNullScale_returnsNumeric() {
            assertThat(TiberoJdbcTypeMapper.getNumberType(null, null))
                    .isEqualTo(Types.NUMERIC);
        }

        @Test
        @DisplayName("precision=null, scale=0 → BIGINT")
        void nullPrecisionScale0_returnsBigint() {
            assertThat(TiberoJdbcTypeMapper.getNumberType(null, 0))
                    .isEqualTo(Types.BIGINT);
        }

        @ParameterizedTest(name = "[{index}] NUMBER({0},{1}) → JDBC {2}")
        @MethodSource("com.cubrid.cubridmigration.tibero.TiberoJdbcTypeMapperTest#precisionScaleToJdbcType")
        void precisionScale_mapsToCorrectJdbcType(
                Integer precision, Integer scale, int expectedJdbcType) {
            assertThat(TiberoJdbcTypeMapper.getNumberType(precision, scale))
                    .isEqualTo(expectedJdbcType);
        }

        @Test
        @DisplayName("정수 타입에 scale 있으면 → NUMERIC")
        void integerPrecisionWithScale_returnsNumeric() {
            assertThat(TiberoJdbcTypeMapper.getNumberType(5, 2)).isEqualTo(Types.NUMERIC);
        }
    }

    static Stream<Arguments> precisionScaleToJdbcType() {
        return Stream.of(
                // precision=1 → BIT
                Arguments.of(1, 0, Types.BIT),
                Arguments.of(1, null, Types.BIT),

                // precision=3 → TINYINT
                Arguments.of(3, 0, Types.TINYINT),
                Arguments.of(3, null, Types.TINYINT),

                // precision=5 → SMALLINT
                Arguments.of(5, 0, Types.SMALLINT),
                Arguments.of(5, null, Types.SMALLINT),

                // precision 2,4,6~10 → INTEGER (명시적 매핑 없음 → <=10)
                Arguments.of(2, 0, Types.INTEGER),
                Arguments.of(4, 0, Types.INTEGER),
                Arguments.of(6, 0, Types.INTEGER),
                Arguments.of(9, 0, Types.INTEGER),
                Arguments.of(10, 0, Types.INTEGER),

                // precision 11~38 → BIGINT
                Arguments.of(11, 0, Types.BIGINT),
                Arguments.of(18, 0, Types.BIGINT),
                Arguments.of(38, 0, Types.BIGINT),

                // precision > 38 → NUMERIC (범위 초과)
                Arguments.of(39, 0, Types.NUMERIC),

                // scale 있으면 → NUMERIC
                Arguments.of(10, 2, Types.NUMERIC),
                Arguments.of(38, 15, Types.NUMERIC)
        );
    }
}
