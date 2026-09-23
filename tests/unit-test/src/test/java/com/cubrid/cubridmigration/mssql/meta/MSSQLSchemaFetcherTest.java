/*
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
 * - Neither the name of the copyright holder nor the names of its contributors
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
package com.cubrid.cubridmigration.mssql.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.datatype.DataType;
import com.cubrid.cubridmigration.core.dbobject.Table;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@DisplayName("MSSQLSchemaFetcher")
class MSSQLSchemaFetcherTest {

    private static final MSSQLSchemaFetcher FETCHER = new MSSQLSchemaFetcher();

    private static ResultSetMetaData oneColumn(String typeName, int jdbcType, int precision)
            throws SQLException {
        ResultSetMetaData rsm = mock(ResultSetMetaData.class);
        when(rsm.getColumnCount()).thenReturn(1);
        when(rsm.getColumnLabel(1)).thenReturn("C1");
        when(rsm.getColumnName(1)).thenReturn("C1");
        when(rsm.getColumnType(1)).thenReturn(jdbcType);
        when(rsm.getColumnTypeName(1)).thenReturn(typeName);
        when(rsm.getPrecision(1)).thenReturn(precision);
        when(rsm.getScale(1)).thenReturn(0);
        return rsm;
    }

    @Test
    @DisplayName("buildSQLTable() fills in how the type is shown and keeps the type itself")
    void buildSQLTable_fillsInTheShownType() throws SQLException {
        Table table = FETCHER.buildSQLTable(oneColumn("varchar", Types.VARCHAR, 20));

        assertThat(table.getColumns().get(0).getDataType()).isEqualTo("varchar");
        assertThat(table.getColumns().get(0).getShownDataType()).isEqualTo("varchar(20)");
    }

    // The other dialects put their own widest text type in when the driver names none. MSSQL
    // does not, so such a column reaches the transform stage still nameless.
    @Test
    @DisplayName("a column with no type name is left nameless, unlike the other dialects")
    void untypedColumn_isLeftNameless() throws SQLException {
        Table table = FETCHER.buildSQLTable(oneColumn("", Types.OTHER, 10));

        assertThat(table.getColumns().get(0).getDataType()).isEmpty();
    }

    // hierarchyid has no JDBC type of its own, so the name's hash stands in as the identifier.
    // Nothing else in CMT derives that number, which is why it is pinned here.
    @Test
    @DisplayName("hierarchyid is registered under an id derived from its own name")
    void hierarchyId_isRegisteredUnderItsNameHash() throws Exception {
        Map<String, List<DataType>> supportedTypes = new HashMap<String, List<DataType>>();
        Method method =
                MSSQLSchemaFetcher.class.getDeclaredMethod(
                        "addHierarchyIDTypeToSupportedTypes", Map.class);
        method.setAccessible(true);

        method.invoke(FETCHER, supportedTypes);

        assertThat(supportedTypes).containsOnlyKeys("hierarchyid");
        assertThat(supportedTypes.get("hierarchyid")).hasSize(1);
        assertThat(supportedTypes.get("hierarchyid").get(0).getTypeName()).isEqualTo("hierarchyid");
        assertThat(supportedTypes.get("hierarchyid").get(0).getJdbcDataTypeID())
                .isEqualTo("hierarchyid".hashCode());
    }
}
