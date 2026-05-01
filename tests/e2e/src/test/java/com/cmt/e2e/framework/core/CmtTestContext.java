package com.cmt.e2e.framework.core;

import com.cmt.e2e.framework.command.CommandRunner;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JUnit 5 extension wiring the common per-test plumbing (paths,
 * CommandRunner, WorkspaceCleaner) and per-test log routing via MDC.
 * Register with {@code @RegisterExtension final CmtTestContext ctx = ...}.
 */
public class CmtTestContext implements BeforeEachCallback, AfterEachCallback {
    private static final Logger log = LoggerFactory.getLogger(CmtTestContext.class);

    /** SiftingAppender discriminator — must match {@code logback-test.xml}. */
    private static final String MDC_TEST_ID = "testId";

    private TestPaths testPaths;
    private CommandRunner commandRunner;
    private WorkspaceCleaner workspaceCleaner;
    private Path cmtConsoleHome;

    public CmtTestContext() {}

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        Class<?> testClass = context.getRequiredTestClass();
        Method testMethod = context.getRequiredTestMethod();

        // MDC first so any log line (including failures here) lands in the per-test file.
        MDC.put(MDC_TEST_ID, testClass.getSimpleName() + "/" + testMethod.getName());

        this.testPaths = new TestPaths(testClass, testMethod);

        String cmtConsoleHome = System.getenv("CMT_CONSOLE_HOME");
        assertThat(cmtConsoleHome)
            .withFailMessage("The CMT_CONSOLE_HOME environment variable must be set.")
            .isNotNull()
            .isNotEmpty();

        File cmtConsoleWorkDir = new File(cmtConsoleHome);
        this.cmtConsoleHome = cmtConsoleWorkDir.toPath();
        this.commandRunner = new CommandRunner(cmtConsoleWorkDir);
        this.workspaceCleaner = new WorkspaceCleaner(cmtConsoleWorkDir);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        try {
            if (workspaceCleaner != null) {
                workspaceCleaner.cleanupWorkspace();
                workspaceCleaner.cleanupOutput();
            }
        } finally {
            // Clear MDC so the next test does not leak logs into _bootstrap.
            MDC.remove(MDC_TEST_ID);
        }
    }

    public TestPaths testPaths() { return testPaths; }
    public CommandRunner commandRunner() { return commandRunner; }
    public WorkspaceCleaner workspaceCleaner() { return workspaceCleaner; }
    public Path cmtConsoleHome() { return cmtConsoleHome; }
}
