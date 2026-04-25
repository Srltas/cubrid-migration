package com.cmt.e2e.framework.assertion;

import static org.assertj.core.api.Assertions.assertThat;

import com.cmt.e2e.framework.command.execution.CommandResult;

/**
 * Helper for validating CMT Console migration results
 * such as stdout, stderr, and exit code.
 */
public final class MigrationAsserts {

    private MigrationAsserts() {}

    /**
     * Validates that the migration completed successfully.
     * <ul>
     *   <li>exit code = 0</li>
     *   <li>stdout contains {@code MIGRATION RESULT: SUCCESS}</li>
     * </ul>
     */
    public static void assertMigrationSucceeded(CommandResult result) {
        assertThat(result.exitCode())
            .as("migration exit code")
            .isZero();

        assertThat(result.stdout())
            .as("migration result")
            .contains("MIGRATION RESULT: SUCCESS");
    }
}
