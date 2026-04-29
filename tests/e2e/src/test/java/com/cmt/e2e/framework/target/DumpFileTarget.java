package com.cmt.e2e.framework.target;

import com.cmt.e2e.framework.source.ConnectionConfig;

/**
 * Dump-file target — no container, just settings for CMT {@code unload}.
 *
 * <p>CMT writes its dump output under
 * {@code $CMT_CONSOLE_HOME/output/{migration.name}/{schema}/...}.
 * The verify layer reads files from there directly; this target only
 * carries the config that CMT needs.
 */
final class DumpFileTarget implements Target {

    private final DumpfileOptions options;

    DumpFileTarget(DumpfileOptions options) {
        this.options = options;
    }

    @Override public void start()                      { /* no-op */ }
    @Override public ConnectionConfig connection()     { return null; }
    @Override public boolean isDumpfile()              { return true; }
    @Override public DumpfileOptions dumpfileOptions() { return options; }

    @Override public void close()                      { /* no-op */ }
}
