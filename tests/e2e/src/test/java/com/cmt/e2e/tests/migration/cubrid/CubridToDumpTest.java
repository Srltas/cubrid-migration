package com.cmt.e2e.tests.migration.cubrid;

import java.io.IOException;
import java.util.List;

import com.cmt.e2e.framework.assertion.MigrationAsserts;
import com.cmt.e2e.framework.command.execution.CommandResult;
import com.cmt.e2e.framework.command.impls.StartCommand;
import com.cmt.e2e.framework.core.CmtTestContext;
import com.cmt.e2e.framework.db.containers.CubridContainer;
import com.cmt.e2e.framework.db.containers.DatabaseContainer;
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
 * E2E test for CUBRID (demodb) -> CMT dump file migration.
 */
@Tag("db-required")
@Testcontainers
@DisplayName("CUB-DO-01: CUBRID demodb -> dump file migration")
public class CubridToDumpTest {

    private static final List<String> EXPECTED_DUMP_FILES = List.of(
        "PUBLIC_clear.sql",
        "PUBLIC_drop_fk.sql",
        "PUBLIC_truncate.sql",
        "demodb_PUBLIC_class",
        "demodb_PUBLIC_fk",
        "demodb_PUBLIC_info",
        "demodb_PUBLIC_object",
        "demodb_PUBLIC_pk",
        "demodb_PUBLIC_serial",
        "demodb_PUBLIC_updatestatistic"
    );

    @RegisterExtension
    final CmtTestContext ctx = CmtTestContext.builder().build();

    @Container
    private final DatabaseContainer sourceDb = CubridContainer.withDemodb();

    @Test
    @TestResources("migration/cubrid/cubrid_to_dumpfile")
    @DisplayName("generates dump files and reports MIGRATION RESULT: SUCCESS")
    void should_generateDumpFiles_when_sourceIsCubridDemodb() throws IOException, InterruptedException {
        // Arrange
        ResolvedScript resolved = ScriptTemplateResolver.builder()
            .template(ctx.testPaths().getResourceDir().resolve("script.xml"))
            .source(sourceDb)
            .outputDir(ctx.testPaths().getArtifactDir())
            .scriptFileName("CUBRID_to_DumpFile.xml")
            .build()
            .resolve();

        // Act
        CommandResult result = ctx.commandRunner().run(
            StartCommand.builder().script(resolved.scriptPath()).build());

        // Assert
        MigrationAsserts.assertMigrationSucceeded(result);
        ctx.migrationOutput(resolved.migrationName(), "PUBLIC")
            .assertFilesExist(EXPECTED_DUMP_FILES);
    }
}
