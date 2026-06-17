package com.cmt.e2e.framework.db.containers;

import java.time.Duration;
import java.util.Set;

import com.cmt.e2e.framework.db.driver.Drivers.DB;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * IBM Informix 14.10 Developer Edition Testcontainer.
 *
 * <h2>Image</h2>
 * {@code icr.io/informix/informix-developer-database:14.10.FC7W1DE}
 * - Free-for-development Developer Edition (no license file, just
 *   {@code LICENSE=accept} env). Distinct from Tibero, where every
 *   container needs a hostname-bound license file from TmaxSoft.
 * - <b>Apple Silicon caveat</b>: this image is amd64-only; on ARM
 *   Macs it runs under Rosetta/QEMU emulation and startup takes
 *   ~5 minutes. The {@code withStartupTimeout(Duration.ofMinutes(8))}
 *   below covers that.
 *
 * <h2>Connection Info</h2>
 * <pre>
 *   Port      : 9088 (SQLI)
 *   Database  : e2e_db (created by init script)
 *   informix  : informix / in4mix       (default DBA / superuser)
 *   Main user : main_user / CmtMain#2026 (owner of MAIN_SCHEMA objects)
 *   Ref user  : ref_user  / CmtRef#2026  (owner of REF_SCHEMA objects)
 * </pre>
 *
 * <h2>JDBC URL Format</h2>
 * {@code jdbc:informix-sqli://host:port/dbname:INFORMIXSERVER=informix}
 * - The trailing {@code :INFORMIXSERVER=informix} is hardcoded by
 *   CMT 's {@code InformixDatabase.makeUrl} (line 85). The
 *   environment variable {@code INFORMIXSERVER} below MUST resolve
 *   to literal {@code informix} so that CMT 's URL matches the
 *   instance name.
 *
 * <h2>OS User Provisioning</h2>
 * Informix grants ({@code GRANT CONNECT}, {@code GRANT RESOURCE}) reference
 * OS-level accounts. Before running the SQL init script we
 * {@code useradd} the main_user and ref_user inside the running
 * container. This requires privileged mode.
 *
 * <h2>Init Script Strategy</h2>
 * Two steps after the server is up:
 * <ol>
 *   <li>{@code execInContainer} — useradd / passwd for main_user, ref_user</li>
 *   <li>{@code execInContainer} — dbaccess to run
 *       {@code db/informix/init/00_prepare_database.sql} (mounted to
 *       {@code /tmp/init.sql}) which creates {@code e2e_db} and grants
 *       CONNECT/RESOURCE to the new users</li>
 * </ol>
 *
 * <h2>Wait Strategy</h2>
 * The image emits {@code Maximum server connections 32} and other
 * lines as the engine warms up. We wait on the message that the
 * Tomcat-style listener prints once the SQLI port binds:
 * {@code "On-Bar*"} sequence finishes, and the Informix dynamic
 * server has booted.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * InformixContainer source = InformixContainer.withMainUser();
 * source.start();
 * }</pre>
 *
 * @see <a href="https://hub.docker.com/r/ibmcom/informix-developer-database/">Docker Hub: Informix Developer DB</a>
 * @see <a href="file:../../../../../../resources/db/informix/init/00_prepare_database.sql">db/informix/init/00_prepare_database.sql</a>
 */
public class InformixContainer implements DatabaseContainer {

    private static final DockerImageName IMAGE =
        DockerImageName.parse("icr.io/informix/informix-developer-database:14.10.FC7W1DE");

    private static final int    INFORMIX_PORT  = 9088;
    private static final String DBA_USER       = "informix";
    private static final String DBA_PASSWORD   = "in4mix";
    private static final String INFORMIX_SERVER = "informix";

    private static final String DATABASE_NAME = "e2e_db";

    /**
     * Main migration target user. Owns objects in MAIN_SCHEMA per
     * SEED_SPEC §2 (Informix uses owner = current user; there is no
     * separate "schema" namespace).
     */
    private static final String MAIN_USER     = "main_user";
    private static final String MAIN_PASSWORD = "CmtMain#2026";
    private static final String MAIN_SCHEMA   = "main_user";

    /**
     * Cross-schema reference user. Owns REF_SCHEMA objects (multi-schema mode).
     */
    private static final String REF_USER     = "ref_user";
    private static final String REF_PASSWORD = "CmtRef#2026";
    private static final String REF_SCHEMA   = "ref_user";

    private static final String INIT_CLASSPATH =
        "db/informix/init/00_prepare_database.sql";
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

