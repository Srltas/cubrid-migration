package com.cmt.e2e.framework.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cleans the CMT Console working directory ({@code workspace/} and
 * {@code output/}) between tests. Nothing happens in beforeEach; cleanup
 * runs only in afterEach.
 */
public class WorkspaceFixtures {
    private static final Logger log = LoggerFactory.getLogger(WorkspaceFixtures.class);

    private final Path cmtConsoleDir;
    private final Path workspaceReportDir;

    public WorkspaceFixtures(File cmtConsoleWorkDir) {
        this.cmtConsoleDir = cmtConsoleWorkDir.toPath();
        this.workspaceReportDir = this.cmtConsoleDir.resolve("workspace/cmt/report");
    }

    public void cleanupWorkspace() throws IOException {
        if (!Files.exists(workspaceReportDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(workspaceReportDir)) {
            walk.filter(path -> !path.equals(workspaceReportDir))
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
        if (!Files.exists(outputDir)) {
            return;
        }
        log.debug("Cleaning up migration output directory: {}", outputDir);
        try (Stream<Path> walk = Files.walk(outputDir)) {
            walk.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(f -> {
                    if (!f.delete()) {
                        log.warn("Failed to delete output file: {}", f.getAbsolutePath());
                    }
                });
        }
    }
}
