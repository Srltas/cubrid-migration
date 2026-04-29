package com.cmt.e2e.framework.runner;

import com.cmt.e2e.framework.db.JdbcDriverJars;
import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import com.cmt.e2e.framework.source.ConnectionConfig;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.target.DumpfileOptions;
import com.cmt.e2e.framework.target.Target;

/**
 * Builds the {@code db.conf} text that CMT Console reads when invoked
 * via {@code migration.sh script -s <SOURCE> -t <TARGET>}.
 *
 * <p>The PoC era of this builder needed an if-cascade per source DB
 * because container types differed in user/dbname accessors. With the
 * v2 {@link Source} / {@link Target} abstractions handing back a uniform
 * {@link ConnectionConfig}, this collapses to two helpers — one for
 * source, one for target — neither branching on engine type.
 *
 * <p>The {@code .type} value (e.g. {@code "oracle"}, {@code "cubrid"})
 * is derived from {@link DB#name()} lowercased. If a future engine needs
 * a non-trivial mapping (e.g. {@code postgresql} vs {@code postgres}),
 * the mapping moves into a small lookup here — not into a per-source
 * branch.
 */
public final class DbConfBuilder {

    /** Logical name of the source-side block in db.conf. */
    public static final String SOURCE_NAME = "regen_source";
    /** Logical name of the target-side block in db.conf. */
    public static final String TARGET_NAME = "regen_target";

    private DbConfBuilder() {}

    /** Builds the full db.conf text for one migration. */
    public static String build(Source source, Target target) {
        StringBuilder sb = new StringBuilder();
        appendSource(sb, source.connection());
        appendTarget(sb, target);
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // source-side
    // -------------------------------------------------------------------------

    private static void appendSource(StringBuilder sb, ConnectionConfig c) {
        prop(sb, SOURCE_NAME + ".type",     dbConfType(c.type()));
        prop(sb, SOURCE_NAME + ".driver",   driverPath(c.type()));
        prop(sb, SOURCE_NAME + ".host",     c.host());
        prop(sb, SOURCE_NAME + ".port",     Integer.toString(c.port()));
        prop(sb, SOURCE_NAME + ".dbname",   c.dbname());
        prop(sb, SOURCE_NAME + ".user",     c.user());
        prop(sb, SOURCE_NAME + ".password", c.password());
        prop(sb, SOURCE_NAME + ".charset",  c.charset());
        if (c.timezone() != null) {
            prop(sb, SOURCE_NAME + ".timezone", c.timezone());
        }
    }

    // -------------------------------------------------------------------------
    // target-side
    // -------------------------------------------------------------------------

    private static void appendTarget(StringBuilder sb, Target target) {
        if (target.isDumpfile()) {
            appendDumpfileTarget(sb, target.dumpfileOptions());
            return;
        }
        appendOnlineTarget(sb, target.connection());
    }

    private static void appendOnlineTarget(StringBuilder sb, ConnectionConfig c) {
        prop(sb, TARGET_NAME + ".type",       dbConfType(c.type()));
        prop(sb, TARGET_NAME + ".driver",     driverPath(c.type()));
        prop(sb, TARGET_NAME + ".host",       c.host());
        prop(sb, TARGET_NAME + ".port",       Integer.toString(c.port()));
        prop(sb, TARGET_NAME + ".dbname",     c.dbname());
        prop(sb, TARGET_NAME + ".user",       c.user());
        prop(sb, TARGET_NAME + ".password",   c.password());
        prop(sb, TARGET_NAME + ".charset",    c.charset());
        prop(sb, TARGET_NAME + ".add_schema", "yes");
    }

    private static void appendDumpfileTarget(StringBuilder sb, DumpfileOptions opts) {
        prop(sb, TARGET_NAME + ".type",               "unload");
        prop(sb, TARGET_NAME + ".output",             "./output");
        prop(sb, TARGET_NAME + ".charset",            "utf-8");
        prop(sb, TARGET_NAME + ".add_schema",         "yes");
        prop(sb, TARGET_NAME + ".split_schema",       "yes");
        prop(sb, TARGET_NAME + ".file_prefix",        opts.filePrefix());
        prop(sb, TARGET_NAME + ".one_table_one_file", opts.oneTableOneFile() ? "yes" : "no");
    }

    // -------------------------------------------------------------------------
    // shared helpers
    // -------------------------------------------------------------------------

    private static String dbConfType(DB db) {
        return db.name().toLowerCase();   // ORACLE → oracle
    }

    private static String driverPath(DB db) {
        return JdbcDriverJars.latest(db).toAbsolutePath().toString();
    }

    private static void prop(StringBuilder sb, String key, String value) {
        sb.append(key).append('=').append(value == null ? "" : value).append('\n');
    }
}
