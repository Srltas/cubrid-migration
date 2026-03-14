package com.cubrid.cubridmigration.tibero.trans;

import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.PartitionInfo;
import com.cubrid.cubridmigration.core.dbobject.PartitionTable;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.View;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.cubrid.trans.ToCUBRIDDataConverterFacade;

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

import static com.cubrid.cubridmigration.testutil.TestColumnFactory.createColumn;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tiber2CUBRIDTransformHelper")
public class Tibero2CUBRIDTransformHelperTest {

    private static final Tibero2CUBRIDTransformHelper HELPER =
            new Tibero2CUBRIDTransformHelper(
                    new TiberoDataTypeMappingHelper(),
                    ToCUBRIDDataConverterFacade.getIntance());

    @Nested
    @DisplayName("adjustDefaultValue()")
    class AdjustDefaultValue {

        @Test
        @DisplayName("null default -> no change")
        void nullDefault_noChange() {
            Column src = createColumn("DATE");
            Column cub = createColumn("DATETIME");

            HELPER.adjustDefaultValue(src, cub);

            assertThat(cub.getDefaultValue()).isNull();
            assertThat(cub.isDefaultIsExpression()).isFalse();
        }

        @ParameterizedTest(name = "[{index}] {0} column | {1} -> {3}")
        @CsvSource({
            "DATE,      SYSDATE,      DATETIME, SYS_DATETIME",
            "DATE,      SYSTIME,      DATETIME, SYS_TIME",
            "TIMESTAMP, SYSTIMESTAMP, DATETIME, SYS_TIMESTAMP",
            "DATE,      CURRENT_DATE, DATETIME, CURRENT_DATETIME",
        })
        @DisplayName("datetime column Tibero date function -> CUBRID function")
        void datetimeColumn_dateTimeFunction_converted(
                String srcType, String tiberoFn, String cubType, String cubridFn) {
            Column src = createColumn(srcType);
            src.setDefaultValue(tiberoFn);
            Column cub = createColumn(cubType);

            HELPER.adjustDefaultValue(src, cub);

            assertThat(cub.getDefaultValue()).isEqualTo(cubridFn);
            assertThat(cub.isDefaultIsExpression()).isFalse();
        }

        @ParameterizedTest(name = "[{index}] {0} column | {1} -> {3}")
        @CsvSource({
            "DATE, sysdate, DATETIME, SYS_DATETIME",
        })
        @DisplayName("datetime column date function is converted case-insensitively")
        void datetimeColumn_dateTimeFunctionConvertedCaseInsensitively(
                String srcType, String tiberoFn, String cubType, String cubridFn) {
            Column src = createColumn(srcType);
            src.setDefaultValue(tiberoFn);
            Column cub = createColumn(cubType);

            HELPER.adjustDefaultValue(src, cub);

            assertThat(cub.getDefaultValue()).isEqualTo(cubridFn);
            assertThat(cub.isDefaultIsExpression()).isFalse();
        }

        @ParameterizedTest(name = "[{index}] {0} column | {1} remains as is")
        @CsvSource({
            "TIMESTAMP, LOCALTIMESTAMP,   DATETIME, LOCALTIMESTAMP",
            "DATE,      CURRENT_TIMESTAMP, DATETIME, CURRENT_TIMESTAMP",
        })
        @DisplayName("datetime column recognized date function without mapping remains unchanged")
        void datetimeColumn_recognizedDateFunctionWithoutMapping_remainsUnchanged(
                String srcType, String tiberoFn, String cubType, String expected) {
            Column src = createColumn(srcType);
            src.setDefaultValue(tiberoFn);
            Column cub = createColumn(cubType);

            HELPER.adjustDefaultValue(src, cub);

            assertThat(cub.getDefaultValue()).isEqualTo(expected);
            assertThat(cub.isDefaultIsExpression()).isFalse();
        }

        @ParameterizedTest(name = "[{index}] {0} column | {1} -> {2}")
        @CsvSource({
            "DATE,      SYSDATE, true",
            "TIMESTAMP, SYSDATE, true",
            "VARCHAR2,  SYSDATE, false",
        })
        @DisplayName("date function conversion depends on source column type")
        void dateFunctionConversion_dependsOnSourceColumnType(
                String srcType, String tiberoFn, boolean converted) {
            Column src = createColumn(srcType);
            src.setDefaultValue(tiberoFn);
            Column cub = createColumn("DATETIME");

            HELPER.adjustDefaultValue(src, cub);

            if (converted) {
                assertThat(cub.getDefaultValue()).isEqualTo("SYS_DATETIME");
            } else {
                assertThat(cub.getDefaultValue()).isNull();
            }
            assertThat(cub.isDefaultIsExpression()).isFalse();
        }

        @ParameterizedTest(name = "[{index}] expression \"{0}\" -> isExpression=true")
        @ValueSource(strings = {
                "(SELECT 1 FROM DUAL)",
                "TO_CHAR(SYSDATE,'YYYY-MM-DD')",
                "CAST('2024-01-01' AS DATE)",
        })
        @DisplayName("expression default -> set isDefaultIsExpression=true")
        void expressionDefault_markedAsExpression(String expr) {
            Column src = createColumn("VARCHAR2");
            src.setDefaultValue(expr);
            Column cub = createColumn("VARCHAR");

            HELPER.adjustDefaultValue(src, cub);

            assertThat(cub.isDefaultIsExpression()).isTrue();
        }

