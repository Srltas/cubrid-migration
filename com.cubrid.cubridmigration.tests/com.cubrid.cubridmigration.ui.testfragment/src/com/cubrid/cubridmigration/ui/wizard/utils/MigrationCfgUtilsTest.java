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
package com.cubrid.cubridmigration.ui.wizard.utils;

import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.datatype.DataTypeConstant;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.FK;
import com.cubrid.cubridmigration.core.dbobject.Index;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceColumnConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSQLTableConfig;
import com.cubrid.cubridmigration.core.engine.template.TemplateParserTest;
import com.cubrid.cubridmigration.cubrid.CUBRIDDataTypeHelper;
import com.cubrid.cubridmigration.ui.wizard.IMigrationWizardStatus;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** @author Kevin Cao */
public class MigrationCfgUtilsTest {

    private static final class MigrationWizardStatusMock implements IMigrationWizardStatus {

        private boolean isTargetOffline;
        private boolean isSourceOffline;

        public MigrationWizardStatusMock(boolean isSourceOffline, boolean isTargetOffline) {
            this.isSourceOffline = isSourceOffline;
            this.isTargetOffline = isTargetOffline;
        }

        @Override
        public boolean isSourceOfflineMode() {
            return isSourceOffline;
        }

        @Override
        public boolean isTargetOfflineMode() {
            return isTargetOffline;
        }
    }

    private static final String CODE = "code";

    /** @param config */
    protected Catalog getFakeTargetCatalog(MigrationConfiguration config) {
        Catalog catalog = new Catalog();
        ConnParameters targetConParams = config.getTargetConParams();
        catalog.setName(targetConParams.getDbName());
        catalog.setConnectionParameters(targetConParams);
        Schema schema = new Schema();
        schema.setName(targetConParams.getDbName());
        catalog.addSchema(schema);
        Table codeTable = new Table();
        codeTable.setName(CODE);
        schema.addTable(codeTable);
        return catalog;
    }

    @Test
    public void test_changeColumnOrder() throws Exception {
        MigrationConfiguration config = TemplateParserTest.get_OL_CUBRID2CUBRIDConfig();
        SourceEntryTableConfig setc = config.getExpEntryTableCfg("", CODE);
        assertNotNull(setc);
        Table tt = config.getTargetTableSchema(setc.getTarget());

        List<SourceColumnConfig> columns = setc.getColumnConfigList();
        // move first to last
        SourceColumnConfig first = columns.get(0);
        columns.remove(first);
        columns.add(first);
        MigrationCfgUtils.changeColumnOrder(setc, tt, columns);
        Column tcol = tt.getColumns().get(columns.size() - 1);
        assertEquals(first.getTarget(), tcol.getName());
    }

    @Test
    public void test_checkAllIllegal() throws Exception {
        MigrationConfiguration config = TemplateParserTest.get_OL_CUBRID2CUBRIDConfig();
        MigrationCfgUtils util = new MigrationCfgUtils();
        util.setMigrationConfiguration(config);
        util.setTargetCatalog(null, new MigrationWizardStatusMock(false, true));

        // No object to be migrated.
        config.setAll(false);
        VerifyResultMessages rvm = util.checkAll(config);
        // setc.setTarget("aaabb[]b");
        assertTrue(rvm.hasError());
        assertNotNull(rvm.getErrorMessage());
    }

