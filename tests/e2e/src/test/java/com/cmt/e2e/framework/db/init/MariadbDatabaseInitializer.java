package com.cmt.e2e.framework.db.init;

import com.cmt.e2e.framework.db.containers.MariaDbContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flyway-based helper for MariaDB test databases. MariaDB "schema" ==
 * database (same as MySQL); each {@link #migrateAs} call connects to one
 * database and Flyway tracks {@code flyway_schema_history} inside it.
 */
public final class MariadbDatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(MariadbDatabaseInitializer.class);

    private static final String MARIADB_DRIVER = "org.mariadb.jdbc.Driver";
    private static final String SCENARIO_BASE  = "classpath:db/";

    private final MariaDbContainer container;

    private MariadbDatabaseInitializer(MariaDbContainer container) {
        this.container = container;
    }

    public static MariadbDatabaseInitializer of(MariaDbContainer container) {
        if (container == null) throw new IllegalArgumentException("container must not be null");
        return new MariadbDatabaseInitializer(container);
    }

    /** Convenience: run a scenario as main_user against the main database. */
    public MariadbDatabaseInitializer migrateMain(String scenarioName) {
        return migrateAs(container.getMainDatabase(),
                         container.getMainUser(),
                         container.getMainPassword(),
                         scenarioName);
    }

    public MariadbDatabaseInitializer migrateAs(String database, String user, String password, String scenarioName) {
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
        log.info("[MariadbDatabaseInitializer] migrate start: scenario='{}', database='{}', user='{}'",
            scenarioName, database, user);

        try {
            MigrateResult result = buildFlyway(location, database, user, password).migrate();
            log.info("[MariadbDatabaseInitializer] migrate complete: scenario='{}', database='{}', user='{}', executed={}, success={}",
                scenarioName, database, user, result.migrationsExecuted, result.success);

            if (!result.success) {
                throw new DatabaseInitializationException(
                    "Flyway migration reported failure for scenario '" + scenarioName +
                    "' on database '" + database + "' as user '" + user + "'", null);
            }
        } catch (FlywayException e) {
            throw new DatabaseInitializationException(
                "Failed to migrate MariaDB scenario '" + scenarioName +
                "' on database '" + database + "' as user '" + user + "': " + e.getMessage(), e);
        }
        return this;
    }

    private Flyway buildFlyway(String location, String database, String user, String password) {
        // jdbc:mariadb://host:port/database — the path component pins the connection
        // to one database, which Flyway also uses as defaultSchema for history tracking.
        String jdbcUrl = container.getJdbcUrl(database, user);

        return Flyway.configure()
            .dataSource(jdbcUrl, user, password)
            .driver(MARIADB_DRIVER)
            .defaultSchema(database)        // MariaDB: schema == database
            .schemas(database)
            .locations(location)
            .cleanDisabled(true)
            .baselineOnMigrate(false)
            .validateOnMigrate(true)
            .load();
    }
}
