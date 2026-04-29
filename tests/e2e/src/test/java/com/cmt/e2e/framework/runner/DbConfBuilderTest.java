package com.cmt.e2e.framework.runner;

import static org.assertj.core.api.Assertions.assertThat;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import com.cmt.e2e.framework.source.ConnectionConfig;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.target.DumpfileOptions;
import com.cmt.e2e.framework.target.Target;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link DbConfBuilder}. Uses inline fake {@link Source}/
 * {@link Target} so the test runs without containers. Driver path
 * resolution still hits {@link com.cmt.e2e.framework.db.JdbcDriverJars}
 * which requires {@code target/test-classes/driver/} populated by
 * {@code mvn generate-test-resources} (always set up in normal Maven
 * lifecycle).
 */
class DbConfBuilderTest {

    @Test
    void oracle_source_to_cubrid_online_target() {
        Source source = fakeSource(new ConnectionConfig(
            DB.ORACLE, "ora.host", 1521, "XE", "MAIN", "pwd",
            "utf-8", "GMT+00:00"));
        Target target = fakeOnlineTarget(new ConnectionConfig(
            DB.CUBRID, "cub.host", 33000, "e2e_db", "dba", "",
            "utf-8", null));

        String conf = DbConfBuilder.build(source, target);

        assertThat(conf).contains(
            "regen_source.type=oracle",
            "regen_source.host=ora.host",
            "regen_source.port=1521",
            "regen_source.dbname=XE",
            "regen_source.user=MAIN",
            "regen_source.password=pwd",
            "regen_source.charset=utf-8",
            "regen_source.timezone=GMT+00:00",
            "regen_target.type=cubrid",
            "regen_target.host=cub.host",
            "regen_target.port=33000",
            "regen_target.dbname=e2e_db",
            "regen_target.user=dba",
            "regen_target.password=",
            "regen_target.charset=utf-8",
            "regen_target.add_schema=yes"
        );
        // Driver paths come from JdbcDriverJars; test only that the keys exist.
        assertThat(conf).contains("regen_source.driver=").contains("regen_target.driver=");
    }

    @Test
    void cubrid_source_to_dumpfile_target() {
        Source source = fakeSource(new ConnectionConfig(
            DB.CUBRID, "cub.host", 33000, "e2e_db", "dba", "",
            "utf-8", null));
        Target target = fakeDumpfileTarget(new DumpfileOptions("demodb", false));

        String conf = DbConfBuilder.build(source, target);

        assertThat(conf).contains(
            "regen_source.type=cubrid",
            "regen_target.type=unload",
            "regen_target.output=./output",
            "regen_target.charset=utf-8",
            "regen_target.add_schema=yes",
            "regen_target.split_schema=yes",
            "regen_target.file_prefix=demodb",
            "regen_target.one_table_one_file=no"
        );
        // Source had null timezone — must be omitted, not emitted as blank.
        assertThat(conf).doesNotContain("regen_source.timezone=");
        // Dumpfile target has no host/port/driver.
        assertThat(conf).doesNotContain("regen_target.host=")
                        .doesNotContain("regen_target.port=")
                        .doesNotContain("regen_target.driver=");
    }

    @Test
    void oracle_source_one_table_one_file_yes() {
        Source source = fakeSource(new ConnectionConfig(
            DB.ORACLE, "h", 1521, "XE", "u", "p", "utf-8", "GMT+00:00"));
        Target target = fakeDumpfileTarget(new DumpfileOptions("XE", true));

        String conf = DbConfBuilder.build(source, target);

        assertThat(conf).contains(
            "regen_target.file_prefix=XE",
            "regen_target.one_table_one_file=yes"
        );
    }

    // -------------------------------------------------------------------------
    // tiny inline fakes
    // -------------------------------------------------------------------------

    private Source fakeSource(ConnectionConfig c) {
        return new Source() {
            @Override public void start()                  { }
            @Override public ConnectionConfig connection() { return c; }
            @Override public DB type()                     { return c.type(); }
            @Override public void close()                  { }
        };
    }

    private Target fakeOnlineTarget(ConnectionConfig c) {
        return new Target() {
            @Override public void start()                       { }
            @Override public ConnectionConfig connection()      { return c; }
            @Override public boolean isDumpfile()               { return false; }
            @Override public DumpfileOptions dumpfileOptions()  { return null; }
            @Override public void close()                       { }
        };
    }

    private Target fakeDumpfileTarget(DumpfileOptions opts) {
        return new Target() {
            @Override public void start()                       { }
            @Override public ConnectionConfig connection()      { return null; }
            @Override public boolean isDumpfile()               { return true; }
            @Override public DumpfileOptions dumpfileOptions()  { return opts; }
            @Override public void close()                       { }
        };
    }
}
