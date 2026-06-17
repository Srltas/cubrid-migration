package com.cmt.e2e.framework.db.containers;

import java.time.Duration;
import java.util.Set;

import com.cmt.e2e.framework.db.driver.Drivers.DB;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Oracle 11g XE Testcontainer.
 *
 * <h2>Image</h2>
 * {@code gvenzl/oracle-xe:11.2.0.2-slim-faststart}
 * - the faststart variant with pre-initialized data for shorter startup (~90s)
 *
 * <h2>Connection Info (single-user mode)</h2>
 * <pre>
 *   SID       : XE  (fixed in 11g XE, SERVICE_NAME is not available)
 *   Port      : 1521
 *   DBA       : system / oracle
 *   Main user : MAIN_SCHEMA / cmt  (Flyway seeds objects under this user)
 * </pre>
 *
 * <h2>Connection Info (two-user mode)</h2>
 * <pre>
 *   Ref user  : REF_SCHEMA  / cmt  (owns cross-schema reference objects and grants access to MAIN_SCHEMA)
 *   Main user : MAIN_SCHEMA / cmt  (references REF_SCHEMA objects through synonyms)
 * </pre>
 *
 * <h2>JDBC URL Format</h2>
 * {@code jdbc:oracle:thin:@host:port:XE} - 11g uses SID format
 * rather than the 12c+ SERVICE_NAME format {@code /XE}
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Single user (basic scenario)
 * OracleContainer oracle = OracleContainer.withEmptyDb();
 *
 * // Two users (cross-schema grant/synonym coverage)
 * OracleContainer oracle = OracleContainer.withTwoUsers();
 * }</pre>
 */
public class OracleContainer implements DatabaseContainer {

    private static final DockerImageName IMAGE =
        DockerImageName.parse("gvenzl/oracle-xe:11.2.0.2-slim-faststart");

    private static final int    ORACLE_PORT  = 1521;
    private static final String SID          = "XE";
    private static final String DBA_USER     = "system";
    private static final String DBA_PASSWORD = "oracle";

    /**
     * Main migration target user.
     * Schema role name per
     * {@code tests/e2e/docs/seed/COMMON_SEED_CONTRACT.md} §2.1.
     */
    private static final String MAIN_USER     = "MAIN_SCHEMA";
    private static final String MAIN_PASSWORD = "cmt";

    /**
     * Cross-schema reference user (two-user mode only).
     * Schema role name per
     * {@code tests/e2e/docs/seed/COMMON_SEED_CONTRACT.md} §2.1.
     */
    private static final String REF_USER     = "REF_SCHEMA";
    private static final String REF_PASSWORD = "cmt";

    /**
     * Init-db extension point provided by the gvenzl image.
     * Any {@code *.sql} mounted here runs as SYSDBA during container startup.
     * The {@code 00_} prefix sequences this script alphabetically before any
     * subsequent gvenzl init scripts; for our purposes the gvenzl image only
     * runs its own internal init regardless of file ordering, so the prefix
     * is essentially decorative but follows the SEED_DATA_GUIDE convention.
     */
    private static final String REF_INIT_CLASSPATH =
        "db/oracle/init/00_prepare_database.sql";
    private static final String REF_INIT_CONTAINER_PATH =
        "/container-entrypoint-initdb.d/00_prepare_database.sql";

    private final GenericContainer<?> container;

