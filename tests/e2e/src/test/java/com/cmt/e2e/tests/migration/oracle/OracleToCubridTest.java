package com.cmt.e2e.tests.migration.oracle;

import java.util.Map;

import com.cmt.e2e.framework.assertion.DatabaseAsserts;
import com.cmt.e2e.framework.command.impls.StartCommand;
import com.cmt.e2e.framework.core.CmtTestContext;
import com.cmt.e2e.framework.db.containers.CubridContainer;
import com.cmt.e2e.framework.db.containers.DatabaseContainer;
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
 * -> CUBRID online migration.
 *
 * <p>Oracle-specific features such as REVERSE indexes and ROWID may not map
 * cleanly to CUBRID, so this test does not assert the process exit code.
 * It validates the actual row counts in the target CUBRID database instead.
 */
@Tag("db-required")
@Testcontainers
@DisplayName("ORA-ON-01: Oracle full_coverage -> CUBRID online migration")
public class OracleToCubridTest {

    @RegisterExtension
    final CmtTestContext ctx = CmtTestContext.builder().build();

    @Container
    private final OracleContainer sourceDb = OracleContainer.withTwoUsers();

    @Container
    private final DatabaseContainer targetDb = CubridContainer.withEmptyDb();

    @Test
    @TestResources("migration/oracle/oracle_to_cubrid")
    @DisplayName("migrates CMT_TEST schema data into CUBRID")
    void should_migrateToCubrid_when_sourceIsOracleOnline() throws Exception {
        // Arrange: initialize two-user Oracle and resolve the script template
        OracleDatabaseInitializer.of(sourceDb)
            .migrateAs(sourceDb.getOwnerUser(), sourceDb.getOwnerPassword(), "oracle/full_coverage/owner")
            .migrateAs(sourceDb.getAppUser(),   sourceDb.getAppPassword(),   "oracle/full_coverage/test");

        ResolvedScript resolved = ScriptTemplateResolver.builder()
            .template(ctx.testPaths().getResourceDir().resolve("script.xml"))
            .source(sourceDb)
            .target(targetDb)
            .outputDir(ctx.testPaths().getArtifactDir())
            .scriptFileName("Oracle_to_CUBRID.xml")
            .build()
            .resolve();

        // Act
        ctx.commandRunner().run(StartCommand.builder().script(resolved.scriptPath()).build());

        // Assert: CMT_TEST tables were migrated with the expected row counts
        DatabaseAsserts.expectRecords(targetDb, "cubdb", "CMT_TEST", Map.ofEntries(
            Map.entry("ora_cov_customer",       3),
            Map.entry("ora_cov_orders",         3),
            Map.entry("ora_cov_order_line",     4),
            Map.entry("ora_cov_text_types",     1),
            Map.entry("ora_cov_binary_types",   1),
            Map.entry("ora_cov_numeric_types",  1),
            Map.entry("ora_cov_temporal_types", 1),
            Map.entry("ora_cov_locator_types",  1),
            Map.entry("ora_cov_program_log",    1),
            Map.entry("ora_cov_rowid_source",   1),
            Map.entry("flyway_schema_history",  6)
        ));
    }
}
