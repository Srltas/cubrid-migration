package com.cmt.e2e.framework.db.containers;

import java.nio.file.Path;
import java.time.Duration;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Tibero 7 Testcontainer.
 *
 * <h3>What this class does NOT decide</h3>
 * Image, hostname, and host-side license path are environment-specific
 * — they come from {@link TiberoEnvironment} with no hardcoded
 * defaults. Setting them is the dev's responsibility (see
 * {@code tests/e2e/tibero/README.md} for the setup SOP); when any is
 * missing, {@code TiberoEnvironment#isAvailable()} returns false and
 * the {@code @EnabledIf} on the Tibero {@code @Test} classes skips the
 * whole scenario.
 *
 * <h3>What stays hardcoded</h3>
 * Values that are part of the bundled image contract rather than the
 * host environment — same image always means same value:
 * <ul>
 *   <li>Tibero listener port {@value #TIBERO_PORT} — image convention.</li>
 *   <li>Service ID {@code "tibero"} — image convention.</li>
 *   <li>In-container license path {@value #LICENSE_IN_CONTAINER} —
 *       Tibero 7 install convention; the listener reads
 *       {@code license.xml} from this exact path. Changing it would
 *       require a custom dockerfile / listener config edit.</li>
 *   <li>DBA credentials ({@code sys}/{@value #DBA_PASSWORD}) — set by
 *       passing {@code TB_ROOT_PASSWORD} to the container at start.</li>
 *   <li>Test schema users (MAIN_SCHEMA, REF_SCHEMA) and their
 *       passwords — created by {@code db/tibero/init/} during seed
 *       application; the test framework owns these names.</li>
 * </ul>
 * Customising any of these means forking the dockerfile + seed scripts
 * together — out of scope for this class.
 */
public final class TiberoContainer implements DatabaseContainer {

    private static final int    TIBERO_PORT          = 8629;
    private static final String SID                  = "tibero";
    private static final String LICENSE_IN_CONTAINER = "/opt/tibero7/license/license.xml";
    private static final String DBA_USER             = "sys";
    private static final String DBA_PASSWORD         = "tibero123";
    private static final String MAIN_USER            = "MAIN_SCHEMA";
    private static final String MAIN_PASSWORD        = "cmt";
    private static final String REF_USER             = "REF_SCHEMA";
    private static final String REF_PASSWORD         = "cmt";

    private final GenericContainer<?> container;

    private TiberoContainer() {
        // The three environment-specific values come from TiberoEnvironment.
        // The @EnabledIf("...isAvailable") guard upstream guarantees these
        // calls succeed; if they don't, the test was wired wrong and the
        // IllegalStateException from required() makes that loud.
        DockerImageName image  = DockerImageName.parse(TiberoEnvironment.image());
        String hostname        = TiberoEnvironment.hostname();
        Path   licenseHostPath = TiberoEnvironment.licensePath();

        this.container = new GenericContainer<>(image)
            // Force hostname (license binding) + amd64 platform (image is x86_64-only).
            .withCreateContainerCmdModifier(cmd -> {
                cmd.withHostName(hostname);
                cmd.withPlatform("linux/amd64");
            })
            .withExposedPorts(TIBERO_PORT)
            .withEnv("TB_ROOT_PASSWORD", DBA_PASSWORD)
            .withCopyFileToContainer(
                MountableFile.forHostPath(licenseHostPath.toAbsolutePath().toString()),
                LICENSE_IN_CONTAINER)
            .withSharedMemorySize(1024L * 1024 * 1024)  // 1 GB shm — Tibero needs it
            // Tibero boot marker — verified empirically by booting the image
            // and tailing logs. The line "Tibero is Ready To Use!" prints
            // after the entrypoint script finishes installing system packages
            // and creating the SYS user; the listener (port 8629) is already
            // accepting connections by this point. Boot under amd64 emulation
            // on Apple Silicon: ~150-180 s wall-clock.
            .waitingFor(
                Wait.forLogMessage(".*Tibero is Ready To Use.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(8)));
    }

    /** Single factory — keeps construction discoverable. */
    public static TiberoContainer create() { return new TiberoContainer(); }

    // -------------------------------------------------------------------------
    // Startable / DatabaseContainer
    // -------------------------------------------------------------------------

    @Override public void start() { container.start(); }
    @Override public void stop()  { container.stop(); }
    @Override public void close() { container.close(); }

    @Override public String  getHost()         { return container.getHost(); }
    @Override public Integer getDatabasePort() { return container.getMappedPort(TIBERO_PORT); }
    @Override public DB      getDbType()       { return DB.TIBERO; }

    /** {@code jdbc:tibero:thin:@host:mappedPort:tibero}. */
    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format("jdbc:tibero:thin:@%s:%d:%s",
            getHost(), getDatabasePort(), SID);
    }

    public String getSid()           { return SID; }
    public String getDbaUser()       { return DBA_USER; }
    public String getDbaPassword()   { return DBA_PASSWORD; }
    public String getMainUser()      { return MAIN_USER; }
    public String getMainPassword()  { return MAIN_PASSWORD; }
    public String getRefUser()       { return REF_USER; }
    public String getRefPassword()   { return REF_PASSWORD; }
}
