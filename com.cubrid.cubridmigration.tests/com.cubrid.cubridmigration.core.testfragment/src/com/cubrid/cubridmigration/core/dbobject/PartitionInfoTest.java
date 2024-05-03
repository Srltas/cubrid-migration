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
package com.cubrid.cubridmigration.core.dbobject;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PartitionInfoTest {

    private static final String HASH = PartitionInfo.PARTITION_METHOD_HASH;

    @Test
    public void testPartitionInfo() throws CloneNotSupportedException {
        PartitionInfo cloneObj = newPartitionInfo();

        assertEquals(1, cloneObj.getPartitionColumnCount());
        assertFalse(cloneObj.getPartitionColumns().isEmpty());
        assertEquals(4, cloneObj.getPartitionCount());
        assertNotNull(cloneObj.getDDL());
        assertNotNull(cloneObj.getPartitionExp());
        assertNotNull(cloneObj.getPartitionFunc());
        assertNotNull(cloneObj.getPartitionMethod());
        assertNotNull(cloneObj.getPartitions());
        assertNotNull(cloneObj.getPartitionTableByName("part_0"));
        assertNull(cloneObj.getPartitionTableByName("nopart"));
        assertNotNull(cloneObj.getPartitionTableByPosition(0));
        assertNull(cloneObj.getPartitionTableByPosition(100));
        assertNotNull(cloneObj.getSubPartitionColumnCount());
        assertNotNull(cloneObj.getSubPartitionColumns());
        assertNotNull(cloneObj.getSubPartitionCount());
        assertNotNull(cloneObj.getSubPartitionExp());
        assertNotNull(cloneObj.getSubPartitionFunc());
        assertNotNull(cloneObj.getSubPartitionMethod());
        assertNotNull(cloneObj.getSubPartitionNameByIdx(0));
        assertNull(cloneObj.getSubPartitionNameByIdx(100));
        assertNotNull(cloneObj.getSubPartitions());
    }

    /** @return PartitionInfo */
    public static PartitionInfo newPartitionInfo() {
        PartitionInfo pt = new PartitionInfo();
        pt.setBoundaryValueOnRight(true);
        pt.setPartitionColumnCount(1);
        pt.setPartitionCount(4);
        pt.setDDL("partition by hash (f1) partitions(4) ");
        pt.setPartitionExp("f1");
        pt.setPartitionFunc("");
        pt.setPartitionMethod(HASH);
        List<Column> partionColumns = new ArrayList<Column>();
        Column col = new Column();
        col.setName("f1");
        col.setDataType("varchar(100)");
        partionColumns.add(col);
        pt.setPartitionColumns(partionColumns);
        List<PartitionTable> partitions = new ArrayList<PartitionTable>();
        for (int i = 0; i < 4; i++) {
            PartitionTable ptable = new PartitionTable();
            ptable.setPartitionDesc("1" + i + "0000");
            ptable.setPartitionIdx(i);
            ptable.setPartitionName("part_" + i);
            partitions.add(ptable);
        }
        pt.setPartitions(partitions);

        partionColumns = new ArrayList<Column>();
        col = new Column();
        col.setName("f2");
        col.setDataType("varchar(100)");
        pt.setSubPartitionColumns(partionColumns);
        pt.setSubPartitionColumnCount(1);
        pt.setSubPartitionCount(4);
        pt.setSubPartitionExp("f2");
        pt.setSubPartitionFunc("");
        pt.setSubPartitionMethod(HASH);
        for (int i = 0; i < 4; i++) {
            PartitionTable ptable = new PartitionTable();
            ptable.setPartitionDesc("1" + i + "0000");
            ptable.setPartitionIdx(i);
            ptable.setPartitionName("sub_part_" + i);
            pt.addSubPartition(ptable);
        }
        PartitionInfo cloneObj = (PartitionInfo) pt.clone();
        return cloneObj;
    }
}
