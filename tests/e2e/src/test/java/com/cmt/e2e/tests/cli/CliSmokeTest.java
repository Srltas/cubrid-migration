package com.cmt.e2e.tests.cli;

import java.nio.file.Files;
import java.nio.file.Path;

import com.cmt.e2e.framework.command.execution.CommandResult;
import com.cmt.e2e.framework.command.impls.RawCommand;
import com.cmt.e2e.framework.core.CmtTestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for {@code migration.sh}. Each test pins one CMT Console
 * binary entry point — dispatch branches and first-run filesystem
 * contracts. Runs in seconds without a database; if any fails the
 * binary is not usable and the rest of the E2E suite is suspect.
 */
@DisplayName("CMT Console CLI smoke tests")
public class CliSmokeTest {

    @RegisterExtension
    final CmtTestContext ctx = new CmtTestContext();

    // --- Dispatch routing (DoMigration.handlerFactory) ---

    @Test
    @DisplayName("CLI-SMOKE-01: lists all subcommands when called with no args")
    void should_listAllSubcommands_when_calledWithoutArgs() throws Exception {
        CommandResult result = ctx.commandRunner().run(new RawCommand("./migration.sh"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.combinedOutput())
            .contains("start", "script", "log", "report")
            .contains("CUBRID Migration Toolkit");
    }

    /**
     * {@code migration.sh start} alone blocks on {@code ConsoleUtils.readingInput()}
     * waiting for stdin, so we pass a deliberately nonexistent path to
     * reach the "script not found → printHelp" exit. The {@code -sd}
     * token is unique to start help — it confirms the right dispatch
     * branch ran.
     */
    @Test
    @DisplayName("CLI-SMOKE-02: dispatches to StartCommandHandler when 'start' subcommand")
    void should_dispatchToStartHandler_when_invokedWithStartSubcommand() throws Exception {
        CommandResult result = ctx.commandRunner().run(
            new RawCommand("./migration.sh", "start", "/__nonexistent_for_smoke__.xml"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.combinedOutput())
            .contains("Usage in Linux: migration.sh start")
            .contains("-sd");
    }

    @Test
    @DisplayName("CLI-SMOKE-03: dispatches to ScriptCommandHandler when 'script' subcommand")
    void should_dispatchToScriptHandler_when_invokedWithScriptSubcommand() throws Exception {
        CommandResult result = ctx.commandRunner().run(new RawCommand("./migration.sh", "script"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.combinedOutput())
            .contains("Usage in Linux: migration.sh script")
            .contains("-schema");
    }

    @Test
    @DisplayName("CLI-SMOKE-04: dispatches to LogCommandHandler when 'log' subcommand")
    void should_dispatchToLogHandler_when_invokedWithLogSubcommand() throws Exception {
        CommandResult result = ctx.commandRunner().run(new RawCommand("./migration.sh", "log"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.combinedOutput())
            .contains("Usage in Linux: migration.sh log")
            .contains("-ps");
    }

    @Test
    @DisplayName("CLI-SMOKE-05: dispatches to ReportCommandHandler when 'report' subcommand")
    void should_dispatchToReportHandler_when_invokedWithReportSubcommand() throws Exception {
        CommandResult result = ctx.commandRunner().run(new RawCommand("./migration.sh", "report"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.combinedOutput())
            .contains("Usage in Linux: migration.sh report")
            .contains("-ao");
    }

    @Test
    @DisplayName("CLI-SMOKE-06: falls back to start help on unknown command")
    void should_fallbackToStartHelp_when_unknownCommand() throws Exception {
        CommandResult result = ctx.commandRunner().run(
            new RawCommand("./migration.sh", "bogus_command_that_does_not_exist"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.combinedOutput())
            .contains("The migration script isn't exists!")
            .contains("Usage in Linux: migration.sh start");
    }

    // --- First-run filesystem contracts ---

    @Test
    @DisplayName("CLI-SMOKE-07: creates workspace/cmt/log and workspace/cmt/report on any invocation")
    void should_createWorkspaceDirectories_onAnyInvocation() throws Exception {
        ctx.commandRunner().run(new RawCommand("./migration.sh"));

        assertThat(ctx.cmtConsoleHome().resolve("workspace/cmt/log")).isDirectory();
        assertThat(ctx.cmtConsoleHome().resolve("workspace/cmt/report")).isDirectory();
    }

    /**
     * Captures log size before invocation and asserts strict growth, so
     * the test stays valid across repeated runs (every invocation logs
     * at least the "Thank you" header).
     */
    @Test
    @DisplayName("CLI-SMOKE-08: appends to cubrid-migration.log on every invocation")
    void should_appendToLogFile_onAnyInvocation() throws Exception {
        Path logFile = ctx.cmtConsoleHome().resolve("workspace/cmt/log/cubrid-migration.log");
        long sizeBefore = Files.exists(logFile) ? Files.size(logFile) : 0L;

        ctx.commandRunner().run(new RawCommand("./migration.sh"));

        assertThat(logFile).exists();
        assertThat(Files.size(logFile)).isGreaterThan(sizeBefore);
    }
}
