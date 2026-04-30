package com.cmt.e2e.framework.source;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import com.cmt.e2e.framework.db.containers.TiberoContainer;
import com.cmt.e2e.framework.db.init.TiberoDatabaseInitializer;

/**
 * Tibero 7 source — two-user e2e seed (REF_SCHEMA + MAIN_SCHEMA).
 * Mirrors {@link OracleSource} shape so the migration TC uses the same
 * {@code AbstractMigrationE2E} lifecycle without per-engine plumbing.
 *
 * <p>Differences from {@code OracleSource}:
 * <ul>
 *   <li>Init runs after boot via JDBC ({@link TiberoDatabaseInitializer#initAsSys})
 *       because the Tibero image has no {@code /container-entrypoint-initdb.d/}
 *       hook.</li>
 *   <li>Seed application uses raw JDBC (Flyway has no Tibero plugin).</li>
 *   <li>Order: SYS init → REF_SCHEMA migrate → MAIN_SCHEMA migrate. The
 *       MAIN_SCHEMA synonym (V5) references {@code REF_SCHEMA.e2e_ref_audit}
 *       which is created in REF_SCHEMA V1, so REF must run first.</li>
 * </ul>
 */
final class TiberoSource implements Source {

    private final TiberoContainer container;
    private boolean started;

    TiberoSource() {
        this.container = TiberoContainer.create();
    }

    @Override
    public void start() {
        if (started) return;
        container.start();
        TiberoDatabaseInitializer.of(container)
            .initAsSys("db/tibero/init")
            .migrateAs(container.getRefUser(),  container.getRefPassword(),  "db/tibero/ref_schema")
            .migrateAs(container.getMainUser(), container.getMainPassword(), "db/tibero/main_schema");
        started = true;
    }

    @Override
    public ConnectionConfig connection() {
        return new ConnectionConfig(
            DB.TIBERO,
            container.getHost(),
            container.getDatabasePort(),
            container.getSid(),
            container.getMainUser(),
            container.getMainPassword(),
            "utf-8",
            "GMT+00:00"
        );
    }

    @Override
    public DB type() {
        return DB.TIBERO;
    }

    @Override
    public void close() {
        if (started) {
            container.close();
        }
    }
}
