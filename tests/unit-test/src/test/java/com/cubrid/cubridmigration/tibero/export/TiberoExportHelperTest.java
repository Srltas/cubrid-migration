package com.cubrid.cubridmigration.tibero.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("TiberoExportHelper")
class TiberoExportHelperTest {

    private TiberoExportHelper helper;

    @BeforeEach
    void setUp() {
        helper = new TiberoExportHelper();
    }

    // ------------------------------------------------------------------ //
    // getDBType()
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getDBType()")
    class GetDBType {

        @Test
        @DisplayName("DatabaseType.TIBERO 반환")
        void returnsTypeTibero() {
            assertThat(helper.getDBType()).isEqualTo(DatabaseType.TIBERO);
        }
    }

    // ------------------------------------------------------------------ //
    // getPagedSelectSQL()
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getPagedSelectSQL()")
    class GetPagedSelectSQL {

        @Test
        @DisplayName("첫 페이지 (exportedRecords=0, rows=100) → ROWNUM <= 100, CMT_ROWNUM > 0")
        void firstPage_correctRownumBounds() {
            String sql = "SELECT * FROM EMP";
            String result = helper.getPagedSelectSQL(sql, 100, 0, null);

            assertThat(result).startsWith("SELECT * FROM (SELECT CMT_PAGED_.*, ROWNUM CMT_ROWNUM FROM (");
            assertThat(result).contains("SELECT * FROM EMP");
            assertThat(result).contains(") CMT_PAGED_ WHERE ROWNUM <= 100");
            assertThat(result).contains(") WHERE CMT_ROWNUM > 0");
        }

        @Test
        @DisplayName("두 번째 페이지 (exportedRecords=100, rows=100) → ROWNUM <= 200, CMT_ROWNUM > 100")
        void secondPage_correctRownumBounds() {
            String result = helper.getPagedSelectSQL("SELECT * FROM EMP", 100, 100, null);

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
        @DisplayName("rows/exportedRecords 조합별 endRow 계산 검증")
        void rownumBounds_variousCombinations(long rows, long exported, long expectedEnd, long expectedStart) {
            String result = helper.getPagedSelectSQL("SELECT 1 FROM DUAL", rows, exported, null);

            assertThat(result).contains("ROWNUM <= " + expectedEnd);
            assertThat(result).contains("CMT_ROWNUM > " + expectedStart);
        }

        @Test
        @DisplayName("sql 앞뒤 공백 제거")
        void sql_trimmed() {
            String result = helper.getPagedSelectSQL("  SELECT * FROM EMP  ", 10, 0, null);

            assertThat(result).contains("SELECT * FROM EMP");
        }

        @Test
        @DisplayName("pk=null이어도 동작 (pk 무시)")
        void nullPk_works() {
            String result = helper.getPagedSelectSQL("SELECT * FROM T", 10, 0, null);

            assertThat(result).isNotNull().isNotEmpty();
        }
    }
}
