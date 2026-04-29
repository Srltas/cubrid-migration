package com.cmt.e2e.framework.db.init;

import com.cmt.e2e.framework.db.containers.MariaDbContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flyway-based helper for initializing MariaDB test databases.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * MariaDbContainer source = MariaDbContainer.withMainUser();
 * source.start();
 * MariadbDatabaseInitializer.of(source)
 *     .migrate("mariadb/main_schema");
 * }</pre>
 *
 * <h2>Scenario Directory Layout</h2>
 * <pre>
 * src/test/resources/db/
 * └── mariadb/
 *     ├── init/
 *     │   └── 00_prepare_database.sql      <- runs as root at container startup
 *     │                                       (mounted by MariaDbContainer.withMainUser)
 *     └── main_schema/                     <- executed as main_user via migrate / migrateMain
 *         ├── V1__schema_business_tables.sql
 *         ├── V2__schema_views.sql           (tentative — see SEED_SPEC §4.2)
 *         ├── V3__schema_type_test_tables.sql
 *         ├── V4__schema_extensions.sql      (enum core; SET / JSON tentative)
 *         ├── V5__schema_routines.sql        (function + procedure)
 *         └── V99__data.sql
 * </pre>
 *
 * <p>{@code ref_schema} is intentionally absent — see SEED_SPEC §1
 * anti-coverage: CMT MariaDB fetcher does not implement {@code buildGrant}
 * / {@code buildSynonym}.
 *
 * <h2>MariaDB Schema Isolation</h2>
 * In MariaDB, "schema" and "database" are synonyms (same as MySQL). Each
 * Flyway invocation connects to a single database via the JDBC URL path
 * component, and Flyway 's {@code defaultSchema} is set to that database
 * so the {@code flyway_schema_history} table is created inside it.
 *
 * <p><b>Note</b>: {@link DatabaseInitializer} is CUBRID-specific,
 * {@link OracleDatabaseInitializer} is Oracle-specific,
 * {@link MysqlDatabaseInitializer} is MySQL-specific. Use this class for
 * MariaDB initialization.
 */
public final class MariadbDatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(MariadbDatabaseInitializer.class);

    private static final String MARIADB_DRIVER = "org.mariadb.jdbc.Driver";
    private static final String SCENARIO_BASE  = "classpath:db/";

    private final MariaDbContainer container;

    private MariadbDatabaseInitializer(MariaDbContainer container) {
        this.container = container;
    }

    /**
     * Creates a {@code MariadbDatabaseInitializer} instance.
     *
     * @param container already-started {@code MariaDbContainer}
     */
    public static MariadbDatabaseInitializer of(MariaDbContainer container) {
        if (container == null) throw new IllegalArgumentException("container must not be null");
        return new MariadbDatabaseInitializer(container);
    }

    /**
     * Convenience: run a scenario as the container 's main user against the
     * main database.
     */
    public MariadbDatabaseInitializer migrateMain(String scenarioName) {
        return migrateAs(container.getMainDatabase(),
                         container.getMainUser(),
                         container.getMainPassword(),
                         scenarioName);
    }

    /**
     * Connects as the specified user against the specified database and applies
     * scenario {@code V*.sql} files in version order.
     *
     * @param database     MariaDB database / schema name (becomes the JDBC URL path)
     * @param user         login user
     * @param password     login user 's password
     * @param scenarioName relative path under {@code src/test/resources/db/}
     * @return {@code this} for chaining
     * @throws DatabaseInitializationException if script execution fails
     */
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
