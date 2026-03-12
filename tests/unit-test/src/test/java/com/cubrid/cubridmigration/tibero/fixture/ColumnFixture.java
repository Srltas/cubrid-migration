package com.cubrid.cubridmigration.tibero.fixture;

import com.cubrid.cubridmigration.core.dbobject.Column;

/**
 * 테스트용 Column 객체 팩토리.
 *
 * <p>사용법: {@code import static com.cubrid.cubridmigration.tibero.fixture.ColumnFixture.*;}
 */
public final class ColumnFixture {

    private ColumnFixture() {}

    public static Column createColumn(String dataType) {
        return createColumn("TEST_COL", dataType, null, null);
    }

    public static Column createColumn(String dataType, Integer precision, Integer scale) {
        return createColumn("TEST_COL", dataType, precision, scale);
    }

    public static Column createColumn(
            String name, String dataType, Integer precision, Integer scale) {
        Column col = new Column();
        col.setName(name);
        col.setDataType(dataType);
        col.setPrecision(precision);
        col.setScale(scale);
        return col;
    }

    public static Column createColumnWithDefault(String dataType, String defaultValue) {
        Column col = createColumn(dataType);
        col.setDefaultValue(defaultValue);
        return col;
    }

    /** charUsed: "C" → CHAR semantics, "B" → BYTE semantics */
    public static Column createCharColumn(String dataType, Integer precision, String charUsed) {
        Column col = createColumn(dataType, precision, null);
        col.setCharUsed(charUsed);
        return col;
    }
}
