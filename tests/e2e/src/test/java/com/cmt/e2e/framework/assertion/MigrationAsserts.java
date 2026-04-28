package com.cmt.e2e.framework.assertion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cmt.e2e.framework.command.execution.CommandResult;

/**
 * Helper for validating CMT Console migration results
 * such as stdout, stderr, and exit code.
 */
public final class MigrationAsserts {
    private static final Pattern FATAL_STDERR_LINE = Pattern.compile(
        "(?m)^(?:ERROR\\b|FATAL\\b|Exception(?:\\s|:)|Caused by:|java\\.lang\\.[A-Za-z]+Exception)"
    );

    private MigrationAsserts() {}

    /**
     * Validates that the migration completed successfully.
     * <ul>
     *   <li>exit code = 0</li>
     *   <li>stdout contains {@code MIGRATION RESULT: SUCCESS}</li>
     * </ul>
     */
    public static void assertMigrationSucceeded(CommandResult result) {
        assertThat(result.timedOut())
            .as("migration timeout")
            .isFalse();

        assertThat(result.exitCode())
            .as("migration exit code")
            .isZero();

        assertThat(result.stdout())
            .as("migration result")
            .contains("MIGRATION RESULT: SUCCESS");
    }

    /**
     * Validates that stderr does not contain an obvious fatal signal.
     *
     * <p>CMT and the JVM may legitimately write harmless lines to stderr
     * (for example JAVA_TOOL_OPTIONS), so stderr is not required to be empty.
     */
    public static void assertNoFatalStderr(CommandResult result) {
        Matcher matcher = FATAL_STDERR_LINE.matcher(result.stderr());
        if (matcher.find()) {
            throw new AssertionError(
                "CMT stderr contained fatal pattern: " + extractLine(result.stderr(), matcher.start()));
        }
    }

    private static String extractLine(String text, int index) {
        int start = text.lastIndexOf('\n', Math.max(0, index - 1)) + 1;
        int end = text.indexOf('\n', index);
        if (end < 0) {
            end = text.length();
        }
        return text.substring(start, end);
    }
}
