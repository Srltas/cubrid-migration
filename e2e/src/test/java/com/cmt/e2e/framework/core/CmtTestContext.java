package com.cmt.e2e.framework.core;

import com.cmt.e2e.framework.assertion.MigrationOutput;
import com.cmt.e2e.framework.assertion.Verifier;
import com.cmt.e2e.framework.command.execution.CommandRunner;
import com.cmt.e2e.framework.junit.extension.FailureLogDumperExtension;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * 모든 E2E 테스트에서 공통으로 필요한 컴포넌트를 초기화하는 JUnit 5 Extension.
 *
 * <p>기존의 {@code CmtE2eTestBase} 상속 방식 대신, Composition으로 필요한 컴포넌트를 조합합니다.
 * 테스트 클래스에서 {@code @RegisterExtension}으로 등록하여 사용합니다.
 *
 * <pre>
 * class MyTest {
 *     {@literal @}RegisterExtension
 *     final CmtTestContext ctx = CmtTestContext.builder().build();
 *
 *     {@literal @}Test
 *     void myTest() throws Exception {
 *         CommandResult result = ctx.commandRunner().run(new HelpCommand());
 *         ctx.verifier().verifyWith(result, "expected.answer", new PlainTextVerificationStrategy());
 *     }
 * }
 * </pre>
 */
public class CmtTestContext implements BeforeEachCallback, AfterEachCallback {
    private static final Logger log = LoggerFactory.getLogger(CmtTestContext.class);

    private final FailureLogDumperExtension failureLogDumper = new FailureLogDumperExtension();

    private TestPaths testPaths;
    private Verifier verifier;
    private CommandRunner commandRunner;
    private WorkspaceFixtures workspaceFixtures;
    private Path cmtConsoleHome;

    private CmtTestContext() {}

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        // FailureLogDumper 초기화 (InMemoryLogHolder 클리어)
        failureLogDumper.beforeEach(context);

        Class<?> testClass = context.getRequiredTestClass();
        Method testMethod = context.getRequiredTestMethod();

        // TestPaths + Verifier 초기화
        this.testPaths = new TestPaths(testClass, testMethod);
        this.verifier = new Verifier(testPaths);
        log.debug("TestPaths and Verifier initialized for test: {}", context.getDisplayName());

        // CommandRunner 초기화
        String cmtConsoleHome = resolveCmtConsoleHome();
        assertThat(cmtConsoleHome)
            .withFailMessage("The CMT_CONSOLE_HOME environment variable must be set.")
            .isNotNull()
            .isNotEmpty();

        File cmtConsoleWorkDir = new File(cmtConsoleHome);
        this.cmtConsoleHome = cmtConsoleWorkDir.toPath();
        this.commandRunner = new CommandRunner(cmtConsoleWorkDir);
        log.debug("CommandRunner initialized with working directory: {}", cmtConsoleHome);

        // WorkspaceFixtures 초기화
        this.workspaceFixtures = new WorkspaceFixtures(cmtConsoleWorkDir, testClass, testMethod);
        this.workspaceFixtures.setupCubridDemodbMh();
        log.debug("WorkspaceFixtures initialized.");
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (workspaceFixtures != null) {
            workspaceFixtures.cleanupWorkspace();
            workspaceFixtures.cleanupOutput();
            workspaceFixtures.cleanupConf();
        }
    }

    // --- Accessors ---

    public TestPaths testPaths() { return testPaths; }
    public Verifier verifier() { return verifier; }
    public CommandRunner commandRunner() { return commandRunner; }
    public WorkspaceFixtures workspaceFixtures() { return workspaceFixtures; }

    /**
     * Dump 마이그레이션 출력 디렉토리에 대한 검증 추상화를 반환합니다.
     * CMT_CONSOLE_HOME 경로는 외부에 노출하지 않고, migration 이름으로만 접근합니다.
     *
     * <p>CMT Console은 파일 타겟 마이그레이션 산출물을
     * {@code {CMT_CONSOLE_HOME}/output/{migration.name}/{schema}} 아래에 생성합니다.
     * 따라서 테스트도 스크립트의 {@code migration/@name} 값을 그대로 사용합니다.
     *
     * @param migrationName 출력 디렉토리명. 예: {@code "CUBRID_demodb_202604062341"}
     *                      ({@link com.cmt.e2e.framework.template.ResolvedScript#migrationName()}에서 얻음)
     * @param schema        스키마명. 예: {@code "PUBLIC"}
     */
    public MigrationOutput migrationOutput(String migrationName, String schema) {
        Path outputBase = cmtConsoleHome.resolve("output");
        Path migrationDir = findMigrationDir(outputBase, migrationName);
        Path baseDir = migrationDir.resolve(schema);
        return new MigrationOutput(baseDir, verifier);
    }

    /**
     * {@code outputBase} 아래에서 migration 이름과 정확히 일치하는 디렉토리를 반환합니다.
     * 찾지 못하면 AssertionError를 발생시킵니다.
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

    /**
     * FailureLogDumperExtension을 반환합니다.
     * 테스트 실패 시 진단 로그를 자동 수집하려면 이 확장도 함께 등록하세요.
     */
    public FailureLogDumperExtension failureLogDumper() { return failureLogDumper; }

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
