package com.cmt.e2e.framework.db.init;

import com.cmt.e2e.framework.db.containers.OracleContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flyway-based helper for initializing Oracle test databases.
 *
 * <h2>Single-User Usage</h2>
 * <pre>{@code
 * OracleContainer source = OracleContainer.withEmptyDb();
 * source.start();
 * OracleDatabaseInitializer.of(source)
 *     .migrate("oracle/basic");
 * }</pre>
 *
 * <h2>Two-User Usage (cross-schema grant/synonym)</h2>
 * <pre>{@code
 * OracleContainer source = OracleContainer.withTwoUsers();
 * source.start();
 * OracleDatabaseInitializer.of(source)
 *     .migrateAs(source.getOwnerUser(), source.getOwnerPassword(),
 *                "oracle/full_coverage/owner")   // create tables + grant to CMT_TEST
 *     .migrateAs(source.getAppUser(),   source.getAppPassword(),
 *                "oracle/full_coverage/test");   // create own objects + synonyms
 * }</pre>
 *
 * <h2>Scenario Directory Layout</h2>
 * <pre>
 * src/test/resources/db/
 * └── oracle/
 *     ├── basic/
 *     │   ├── V1__schema.sql
 *     │   └── V2__data.sql
 *     └── full_coverage/
 *         ├── owner/   <- executed as CMT_OWNER via migrateAs
 *         └── test/    <- executed as CMT_TEST via migrateAs
 * </pre>
 *
 * <h2>Oracle Schema Isolation</h2>
 * In Oracle, a user and a schema are the same concept.
 * Flyway connections under different users manage independent
 * {@code flyway_schema_history} tables in each schema without extra configuration.
 *
 * <p><b>Note</b>: {@link DatabaseInitializer} is CUBRID-specific.
 * Use this class for Oracle initialization.
 */
public final class OracleDatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(OracleDatabaseInitializer.class);

    private static final String ORACLE_DRIVER  = "oracle.jdbc.OracleDriver";
    private static final String SCENARIO_BASE  = "classpath:db/";

    private final OracleContainer container;

    private OracleDatabaseInitializer(OracleContainer container) {
        this.container = container;
    }

    /**
     * Creates an {@code OracleDatabaseInitializer} instance.
     *
     * @param container already-started {@code OracleContainer}
     */
    public static OracleDatabaseInitializer of(OracleContainer container) {
        if (container == null) throw new IllegalArgumentException("container must not be null");
        return new OracleDatabaseInitializer(container);
    }

    /**
     * Applies the given scenario using the container app user ({@code CMT_TEST}).
     *
     * <p>Used for single-user scenarios. It supports chaining but is also fine
     * as a standalone call.
     *
     * @param scenarioName relative path under {@code src/test/resources/db/}
     * @return {@code this} for chaining
     * @throws DatabaseInitializationException if script execution fails
     */
    public OracleDatabaseInitializer migrate(String scenarioName) {
        return migrateAs(container.getAppUser(), container.getAppPassword(), scenarioName);
    }

    /**
     * Connects as the specified user and applies scenario {@code V*.sql} files in version order.
     *
     * <p>Use this in two-user mode ({@code OracleContainer.withTwoUsers()}).
     * Call it in {@code CMT_OWNER -> CMT_TEST} order because
     * {@code CMT_TEST} synonyms reference {@code CMT_OWNER} objects.
     *
     * <pre>{@code
     * OracleDatabaseInitializer.of(sourceDb)
     *     .migrateAs(sourceDb.getOwnerUser(), sourceDb.getOwnerPassword(),
     *                "oracle/full_coverage/owner")
     *     .migrateAs(sourceDb.getAppUser(),   sourceDb.getAppPassword(),
     *                "oracle/full_coverage/test");
     * }</pre>
     *
     * @param user Oracle user name (Oracle schema == user name)
     * @param password user password
     * @param scenarioName relative path under {@code src/test/resources/db/}
     * @return {@code this} for chaining
     * @throws DatabaseInitializationException if script execution fails
     */
    public OracleDatabaseInitializer migrateAs(String user, String password, String scenarioName) {
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
        log.info("[OracleDatabaseInitializer] migrate start: scenario='{}', user='{}'",
            scenarioName, user);

        try {
            MigrateResult result = buildFlyway(location, user, password).migrate();
            log.info("[OracleDatabaseInitializer] migrate complete: scenario='{}', user='{}', executed={}, success={}",
                scenarioName, user, result.migrationsExecuted, result.success);

            if (!result.success) {
                throw new DatabaseInitializationException(
                    "Flyway migration reported failure for scenario '" + scenarioName +
                    "' as user '" + user + "'", null);
            }
        } catch (FlywayException e) {
            throw new DatabaseInitializationException(
                "Failed to migrate Oracle scenario '" + scenarioName +
                "' as user '" + user + "': " + e.getMessage(), e);
        }
        return this;
    }

    /**
     * Removes all objects from the current app-user schema.
     * Useful when reusing a container and unnecessary when each test starts a new one.
     */
    public void clean() {
        log.info("[OracleDatabaseInitializer] clean start: user='{}'", container.getAppUser());
        try {
            buildFlyway(SCENARIO_BASE + "_clean_placeholder",
                        container.getAppUser(), container.getAppPassword()).clean();
            log.info("[OracleDatabaseInitializer] clean complete");
        } catch (FlywayException e) {
            throw new DatabaseInitializationException(
                "Failed to clean Oracle schema '" + container.getAppUser() + "': " + e.getMessage(), e);
        }
    }

    /** Runs {@link #clean()} and then {@link #migrate(String)} when reusing containers. */
    public void reset(String scenarioName) {
        clean();
        migrate(scenarioName);
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    /**
     * Builds a Flyway instance for the given user, password, and location.
     * Because Oracle treats schema == user, both {@code defaultSchema} and
     * {@code schemas} are set to the user name so Flyway tracks history
     * in the correct schema.
     */
    private Flyway buildFlyway(String location, String user, String password) {
        // Oracle 11g XE: jdbc:oracle:thin:@host:port:XE
        String jdbcUrl = container.getJdbcUrl(null, null);

        return Flyway.configure()
            .dataSource(jdbcUrl, user, password)
            .driver(ORACLE_DRIVER)
            // In Oracle, schema == user, so each user gets an independent
            // flyway_schema_history automatically.
            .defaultSchema(user)
            .schemas(user)
            .locations(location)
            .cleanDisabled(false)           // allow clean() in tests
            .baselineOnMigrate(false)
            .validateOnMigrate(true)
            .load();
    }
}
