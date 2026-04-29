package com.cmt.e2e.framework.env;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Single source of truth for {@code CMT_CONSOLE_HOME} resolution.
 *
 * <p>Tests and runner code should call {@link #resolve()} once; failure to
 * locate a usable Console install fails fast with an actionable message
 * rather than producing cryptic IO errors deeper in the stack.
 */
public final class CmtConsoleEnv {

    /** Environment variable name. */
    public static final String ENV_VAR = "CMT_CONSOLE_HOME";

    private CmtConsoleEnv() {}

    /**
     * Resolves {@code CMT_CONSOLE_HOME} to an absolute path and verifies that
     * a runnable {@code migration.sh} exists inside it.
     *
     * @throws IllegalStateException if the env var is unset/blank or
     *         {@code migration.sh} is missing/non-executable
     */
    public static Path resolve() {
        String home = System.getenv(ENV_VAR);
        if (home == null || home.isBlank()) {
            throw new IllegalStateException(
                ENV_VAR + " is not set. "
                    + "Point it at the extracted CMT Console directory.");
        }
        Path path = Paths.get(home).toAbsolutePath().normalize();
        Path script = path.resolve("migration.sh");
        if (!Files.isExecutable(script)) {
            throw new IllegalStateException(
                ENV_VAR + " is invalid (migration.sh missing or not executable): "
                    + path);
        }
        return path;
    }
}
