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
package com.cubrid.cubridmigration.core.datatype;

import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DBDataTypeHelperTest extends DBDataTypeHelper {

    public DatabaseType getDBType() {
        return null;
    }

    @Override
    public Integer getJdbcDataTypeID(
            Catalog catalog, String dataType, Integer precision, Integer scale) {
        return 0;
    }

    @Override
    public String getShownDataType(Column column) {
        return column.getShownDataType();
    }

    @Override
    public boolean isBinary(String dataType) {
        return false;
    }

    @Override
    public boolean isCollection(String dataType) {
        return false;
    }

    @Test
    public void test() {
        assertTrue(this.isChar("character(1)"));
        assertTrue(this.isChar("character"));
        assertTrue(this.isChar("CHARACTER"));
        assertTrue(this.isChar("char(1)"));
        assertTrue(this.isChar("CHAR"));
        assertTrue(this.isString("char"));
        assertTrue(this.isString("varchar"));
        assertFalse(this.isString("nchar"));
        assertFalse(this.isString("nvarchar"));
        assertTrue(this.isVarchar("varchar"));
        assertTrue(this.isVarchar("varchar(100)"));
        assertTrue(this.isEnum("enum"));
        assertTrue(this.isEnum("ENUM"));
        assertTrue(this.isNumeric("numeric"));
        assertTrue(this.isNumeric("numeric(38,2)"));
        assertTrue(this.isNumeric("NUMERIC(38)"));
        assertTrue(this.isNumeric("dec"));
        assertTrue(this.isNumeric("decimal"));
        assertTrue(this.isSupportAutoIncr("int", "", null));
        assertEquals("int", this.getMainDataType("int"));
        assertEquals("varchar", this.getMainDataType("varchar(200)"));
        assertEquals("numeric", this.getMainDataType("numeric(10,2)"));
        //		Column col = new Column();
        //		assertTrue(this.isNChar(col));
        //		assertTrue(this.isNVarchar(col));
        //		assertTrue(this.isString(col));

    }
}
