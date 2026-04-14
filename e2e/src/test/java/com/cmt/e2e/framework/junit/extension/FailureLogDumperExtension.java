package com.cmt.e2e.framework.junit.extension;

import com.cmt.e2e.framework.util.InMemoryLogHolder;
import com.cmt.e2e.framework.core.TestPaths;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 테스트 실패 시 TestLogHolder에 기록된 진단 로그를 파일로 저장하는 JUnit 5 확장.
 */
public class FailureLogDumperExtension implements TestWatcher, BeforeEachCallback {

    private static final Logger log = LoggerFactory.getLogger(FailureLogDumperExtension.class);
    private static final String LOG_FILE_NAME = "diagnostic-log.txt";

    @Override
    public void beforeEach(ExtensionContext context) {
        InMemoryLogHolder.clear();
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        try {
            TestPaths testPaths = new TestPaths(context.getRequiredTestClass(), context.getRequiredTestMethod());
            Path artifactDir = testPaths.getArtifactDir();
            Files.createDirectories(artifactDir);
            Path logFilePath = artifactDir.resolve(LOG_FILE_NAME);

            List<String> logs = InMemoryLogHolder.getLogs();

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);

            pw.println("--- DIAGNOSTIC LOG ---");
            pw.printf("Test: %s#%s%n", context.getRequiredTestClass().getSimpleName(), context.getRequiredTestMethod().getName());
            pw.printf("Failed at: %s%n", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            pw.println("------------------------");
            pw.println();

            if (logs.isEmpty()) {
                pw.println("(No diagnostic logs were recorded)");
            } else {
                logs.forEach(pw::println);
            }

            pw.println();
            pw.println("--- FAILURE CAUSE ---");
            cause.printStackTrace(pw);
            pw.println("---------------------");

            Files.writeString(logFilePath, sw.toString());

            log.info("Detailed diagnostic log saved to: {}", logFilePath.toUri());

        } catch (IOException e) {
            log.error("Failed to write diagnostic log file.", e);
        } finally {
            InMemoryLogHolder.clear();
        }
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        InMemoryLogHolder.clear();
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        InMemoryLogHolder.clear();
    }

    @Override
    public void testDisabled(ExtensionContext context, java.util.Optional<String> reason) {
        InMemoryLogHolder.clear();
    }
}
