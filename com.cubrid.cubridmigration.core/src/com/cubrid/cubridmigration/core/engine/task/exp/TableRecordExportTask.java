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
package com.cubrid.cubridmigration.core.engine.task.exp;

import java.util.List;

import com.cubrid.cubridmigration.core.dbobject.PartitionTable;
import com.cubrid.cubridmigration.core.dbobject.Record;
import com.cubrid.cubridmigration.core.engine.MigrationContext;
import com.cubrid.cubridmigration.core.engine.RecordExportedListener;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceTableConfig;
import com.cubrid.cubridmigration.core.engine.event.ExportRecordsEvent;
import com.cubrid.cubridmigration.core.engine.event.StartExpTableEvent;
import com.cubrid.cubridmigration.core.engine.task.ExportTask;
import com.cubrid.cubridmigration.core.engine.task.ImportTask;

/**
 * JDBCExportRecordTask responses to read records from source database through
 * JDBC driver.
 * 
 * @author Kevin Cao
 * @version 1.0 - 2011-8-8 created by Kevin Cao
 */
public class TableRecordExportTask extends
		ExportTask {

	protected SourceTableConfig sourceTable;
	protected final MigrationContext mrManager;
	protected SourceEntryTableConfig newStc;

	public TableRecordExportTask(MigrationContext mrManager, SourceTableConfig table) {
		this.mrManager = mrManager;
		sourceTable = table;
	}

	/**
	 * Export source table's records
	 */
	protected void executeExportTask() {
		if (sourceTable.getPartitionInfo() != null) {
			List<PartitionTable> partitionTableList = sourceTable.getPartitionInfo().getPartitions();
			for (PartitionTable partitionTable : partitionTableList) {
				SourceEntryTableConfig stc2 = (SourceEntryTableConfig) sourceTable;
				newStc = new SourceEntryTableConfig();				
				newStc.setOwner(stc2.getOwner());
				newStc.setName(stc2.getName());
				newStc.setComment(stc2.getComment());
				newStc.setTarget(stc2.getTarget());
				newStc.setPartitionInfo(stc2.getPartitionInfo());
				newStc.setTargetPartitionTable(partitionTable.getPartitionName());
				newStc.setTargetPartitionTableRowCount(partitionTable.getPartitionTableRowCount());
				newStc.setCreateNewTable(stc2.isCreateNewTable());
				newStc.setMigrateData(stc2.isMigrateData());
				newStc.setReplace(stc2.isReplace());
				newStc.setSqlBefore(stc2.getSqlBefore());
				newStc.setSqlAfter(stc2.getSqlAfter());
				newStc.addAllColumnList(stc2.getColumnConfigList());
				newStc.setCreatePK(stc2.isCreatePK());
				newStc.setCreatePartition(stc2.isCreatePartition());
				newStc.setEnableExpOpt(stc2.isEnableExpOpt());
				newStc.setStartFromTargetMax(stc2.isStartFromTargetMax());
				newStc.setFKs(stc2.getFKConfigList());
				newStc.setIndexes(stc2.getIndexConfigList());
				newStc.setCondition(stc2.getCondition());
				
				exporter.exportTableRecords(newStc, new RecordExportedListener() {
					public void processRecords(String sourceTableName, List<Record> records) {
						eventHandler.handleEvent(new ExportRecordsEvent(newStc, records.size()));
						ImportTask task = taskFactory.createImportRecordsTask(newStc, records);

						importTaskExecutor = mrManager.getImportRecordExecutor();
						importTaskExecutor.execute((Runnable) task);
						mrManager.getStatusMgr().addExpCount(newStc.getOwner(), newStc.getName(), records.size());
					}

					public void startExportTable(String tableName) {
						eventHandler.handleEvent(new StartExpTableEvent(newStc));
					}

					public void endExportTable(String tableName) {
						mrManager.getStatusMgr().setExpFinished(newStc.getOwner(), newStc.getName());
					}
				});
			}
		} else {
			exporter.exportTableRecords(sourceTable, new RecordExportedListener() {
				public void processRecords(String sourceTableName, List<Record> records) {
					eventHandler.handleEvent(new ExportRecordsEvent(sourceTable, records.size()));
					ImportTask task = taskFactory.createImportRecordsTask(sourceTable, records);

					importTaskExecutor = mrManager.getImportRecordExecutor();
					importTaskExecutor.execute((Runnable) task);
					mrManager.getStatusMgr().addExpCount(sourceTable.getOwner(), sourceTable.getName(), records.size());
				}

				public void startExportTable(String tableName) {
					eventHandler.handleEvent(new StartExpTableEvent(sourceTable));
				}

				public void endExportTable(String tableName) {
					mrManager.getStatusMgr().setExpFinished(sourceTable.getOwner(), sourceTable.getName());
				}
			});
		}
	}

	public SourceTableConfig getSourceTable() {
		return sourceTable;
	}
}
