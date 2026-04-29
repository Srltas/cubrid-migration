package com.cmt.e2e.framework.verify;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Compares an actual text payload against a checked-in snapshot file,
 * with a developer escape hatch for capturing/refreshing snapshots.
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>Default (CI / regression):</b> read snapshot file, diff against
 *       {@code actual}, throw {@link AssertionError} on mismatch.</li>
 *   <li><b>Update ({@code -Dsnapshot.update=true}):</b> write {@code actual}
 *       to the snapshot path (creating parent dirs as needed). No diff,
 *       no exception. Used for capturing a new snapshot or absorbing an
 *       expected CMT-output change.</li>
 * </ul>
 *
 * <p>The update flag is read once per call via {@link Boolean#getBoolean(String)}
 * so tests can toggle it via {@code System.setProperty} without rebuilding.
 *
 * <h2>Workflow</h2>
 * <pre>
 *   mvn test                          # diff mode — fails on mismatch
 *   mvn -Dsnapshot.update=true test   # regen mode — overwrites snapshots
 * </pre>
 * After regen, the developer reviews the file diff in their PR and
 * documents the change in the commit message.
 */
public final class SnapshotStore {

    /** System property toggling capture/regen mode. */
    public static final String UPDATE_PROP = "snapshot.update";

    private SnapshotStore() {}

    /**
     * Compare {@code actual} to the contents of {@code snapshotPath}.
     *
     * @throws AssertionError if the snapshot is missing (default mode) or
     *         differs from {@code actual}
     */
    public static void match(Path snapshotPath, String actual) {
        if (updateMode()) {
            write(snapshotPath, actual);
            return;
        }
        if (!Files.exists(snapshotPath)) {
            throw new AssertionError(
                "Snapshot missing: " + snapshotPath + "\n" +
                "Run with -D" + UPDATE_PROP + "=true to capture an initial snapshot.");
        }
        String expected;
        try {
            expected = Files.readString(snapshotPath, UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Failed to read snapshot: " + snapshotPath, e);
        }
        if (!expected.equals(actual)) {
            throw new AssertionError(diffMessage(snapshotPath, expected, actual));
        }
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static boolean updateMode() {
        return Boolean.getBoolean(UPDATE_PROP);
    }

    private static void write(Path path, String content) {
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(path, content, UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write snapshot: " + path, e);
        }
    }

    private static String diffMessage(Path path, String expected, String actual) {
        String[] expLines = expected.split("\n", -1);
        String[] actLines = actual.split("\n", -1);
        int max = Math.max(expLines.length, actLines.length);
        int firstDiff = -1;
        for (int i = 0; i < max; i++) {
            String e = i < expLines.length ? expLines[i] : "<MISSING>";
            String a = i < actLines.length ? actLines[i] : "<MISSING>";
            if (!e.equals(a)) { firstDiff = i; break; }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Snapshot mismatch: ").append(path).append('\n');
        if (firstDiff >= 0) {
            sb.append("First difference at line ").append(firstDiff + 1).append(":\n");
            sb.append("  expected: ").append(lineAt(expLines, firstDiff)).append('\n');
            sb.append("  actual:   ").append(lineAt(actLines, firstDiff)).append('\n');
        }
        sb.append("\n--- expected (").append(expLines.length - 1).append(" lines) ---\n");
        sb.append(expected);
        sb.append("--- actual (").append(actLines.length - 1).append(" lines) ---\n");
        sb.append(actual);
        sb.append("--- end ---\n");
        sb.append("To accept the new snapshot, re-run with -D")
          .append(UPDATE_PROP).append("=true.\n");
        return sb.toString();
    }

    private static String lineAt(String[] lines, int idx) {
        return idx < lines.length ? lines[idx] : "<MISSING>";
    }
}
