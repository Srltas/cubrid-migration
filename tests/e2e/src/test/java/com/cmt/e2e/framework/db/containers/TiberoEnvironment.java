package com.cmt.e2e.framework.db.containers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.cmt.e2e.framework.core.E2eTestProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single source of truth for Tibero scenario configuration.
 *
 * <h3>Why this class owns all access</h3>
 * Tibero requires three environment-specific values that {@link
 * TiberoContainer} cannot guess at — Docker image, license-bound
 * hostname, and host-side path to {@code license.xml}. None of these
 * have a meaningful default outside of one specific developer's setup,
 * so the suite treats them as <b>required</b>: if any is missing, the
 * Tibero {@code @Test} methods skip via {@link #isAvailable()} (used as
 * a JUnit 5 {@code @EnabledIf} hook). The rest of the suite (Oracle,
 * CUBRID) runs unchanged.
 *
 * <p>The fourth value — the in-container path Tibero reads
 * {@code license.xml} from — is fixed by the bundled image (Tibero 7
 * install convention {@code /opt/tibero7/license/license.xml}) and so
 * lives as a hardcoded constant in {@link TiberoContainer}, not here.
 *
 * <p>Keeping all three lookups in one class — rather than scattering
 * {@code System.getProperty} calls across {@code TiberoContainer} +
 * {@code TiberoEnvironment} — means the {@code @EnabledIf} check and
 * the actual container construction can never disagree about which
 * config the test is using. Drift between them would manifest as
 * "skip says ready, boot says not ready" (or vice versa) — silent.
 *
 * <h3>Configuration source</h3>
 * Each key is resolved by {@link E2eTestProperties}: command-line
 * {@code -Dkey=value} → {@code tests/e2e/e2e-test.properties} →
 * (no default; missing means "skip"). See
 * {@code tests/e2e/e2e-test.properties.example} for the full key list
 * and {@code tests/e2e/tibero/README.md} for the setup SOP.
 *
 * <h3>What this class does NOT verify</h3>
 * <ul>
 *   <li>That the configured Docker image actually exists in the local
 *       Docker daemon. Testcontainers will surface a clear pull/run
 *       error early in the test if it doesn't.</li>
 *   <li>That the license file is valid (un-expired, hostname matches).
 *       Tibero's listener fails to start with a license error in that
 *       case; the test then fails at boot rather than skipping.</li>
 * </ul>
 */
public final class TiberoEnvironment {

    private static final Logger log = LoggerFactory.getLogger(TiberoEnvironment.class);

    /** Required config keys — all three must be set for the scenario to run. */
    public static final String IMAGE_KEY    = "e2e.tibero.image";
    public static final String HOSTNAME_KEY = "e2e.tibero.hostname";
    public static final String LICENSE_KEY  = "e2e.tibero.license";

    private TiberoEnvironment() {}

    // -------------------------------------------------------------------------
    // @EnabledIf hook
    // -------------------------------------------------------------------------

    /**
     * True when Tibero is fully provisioned and tests should run.
     * Logs a one-line skip reason at INFO when it returns false so the
     * surefire output explains <i>why</i> the Tibero TC was skipped
     * (driver missing? config missing? which key? license file gone?).
     */
    public static boolean isAvailable() {
        if (!driverOnClasspath()) {
            log.info("[Tibero] skipping — JDBC driver com.tmax.tibero.jdbc.TbDriver "
                + "not on classpath. See tests/e2e/lib/README.md.");
            return false;
        }
        String missing = firstMissingRequiredKey();
        if (missing != null) {
            log.info("[Tibero] skipping — required config '{}' not set. "
                + "Set it in tests/e2e/e2e-test.properties or pass -D{}=value. "
                + "See tests/e2e/tibero/README.md.", missing, missing);
            return false;
        }
        Path lic = licensePath();
        if (!lic.isAbsolute()) {
            log.info("[Tibero] skipping — license path '{}' (from key '{}') "
                + "must be absolute. Relative paths are no longer accepted; "
                + "use a full path like /Users/you/.../license.xml.",
                lic, LICENSE_KEY);
            return false;
        }
        if (!Files.exists(lic)) {
            log.info("[Tibero] skipping — license file not found at {} "
                + "(from key '{}').", lic, LICENSE_KEY);
            return false;
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Required config getters — call only after isAvailable() returned true
    // -------------------------------------------------------------------------

    /** Docker image tag, e.g. {@code "faketime-tibero:2026-fixed"}. */
    public static String image() {
        return required(IMAGE_KEY);
    }

    /** License-bound hostname (matches the licensee suffix in license.xml). */
    public static String hostname() {
        return required(HOSTNAME_KEY);
    }

    /** Host-side path to {@code license.xml}. Must be absolute —
     *  {@link #isAvailable()} skips the scenario when the configured
     *  value is relative. Returning a {@link Path} as-is (without
     *  resolving against cwd) keeps the contract honest: whatever the
     *  user typed is what the container sees. */
    public static Path licensePath() {
        return Paths.get(required(LICENSE_KEY));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static boolean driverOnClasspath() {
        try {
            Class.forName("com.tmax.tibero.jdbc.TbDriver");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Returns the first required key that has no usable value, or
     *  {@code null} if all three are set. */
    private static String firstMissingRequiredKey() {
        for (String key : new String[] {IMAGE_KEY, HOSTNAME_KEY, LICENSE_KEY}) {
            if (rawValue(key) == null) return key;
        }
        return null;
    }

    private static String rawValue(String key) {
        String v = E2eTestProperties.get(key);
        return (v == null || v.isBlank()) ? null : v;
    }

    private static String required(String key) {
        String v = rawValue(key);
        if (v == null) {
            // Should never reach here via the normal test entry point —
            // @EnabledIf("...isAvailable") filters first.
            throw new IllegalStateException(
                "Tibero config key '" + key + "' is not set. "
                + "Set it in tests/e2e/e2e-test.properties or pass -D" + key + "=value. "
                + "See tests/e2e/tibero/README.md.");
        }
        return v;
    }
}