    @Test
    public void test_checkAllLegal() throws Exception {
        MigrationConfiguration config = TemplateParserTest.get_OL_CUBRID2CUBRIDConfig();
        MigrationCfgUtils util = new MigrationCfgUtils();
        util.setMigrationConfiguration(config);
        util.setTargetCatalog(null, new MigrationWizardStatusMock(false, true));

        VerifyResultMessages rvm = util.checkAll(config);
        assertFalse(rvm.hasError());
        assertTrue(rvm.hasConfirm());
        assertFalse(rvm.hasWarning());
        assertNull(rvm.getErrorMessage());

        config = TemplateParserTest.get_OL_CUBRID2CSVConfig();
        util.setMigrationConfiguration(config);
        rvm = util.checkAll(config);
        assertFalse(rvm.hasError());
        assertTrue(rvm.hasConfirm());
        assertFalse(rvm.hasWarning());

        config = TemplateParserTest.get_OL_CUBRID2DumpConfig();
        util.setMigrationConfiguration(config);
        rvm = util.checkAll(config);
        assertFalse(rvm.hasError());
        assertTrue(rvm.hasConfirm());
        assertFalse(rvm.hasWarning());

        config = TemplateParserTest.get_OL_CUBRID2SQLConfig();
        util.setMigrationConfiguration(config);
        rvm = util.checkAll(config);
        assertFalse(rvm.hasError());
        assertTrue(rvm.hasConfirm());
        assertFalse(rvm.hasWarning());

        config = TemplateParserTest.get_OL_CUBRID2XLSConfig();
        util.setMigrationConfiguration(config);
        rvm = util.checkAll(config);
        assertFalse(rvm.hasError());
        assertTrue(rvm.hasConfirm());
        assertFalse(rvm.hasWarning());

        config = TemplateParserTest.get_LF_CSV2CUBRIDConfig();
        util.setMigrationConfiguration(config);
        rvm = util.checkAll(config);
        assertFalse(rvm.hasError());
        assertFalse(rvm.hasConfirm());
        assertFalse(rvm.hasWarning());
    }

    @Test
    public void test_checkEntryTableCondition() throws Exception {
        MigrationConfiguration config = TemplateParserTest.get_OL_CUBRID2CUBRIDConfig();
        SourceEntryTableConfig setc = config.getExpEntryTableCfg("", CODE);
        assertNotNull(setc);

        setc.setCondition(" f_name is not null");
        assertTrue(
                MigrationCfgUtils.checkEntryTableCondition(
                        config, false, setc.getTarget(), setc.getCondition()));
        assertTrue(
                MigrationCfgUtils.checkEntryTableCondition(
                        config, true, setc.getTarget(), setc.getCondition()));
        assertTrue(
                MigrationCfgUtils.checkEntryTableCondition(config, true, setc.getTarget(), ""));
        assertFalse(
                MigrationCfgUtils.checkEntryTableCondition(
                        config, true, setc.getTarget(), " asdfsb "));
    }

    @Test
    public void test_checkEntryTableConfig() throws Exception {
        MigrationConfiguration config = TemplateParserTest.get_OL_CUBRID2CUBRIDConfig();
        MigrationCfgUtils util = new MigrationCfgUtils();
        util.setMigrationConfiguration(config);
        util.setTargetCatalog(null, new MigrationWizardStatusMock(false, true));

        SourceEntryTableConfig setc = config.getExpEntryTableCfg("", CODE);
        setc.setCreateNewTable(false);
        setc.setMigrateData(false);
        VerifyResultMessages rvm = util.checkEntryTableCfg(config, setc);
        assertFalse(rvm.hasError());
        assertFalse(rvm.hasWarning());
        assertFalse(rvm.hasConfirm());

        setc.setCreateNewTable(true);
        setc.setMigrateData(true);

        try {
            setc.setTarget("code1");
            util.checkEntryTableCfg(config, setc);
            // assertNotNull(rvm.getErrorMessage());
        } catch (MigrationConfigurationCheckingErrorException ex) {
            // If this exception thrown, the test is passed.
            System.out.println(ex.getMessage());
        }

        try {
            setc.setTarget(CODE);
            setc.setName("code1");
            util.checkEntryTableCfg(config, setc);
            // assertNotNull(rvm.getErrorMessage());
        } catch (MigrationConfigurationCheckingErrorException ex) {
            // If this exception thrown, the test is passed.
            System.out.println(ex.getMessage());
        }

        try {
            setc.setTarget(CODE);
            setc.setName(CODE);
            setc.setCondition("test false");
            util.checkEntryTableCfg(config, setc);
            // assertNotNull(rvm.getErrorMessage());
        } catch (MigrationConfigurationCheckingErrorException ex) {
            // If this exception thrown, the test is passed.
            System.out.println(ex.getMessage());
        }

        setc.setCondition("");
        try {
            setc.setTarget("");
            util.checkEntryTableCfg(config, setc);
            // assertNotNull(rvm.getErrorMessage());
        } catch (MigrationConfigurationCheckingErrorException ex) {
            // If this exception thrown, the test is passed.
            System.out.println(ex.getMessage());
        }
    }

