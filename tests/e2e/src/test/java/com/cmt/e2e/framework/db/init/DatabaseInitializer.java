package com.cmt.e2e.framework.db.init;

import com.cmt.e2e.framework.db.containers.CubridContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flyway-based helper for initializing CUBRID test databases.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Apply scenario scripts after starting the container
 * CubridContainer source = CubridContainer.withEmptyDb();
 * source.start();
 * DatabaseInitializer.of(source, "cubdb", "public")
 *     .migrate("cubrid/my_scenario");
 * }</pre>
 *
 * <h2>Scenario Directory Layout</h2>
 * <pre>
 * src/test/resources/db/
 * └── cubrid/
 *     └── my_scenario/
 *         ├── V1__schema.sql
 *         └── V2__seed.sql
 * </pre>
 *
 * <h2>Key Methods</h2>
 * <ul>
 *   <li>{@link #migrate(String)} - applies {@code V*.sql} files from a scenario folder in order</li>
 *   <li>{@link #clean()} - removes all objects from the current DB when reusing containers</li>
 *   <li>{@link #reset(String)} - runs {@code clean()} and then {@code migrate()} to reapply from scratch</li>
 * </ul>
 *
 * <p><b>Note</b>: if every test starts a fresh container, {@code clean()} is unnecessary.
 * Use {@code reset()} only when reusing a container and needing isolation.
 */
public final class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private static final String CUBRID_DRIVER = "cubrid.jdbc.driver.CUBRIDDriver";
    private static final String SCENARIO_BASE  = "classpath:db/";

    private final CubridContainer container;
    private final String dbName;
    private final String userName;
    private final String password;

    private DatabaseInitializer(CubridContainer container, String dbName, String userName, String password) {
        this.container = container;
        this.dbName    = dbName;
        this.userName  = userName;
        this.password  = password;
    }

    /**
     * Creates a {@code DatabaseInitializer} for a user that has no password
     * (the default for {@code dba} / {@code public} on a fresh CUBRID).
     *
     * @param container already-started {@code CubridContainer}
     * @param dbName database name to connect to (for example {@code cubdb})
     * @param userName login user and Flyway default schema (for example {@code public})
     */
    public static DatabaseInitializer of(CubridContainer container,
                                         String dbName,
                                         String userName) {
        return of(container, dbName, userName, "");
    }

    /**
     * Creates a {@code DatabaseInitializer} for a user that has a password.
     *
     * <p>Useful when bootstrapping a multi-user CUBRID seed: each schema role
     * (for example {@code MAIN_SCHEMA}, {@code REF_SCHEMA}) is created via
     * {@link ClasspathSqlRunner} as {@code dba} and then Flyway runs as the
     * new user.
     */
    public static DatabaseInitializer of(CubridContainer container,
                                         String dbName,
                                         String userName,
                                         String password) {
        if (container == null) throw new IllegalArgumentException("container must not be null");
        if (dbName == null || dbName.isBlank()) throw new IllegalArgumentException("dbName must not be blank");
        if (userName == null || userName.isBlank()) throw new IllegalArgumentException("userName must not be blank");
        if (password == null) throw new IllegalArgumentException("password must not be null (use \"\" for none)");
        return new DatabaseInitializer(container, dbName, userName, password);
    }

    /**
     * Applies {@code V*.sql} files from the given scenario folder to the database.
     *
     * <p>{@code flyway_schema_history} is created automatically.
     * Already-applied scripts are skipped.
     *
     * @param scenarioName relative path under {@code src/test/resources/db/}
     * @throws DatabaseInitializationException if script execution fails
     */
    public void migrate(String scenarioName) {
        if (scenarioName == null || scenarioName.isBlank()) {
            throw new IllegalArgumentException("scenarioName must not be blank");
        }

        // If the scenario folder is missing from the classpath, Flyway exits
        // successfully with executed=0, so validate it up front for a fast failure.
        String resourcePath = "db/" + scenarioName;
        if (Thread.currentThread().getContextClassLoader().getResource(resourcePath) == null) {
            throw new DatabaseInitializationException(
                "Scenario not found on classpath: '" + resourcePath + "'. " +
                "Check src/test/resources/" + resourcePath + " exists.", null);
        }

        String location = SCENARIO_BASE + scenarioName;
        log.info("[DatabaseInitializer] migrate start: scenario='{}', db='{}', user='{}'",
            scenarioName, dbName, userName);

        try {
            MigrateResult result = buildFlyway(location).migrate();
            log.info("[DatabaseInitializer] migrate complete: executed={}, success={}",
                result.migrationsExecuted, result.success);

            if (!result.success) {
                throw new DatabaseInitializationException(
                    "Flyway migration reported failure for scenario: " + scenarioName, null);
            }
        } catch (FlywayException e) {
            throw new DatabaseInitializationException(
                "Failed to migrate scenario '" + scenarioName + "': " + e.getMessage(), e);
        }
    }

    private Flyway buildFlyway(String location) {
        String jdbcUrl = container.getJdbcUrl(dbName, userName);
        return Flyway.configure()
            .dataSource(jdbcUrl, userName, password)
            .driver(CUBRID_DRIVER)
            .defaultSchema(userName)        // CUBRID: schema == user name
            .locations(location)
            .cleanDisabled(true)
            // Fresh CUBRID users can already look "non-empty" to Flyway because
            // the cross-schema GRANT applied during ref_schema bootstrap leaves
            // catalog entries owned by the grantee. Baseline at version 0 so
            // every V1+ migration in this scenario still runs while Flyway
            // doesn't refuse with "non-empty schema, no history table".
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .validateOnMigrate(true)
            .load();
    }
}
