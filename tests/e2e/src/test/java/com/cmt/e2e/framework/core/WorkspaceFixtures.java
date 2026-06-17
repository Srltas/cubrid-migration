package com.cmt.e2e.framework.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages file-system fixtures in the CMT Console working directory
 * such as workspace, output, and conf.
 * Nothing happens in beforeEach; cleanup runs only in afterEach.
 */
public class WorkspaceFixtures {
    private static final Logger log = LoggerFactory.getLogger(WorkspaceFixtures.class);

    private final Path cmtConsoleDir;
    private final Path workspaceReportDir;

    public WorkspaceFixtures(File cmtConsoleWorkDir, TestInfo testInfo) {
        this(cmtConsoleWorkDir,
            testInfo.getTestClass().orElse(null),
            testInfo.getTestMethod().orElse(null));
    }

    public WorkspaceFixtures(File cmtConsoleWorkDir, Class<?> testClass, java.lang.reflect.Method testMethod) {
        this.cmtConsoleDir = cmtConsoleWorkDir.toPath();
        this.workspaceReportDir = cmtConsoleWorkDir.toPath().resolve("workspace/cmt/report");
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
     * Cleans the output directory created by CMT Console for dump migrations.
     * Called from afterEach to keep tests isolated from one another.
     *
     * <p>Set the {@code CMT_E2E_HOLD_OUTPUT} environment variable (any non-empty
     * value) to skip cleanup. This is the supported escape hatch for
     * regenerating dump golden files: run the dump test with the variable set,
     * then copy the artifacts from {@code $CMT_CONSOLE_HOME/output/} into the
     * appropriate {@code expected/} resource directory.
     */
    public void cleanupOutput() throws IOException {
        String holdOutput = System.getenv("CMT_E2E_HOLD_OUTPUT");
        if (holdOutput != null && !holdOutput.isBlank()) {
            log.info("Skipping output cleanup because CMT_E2E_HOLD_OUTPUT is set "
                + "(use this to capture dump artifacts for golden regeneration).");
            return;
        }

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
