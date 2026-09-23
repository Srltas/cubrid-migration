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
package com.cubrid.cubridmigration.informix.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Table;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.MissingFormatArgumentException;

@DisplayName("InformixSchemaFetcher")
class InformixSchemaFetcherTest {

    private static final InformixSchemaFetcher FETCHER = new InformixSchemaFetcher();

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

    private static Object buildTriggerDDL(String body, String name, String event) throws Exception {
        Method method =
                InformixSchemaFetcher.class.getDeclaredMethod(
                        "buildTriggerDDL", String.class, String.class, String.class);
        method.setAccessible(true);
        return method.invoke(FETCHER, body, name, event);
    }

    // Informix spells a timestamp as a range, "datetime year to second". CUBRID has no such form,
    // so the qualifier is cut and the plain type name is what reaches the transform stage.
    @Test
    @DisplayName("a datetime range -> the bare datetime type")
    void datetimeRange_losesItsQualifier() throws SQLException {
        Table table =
                FETCHER.buildSQLTable(oneColumn("datetime year to second", Types.TIMESTAMP, 0));

        assertThat(table.getColumns().get(0).getDataType()).isEqualTo("datetime");
        assertThat(table.getColumns().get(0).getShownDataType()).isEqualTo("datetime");
    }

    @Test
    @DisplayName("any other type keeps its name and gains how it is shown")
    void otherType_keepsItsName() throws SQLException {
        Table table = FETCHER.buildSQLTable(oneColumn("varchar", Types.VARCHAR, 20));

        assertThat(table.getColumns().get(0).getDataType()).isEqualTo("varchar");
        assertThat(table.getColumns().get(0).getShownDataType()).isEqualTo("varchar(20)");
    }

    // DEFECT: the template carries four placeholders but only three arguments are supplied, so
    // every trigger Informix reports raises instead of producing DDL. buildTriggers() is where
    // it surfaces, and the schema build loses the whole trigger catalog with it
    // - see InformixSchemaFetcher.buildTriggerDDL()
    @ParameterizedTest(name = "[{index}] event {0} -> raises")
    @DisplayName("every event Informix names raises instead of producing DDL")
    @ValueSource(strings = {"U", "I", "S", "D"})
    void knownEvent_raisesInsteadOfProducingDdl(String event) {
        assertThatThrownBy(() -> buildTriggerDDL("trigger body", "trg1", event))
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(MissingFormatArgumentException.class);
    }

    // An event the method does not know skips the template entirely, which is the only path that
    // returns anything at all.
    @Test
    @DisplayName("an event with no mapping -> the body returned untouched")
    void unknownEvent_returnsTheBodyUntouched() throws Exception {
        assertThat(buildTriggerDDL("trigger body", "trg1", "X")).isEqualTo("trigger body");
    }
}
