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
 *   SID      : XE  (fixed in 11g XE, SERVICE_NAME is not available)
 *   Port     : 1521
 *   DBA      : system / oracle
 *   App user : CMT_TEST / cmt  (Flyway seeds objects under this user)
 * </pre>
 *
 * <h2>Connection Info (two-user mode)</h2>
 * <pre>
 *   Owner user : CMT_OWNER / cmt  (owns shared tables and grants access to CMT_TEST)
 *   App user   : CMT_TEST / cmt   (references CMT_OWNER objects through synonyms)
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
 * // Two users (full_coverage scenario with cross-schema grant/synonym coverage)
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
    private static final String APP_USER     = "CMT_TEST";
    private static final String APP_PASSWORD = "cmt";

    /** Two-user mode: owner schema for shared tables */
    private static final String OWNER_USER     = "CMT_OWNER";
    private static final String OWNER_PASSWORD = "cmt";

    /**
     * Init-db extension point provided by the gvenzl image.
     * Any {@code *.sql} mounted here runs as SYSDBA during container startup.
     * The {@code 99_} prefix ensures it runs after the internal gvenzl scripts.
     */
    private static final String OWNER_INIT_CLASSPATH =
        "db/oracle/init/99_create_owner.sql";
    private static final String OWNER_INIT_CONTAINER_PATH =
        "/container-entrypoint-initdb.d/99_create_owner.sql";

    private final GenericContainer<?> container;

    private OracleContainer(boolean twoUsers) {
        GenericContainer<?> c = new GenericContainer<>(IMAGE)
            .withExposedPorts(ORACLE_PORT)
            .withEnv("ORACLE_PASSWORD",   DBA_PASSWORD)  // system/SYSTEM password
            .withEnv("APP_USER",          APP_USER)       // auto-created app user
            .withEnv("APP_USER_PASSWORD", APP_PASSWORD)   // app user password
            .waitingFor(
                Wait.forLogMessage(".*DATABASE IS READY TO USE!.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(5))
            );

        if (twoUsers) {
            // Create the CMT_OWNER user at container startup.
            // See db/oracle/init/99_create_owner.sql for details.
            c.withCopyFileToContainer(
                MountableFile.forClasspathResource(OWNER_INIT_CLASSPATH),
                OWNER_INIT_CONTAINER_PATH
            );
        }

        this.container = c;
    }

    /**
     * Creates an empty Oracle 11g XE instance.
     * {@code CMT_TEST} is created automatically when the container starts.
     * Intended for single-user scenarios such as {@code oracle/basic}.
     */
    public static OracleContainer withEmptyDb() {
        return new OracleContainer(false);
    }

    /**
     * Creates an Oracle 11g XE instance with both {@code CMT_TEST}
     * and {@code CMT_OWNER}.
     *
     * <h2>User Roles</h2>
     * <ul>
     *   <li>{@code CMT_OWNER} - owner of shared tables, initialized by Flyway
     *       {@code owner/} scripts and grants object privileges to {@code CMT_TEST}</li>
     *   <li>{@code CMT_TEST} - migration target user, initialized by Flyway
     *       {@code test/} scripts and owns private synonyms pointing to {@code CMT_OWNER} objects</li>
     * </ul>
     *
     * <h2>Initialization Order</h2>
     * <pre>
     * 1. Container startup: create CMT_TEST (gvenzl APP_USER) and CMT_OWNER (99_create_owner.sql)
     * 2. OracleDatabaseInitializer.migrateAs(getOwnerUser(), ..., "oracle/full_coverage/owner")
     * 3. OracleDatabaseInitializer.migrateAs(getAppUser(),   ..., "oracle/full_coverage/test")
     * </pre>
     *
     * <p>Used for the {@code full_coverage} scenario, including cross-schema
     * grant and synonym coverage.
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

    /** Application user used by Flyway seeding and tests */
    public String getAppUser() {
        return APP_USER;
    }

    /** Application user password */
    public String getAppPassword() {
        return APP_PASSWORD;
    }

    /**
     * Schema user that owns shared tables in two-user mode.
     * Only valid for containers created with {@link #withTwoUsers()}.
     */
    public String getOwnerUser() {
        return OWNER_USER;
    }

    /**
     * Password for {@link #getOwnerUser()}.
     * Only valid for containers created with {@link #withTwoUsers()}.
     */
    public String getOwnerPassword() {
        return OWNER_PASSWORD;
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
