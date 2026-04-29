package com.cmt.e2e.framework.target;

import com.cmt.e2e.framework.source.ConnectionConfig;

/**
 * Migration target — either an online CUBRID database or a CMT
 * {@code unload} dump-file output.
 *
 * <p>The two shapes intentionally share one interface so {@code Migration}
 * can dispatch on {@link #isDumpfile()} once and tests don't branch on
 * "what kind of target".
 *
 * <p>Lifecycle mirrors {@link com.cmt.e2e.framework.source.Source}.
 * Online targets bring up a container on {@link #start()}; dump-file
 * targets are no-ops at start time (output dir is owned by CMT, not by
 * the test framework).
 */
public interface Target extends AutoCloseable {

    /**
     * Brings up backing resources (container for online; nothing for dump).
     * Idempotent.
     */
    void start();

    /**
     * The connection identity CMT writes into the {@code <connection id="target">}
     * block. Returns {@code null} when {@link #isDumpfile()} is {@code true}.
     */
    ConnectionConfig connection();

    /** {@code true} when the target is a CMT {@code unload} dump-file output. */
    boolean isDumpfile();

    /**
     * Dump-file specific options ({@code file_prefix}, {@code one_table_one_file}).
     * Returns {@code null} when {@link #isDumpfile()} is {@code false}.
     */
    DumpfileOptions dumpfileOptions();

    @Override
    void close();
}
