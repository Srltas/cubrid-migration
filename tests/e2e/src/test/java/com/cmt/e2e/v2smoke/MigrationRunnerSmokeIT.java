package com.cmt.e2e.v2smoke;

import static org.assertj.core.api.Assertions.assertThat;

import com.cmt.e2e.framework.junit.AbstractMigrationE2E;
import com.cmt.e2e.framework.junit.MigrationE2E;
import com.cmt.e2e.framework.runner.MigrationOutcome;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.source.Sources;
import com.cmt.e2e.framework.target.Target;
import com.cmt.e2e.framework.target.Targets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Phase 2.4 smoke — exercises the v2 runner pipeline plumbing
 * end-to-end:
 *
 * <pre>
 *   Sources.cubridE2eSeed()                                 // CUBRID source + seed
 *   Targets.cubridOnline()                                  // empty CUBRID target
 *   ↓ AbstractMigrationE2E lifecycle
 *   ↓ Migration.run() = DbConfBuilder + ScriptXmlBuilder
 *                       + StartCommand
 *   ↓ MigrationOutcome (CommandResult captured)
 * </pre>
 *
 * <h2>Scope intentionally narrow</h2>
 * This test verifies that the <b>plumbing</b> works — script.xml is
 * generated, CMT is invoked, output is captured, MigrationOutcome carries
 * a CommandResult. It does <b>not</b> assert {@code MIGRATION RESULT:
 * SUCCESS}; that is L1 behaviour for the actual scenario tests in
 * Phase 4/5, which also need to add CMT-quirk sanitisation (e.g.
 * filtering DBA / PUBLIC system schemas from the generated script.xml
 * when introspecting CUBRID as {@code dba}).
 *
 * <h2>Why CUBRID-to-CUBRID for smoke</h2>
 * Two CUBRID containers are the fastest path: ~75-90 s startup each on
 * Apple Silicon (amd64 emulation) plus ~60 s for CMT script + start.
 * Total ≈ 3-4 minutes vs. ~8 minutes if Oracle were involved.
 */
@MigrationE2E(name = "smoke_cubrid_to_cubrid")
@DisplayName("Phase 2.4 — v2 runner pipeline plumbing")
class MigrationRunnerSmokeIT extends AbstractMigrationE2E {

    @Override protected Source source() { return Sources.cubridE2eSeed(); }
    @Override protected Target target() { return Targets.cubridOnline(); }

    @Test
    @DisplayName("CMT script.xml generation + start invocation succeeds end-to-end")
    void pipeline_invokes_cmt_and_captures_output() {
        MigrationOutcome outcome = run();

        // Pipeline ran to completion (no IO/timeout/abort).
        assertThat(outcome.commandResult().timedOut()).isFalse();

        // CMT produced its standard banner and a Migration Report summary —
        // proves migration.sh start actually ran the engine, not just exited.
        assertThat(outcome.commandResult().stdout())
            .contains("Thank you for using CUBRID Migration Toolkit")
            .contains("Migration Report summary");

        // script.xml was generated and exists on disk.
        assertThat(outcome.scriptXml()).isNotNull();
        assertThat(outcome.scriptXml().toFile()).exists().isFile();
    }
}
