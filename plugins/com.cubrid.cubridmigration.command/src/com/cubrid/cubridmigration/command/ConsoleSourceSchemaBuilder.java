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
package com.cubrid.cubridmigration.command;

import com.cubrid.cubridmigration.core.dbmetadata.JDBCDBSchemaFetcherFacade;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSQLTableConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * This class builds the source catalog for the console mode of the CUBRID Migration Toolkit. It
 * extracts the schema names from the migration configuration and fetches only those schemas from the
 * source database.
 *
 * @author CUBRID Corporation
 * @version 1.0 - 2023-11-28 created by CUBRID Corporation
 */
public class ConsoleSourceSchemaBuilder {

    /**
     * build source catalog for console migration
     *
     * @param config MigrationConfiguration
     * @return Catalog
     */
    public Catalog build(MigrationConfiguration config) {
        List<String> schemaNames = new ArrayList<String>();
        if (config.sourceIsSchema()) {
            for (SourceEntryTableConfig setc : config.getExpEntryTableCfg()) {
                if (setc.getOwner() == null || schemaNames.contains(setc.getOwner())) {
                    continue;
                }
                schemaNames.add(setc.getOwner());
            }
        } else {
            for (SourceSQLTableConfig sstc : config.getExpSQLCfg()) {
                if (sstc.getOwner() == null || schemaNames.contains(sstc.getOwner())) {
                    continue;
                }
                schemaNames.add(sstc.getOwner());
            }
        }
        if (schemaNames.isEmpty()) {
            throw new RuntimeException("No source schema specified in migration script.");
        }

        JDBCDBSchemaFetcherFacade builder = new JDBCDBSchemaFetcherFacade();
        return builder.fetchSchemas(config.getSourceConParams(), schemaNames);
    }
}
