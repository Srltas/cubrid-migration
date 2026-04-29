package com.cmt.e2e.framework.runner;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cmt.e2e.framework.command.CommandResult;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.target.Target;

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

    MigrationOutcome(CommandResult result, Source source, Target target, Path scriptXml) {
        this.result    = result;
        this.source    = source;
        this.target    = target;
        this.scriptXml = scriptXml;
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
    // package-private accessors — used by Phase 3 verify entry points
    // -------------------------------------------------------------------------

    Source source()                 { return source; }
    Target target()                 { return target; }
    CommandResult commandResult()   { return result; }
    Path scriptXml()                { return scriptXml; }
}
