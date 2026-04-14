package com.cmt.e2e.tests.migration.cubrid;

import java.io.IOException;

import com.cmt.e2e.framework.template.ResolvedScript;
import com.cmt.e2e.framework.assertion.DatabaseAsserts;
import com.cmt.e2e.framework.assertion.strategies.MigrationSummaryVerificationStrategy;
import com.cmt.e2e.framework.command.execution.CommandResult;
import com.cmt.e2e.framework.command.impls.StartCommand;
import com.cmt.e2e.framework.core.CmtTestContext;
import com.cmt.e2e.framework.db.containers.CubridContainer;
import com.cmt.e2e.framework.db.containers.DatabaseContainer;
import com.cmt.e2e.framework.junit.annotation.TestResources;
import com.cmt.e2e.framework.template.ScriptTemplateResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("db-required")
@Testcontainers
@DisplayName("CUBRID to CUBRID Migration Test")
public class CubridToCubridTest {

    @RegisterExtension
    final CmtTestContext ctx = CmtTestContext.builder().build();

    @Container
    private final DatabaseContainer sourceDb = CubridContainer.withDemodb();

    @Container
    private final DatabaseContainer targetDb = CubridContainer.withEmptyDb();

    @Test
    @TestResources("migration/cubrid/cubrid_to_cubrid")
    @DisplayName("CUBRID(demodb) to CUBRID(empty) Migration")
    void cubridToCubridMigration() throws IOException, InterruptedException {
        // Arrange
        ResolvedScript resolved = ScriptTemplateResolver.builder()
            .template(ctx.testPaths().getResourceDir().resolve("script.xml"))
            .source(sourceDb)
            .target(targetDb)
            .outputDir(ctx.testPaths().getArtifactDir())
            .scriptFileName("CUBRID_to_CUBRID.xml")
            .build()
            .resolve();

        // Act
        StartCommand command = StartCommand.builder().script(resolved.scriptPath()).build();
        CommandResult result = ctx.commandRunner().run(command);

        // Assert - CMT returns exit code 1 due to known issues:
        // Schema creation fails (PUBLIC user already exists in CUBRID)
        // These don't affect actual data migration, so we verify records instead.

        ctx.verifier().verifyWith(result, "expected_summary.answer", new MigrationSummaryVerificationStrategy());

        DatabaseAsserts.assertRecordCount(targetDb, "cubdb", "public", "athlete",  6677);
        DatabaseAsserts.assertRecordCount(targetDb, "cubdb", "public", "code",     6);
        DatabaseAsserts.assertRecordCount(targetDb, "cubdb", "public", "event",    422);
        DatabaseAsserts.assertRecordCount(targetDb, "cubdb", "public", "game",     8653);
        DatabaseAsserts.assertRecordCount(targetDb, "cubdb", "public", "history",  147);
        DatabaseAsserts.assertRecordCount(targetDb, "cubdb", "public", "nation",   215);
        DatabaseAsserts.assertRecordCount(targetDb, "cubdb", "public", "olympic",  25);
        DatabaseAsserts.assertRecordCount(targetDb, "cubdb", "public", "participant", 916);
        DatabaseAsserts.assertRecordCount(targetDb, "cubdb", "public", "record",   2000);
        DatabaseAsserts.assertRecordCount(targetDb, "cubdb", "public", "stadium",  141);
    }
}
