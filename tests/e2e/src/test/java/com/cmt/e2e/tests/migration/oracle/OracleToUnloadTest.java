package com.cmt.e2e.tests.migration.oracle;

import com.cmt.e2e.framework.junit.AbstractMigrationE2E;
import com.cmt.e2e.framework.junit.MigrationE2E;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.source.Sources;
import com.cmt.e2e.framework.target.Target;
import com.cmt.e2e.framework.target.Targets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Oracle 11g e2e dataset → CMT {@code unload} (CUBRID LoadDB) dump.
 *
 * <p>The "unload" name aligns with CMT's own destination type token —
 * {@code MigrationConfiguration.setDestTypeName("unload")} maps to
 * {@code DEST_DB_UNLOAD}, the format consumed by CUBRID's
 * {@code loaddb} import tool.
 *
 * <p><b>Variant grouping via {@code @Nested}.</b> The outer class is a
 * namespace for the {@code Oracle → unload} shape; each option
 * combination lives as a {@code @Nested} inner class. Reading the
 * outer alone tells you the source/target shape; reading an inner
 * tells you the specific options. This avoids both
 * (a) class-name proliferation and
 * (b) implicit "default" pretending — every {@code @Nested} explicitly
 * declares its full {@code options[]}. ARCHITECTURE.md §12 / D14.
 */
@DisplayName("ORA-UN: Oracle e2e dataset → CMT unload (LoadDB) dump")
class OracleToUnloadTest {

    /**
     * Variant: {@code split_schema=true}, {@code one_table_one_file=true}.
     * Schema and data go to separate files, and each table gets its own
     * data file ("split + per-table fanout").
     */
    @Nested
    @MigrationE2E(
        name = "oracle_to_unload__split_per_table",
        options = {
            "file_prefix=XE",
            "split_schema=true",
            "one_table_one_file=true",
        })
    @DisplayName("ORA-UN-SPLIT-PER-TABLE: split_schema=true, one_table_one_file=true (file_prefix=XE)")
    class SplitPerTable extends AbstractMigrationE2E {

        @Override protected Source source() { return Sources.oracleE2eSeed(); }
        @Override protected Target target() { return Targets.unload("XE", true); }

        @Test
        @DisplayName("CMT exits 0 with MIGRATION RESULT: SUCCESS, no fatal stderr")
        void migration_succeeds() {
            run().expectSuccess().expectNoFatalStderr();
        }

        @Test
        @DisplayName("Dump file tree matches snapshot")
        void dump_tree_matches_snapshot() {
            run().dumpfile().matchesSnapshot();
        }
    }

    // Future variants (each as @Nested with its own @MigrationE2E + scenario id):
    //   @Nested class FlatMerged       — split_schema=false, one_table_one_file=false
    //   @Nested class SplitMerged      — split_schema=true,  one_table_one_file=false
    //   @Nested class FlatPerTable     — split_schema=false, one_table_one_file=true
}