    @Test
    public void test_checkTableIsInTargetDb() throws Exception {
        MigrationConfiguration config = TemplateParserTest.get_OL_CUBRID2CUBRIDConfig();
        MigrationCfgUtils util = new MigrationCfgUtils();
        util.setMigrationConfiguration(config);
        Catalog catalog = getFakeTargetCatalog(config);
        util.setTargetCatalog(catalog, new MigrationWizardStatusMock(false, false));

        SourceEntryTableConfig setc = config.getExpEntryTableCfg("", CODE);
        setc.setReplace(false);
        StringBuffer sbConfirm = new StringBuffer();
        try {
            Table targetTable = config.getTargetTableSchema(CODE);
            util.checkTableIsInTargetDb(config, setc, targetTable, sbConfirm);
        } catch (MigrationConfigurationCheckingErrorException ex) {
            // If this exception thrown, the test is passed.
            System.out.println(ex.getMessage());
        }

        sbConfirm = new StringBuffer();
        setc.setReplace(true);
        Table targetTable = config.getTargetTableSchema(CODE);
        util.checkTableIsInTargetDb(config, setc, targetTable, sbConfirm);
        assertTrue(sbConfirm.length() > 0);

        try {
            sbConfirm = new StringBuffer();
            setc = config.getExpEntryTableCfg("", "olympic");
            setc.setCreateNewTable(false);
            targetTable = config.getTargetTableSchema("olympic");
            util.checkTableIsInTargetDb(config, setc, targetTable, sbConfirm);
            assertTrue(sbConfirm.length() > 0);
        } catch (MigrationConfigurationCheckingErrorException ex) {
            // If this exception thrown, the test is passed.
            System.out.println(ex.getMessage());
        }
    }

