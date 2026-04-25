package com.cmt.e2e.tests.migration.oracle;

import java.util.List;

import com.cmt.e2e.framework.assertion.MigrationAsserts;
import com.cmt.e2e.framework.command.execution.CommandResult;
import com.cmt.e2e.framework.command.impls.StartCommand;
import com.cmt.e2e.framework.core.CmtTestContext;
import com.cmt.e2e.framework.db.containers.OracleContainer;
import com.cmt.e2e.framework.db.init.OracleDatabaseInitializer;
import com.cmt.e2e.framework.junit.annotation.TestResources;
import com.cmt.e2e.framework.template.ResolvedScript;
import com.cmt.e2e.framework.template.ScriptTemplateResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * E2E test for Oracle 11g (two users, full coverage)
 * -> CMT dump file migration.
 *
 * <p>All artifacts listed in {@link #EXPECTED_DUMP_FILES} must be generated.
 * If the outputs differ from expectations, detailed diagnostics remain in
 * the per-test log.
 */
@Tag("db-required")
@Testcontainers
@DisplayName("ORA-DO-01: Oracle full_coverage -> dump file migration")
public class OracleToDumpTest {

    private static final List<String> EXPECTED_DUMP_FILES = List.of(
        "CMT_TEST_clear.sql",
        "CMT_TEST_truncate.sql",
        "CMT_TEST_drop_fk.sql",
        "XE_CMT_TEST_class",
        "XE_CMT_TEST_pk",
        "XE_CMT_TEST_fk",
        "XE_CMT_TEST_uk",
        "XE_CMT_TEST_indexes",
        "XE_CMT_TEST_serial",
        "XE_CMT_TEST_info",
        "XE_CMT_TEST_updatestatistic",
        "XE_CMT_TEST_vclass",
        "XE_CMT_TEST_vclass_query_spec",
        "XE_CMT_TEST_function",
        "XE_CMT_TEST_function_header",
        "XE_CMT_TEST_procedure",
        "XE_CMT_TEST_procedure_header",
        "XE_CMT_TEST_synonym",
        "XE_CMT_TEST_grant.CMT_OWNER"
    );

    @RegisterExtension
    final CmtTestContext ctx = CmtTestContext.builder().build();

    @Container
    private final OracleContainer sourceDb = OracleContainer.withTwoUsers();

    @Test
    @TestResources("migration/oracle/oracle_to_dumpfile")
    @DisplayName("generates dump files and reports MIGRATION RESULT: SUCCESS")
    void should_generateDumpFiles_when_sourceIsOracleOnline() throws Exception {
        // Arrange
        OracleDatabaseInitializer.of(sourceDb)
            .migrateAs(sourceDb.getOwnerUser(), sourceDb.getOwnerPassword(), "oracle/full_coverage/owner")
            .migrateAs(sourceDb.getAppUser(),   sourceDb.getAppPassword(),   "oracle/full_coverage/test");

        ResolvedScript resolved = ScriptTemplateResolver.builder()
            .template(ctx.testPaths().getResourceDir().resolve("script.xml"))
            .source(sourceDb)
            .outputDir(ctx.testPaths().getArtifactDir())
            .scriptFileName("Oracle_to_DumpFile.xml")
            .build()
            .resolve();

        // Act
        CommandResult result = ctx.commandRunner().run(
            StartCommand.builder().script(resolved.scriptPath()).build());

        // Assert
        MigrationAsserts.assertMigrationSucceeded(result);
        ctx.migrationOutput(resolved.migrationName(), "CMT_TEST")
            .assertFilesExist(EXPECTED_DUMP_FILES);
    }
}
