package com.cmt.e2e.framework.db.containers;

import java.time.Duration;

import com.cmt.e2e.framework.db.Drivers.DB;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Oracle 11g XE Testcontainer (image {@code gvenzl/oracle-xe:11.2.0.2-slim-faststart}).
 *
 * <ul>
 *   <li>JDBC URL: {@code jdbc:oracle:thin:@host:port:XE} — 11g uses SID format</li>
 *   <li>{@link #withEmptyDb()} — single-user (MAIN_SCHEMA only)</li>
 *   <li>{@link #withTwoUsers()} — adds REF_SCHEMA for cross-schema grant/synonym</li>
 * </ul>
 */
public class OracleContainer implements DatabaseContainer {

    private static final DockerImageName IMAGE =
        DockerImageName.parse("gvenzl/oracle-xe:11.2.0.2-slim-faststart");

    private static final int    ORACLE_PORT  = 1521;
    private static final String SID          = "XE";
    private static final String DBA_USER     = "system";
    private static final String DBA_PASSWORD = "oracle";
    private static final String MAIN_USER     = "MAIN_SCHEMA";
    private static final String MAIN_PASSWORD = "cmt";
    private static final String REF_USER     = "REF_SCHEMA";
    private static final String REF_PASSWORD = "cmt";

    private static final String REF_INIT_CLASSPATH =
        "db/oracle/init/00_prepare_database.sql";
    private static final String REF_INIT_CONTAINER_PATH =
        "/container-entrypoint-initdb.d/00_prepare_database.sql";

    private final GenericContainer<?> container;

    private OracleContainer(boolean twoUsers) {
        GenericContainer<?> c = new GenericContainer<>(IMAGE)
            .withExposedPorts(ORACLE_PORT)
            .withEnv("ORACLE_PASSWORD",   DBA_PASSWORD)
            .withEnv("APP_USER",          MAIN_USER)
            .withEnv("APP_USER_PASSWORD", MAIN_PASSWORD)
            .waitingFor(
                Wait.forLogMessage(".*DATABASE IS READY TO USE!.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(5))
            );

        if (twoUsers) {
            c.withCopyFileToContainer(
                MountableFile.forClasspathResource(REF_INIT_CLASSPATH),
                REF_INIT_CONTAINER_PATH
            );
        }

        this.container = c;
    }

    public static OracleContainer withEmptyDb() {
        return new OracleContainer(false);
    }

    public static OracleContainer withTwoUsers() {
        return new OracleContainer(true);
    }

    @Override
    public void start() {
        // Oracle 11g XE 's timezone file (v4) does not recognize newer region IDs.
        // ojdbc 21.x sends the JVM timezone as a region name during authentication
        // unless this property forces a numeric UTC offset, which causes
        // ORA-01882. The setting only affects the test JVM; the CMT Console
        // child process is handled separately via JAVA_TOOL_OPTIONS in
        // docker-compose.yml and the GHA workflow.
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
    public String getJdbcUrl(String dbName, String user) {
        // 11g XE always exposes SID=XE; dbName/user args ignored.
        return String.format("jdbc:oracle:thin:@%s:%d:%s",
            getHost(), getDatabasePort(), SID);
    }

    public String getMainUser()     { return MAIN_USER; }
    public String getMainPassword() { return MAIN_PASSWORD; }
    public String getRefUser()      { return REF_USER; }
    public String getRefPassword()  { return REF_PASSWORD; }
    public String getDbaUser()      { return DBA_USER; }
    public String getDbaPassword()  { return DBA_PASSWORD; }
    public String getSid()          { return SID; }
}
