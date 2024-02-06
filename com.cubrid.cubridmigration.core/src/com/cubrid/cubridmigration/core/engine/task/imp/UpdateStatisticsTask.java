/*
 * Copyright (C) 2009 Search Solution Corporation. All rights reserved by Search Solution.
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
package com.cubrid.cubridmigration.core.engine.task.imp;

import com.cubrid.cubridmigration.core.common.Closer;
import com.cubrid.cubridmigration.core.common.log.LogUtil;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceCSVConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSQLTableConfig;
import com.cubrid.cubridmigration.core.engine.task.ImportTask;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;

/**
 * CleanDBTask is to clear the database objects which were set to be replaced in target database
 *
 * @author Kevin Cao
 * @version 1.0 - 2012-9-5 created by Kevin Cao
 */
public class UpdateStatisticsTask extends ImportTask {

    private static final Logger LOG = LogUtil.getLogger(UpdateStatisticsTask.class);

    private final MigrationConfiguration config;

    public UpdateStatisticsTask(MigrationConfiguration config) {
        this.config = config;
    }

    /**
     * Retrieves the SQL's about UPDATE STATISTICS ON
     * <table>
     *
     * @return UPDATE STATISTICS ON SQLs
     */
    private List<String> getUpdateStatisticsSQLs(String schemaName) {
        List<String> result = new ArrayList<String>();
        if (config.sourceIsSQL()) {
            return result;
        }
        List<String> objectsToBeUpdated = new ArrayList<String>();

        if (config.isAddUserSchema()) {
            if (config.sourceIsCSV()) {
                List<SourceCSVConfig> csvConfigs = config.getCSVConfigs();
                for (SourceCSVConfig csvf : csvConfigs) {
                    if (csvf.getTargetOwner().equals(schemaName)) {
                        objectsToBeUpdated.add(
                                "[" + csvf.getTargetOwner() + "].[" + csvf.getTarget() + "]");
                    }
                }
            } else {
                List<SourceEntryTableConfig> expEntryTableCfg = config.getExpEntryTableCfg();
                for (SourceEntryTableConfig setc : expEntryTableCfg) {
                    if (setc.getTargetOwner().equals(schemaName)
                            && setc.isMigrateData()
                            && !objectsToBeUpdated.contains(setc.getTarget())) {
                        objectsToBeUpdated.add(
                                "[" + setc.getTargetOwner() + "].[" + setc.getTarget() + "]");
                    }
                }
                List<SourceSQLTableConfig> expSQLCfg = config.getExpSQLCfg();
                for (SourceSQLTableConfig sstc : expSQLCfg) {
                    if (sstc.getTargetOwner().equals(schemaName)
                            && sstc.isMigrateData()
                            && !objectsToBeUpdated.contains(sstc.getTarget())) {
                        objectsToBeUpdated.add(
                                "[" + sstc.getTargetOwner() + "].[" + sstc.getTarget() + "]");
                    }
                }
            }

        } else {
            if (config.sourceIsCSV()) {
                List<SourceCSVConfig> csvConfigs = config.getCSVConfigs();
                for (SourceCSVConfig csvf : csvConfigs) {
                    objectsToBeUpdated.add(csvf.getTarget());
                }
            } else {
                List<SourceEntryTableConfig> expEntryTableCfg = config.getExpEntryTableCfg();
                for (SourceEntryTableConfig setc : expEntryTableCfg) {
                    if (setc.isMigrateData() && !objectsToBeUpdated.contains(setc.getTarget())) {
                        objectsToBeUpdated.add(setc.getTarget());
                    }
                }
                List<SourceSQLTableConfig> expSQLCfg = config.getExpSQLCfg();
                for (SourceSQLTableConfig sstc : expSQLCfg) {
                    if (sstc.isMigrateData() && !objectsToBeUpdated.contains(sstc.getTarget())) {
                        objectsToBeUpdated.add(sstc.getTarget());
                    }
                }
            }
        }

        for (String target : objectsToBeUpdated) {
            String sql = "UPDATE STATISTICS ON " + target + ";";

            result.add(sql);
        }
        return result;
    }

    /** Execute import */
    protected void executeImport() {
        if (config.targetIsOnline() && config.isUpdateStatistics()) {
            List<Schema> schemaList = config.getTargetSchemaList();
            for (Schema schema : schemaList) {
                LOG.debug("Execute update statistics for " + schema.getTargetSchemaName());
                execSQLList(getUpdateStatisticsSQLs(schema.getTargetSchemaName()));
            }
            return;
        }

        if (config.isAddUserSchema()) {
            List<Schema> schemaList = config.getTargetSchemaList();
            for (Schema schema : schemaList) {
                writeFile(schema.getName(), schema.getTargetSchemaName());
            }
        } else {
            // For versions that do not support multi schema, the targetSchema parameter is not
            // needed.
            writeFile(config.getSourceConParams().getConUser(), "");
        }
    }

    /**
     * Write Update statistic SQL to the file
     *
     * @param schemaName String
     */
    private void writeFile(String schemaName, String targetSchemaName) {
        if (!checkDataFileRepository(schemaName)) {
            return;
        }

        String tfile = config.getTargetUpdateStatisticFileName(schemaName);
        File file = new File(tfile);
        OutputStream os = null; // NO PMD
        try {
            os = new BufferedOutputStream(new FileOutputStream(file, true));
            List<String> sqlList = getUpdateStatisticsSQLs(targetSchemaName);
            byte[] enterBytes = "\n".getBytes();
            for (String sql : sqlList) {
                os.write(sql.getBytes());
                os.write(enterBytes);
            }
            os.flush();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            Closer.close(os);
        }
    }

    /**
     * Execute sqls
     *
     * @param sqlList String
     */
    private void execSQLList(List<String> sqlList) {
        for (String sql : sqlList) {
            try {
                importer.executeDDL(sql);
            } catch (Exception ex) {
                LOG.warn("Execute SQL error:" + sql, ex);
            }
        }
    }

    /**
     * Check if a data file repository exists
     *
     * @param fileRepository
     * @return repository exist true, no repository false boolean
     */
    private boolean checkDataFileRepository(String schemaName) {
        String fileRepository = config.getTargetDataFileName(schemaName);
        return fileRepository != null ? new File(fileRepository).exists() : false;
    }
}
