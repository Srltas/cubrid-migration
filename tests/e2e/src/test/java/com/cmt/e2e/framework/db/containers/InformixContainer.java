package com.cmt.e2e.framework.db.containers;

import java.time.Duration;

import com.cmt.e2e.framework.db.driver.Drivers.DB;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * IBM Informix 14.10 Developer Edition Testcontainer (image
 * {@code icr.io/informix/informix-developer-database:14.10.FC7W1DE}).
 *
 * <p>JDBC URL: {@code jdbc:informix-sqli://host:port/dbname:INFORMIXSERVER=informix}
 * — the {@code INFORMIXSERVER=informix} suffix is hardcoded by CMT 's
 * {@code InformixDatabase.makeUrl}, so the env var below must match.
 *
 * <p>Image is amd64-only; on Apple Silicon it runs under emulation
 * (~5 minute startup), hence the 8-minute startup timeout.
 */
public class InformixContainer implements DatabaseContainer {

    private static final DockerImageName IMAGE =
        DockerImageName.parse("icr.io/informix/informix-developer-database:14.10.FC7W1DE");

    private static final int    INFORMIX_PORT   = 9088;
    private static final String DBA_USER        = "informix";
    private static final String DBA_PASSWORD    = "in4mix";
    private static final String INFORMIX_SERVER = "informix";
    private static final String DATABASE_NAME   = "e2e_db";

    private static final String MAIN_USER     = "main_user";
    private static final String MAIN_PASSWORD = "CmtMain#2026";
    private static final String MAIN_SCHEMA   = "main_user";
    private static final String REF_USER      = "ref_user";
    private static final String REF_PASSWORD  = "CmtRef#2026";
    private static final String REF_SCHEMA    = "ref_user";

    private static final String INIT_CLASSPATH      = "db/informix/init/00_prepare_database.sql";
    private static final String INIT_CONTAINER_PATH = "/tmp/init.sql";

    private final GenericContainer<?> container;
    private final boolean runInit;

    private InformixContainer(boolean withInit) {
        this.runInit = withInit;
        GenericContainer<?> c = new GenericContainer<>(IMAGE)
            .withExposedPorts(INFORMIX_PORT)
            .withEnv("LICENSE", "accept")
            .withEnv("INFORMIXSERVER", INFORMIX_SERVER)
            .withEnv("DB_LOCALE",     "en_us.utf8")
            .withEnv("CLIENT_LOCALE", "en_us.utf8")
            .withPrivilegedMode(true)
            .waitingFor(Wait.forLogMessage(".*Maximum server connections.*", 1))
            .withStartupTimeout(Duration.ofMinutes(8));

        if (withInit) {
            c.withCopyFileToContainer(
                MountableFile.forClasspathResource(INIT_CLASSPATH),
                INIT_CONTAINER_PATH
            );
        }

        this.container = c;
    }

    public static InformixContainer withEmptyDb()  { return new InformixContainer(false); }
    public static InformixContainer withMainUser() { return new InformixContainer(true); }

    @Override
    public void start() {
        container.start();
        if (runInit) {
            try {
                createOsUser(MAIN_USER, MAIN_PASSWORD);
                createOsUser(REF_USER,  REF_PASSWORD);
                runInitSql();
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize Informix container", e);
            }
        }
    }

    /**
     * The Developer Edition image runs as the {@code informix} user (UID 1001),
     * not root, so {@code useradd} is invoked via {@code sudo}. The image
     * grants informix passwordless sudo (per docker hub README).
     */
    private void createOsUser(String name, String password) throws Exception {
        Container.ExecResult addRes = container.execInContainer(
            "sudo", "useradd", "-m", "-s", "/bin/false", name);
        if (addRes.getExitCode() != 0 && !addRes.getStderr().contains("already exists")) {
            throw new IllegalStateException(
                "useradd " + name + " failed (exit " + addRes.getExitCode() + ")\n"
                    + "stderr:\n" + addRes.getStderr());
        }
        Container.ExecResult pwdRes = container.execInContainer(
            "sudo", "bash", "-c",
            "echo '" + name + ":" + password + "' | chpasswd");
        if (pwdRes.getExitCode() != 0) {
            throw new IllegalStateException(
                "chpasswd " + name + " failed (exit " + pwdRes.getExitCode() + ")\n"
                    + "stderr:\n" + pwdRes.getStderr());
        }
    }

    private void runInitSql() throws Exception {
        Container.ExecResult result = container.execInContainer(
            "bash", "-lc",
            "source $INFORMIXDIR/bin/setenv.sh 2>/dev/null; "
                + "dbaccess - " + INIT_CONTAINER_PATH);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException(
                "Informix init script failed (exit " + result.getExitCode() + ")\n"
                    + "stdout:\n" + result.getStdout() + "\n"
                    + "stderr:\n" + result.getStderr());
        }
    }

    @Override public void stop()  { container.stop(); }
    @Override public String  getHost()         { return container.getHost(); }
    @Override public Integer getDatabasePort() { return container.getMappedPort(INFORMIX_PORT); }
    @Override public DB      getDbType()       { return DB.INFORMIX; }

    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format(
            "jdbc:informix-sqli://%s:%d/%s:INFORMIXSERVER=%s",
            getHost(), getDatabasePort(), dbName, INFORMIX_SERVER);
    }

    public String getDatabaseName()   { return DATABASE_NAME; }
    public String getInformixServer() { return INFORMIX_SERVER; }
    public String getMainUser()       { return MAIN_USER; }
    public String getMainPassword()   { return MAIN_PASSWORD; }
    public String getMainSchema()     { return MAIN_SCHEMA; }
    public String getRefUser()        { return REF_USER; }
    public String getRefPassword()    { return REF_PASSWORD; }
    public String getRefSchema()      { return REF_SCHEMA; }
    public String getDbaUser()        { return DBA_USER; }
    public String getDbaPassword()    { return DBA_PASSWORD; }
}
