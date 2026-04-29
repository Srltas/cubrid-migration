package com.cmt.e2e.tests.migration.cubrid;

import com.cmt.e2e.framework.junit.AbstractMigrationE2E;
import com.cmt.e2e.framework.junit.MigrationE2E;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.source.Sources;
import com.cmt.e2e.framework.target.Target;
import com.cmt.e2e.framework.target.Targets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CUBRID e2e dataset → CMT {@code unload} dump file.
 *
 * <p>Snapshot: {@code src/test/resources/snapshots/cubrid_to_dumpfile/dumpfile/}
 * mirrors the directory tree CMT writes under
 * {@code $CMT_CONSOLE_HOME/output/<migration-name>/}.
 *
 * <p>Dump-file options match PoC: {@code file_prefix="demodb"} and
 * {@code one_table_one_file=false}.
 */
@MigrationE2E(name = "cubrid_to_dumpfile")
@DisplayName("CUB-DO: CUBRID e2e dataset → CMT dump file")
class CubridToDumpTest extends AbstractMigrationE2E {

    @Override protected Source source() { return Sources.cubridE2eSeed(); }
    @Override protected Target target() { return Targets.dumpFile("demodb", false); }

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
