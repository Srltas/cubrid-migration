package com.cmt.e2e.framework.db.init;

import com.cmt.e2e.framework.db.containers.InformixContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flyway-based helper for Informix. Informix has no schema namespace —
 * every object's schema equals its creating user, so {@code schema}
 * arg matches {@code user}. Requires {@code flyway-database-informix}
 * on the classpath.
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

    private Flyway buildFlyway(String location, String database, String schema, String user, String password) {
        return Flyway.configure()
            .dataSource(container.getJdbcUrl(database, user), user, password)
            .driver(INFORMIX_DRIVER)
            .defaultSchema(schema)
            .schemas(schema)
            // Per-user history table — flyway-database-informix mis-detects
            // a shared history table on non-ANSI Informix DBs ("Error -310:
            // Table already exists" when the second user bootstraps).
            .table("flyway_schema_history_" + schema)
            .locations(location)
            .cleanDisabled(true)
            .baselineOnMigrate(false)
            .validateOnMigrate(true)
            .load();
    }
}