        @ParameterizedTest(name = "[{index}] DATETIME column | TO_DATE/TO_TIMESTAMP conversion")
        @MethodSource("com.cubrid.cubridmigration.tibero.trans.Tibero2CUBRIDTransformHelperTest#toDateConversionCases")
        @DisplayName("DATETIME type converts TO_DATE/TO_TIMESTAMP to TO_DATETIME")
        void dateTimeColumn_toDateConverted(String input, String expected) {
            Column src = createColumn("DATE");
            src.setDefaultValue(input);
            Column cub = createColumn("DATETIME");

            HELPER.adjustDefaultValue(src, cub);

            assertThat(cub.getDefaultValue()).isEqualTo(expected);
        }

        @Test
        @DisplayName("non-date literal default -> cub stays unchanged")
        void nonDateLiteralDefault_cubridColumnUnchanged() {
            Column src = createColumn("NUMBER");
            src.setDefaultValue("42");
            Column cub = createColumn("INT");

            HELPER.adjustDefaultValue(src, cub);

            assertThat(cub.getDefaultValue()).isNull();
            assertThat(cub.isDefaultIsExpression()).isFalse();
        }

    }

    @Nested
    @DisplayName("getColumnView()")
    class GetColunView {

        private final MigrationConfiguration config = new MigrationConfiguration();

        @Test
        @DisplayName("removes uppercase WITH READ ONLY")
        void uppercase_withReadOnly_removed() {
            View view = new View();
            view.setName("V_EMP");
            view.setQuerySpec("SELECT * FROM EMP WITH READ ONLY");

            View result = HELPER.getCloneView(view, config);

            assertThat(result.getQuerySpec()).doesNotContainIgnoringCase("with read only");
            assertThat(result.getQuerySpec()).contains("SELECT * FROM EMP");
        }

        @Test
        @DisplayName("removes lowercase with read only")
        void lowercase_withReadOnly_removed() {
            View view = new View();
            view.setName("V_EMP");
            view.setQuerySpec("SELECT * FROM EMP with read only");

            View result = HELPER.getCloneView(view, config);

            assertThat(result.getQuerySpec()).doesNotContainIgnoringCase("with read only");
        }

        @Test
        @DisplayName("view without WITH READ ONLY -> keep querySpec")
        void noWithReadOnly_querySpecUnchanged() {
            View view = new View();
            view.setName("V_EMP");
            view.setQuerySpec("SELECT ID, NAME FROM EMP WHERE ACTIVE = 1");

            View result = HELPER.getCloneView(view, config);

            assertThat(result.getQuerySpec()).isEqualTo("SELECT ID, NAME FROM EMP WHERE ACTIVE = 1");
        }
    }

    @Nested
    @DisplayName("getToCUBRIDPartitionDDL()")
    class GetToCUBRIDPartitionDDL {

        @Test
        @DisplayName("table=null -> null")
        void nullTable_returnNull() {
            assertThat(HELPER.getToCUBRIDPartitionDDL(null)).isNull();
        }

        @Test
        @DisplayName("partitionInfo=null -> null")
        void nullPartitionInfo_returnNull() {
            assertThat(HELPER.getToCUBRIDPartitionDDL(new Table())).isNull();
        }

        @Test
        @DisplayName("partitionColumnCount=0 -> return source DDL")
        void zeroPartitionColumns_returnsSrcDDL() {
            Table table = new Table();
            PartitionInfo info = new PartitionInfo();
            info.setDDL("ORIGINAL_DDL");
            info.setPartitionColumnCount(0);
            info.setPartitionCount(2);
            table.setPartitionInfo(info);

            assertThat(HELPER.getToCUBRIDPartitionDDL(table)).isEqualTo("ORIGINAL_DDL");
        }

        @Test
        @DisplayName("HASH partition -> PARTITION BY HASH(COL) PARTITION N")
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

            String ddl = HELPER.getToCUBRIDPartitionDDL(table);

            assertThat(ddl).contains("PARTITION BY");
            assertThat(ddl).contains("HASH");
            assertThat(ddl).contains("(ID)");
            assertThat(ddl).contains("PARTITIONS 4");
        }

        @Test
        @DisplayName("RANGE partition -> handles VALUES LESS THAN and MAXVALUE")
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

            String ddl = HELPER.getToCUBRIDPartitionDDL(table);

            assertThat(ddl).contains("PARTITION BY");
            assertThat(ddl).contains("RANGE");
            assertThat(ddl).contains("PARTITION P_LOW VALUES LESS THAN (1000)");
            assertThat(ddl).contains("PARTITION P_HIGH VALUES LESS THAN MAXVALUE");
        }

        @Test
        @DisplayName("LIST partition -> VALUES IN format")
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

            String ddl = HELPER.getToCUBRIDPartitionDDL(table);

            assertThat(ddl).contains("LIST");
            assertThat(ddl).contains("PARTITION P_SEOUL VALUES IN ('SEOUL','BUSAN')");
        }

        @Test
        @DisplayName("unknown partition method -> return source DDL")
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

            assertThat(HELPER.getToCUBRIDPartitionDDL(table)).isEqualTo("ORIGINAL_DDL");
        }
    }

    static Stream<Arguments> toDateConversionCases() {
        return Stream.of(
                Arguments.of("TO_DATE('2024-01-01','YYYY-MM-DD')",
                        "TO_DATETIME('2024-01-01','YYYY-MM-DD')"),
                Arguments.of("TO_TIMESTAMP('2024-01-01','YYYY-MM-DD')",
                        "TO_DATETIME('2024-01-01','YYYY-MM-DD')")
                );
    }
}
