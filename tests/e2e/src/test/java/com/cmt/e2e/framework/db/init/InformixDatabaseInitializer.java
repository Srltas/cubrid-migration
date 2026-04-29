package com.cmt.e2e.framework.db.init;

import com.cmt.e2e.framework.db.containers.InformixContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flyway-based helper for initializing Informix test databases.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * InformixContainer source = InformixContainer.withMainUser();
 * source.start();
 * InformixDatabaseInitializer.of(source)
 *     .migrateMain("informix/main_schema");  // runs as main_user @ e2e_db
 * }</pre>
 *
 * <h2>Scenario Directory Layout</h2>
 * <pre>
 * src/test/resources/db/
 * └── informix/
 *     ├── init/
 *     │   └── 00_prepare_database.sql   <- runs via dbaccess inside the container
 *     │                                    (mounted by InformixContainer.withMainUser)
 *     └── main_schema/                  <- runs as main_user (owner = main_user)
 *         ├── V1__schema_business_tables.sql
 *         ├── V2__schema_views.sql
 *         ├── V3__schema_type_test_tables.sql
 *         ├── V4__schema_extensions.sql
 *         └── V99__data.sql
 * </pre>
 *
 * <p>{@code ref_schema} is intentionally absent. The first run confirmed
 * that CMT 's {@code InformixSchemaFetcher} writes an empty
 * {@code schema=""} attribute on source-side {@code <table>} elements
 * even when {@code getSchemas()} returns multiple owners, so multi-user
 * seed objects cannot be exported by CMT. Following the MySQL/MariaDB
 * pattern, the Informix seed collapses to single-user — see SEED_SPEC §1
 * anti-coverage entry on cross-schema.
 *
 * <h2>Informix Schema vs Owner</h2>
 * Informix has no separate "schema" namespace — every object 's schema is
 * its creating user. This initializer connects with different users for
 * MAIN_SCHEMA vs REF_SCHEMA migrations, and Flyway 's {@code defaultSchema}
 * is set to the connecting user 's name so {@code flyway_schema_history}
 * lands under that owner.
 *
 * <h2>Flyway Compatibility</h2>
 * Flyway 10.x discovers DB-specific {@code DatabaseType} implementations
 * via {@code java.util.ServiceLoader}. The
 * {@code org.flywaydb:flyway-database-informix:${flyway.version}}
 * community plugin registers the handler for
 * {@code jdbc:informix-sqli:} URLs. Without it, Flyway raises
 * "No database found to handle &lt;url&gt;" at migrate time.
 *
 * <p><b>Note</b>: {@link DatabaseInitializer} is CUBRID-specific,
 * {@link OracleDatabaseInitializer} is Oracle-specific,
 * {@link MysqlDatabaseInitializer} is MySQL-specific,
 * {@link MariadbDatabaseInitializer} is MariaDB-specific,
 * {@link MssqlDatabaseInitializer} is MSSQL-specific. Use this class
 * for Informix initialization.
 */
public final class InformixDatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(InformixDatabaseInitializer.class);

    private static final String INFORMIX_DRIVER = "com.informix.jdbc.IfxDriver";
    private static final String SCENARIO_BASE   = "classpath:db/";

    private final InformixContainer container;

    private InformixDatabaseInitializer(InformixContainer container) {
        this.container = container;
    }

    /**
     * Creates an {@code InformixDatabaseInitializer} instance.
     *
     * @param container already-started {@code InformixContainer}
     */
    public static InformixDatabaseInitializer of(InformixContainer container) {
        if (container == null) throw new IllegalArgumentException("container must not be null");
        return new InformixDatabaseInitializer(container);
    }

    /**
     * Convenience: run a scenario as the container 's main user
     * ({@code main_user}) against {@code e2e_db}. All objects created
     * land under owner = main_user (Informix has no separate schema).
     */
    public InformixDatabaseInitializer migrateMain(String scenarioName) {
        return migrateAs(container.getDatabaseName(),
                         container.getMainSchema(),
                         container.getMainUser(),
                         container.getMainPassword(),
                         scenarioName);
    }

    /**
     * Connects to the given database with the given user and applies
     * scenario {@code V*.sql} files in version order.
     *
     * @param database     Informix database name (becomes the JDBC URL path)
     * @param schema       Informix object owner (Flyway defaultSchema; for
     *                     Informix this is the connecting user 's name)
     * @param user         login user
     * @param password     login user 's password
     * @param scenarioName relative path under {@code src/test/resources/db/}
     * @return {@code this} for chaining
     * @throws DatabaseInitializationException if script execution fails
     */
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
