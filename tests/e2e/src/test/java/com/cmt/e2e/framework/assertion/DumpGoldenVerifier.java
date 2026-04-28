package com.cmt.e2e.framework.assertion;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies CMT dump outputs with category-specific rules:
 * deterministic DDL/control files are compared to golden files, while data
 * files are checked only for existence and non-emptiness.
 */
public final class DumpGoldenVerifier {
    private static final Logger log = LoggerFactory.getLogger(DumpGoldenVerifier.class);

    private final MigrationOutput output;

    private DumpGoldenVerifier(MigrationOutput output) {
        this.output = output;
    }

    public static DumpGoldenVerifier of(MigrationOutput output) {
        return new DumpGoldenVerifier(output);
    }

    public DumpGoldenVerifier verify(DumpManifest manifest, Path expectedBaseDir) {
        for (DumpManifest.Entry entry : manifest.entries()) {
            verifyRequiredEntry(entry);
        }

        for (DumpManifest.Entry entry : manifest.goldenFiles()) {
            compareGoldenFile(entry.relativePath(), expectedBaseDir);
        }

        return this;
    }

    private void verifyRequiredEntry(DumpManifest.Entry entry) {
        Path actualPath = output.baseDir().resolve(entry.relativePath());
        if (entry.pathType() == DumpManifest.PathType.FILE) {
            assertRegularNonEmptyFile(actualPath, entry.relativePath());
            return;
        }

        assertDataDirectory(actualPath, entry.relativePath(), entry.expectedChildren());
    }

    private void compareGoldenFile(String relativePath, Path expectedBaseDir) {
        Path expectedPath = expectedBaseDir.resolve(relativePath);
        Path actualPath = output.baseDir().resolve(relativePath);

        assertRegularNonEmptyFile(expectedPath, "expected/" + relativePath);
        String expected = sanitize(readText(expectedPath));
        String actual = sanitize(readText(actualPath));

        if (!actual.equals(expected)) {
            Difference diff = firstDifference(expected, actual);
            log.info(
                "Dump golden mismatch for '{}'. expectedPath={}, actualPath={}, firstDifference={}",
                relativePath, expectedPath, actualPath, diff);
            throw new AssertionError(String.format(
                "Dump golden mismatch: %s (first difference at line %d)", relativePath, diff.lineNumber()));
        }
    }

    private static void assertRegularNonEmptyFile(Path path, String label) {
        if (!Files.exists(path)) {
            throw new AssertionError("Missing dump file: " + label);
        }
        if (!Files.isRegularFile(path)) {
            throw new AssertionError("Dump output is not a regular file: " + label);
        }
        try {
            if (Files.size(path) == 0L) {
                throw new AssertionError("Dump file is empty: " + label);
            }
        } catch (IOException e) {
            throw new AssertionError("Failed to inspect dump file: " + label, e);
        }
    }

    private static void assertDataDirectory(Path path, String label, List<String> expectedFileNames) {
        if (!Files.exists(path)) {
            throw new AssertionError("Missing dump directory: " + label);
        }
        if (!Files.isDirectory(path)) {
            throw new AssertionError("Dump output is not a directory: " + label);
        }

        if (!expectedFileNames.isEmpty()) {
            assertDirectoryFileNames(path, label, expectedFileNames);
            for (String fileName : expectedFileNames) {
                assertRegularNonEmptyFile(path.resolve(fileName), label + "/" + fileName);
            }
            return;
        }

        List<Path> files;
        try (Stream<Path> walk = Files.walk(path)) {
            files = walk
                .filter(Files::isRegularFile)
                .sorted()
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new AssertionError("Failed to inspect dump directory: " + label, e);
        }

        if (files.isEmpty()) {
            throw new AssertionError("Dump directory has no files: " + label);
        }

        for (Path file : files) {
            assertRegularNonEmptyFile(file, label + "/" + path.relativize(file));
        }
    }

    private static void assertDirectoryFileNames(
        Path path,
        String label,
        List<String> expectedFileNames
    ) {
        List<String> actualFileNames;
        try (Stream<Path> children = Files.list(path)) {
            actualFileNames = children
                .map(child -> child.getFileName().toString())
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new AssertionError("Failed to list dump directory: " + label, e);
        }

        Set<String> expected = new LinkedHashSet<>(expectedFileNames);
        Set<String> actual = new LinkedHashSet<>(actualFileNames);

        List<String> missing = new ArrayList<>(expected);
        missing.removeAll(actual);

        List<String> extra = new ArrayList<>(actual);
        extra.removeAll(expected);

        if (!missing.isEmpty() || !extra.isEmpty()) {
            log.info("Dump directory '{}' has unexpected file names. expected={}, actual={}, missing={}, extra={}",
                label, expectedFileNames, actualFileNames, missing, extra);
            throw new AssertionError(String.format(
                "Dump directory file names differ: %s (missing=%s, extra=%s)", label, missing, extra));
        }
    }

    private static String readText(Path path) {
        try {
            return Files.readString(path, UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Failed to read dump file: " + path, e);
        }
    }

    private static String sanitize(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static Difference firstDifference(String expected, String actual) {
        String[] expectedLines = expected.split("\n", -1);
        String[] actualLines = actual.split("\n", -1);
        int max = Math.max(expectedLines.length, actualLines.length);
        for (int i = 0; i < max; i++) {
            String expectedLine = i < expectedLines.length ? expectedLines[i] : "<missing>";
            String actualLine = i < actualLines.length ? actualLines[i] : "<missing>";
            if (!expectedLine.equals(actualLine)) {
                return new Difference(i + 1, expectedLine, actualLine);
            }
        }
        return new Difference(1, "", "");
    }

    private record Difference(int lineNumber, String expectedLine, String actualLine) {}
}
