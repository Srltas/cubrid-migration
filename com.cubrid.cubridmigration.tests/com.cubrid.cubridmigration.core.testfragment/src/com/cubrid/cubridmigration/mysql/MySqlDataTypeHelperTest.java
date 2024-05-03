/*
 * Copyright (C) 2008 Search Solution Corporation.
 * Copyright (C) 2016 CUBRID Corporation.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * - Neither the name of the <ORGANIZATION> nor the names of its contributors
 *   may be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 *
 */
package com.cubrid.cubridmigration.mysql;

import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.template.TemplateParserTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * MySQLDataTypeTest
 *
 * @author JessieHuang
 * @version 1.0 - 2009-11-5
 */
public class MySqlDataTypeHelperTest {
    MySQLDataTypeHelper helper = MySQLDataTypeHelper.getInstance(null);

    private String getDataTypeStr(String colType, Integer precision, Integer scale) {

        Column column = new Column();
        column.setDataType(colType);
        column.setPrecision(precision);
        column.setScale(scale);
        return helper.getShownDataType(column);
    }

    /** testMakeType */
    @Test
    public final void testMakeType() {
        Integer precision = 0;
        Integer scale = 2;

        assertEquals("tinyblob", getDataTypeStr("tinyblob", precision, scale));
        assertEquals("tinytext", getDataTypeStr("tinytext", precision, scale));
        assertEquals("blob", getDataTypeStr("blob", precision, scale));
        assertEquals("text", getDataTypeStr("text", precision, scale));
        assertEquals("mediumblob", getDataTypeStr("mediumblob", precision, scale));
        assertEquals("mediumtext", getDataTypeStr("mediumtext", precision, scale));
        assertEquals("longblob", getDataTypeStr("longblob", precision, scale));
        assertEquals("longtext", getDataTypeStr("longtext", precision, scale));

        precision = 10;
        assertEquals("char(10)", getDataTypeStr("char", precision, scale));
        assertEquals("varchar(10)", getDataTypeStr("varchar", precision, scale));
        assertEquals("tinyint(10)", getDataTypeStr("tinyint", precision, scale));
        assertEquals("smallint(10)", getDataTypeStr("smallint", precision, scale));
        assertEquals("mediumint(10)", getDataTypeStr("mediumint", precision, scale));
        assertEquals("int(10)", getDataTypeStr("int", precision, scale));

        assertEquals("bigint(10)", getDataTypeStr("bigint", precision, scale));
        assertEquals("bit(10)", getDataTypeStr("bit", precision, scale));
        assertEquals("binary(10)", getDataTypeStr("binary", precision, scale));
        assertEquals("varbinary(10)", getDataTypeStr("varbinary", precision, scale));

        assertEquals("float(10,2)", getDataTypeStr("float", precision, scale));
        assertEquals("double(10,2)", getDataTypeStr("double", precision, scale));
        assertEquals("decimal(10,2)", getDataTypeStr("decimal", precision, scale));

        String colType = "float";
        String res = getDataTypeStr(colType, precision, scale);
        assertNotNull(res);

        colType = "float unsigned";
        res = getDataTypeStr(colType, precision, scale);
        assertNotNull(res);

        colType = "enum";
        res = getDataTypeStr(colType, precision, scale);
        assertNotNull(res);

        colType = "character(10)";
        res = getDataTypeStr(colType, precision, scale);
        assertNotNull(res);
    }

    /** testGetScale */
    @Test
    public final void testGetScale() {
        String jdbcType = "decimal(5,2)";
        Integer res = helper.parseScale(jdbcType);
        assertEquals(res, new Integer(2));

        jdbcType = "char(10)";
        res = helper.parseScale(jdbcType);
        assertEquals(null, res);

        jdbcType = "enum";
        res = helper.parseScale(jdbcType);
        assertEquals(null, res);

        helper.parseScale("integer");
        helper.parseScale("varchar(200)");
        helper.parseScale("number(38,2)");
        helper.parseScale("enum(int)");
        helper.parseScale("set(int)");
    }

    /** testGetPrecision */
    @Test
    public final void testGetPrecision() {
        String jdbcType = "decimal(5,2)";
        int res = helper.parsePrecision(jdbcType);
        assertEquals(5, res);

        jdbcType = "char(10)";
        res = helper.parsePrecision(jdbcType);
        assertEquals(10, res);

        jdbcType = "enum";
        res = helper.parsePrecision(jdbcType);
        assertEquals(-1, res);
    }

    /** testIsValidDatatype */
    @Test
    public final void testGetTypePart() {
        String type = "decimal(5,2)";
        String typePart = helper.parseMainType(type);
        assertEquals("decimal", typePart);

        type = "enum";
        typePart = helper.parseMainType(type);
        assertEquals("enum", typePart);
    }

    /** testGetTypeRemain */
    @Test
    public final void testGetTypeRemain() {
        String type = "Integer(5)";
        String typeRemain = helper.parseTypeRemain(type);

        System.out.println(typeRemain);
        assertEquals("5", typeRemain);
    }

    @Test
    public final void testMySQLColumn() {
        assertFalse(new Column().equals(12));
    }

    @Test
    public final void testGetJdbcDataTypeID() throws Exception {
        MigrationConfiguration config = TemplateParserTest.getMySQLConfig();
        helper.getJdbcDataTypeID(config.getSrcCatalog(), "INTEGER", null, null);
        helper.getJdbcDataTypeID(config.getSrcCatalog(), "VARCHAR", 200, null);
        helper.getJdbcDataTypeID(config.getSrcCatalog(), "BLOB", null, null);
        try {
            helper.getJdbcDataTypeID(config.getSrcCatalog(), "testnotype", null, null);
        } catch (Exception ex) {
            assertTrue(ex.getMessage().startsWith("Not supported MySQL data type"));
        }
    }

    @Test
    public final void testIsBinaryType() {
        Column col = new Column();
        col.setDataType("blob");
        assertTrue(helper.isBinary(col.getDataType()));
        col.setDataType("tinyblob");
        assertTrue(helper.isBinary(col.getDataType()));
        col.setDataType("mediumblob");
        assertTrue(helper.isBinary(col.getDataType()));
        col.setDataType("longblob");
        assertTrue(helper.isBinary(col.getDataType()));
        col.setDataType("bit");
        assertTrue(helper.isBinary(col.getDataType()));
        col.setDataType("int");
        assertFalse(helper.isBinary(col.getDataType()));
    }
}
