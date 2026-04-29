package com.cmt.e2e.tests.migration.oracle;

import com.cmt.e2e.framework.junit.AbstractMigrationE2E;
import com.cmt.e2e.framework.junit.MigrationE2E;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.source.Sources;
import com.cmt.e2e.framework.target.Target;
import com.cmt.e2e.framework.target.Targets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Oracle 11g e2e dataset → CMT {@code unload} dump file.
 *
 * <p>Snapshot: {@code src/test/resources/snapshots/oracle_to_dumpfile/dumpfile/}
 * mirrors the directory tree CMT writes under
 * {@code $CMT_CONSOLE_HOME/output/<migration-name>/}.
 *
 * <p>Dump-file options match PoC: {@code file_prefix="XE"} and
 * {@code one_table_one_file=true} (Oracle is the only DB where each
 * table gets its own dump file in the PoC convention).
 */
@MigrationE2E(name = "oracle_to_dumpfile")
@DisplayName("ORA-DO: Oracle e2e dataset → CMT dump file")
class OracleToDumpTest extends AbstractMigrationE2E {

    @Override protected Source source() { return Sources.oracleE2eSeed(); }
    @Override protected Target target() { return Targets.dumpFile("XE", true); }

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
