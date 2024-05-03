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
package com.cubrid.cubridmigration.cubrid;

import com.cubrid.cubridmigration.core.datatype.DataTypeInstance;
import com.cubrid.cubridmigration.core.dbobject.Column;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CUBRIDDataTypeTest
 *
 * @author moulinwang Kevin Cao
 * @version 1.0 - 2010-10-4
 */
public class CUBRIDDataTypeHelperTest {
    CUBRIDDataTypeHelper dataTypeHelper = CUBRIDDataTypeHelper.getInstance(null);

    @Test
    public void testCUBRIDDataTypeMethods() {
        String[][] types =
                new String[][] {
                    {"CLOB", "clob", "clob"},
                    {"BLOB", "blob", "blob"},
                    {"character(1)", "char(1)", "char"},
                    {"char(1)", "char(1)", "char"},
                    {"character varying(1073741823)", "varchar(1073741823)", "varchar"},
                    {"STRING", "varchar(1073741823)", "varchar"},
                    {"character varying(30)", "varchar(30)", "varchar"},
                    {"VARCHAR(30)", "varchar(30)", "varchar"},
                    {"national character(1)", "char(1)", "char"},
                    {"NCHAR(1)", "char(1)", "char"},
                    {"national character varying(4)", "varchar(4)", "varchar"},
                    {"VARNCHAR(4)", "varchar(4)", "varchar"},
                    {"bit(10)", "bit(10)", "bit"},
                    {"bit varying(30)", "bit varying(30)", "bit varying"},
                    {"varbit(30)", "bit varying(30)", "bit varying"},
                    {"numeric(15,0)", "numeric(15,0)", "numeric"},
                    {"dec(15,0)", "numeric(15,0)", "numeric"},
                    {"decimal(15,0)", "numeric(15,0)", "numeric"},
                    {"integer", "int", "int"},
                    {"smallint", "short", "short"},
                    {"monetary", "monetary", "monetary"},
                    {"float", "float", "float"},
                    {"real", "float", "float"},
                    {"double", "double", "double"},
                    {"double precision", "double", "double"},
                    {"date", "date", "date"},
                    {"time", "time", "time"},
                    {"timestamp", "timestamp", "timestamp"},
                    {"set_of(numeric(15,0))", "set(numeric(15,0))", "set"},
                    {"multiset_of(string)", "multiset(varchar(1073741823))", "multiset"},
                    {"sequence_of(char(10))", "list(char(10))", "list"},
                    {"sequence_of(numeric(10,2))", "list(numeric(10,2))", "list"},
                    {"sequence_of(datetime)", "list(datetime)", "list"},
                    {"SEQUENCE_OF(DATETIME)", "list(datetime)", "list"},
                    {"ENUM('1','2','a','A')", "enum('1','2','a','A')", "enum"}
                };
        for (String[] strs : types) {
            System.out.println(strs[0]);
            Column column = new Column();
            dataTypeHelper.setColumnDataType(strs[0], column);
            assertTrue(dataTypeHelper.isValidDatatype(strs[0]));
            String shownType = dataTypeHelper.getShownDataType(column);
            assertEquals(strs[1], shownType);
            assertEquals(strs[2], dataTypeHelper.getStdMainDataType(strs[0]));
        }
    }

    /** testIsValidDatatype */
    @Test
    public final void testIsValidDatatype() {
        String dataTypeInstance = null;
        boolean flag = dataTypeHelper.isValidDatatype(dataTypeInstance);
        assertFalse(flag);

        dataTypeInstance = "smallint";
        flag = dataTypeHelper.isValidDatatype(dataTypeInstance);
        assertTrue(flag);

        dataTypeInstance = "VARCHAR(10)";
        flag = dataTypeHelper.isValidDatatype(dataTypeInstance);

        assertTrue(flag);

        dataTypeInstance = "VARCHAR(2";
        flag = dataTypeHelper.isValidDatatype(dataTypeInstance);
        assertFalse(flag);

        dataTypeInstance = "VARCHAR2(2";
        flag = dataTypeHelper.isValidDatatype(dataTypeInstance);
        assertFalse(flag);

        dataTypeInstance = "VARCHAR(a)";
        flag = dataTypeHelper.isValidDatatype(dataTypeInstance);
        assertFalse(flag);

        dataTypeInstance = "sequence_of(char(10)";
        assertFalse(dataTypeHelper.isValidDatatype(dataTypeInstance));

        dataTypeInstance = "char(10))";
        assertFalse(dataTypeHelper.isValidDatatype(dataTypeInstance));
    }

