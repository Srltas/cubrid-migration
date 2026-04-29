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
 * JUnit 5 extension that initializes the common components needed
 * by every E2E test. Register from a test class with
 * {@code @RegisterExtension final CmtTestContext ctx = new CmtTestContext()}.
 */
public class CmtTestContext implements BeforeEachCallback, AfterEachCallback {
    private static final Logger log = LoggerFactory.getLogger(CmtTestContext.class);

    /** Logback SiftingAppender discriminator key. Must match {@code logback-test.xml}. */
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

        // Set MDC first so every later log line is routed to
        // target/e2e/<Class>/<method>/test.log.
        // Failures in beforeEach, such as missing CMT_CONSOLE_HOME, also land there.
        MDC.put(MDC_TEST_ID, testClass.getSimpleName() + "/" + testMethod.getName());

        // Initialize TestPaths
        this.testPaths = new TestPaths(testClass, testMethod);
        log.debug("TestPaths initialized for test: {}", context.getDisplayName());

        // Initialize CommandRunner
        String cmtConsoleHome = System.getenv("CMT_CONSOLE_HOME");
        assertThat(cmtConsoleHome)
            .withFailMessage("The CMT_CONSOLE_HOME environment variable must be set.")
            .isNotNull()
            .isNotEmpty();

        File cmtConsoleWorkDir = new File(cmtConsoleHome);
        this.cmtConsoleHome = cmtConsoleWorkDir.toPath();
        this.commandRunner = new CommandRunner(cmtConsoleWorkDir);
        log.debug("CommandRunner initialized with working directory: {}", cmtConsoleHome);

        // Initialize WorkspaceCleaner
        this.workspaceCleaner = new WorkspaceCleaner(cmtConsoleWorkDir);
        log.debug("WorkspaceCleaner initialized.");
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

    // --- Accessors ---

    public TestPaths testPaths() { return testPaths; }
    public CommandRunner commandRunner() { return commandRunner; }
    public WorkspaceCleaner workspaceCleaner() { return workspaceCleaner; }

    /**
     * Returns the resolved {@code CMT_CONSOLE_HOME} as a {@link Path}.
     * Used by smoke tests that need to assert on workspace files (logs,
     * reports) created by {@code migration.sh} invocations.
     */
    public Path cmtConsoleHome() { return cmtConsoleHome; }
}
