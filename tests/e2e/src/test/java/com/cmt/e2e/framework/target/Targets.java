package com.cmt.e2e.framework.target;

/**
 * Factory for E2E migration targets — one factory method per CMT
 * destination type ({@code MigrationConfiguration.DEST_*}). Currently
 * supported:
 * <ul>
 *   <li>{@link #cubridOnline()} — {@code DEST_ONLINE}: empty CUBRID
 *       container; CMT migrates schemas/data into it as {@code dba}.</li>
 *   <li>{@link #unload(String, boolean)} — {@code DEST_DB_UNLOAD}: no
 *       container; CMT writes the CUBRID LoadDB-format dump tree
 *       ({@code _class}, {@code _pk}, {@code _object} etc.) to its
 *       {@code output/} directory.</li>
 * </ul>
 *
 * <p>Future formats ({@code DEST_CSV}, {@code DEST_SQL}, {@code DEST_XLS})
 * land as peer factory methods so each TC names its destination type
 * explicitly per ARCHITECTURE.md §12.0.
 */
public final class Targets {

    private Targets() {}

    /** Empty CUBRID 11.4 container as the {@code DEST_ONLINE} target. */
    public static Target cubridOnline() {
        return new CubridOnlineTarget();
    }

    /**
     * {@code DEST_DB_UNLOAD} target — produces the CUBRID LoadDB-format
     * dump tree consumed by {@code loaddb}. {@code filePrefix} controls
     * the prefix of generated control files; {@code oneTableOneFile}
     * splits the {@code _object} dump per table when {@code true}.
     */
    public static Target unload(String filePrefix, boolean oneTableOneFile) {
        return new DumpFileTarget(new DumpfileOptions(filePrefix, oneTableOneFile));
    }
}
