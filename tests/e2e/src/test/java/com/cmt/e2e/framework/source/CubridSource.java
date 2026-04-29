package com.cmt.e2e.framework.source;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import com.cmt.e2e.framework.db.containers.CubridContainer;
import com.cmt.e2e.framework.db.init.ClasspathSqlRunner;
import com.cmt.e2e.framework.db.init.CubridDatabaseInitializer;

/**
 * CUBRID source — two-user e2e seed (REF_SCHEMA + MAIN_SCHEMA).
 *
 * <p>Bootstrap order is: dba → CREATE USER (init/), then Flyway as
 * each user. CMT introspects the source as {@code dba} so it can see
 * objects across both schemas (single-user connect would miss
 * {@code REF_SCHEMA.e2e_ref_audit}).
 */
final class CubridSource implements Source {

    private final CubridContainer container;
    private boolean started;

    CubridSource() {
        this.container = CubridContainer.withEmptyDb();
    }

    @Override
    public void start() {
        if (started) return;
        container.start();

        String dbaUrl = container.getJdbcUrl(container.getDatabaseName(), "dba");
        ClasspathSqlRunner.runDirectory(dbaUrl, "dba", "", "db/cubrid/init");

        CubridDatabaseInitializer.of(container, container.getDatabaseName(), "REF_SCHEMA", "cmt")
            .migrate("cubrid/ref_schema");
        CubridDatabaseInitializer.of(container, container.getDatabaseName(), "MAIN_SCHEMA", "cmt")
            .migrate("cubrid/main_schema");

        started = true;
    }

    @Override
    public ConnectionConfig connection() {
        // dba sees both REF_SCHEMA and MAIN_SCHEMA; needed for CMT introspection.
        return new ConnectionConfig(
            DB.CUBRID,
            container.getHost(),
            container.getDatabasePort(),
            container.getDatabaseName(),
            "dba",
            "",
            "utf-8",
            null
        );
    }

    @Override
    public DB type() {
        return DB.CUBRID;
    }

    @Override
    public void close() {
        if (started) {
            container.close();
        }
    }
}
