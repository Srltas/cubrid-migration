package com.cubrid.cubridmigration.tibero.trans;

import static com.cubrid.cubridmigration.tibero.fixture.ColumnFixture.createColumn;
import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.PartitionInfo;
import com.cubrid.cubridmigration.core.dbobject.PartitionTable;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.View;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.cubrid.trans.ToCUBRIDDataConverterFacade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

@DisplayName("Tibero2CUBRIDTransformHelper")
class Tibero2CUBRIDTransformHelperTest {

    private Tibero2CUBRIDTransformHelper helper;

    @BeforeEach
    void setUp() {
        helper = new Tibero2CUBRIDTransformHelper(
                new TiberoDataTypeMappingHelper(),
                ToCUBRIDDataConverterFacade.getIntance());
    }

    // ------------------------------------------------------------------ //
    // adjustDefaultValue()
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("adjustDefaultValue()")
    class AdjustDefaultValue {

        @Test
        @DisplayName("null 기본값 → 변경 없음")
        void nullDefault_noChange() {
            Column src = createColumn("DATE");
            Column cub = createColumn("DATETIME");

            helper.adjustDefaultValue(src, cub);

            assertThat(cub.getDefaultValue()).isNull();
            assertThat(cub.isDefaultIsExpression()).isFalse();
        }

        @ParameterizedTest(name = "[{index}] DATE 컬럼 | {0} → {1}")
        @CsvSource({
            "SYSDATE,      SYS_DATETIME",
            "sysdate,      SYS_DATETIME",
            "SYSTIME,      SYS_TIME",
            "SYSTIMESTAMP, SYS_TIMESTAMP",
            "CURRENT_DATE, CURRENT_DATETIME",
        })
        @DisplayName("DATE 컬럼의 Tibero 날짜 함수 → CUBRID 함수로 변환, isExpression=false")
        void dateColumn_dateTimeFunction_converted(String tiberoFn, String cubridFn) {
            Column src = createColumn("DATE");
            src.setDefaultValue(tiberoFn);
            Column cub = createColumn("DATETIME");

            helper.adjustDefaultValue(src, cub);

            assertThat(cub.getDefaultValue()).isEqualTo(cubridFn);
            assertThat(cub.isDefaultIsExpression()).isFalse();
        }

        @ParameterizedTest(name = "[{index}] TIMESTAMP 컬럼 | {0} → {1}")
        @CsvSource({
            "SYSDATE,      SYS_DATETIME",
            "SYSTIMESTAMP, SYS_TIMESTAMP",
        })
        @DisplayName("TIMESTAMP 컬럼의 날짜 함수도 변환 적용")
        void timestampColumn_dateTimeFunctionConverted(String tiberoFn, String cubridFn) {
            Column src = createColumn("TIMESTAMP");
            src.setDefaultValue(tiberoFn);
            Column cub = createColumn("DATETIME");

            helper.adjustDefaultValue(src, cub);

            assertThat(cub.getDefaultValue()).isEqualTo(cubridFn);
        }

        @ParameterizedTest(name = "[{index}] 표현식 \"{0}\" → isExpression=true")
        @ValueSource(strings = {
            "(SELECT 1 FROM DUAL)",
            "TO_CHAR(SYSDATE,'YYYY-MM-DD')",
            "CAST('2024-01-01' AS DATE)",
        })
        @DisplayName("표현식 기본값 → isDefaultIsExpression=true 설정")
        void expressionDefault_markedAsExpression(String expr) {
            Column src = createColumn("VARCHAR2");
            src.setDefaultValue(expr);
            Column cub = createColumn("VARCHAR");

            helper.adjustDefaultValue(src, cub);

            assertThat(cub.isDefaultIsExpression()).isTrue();
        }

        @ParameterizedTest(name = "[{index}] DATETIME 컬럼 | TO_DATE/TO_TIMESTAMP 변환")
        @MethodSource("com.cubrid.cubridmigration.tibero.trans.Tibero2CUBRIDTransformHelperTest#toDateConversionCases")
        @DisplayName("DATETIME 타입에서 TO_DATE/TO_TIMESTAMP → TO_DATETIME 변환")
        void datetimeColumn_toDateConverted(String input, String expected) {
            Column src = createColumn("DATE");
            src.setDefaultValue(input);
            Column cub = createColumn("DATETIME");

            helper.adjustDefaultValue(src, cub);

            assertThat(cub.getDefaultValue()).isEqualTo(expected);
        }

