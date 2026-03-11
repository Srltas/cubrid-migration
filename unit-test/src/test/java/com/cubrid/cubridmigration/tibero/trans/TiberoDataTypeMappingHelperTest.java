package com.cubrid.cubridmigration.tibero.trans;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.mapping.AbstractDataTypeMappingHelper;
import com.cubrid.cubridmigration.core.mapping.model.MapItem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

@DisplayName("TiberoDataTypeMappingHelper")
class TiberoDataTypeMappingHelperTest {

    private TiberoDataTypeMappingHelper helper;

    @BeforeEach
    void setUp() {
        // 생성자에서 Tibero2CUBRID.xml 로드 (classpath에서)
        helper = new TiberoDataTypeMappingHelper();
    }

    // ------------------------------------------------------------------ //
    // XML 로딩 검증
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Tibero2CUBRID.xml 로딩")
    class XmlLoading {

        @Test
        @DisplayName("XML 로드 성공 → xmlConfigMap이 비어있지 않음")
        void xmlLoaded_configMapIsNotEmpty() {
            Map<String, MapItem> configMap = helper.getXmlConfigMap();

            assertThat(configMap).isNotEmpty();
        }

        @Test
        @DisplayName("NUMBER (precision 없음) 매핑 존재")
        void numberWithoutPrecision_keyExists() {
            assertThat(helper.getXmlConfigMap()).containsKey("NUMBER");
        }

        @Test
        @DisplayName("NUMBER (precision=p, scale=s) 매핑 존재")
        void numberWithPrecisionScale_keyExists() {
            // AbstractDataTypeMappingHelper.MAP_KEY_SEPARATOR = "_"
            assertThat(helper.getXmlConfigMap()).containsKey("NUMBER_p_s");
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" 키가 xmlConfigMap에 존재")
        @CsvSource({
            // XML에는 VARCHAR (Tibero의 VARCHAR2가 아닌 VARCHAR로 등록됨)
            "VARCHAR",
            "CHAR",
            "NCHAR",
            // XML에는 NVARCHAR (NVARCHAR2 아님)
            "NVARCHAR",
            "CLOB",
            "BLOB",
            "DATE",
            "TIMESTAMP",
            "TIMESTAMP WITH TIME ZONE",
            "TIMESTAMP WITH LOCAL TIME ZONE",
            "INTERVAL DAY TO SECOND",
            "INTERVAL YEAR TO MONTH",
            "JSON",
            "XMLTYPE",
            "RAW",
            // ROWID는 Tibero2CUBRID.xml에 매핑 없음 (마이그레이션 불가 타입)
            "BINARY_FLOAT",
            "BINARY_DOUBLE",
        })
        void expectedKeys_existInXmlConfig(String expectedKey) {
            assertThat(helper.getXmlConfigMap())
                    .as("Tibero2CUBRID.xml에 '%s' 키가 없음", expectedKey)
                    .containsKey(expectedKey);
        }
    }

    // ------------------------------------------------------------------ //
    // getMapKey() 로직 검증
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getMapKey()")
    class GetMapKey {

        @Nested
        @DisplayName("NUMBER 타입")
        class NumberType {

            @Test
            @DisplayName("NUMBER, precision=null → \"NUMBER\"")
            void nullPrecision_returnsNumber() {
                assertThat(helper.getMapKey("NUMBER", null, null))
                        .isEqualTo("NUMBER");
            }

            @Test
            @DisplayName("NUMBER, precision=빈문자열 → \"NUMBER\"")
            void emptyPrecision_returnsNumber() {
                assertThat(helper.getMapKey("NUMBER", "", ""))
                        .isEqualTo("NUMBER");
            }

            @Test
            @DisplayName("NUMBER, precision=\"10\" → \"NUMBER_p_s\" (숫자 precision)")
            void numericPrecision_returnsNumberPs() {
                assertThat(helper.getMapKey("NUMBER", "10", "2"))
                        .isEqualTo("NUMBER" + AbstractDataTypeMappingHelper.MAP_KEY_SEPARATOR
                                + "p" + AbstractDataTypeMappingHelper.MAP_KEY_SEPARATOR + "s");
            }

            @Test
            @DisplayName("NUMBER, precision=\"0\" → \"NUMBER_p_s\" (0도 숫자)")
            void zeroPrecision_returnsNumberPs() {
                assertThat(helper.getMapKey("NUMBER", "0", "0"))
                        .isEqualTo("NUMBER_p_s");
            }

            @Test
            @DisplayName("NUMBER, precision=\"p\" → \"NUMBER_p_s\" (템플릿 키)")
            void templatePrecision_returnsNumberPs() {
                assertThat(helper.getMapKey("NUMBER", "p", "s"))
                        .isEqualTo("NUMBER_p_s");
            }

            @Test
            @DisplayName("NUMBER, precision=\"P\" (대문자) → \"NUMBER_p_s\"")
            void templatePrecisionUppercase_returnsNumberPs() {
                assertThat(helper.getMapKey("NUMBER", "P", "S"))
                        .isEqualTo("NUMBER_p_s");
            }
        }

        @Nested
        @DisplayName("비-NUMBER 타입")
        class NonNumberTypes {

            @ParameterizedTest(name = "[{index}] getMapKey(\"{0}\", ...) → \"{1}\"")
            @CsvSource({
                "VARCHAR2,                      VARCHAR2",
                "CHAR,                          CHAR",
                "TIMESTAMP,                     TIMESTAMP",
                // TIMESTAMP(6) → getTiberoDataTypeKey → "TIMESTAMP"
                "TIMESTAMP(6),                  TIMESTAMP",
                // INTERVAL DAY TO SECOND (precision 없음) → 그대로
                "INTERVAL DAY TO SECOND,        INTERVAL DAY TO SECOND",
                // INTERVAL DAY(5) TO SECOND(9) → 정규화
                "INTERVAL DAY(5) TO SECOND(9),  INTERVAL DAY TO SECOND",
                "INTERVAL YEAR TO MONTH,        INTERVAL YEAR TO MONTH",
                "INTERVAL YEAR(4) TO MONTH,     INTERVAL YEAR TO MONTH",
                "JSON,                          JSON",
                "XMLTYPE,                       XMLTYPE",
                "CLOB,                          CLOB",
                "BLOB,                          BLOB",
            })
            void nonNumberTypes_returnsNormalizedKey(String inputType, String expectedKey) {
                assertThat(helper.getMapKey(inputType.trim(), null, null))
                        .isEqualTo(expectedKey.trim());
            }

            @Test
            @DisplayName("소문자 입력 → 대문자 정규화")
            void lowercase_returnsUppercaseKey() {
                assertThat(helper.getMapKey("varchar2", "100", null))
                        .isEqualTo("VARCHAR2");
            }

            @Test
            @DisplayName("null 타입 → 빈 문자열")
            void nullType_returnsEmptyKey() {
                assertThat(helper.getMapKey(null, null, null)).isEmpty();
            }
        }
    }
}
