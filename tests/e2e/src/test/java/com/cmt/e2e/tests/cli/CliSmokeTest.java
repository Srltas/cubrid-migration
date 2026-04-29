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
 * Smoke tests for the CMT Console CLI ({@code migration.sh}).
 *
 * <p>These tests verify that the CMT Console binary boots correctly and
 * that each documented entry point reaches its handler. They run in a
 * few seconds total, do not need a database, and serve as the first-line
 * regression alarm: if any of these fail, the CMT binary itself is not
 * usable and the rest of the E2E suite cannot be trusted.
 *
 * <h2>Coverage map</h2>
 *
 * The 8 cases are grouped by what they catch:
 *
 * <h3>Group A — Dispatch routing ({@code DoMigration.handlerFactory})</h3>
 * One test per dispatch branch. Each test asserts a token unique to the
 * matching subcommand 's help text, so it is impossible for the wrong
 * handler to make the test pass.
 *
 * <ul>
 *   <li>CLI-SMOKE-01: no args → top-level {@code help.txt}
 *       (lists 4 subcommands)</li>
 *   <li>CLI-SMOKE-02: {@code start} → {@code help_start.txt}
 *       (unique token: {@code -sd})</li>
 *   <li>CLI-SMOKE-03: {@code script} → {@code help_script.txt}
 *       (unique token: {@code -schema})</li>
 *   <li>CLI-SMOKE-04: {@code log} → {@code help_log.txt}
 *       (unique token: {@code -ps})</li>
 *   <li>CLI-SMOKE-05: {@code report} → {@code help_report.txt}
 *       (unique token: {@code -ao})</li>
 *   <li>CLI-SMOKE-06: unknown command → fallback path through
 *       {@code StartCommandHandler}</li>
 * </ul>
 *
 * <h3>Group B — First-run filesystem contracts</h3>
 *
 * <ul>
 *   <li>CLI-SMOKE-07: {@code workspace/cmt/log} and
 *       {@code workspace/cmt/report} dirs exist after any invocation
 *       ({@code PathUtils.initPaths})</li>
 *   <li>CLI-SMOKE-08: {@code cubrid-migration.log} grows after each
 *       invocation (logback wiring + {@code LogInitializer})</li>
 * </ul>
 *
 * <h2>What is intentionally not tested here</h2>
 *
 * Subcommand argument parsing (e.g. {@code script -s missing}), exit
 * codes for failure modes, and migration outcomes belong in the per-DB
 * E2E tests, not in smoke. Smoke is broad and shallow on purpose.
 */
@DisplayName("CMT Console CLI smoke tests")
public class CliSmokeTest {

    @RegisterExtension
    final CmtTestContext ctx = CmtTestContext.builder().build();

    // ---------------------------------------------------------------------
    // Group A — Dispatch routing
    // ---------------------------------------------------------------------

    /**
     * {@code migration.sh} (no arguments) hits the
     * {@code argList.isEmpty()} branch in {@code DoMigration.main} and
     * prints the top-level {@code help.txt} that lists every available
     * subcommand. If this fails, the empty-args branch is broken or
     * {@code help.txt} is missing from the deployed jar.
     */
    @Test
    @DisplayName("CLI-SMOKE-01: lists all subcommands when called with no args")
    void should_listAllSubcommands_when_calledWithoutArgs() throws Exception {
        CommandResult result = ctx.commandRunner().run(new RawCommand("./migration.sh"));

        assertThat(result.exitCode()).as("no-args exit code").isZero();
        assertThat(result.combinedOutput())
            .as("top-level help should advertise every subcommand")
            .contains("start", "script", "log", "report")
            .contains("CUBRID Migration Toolkit");
    }

    /**
     * {@code migration.sh start <script>} reaches {@code StartCommandHandler}
     * which prints {@code help_start.txt} when the given script path does
     * not exist. The {@code -sd} flag (Source JDBC driver) is unique to
     * start, so seeing it confirms the right dispatch branch ran AND the
     * right help resource was packaged.
     *
     * <p><b>Why pass an explicit nonexistent path</b>: {@code migration.sh
     * start} alone (no script argument) hits the {@code args.isEmpty()}
     * branch in {@code StartCommandHandler.getScriptFile} which calls
     * {@code ConsoleUtils.readingInput()} and waits for stdin
     * indefinitely — that would block the smoke test forever. Passing a
     * deliberately nonexistent path bypasses the prompt and reaches the
     * "script not found → printHelp" path.
     *
     * <p><b>Distinct from CLI-SMOKE-06 (fallback)</b>: this test exercises
     * the {@code "start"} branch in {@code DoMigration.handlerFactory},
     * verifying that the keyword is correctly stripped (otherwise the
     * file path would be "start", not the explicit path supplied here).
     * SMOKE-06 exercises the else branch with no subcommand keyword.
     */
    @Test
    @DisplayName("CLI-SMOKE-02: dispatches to StartCommandHandler when 'start' subcommand")
    void should_dispatchToStartHandler_when_invokedWithStartSubcommand() throws Exception {
        CommandResult result = ctx.commandRunner().run(
            new RawCommand("./migration.sh", "start", "/__nonexistent_for_smoke__.xml"));

        assertThat(result.exitCode()).as("start exit code").isZero();
        assertThat(result.combinedOutput())
            .as("start help should mention start-specific options")
            .contains("Usage in Linux: migration.sh start")
            .contains("-sd")  // Source JDBC driver — unique to start
            .contains("-mm"); // Monitor mode — unique to start
    }