    /** testGetScale */
    @Test
    public final void testGetScale() {
        String jdbcType = "numeric(10,3)";
        int res = dataTypeHelper.getScale(jdbcType);
        System.out.println(res);
        assertEquals(3, res);

        String jdbcType2 = "SET(numeric(10,3))";
        int res2 = dataTypeHelper.getScale(jdbcType2);
        System.out.println(res2);
        assertEquals(3, res2);

        assertNull(dataTypeHelper.getScale("varchar(200)"));
        assertNull(dataTypeHelper.getScale("numeric(38)"));
        assertNull(dataTypeHelper.getScale("int"));
    }

    /** testGetPrecision */
    @Test
    public final void testGetPrecision() {
        assertTrue(10 == dataTypeHelper.getPrecision("SET(numeric(10,3))"));
        assertTrue(10 == dataTypeHelper.getPrecision("SET(varchar(10))"));
        assertNull(dataTypeHelper.getPrecision("SET(int)"));
        assertTrue(10 == dataTypeHelper.getPrecision("numeric(10)"));
        assertTrue(10 == dataTypeHelper.getPrecision("varchar(10)"));
        assertNull(dataTypeHelper.getPrecision("int"));
    }

    /** testGetTypeRemain */
    @Test
    public final void testGetRemain() {
        assertEquals("int", dataTypeHelper.getRemain("MULTISET(int)"));
        assertEquals("varchar(10)", dataTypeHelper.getRemain("MULTISET(varchar(10))"));
        assertEquals("numeric(10,2)", dataTypeHelper.getRemain("MULTISET(numeric(10,2))"));
        assertEquals("numeric(10,2)", dataTypeHelper.getRemain("MULTISET(NUMERIC(10,2))"));
    }

    @Test
    public void testIsValidValue() {
        CUBRIDDataTypeHelper helper = CUBRIDDataTypeHelper.getInstance(null);
        assertTrue(helper.isValidValue("char(1)", "a"));
        assertFalse(helper.isValidValue("char(1)", "aa"));
        assertTrue(helper.isValidValue("varchar(2)", "aa"));
        assertFalse(helper.isValidValue("varchar(2)", "aaa"));
        assertTrue(helper.isValidValue("int", "1"));
        assertFalse(helper.isValidValue("int", "a"));

        assertTrue(helper.isValidValue("integer", "1"));
        assertFalse(helper.isValidValue("integer", "b"));
        assertFalse(helper.isValidValue("integer", "99999999999999999"));

        assertTrue(helper.isValidValue("short", "1"));
        assertFalse(helper.isValidValue("short", "b"));

        assertTrue(helper.isValidValue("smallint", "1"));
        assertFalse(helper.isValidValue("smallint", "a"));

        assertTrue(helper.isValidValue("long", "111"));
        assertFalse(helper.isValidValue("long", "ff"));

        assertTrue(helper.isValidValue("bigint", "111"));
        assertFalse(helper.isValidValue("bigint", "ddd"));

        assertTrue(helper.isValidValue("date", "2013-01-01"));
        assertFalse(helper.isValidValue("date", "fasdf"));

        assertTrue(helper.isValidValue("time", "01:01:01.001"));
        assertFalse(helper.isValidValue("time", "fasdfasdf"));

        assertTrue(helper.isValidValue("datetime", "2013-01-01 01:01:01.001"));
        assertFalse(helper.isValidValue("datetime", "ttttt"));

        assertTrue(helper.isValidValue("timestamp", "2013-01-01 01:01:01"));
        assertFalse(helper.isValidValue("timestamp", "ffff"));

        assertTrue(helper.isValidValue("bit(8)", "b'00000001'"));
        assertTrue(helper.isValidValue("bit(8)", "B'00000001'"));
        assertTrue(helper.isValidValue("bit(8)", "x'FF'"));
        assertTrue(helper.isValidValue("bit(8)", "X'ff'"));
        assertTrue(helper.isValidValue("bit(8)", "0xFF"));
        assertTrue(helper.isValidValue("bit(8)", "0Xff"));
        assertTrue(helper.isValidValue("bit(8)", "0b00000001"));
        assertTrue(helper.isValidValue("bit(8)", "0B00000001"));
        assertTrue(helper.isValidValue("bit varying(16)", "b'00000001'"));
        assertFalse(!helper.isValidValue("bit(8)", "b'0a000001'"));
    }

