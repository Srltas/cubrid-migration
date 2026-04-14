package com.cmt.e2e.tests.cli;

import com.cmt.e2e.framework.assertion.strategies.PlainTextVerificationStrategy;
import com.cmt.e2e.framework.command.Command;
import com.cmt.e2e.framework.command.execution.CommandResult;
import com.cmt.e2e.framework.command.impls.RawCommand;
import com.cmt.e2e.framework.core.CmtTestContext;
import com.cmt.e2e.framework.junit.annotation.TestResources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;

@Tag("fast")
public class ErrorTest {

    @RegisterExtension
    final CmtTestContext ctx = CmtTestContext.builder().build();

    private static final String UNKNOWN_COMMAND_ANSWER_FILENAME = "unknownCommand.answer";

    @Test
    @TestResources("cli/error/unknownCommand")
    @DisplayName("알 수 없는 명령어를 입력하면 전체 도움말을 출력한다")
    void should_printErrorMessage_when_commandIsUnknown() throws IOException, InterruptedException {
        // Arrange
        Command unknownCommand = new RawCommand("./migration.sh", "unknown-command");

        // Act
        CommandResult result = ctx.commandRunner().run(unknownCommand);

        // Assert
        ctx.verifier().verifyWith(result, UNKNOWN_COMMAND_ANSWER_FILENAME, new PlainTextVerificationStrategy());
    }
}
