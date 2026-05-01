package com.cmt.e2e.tests.migration.tibero;

import com.cmt.e2e.framework.junit.AbstractMigrationE2E;
import com.cmt.e2e.framework.junit.MigrationE2E;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.source.Sources;
import com.cmt.e2e.framework.target.Target;
import com.cmt.e2e.framework.target.Targets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Tibero 7 e2e dataset → CUBRID online migration.
 *
 * <p>Phase 3b parity with Oracle's business + view + synonym + ref_schema +
 * grant coverage (no type-test tables yet — those land in a later phase).
 * Each {@code @Test} maps to one verification layer (ARCHITECTURE.md §3).
 *
 * <p>Snapshot files:
 * {@code src/test/resources/snapshots/tibero_to_cubrid/}.
 */
@MigrationE2E(name = "tibero_to_cubrid")
@DisplayName("TIB-ON: Tibero e2e dataset → CUBRID online migration")
@EnabledIf("com.cmt.e2e.framework.db.containers.TiberoEnvironment#isAvailable")
class TiberoToCubridTest extends AbstractMigrationE2E {

    @Override protected Source source() { return Sources.tiberoE2eSeed(); }
    @Override protected Target target() { return Targets.cubridOnline(); }

    // L1 — smoke ----------------------------------------------------------

    @Test
    @DisplayName("CMT exits 0 with MIGRATION RESULT: SUCCESS, no fatal stderr")
    void migration_succeeds() {
        run().expectSuccess().expectNoFatalStderr();
    }

    // L2 — coverage (catalog snapshots) -----------------------------------
    //
    // Routines (function/procedure) are deferred until V4 lands — Tibero
    // PL/SQL syntax is Oracle-compatible but ClasspathSqlRunner's naive
    // semicolon-splitter doesn't understand BEGIN…END blocks, so a
    // dedicated PL/SQL applier is needed first. Type-test tables (V3) and
    // representative-rows are deferred to a later batch.

    @Test
    @DisplayName("All target classes (tables/views) match snapshot")
    void classes_match_snapshot() {
        run().catalog().matchesSnapshot("classes");
    }

    @Test
    @DisplayName("All synonyms preserved (cross-schema reference)")
    void synonyms_match_snapshot() {
        run().catalog().matchesSnapshot("synonyms");
    }

    @Test
    @DisplayName("Cross-schema GRANTs preserved")
    void grants_match_snapshot() {
        run().catalog().matchesSnapshot("grants");
    }

    // L3 — fidelity (column types, indexes, sequences, row counts) --------

    @Test
    @DisplayName("Column types preserved through Tibero → CUBRID translation")
    void columns_match_snapshot() {
        run().catalog().matchesSnapshot("columns");
    }

    @Test
    @DisplayName("Primary keys preserved (with key columns and order)")
    void pk_match_snapshot() {
        run().catalog().matchesSnapshot("pk");
    }

    @Test
    @DisplayName("Foreign-key indexes preserved (with referencing columns)")
    void fk_match_snapshot() {
        run().catalog().matchesSnapshot("fk");
    }

    @Test
    @DisplayName("Unique non-PK indexes preserved (with key columns)")
    void unique_match_snapshot() {
        run().catalog().matchesSnapshot("unique");
    }

    @Test
    @DisplayName("Plain indexes preserved (asc/desc, function expressions)")
    void indexes_match_snapshot() {
        // Plain bucket includes idx_, idxd_ (descending), and idxf_ (functional —
        // TENTATIVE per tibero/SEED_SPEC.md §1; first-run determines whether
        // Tibero's fetcher emits the function expression like Oracle's does).
        run().catalog().matchesSnapshot("indexes");
    }

    @Test
    @DisplayName("Tibero SEQUENCE current_val preserved as CUBRID SERIAL current_val")
    void serials_match_snapshot() {
        run().catalog().matchesSnapshot("serials");
    }

    @Test
    @DisplayName("Row counts per migrated table match")
    void row_counts_match_snapshot() {
        run().rowCounts().matchesSnapshot("row_counts");
    }
}