    @Test
    public void testparseDTInstance() {
        DataTypeInstance dti = dataTypeHelper.parseDTInstance("int");
        assertNull(dti.getSubType());
        assertEquals("int", dti.getName());
        assertNull(dti.getPrecision());
        assertNull(dti.getScale());
        assertNull(dti.getElments());

        dti = dataTypeHelper.parseDTInstance("enum('1','2','4','3','A')");
        assertNull(dti.getSubType());
        assertEquals("enum", dti.getName());
        assertNull(dti.getPrecision());
        assertNull(dti.getScale());
        assertEquals("'1','2','4','3','A'", dti.getElments());

        dti = dataTypeHelper.parseDTInstance("varchar(100)");
        assertNull(dti.getSubType());
        assertEquals("varchar", dti.getName());
        assertEquals(new Integer(100), dti.getPrecision());
        assertNull(dti.getScale());
        assertNull(dti.getElments());

        dti = dataTypeHelper.parseDTInstance("numeric(38,2)");
        assertNull(dti.getSubType());
        assertEquals("numeric", dti.getName());
        assertEquals(new Integer(38), dti.getPrecision());
        assertEquals(new Integer(2), dti.getScale());
        assertNull(dti.getElments());

        dti = dataTypeHelper.parseDTInstance("numeric(38)");
        assertNull(dti.getSubType());
        assertEquals("numeric", dti.getName());
        assertEquals(new Integer(38), dti.getPrecision());
        assertNull(dti.getScale());
        assertNull(dti.getElments());

        dti = dataTypeHelper.parseDTInstance("set(int)");
        assertEquals("set", dti.getName());
        assertNull(dti.getPrecision());
        assertNull(dti.getScale());
        assertNull(dti.getElments());
        assertNotNull(dti.getSubType());
        dti = dti.getSubType();
        assertNull(dti.getSubType());
        assertEquals("int", dti.getName());
        assertNull(dti.getPrecision());
        assertNull(dti.getScale());
        assertNull(dti.getElments());

        dti = dataTypeHelper.parseDTInstance("set(varchar(100))");
        assertEquals("set", dti.getName());
        assertNull(dti.getPrecision());
        assertNull(dti.getScale());
        assertNull(dti.getElments());
        assertNotNull(dti.getSubType());
        dti = dti.getSubType();
        assertNull(dti.getSubType());
        assertEquals("varchar", dti.getName());
        assertEquals(new Integer(100), dti.getPrecision());
        assertNull(dti.getScale());
        assertNull(dti.getElments());

        dti = dataTypeHelper.parseDTInstance("set(numeric(38,2))");
        assertEquals("set", dti.getName());
        assertNull(dti.getPrecision());
        assertNull(dti.getScale());
        assertNull(dti.getElments());
        assertNotNull(dti.getSubType());
        dti = dti.getSubType();
        assertNull(dti.getSubType());
        assertEquals("numeric", dti.getName());
        assertEquals(new Integer(38), dti.getPrecision());
        assertEquals(new Integer(2), dti.getScale());
        assertNull(dti.getElments());

        dti = dataTypeHelper.parseDTInstance("SET(NUMERIC(38,2))");
        assertEquals("SET", dti.getName());
        assertNull(dti.getPrecision());
        assertNull(dti.getScale());
        assertNull(dti.getElments());
        assertNotNull(dti.getSubType());
        dti = dti.getSubType();
        assertNull(dti.getSubType());
        assertEquals("NUMERIC", dti.getName());
        assertEquals(new Integer(38), dti.getPrecision());
        assertEquals(new Integer(2), dti.getScale());
        assertNull(dti.getElments());

        dti = dataTypeHelper.parseDTInstance("SET(STRING)");
        assertEquals("SET", dti.getName());
        assertNull(dti.getPrecision());
        assertNull(dti.getScale());
        assertNull(dti.getElments());
        assertNotNull(dti.getSubType());
        dti = dti.getSubType();
        assertNull(dti.getSubType());
        assertEquals("varchar", dti.getName());
        assertEquals(new Integer(1073741823), dti.getPrecision());
        assertNull(dti.getScale());
        assertNull(dti.getElments());
    }

