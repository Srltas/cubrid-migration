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
package com.cubrid.cubridmigration.tibero.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.DBObjectFactory;
import com.cubrid.cubridmigration.core.dbobject.PartitionInfo;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Table;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@DisplayName("TiberoPartitionMetadataLoader")
class TiberoPartitionMetadataLoaderTest {

    @Test
    @DisplayName("buildPartitions() keeps partition metadata when source table DDL is absent")
    void buildPartitions_doesNotRequireSourceTableDDL() throws Exception {
        TiberoPartitionMetadataLoader loader = new TiberoPartitionMetadataLoader();
        DBObjectFactory factory = new DBObjectFactory();
        Schema schema = new Schema();
        schema.setName("APP");

        Table table = factory.createTable();
        table.setName("CMT_RANGE_PART");
        Column column = factory.createColumn();
        column.setName("SALE_DATE");
        column.setDataType("DATE");
        table.addColumn(column);
        schema.addTable(table);

        Connection conn = mock(Connection.class);

        PreparedStatement partTablesStmt = mock(PreparedStatement.class);
        PreparedStatement partColumnsStmt = mock(PreparedStatement.class);
        PreparedStatement subPartColumnsStmt = mock(PreparedStatement.class);
        PreparedStatement partitionsStmt = mock(PreparedStatement.class);
        PreparedStatement subPartitionsStmt = mock(PreparedStatement.class);

        ResultSet partTablesRs = mock(ResultSet.class);
        ResultSet partColumnsRs = mock(ResultSet.class);
        ResultSet subPartColumnsRs = mock(ResultSet.class);
        ResultSet partitionsRs = mock(ResultSet.class);
        ResultSet subPartitionsRs = mock(ResultSet.class);

        when(conn.prepareStatement(TiberoSqlConstants.SQL_GET_PART_TABLES))
                .thenReturn(partTablesStmt);
        when(conn.prepareStatement(TiberoSqlConstants.SQL_GET_PART_COLUMN))
                .thenReturn(partColumnsStmt);
        when(conn.prepareStatement(TiberoSqlConstants.SQL_GET_SUBPART_KEY_COLUMN))
                .thenReturn(subPartColumnsStmt);
        when(conn.prepareStatement(TiberoSqlConstants.SQL_GET_PARTITIONS))
                .thenReturn(partitionsStmt);
        when(conn.prepareStatement(TiberoSqlConstants.SQL_GET_SUB_PART_TABLES))
                .thenReturn(subPartitionsStmt);

        when(partTablesStmt.executeQuery()).thenReturn(partTablesRs);
        when(partColumnsStmt.executeQuery()).thenReturn(partColumnsRs);
        when(subPartColumnsStmt.executeQuery()).thenReturn(subPartColumnsRs);
        when(partitionsStmt.executeQuery()).thenReturn(partitionsRs);
        when(subPartitionsStmt.executeQuery()).thenReturn(subPartitionsRs);

        when(partTablesRs.next()).thenReturn(true, false);
        when(partTablesRs.getString("TABLE_NAME")).thenReturn("CMT_RANGE_PART");
        when(partTablesRs.getString("PARTITIONING_TYPE")).thenReturn("RANGE");
        when(partTablesRs.getInt("PARTITION_COUNT")).thenReturn(1);
        when(partTablesRs.getInt("PARTITIONING_KEY_COUNT")).thenReturn(1);
        when(partTablesRs.getString("SUBPARTITIONING_TYPE")).thenReturn("NONE");
        when(partTablesRs.getInt("DEF_SUBPARTITION_COUNT")).thenReturn(0);
        when(partTablesRs.getInt("SUBPARTITIONING_KEY_COUNT")).thenReturn(0);

        when(partColumnsRs.next()).thenReturn(true, false);
        when(partColumnsRs.getString("NAME")).thenReturn("CMT_RANGE_PART");
        when(partColumnsRs.getString("COLUMN_NAME")).thenReturn("SALE_DATE");

        when(subPartColumnsRs.next()).thenReturn(false);

        when(partitionsRs.next()).thenReturn(true, false);
        when(partitionsRs.getString("TABLE_NAME")).thenReturn("CMT_RANGE_PART");
        when(partitionsRs.getString("PARTITION_NAME")).thenReturn("P2024");
        when(partitionsRs.getCharacterStream("BOUND"))
                .thenReturn(new StringReader("(DATE '2025-01-01')"));
        when(partitionsRs.getInt("PARTITION_POSITION")).thenReturn(1);

        when(subPartitionsRs.next()).thenReturn(false);

        loader.buildPartitions(conn, schema, factory);

        PartitionInfo partitionInfo = table.getPartitionInfo();
        assertThat(partitionInfo).isNotNull();
        assertThat(partitionInfo.getPartitionMethod()).isEqualTo("RANGE");
        assertThat(partitionInfo.getPartitionColumns())
                .extracting(Column::getName)
                .containsExactly("SALE_DATE");
        assertThat(partitionInfo.getPartitions()).hasSize(1);
        assertThat(partitionInfo.getPartitions().get(0).getPartitionDesc())
                .isEqualTo("DATE '2025-01-01'");
    }
}