    /**
     * Empty Informix instance (no e2e_db, only the default databases
     * shipped with the image).
     */
    public static InformixContainer withEmptyDb() {
        return new InformixContainer(false);
    }

    /**
     * Informix instance with {@code e2e_db} created and both
     * {@code main_user} and {@code ref_user} provisioned (OS user
     * + CONNECT/RESOURCE grants applied via
     * {@code db/informix/init/00_prepare_database.sql}).
     *
     * <h2>Initialization Order</h2>
     * <pre>
     * 1. Container boots; informix DBA exists.
     * 2. start() waits for "Maximum server connections" log, then:
     *    a. useradd main_user + chpasswd
     *    b. useradd ref_user + chpasswd
     *    c. dbaccess - {@code /tmp/init.sql}  (CREATE DATABASE + GRANTs)
     * 3. InformixDatabaseInitializer.migrateRef("informix/ref_schema")  — ref_user
     * 4. InformixDatabaseInitializer.migrateMain("informix/main_schema") — main_user
     * </pre>
     */
    public static InformixContainer withMainUser() {
        return new InformixContainer(true);
    }

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
     * Add an OS-level account inside the container.
     *
     * <p>The Informix Developer Edition image runs as the {@code informix}
     * user (UID 1001), <b>not</b> root. Even with {@code --privileged}, a
     * non-root process cannot lock {@code /etc/passwd}. The image grants
     * the informix user passwordless sudo, so we prefix the privileged
     * commands with {@code sudo} (verified in the official docker hub
     * README — "The informix user has sudo privileges").
     */
    private void createOsUser(String name, String password) throws Exception {
        // -m creates a home dir; -s /bin/false because the user only logs in via SQLI.
        Container.ExecResult addRes = container.execInContainer(
            "sudo", "useradd", "-m", "-s", "/bin/false", name);
        if (addRes.getExitCode() != 0 && !addRes.getStderr().contains("already exists")) {
            throw new IllegalStateException(
                "useradd " + name + " failed (exit " + addRes.getExitCode() + ")\n"
                    + "stderr:\n" + addRes.getStderr());
        }
        // chpasswd reads "user:password" lines on stdin. Use bash -c with a
        // here-string so sudo runs the whole pipeline as root.
        Container.ExecResult pwdRes = container.execInContainer(
            "sudo", "bash", "-c",
            "echo '" + name + ":" + password + "' | chpasswd");
        if (pwdRes.getExitCode() != 0) {
            throw new IllegalStateException(
                "chpasswd " + name + " failed (exit " + pwdRes.getExitCode() + ")\n"
                    + "stderr:\n" + pwdRes.getStderr());
        }
    }

    /**
     * Apply {@code db/informix/init/00_prepare_database.sql} via dbaccess
     * as the informix DBA. dbaccess runs the file in command mode with
     * implicit transaction (CREATE DATABASE is its own logical commit).
     */
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
    @Override public GenericContainer<?> getContainer() { return container; }

    /**
     * Builds an Informix JDBC URL pointing at the named database.
     * The {@code INFORMIXSERVER=informix} suffix matches the env var
     * set on the container and is hardcoded in CMT 's
     * {@code InformixDatabase.makeUrl}.
     */
    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format(
            "jdbc:informix-sqli://%s:%d/%s:INFORMIXSERVER=%s",
            getHost(), getDatabasePort(), dbName, INFORMIX_SERVER);
    }

    @Override public Set<Startable> getDependencies() { return container.getDependencies(); }

    /** Database name created by the init script. */
    public String getDatabaseName() { return DATABASE_NAME; }

    /** INFORMIXSERVER name (matches CMT 's hardcoded URL suffix). */
    public String getInformixServer() { return INFORMIX_SERVER; }

    /** Main migration user. */
    public String getMainUser()     { return MAIN_USER; }
    /** Password for {@link #getMainUser()}. */
    public String getMainPassword() { return MAIN_PASSWORD; }
    /** Schema (= owner) for objects created by main_user. */
    public String getMainSchema()   { return MAIN_SCHEMA; }

    /** Reference-schema user (multi-schema mode). */
    public String getRefUser()     { return REF_USER; }
    /** Password for {@link #getRefUser()}. */
    public String getRefPassword() { return REF_PASSWORD; }
    /** Schema (= owner) for objects created by ref_user. */
    public String getRefSchema()   { return REF_SCHEMA; }

    /** Informix DBA user. */
    public String getDbaUser()     { return DBA_USER; }
    /** DBA password. */
    public String getDbaPassword() { return DBA_PASSWORD; }
}
