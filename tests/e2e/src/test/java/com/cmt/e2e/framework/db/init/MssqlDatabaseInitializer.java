package com.cmt.e2e.framework.db.init;

import com.cmt.e2e.framework.db.containers.MsSqlContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Flyway-based seed helper for MSSQL (database + schema both apply). */
public final class MssqlDatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(MssqlDatabaseInitializer.class);

    private static final String MSSQL_DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    private static final String SCENARIO_BASE = "classpath:db/";

    private final MsSqlContainer container;

    private MssqlDatabaseInitializer(MsSqlContainer container) {
        this.container = container;
    }

    public static MssqlDatabaseInitializer of(MsSqlContainer container) {
        if (container == null) throw new IllegalArgumentException("container must not be null");
        return new MssqlDatabaseInitializer(container);
    }

    /** Convenience: run a scenario as main_user against main_schema in e2e_db. */
    public MssqlDatabaseInitializer migrateMain(String scenarioName) {
        return migrateAs(container.getDatabaseName(),
                         container.getMainSchema(),
                         container.getMainUser(),
                         container.getMainPassword(),
                         scenarioName);
    }

    /**
     * Convenience: run a scenario as ref_user against ref_schema. Call
     * before {@link #migrateMain} so cross-schema synonym targets exist.
     */
    public MssqlDatabaseInitializer migrateRef(String scenarioName) {
        return migrateAs(container.getDatabaseName(),
                         container.getRefSchema(),
                         container.getRefUser(),
                         container.getRefPassword(),
                         scenarioName);
    }

    public MssqlDatabaseInitializer migrateAs(
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
        log.info("[MssqlDatabaseInitializer] migrate start: scenario='{}', database='{}', schema='{}', user='{}'",
            scenarioName, database, schema, user);

        try {
            MigrateResult result = buildFlyway(location, database, schema, user, password).migrate();
            log.info("[MssqlDatabaseInitializer] migrate complete: scenario='{}', schema='{}', user='{}', executed={}, success={}",
                scenarioName, schema, user, result.migrationsExecuted, result.success);

            if (!result.success) {
                throw new DatabaseInitializationException(
                    "Flyway migration reported failure for scenario '" + scenarioName +
                    "' on database '" + database + "', schema '" + schema +
                    "' as user '" + user + "'", null);
            }
        } catch (FlywayException e) {
            throw new DatabaseInitializationException(
                "Failed to migrate MSSQL scenario '" + scenarioName +
                "' on database '" + database + "', schema '" + schema +
                "' as user '" + user + "': " + e.getMessage(), e);
        }
        return this;
    }

    private Flyway buildFlyway(String location, String database, String schema, String user, String password) {
        return Flyway.configure()
            .dataSource(container.getJdbcUrl(database, user), user, password)
            .driver(MSSQL_DRIVER)
            .defaultSchema(schema)
            .schemas(schema)
            .locations(location)
            .cleanDisabled(true)
            .baselineOnMigrate(false)
            .validateOnMigrate(true)
            .load();
    }
}
