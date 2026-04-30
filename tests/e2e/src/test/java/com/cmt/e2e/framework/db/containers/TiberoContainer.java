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
 * Tibero 7.2.4 Testcontainer (custom image: {@code faketime-tibero:2026-fixed}).
 *
 * <h3>Why a custom image is required</h3>
 * <ul>
 *   <li>Tibero JDBC driver is not on Maven Central — license-restricted.
 *       The jar is committed to {@code tests/e2e/lib/} (system-scope dep).</li>
 *   <li>Tibero license file is hostname-bound:
 *       {@code <licensee>Demo_Trial_tibero-3-100</licensee>} in
 *       {@code tests/e2e/tibero/license.xml}. The container hostname must
 *       match exactly or boot fails. Forced via
 *       {@code withCreateContainerCmdModifier(cmd -> cmd.withHostName(...))}.</li>
 *   <li>Trial license is 30-day. The image bakes {@code libfaketime} via
 *       {@code LD_PRELOAD} ({@code FAKETIME=-99d}) so the in-container clock
 *       stays inside the validity window. Means at most one Tibero container
 *       can run on a host at a time — sufficient for our PER_CLASS lifecycle
 *       since we never parallelize TC classes.</li>
 * </ul>
 *
 * <h3>License path</h3>
 * Default: {@code tests/e2e/tibero/license.xml} (relative to the e2e module
 * root, which is the working dir during {@code mvn test}). Override with
 * {@code -De2e.tibero.license=/abs/path/to/license.xml}.
 */
public final class TiberoContainer implements DatabaseContainer {

    /** {@code faketime-tibero:2026-fixed} — built locally from
     *  {@code tests/e2e/tibero/dockerfile} on top of {@code tiberoofficial/tibero:7.2.4}.
     *  Not on a registry; the image must be built on each developer machine. */
    private static final DockerImageName IMAGE =
        DockerImageName.parse("faketime-tibero:2026-fixed");

    /** License-bound hostname. Must equal the {@code licensee} suffix in
     *  {@code tests/e2e/tibero/license.xml}. */
    private static final String FIXED_HOSTNAME = "tibero-3-100";

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
        this.container = new GenericContainer<>(IMAGE)
            // Force hostname (license binding) + amd64 platform (image is x86_64-only).
            .withCreateContainerCmdModifier(cmd -> {
                cmd.withHostName(FIXED_HOSTNAME);
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
            throw new IllegalStateException(
                "Tibero license not found at " + p.toAbsolutePath() +
                "\nProvide via -D" + LICENSE_PROP + "=/abs/path or place at " + LICENSE_DEFAULT);
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
