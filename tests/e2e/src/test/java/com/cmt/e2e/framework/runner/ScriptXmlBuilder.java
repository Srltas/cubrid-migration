package com.cmt.e2e.framework.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cmt.e2e.framework.command.CommandResult;
import com.cmt.e2e.framework.command.CommandRunner;
import com.cmt.e2e.framework.command.ScriptCommand;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a CMT {@code script.xml} by invoking
 * {@code migration.sh script -s ... -t ... -o ...} with a supplied
 * {@code db.conf} and then normalises timestamp-bearing attributes so
 * the result is byte-deterministic across runs.
 *
 * <p>Why the normalisation matters: CMT writes
 * {@code <migration name="CUBRID_e2e_db_<14-digit timestamp>"
 * wizard_start_date_time="<14-digit timestamp>">}. The timestamp drifts
 * every run; that drift would propagate into the dump output directory
 * name and into snapshot diffs. Normalising at script-generation time
 * keeps the rest of the pipeline (run + verify + snapshot) stable.
 *
 * <p>Determinism contract — see {@code ARCHITECTURE.md} §7.
 */
public final class ScriptXmlBuilder {

    private static final Logger log = LoggerFactory.getLogger(ScriptXmlBuilder.class);

    private ScriptXmlBuilder() {}

    /** Result of {@link #generate} — sanitized script path + migration name. */
    public record Result(Path scriptXml, String migrationName) {}

    /**
     * @param consoleHome  path to {@code CMT_CONSOLE_HOME}
     * @param dbConf       full {@code db.conf} text (use {@link DbConfBuilder})
     * @param outputDir    where to place the sanitized {@code script.xml}.
     *                     A {@code raw/} subdir holds the unsanitized CMT output.
     * @return path to the sanitized {@code script.xml} and the deterministic
     *         {@code <migration name="...">} value (CMT writes dump output
     *         under {@code $CMT_CONSOLE_HOME/output/<name>/...})
     */
    public static Result generate(Path consoleHome, String dbConf, Path outputDir) throws Exception {
        Files.createDirectories(outputDir);
        Path rawDir = outputDir.resolve("raw");
        recreateDirectory(rawDir);

        ScriptCommand cmd = ScriptCommand.builder()
            .sourceConfig(DbConfBuilder.SOURCE_NAME)
            .targetConfig(DbConfBuilder.TARGET_NAME)
            .outputDir(rawDir.toAbsolutePath().toString())
            .build();

        CommandRunner runner = new CommandRunner(consoleHome.toFile());
        CommandResult result = runWithTemporaryDbConf(
            consoleHome, dbConf, () -> runner.run(cmd));

        log.debug("[ScriptXmlBuilder] migration.sh script — exit={}", result.exitCode());
        if (result.exitCode() != 0) {
            throw new IllegalStateException(
                "migration.sh script failed (exit " + result.exitCode() + ")\n"
                    + "stdout:\n" + result.stdout() + "\n"
                    + "stderr:\n" + result.stderr());
        }

        Path raw = findGeneratedXml(rawDir);
        String sanitized = sanitize(Files.readString(raw));
        Path out = outputDir.resolve("script.xml");
        Files.writeString(out, sanitized);
        return new Result(out.toAbsolutePath(), extractMigrationName(sanitized));
    }

    static String extractMigrationName(String content) {
        Matcher m = Pattern.compile("<migration\\s+name=\"([^\"]+)\"").matcher(content);
        if (!m.find()) {
            throw new IllegalStateException("script.xml missing <migration name=...>");
        }
        return m.group(1);
    }

    // -------------------------------------------------------------------------
    // sanitize — normalise volatile timestamps
    // -------------------------------------------------------------------------

    /**
     * Two-step normalization for snapshot determinism:
     *
     * <ol>
     *   <li>Strip CMT's wall-clock state from migration metadata
     *       ({@code <migration name>} timestamp suffix and
     *       {@code wizard_start_date_time}).</li>
     *   <li>Drop the Flyway-generated {@code flyway_schema_history}
     *       table from the migration plan. It is a seed implementation
     *       detail, not part of the migration contract; its data is also
     *       wall-clock state which would break dump-file determinism.</li>
     * </ol>
     */
    static String sanitize(String content) {
        // (1) wall-clock state in <migration ...>
        // <migration name="CUBRID_e2e_db_202604062341" ...>
        //                                ^^^^^^^^^^^^^ strip (12 digits)
        content = content.replaceAll(
            "(<migration\\s+name=\")([^\"]+?)_\\d{12}(\")",
            "$1$2$3");
        // wizard_start_date_time="202604062341" → "000000000000"
        content = content.replaceAll(
            "(wizard_start_date_time=\")\\d{12}(\")",
            "$1000000000000$2");

        // (2) Flyway metadata removal. CMT references flyway_schema_history
        // in two element shapes:
        //   - self-closing tags carrying its name (sourceTable, table, etc.)
        //   - multi-line <table ...>...</table> blocks with nested
        //     <columns>/<constraints> on both source-side and target-side
        // Strip both. (?s) = DOTALL so '.' matches newlines.
        content = content.replaceAll(
            "(?s)\\s*<table\\b[^>]*\\bname=\"flyway_schema_history\"[^>]*>.*?</table>\\s*",
            "\n            ");
        content = content.replaceAll(
            "(?m)\\s*<\\w+\\b[^>]*\\bname=\"flyway_schema_history\"[^>]*/>\\s*\\R?",
            "");

        return content;
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static CommandResult runWithTemporaryDbConf(
            Path consoleHome, String dbConfContent, ThrowingSupplier<CommandResult> action)
            throws Exception {
        Path dbConf = consoleHome.resolve("db.conf");
        Path backup = null;
        if (Files.exists(dbConf)) {
            backup = Files.createTempFile(consoleHome, "db.conf.", ".bak");
            Files.copy(dbConf, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(dbConf, dbConfContent);
        try {
            return action.get();
        } finally {
            if (backup != null) {
                Files.move(backup, dbConf, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(dbConf);
            }
        }
    }

    private static void recreateDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); }
                    catch (IOException e) { throw new RuntimeException("delete failed: " + p, e); }
                });
            }
        }
        Files.createDirectories(dir);
    }

    private static Path findGeneratedXml(Path rawDir) throws IOException {
        try (var walk = Files.list(rawDir)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".xml"))
                .max(Comparator.comparingLong(ScriptXmlBuilder::lastModified))
                .orElseThrow(() -> new IllegalStateException(
                    "CMT did not produce an XML under " + rawDir));
        }
    }

    private static long lastModified(Path p) {
        try { return Files.getLastModifiedTime(p).toMillis(); }
        catch (IOException e) { throw new RuntimeException("stat: " + p, e); }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