    /**
     * {@code migration.sh script} reaches {@code ScriptCommandHandler}.
     * The {@code -schema} flag (Include source schema) is unique to
     * script. This is also the dispatch path that
     * {@code RegenerateScripts} relies on for fixture generation, so a
     * regression here breaks the entire fixture refresh pipeline.
     */
    @Test
    @DisplayName("CLI-SMOKE-03: dispatches to ScriptCommandHandler when 'script' subcommand")
    void should_dispatchToScriptHandler_when_invokedWithScriptSubcommand() throws Exception {
        CommandResult result = ctx.commandRunner().run(new RawCommand("./migration.sh", "script"));

        assertThat(result.exitCode()).as("script exit code").isZero();
        assertThat(result.combinedOutput())
            .as("script help should mention script-specific options")
            .contains("Usage in Linux: migration.sh script")
            .contains("-schema"); // Include source schema — unique to script
    }

    /**
     * {@code migration.sh log} reaches {@code LogCommandHandler}. The
     * {@code -ps} flag (Lines of text per page) is unique to log
     * (report shares {@code -l} but not {@code -ps}).
     */
    @Test
    @DisplayName("CLI-SMOKE-04: dispatches to LogCommandHandler when 'log' subcommand")
    void should_dispatchToLogHandler_when_invokedWithLogSubcommand() throws Exception {
        CommandResult result = ctx.commandRunner().run(new RawCommand("./migration.sh", "log"));

        assertThat(result.exitCode()).as("log exit code").isZero();
        assertThat(result.combinedOutput())
            .as("log help should mention log-specific options")
            .contains("Usage in Linux: migration.sh log")
            .contains("-ps"); // Lines per page — unique to log
    }

    /**
     * {@code migration.sh report} reaches {@code ReportCommandHandler}.
     * The {@code -ao} flag (Show report at once) is unique to report.
     */
    @Test
    @DisplayName("CLI-SMOKE-05: dispatches to ReportCommandHandler when 'report' subcommand")
    void should_dispatchToReportHandler_when_invokedWithReportSubcommand() throws Exception {
        CommandResult result = ctx.commandRunner().run(new RawCommand("./migration.sh", "report"));

        assertThat(result.exitCode()).as("report exit code").isZero();
        assertThat(result.combinedOutput())
            .as("report help should mention report-specific options")
            .contains("Usage in Linux: migration.sh report")
            .contains("-ao"); // Show at once — unique to report
    }

    /**
     * Unknown command (e.g. user typo) falls through the
     * {@code handlerFactory} else branch into {@code StartCommandHandler},
     * which treats the argument as a script-file path, fails to find it,
     * and prints start help. Verifies the fallback path itself, not the
     * specific subcommand. CMT 's convention here is to exit 0 so the
     * caller can distinguish "user mistake" from "migration failure".
     */
    @Test
    @DisplayName("CLI-SMOKE-06: falls back to start help on unknown command")
    void should_fallbackToStartHelp_when_unknownCommand() throws Exception {
        CommandResult result = ctx.commandRunner().run(
            new RawCommand("./migration.sh", "bogus_command_that_does_not_exist"));

        assertThat(result.exitCode()).as("fallback exit code").isZero();
        assertThat(result.combinedOutput())
            .as("fallback message and start help should both appear")
            .contains("The migration script isn't exists!")
            .contains("Usage in Linux: migration.sh start");
    }

    // ---------------------------------------------------------------------
    // Group B — First-run filesystem contracts
    // ---------------------------------------------------------------------

    /**
     * {@code DoMigration.main} calls {@code PathUtils.initPaths()} on
     * every invocation, which is responsible for creating the workspace
     * directory tree (log, report, ...). If this is broken, the binary
     * appears to launch but every subsequent step that reads from these
     * dirs (logback, history files) fails downstream. Catching it at
     * smoke level avoids late mysterious failures.
     */
    @Test
    @DisplayName("CLI-SMOKE-07: creates workspace/cmt/log and workspace/cmt/report on any invocation")
    void should_createWorkspaceDirectories_onAnyInvocation() throws Exception {
        ctx.commandRunner().run(new RawCommand("./migration.sh"));

        Path workspaceLog = ctx.cmtConsoleHome().resolve("workspace/cmt/log");
        Path workspaceReport = ctx.cmtConsoleHome().resolve("workspace/cmt/report");

        assertThat(workspaceLog)
            .as("workspace/cmt/log should be auto-created by PathUtils.initPaths")
            .exists()
            .isDirectory();
        assertThat(workspaceReport)
            .as("workspace/cmt/report should be auto-created by PathUtils.initPaths")
            .exists()
            .isDirectory();
    }

    /**
     * {@code DoMigration.main} initializes logback via
     * {@code LogInitializer.initLog} after path setup. Verifying that
     * {@code cubrid-migration.log} grows after a fresh invocation
     * proves logback is wired AND the log directory is writable.
     *
     * <p>The test captures the log size before invocation and asserts
     * post-invocation size strictly greater. This stays valid across
     * many test runs — every invocation must add at least one line
     * (the "Thank you" header is always logged).
     */
    @Test
    @DisplayName("CLI-SMOKE-08: appends to cubrid-migration.log on every invocation")
    void should_appendToLogFile_onAnyInvocation() throws Exception {
        Path logFile = ctx.cmtConsoleHome().resolve("workspace/cmt/log/cubrid-migration.log");
        long sizeBefore = Files.exists(logFile) ? Files.size(logFile) : 0L;

        ctx.commandRunner().run(new RawCommand("./migration.sh"));

        assertThat(logFile)
            .as("cubrid-migration.log should exist after invocation (logback wired)")
            .exists();
        assertThat(Files.size(logFile))
            .as("log file size should grow after invocation (logback writes)")
            .isGreaterThan(sizeBefore);
    }
}