    @Test
    public void test_checkTableColumns() throws Exception {
        MigrationConfiguration config = TemplateParserTest.get_OL_CUBRID2CUBRIDConfig();
        MigrationCfgUtils util = new MigrationCfgUtils();
        util.setMigrationConfiguration(config);
        util.setTargetCatalog(null, new MigrationWizardStatusMock(false, true));

        SourceEntryTableConfig setc = config.getExpEntryTableCfg("", CODE);
        SourceColumnConfig scc1 = setc.getColumnConfigList().get(0);
        SourceColumnConfig scc2 = setc.getColumnConfigList().get(1);

        String name1 = scc1.getName();
        String name2 = scc2.getName();

        Table tt = config.getTargetTableSchema(CODE);
        Table st = config.getSrcTableSchema(null, CODE);

        StringBuffer sbConfirm = new StringBuffer();
        try {
            scc1.setCreate(false);
            scc2.setCreate(false);
            util.checkTableColumns(config, setc, st, tt, sbConfirm);
            // assertNotNull(rvm.getErrorMessage());
        } catch (MigrationConfigurationCheckingErrorException ex) {
            // If this exception thrown, the test is passed.
            System.out.println(ex.getMessage());
            scc1.setCreate(true);
            scc2.setCreate(true);
        }
        assertTrue(scc1.isCreate());

        sbConfirm = new StringBuffer();
        try {
            scc1.setName("");
            util.checkTableColumns(config, setc, st, tt, sbConfirm);
            // assertNotNull(rvm.getErrorMessage());
        } catch (MigrationConfigurationCheckingErrorException ex) {
            // If this exception thrown, the test is passed.
            System.out.println(ex.getMessage());
            scc1.setName(name1);
        }
        assertEquals(name1, scc1.getName());

        sbConfirm = new StringBuffer();
        try {
            scc1.setTarget("");
            util.checkTableColumns(config, setc, st, tt, sbConfirm);
            // assertNotNull(rvm.getErrorMessage());
        } catch (MigrationConfigurationCheckingErrorException ex) {
            // If this exception thrown, the test is passed.
            System.out.println(ex.getMessage());
            scc1.setTarget(name1);
        }
        assertEquals(name1, scc1.getTarget());

        sbConfirm = new StringBuffer();
        try {
            scc2.setTarget(name1);
            util.checkTableColumns(config, setc, st, tt, sbConfirm);
            // assertNotNull(rvm.getErrorMessage());
        } catch (MigrationConfigurationCheckingErrorException ex) {
            // If this exception thrown, the test is passed.
            System.out.println(ex.getMessage());
            scc2.setTarget(name2);
        }
        assertEquals(name2, scc2.getTarget());

        sbConfirm = new StringBuffer();
        Column tcol2 = tt.getColumnByName(name2);
        String sdt = tcol2.getShownDataType();
        tcol2.setShownDataType("int(1)");
        try {
            util.checkTableColumns(config, setc, st, tt, sbConfirm);
            // assertNotNull(rvm.getErrorMessage());
        } catch (MigrationConfigurationCheckingErrorException ex) {
            // If this exception thrown, the test is passed.
            System.out.println(ex.getMessage());
            tcol2.setShownDataType(sdt);
        }
        assertEquals(sdt, tcol2.getShownDataType());

        sbConfirm = new StringBuffer();
        sdt = tcol2.getDataType();
        tcol2.setDataType("int");
        util.checkTableColumns(config, setc, st, tt, sbConfirm);
        assertEquals("int", tcol2.getDataType());
        assertTrue(sbConfirm.length() > 0);
        tcol2.setDataType(sdt);

        sbConfirm = new StringBuffer();
        Integer precision = tcol2.getPrecision();
        tcol2.setPrecision(precision - 1);
        util.checkTableColumns(config, setc, st, tt, sbConfirm);
        assertTrue(sbConfirm.length() > 0);
        tcol2.setPrecision(precision);
    }

    @Test
    public void test_checkEntryTableConstrains_FK() throws Exception {
        MigrationConfiguration config = TemplateParserTest.get_OL_CUBRID2CUBRIDConfig();
        MigrationCfgUtils util = new MigrationCfgUtils();
        util.setMigrationConfiguration(config);
        util.setTargetCatalog(null, new MigrationWizardStatusMock(false, true));

        StringBuffer sbConfirm = new StringBuffer();
        SourceEntryTableConfig setc = config.getExpEntryTableCfg(null, "game");
        Table targetTable = config.getTargetTableSchema("game");
        StringBuffer sbWarn = new StringBuffer();
        util.checkEntryTableConstrains(setc, targetTable, sbWarn, sbConfirm);

        assertTrue(sbWarn.length() == 0);
        assertTrue(sbConfirm.length() == 0);

        FK fk = targetTable.getFKByName("fk_game_event_code");
        fk.setName("111");
        try {
            util.checkEntryTableConstrains(setc, targetTable, sbWarn, sbConfirm);
        } catch (MigrationConfigurationCheckingErrorException ex) {
            System.out.println(ex.getMessage());
            fk.setName("fk_game_event_code");
        }
        assertEquals("fk_game_event_code", fk.getName());

        int updateRule = fk.getUpdateRule();
        Assert.assertNotSame(updateRule, 0);
        fk.setUpdateRule(0);
        try {
            util.checkEntryTableConstrains(setc, targetTable, sbWarn, sbConfirm);
        } catch (MigrationConfigurationCheckingErrorException ex) {
            System.out.println(ex.getMessage());
            fk.setUpdateRule(updateRule);
        }
        assertEquals(updateRule, fk.getUpdateRule());

        Map<String, String> fkColumns = fk.getColumns();

        fk.setColumns(new TreeMap<String, String>());
        try {
            util.checkEntryTableConstrains(setc, targetTable, sbWarn, sbConfirm);
        } catch (MigrationConfigurationCheckingErrorException ex) {
            System.out.println(ex.getMessage());
            fk.setColumns(fkColumns);
        }
        assertEquals(fkColumns.size(), fk.getColumns().size());

        Map<String, String> errorColumns = new TreeMap<String, String>();
        errorColumns.put("ttt", "desc");
        fk.setColumns(errorColumns);
        try {
            util.checkEntryTableConstrains(setc, targetTable, sbWarn, sbConfirm);
        } catch (MigrationConfigurationCheckingErrorException ex) {
            System.out.println(ex.getMessage());
            fk.setColumns(fkColumns);
        }
        assertEquals(fkColumns.size(), fk.getColumns().size());
    }

