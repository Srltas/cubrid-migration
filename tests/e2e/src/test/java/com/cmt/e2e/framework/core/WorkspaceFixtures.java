package com.cmt.e2e.framework.core;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Optional;

import com.cmt.e2e.framework.junit.annotation.CubridDemodbMh;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkspaceFixtures {
    private static final Logger log = LoggerFactory.getLogger(WorkspaceFixtures.class);

    public static final String CUBRID_DEMODB_MH = "1758883298370.mh";

    private final Path cmtConsoleDir;
    private final Path workspaceReportDir;
    private final Class<?> testClass;
    private final java.lang.reflect.Method testMethod;

    /**
     * CMT 콘솔의 작업 디렉터리를 기반으로 report 디렉터리 경로를 설정
     * @param cmtConsoleWorkDir CMT_CONSOLE_HOME 경로
     */
    public WorkspaceFixtures(File cmtConsoleWorkDir, TestInfo testInfo) {
        this(cmtConsoleWorkDir,
            testInfo.getTestClass().orElse(null),
            testInfo.getTestMethod().orElse(null));
    }

    public WorkspaceFixtures(File cmtConsoleWorkDir, Class<?> testClass, java.lang.reflect.Method testMethod) {
        this.cmtConsoleDir = cmtConsoleWorkDir.toPath();
        this.workspaceReportDir = cmtConsoleWorkDir.toPath().resolve("workspace/cmt/report");
        this.testClass = testClass;
        this.testMethod = testMethod;
    }

    public void copyConfToWorkspace(Path sourcePath) throws IOException {
        copyConfToWorkspace(sourcePath, sourcePath.getFileName().toString());
    }

    public void copyConfToWorkspace(Path sourcePath, String destinationFilename) throws IOException {
        if (!Files.exists(sourcePath)) {
            throw new IOException("Resource file not found: " + sourcePath);
        }

        Path destinationPath = cmtConsoleDir.resolve(destinationFilename);
        log.debug("Copying config {} to {}", sourcePath, destinationPath);
        Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
    }

    public void cleanupConf() throws IOException {
        Path confPath = cmtConsoleDir.resolve("db.conf");
        Files.deleteIfExists(confPath);
    }

    public Optional<String> setupCubridDemodbMh() throws IOException {
        boolean isAnnotationPresent =
            (testMethod != null && testMethod.isAnnotationPresent(CubridDemodbMh.class))
            || (testClass != null && testClass.isAnnotationPresent(CubridDemodbMh.class));

        if (isAnnotationPresent) {
            URL resourceUrl = getClass().getClassLoader().getResource("tests/mh/" + CUBRID_DEMODB_MH);
            if (resourceUrl == null) {
                throw new IOException("Shared mh file not found in resources/tests/mh: " + CUBRID_DEMODB_MH);
            }
            try {
                Path sourceMhPath = Paths.get(resourceUrl.toURI());
                copyResourceToWorkspace(sourceMhPath);
                return Optional.of(CUBRID_DEMODB_MH);
            } catch (URISyntaxException e) {
                throw new IOException("Failed to setup shared mh file", e);
            }
        }
        return Optional.empty();
    }

    /**
     * 테스트에 필요한 리소스(.mh 파일 등)를 report 작업 공간으로 복사
     * @param resourcePath
     * @return
     * @throws IOException
     */
    public Path copyResourceToWorkspace(Path resourcePath) throws IOException {
        if (!Files.exists(resourcePath)) {
            throw new IOException("Resource file not found: " + resourcePath);
        }

        Files.createDirectories(workspaceReportDir);

        Path destinationPath = workspaceReportDir.resolve(resourcePath.getFileName());
        Files.copy(resourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        return destinationPath;
    }

    public void cleanupWorkspace() throws IOException {
        if (Files.exists(workspaceReportDir)) {
            Files.walk(workspaceReportDir)
                .filter(path -> !path.equals(workspaceReportDir))
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(f -> {
                    if (!f.delete()) {
                        log.warn("Failed to delete workspace file: {}", f.getAbsolutePath());
                    }
                });
        }
    }

    /**
     * CMT Console이 Dump 마이그레이션 시 생성하는 output 디렉토리를 정리합니다.
     * 테스트 간 격리를 보장하기 위해 AfterEach에서 호출합니다.
     */
    public void cleanupOutput() throws IOException {
        Path outputDir = cmtConsoleDir.resolve("output");
        if (Files.exists(outputDir)) {
            log.debug("Cleaning up migration output directory: {}", outputDir);
            Files.walk(outputDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(f -> {
                    if (!f.delete()) {
                        log.warn("Failed to delete output file: {}", f.getAbsolutePath());
                    }
                });
        }
    }
}
