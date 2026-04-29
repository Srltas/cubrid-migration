package com.cmt.e2e.framework.target;

/**
 * Factory for E2E migration targets.
 *
 * <p>Two shapes:
 * <ul>
 *   <li>{@link #cubridOnline()} — empty CUBRID container; CMT migrates
 *       schemas/data into it as {@code dba}.</li>
 *   <li>{@link #dumpFile(String, boolean)} — no container; CMT writes
 *       SQL/control files to its {@code output/} directory.</li>
 * </ul>
 */
public final class Targets {

    private Targets() {}

    /** Empty CUBRID 11.4 container as the online migration target. */
    public static Target cubridOnline() {
        return new CubridOnlineTarget();
    }

    /**
     * Dump-file target for CMT {@code unload}. {@code filePrefix} controls
     * the prefix of generated control files; {@code oneTableOneFile}
     * splits the {@code _object} dump per table when {@code true}.
     */
    public static Target dumpFile(String filePrefix, boolean oneTableOneFile) {
        return new DumpFileTarget(new DumpfileOptions(filePrefix, oneTableOneFile));
    }
}
