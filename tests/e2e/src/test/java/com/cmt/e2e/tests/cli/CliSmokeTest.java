package com.cmt.e2e.tests.cli;

import com.cmt.e2e.framework.command.execution.CommandResult;
import com.cmt.e2e.framework.command.impls.RawCommand;
import com.cmt.e2e.framework.core.CmtTestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Minimal smoke test for the CMT Console CLI.
 * It finishes in a few seconds and does not require a database.
 *
 * <ul>
 *   <li>{@code migration.sh --help} -> exit 0 and a usage string</li>
 *   <li>{@code migration.sh bogus_command} -> start help for a missing script path</li>
 * </ul>
 *
 * If this test fails, the CMT binary itself is not booting correctly,
 * so there is little value in running the rest of the E2E suite.
 */
@DisplayName("CLI-SMOKE-01: Basic migration.sh behavior")
public class CliSmokeTest {

    @RegisterExtension
    final CmtTestContext ctx = CmtTestContext.builder().build();

    @Test
    @DisplayName("prints usage and exits 0 for --help")
    void should_showUsage_when_calledWithHelp() throws Exception {
        // Act
        CommandResult result = ctx.commandRunner().run(
            new RawCommand("./migration.sh", "--help"));

        // Assert
        assertThat(result.exitCode())
            .as("--help exit code")
            .isZero();
        assertThat(result.combinedOutput())
            .as("usage output")
            .containsIgnoringCase("usage");
    }

    @Test
    @DisplayName("prints start help when the first argument is not a known subcommand")
    void should_printStartHelp_when_commandIsUnknown() throws Exception {
        // Act
        CommandResult result = ctx.commandRunner().run(
            new RawCommand("./migration.sh", "bogus_command_that_does_not_exist"));

        // Assert
        assertThat(result.exitCode())
            .as("fallback exit code")
            .isZero();
        assertThat(result.combinedOutput())
            .as("missing script message")
            .contains("The migration script isn't exists!");
        assertThat(result.combinedOutput())
            .as("start help output")
            .contains("Usage in Linux: migration.sh start");
    }
}
