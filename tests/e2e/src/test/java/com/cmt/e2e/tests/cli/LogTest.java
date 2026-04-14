package com.cmt.e2e.tests.cli;

import com.cmt.e2e.framework.assertion.strategies.PlainTextVerificationStrategy;
import com.cmt.e2e.framework.command.execution.CommandResult;
import com.cmt.e2e.framework.command.impls.LogCommand;
import com.cmt.e2e.framework.core.CmtTestContext;
import com.cmt.e2e.framework.junit.annotation.CubridDemodbMh;
import com.cmt.e2e.framework.junit.annotation.TestResources;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.cmt.e2e.framework.core.WorkspaceFixtures.CUBRID_DEMODB_MH;

public class LogTest {

    @RegisterExtension
    final CmtTestContext ctx = CmtTestContext.builder().build();

    private static final String LOG_PS_20_ANSWER_FILENAME = "expected.answer";
    private static final int DEFAULT_PAGE_SIZE = 20;

    @Test
    @CubridDemodbMh
    @TestResources("cli/log/ps_20")
    @DisplayName("log 명령어에 페이지 크기를 지정하면 로그의 첫 페이지를 출력한다")
    @Disabled("현재 System.console()을 사용하여 자동 입력이 불가능. System.in으로 변경 후 활성화 필요")
    void should_displayFirstPageOfLogs_when_logCommandIsExecutedWithPageSize() throws Exception {
        // Arrange
        LogCommand logCommand = LogCommand.builder()
            .pageSize(DEFAULT_PAGE_SIZE)
            .mhFile(CUBRID_DEMODB_MH)
            .build();

        // Act
        CommandResult result = ctx.commandRunner().run(logCommand);

        // Assert
        ctx.verifier().verifyWith(result, LOG_PS_20_ANSWER_FILENAME, new PlainTextVerificationStrategy());
    }
}
