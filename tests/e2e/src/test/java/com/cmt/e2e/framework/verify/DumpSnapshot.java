package com.cmt.e2e.framework.verify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Dump-file snapshot — compares the entire CMT {@code unload} output tree
 * against a checked-in golden tree.
 *
 * <p>Snapshot location:
 * {@code src/test/resources/snapshots/<scenario>/dumpfile/...}
 * — mirrors the structure of
 * {@code $CMT_CONSOLE_HOME/output/<migration-name>/...} 1:1.
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>Default (CI):</b> walk the actual output tree; for each file
 *       call {@link SnapshotStore#match(Path, String)} against the
 *       corresponding snapshot file. Then walk the snapshot tree and
 *       fail if any expected file is missing from CMT output.</li>
 *   <li><b>Update ({@code -Dsnapshot.update=true}):</b> wipe the snapshot
 *       directory, then copy the actual output tree into it.</li>
 * </ul>
 *
 * <p>If the dump output contains non-deterministic content (e.g. data
 * file row order), the standard fix is to add CMT-side determinism in
 * {@link com.cmt.e2e.framework.runner.ScriptXmlBuilder#sanitize(String)}
 * (e.g. forcing {@code split_schema=yes}, fixed timezone) rather than
 * special-casing files here.
 */
public final class DumpSnapshot {

    private final Path outputBase;
    private final String scenarioName;

    public DumpSnapshot(Path outputBase, String scenarioName) {
        this.outputBase = outputBase;
        this.scenarioName = scenarioName;
    }

    /** Compare the full output tree against {@code snapshots/<scenario>/dumpfile/}. */
    public DumpSnapshot matchesSnapshot() {
        Path snapshotBase = CatalogSnapshot.SNAPSHOT_ROOT
            .resolve(scenarioName)
            .resolve("dumpfile");

        if (Boolean.getBoolean(SnapshotStore.UPDATE_PROP)) {
            captureTree(outputBase, snapshotBase);
            return this;
        }
        diffTree(outputBase, snapshotBase);
        return this;
    }

    // -------------------------------------------------------------------------
    // capture (update mode)
    // -------------------------------------------------------------------------

    private static void captureTree(Path actual, Path snapshot) {
        if (!Files.isDirectory(actual)) {
            throw new AssertionError(
                "Cannot capture dump snapshot: CMT output not found at " + actual);
        }
        try {
            if (Files.exists(snapshot)) {
                deleteRecursively(snapshot);
            }
            Files.createDirectories(snapshot);
            try (Stream<Path> walk = Files.walk(actual)) {
                List<Path> all = walk.toList();
                for (Path src : all) {
                    Path rel = actual.relativize(src);
                    Path dst = snapshot.resolve(rel);
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dst);
                    } else {
                        Files.createDirectories(dst.getParent());
                        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to capture dump snapshot at " + snapshot, e);
        }
    }

    // -------------------------------------------------------------------------
    // diff (default mode)
    // -------------------------------------------------------------------------

    private static void diffTree(Path actual, Path snapshot) {
        if (!Files.isDirectory(actual)) {
            throw new AssertionError(
                "CMT did not produce a dump output directory: " + actual);
        }
        if (!Files.isDirectory(snapshot)) {
            throw new AssertionError(
                "Dump snapshot missing: " + snapshot + "\n"
                + "Run with -D" + SnapshotStore.UPDATE_PROP + "=true to capture.");
        }

        // (1) Each file in actual must match its snapshot counterpart.
        List<Path> actualFiles = listFiles(actual);
        for (Path file : actualFiles) {
            Path rel = actual.relativize(file);
            Path snap = snapshot.resolve(rel);
            String content = readString(file);
            SnapshotStore.match(snap, content);
        }

        // (2) Each file in snapshot must exist in actual.
        List<Path> snapshotFiles = listFiles(snapshot);
        List<String> missing = new ArrayList<>();
        for (Path snap : snapshotFiles) {
            Path rel = snapshot.relativize(snap);
            Path actualFile = actual.resolve(rel);
            if (!Files.exists(actualFile)) {
                missing.add(rel.toString());
            }
        }
        if (!missing.isEmpty()) {
            throw new AssertionError(
                "Expected dump files missing from CMT output:\n  - "
                    + String.join("\n  - ", missing));
        }
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static List<Path> listFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            throw new RuntimeException("walk failed: " + root, e);
        }
    }

    private static String readString(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new RuntimeException("read failed: " + file, e);
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); }
                catch (IOException e) { throw new RuntimeException("delete: " + p, e); }
            });
        }
    }
}