    @Test
    public void testgetCUBRIDDataTypeID() {
        String[][] testcases =
                new String[][] {
                    {"smallint", "5"},
                    {"int", "4"},
                    {"bigint", "-5"},
                    {"numeric", "2"},
                    {"float", "7"},
                    {"double", "8"},
                    {"monetary", "30008"},
                    {"char", "1"},
                    {"varchar", "12"},
                    {"nchar", "1"},
                    {"nvarchar", "12"},
                    {"date", "91"},
                    {"time", "92"},
                    {"timestamp", "93"},
                    {"datetime", "30093"},
                    {"bit", "-2"},
                    {"bit varying", "-3"},
                    {"set", "31111"},
                    {"multiset", "41111"},
                    {"sequence", "51111"},
                    {"object", "32000"},
                    {"blob", "2004"},
                    {"clob", "2005"},
                    {"enum", "61111"}
                };
        for (String[] strs : testcases) {
            assertEquals(
                    Integer.parseInt(strs[1]), dataTypeHelper.getCUBRIDDataTypeID(strs[0]));
        }
        testcases =
                new String[][] {
                    {"SMALLINT", "5"},
                    {"short", "5"},
                    {"INT", "4"},
                    {"BIGINT", "-5"},
                    {"NUMERIC(38,2)", "2"},
                    {"decimal(38,2)", "2"},
                    {"dec(38,2)", "2"},
                    {"FLOAT", "7"},
                    {"real", "7"},
                    {"DOUBLE", "8"},
                    {"MONETARY", "30008"},
                    {"CHAR(1)", "1"},
                    {"CHARacter(1)", "1"},
                    {"VARCHAR(10)", "12"},
                    {"character varying(10)", "12"},
                    {"NCHAR(10)", "1"},
                    {"NVARCHAR(100)", "12"},
                    {"DATE", "91"},
                    {"TIME", "92"},
                    {"TIMESTAMP", "93"},
                    {"DATETIME", "30093"},
                    {"BIT(10)", "-2"},
                    {"VARBIT(11)", "-3"},
                    {"SET(int)", "31111"},
                    {"MULTISET(varchar(2))", "41111"},
                    {"SEQUENCE(numeric(38,2))", "51111"},
                    {"OBJECT", "32000"},
                    {"BLOB", "2004"},
                    {"CLOB", "2005"},
                    {"ENUM('a','A','b')", "61111"}
                }; // , {"string", "12" }
        for (String[] strs : testcases) {
            assertEquals(
                    Integer.parseInt(strs[1]), dataTypeHelper.getCUBRIDDataTypeID(strs[0]));
        }
    }
}
