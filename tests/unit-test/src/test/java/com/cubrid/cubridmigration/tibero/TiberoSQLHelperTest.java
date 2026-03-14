package com.cubrid.cubridmigration.tibero;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TiberoSQLHelper")
public class TiberoSQLHelperTest {

    private static final TiberoSQLHelper HELPER = TiberoSQLHelper.getInstance(null);

    @Nested
    @DisplayName("getTestSelectSQL()")
    class GetTestSelectSQL {

        @Test
        @DisplayName("SELECT * FROM T -> wrapped with WHERE 1<>1")
        void simpleSelect_wrapsWithWhereClause() {
            String sql = "SELECT * FROM EMP";

            String result = HELPER.getTestSelectSQL(sql);

            assertThat(result).isEqualTo("SELECT * FROM ( SELECT * FROM EMP ) WHERE 1<>1");
        }

        @Test
        @DisplayName("result contains original SQL")
        void result_containsOriginalSql() {
            String sql = "SELECT ID, NAME FROM ACCOUNTS WHERE STATUS = 'ACTIVE'";

            String result = HELPER.getTestSelectSQL(sql);

            assertThat(result).contains(sql);
            assertThat(result).startsWith("SELECT * FROM (");
            assertThat(result).endsWith("WHERE 1<>1");
        }

        @Test
        @DisplayName("result is a zero-row query")
        void result_hasZeroRowCondition() {
            String result = HELPER.getTestSelectSQL("SELECT 1 FROM DUAL");

            assertThat(result).contains("WHERE 1<>1");
        }
    }

    @Nested
    @DisplayName("getQuotedObjName()")
    class GetQuotedObjName {

        @Test
        @DisplayName("normal table name -> double quotes")
        void tableName_wrapsInDoubleQoutes() {
            assertThat(HELPER.getQuotedObjName("MY_TABLE"))
                    .isEqualTo("\"MY_TABLE\"");
        }

        @Test
        @DisplayName("lowercase object name -> keep case")
        void lowercase_wrapsWithoutCaseChange() {
            assertThat(HELPER.getQuotedObjName("my_view"))
                    .isEqualTo("\"my_view\"");
        }

        @Test
        @DisplayName("reserved word -> double quotes")
        void reservedWord_wrapsInDoubleQuotes() {
            assertThat(HELPER.getQuotedObjName("SELECT"))
                    .isEqualTo("\"SELECT\"");
        }

        @Test
        @DisplayName("empty string -> two double quotes")
        void emptyString_returnsEmptyQuotes() {
            assertThat(HELPER.getQuotedObjName(""))
                    .isEqualTo("\"\"");
        }
    }

    @Nested
    @DisplayName("getInstance()")
    class GetInstance {

        @Test
        @DisplayName("singleton returns same instance")
        void singleton_returnsSameInstance() {
            assertThat(TiberoSQLHelper.getInstance(null))
                    .isSameAs(TiberoSQLHelper.getInstance("7.0"));
        }
    }
}
