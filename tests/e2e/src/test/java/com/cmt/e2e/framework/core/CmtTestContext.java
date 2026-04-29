package com.cmt.e2e.framework.core;

import com.cmt.e2e.framework.assertion.MigrationOutput;
import com.cmt.e2e.framework.command.execution.CommandRunner;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JUnit 5 extension that initializes the common components needed
 * by every E2E test.
 *
 * <p>Register this from a test class with {@code @RegisterExtension}.
 *
 * <pre>
 * class MyTest {
 *     {@literal @}RegisterExtension
 *     final CmtTestContext ctx = CmtTestContext.builder().build();
 *
 *     {@literal @}Test
 *     void myTest() throws Exception {
 *         CommandResult result = ctx.commandRunner().run(startCommand);
 *         // ... assertions ...
 *     }
 * }
 * </pre>
 */
public class CmtTestContext implements BeforeEachCallback, AfterEachCallback {
    private static final Logger log = LoggerFactory.getLogger(CmtTestContext.class);

    /** Logback SiftingAppender discriminator key. Must match {@code logback-test.xml}. */
    public static final String MDC_TEST_ID = "testId";

    private TestPaths testPaths;
    private CommandRunner commandRunner;
    private WorkspaceFixtures workspaceFixtures;
    private Path cmtConsoleHome;

    private CmtTestContext() {}

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
        String cmtConsoleHome = resolveCmtConsoleHome();
        assertThat(cmtConsoleHome)
            .withFailMessage("The CMT_CONSOLE_HOME environment variable must be set.")
            .isNotNull()
            .isNotEmpty();

        File cmtConsoleWorkDir = new File(cmtConsoleHome);
        this.cmtConsoleHome = cmtConsoleWorkDir.toPath();
        this.commandRunner = new CommandRunner(cmtConsoleWorkDir);
        log.debug("CommandRunner initialized with working directory: {}", cmtConsoleHome);

        // Initialize WorkspaceFixtures
        this.workspaceFixtures = new WorkspaceFixtures(cmtConsoleWorkDir, testClass, testMethod);
        log.debug("WorkspaceFixtures initialized.");
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        try {
            if (workspaceFixtures != null) {
                workspaceFixtures.cleanupWorkspace();
                workspaceFixtures.cleanupOutput();
                workspaceFixtures.cleanupConf();
            }
        } finally {
            // Clear MDC so the next test does not leak logs into _bootstrap.
            MDC.remove(MDC_TEST_ID);
        }
    }

    // --- Accessors ---

    public TestPaths testPaths() { return testPaths; }
    public CommandRunner commandRunner() { return commandRunner; }
    public WorkspaceFixtures workspaceFixtures() { return workspaceFixtures; }

    /**
     * Returns the resolved {@code CMT_CONSOLE_HOME} as a {@link Path}.
     * Used by smoke tests that need to assert on workspace files (logs,
     * reports) created by {@code migration.sh} invocations.
     */
    public Path cmtConsoleHome() { return cmtConsoleHome; }

    /**
     * Returns a validation wrapper around a dump migration output directory.
     * The CMT_CONSOLE_HOME path stays hidden; callers only use the migration name.
     *
     * <p>CMT Console writes file-target migration outputs under
     * {@code {CMT_CONSOLE_HOME}/output/{migration.name}/{schema}}.
     * Tests therefore use the script's {@code migration/@name} value directly.
     *
     * @param migrationName output directory name, for example
     *                      {@code "CUBRID_demodb_202604062341"}
     * @param schema schema name, for example {@code "PUBLIC"}
     */
    public MigrationOutput migrationOutput(String migrationName, String schema) {
        Path outputBase = cmtConsoleHome.resolve("output");
        Path migrationDir = findMigrationDir(outputBase, migrationName);
        Path baseDir = migrationDir.resolve(schema);
        return new MigrationOutput(baseDir);
    }

    /**
     * Returns the directory under {@code outputBase} whose name exactly matches
     * the migration name. Throws {@link AssertionError} if it cannot be found.
     */
    private Path findMigrationDir(Path outputBase, String migrationName) {
        assertThat(outputBase)
            .as("CMT Console output base directory")
            .isDirectory();

        Path migrationDir = outputBase.resolve(migrationName);
        if (!Files.isDirectory(migrationDir)) {
            throw new AssertionError(
                "No migration output directory found with name '" + migrationName
                    + "' under " + outputBase);
        }
        return migrationDir;
    }

    // --- Builder ---

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        public CmtTestContext build() {
            return new CmtTestContext();
        }
    }

    // --- Internal ---

    private String resolveCmtConsoleHome() throws IOException {
        String homePath = System.getenv("CMT_CONSOLE_HOME");
        if (homePath != null && !homePath.isBlank()) {
            log.debug("Using CMT_CONSOLE_HOME from environment variable: {}", homePath);
            return homePath;
        }

        Path propsPath = Paths.get("e2e-test.properties");
        if (Files.exists(propsPath)) {
            Properties props = new Properties();
            try (InputStream input = Files.newInputStream(propsPath)) {
                props.load(input);
                homePath = props.getProperty("cmt.console.home");
                if (homePath != null && !homePath.isBlank()) {
                    log.debug("Using cmt.console.home from e2e-test.properties: {}", homePath);
                    return homePath;
                }
            }
        }
        return null;
    }
}
