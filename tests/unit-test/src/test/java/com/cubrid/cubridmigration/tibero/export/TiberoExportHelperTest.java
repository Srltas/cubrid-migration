package com.cubrid.cubridmigration.tibero.export;

import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TiberoExportHelper")
public class TiberoExportHelperTest {

    private static final TiberoExportHelper HELPER = new TiberoExportHelper();

    @Nested
    @DisplayName("getDBType()")
    class GetDBType {

        @Test
        @DisplayName("returns DatabaseType.TIBERO")
        void returnsTypeTibero() {
            assertThat(HELPER.getDBType()).isEqualTo(DatabaseType.TIBERO);
        }
    }

    @Nested
    @DisplayName("getPagedSelectSQL")
    class GetPagedSelectSQL {

        @Test
        @DisplayName("first page -> ROWNUM <= 100 and CMT_ROWNUM > 0")
        void firstPage_correctRownumBounds() {
            String sql = "SELECT * FROM EMP";
            String result = HELPER.getPagedSelectSQL(sql, 100, 0, null);

            assertThat(result).startsWith("SELECT * FROM (SELECT CMT_PAGED_.*, ROWNUM CMT_ROWNUM FROM (");
            assertThat(result).contains("SELECT * FROM EMP");
            assertThat(result).contains(") CMT_PAGED_ WHERE ROWNUM <= 100");
            assertThat(result).contains(") WHERE CMT_ROWNUM > 0");
        }

        @Test
        @DisplayName("second page -> ROWNUM <= 200 and CMT_ROWNUM > 100")
        void secondPage_correctRownumBounds() {
            String result = HELPER.getPagedSelectSQL("SLECT * FROM EMP", 100, 100, null);

            assertThat(result).contains("ROWNUM <= 200");
            assertThat(result).contains("CMT_ROWNUM > 100");
        }

        @ParameterizedTest(name = "[{index}] rows={0}, exported={1} → endRow={2}, start={3}")
        @CsvSource({
            "50,   0,   50,  0",
            "50,  50,  100, 50",
            "200,  0,  200,  0",
            "1,    0,    1,  0",
        })
        @DisplayName("checks endRow for rows/exportedRecords")
        void rownumBounds_variousCombinations(long rows, long exported, long expectedEnd, long expectedStart) {
            String result = HELPER.getPagedSelectSQL("SELECT 1 FROM DUAL", rows, exported, null);

            assertThat(result).contains("ROWNUM <= " + expectedEnd);
            assertThat(result).contains("CMT_ROWNUM > " + expectedStart);
        }

        @Test
        @DisplayName("trims sql")
        void sql_trimmed() {
            String result = HELPER.getPagedSelectSQL("  SELECT * FROM EMP  ", 10, 0, null);

            assertThat(result).contains("SELECT * FROM EMP");
        }

        @Test
        @DisplayName("works with null pk")
        void nullPk_works() {
            String result = HELPER.getPagedSelectSQL("SELECT * FROM T", 10, 0, null);

            assertThat(result).isNotNull().isNotEmpty();
        }
    }
}
