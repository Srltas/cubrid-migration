package com.cmt.e2e.tests.migration.cubrid;

import java.io.IOException;
import java.util.List;

import com.cmt.e2e.framework.assertion.strategies.MigrationSummaryVerificationStrategy;
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

@Tag("db-required")
@Testcontainers
public class CubridToDumpTest {

    /** CMT Console이 Dump 마이그레이션 시 생성하는 파일 목록 */
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
    @DisplayName("CUBRID(demodb) to Dump File Migration")
    void cubridToDumpFileMigration() throws IOException, InterruptedException {
        // Arrange
        ResolvedScript resolved = ScriptTemplateResolver.builder()
            .template(ctx.testPaths().getResourceDir().resolve("script.xml"))
            .source(sourceDb)
            .outputDir(ctx.testPaths().getArtifactDir())
            .scriptFileName("CUBRID_to_DumpFile.xml")
            .build()
            .resolve();

        // Act
        StartCommand startCommand = StartCommand.builder().script(resolved.scriptPath()).build();
        CommandResult result = ctx.commandRunner().run(startCommand);

        // Assert 1: CLI 출력 검증 (마이그레이션 요약 리포트)
        ctx.verifier().verifyWith(result, "expected_summary.answer",
            new MigrationSummaryVerificationStrategy());

        // Assert 2: Dump 파일 구조 및 내용 검증
        // migration name은 script.xml의 <migration name="..."> 값을 그대로 사용합니다.
        ctx.migrationOutput(resolved.migrationName(), "PUBLIC")
            .assertFilesExist(EXPECTED_DUMP_FILES)
            .verifySchemaFile("demodb_PUBLIC_class", "expected_class.answer")
            .verifySchemaFile("demodb_PUBLIC_fk", "expected_fk.answer")
            .verifySchemaFile("demodb_PUBLIC_pk", "expected_pk.answer")
            .verifySchemaFile("demodb_PUBLIC_serial", "expected_serial.answer")
            .verifySchemaFile("demodb_PUBLIC_updatestatistic", "expected_updatestatistic.answer")
            .verifySchemaFile("demodb_PUBLIC_info", "expected_info.answer")
            .verifyObjectFile("demodb_PUBLIC_object", "expected_object.answer");
    }
}
