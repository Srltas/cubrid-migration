package com.cmt.e2e.framework.db.containers;

import java.nio.file.Files;
import java.nio.file.Paths;

import com.cmt.e2e.framework.core.E2eTestProperties;

/**
 * Pre-flight checks for the Tibero scenario. Used as a JUnit 5
 * {@code @EnabledIf} hook so that on a public clone — where the
 * proprietary TmaxSoft JDBC jar and the hostname-bound license file
 * are absent — the Tibero {@code @Test} methods skip gracefully and
 * the rest of the suite runs unchanged.
 *
 * <p>Internal developers: see {@code tests/e2e/tibero/README.md} for
 * how to provision the dev-private assets so {@link #isAvailable()}
 * returns {@code true} and the Tibero scenario actually executes.
 *
 * <p><b>What this method does NOT verify</b>:
 * <ul>
 *   <li>That the Docker image (default {@code faketime-tibero:2026-fixed},
 *       overridable via {@code -De2e.tibero.image=...}) actually exists in
 *       the local Docker daemon. We don't probe Docker here because
 *       Testcontainers will surface a clear pull/run error early in the
 *       test if the image is missing.</li>
 *   <li>That the license file is valid (un-expired, hostname matches).
 *       Tibero's listener fails to start with a license error in that
 *       case; the test then fails at boot rather than skipping.</li>
 * </ul>
 * The two cheap checks here ({@link #driverOnClasspath()} and
 * {@link #licensePresent()}) catch the public-clone case fully —
 * which is the only scenario that needs silent skipping.
 */
public final class TiberoEnvironment {

    private TiberoEnvironment() {}

    /** {@code @EnabledIf} hook — true when Tibero is provisioned to run. */
    public static boolean isAvailable() {
        return driverOnClasspath() && licensePresent();
    }

    private static boolean driverOnClasspath() {
        try {
            Class.forName("com.tmax.tibero.jdbc.TbDriver");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean licensePresent() {
        // Same resolution path as TiberoContainer.resolveLicensePath() —
        // system property → e2e-test.properties → default. Keeping these
        // two checks in sync is critical: if @EnabledIf passes here but
        // resolveLicensePath() throws there, the test would fail at boot
        // instead of skipping cleanly.
        String path = E2eTestProperties.get(
            TiberoContainer.LICENSE_KEY, TiberoContainer.LICENSE_DEFAULT);
        return Files.exists(Paths.get(path));
    }
}
