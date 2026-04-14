package com.cmt.e2e.tests.cli;

import com.cmt.e2e.framework.assertion.strategies.PlainTextVerificationStrategy;
import com.cmt.e2e.framework.command.execution.CommandResult;
import com.cmt.e2e.framework.command.impls.ReportCommand;
import com.cmt.e2e.framework.core.CmtTestContext;
import com.cmt.e2e.framework.junit.annotation.CubridDemodbMh;
import com.cmt.e2e.framework.junit.annotation.TestResources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;

import static com.cmt.e2e.framework.core.WorkspaceFixtures.CUBRID_DEMODB_MH;

@Tag("fast")
public class ReportTest {

    @RegisterExtension
    final CmtTestContext ctx = CmtTestContext.builder().build();

    private static final String REPORT_AO_ANSWER_FILENAME = "report_ao.answer";
    private static final String REPORT_AO_L_ANSWER_FILENAME = "report_ao_l.answer";

    @Test
    @CubridDemodbMh
    @TestResources("cli/report/ao")
    @DisplayName("report 명령어에 -ao 옵션을 사용하면 전체 내용을 출력한다")
    void should_generateFullReport_when_allOutputOptionIsUsed() throws IOException, InterruptedException {
        // Arrange
        ReportCommand command = ReportCommand.builder()
            .allOutput()
            .mhFile(CUBRID_DEMODB_MH)
            .build();

        // Act
        CommandResult result = ctx.commandRunner().run(command);

        // Assert
        ctx.verifier().verifyWith(result, REPORT_AO_ANSWER_FILENAME, new PlainTextVerificationStrategy());
    }

    @Test
    @CubridDemodbMh
    @TestResources("cli/report/ao_l")
    @DisplayName("report 명령어에 -ao와 -l 옵션을 함께 사용하면 가장 최신 작업의 전체 내용을 출력한다")
    void should_generateFullReportForLatest_when_allOutputAndLatestOptionsAreUsed() throws IOException, InterruptedException {
        // Arrange
        ReportCommand command = ReportCommand.builder()
            .allOutput()
            .latest()
            .mhFile(CUBRID_DEMODB_MH)
            .build();

        // Act
        CommandResult result = ctx.commandRunner().run(command);

        // Assert
        ctx.verifier().verifyWith(result, REPORT_AO_L_ANSWER_FILENAME, new PlainTextVerificationStrategy());
    }
}
