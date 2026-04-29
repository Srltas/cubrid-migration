package com.cmt.e2e.framework.runner;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.List;

import com.cmt.e2e.framework.command.CommandResult;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.target.Target;
import com.cmt.e2e.framework.verify.CatalogSnapshot;
import com.cmt.e2e.framework.verify.RowCounts;
import com.cmt.e2e.framework.verify.RowQueries;
import com.cmt.e2e.framework.verify.RowQuery;

/**
 * Outcome of one {@link Migration#run(Path)} call. Tests use this to
 * assert behaviour at four layers — see {@code ARCHITECTURE.md} §3:
 *
 * <ul>
 *   <li>L1 smoke: {@link #expectSuccess()}, {@link #expectNoFatalStderr()}
 *       (this phase)</li>
 *   <li>L2 coverage / L3 fidelity: {@code .catalog()},
 *       {@code .rowCounts()}, {@code .query()} (Phase 3)</li>
 *   <li>L4 regression: same fluent surface as L3, scoped per {@code @Test}</li>
 * </ul>
 *
 * <p>The fluent builders return {@code this} so multiple expectations
 * can chain inside one statement, but tests should generally place each
 * assertion in its own {@code @Test} method per the layer contract.
 */
public final class MigrationOutcome {

    /** Substring CMT writes to stdout on a successful run. */
    static final String SUCCESS_MARKER = "MIGRATION RESULT: SUCCESS";

    /**
     * Stderr lines that, when present, indicate a real failure rather
     * than benign noise. Mirrors the PoC {@code MigrationAsserts} regex.
     */
    static final Pattern FATAL_STDERR = Pattern.compile(
        "(?m)^(?:ERROR\\b|FATAL\\b|Exception(?:\\s|:)|Caused by:|java\\.lang\\.[A-Za-z]+Exception)");

    private final CommandResult result;
    private final Source source;
    private final Target target;
    private final Path scriptXml;
    private final String scenarioName;

    public MigrationOutcome(CommandResult result, Source source, Target target,
                            Path scriptXml, String scenarioName) {
        this.result       = result;
        this.source       = source;
        this.target       = target;
        this.scriptXml    = scriptXml;
        this.scenarioName = scenarioName;
    }

    // -------------------------------------------------------------------------
    // L1 — smoke
    // -------------------------------------------------------------------------

    /**
     * Asserts the migration ran to completion: not timed out, exit code 0,
     * and {@code MIGRATION RESULT: SUCCESS} present in stdout.
     *
     * @return {@code this} for chaining
     * @throws AssertionError on any of the above failing
     */
    public MigrationOutcome expectSuccess() {
        if (result.timedOut()) {
            throw new AssertionError("migration timed out");
        }
        if (result.exitCode() != 0) {
            throw new AssertionError(String.format(
                "migration failed (exit=%d)%nstdout:%n%s%nstderr:%n%s",
                result.exitCode(), result.stdout(), result.stderr()));
        }
        if (!result.stdout().contains(SUCCESS_MARKER)) {
            throw new AssertionError(
                "stdout missing '" + SUCCESS_MARKER + "':\n" + result.stdout());
        }
        return this;
    }

    /**
     * Asserts CMT did not emit a fatal-looking stderr line. CMT and the JVM
     * may print harmless lines (e.g. {@code JAVA_TOOL_OPTIONS} echo); this
     * check looks for the patterns that indicate a real failure.
     */
    public MigrationOutcome expectNoFatalStderr() {
        Matcher m = FATAL_STDERR.matcher(result.stderr());
        if (m.find()) {
            throw new AssertionError(
                "stderr contained fatal pattern: " + extractLine(result.stderr(), m.start()));
        }
        return this;
    }

    private static String extractLine(String text, int index) {
        int start = text.lastIndexOf('\n', Math.max(0, index - 1)) + 1;
        int end = text.indexOf('\n', index);
        if (end < 0) end = text.length();
        return text.substring(start, end);
    }

    // -------------------------------------------------------------------------
    // L2 / L3 — verify entry points
    // -------------------------------------------------------------------------

    /**
     * Returns a CUBRID catalog snapshot helper for this migration's target.
     * Online-target only; throws on dump-file targets.
     */
    public CatalogSnapshot catalog() {
        requireOnlineTarget("catalog()");
        return new CatalogSnapshot(target.connection(), scenarioName);
    }

    /**
     * Returns a row-count snapshot helper. With no arguments, all user
     * tables (owner not in DBA/PUBLIC) are counted. Pass owner names to
     * restrict (e.g. {@code rowCounts("MAIN_SCHEMA")}).
     */
    public RowCounts rowCounts(String... ownerSchemas) {
        requireOnlineTarget("rowCounts()");
        return new RowCounts(target.connection(), scenarioName, List.of(ownerSchemas));
    }

    /** Single arbitrary SQL query against the target. */
    public RowQuery query(String sql) {
        requireOnlineTarget("query()");
        return new RowQuery(sql, target.connection(), scenarioName);
    }

    /**
     * Run all queries from a labelled SQL file
     * ({@code src/test/resources/queries/<scenario>.sql} by convention)
     * and snapshot the concatenated output.
     */
    public RowQueries queries(java.nio.file.Path sqlFile) {
        requireOnlineTarget("queries()");
        return new RowQueries(sqlFile, target.connection(), scenarioName);
    }

    private void requireOnlineTarget(String op) {
        if (target.isDumpfile()) {
            throw new IllegalStateException(
                op + " is for online targets; this is a dump-file scenario. "
                + "Use dumpfile() instead (Phase 3.5).");
        }
    }

    // -------------------------------------------------------------------------
    // accessors — used by Phase 3 verify entry points and pipeline smoke
    // -------------------------------------------------------------------------

    /** Full CMT child-process result (stdout, stderr, exit code, timed-out flag). */
    public CommandResult commandResult() { return result; }

    /** Path to the sanitized script.xml that was fed into {@code migration.sh start}. */
    public Path scriptXml() { return scriptXml; }

    Source source() { return source; }
    Target target() { return target; }
}
