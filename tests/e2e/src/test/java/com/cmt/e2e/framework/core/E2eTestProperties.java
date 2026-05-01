package com.cmt.e2e.framework.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional local-only configuration file for E2E test runs.
 *
 * <p>The file lives at {@code tests/e2e/e2e-test.properties} (relative
 * to the module root, which is the working directory during {@code mvn
 * test} both on the host and inside the {@code e2e-test} docker-compose
 * service). It is committed to {@code .gitignore} so per-developer
 * values (machine-specific paths, internal image registries, license
 * locations) do not leak into the repo. A committed
 * {@code e2e-test.properties.example} ships a template.
 *
 * <h3>Resolution order</h3>
 * Each call to {@link #get(String, String)} checks, in priority order:
 * <ol>
 *   <li>Java system property ({@code -Dkey=value} on the command line) —
 *       wins, intended for CI overrides and one-off runs.</li>
 *   <li>{@code e2e-test.properties} entry for the same key.</li>
 *   <li>The {@code defaultValue} passed by the caller.</li>
 * </ol>
 * Empty/whitespace-only values at levels 1 and 2 are treated as "not
 * set" and fall through to the next level — so a literal blank line
 * like {@code e2e.tibero.image=} in the file means "use the default,"
 * not "set the image to empty string."
 *
 * <h3>Why both system property and file</h3>
 * <ul>
 *   <li>Properties file → smooth dev experience, persists across runs.</li>
 *   <li>System property → CI invocations, ad-hoc overrides without
 *       editing files.</li>
 * </ul>
 *
 * <h3>Known keys</h3>
 * Discoverable in {@code e2e-test.properties.example}. Tibero-related:
 * {@code e2e.tibero.image}, {@code e2e.tibero.hostname},
 * {@code e2e.tibero.license}. Future settings register here too.
 */
public final class E2eTestProperties {

    private static final Logger log = LoggerFactory.getLogger(E2eTestProperties.class);
    private static final String FILE_NAME = "e2e-test.properties";
    private static final Properties PROPS = loadProps();

    private E2eTestProperties() {}

    /**
     * Resolve {@code key} via system property → file → default. Returns
     * {@code defaultValue} (may be {@code null}) when neither the system
     * property nor the file entry has a non-blank value.
     */
    public static String get(String key, String defaultValue) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) return sys.trim();
        String file = PROPS.getProperty(key);
        if (file != null && !file.isBlank()) return file.trim();
        return defaultValue;
    }

    /** {@link #get(String, String)} with {@code null} default. */
    public static String get(String key) {
        return get(key, null);
    }

    private static Properties loadProps() {
        Properties p = new Properties();
        Path file = Paths.get(FILE_NAME);
        if (!Files.exists(file)) {
            log.debug("[E2eTestProperties] no {} found at {} — defaults will apply",
                FILE_NAME, file.toAbsolutePath());
            return p;
        }
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
            log.info("[E2eTestProperties] loaded {} keys from {}",
                p.size(), file.toAbsolutePath());
        } catch (IOException e) {
            // A malformed properties file should not break the test run —
            // log a warning and proceed with defaults.
            log.warn("[E2eTestProperties] failed to read {} ({}); using defaults",
                file.toAbsolutePath(), e.getMessage());
        }
        return p;
    }
}
