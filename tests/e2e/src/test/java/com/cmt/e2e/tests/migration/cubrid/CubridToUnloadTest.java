package com.cmt.e2e.tests.migration.cubrid;

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
 * CUBRID e2e dataset → CMT {@code unload} (CUBRID LoadDB) dump.
 *
 * <p>See {@link com.cmt.e2e.tests.migration.oracle.OracleToUnloadTest} for
 * the outer/{@code @Nested} variant pattern rationale.
 */
@DisplayName("CUB-UN: CUBRID e2e dataset → CMT unload (LoadDB) dump")
class CubridToUnloadTest {

    /**
     * Variant: {@code split_schema=true}, {@code one_table_one_file=true}.
     *
     * <p>PoC used {@code one_table_one_file=false} (single combined data
     * dump), but CMT does not stabilise the table order inside that
     * combined file — snapshots fail under regression mode. Splitting
     * per table gives a deterministic file-per-class layout (D10).
     */
    @Nested
    @MigrationE2E(
        name = "cubrid_to_unload__split_per_table",
        options = {
            "file_prefix=demodb",
            "split_schema=true",
            "one_table_one_file=true",
        })
    @DisplayName("CUB-UN-SPLIT-PER-TABLE: split_schema=true, one_table_one_file=true (file_prefix=demodb)")
    class SplitPerTable extends AbstractMigrationE2E {

        @Override protected Source source() { return Sources.cubridE2eSeed(); }
        @Override protected Target target() { return Targets.unload("demodb", true); }

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
}
