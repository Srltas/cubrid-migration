package com.cmt.e2e.framework.db.init;

import com.cmt.e2e.framework.db.containers.MsSqlContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flyway-based helper for initializing MSSQL test databases.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * MsSqlContainer source = MsSqlContainer.withMainUser();
 * source.start();
 * MssqlDatabaseInitializer.of(source)
 *     .migrateRef("mssql/ref_schema")     // runs as ref_user, default schema=ref_schema
 *     .migrateMain("mssql/main_schema");  // runs as main_user, default schema=main_schema
 * }</pre>
 *
 * <h2>Scenario Directory Layout</h2>
 * <pre>
 * src/test/resources/db/
 * └── mssql/
 *     ├── init/
 *     │   └── 00_prepare_database.sql   <- runs via sqlcmd inside the container
 *     │                                    (mounted by MsSqlContainer.withMainUser)
 *     ├── ref_schema/                   <- runs as ref_user @ e2e_db (default schema=ref_schema)
 *     │   ├── V1__schema_ref_objects.sql
 *     │   └── V2__data_ref_objects.sql
 *     └── main_schema/                  <- runs as main_user @ e2e_db (default schema=main_schema)
 *         ├── V1__schema_business_tables.sql
 *         ├── V2__schema_views.sql
 *         ├── V3__schema_type_test_tables.sql
 *         ├── V4__schema_extensions.sql
 *         ├── V5__synonyms.sql
 *         └── V99__data.sql
 * </pre>
 *
 * <p>Multi-schema is the {@link MsSqlContainer.withMainUser()} default. If
 * the first run shows CMT MSSQL fetcher cannot extract objects from both
 * schemas, the seed collapses to single-schema (main_schema only) and
 * ref_schema becomes anti-coverage — see SEED_SPEC §1 / §2.
 *
 * <h2>MSSQL Schema vs Database</h2>
 * MSSQL separates database (catalog) and schema (namespace within a
 * database). One Flyway invocation connects to a single database via the
 * JDBC URL and Flyway 's {@code defaultSchema} pins the unqualified DDL
 * to a specific schema. We use the same {@code e2e_db} database for
 * both roles and switch only the user + default schema.
 *
 * <p><b>Note</b>: {@link DatabaseInitializer} is CUBRID-specific,
 * {@link OracleDatabaseInitializer} is Oracle-specific,
 * {@link MysqlDatabaseInitializer} is MySQL-specific,
 * {@link MariadbDatabaseInitializer} is MariaDB-specific. Use this class
 * for MSSQL initialization.
 */
public final class MssqlDatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(MssqlDatabaseInitializer.class);

    private static final String MSSQL_DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    private static final String SCENARIO_BASE = "classpath:db/";

    private final MsSqlContainer container;

    private MssqlDatabaseInitializer(MsSqlContainer container) {
        this.container = container;
    }

    /**
     * Creates an {@code MssqlDatabaseInitializer} instance.
     *
     * @param container already-started {@code MsSqlContainer}
     */
    public static MssqlDatabaseInitializer of(MsSqlContainer container) {
        if (container == null) throw new IllegalArgumentException("container must not be null");
        return new MssqlDatabaseInitializer(container);
    }

    /**
     * Convenience: run a scenario as the container 's main user (
     * {@code main_user}) against {@code main_schema} in the e2e database.
     */
    public MssqlDatabaseInitializer migrateMain(String scenarioName) {
        return migrateAs(container.getDatabaseName(),
                         container.getMainSchema(),
                         container.getMainUser(),
                         container.getMainPassword(),
                         scenarioName);
    }

    /**
     * Convenience: run a scenario as the container 's ref user (
     * {@code ref_user}) against {@code ref_schema} in the e2e database.
     * Use this in multi-schema mode before {@link #migrateMain} so cross-schema
     * synonyms have their target object available.
     */
    public MssqlDatabaseInitializer migrateRef(String scenarioName) {
        return migrateAs(container.getDatabaseName(),
                         container.getRefSchema(),
                         container.getRefUser(),
                         container.getRefPassword(),
                         scenarioName);
    }

    /**
     * Connects to the given database with the given user and applies
     * scenario {@code V*.sql} files in version order, with Flyway 's
     * {@code defaultSchema} set to the given schema.
     *
     * @param database     SQL Server database name (becomes the JDBC databaseName)
     * @param schema       SQL Server schema (Flyway defaultSchema; default for unqualified DDL)
     * @param user         login user
     * @param password     login user 's password
     * @param scenarioName relative path under {@code src/test/resources/db/}
     * @return {@code this} for chaining
     * @throws DatabaseInitializationException if script execution fails
     */
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

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    private Flyway buildFlyway(String location, String database, String schema, String user, String password) {
        // jdbc:sqlserver://...;databaseName=<db>;encrypt=false;trustServerCertificate=true
        String jdbcUrl = container.getJdbcUrl(database, user);

        return Flyway.configure()
            .dataSource(jdbcUrl, user, password)
            .driver(MSSQL_DRIVER)
            .defaultSchema(schema)          // MSSQL: schema is independent of database
            .schemas(schema)
            .locations(location)
            .cleanDisabled(true)
            .baselineOnMigrate(false)
            .validateOnMigrate(true)
            .load();
    }
}
