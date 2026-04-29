package com.cmt.e2e.framework.db.init;

import com.cmt.e2e.framework.db.containers.InformixContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flyway-based helper for Informix test databases. Informix has no
 * separate "schema" namespace — every object 's schema is its creating
 * user, so {@link #migrateAs} 's {@code schema} arg matches the user.
 *
 * <p>{@code ref_schema} is absent (single-user pattern); see SEED_SPEC §1
 * for the cross-schema anti-coverage rationale.
 *
 * <p>Requires the {@code flyway-database-informix} plugin on the classpath
 * — Flyway 10.x discovers {@code jdbc:informix-sqli:} support via
 * ServiceLoader and would otherwise raise "No database found to handle ...".
 */
public final class InformixDatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(InformixDatabaseInitializer.class);

    private static final String INFORMIX_DRIVER = "com.informix.jdbc.IfxDriver";
    private static final String SCENARIO_BASE   = "classpath:db/";

    private final InformixContainer container;

    private InformixDatabaseInitializer(InformixContainer container) {
        this.container = container;
    }

    public static InformixDatabaseInitializer of(InformixContainer container) {
        if (container == null) throw new IllegalArgumentException("container must not be null");
        return new InformixDatabaseInitializer(container);
    }

    /** Convenience: run a scenario as main_user against {@code e2e_db}. */
    public InformixDatabaseInitializer migrateMain(String scenarioName) {
        return migrateAs(container.getDatabaseName(),
                         container.getMainSchema(),
                         container.getMainUser(),
                         container.getMainPassword(),
                         scenarioName);
    }

    public InformixDatabaseInitializer migrateAs(
            String database, String schema, String user, String password, String scenarioName) {
        if (database == null || database.isBlank()) {
            throw new IllegalArgumentException("database must not be blank");
        }
        if (schema == null || schema.isBlank()) {
            throw new IllegalArgumentException("schema must not be blank");
        }
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("user must not be blank");
        }
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }
        if (scenarioName == null || scenarioName.isBlank()) {
            throw new IllegalArgumentException("scenarioName must not be blank");
        }

        String resourcePath = "db/" + scenarioName;
        if (Thread.currentThread().getContextClassLoader().getResource(resourcePath) == null) {
            throw new DatabaseInitializationException(
                "Scenario not found on classpath: '" + resourcePath + "'. " +
                "Check src/test/resources/" + resourcePath + " exists.", null);
        }

        String location = SCENARIO_BASE + scenarioName;
        log.info("[InformixDatabaseInitializer] migrate start: scenario='{}', database='{}', schema='{}', user='{}'",
            scenarioName, database, schema, user);

        try {
            MigrateResult result = buildFlyway(location, database, schema, user, password).migrate();
            log.info("[InformixDatabaseInitializer] migrate complete: scenario='{}', schema='{}', user='{}', executed={}, success={}",
                scenarioName, schema, user, result.migrationsExecuted, result.success);

            if (!result.success) {
                throw new DatabaseInitializationException(
                    "Flyway migration reported failure for scenario '" + scenarioName +
                    "' on database '" + database + "', schema '" + schema +
                    "' as user '" + user + "'", null);
            }
        } catch (FlywayException e) {
            throw new DatabaseInitializationException(
                "Failed to migrate Informix scenario '" + scenarioName +
                "' on database '" + database + "', schema '" + schema +
                "' as user '" + user + "': " + e.getMessage(), e);
        }
        return this;
    }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    private Flyway buildFlyway(String location, String database, String schema, String user, String password) {
        // jdbc:informix-sqli://host:port/database:INFORMIXSERVER=informix
        String jdbcUrl = container.getJdbcUrl(database, user);

        return Flyway.configure()
            .dataSource(jdbcUrl, user, password)
            .driver(INFORMIX_DRIVER)
            .defaultSchema(schema)          // Informix: schema = owner = connecting user
            .schemas(schema)
            // Informix multi-user history-table workaround.
            //
            // Two consecutive migrate() calls (migrateRef as ref_user, then
            // migrateMain as main_user) share the same e2e_db database.
            // flyway-database-informix does not consistently owner-qualify the
            // history-table existence check on a non-ANSI Informix database
            // (which is what `CREATE DATABASE ... WITH LOG` produces). The
            // table created by ref_user is then mis-detected when main_user
            // tries to bootstrap, causing
            //     Error -310: Table (main_user.flyway_schema_history) already exists
            //
            // Giving each scenario its own history-table name keeps the two
            // bootstraps independent. ref_user gets
            // flyway_schema_history_ref_user; main_user gets
            // flyway_schema_history_main_user. Both coexist in the same
            // database without colliding.
            //
            // This change does not affect the migration target — CMT 's
            // InformixSchemaFetcher uses systables to enumerate user objects;
            // the per-scenario history tables are filtered out by the
            // top-level test.* / migration.* fixtures via standard naming.
            .table("flyway_schema_history_" + schema)
            .locations(location)
            .cleanDisabled(true)
            .baselineOnMigrate(false)
            .validateOnMigrate(true)
            .load();
    }
}
