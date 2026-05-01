package com.cmt.e2e.framework.db.containers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Tibero 7.2.4 Testcontainer.
 *
 * <h3>Dev-private prerequisites (TmaxSoft license, not on public registries)</h3>
 * Three things are not in the public repo:
 * <ul>
 *   <li><b>Custom Docker image</b> — defaults to
 *       {@code faketime-tibero:2026-fixed}, built locally from
 *       {@code tests/e2e/tibero/dockerfile} on top of
 *       {@code tiberoofficial/tibero:7.2.4} + {@code libfaketime}. Override
 *       with {@code -De2e.tibero.image=<image:tag>}.</li>
 *   <li><b>JDBC driver</b> ({@code com.tmax.tibero.jdbc.TbDriver}) — not on
 *       Maven Central; place at {@code tests/e2e/lib/tibero7-jdbc-17.jar}.
 *       The {@code tibero} Maven profile auto-activates on the jar's
 *       presence and registers a system-scope dep.</li>
 *   <li><b>License file</b> — TmaxSoft 30-day trial, hostname-bound.
 *       Default location {@code tests/e2e/tibero/license.xml}; override with
 *       {@code -De2e.tibero.license=/abs/path}. Container hostname must
 *       match the licensee string or boot fails — default
 *       {@code tibero-3-100}, override with
 *       {@code -De2e.tibero.hostname=<hostname>}.</li>
 * </ul>
 *
 * <p>If any of these is missing, Tibero {@code @Test} methods skip via
 * {@code @EnabledIf("...TiberoEnvironment#isAvailable")}; the rest of the
 * suite runs unchanged. See {@code tests/e2e/tibero/README.md} for the
 * full setup SOP.
 */
public final class TiberoContainer implements DatabaseContainer {

    private static final String IMAGE_PROP    = "e2e.tibero.image";
    private static final String IMAGE_DEFAULT = "faketime-tibero:2026-fixed";

    private static final String HOSTNAME_PROP    = "e2e.tibero.hostname";
    private static final String HOSTNAME_DEFAULT = "tibero-3-100";

    private static final int    TIBERO_PORT    = 8629;
    private static final String SID            = "tibero";
    private static final String DBA_USER       = "sys";
    private static final String DBA_PASSWORD   = "tibero123";
    private static final String MAIN_USER      = "MAIN_SCHEMA";
    private static final String MAIN_PASSWORD  = "cmt";
    private static final String REF_USER       = "REF_SCHEMA";
    private static final String REF_PASSWORD   = "cmt";

    private static final String LICENSE_PROP      = "e2e.tibero.license";
    private static final String LICENSE_DEFAULT   = "tibero/license.xml";  // relative to e2e module root
    private static final String LICENSE_CONTAINER = "/opt/tibero7/license/license.xml";

    private final GenericContainer<?> container;

    private TiberoContainer() {
        Path licensePath = resolveLicensePath();
        DockerImageName image = DockerImageName.parse(
            System.getProperty(IMAGE_PROP, IMAGE_DEFAULT));
        String hostname = System.getProperty(HOSTNAME_PROP, HOSTNAME_DEFAULT);
        this.container = new GenericContainer<>(image)
            // Force hostname (license binding) + amd64 platform (image is x86_64-only).
            .withCreateContainerCmdModifier(cmd -> {
                cmd.withHostName(hostname);
                cmd.withPlatform("linux/amd64");
            })
            .withExposedPorts(TIBERO_PORT)
            .withEnv("TB_ROOT_PASSWORD", DBA_PASSWORD)
            .withCopyFileToContainer(
                MountableFile.forHostPath(licensePath.toAbsolutePath().toString()),
                LICENSE_CONTAINER)
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

    private static Path resolveLicensePath() {
        String override = System.getProperty(LICENSE_PROP);
        Path p = override != null ? Paths.get(override) : Paths.get(LICENSE_DEFAULT);
        if (!Files.exists(p)) {
            // Reaching this branch means @EnabledIf("...TiberoEnvironment#isAvailable")
            // was bypassed — should never happen via the normal test entry point.
            throw new IllegalStateException(
                "Tibero license not found at " + p.toAbsolutePath() +
                "\nProvide via -D" + LICENSE_PROP + "=/abs/path or place at " + LICENSE_DEFAULT +
                "\nSee tests/e2e/tibero/README.md for the setup SOP.");
        }
        return p;
    }

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
