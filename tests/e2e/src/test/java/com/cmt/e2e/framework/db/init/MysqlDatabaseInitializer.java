package com.cmt.e2e.framework.db.init;

import com.cmt.e2e.framework.db.containers.MySqlContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flyway-based helper for initializing MySQL test databases.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * MySqlContainer source = MySqlContainer.withMainUser();
 * source.start();
 * MysqlDatabaseInitializer.of(source)
 *     .migrate("mysql/main_schema");
 * }</pre>
 *
 * <h2>Scenario Directory Layout</h2>
 * <pre>
 * src/test/resources/db/
 * └── mysql/
 *     ├── init/
 *     │   └── 00_prepare_database.sql      <- runs as root at container startup
 *     │                                       (mounted by MySqlContainer.withMainUser)
 *     └── main_schema/                     <- executed as main_user via migrate / migrateMain
 *         ├── V1__schema_business_tables.sql
 *         ├── V2__schema_views.sql
 *         ├── V3__schema_type_test_tables.sql
 *         ├── V4__schema_extensions.sql
 *         ├── V5__schema_routines.sql
 *         └── V99__data.sql
 * </pre>
 *
 * <p>{@code ref_schema} is intentionally absent — see SEED_SPEC §1
 * anti-coverage: CMT MySQL fetcher does not implement {@code buildGrant} /
 * {@code buildSynonym} and collapses everything into a single connection-user
 * namespace.
 *
 * <h2>MySQL Schema Isolation</h2>
 * In MySQL, "schema" and "database" are synonyms. Each Flyway invocation
 * connects to a single database via the JDBC URL path component, and Flyway 's
 * {@code defaultSchema} is set to that database so the
 * {@code flyway_schema_history} table is created inside it.
 *
 * <p><b>Note</b>: {@link DatabaseInitializer} is CUBRID-specific,
 * {@link OracleDatabaseInitializer} is Oracle-specific. Use this class for
 * MySQL initialization.
 */
public final class MysqlDatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(MysqlDatabaseInitializer.class);

    private static final String MYSQL_DRIVER  = "com.mysql.cj.jdbc.Driver";
    private static final String SCENARIO_BASE = "classpath:db/";

    private final MySqlContainer container;

    private MysqlDatabaseInitializer(MySqlContainer container) {
        this.container = container;
    }

    /**
     * Creates a {@code MysqlDatabaseInitializer} instance.
     *
     * @param container already-started {@code MySqlContainer}
     */
    public static MysqlDatabaseInitializer of(MySqlContainer container) {
        if (container == null) throw new IllegalArgumentException("container must not be null");
        return new MysqlDatabaseInitializer(container);
    }

    /**
     * Convenience: run a scenario as the container 's main user against the
     * main database. Equivalent to
     * {@link #migrateAs(String, String, String, String)
     * migrateAs(getMainDatabase(), getMainUser(), getMainPassword(), scenarioName)}.
     */
    public MysqlDatabaseInitializer migrate(String scenarioName) {
        return migrateMain(scenarioName);
    }

    /**
     * Convenience: run a scenario as the container 's main user against the
     * main database.
     */
    public MysqlDatabaseInitializer migrateMain(String scenarioName) {
        return migrateAs(container.getMainDatabase(),
                         container.getMainUser(),
                         container.getMainPassword(),
                         scenarioName);
    }

    /**
     * Connects as the specified user against the specified database and applies
     * scenario {@code V*.sql} files in version order.
     *
     * @param database     MySQL database / schema name (becomes the JDBC URL path)
     * @param user         login user
     * @param password     login user 's password
     * @param scenarioName relative path under {@code src/test/resources/db/}
     * @return {@code this} for chaining
     * @throws DatabaseInitializationException if script execution fails
     */
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

    /**
     * Removes all objects from the main database. Useful when reusing a
     * container; unnecessary when each test starts a fresh one.
     */
    public void clean() {
        log.info("[MysqlDatabaseInitializer] clean start: database='{}', user='{}'",
            container.getMainDatabase(), container.getMainUser());
        try {
            buildFlyway(SCENARIO_BASE + "_clean_placeholder",
                        container.getMainDatabase(),
                        container.getMainUser(),
                        container.getMainPassword()).clean();
            log.info("[MysqlDatabaseInitializer] clean complete");
        } catch (FlywayException e) {
            throw new DatabaseInitializationException(
                "Failed to clean MySQL database '" + container.getMainDatabase() + "': " + e.getMessage(), e);
        }
    }

    /** Runs {@link #clean()} and then {@link #migrate(String)} when reusing containers. */
    public void reset(String scenarioName) {
        clean();
        migrate(scenarioName);
    }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    private Flyway buildFlyway(String location, String database, String user, String password) {
        // jdbc:mysql://host:port/database — the path component pins the connection
        // to one database, which Flyway also uses as defaultSchema for history tracking.
        String jdbcUrl = container.getJdbcUrl(database, user);

        return Flyway.configure()
            .dataSource(jdbcUrl, user, password)
            .driver(MYSQL_DRIVER)
            .defaultSchema(database)        // MySQL: schema == database
            .schemas(database)
            .locations(location)
            .cleanDisabled(false)           // allow clean() in tests
            .baselineOnMigrate(false)
            .validateOnMigrate(true)
            .load();
    }
}
