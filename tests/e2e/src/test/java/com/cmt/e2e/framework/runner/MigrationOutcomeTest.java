package com.cmt.e2e.framework.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cmt.e2e.framework.command.CommandResult;
import org.junit.jupiter.api.Test;

/**
 * Unit test for L1 verifications on {@link MigrationOutcome}. Source/Target
 * are not exercised here — those are passed as {@code null} since the L1
 * checks operate purely on {@link CommandResult}. The Phase 4 smoke test
 * exercises the full pipeline with real containers.
 */
class MigrationOutcomeTest {

    @Test
    void expectSuccess_passes_when_exit0_and_marker_present() {
        MigrationOutcome o = outcome(new CommandResult(
            "MIGRATION RESULT: SUCCESS\n", "", "", 0, false));
        assertThat(o.expectSuccess()).isSameAs(o);
    }

    @Test
    void expectSuccess_fails_on_nonzero_exit() {
        MigrationOutcome o = outcome(new CommandResult(
            "...stuff...\n", "boom", "", 2, false));
        assertThatThrownBy(o::expectSuccess)
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("exit=2");
    }

    @Test
    void expectSuccess_fails_when_timed_out() {
        MigrationOutcome o = outcome(new CommandResult("", "", "", -1, true));
        assertThatThrownBy(o::expectSuccess)
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("timed out");
    }

    @Test
    void expectSuccess_fails_when_marker_missing() {
        MigrationOutcome o = outcome(new CommandResult(
            "started\nfinished\n", "", "", 0, false));
        assertThatThrownBy(o::expectSuccess)
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining(MigrationOutcome.SUCCESS_MARKER);
    }

    @Test
    void expectNoFatalStderr_passes_on_clean_stderr() {
        MigrationOutcome o = outcome(new CommandResult(
            "MIGRATION RESULT: SUCCESS\n",
            "Picked up JAVA_TOOL_OPTIONS: -Doracle.jdbc.timezoneAsRegion=false\n",
            "", 0, false));
        assertThat(o.expectNoFatalStderr()).isSameAs(o);
    }

    @Test
    void expectNoFatalStderr_fails_on_caused_by() {
        MigrationOutcome o = outcome(new CommandResult(
            "", "Caused by: java.sql.SQLException: nope\n", "", 0, false));
        assertThatThrownBy(o::expectNoFatalStderr)
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("Caused by:");
    }

    @Test
    void expectNoFatalStderr_fails_on_top_level_ERROR() {
        MigrationOutcome o = outcome(new CommandResult(
            "", "ERROR something terrible\n", "", 0, false));
        assertThatThrownBy(o::expectNoFatalStderr)
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("ERROR");
    }

    private MigrationOutcome outcome(CommandResult result) {
        return new MigrationOutcome(result, null, null, null, "smoke_unit_test");
    }
}
