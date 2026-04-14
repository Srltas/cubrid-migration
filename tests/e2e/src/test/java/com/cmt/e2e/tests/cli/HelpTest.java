package com.cmt.e2e.tests.cli;

import com.cmt.e2e.framework.assertion.strategies.PlainTextVerificationStrategy;
import com.cmt.e2e.framework.command.Command;
import com.cmt.e2e.framework.command.execution.CommandResult;
import com.cmt.e2e.framework.command.impls.HelpCommand;
import com.cmt.e2e.framework.command.impls.LogCommand;
import com.cmt.e2e.framework.command.impls.ReportCommand;
import com.cmt.e2e.framework.command.impls.ScriptCommand;
import com.cmt.e2e.framework.core.CmtTestContext;
import com.cmt.e2e.framework.junit.annotation.TestResources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;

@Tag("fast")
public class HelpTest {

    @RegisterExtension
    final CmtTestContext ctx = CmtTestContext.builder().build();

    private static final String HELP_ANSWER_FILENAME = "commandHelp.answer";

    @Test
    @TestResources("cli/help/command")
    @DisplayName("help 명령어를 실행하면 전체 도움말을 출력한다")
    void should_displayGeneralHelp_when_helpCommandIsExecuted() throws IOException, InterruptedException {
        // Arrange
        Command helpCommand = new HelpCommand();

        // Act
        CommandResult result = ctx.commandRunner().run(helpCommand);

        // Assert
        ctx.verifier().verifyWith(result, HELP_ANSWER_FILENAME, new PlainTextVerificationStrategy());
    }

    @Test
    @TestResources("cli/help/command")
    @DisplayName("script 명령어 도움말을 출력한다")
    void should_displayScriptHelp_when_scriptCommandIsExecuted() throws IOException, InterruptedException {
        // Arrange
        ScriptCommand scriptCommand = new ScriptCommand.Builder().build();

        // Act
        CommandResult result = ctx.commandRunner().run(scriptCommand);

        // Assert
        ctx.verifier().verifyWith(result, "scriptHelp.answer", new PlainTextVerificationStrategy());
    }

    @Test
    @TestResources("cli/help/command")
    @DisplayName("log 명령어 도움말을 출력한다")
    void should_displayLogHelp_when_logCommandIsExecuted() throws IOException, InterruptedException {
        // Arrange
        LogCommand logCommand = new LogCommand.Builder().build();

        // Act
        CommandResult result = ctx.commandRunner().run(logCommand);

        // Assert
        ctx.verifier().verifyWith(result, "logHelp.answer", new PlainTextVerificationStrategy());
    }

    @Test
    @TestResources("cli/help/command")
    @DisplayName("report 명령어 도움말을 출력한다")
    void should_displayReportHelp_when_reportCommandIsExecuted() throws IOException, InterruptedException {
        // Arrange
        ReportCommand reportCommand = new ReportCommand.Builder().build();

        // Act
        CommandResult result = ctx.commandRunner().run(reportCommand);

        // Assert
        ctx.verifier().verifyWith(result, "reportHelp.answer", new PlainTextVerificationStrategy());
    }
}
