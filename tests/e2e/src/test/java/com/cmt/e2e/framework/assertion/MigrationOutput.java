package com.cmt.e2e.framework.assertion;

import java.nio.file.Path;

/**
 * Wrapper around a CMT dump migration output directory
 * (e.g. {@code {CMT_CONSOLE_HOME}/output/{migration}/{schema}}).
 * Used by {@link DumpGoldenVerifier} to resolve actual file paths.
 */
public record MigrationOutput(Path baseDir) {
}