    @Test
    public void test_checkEntryTableConstrains_Index() throws Exception {
        MigrationConfiguration config = TemplateParserTest.get_OL_CUBRID2CUBRIDConfig();
        MigrationCfgUtils util = new MigrationCfgUtils();
        util.setMigrationConfiguration(config);
        util.setTargetCatalog(null, new MigrationWizardStatusMock(false, true));

        StringBuffer sbConfirm = new StringBuffer();
        SourceEntryTableConfig setc = config.getExpEntryTableCfg(null, "game");
        Table targetTable = config.getTargetTableSchema("game");
        StringBuffer sbWarn = new StringBuffer();
        util.checkEntryTableConstrains(setc, targetTable, sbWarn, sbConfirm);

        assertTrue(sbWarn.length() == 0);
        assertTrue(sbConfirm.length() == 0);

        Index idx = targetTable.getIndexByName("idx_game_game_date");
        idx.setName("111");
        try {
            util.checkEntryTableConstrains(setc, targetTable, sbWarn, sbConfirm);
        } catch (MigrationConfigurationCheckingErrorException ex) {
            System.out.println(ex.getMessage());
            idx.setName("idx_game_game_date");
        }
        assertEquals("idx_game_game_date", idx.getName());

        Map<String, Boolean> idxColumns = idx.getIndexColumns();
        Map<String, Boolean> errorColumns = new TreeMap<String, Boolean>();
        idx.setIndexColumns(errorColumns);
        try {
            util.checkEntryTableConstrains(setc, targetTable, sbWarn, sbConfirm);
        } catch (MigrationConfigurationCheckingErrorException ex) {
            System.out.println(ex.getMessage());
            idx.setIndexColumns(idxColumns);
        }
        assertEquals(idxColumns.size(), idx.getIndexColumns().size());

        errorColumns = new TreeMap<String, Boolean>();
        errorColumns.put("substr(date)", true);
        idx.setIndexColumns(errorColumns);
        sbConfirm = new StringBuffer();
        sbWarn = new StringBuffer();
        util.checkEntryTableConstrains(setc, targetTable, sbWarn, sbConfirm);
        assertTrue(sbWarn.length() > 0);

        errorColumns = new TreeMap<String, Boolean>();
        errorColumns.put("ddd", true);
        idx.setIndexColumns(errorColumns);
        sbConfirm = new StringBuffer();
        sbWarn = new StringBuffer();
        util.checkEntryTableConstrains(setc, targetTable, sbWarn, sbConfirm);
        assertTrue(sbConfirm.length() > 0);
    }

