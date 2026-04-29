package com.cmt.e2e.tests.migration.oracle;

import java.nio.file.Path;

import com.cmt.e2e.framework.junit.AbstractMigrationE2E;
import com.cmt.e2e.framework.junit.MigrationE2E;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.source.Sources;
import com.cmt.e2e.framework.target.Target;
import com.cmt.e2e.framework.target.Targets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Oracle 11g e2e dataset → CUBRID online migration.
 *
 * <p>Each {@code @Test} verifies one fact at one verification layer
 * (ARCHITECTURE.md §3). The migration runs once per class
 * ({@code @BeforeAll} in {@link AbstractMigrationE2E}); every test
 * here reads the cached outcome.
 *
 * <p>Snapshot files: {@code src/test/resources/snapshots/oracle_to_cubrid/}.
 * To regenerate after an intended CMT-output change:
 * <pre>{@code
 *   mvn -Dsnapshot.update=true test -Dtest=OracleToCubridTest
 * }</pre>
 */
@MigrationE2E(name = "oracle_to_cubrid")
@DisplayName("ORA-ON: Oracle e2e dataset → CUBRID online migration")
class OracleToCubridTest extends AbstractMigrationE2E {

    @Override protected Source source() { return Sources.oracleE2eSeed(); }
    @Override protected Target target() { return Targets.cubridOnline(); }

    // L1 — smoke ----------------------------------------------------------

    @Test
    @DisplayName("CMT exits 0 with MIGRATION RESULT: SUCCESS, no fatal stderr")
    void migration_succeeds() {
        run().expectSuccess().expectNoFatalStderr();
    }

    // L2 — coverage (catalog snapshots) -----------------------------------

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
    @DisplayName("All stored routines (FUNCTION/PROCEDURE) preserved")
    void routines_match_snapshot() {
        run().catalog().matchesSnapshot("routines");
    }

    @Test
    @DisplayName("Cross-schema GRANTs preserved")
    void grants_match_snapshot() {
        run().catalog().matchesSnapshot("grants");
    }

    // L3 — fidelity (column types, indexes, sequences, row counts) --------

    @Test
    @DisplayName("Column types preserved through Oracle → CUBRID translation")
    void columns_match_snapshot() {
        run().catalog().matchesSnapshot("columns");
    }

    @Test
    @DisplayName("Indexes preserved (PK/UK/FK/functional)")
    void indexes_match_snapshot() {
        var c = run().catalog();
        c.matchesSnapshot("indexes");
        c.matchesSnapshot("index_keys");
    }

    @Test
    @DisplayName("Oracle SEQUENCE current_val preserved as CUBRID SERIAL current_val")
    void serials_match_snapshot() {
        run().catalog().matchesSnapshot("serials");
    }

    @Test
    @DisplayName("Row counts per migrated table match")
    void row_counts_match_snapshot() {
        run().rowCounts().matchesSnapshot("row_counts");
    }

    @Test
    @DisplayName("Representative business rows preserved (8 spot-checks)")
    void representative_rows_match_snapshot() {
        run().queries(Path.of("src/test/resources/queries/oracle_to_cubrid.sql"))
             .matchesSnapshot("representative_rows");
    }
}
