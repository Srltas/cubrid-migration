package com.cubrid.cubridmigration.tibero;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TiberoSQLHelper")
class TiberoSQLHelperTest {

    private TiberoSQLHelper helper;

    @BeforeEach
    void setUp() {
        helper = TiberoSQLHelper.getInstance(null);
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
            assertThat(TiberoSQLHelper.getInstance(null))
                    .isSameAs(TiberoSQLHelper.getInstance("7.0"));
        }
    }

    // ------------------------------------------------------------------ //
    // getTestSelectSQL()
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getTestSelectSQL()")
    class GetTestSelectSQL {

        @Test
        @DisplayName("SELECT * FROM T → 래핑 후 WHERE 1<>1")
        void simpleSelect_wrapsWithWhereClause() {
            String sql = "SELECT * FROM EMP";

            String result = helper.getTestSelectSQL(sql);

            assertThat(result).isEqualTo("SELECT * FROM ( SELECT * FROM EMP ) WHERE 1<>1");
        }

        @Test
        @DisplayName("결과에 원본 SQL이 포함됨")
        void result_containsOriginalSql() {
            String sql = "SELECT ID, NAME FROM ACCOUNTS WHERE STATUS = 'ACTIVE'";

            String result = helper.getTestSelectSQL(sql);

            assertThat(result).contains(sql);
            assertThat(result).startsWith("SELECT * FROM (");
            assertThat(result).endsWith("WHERE 1<>1");
        }

        @Test
        @DisplayName("결과는 0건 반환 쿼리 형태")
        void result_hasZeroRowCondition() {
            String result = helper.getTestSelectSQL("SELECT 1 FROM DUAL");

            assertThat(result).contains("WHERE 1<>1");
        }
    }

    // ------------------------------------------------------------------ //
    // getQuotedObjName()
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getQuotedObjName()")
    class GetQuotedObjName {

        @Test
        @DisplayName("일반 테이블명 → 쌍따옴표로 감싸기")
        void tableName_wrapsInDoubleQuotes() {
            assertThat(helper.getQuotedObjName("MY_TABLE"))
                    .isEqualTo("\"MY_TABLE\"");
        }

        @Test
        @DisplayName("소문자 객체명 → 그대로 감싸기 (대소문자 변환 없음)")
        void lowercase_wrapsWithoutCaseChange() {
            assertThat(helper.getQuotedObjName("my_view"))
                    .isEqualTo("\"my_view\"");
        }

        @Test
        @DisplayName("예약어 객체명 → 쌍따옴표로 안전하게 처리")
        void reservedWord_wrapsInDoubleQuotes() {
            assertThat(helper.getQuotedObjName("SELECT"))
                    .isEqualTo("\"SELECT\"");
        }

        @Test
        @DisplayName("빈 문자열 → 쌍따옴표 두 개")
        void emptyString_returnsEmptyQuotes() {
            assertThat(helper.getQuotedObjName(""))
                    .isEqualTo("\"\"");
        }
    }
}