        @Test
        @DisplayName("비-날짜 타입의 일반 리터럴 기본값 → cub 변경 없음")
        void nonDateLiteralDefault_cubridColumnUnchanged() {
            Column src = createColumn("NUMBER");
            src.setDefaultValue("42");
            Column cub = createColumn("INT");

            helper.adjustDefaultValue(src, cub);

            assertThat(cub.getDefaultValue()).isNull();
            assertThat(cub.isDefaultIsExpression()).isFalse();
        }

        @Test
        @DisplayName("LOCALTIMESTAMP → 날짜 함수로 인식 (변환 규칙 없으면 원본 유지)")
        void localtimestamp_recognizedAsDatetimeFunction() {
            Column src = createColumn("TIMESTAMP");
            src.setDefaultValue("LOCALTIMESTAMP");
            Column cub = createColumn("DATETIME");

            helper.adjustDefaultValue(src, cub);

            // LOCALTIMESTAMP는 switch case 없음 → defaultValue 설정됨 (원본 유지)
            assertThat(cub.getDefaultValue()).isEqualTo("LOCALTIMESTAMP");
        }
    }

    // ------------------------------------------------------------------ //
    // getCloneView()
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getCloneView()")
    class GetCloneView {

        private MigrationConfiguration config;

        @BeforeEach
        void setUp() {
            config = new MigrationConfiguration();
        }

        @Test
        @DisplayName("대문자 WITH READ ONLY 제거")
        void uppercase_withReadOnly_removed() {
            View view = new View();
            view.setName("V_EMP");
            view.setQuerySpec("SELECT * FROM EMP WITH READ ONLY");

            View result = helper.getCloneView(view, config);

            assertThat(result.getQuerySpec()).doesNotContainIgnoringCase("with read only");
            assertThat(result.getQuerySpec()).contains("SELECT * FROM EMP");
        }

        @Test
        @DisplayName("소문자 with read only 제거")
        void lowercase_withReadOnly_removed() {
            View view = new View();
            view.setName("V_EMP");
            view.setQuerySpec("SELECT * FROM EMP with read only");

            View result = helper.getCloneView(view, config);

            assertThat(result.getQuerySpec()).doesNotContainIgnoringCase("with read only");
        }

        @Test
        @DisplayName("WITH READ ONLY 없는 뷰 → querySpec 원본 유지")
        void noWithReadOnly_querySpecUnchanged() {
            View view = new View();
            view.setName("V_EMP");
            view.setQuerySpec("SELECT ID, NAME FROM EMP WHERE ACTIVE = 1");

            View result = helper.getCloneView(view, config);

            assertThat(result.getQuerySpec()).isEqualTo("SELECT ID, NAME FROM EMP WHERE ACTIVE = 1");
        }
    }

    // ------------------------------------------------------------------ //
    // getToCUBRIDPartitionDDL()
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getToCUBRIDPartitionDDL()")
    class GetToCUBRIDPartitionDDL {

        @Test
        @DisplayName("table=null → null")
        void nullTable_returnsNull() {
            assertThat(helper.getToCUBRIDPartitionDDL(null)).isNull();
        }

        @Test
        @DisplayName("partitionInfo=null → null")
        void nullPartitionInfo_returnsNull() {
            assertThat(helper.getToCUBRIDPartitionDDL(new Table())).isNull();
        }

        @Test
        @DisplayName("partitionColumnCount=0 → 원본 DDL 반환")
        void zeroPartitionColumns_returnsSrcDDL() {
            Table table = new Table();
            PartitionInfo info = new PartitionInfo();
            info.setDDL("ORIGINAL_DDL");
            info.setPartitionColumnCount(0);
            info.setPartitionCount(2);
            table.setPartitionInfo(info);

            assertThat(helper.getToCUBRIDPartitionDDL(table)).isEqualTo("ORIGINAL_DDL");
        }

