package com.cmt.e2e.framework.assertion;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Declares how each dump output path should be verified.
 *
 * <p>GOLDEN entries are deterministic DDL/control files and are compared with
 * checked-in expected files. DATA entries contain table records and are only
 * checked for existence/non-emptiness here; round-trip loading verifies their
 * contents in a later test layer.
 */
public final class DumpManifest {
    public enum Category {
        GOLDEN,
        DATA
    }

    public enum PathType {
        FILE,
        DIRECTORY
    }

    public record Entry(
        String relativePath,
        Category category,
        PathType pathType,
        List<String> expectedChildren
    ) {
        public Entry {
            expectedChildren = List.copyOf(expectedChildren);
        }
    }

    private final List<Entry> entries;

    private DumpManifest(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Entry> entries() {
        return entries;
    }

    public List<Entry> goldenFiles() {
        return entries.stream()
            .filter(e -> e.category() == Category.GOLDEN)
            .toList();
    }

    public static final class Builder {
        private final Map<String, Entry> entries = new LinkedHashMap<>();

        public Builder golden(String relativePath) {
            add(relativePath, Category.GOLDEN, PathType.FILE);
            return this;
        }

        public Builder dataFile(String relativePath) {
            add(relativePath, Category.DATA, PathType.FILE);
            return this;
        }

        public Builder dataDirectory(String relativePath) {
            add(relativePath, Category.DATA, PathType.DIRECTORY, List.of());
            return this;
        }

        public Builder dataDirectory(String relativePath, String... expectedFileNames) {
            add(relativePath, Category.DATA, PathType.DIRECTORY, normalizeFileNames(expectedFileNames));
            return this;
        }

        public DumpManifest build() {
            if (entries.isEmpty()) {
                throw new IllegalStateException("At least one dump output path is required.");
            }
            return new DumpManifest(new ArrayList<>(entries.values()));
        }

        private void add(String relativePath, Category category, PathType pathType) {
            add(relativePath, category, pathType, List.of());
        }

        private void add(
            String relativePath,
            Category category,
            PathType pathType,
            List<String> expectedChildren
        ) {
            String normalizedPath = requireRelativePath(relativePath);
            Entry previous = entries.put(
                normalizedPath,
                new Entry(normalizedPath, category, pathType, expectedChildren));
            if (previous != null) {
                throw new IllegalStateException("Duplicate dump output path: " + normalizedPath);
            }
        }

        private static List<String> normalizeFileNames(String[] fileNames) {
            Set<String> normalizedNames = new LinkedHashSet<>();
            for (String fileName : fileNames) {
                normalizedNames.add(requireFileName(fileName));
            }
            if (normalizedNames.isEmpty()) {
                throw new IllegalStateException("At least one expected file name is required.");
            }
            return new ArrayList<>(normalizedNames);
        }

        private static String requireRelativePath(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Dump output path must not be blank.");
            }
            if (value.startsWith("/") || value.contains("..")) {
                throw new IllegalStateException("Dump output path must be a safe relative path: " + value);
            }
            return value.replace('\\', '/');
        }

        private static String requireFileName(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Expected file name must not be blank.");
            }
            if (value.contains("/") || value.contains("\\") || value.contains("..")) {
                throw new IllegalStateException("Expected file name must be a plain file name: " + value);
            }
            return value;
        }
    }
}