    @Test
    public void test_checkSQLTableCfg() throws Exception {
        MigrationConfiguration config = TemplateParserTest.get_OL_CUBRID2CUBRIDConfig();
        MigrationCfgUtils util = new MigrationCfgUtils();
        util.setMigrationConfiguration(config);
        util.setTargetCatalog(null, new MigrationWizardStatusMock(false, true));

        SourceSQLTableConfig sstc = new SourceSQLTableConfig();
        sstc.setCreateNewTable(true);
        sstc.setReplace(true);
        sstc.setMigrateData(true);
        String sqlTableName = "test_sql";
        sstc.setName(sqlTableName);
        sstc.setTarget(sqlTableName);
        sstc.setSql("select * from game");
        config.addExpSQLTableCfgWithST(sstc);

        VerifyResultMessages vrm = util.checkSQLTableCfg(config, sstc);
        assertFalse(vrm.hasError());
        assertFalse(vrm.hasWarning());
        assertFalse(vrm.hasConfirm());

        sstc.setCreateNewTable(false);
        sstc.setReplace(true);
        sstc.setMigrateData(false);
        vrm = util.checkSQLTableCfg(config, sstc);
        assertFalse(vrm.hasError());
        assertFalse(vrm.hasWarning());
        assertFalse(vrm.hasConfirm());
        sstc.setCreateNewTable(true);
        sstc.setReplace(true);
        sstc.setMigrateData(true);

        sstc.setTarget("");
        try {
            vrm = util.checkSQLTableCfg(config, sstc);
        } catch (MigrationConfigurationCheckingErrorException ex) {
            System.out.println(ex.getMessage());
            sstc.setTarget(sqlTableName);
        }
        assertEquals(sqlTableName, sstc.getTarget());

        sstc.setName("test");
        try {
            vrm = util.checkSQLTableCfg(config, sstc);
        } catch (MigrationConfigurationCheckingErrorException ex) {
            System.out.println(ex.getMessage());
            sstc.setName(sqlTableName);
        }
        assertEquals(sqlTableName, sstc.getName());
    }

    @Test
    public void test_doesNeedToChangeCharacterTypeSize() {
        MigrationConfiguration config = new MigrationConfiguration();
        MigrationCfgUtils util = new MigrationCfgUtils();
        util.setMigrationConfiguration(config);
        util.setTargetCatalog(null, new MigrationWizardStatusMock(false, true));

        config.setSourceType(MigrationConfiguration.SOURCE_TYPE_MYSQL);
        assertTrue(util.doesNeedToChangeCharacterTypeSize());

        config.setSourceType(MigrationConfiguration.SOURCE_TYPE_ORACLE);
        assertTrue(util.doesNeedToChangeCharacterTypeSize());

        config.setSourceType(MigrationConfiguration.SOURCE_TYPE_XML_1);
        assertTrue(util.doesNeedToChangeCharacterTypeSize());

        config.setSourceType(MigrationConfiguration.SOURCE_TYPE_MSSQL);
        assertFalse(util.doesNeedToChangeCharacterTypeSize());

        config.setSourceType(MigrationConfiguration.SOURCE_TYPE_CUBRID);
        assertFalse(util.doesNeedToChangeCharacterTypeSize());

        config.setSourceType(MigrationConfiguration.SOURCE_TYPE_CSV);
        assertFalse(util.doesNeedToChangeCharacterTypeSize());

        config.setSourceType(MigrationConfiguration.SOURCE_TYPE_SQL);
        assertFalse(util.doesNeedToChangeCharacterTypeSize());
    }

    @Test
    public void test_getRightCharacerTypePrecision() {
        MigrationCfgUtils util = new MigrationCfgUtils();
        int precision = util.getRightCharacerTypePrecision(-1);
        assertEquals(DataTypeConstant.CUBRID_MAXSIZE, precision);

        precision = util.getRightCharacerTypePrecision(DataTypeConstant.CUBRID_MAXSIZE + 1);
        assertEquals(DataTypeConstant.CUBRID_MAXSIZE, precision);

        precision = util.getRightCharacerTypePrecision(100);
        assertEquals(100, precision);
    }

