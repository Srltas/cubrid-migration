package com.cmt.e2e.framework.assertion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin helper for validating CMT dump migration outputs
 * under a specific schema directory.
 */
public class MigrationOutput {
    private static final Logger log = LoggerFactory.getLogger(MigrationOutput.class);

    private final Path baseDir;

    public MigrationOutput(Path baseDir) {
        this.baseDir = baseDir;
    }

    public Path baseDir() {
        return baseDir;
    }

    public MigrationOutput assertFilesExist(List<String> expectedFiles) {
        if (!Files.isDirectory(baseDir)) {
            throw new AssertionError(String.format("Migration output directory does not exist: %s", baseDir));
        }

        for (String f : expectedFiles) {
            Path file = baseDir.resolve(f);
            if (!Files.exists(file)) {
                log.info("Expected migration file '{}' is missing under '{}'. Actual files:{}{}",
                    f, baseDir, System.lineSeparator(), listDirectorySafely(baseDir));
                throw new AssertionError(String.format("Missing migration file: %s", f));
            }

            if (!Files.isRegularFile(file)) {
                log.info("Expected migration file '{}' exists but is not a regular file: {}", f, file);
                throw new AssertionError(String.format("Migration output is not a regular file: %s", f));
            }

            try {
                if (Files.size(file) == 0L) {
                    log.info("Expected migration file '{}' exists but is empty: {}", f, file);
                    throw new AssertionError(String.format("Migration file is empty: %s", f));
                }
            } catch (IOException e) {
                log.info("Failed to inspect migration file '{}': {}", file, e.getMessage(), e);
                throw new AssertionError(String.format("Failed to inspect migration file: %s", f), e);
            }
        }
        return this;
    }

    /**
     * Returns the sorted file names directly under {@code baseDir},
     * one per line, for diagnostic logging.
     */
    private static String listDirectorySafely(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString())
                        .sorted()
                        .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "(failed to list directory: " + e.getMessage() + ")";
        }
    }
}
