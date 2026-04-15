package com.cmt.e2e.framework.assertion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.cmt.e2e.framework.assertion.strategies.VerificationStrategy;
import com.cmt.e2e.framework.command.execution.CommandResult;
import com.cmt.e2e.framework.core.TestPaths;
import org.assertj.core.util.diff.DiffUtils;
import org.assertj.core.util.diff.Patch;

/**
 * 테스트 검증을 수행하고, 실패 시 상세 리포트(아티팩트)를 생성하는 책임을 가집니다.
 */
public class Verifier {
    private final TestPaths testPaths;

    public Verifier(TestPaths testPaths) {
        this.testPaths = testPaths;
    }

    public void verifyWith(CommandResult result, String expectedAnswerFileName, VerificationStrategy strategy) throws IOException {
        Path expectedAnswerPath = testPaths.getResourceDir().resolve(expectedAnswerFileName);
        if (!Files.exists(expectedAnswerPath)) {
            throw new AssertionError("Answer file not found: " + expectedAnswerPath);
        }

        try {
            strategy.verify(result, expectedAnswerPath);
        } catch (VerificationFailedException e) {
            String diff = writeFailureArtifacts(e.getActual(), e.getExpected());
            throw new AssertionError(buildFailureMessage("Verification failed", diff), e);
        }
    }

    public void verifyFileWith(Path actualFile, String expectedAnswerFileName, VerificationStrategy strategy) throws IOException {
        Path expectedAnswerPath = testPaths.getResourceDir().resolve(expectedAnswerFileName);
        if (!Files.exists(actualFile)) {
            throw new AssertionError("Actual file not found: " + actualFile);
        }
        if (!Files.exists(expectedAnswerPath)) {
            throw new AssertionError("Answer file not found: " + expectedAnswerPath);
        }

        String actualContent = Files.readString(actualFile);
        CommandResult wrapped = new CommandResult(actualContent, "", actualContent, 0, false);

        try {
            strategy.verify(wrapped, expectedAnswerPath);
        } catch (VerificationFailedException e) {
            String diff = writeFailureArtifacts(e.getActual(), e.getExpected());
            throw new AssertionError(buildFailureMessage("File verification failed", diff), e);
        }
    }

    /**
     * diff 요약 + 전체 unified diff를 포함한 실패 메시지를 구성한다.
     * EnricoMi/publish-unit-test-result-action이 Test Results의
     * 접힌 섹션(details)에서 멀티라인 메시지를 렌더링하므로,
     * 전체 diff를 그대로 포함해 리포트에서 바로 확인할 수 있다.
     */
    private String buildFailureMessage(String prefix, String diff) {
        String[] lines = diff.split("\n");
        long changed = Arrays.stream(lines)
            .filter(l -> l.startsWith("+") || l.startsWith("-"))
            .filter(l -> !l.startsWith("+++") && !l.startsWith("---"))
            .count();
        return prefix + " (" + changed + " lines differ).\n\n" + diff;
    }

    /**
     * 검증 실패 시 expected.log, actual.log, diff.patch 파일을 생성하고
     * unified diff 문자열을 반환한다.
     */
    private String writeFailureArtifacts(String actualContent, String expectedContent) throws IOException {
        Files.createDirectories(testPaths.getArtifactDir());

        List<String> actualLines = Arrays.asList(actualContent.split("\\R"));
        List<String> expectedLines = Arrays.asList(expectedContent.split("\\R"));
        Patch<String> patch = DiffUtils.diff(expectedLines, actualLines);
        List<String> diff = DiffUtils.generateUnifiedDiff("expected.log", "actual.log", expectedLines, patch, 3);

        Path actualPath = testPaths.getArtifactDir().resolve("actual.log");
        Path expectedPath = testPaths.getArtifactDir().resolve("expected.log");
        Path diffPath = testPaths.getArtifactDir().resolve("diff.patch");

        Files.writeString(actualPath, actualContent, UTF_8);
        Files.writeString(expectedPath, expectedContent, UTF_8);
        Files.write(diffPath, diff, UTF_8);

        return String.join("\n", diff);
    }
}
