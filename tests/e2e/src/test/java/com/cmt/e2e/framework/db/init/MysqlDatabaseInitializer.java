package com.cmt.e2e.framework.db.init;

import com.cmt.e2e.framework.db.containers.MySqlContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Flyway-based seed helper for MySQL (schema == database). */
public final class MysqlDatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(MysqlDatabaseInitializer.class);

    private static final String MYSQL_DRIVER  = "com.mysql.cj.jdbc.Driver";
    private static final String SCENARIO_BASE = "classpath:db/";

    private final MySqlContainer container;

    private MysqlDatabaseInitializer(MySqlContainer container) {
        this.container = container;
    }

    public static MysqlDatabaseInitializer of(MySqlContainer container) {
        if (container == null) throw new IllegalArgumentException("container must not be null");
        return new MysqlDatabaseInitializer(container);
    }

    /** Convenience: run a scenario as main_user against the main database. */
    public MysqlDatabaseInitializer migrateMain(String scenarioName) {
        return migrateAs(container.getMainDatabase(),
                         container.getMainUser(),
                         container.getMainPassword(),
                         scenarioName);
    }

    public MysqlDatabaseInitializer migrateAs(String database, String user, String password, String scenarioName) {
        if (database == null || database.isBlank()) {
            throw new IllegalArgumentException("database must not be blank");
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
        log.info("[MysqlDatabaseInitializer] migrate start: scenario='{}', database='{}', user='{}'",
            scenarioName, database, user);

        try {
            MigrateResult result = buildFlyway(location, database, user, password).migrate();
            log.info("[MysqlDatabaseInitializer] migrate complete: scenario='{}', database='{}', user='{}', executed={}, success={}",
                scenarioName, database, user, result.migrationsExecuted, result.success);

            if (!result.success) {
                throw new DatabaseInitializationException(
                    "Flyway migration reported failure for scenario '" + scenarioName +
                    "' on database '" + database + "' as user '" + user + "'", null);
            }
        } catch (FlywayException e) {
            throw new DatabaseInitializationException(
                "Failed to migrate MySQL scenario '" + scenarioName +
                "' on database '" + database + "' as user '" + user + "': " + e.getMessage(), e);
        }
        return this;
    }

    private Flyway buildFlyway(String location, String database, String user, String password) {
        return Flyway.configure()
            .dataSource(container.getJdbcUrl(database, user), user, password)
            .driver(MYSQL_DRIVER)
            .defaultSchema(database)
            .schemas(database)
            .locations(location)
            .cleanDisabled(true)
            .baselineOnMigrate(false)
            .validateOnMigrate(true)
            .load();
    }
}