        @Test
        @DisplayName("HASH 파티션 → PARTITION BY HASH(COL) PARTITIONS N")
        void hashPartition_generatesHashDDL() {
            Table table = new Table();
            PartitionInfo info = new PartitionInfo();
            info.setPartitionMethod("HASH");
            info.setPartitionColumnCount(1);
            info.setPartitionCount(4);
            Column col = new Column();
            col.setName("ID");
            info.setPartitionColumns(List.of(col));
            info.setPartitions(List.of());
            table.setPartitionInfo(info);

            String ddl = helper.getToCUBRIDPartitionDDL(table);

            assertThat(ddl).contains("PARTITION BY");
            assertThat(ddl).contains("HASH");
            assertThat(ddl).contains("(ID)");
            assertThat(ddl).contains("PARTITIONS 4");
        }

        @Test
        @DisplayName("RANGE 파티션 → VALUES LESS THAN, MAXVALUE 처리")
        void rangePartition_generatesRangeDDL() {
            Table table = new Table();
            PartitionInfo info = new PartitionInfo();
            info.setPartitionMethod("RANGE");
            info.setPartitionColumnCount(1);
            info.setPartitionCount(2);
            Column col = new Column();
            col.setName("SALARY");
            info.setPartitionColumns(List.of(col));
            PartitionTable p1 = new PartitionTable();
            p1.setPartitionName("P_LOW");
            p1.setPartitionDesc("1000");
            PartitionTable p2 = new PartitionTable();
            p2.setPartitionName("P_HIGH");
            p2.setPartitionDesc("MAXVALUE");
            info.setPartitions(List.of(p1, p2));
            table.setPartitionInfo(info);

            String ddl = helper.getToCUBRIDPartitionDDL(table);

            assertThat(ddl).contains("PARTITION BY");
            assertThat(ddl).contains("RANGE");
            assertThat(ddl).contains("PARTITION P_LOW VALUES LESS THAN (1000)");
            assertThat(ddl).contains("PARTITION P_HIGH VALUES LESS THAN MAXVALUE");
        }

        @Test
        @DisplayName("LIST 파티션 → VALUES IN 형식")
        void listPartition_generatesListDDL() {
            Table table = new Table();
            PartitionInfo info = new PartitionInfo();
            info.setPartitionMethod("LIST");
            info.setPartitionColumnCount(1);
            info.setPartitionCount(1);
            Column col = new Column();
            col.setName("REGION");
            info.setPartitionColumns(List.of(col));
            PartitionTable p1 = new PartitionTable();
            p1.setPartitionName("P_SEOUL");
            p1.setPartitionDesc("'SEOUL','BUSAN'");
            info.setPartitions(List.of(p1));
            table.setPartitionInfo(info);

            String ddl = helper.getToCUBRIDPartitionDDL(table);

            assertThat(ddl).contains("LIST");
            assertThat(ddl).contains("PARTITION P_SEOUL VALUES IN ('SEOUL','BUSAN')");
        }

        @Test
        @DisplayName("알 수 없는 파티션 방식 → 원본 DDL 반환")
        void unknownPartitionMethod_returnsSrcDDL() {
            Table table = new Table();
            PartitionInfo info = new PartitionInfo();
            info.setDDL("ORIGINAL_DDL");
            info.setPartitionMethod("UNKNOWN");
            info.setPartitionColumnCount(1);
            info.setPartitionCount(1);
            Column col = new Column();
            col.setName("ID");
            info.setPartitionColumns(List.of(col));
            info.setPartitions(List.of());
            table.setPartitionInfo(info);

            assertThat(helper.getToCUBRIDPartitionDDL(table)).isEqualTo("ORIGINAL_DDL");
        }
    }

    // ------------------------------------------------------------------ //
    // @MethodSource data providers
    // ------------------------------------------------------------------ //

    static Stream<Arguments> toDateConversionCases() {
        return Stream.of(
                Arguments.of("TO_DATE('2024-01-01','YYYY-MM-DD')",
                             "TO_DATETIME('2024-01-01','YYYY-MM-DD')"),
                Arguments.of("TO_TIMESTAMP('2024-01-01','YYYY-MM-DD')",
                             "TO_DATETIME('2024-01-01','YYYY-MM-DD')")
        );
    }
}