    @Test
    public void test_isHACUBRID() throws Exception {
        MigrationConfiguration config = TemplateParserTest.get_OL_CUBRID2DumpConfig();
        assertFalse(MigrationCfgUtils.isHACUBRID(config));
        config = TemplateParserTest.get_OL_CUBRID2CUBRIDConfig();
        assertFalse(MigrationCfgUtils.isHACUBRID(config));
        config.setTargetConParams(null);
        assertFalse(MigrationCfgUtils.isHACUBRID(config));
    }

    @Test
    public void test_multiplyCharColumn() throws Exception {
        MigrationConfiguration config = TemplateParserTest.get_OL_Oracle2CUBRIDConfig();
        MigrationCfgUtils util = new MigrationCfgUtils();
        util.setMigrationConfiguration(config);
        util.setTargetCatalog(null, new MigrationWizardStatusMock(false, true));

        Table srcTable = config.getSrcCatalog().getSchemas().get(0).getTableByName("CODE");
        Table tarTable = config.getTargetTableSchema(CODE);

        Column sCol = srcTable.getColumns().get(0);
        Column tCol = tarTable.getColumns().get(0);

        util.multiplyCharColumn(sCol, tCol, 3);
        assertNotNull(tarTable);
        assertEquals(new Integer(3), tCol.getPrecision());

        srcTable = config.getSrcCatalog().getSchemas().get(0).getTableByName("ATHLETE");
        tarTable = config.getTargetTableSchema("athlete");

        sCol = srcTable.getColumns().get(0);
        tCol = tarTable.getColumns().get(0);

        util.multiplyCharColumn(sCol, tCol, 3);
        assertNotNull(tarTable);
        assertEquals("int", tCol.getDataType());
        assertEquals(new Integer(0), tCol.getPrecision());
    }

    @Test
    public void test_setCharTypeColumnToDefaultMapping() throws Exception {
        MigrationConfiguration config = TemplateParserTest.get_OL_Oracle2CUBRIDConfig();
        MigrationCfgUtils util = new MigrationCfgUtils();
        util.setMigrationConfiguration(config);
        util.setTargetCatalog(null, new MigrationWizardStatusMock(false, true));

        Table srcCodeTable = config.getSrcCatalog().getSchemas().get(0).getTableByName("CODE");
        Table codeTable = config.getTargetTableSchema(CODE);

        Column sCol = srcCodeTable.getColumns().get(0);
        Column tCol = codeTable.getColumns().get(0);

        util.setCharTypeColumnToDefaultMapping(sCol, tCol);
        assertNotNull(codeTable);
        assertEquals(new Integer(1), tCol.getPrecision());
    }

    @Test
    public void test_verifyTargetDBObjName() {
        assertFalse(MigrationCfgUtils.verifyTargetDBObjName(null));
        assertFalse(MigrationCfgUtils.verifyTargetDBObjName(""));
        assertFalse(MigrationCfgUtils.verifyTargetDBObjName("tes[t"));
        assertFalse(MigrationCfgUtils.verifyTargetDBObjName("test]"));
        assertFalse(MigrationCfgUtils.verifyTargetDBObjName("te\"st"));
        assertTrue(MigrationCfgUtils.verifyTargetDBObjName("test"));
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < CUBRIDDataTypeHelper.DB_OBJ_NAME_MAX_LENGTH; i++) {
            sb.append("a");
        }
        assertTrue(MigrationCfgUtils.verifyTargetDBObjName(sb.toString()));
        sb.append("a");
        assertFalse(MigrationCfgUtils.verifyTargetDBObjName(sb.toString()));
    }

    @Test
    public void test_getNoPKTables() throws Exception {
        MigrationConfiguration config = TemplateParserTest.get_OL_CUBRID2CUBRIDConfig();
        MigrationCfgUtils util = new MigrationCfgUtils();
        util.setMigrationConfiguration(config);
        util.setTargetCatalog(null, new MigrationWizardStatusMock(false, true));

        List<String> noPkTableNames = util.getNoPKTables(config.getSrcCatalog());
        assertTrue(noPkTableNames.size() == 4);
    }
}