    private OracleContainer(boolean twoUsers) {
        GenericContainer<?> c = new GenericContainer<>(IMAGE)
            .withExposedPorts(ORACLE_PORT)
            .withEnv("ORACLE_PASSWORD",   DBA_PASSWORD)   // system/SYSTEM password
            .withEnv("APP_USER",          MAIN_USER)      // auto-created main user
            .withEnv("APP_USER_PASSWORD", MAIN_PASSWORD)  // main user password
            .waitingFor(
                Wait.forLogMessage(".*DATABASE IS READY TO USE!.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(5))
            );

        if (twoUsers) {
            // Create the REF_SCHEMA user at container startup.
            // See db/oracle/init/00_prepare_database.sql for details.
            c.withCopyFileToContainer(
                MountableFile.forClasspathResource(REF_INIT_CLASSPATH),
                REF_INIT_CONTAINER_PATH
            );
        }

        this.container = c;
    }

    /**
     * Creates an empty Oracle 11g XE instance.
     * {@code MAIN_SCHEMA} is created automatically when the container starts.
     * Intended for single-user scenarios.
     */
    public static OracleContainer withEmptyDb() {
        return new OracleContainer(false);
    }

    /**
     * Creates an Oracle 11g XE instance with both {@code MAIN_SCHEMA}
     * and {@code REF_SCHEMA}.
     *
     * <h2>User Roles</h2>
     * <ul>
     *   <li>{@code REF_SCHEMA}  - owns cross-schema reference objects, grants
     *       SELECT/INSERT/UPDATE/DELETE on those objects to {@code MAIN_SCHEMA}.
     *       Initialized by Flyway scripts under {@code db/oracle/ref_schema/}.</li>
     *   <li>{@code MAIN_SCHEMA} - main migration target, owns business tables,
     *       type test tables, view, routines, and a synonym pointing at
     *       {@code REF_SCHEMA.e2e_ref_audit}. Initialized by Flyway scripts
     *       under {@code db/oracle/main_schema/}.</li>
     * </ul>
     *
     * <h2>Initialization Order</h2>
     * <pre>
     * 1. Container startup: create MAIN_SCHEMA (gvenzl APP_USER) and REF_SCHEMA
     *    (00_prepare_database.sql)
     * 2. OracleDatabaseInitializer.migrateAs(getRefUser(),  ..., "oracle/ref_schema")
     * 3. OracleDatabaseInitializer.migrateAs(getMainUser(), ..., "oracle/main_schema")
     * </pre>
     *
     * <p>Used for cross-schema grant and synonym coverage scenarios.
     */
    public static OracleContainer withTwoUsers() {
        return new OracleContainer(true);
    }

    @Override
    public void start() {
        // Oracle 11g XE (11.2.0.2) uses an old timezone file (v4) that does not
        // recognize newer region IDs such as Asia/Seoul. If ojdbc8(21.x) sends
        // the JVM timezone as a region name during authentication, ORA-01882 occurs.
        // Setting this to false makes the driver send a UTC offset (+09:00),
        // which works regardless of timezone-file version.
        // This is for the test JVM only. The child CMT Console process is handled
        // separately through JAVA_TOOL_OPTIONS in docker-compose.yml.
        System.setProperty("oracle.jdbc.timezoneAsRegion", "false");
        container.start();
    }

    @Override
    public void stop() {
        container.stop();
    }

    @Override
    public String getHost() {
        return container.getHost();
    }

    @Override
    public Integer getDatabasePort() {
        return container.getMappedPort(ORACLE_PORT);
    }

    @Override
    public DB getDbType() {
        return DB.ORACLE;
    }

    @Override
    public GenericContainer<?> getContainer() {
        return container;
    }

    /**
     * Returns a JDBC URL in Oracle 11g XE SID format.
     * The {@code dbName} and {@code user} parameters are ignored because
     * 11g XE always exposes the same SID: {@code XE}.
     */
    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format("jdbc:oracle:thin:@%s:%d:%s",
            getHost(), getDatabasePort(), SID);
    }

    /** Main migration user used by Flyway seeding and tests. */
    public String getMainUser() {
        return MAIN_USER;
    }

    /** Password for {@link #getMainUser()}. */
    public String getMainPassword() {
        return MAIN_PASSWORD;
    }

    /**
     * Reference-schema user that owns cross-schema reference objects.
     * Only valid for containers created with {@link #withTwoUsers()}.
     */
    public String getRefUser() {
        return REF_USER;
    }

    /**
     * Password for {@link #getRefUser()}.
     * Only valid for containers created with {@link #withTwoUsers()}.
     */
    public String getRefPassword() {
        return REF_PASSWORD;
    }

    /** DBA user ({@code system}) */
    public String getDbaUser() {
        return DBA_USER;
    }

    /** DBA password */
    public String getDbaPassword() {
        return DBA_PASSWORD;
    }

    /** Oracle SID (fixed to XE in Oracle 11g XE) */
    public String getSid() {
        return SID;
    }

    @Override
    public Set<Startable> getDependencies() {
        return container.getDependencies();
    }
}
