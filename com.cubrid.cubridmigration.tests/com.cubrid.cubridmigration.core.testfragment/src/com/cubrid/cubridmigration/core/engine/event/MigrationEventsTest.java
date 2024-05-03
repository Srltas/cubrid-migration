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
package com.cubrid.cubridmigration.core.engine.event;

import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.Function;
import com.cubrid.cubridmigration.core.dbobject.Procedure;
import com.cubrid.cubridmigration.core.dbobject.Record;
import com.cubrid.cubridmigration.core.dbobject.Sequence;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.Trigger;
import com.cubrid.cubridmigration.core.engine.config.SourceTableConfig;
import com.cubrid.cubridmigration.core.engine.exception.NormalMigrationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MigrationEventsTest {

    private Table table = new Table();

    {
        table.setName("test");
        Column column = new Column();
        column.setName("f1");
        column.setDataType("integer");
        table.addColumn(column);
        column = new Column();
        column.setName("f2");
        column.setDataType("integer");
        table.addColumn(column);
    }

    @Test
    public void testCreateObjectFailEvent() {
        CreateObjectEvent event = new CreateObjectEvent(table, new RuntimeException("error"));
        assertNotNull(event.getError());
        event.toString();

        event = new CreateObjectEvent(table);
        assertNull(event.getError());
        event.toString();
    }

    @Test
    public void testCreateObjectStartEvent() {
        CreateObjectStartEvent event = new CreateObjectStartEvent(table);
        event.toString();
    }

    @Test
    public void testSingleRecordErrorEvent() {
        Record record = new Record();

        SingleRecordErrorEvent event;
        for (Column col : table.getColumns()) {
            record.addColumnValue(col, "1");
        }
        event = new SingleRecordErrorEvent(record, new RuntimeException("exception"));
        event.toString();
    }

    @Test
    public void testMigrationErrorEvent() {
        MigrationErrorEvent event = new MigrationErrorEvent(new RuntimeException("exception"));
        assertTrue(event.isFatalError());
        event.getError();
        event.toString();
    }

    @Test
    public void testExportRecordsEvent() {
        SourceTableConfig sourceTable = new SourceTableConfig();
        sourceTable.setName("test");
        ExportRecordsEvent event = new ExportRecordsEvent(sourceTable, 0);
        System.out.println(event.toString());
        assertNotNull(event.getSourceTable());
        event = new ExportRecordsEvent(sourceTable, 100);
        System.out.println(event.toString());
        assertEquals(100, event.getRecordCount());
    }

    @Test
    public void testCreateObjectEvent() {
        Trigger obj = new Trigger();
        obj.setName("testtrigger");
        CreateObjectEvent event = new CreateObjectEvent(obj);
        assertEquals("Create trigger[testtrigger] successfully.", event.toString());

        Function fun = new Function();
        fun.setName("testfunction");
        event = new CreateObjectEvent(fun);
        assertEquals("Create function[testfunction] successfully.", event.toString());

        Procedure pro = new Procedure();
        pro.setName("testprocedure");
        event = new CreateObjectEvent(pro);
        assertEquals("Create procedure[testprocedure] successfully.", event.toString());

        Sequence seq = new Sequence();
        seq.setName("testsequence");
        event = new CreateObjectEvent(seq);
        assertEquals("Create sequence[testsequence] successfully.", event.toString());
    }

    @Test
    public void testImportRecordsEvent() {
        SourceTableConfig sourceTable = new SourceTableConfig();
        sourceTable.setName("test");
        sourceTable.setTarget("target");
        ImportRecordsEvent event = new ImportRecordsEvent(sourceTable, 100);
        assertTrue(event.toString().length() > 0);
        event =
                new ImportRecordsEvent(
                        sourceTable, 100, new NormalMigrationException("test error"), null);
        assertTrue(event.toString().length() > 0);
        event = new ImportRecordsEvent(sourceTable, 0);
        assertTrue(event.toString().length() > 0);
    }

    @Test
    public void testMigrationNoSupportEvent() {
        MigrationNoSupportEvent event = new MigrationNoSupportEvent(null);
        assertEquals("NULL", event.toString());
        event = new MigrationNoSupportEvent(new Table());
        assertTrue(event.toString().length() > 0);
    }

    @Test
    public void testMigrationFinishedEvent() {
        MigrationFinishedEvent event = new MigrationFinishedEvent(true);
        assertTrue(event.toString().length() > 0);
        event = new MigrationFinishedEvent(false);
        assertTrue(event.toString().length() > 0);
    }
}
