package com.cmt.e2e.scripts;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cmt.e2e.framework.command.CommandResult;
import com.cmt.e2e.framework.command.CommandRunner;
import com.cmt.e2e.framework.command.ScriptCommand;
import com.cmt.e2e.framework.db.JdbcDriverJars;
import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import com.cmt.e2e.framework.db.containers.CubridContainer;
import com.cmt.e2e.framework.db.containers.DatabaseContainer;
import com.cmt.e2e.framework.db.containers.InformixContainer;
import com.cmt.e2e.framework.db.containers.MariaDbContainer;
import com.cmt.e2e.framework.db.containers.MsSqlContainer;
import com.cmt.e2e.framework.db.containers.MySqlContainer;
import com.cmt.e2e.framework.db.containers.OracleContainer;
import com.cmt.e2e.framework.db.init.ClasspathSqlRunner;
import com.cmt.e2e.framework.db.init.CubridDatabaseInitializer;
import com.cmt.e2e.framework.db.init.InformixDatabaseInitializer;
import com.cmt.e2e.framework.db.init.MariadbDatabaseInitializer;
import com.cmt.e2e.framework.db.init.MssqlDatabaseInitializer;
import com.cmt.e2e.framework.db.init.MysqlDatabaseInitializer;
import com.cmt.e2e.framework.db.init.OracleDatabaseInitializer;

/**
 * Utility that regenerates test fixture {@code script.xml} files from CMT Console.
 *
 * <p>When CMT adds schema elements or attributes, existing scripts may become
 * incompatible and break tests. This tool generates a fresh {@code script.xml}
 * through the real {@code migration.sh script} command, replaces
 * container-dependent host/port/driver values with {@code %%PLACEHOLDER%%},
 * and stores the result under {@code target/regenerate/}.
 * Developers can review the diff and manually replace the fixture under
 * {@code src/test/resources}.
 *
 * <h2>Prerequisites</h2>
 * <ul>
 *   <li>{@code CMT_CONSOLE_HOME} is set on the host.</li>
 *   <li>Docker daemon access is available.</li>
 * </ul>
 *
 * <h2>Execution</h2>
 * Always invoke through the wrapper script, not {@code mvn exec:java} directly.
 * The wrapper detects whether it is running on the host or inside the
 * {@code e2e-test} container (via {@code /.dockerenv}) and, on the host,
 * re-executes itself inside the container. This is required because the CMT
 * Console bundled JRE is a {@code linux/amd64} ELF binary and cannot run on
 * macOS or Windows hosts directly.
 * <pre>
 * ./bin/regenerate-scripts.sh                   # all scenarios
 * ./bin/regenerate-scripts.sh oracle_to_cubrid  # one scenario
 * </pre>
 *
 * <h2>Architecture</h2>
 * Scenarios are declarative {@link Scenario} records held in {@link #SCENARIOS}.
 * The DB-specific knowledge is split into:
 * <ul>
 *   <li>{@link #SCENARIOS} — what container to start, what seed to apply</li>
 *   <li>{@link #SOURCE_CONFIGS} — how to write source-side {@code db.conf}
 *       per source DB type</li>
 *   <li>{@link #DUMPFILE_TARGETS} — dumpfile prefix / split policy per
 *       source DB type (used only when target is {@code unload})</li>
 * </ul>
 * Adding a new source DB requires touching all three; that triple touch is
 * intentional — each map captures a distinct concern.
 */
public final class RegenerateScripts {

    private static final Path FIXTURE_BASE = Paths.get("src/test/resources/tests/migration");
    private static final Path OUTPUT_BASE  = Paths.get("target/regenerate");
    private static final String SOURCE_CONFIG_NAME = "regen_source";
    private static final String TARGET_CONFIG_NAME = "regen_target";

    private RegenerateScripts() {}

    public static void main(String[] args) throws Exception {
        List<String> filter = List.of(args);

        ensureCmtConsoleHome();
        Files.createDirectories(OUTPUT_BASE);

        for (Scenario s : SCENARIOS) {
            if (!filter.isEmpty() && !filter.contains(s.id())) continue;
            System.out.println("\n=== Regenerating: " + s.id() + " ===");
            try {
                run(s);
                System.out.println("[OK] generated: " + OUTPUT_BASE.resolve(s.id()).resolve("script.xml").toAbsolutePath());
                System.out.println("[NEXT] diff and overwrite the fixture if acceptable:");
                System.out.println("  cp " + OUTPUT_BASE.resolve(s.id()).resolve("script.xml")
                    + " " + s.fixtureDir().resolve("script.xml"));
            } catch (Exception e) {
                System.err.println("[FAIL] " + s.id() + ": " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Scenarios — declarative description of the 12 source/target combinations
    // ---------------------------------------------------------------------------

    /**
     * One regen scenario. {@code targetFactory == null} means dump-file target
     * (CMT {@code unload}); otherwise it is an online CUBRID target.
     */
    private record Scenario(
        String id,
        String fixtureRel,
        Supplier<? extends DatabaseContainer> sourceFactory,
        Supplier<? extends DatabaseContainer> targetFactory,
        Consumer<DatabaseContainer> seed
    ) {
        Path fixtureDir() {
            return FIXTURE_BASE.resolve(fixtureRel);
        }

        boolean isDumpfile() {
            return targetFactory == null;
        }
    }

    private static final List<Scenario> SCENARIOS = List.of(
        new Scenario("oracle_to_cubrid",     "oracle/oracle_to_cubrid",
            OracleContainer::withTwoUsers,    CubridContainer::withEmptyDb,  RegenerateScripts::seedOracle),
        new Scenario("oracle_to_dumpfile",   "oracle/oracle_to_dumpfile",
            OracleContainer::withTwoUsers,    null,                          RegenerateScripts::seedOracle),
        new Scenario("cubrid_to_cubrid",     "cubrid/cubrid_to_cubrid",
            CubridContainer::withEmptyDb,     CubridContainer::withEmptyDb,  RegenerateScripts::seedCubrid),
        new Scenario("cubrid_to_dumpfile",   "cubrid/cubrid_to_dumpfile",
            CubridContainer::withEmptyDb,     null,                          RegenerateScripts::seedCubrid),
        new Scenario("mysql_to_cubrid",      "mysql/mysql_to_cubrid",
            MySqlContainer::withMainUser,     CubridContainer::withEmptyDb,  RegenerateScripts::seedMysql),
        new Scenario("mysql_to_dumpfile",    "mysql/mysql_to_dumpfile",
            MySqlContainer::withMainUser,     null,                          RegenerateScripts::seedMysql),
        new Scenario("mariadb_to_cubrid",    "mariadb/mariadb_to_cubrid",
            MariaDbContainer::withMainUser,   CubridContainer::withEmptyDb,  RegenerateScripts::seedMariadb),
        new Scenario("mariadb_to_dumpfile",  "mariadb/mariadb_to_dumpfile",
            MariaDbContainer::withMainUser,   null,                          RegenerateScripts::seedMariadb),
        new Scenario("mssql_to_cubrid",      "mssql/mssql_to_cubrid",
            MsSqlContainer::withMainUser,     CubridContainer::withEmptyDb,  RegenerateScripts::seedMssql),
        new Scenario("mssql_to_dumpfile",    "mssql/mssql_to_dumpfile",
            MsSqlContainer::withMainUser,     null,                          RegenerateScripts::seedMssql),
        new Scenario("informix_to_cubrid",   "informix/informix_to_cubrid",
            InformixContainer::withMainUser,  CubridContainer::withEmptyDb,  RegenerateScripts::seedInformix),
        new Scenario("informix_to_dumpfile", "informix/informix_to_dumpfile",
            InformixContainer::withMainUser,  null,                          RegenerateScripts::seedInformix)
    );

    /** Run a single scenario end-to-end: start, seed, run CMT, sanitize, save. */
    @SuppressWarnings("resource")
    private static void run(Scenario s) throws Exception {
        DatabaseContainer source = s.sourceFactory().get();
        DatabaseContainer target = s.isDumpfile() ? null : s.targetFactory().get();
        try {
            source.start();
            if (target != null) target.start();
            s.seed().accept(source);

            Path raw = runCmtScript(source, target, s);
            Path sanitized = sanitizeXml(raw, source, target, s);
            Files.move(sanitized,
                OUTPUT_BASE.resolve(s.id()).resolve("script.xml"),
                StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (target != null) target.close();
            source.close();
        }
    }

    // ---------------------------------------------------------------------------
    // Seed actions — DB-specific bootstrap before CMT inspects the source
    // ---------------------------------------------------------------------------

    private static void seedOracle(DatabaseContainer src) {
        OracleContainer o = (OracleContainer) src;
        OracleDatabaseInitializer.of(o)
            .migrateAs(o.getRefUser(),  o.getRefPassword(),  "oracle/ref_schema")
            .migrateAs(o.getMainUser(), o.getMainPassword(), "oracle/main_schema");
    }

    private static void seedCubrid(DatabaseContainer src) {
        CubridContainer c = (CubridContainer) src;
        // Bootstrap the two-user e2e seed: CREATE USER as dba, then
        // run Flyway migrations as the freshly-created users.
        String dbaUrl = c.getJdbcUrl(c.getDatabaseName(), "dba");
        ClasspathSqlRunner.runDirectory(dbaUrl, "dba", "", "db/cubrid/init");
        CubridDatabaseInitializer.of(c, c.getDatabaseName(), "REF_SCHEMA", "cmt")
            .migrate("cubrid/ref_schema");
        CubridDatabaseInitializer.of(c, c.getDatabaseName(), "MAIN_SCHEMA", "cmt")
            .migrate("cubrid/main_schema");
    }

    private static void seedMysql(DatabaseContainer src) {
        MysqlDatabaseInitializer.of((MySqlContainer) src).migrateMain("mysql/main_schema");
    }

    private static void seedMariadb(DatabaseContainer src) {
        MariadbDatabaseInitializer.of((MariaDbContainer) src).migrateMain("mariadb/main_schema");
    }

    private static void seedMssql(DatabaseContainer src) {
        MssqlDatabaseInitializer.of((MsSqlContainer) src)
            .migrateRef("mssql/ref_schema")
            .migrateMain("mssql/main_schema");
    }

    private static void seedInformix(DatabaseContainer src) {
        InformixDatabaseInitializer.of((InformixContainer) src)
            .migrateMain("informix/main_schema");
    }

    // ---------------------------------------------------------------------------
    // db.conf builders — driven by Map<DB, Writer> instead of if-cascade
    // ---------------------------------------------------------------------------

    @FunctionalInterface
    private interface SourceConfigWriter {
        void write(StringBuilder conf, DatabaseContainer source);
    }

    /**
     * Per source-DB writer for the {@code regen_source.*} block of {@code db.conf}.
     * Each writer is responsible for the full set of keys CMT reads
     * ({@code .type / .driver / .host / .port / .dbname / .user / .password /
     * .charset} and any DB-specific extras like {@code .timezone}).
     */
    private static final Map<DB, SourceConfigWriter> SOURCE_CONFIGS = Map.of(
        DB.ORACLE, (conf, src) -> {
            OracleContainer o = (OracleContainer) src;
            appendBaseSource(conf, "oracle", src);
            appendProperty(conf, SOURCE_CONFIG_NAME + ".dbname",   o.getSid());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".user",     o.getMainUser());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".password", o.getMainPassword());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".charset",  "utf-8");
            appendProperty(conf, SOURCE_CONFIG_NAME + ".timezone", "GMT+00:00");
        },
        DB.CUBRID, (conf, src) -> {
            // Connect as dba so CMT can discover both REF_SCHEMA and MAIN_SCHEMA
            // when scanning user objects of the e2e seed.
            CubridContainer c = (CubridContainer) src;
            appendBaseSource(conf, "cubrid", src);
            appendProperty(conf, SOURCE_CONFIG_NAME + ".dbname",   c.getDatabaseName());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".user",     "dba");
            appendProperty(conf, SOURCE_CONFIG_NAME + ".password", "");
            appendProperty(conf, SOURCE_CONFIG_NAME + ".charset",  "utf-8");
        },
        DB.MYSQL, (conf, src) -> {
            // Connect as root so CMT can introspect both main_schema and ref_schema
            // databases. (main_user has table-level grants on ref_schema.e2e_ref_audit
            // but cannot enumerate the rest of the ref_schema namespace; root sees all.)
            MySqlContainer m = (MySqlContainer) src;
            appendBaseSource(conf, "mysql", src);
            appendProperty(conf, SOURCE_CONFIG_NAME + ".dbname",   m.getMainDatabase());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".user",     m.getRootUser());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".password", m.getRootPassword());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".charset",  "utf-8");
        },
        DB.MARIADB, (conf, src) -> {
            // Same single-database collapse as MySQL — connect as root so CMT
            // sees the full namespace. CMT 's MariaDB plugin uses db_type=mariadb
            // (separate from MySQL) which selects MariaDBSchemaFetcher.
            MariaDbContainer m = (MariaDbContainer) src;
            appendBaseSource(conf, "mariadb", src);
            appendProperty(conf, SOURCE_CONFIG_NAME + ".dbname",   m.getMainDatabase());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".user",     m.getRootUser());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".password", m.getRootPassword());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".charset",  "utf-8");
        },
        DB.MSSQL, (conf, src) -> {
            // Connect as sa so CMT can introspect both main_schema and ref_schema
            // (MSSQL multi-schema). main_user lacks visibility into ref_schema 's
            // metadata even with the runtime SELECT GRANT.
            //
            // CMT 's MSSQLDatabase.makeUrl produces "jdbc:sqlserver://host:port;
            // databaseName=<db>" with no TLS options. mssql-jdbc 11.x default
            // is encrypt=false (12.x flipped to true) — we pin 11.x in pom so
            // the connection bypasses server-cert verification without us having
            // to inject encrypt=false / trustServerCertificate at URL level
            // (CMT 's db.conf parser ignores user_jdbc_url).
            MsSqlContainer m = (MsSqlContainer) src;
            appendBaseSource(conf, "mssql", src);
            appendProperty(conf, SOURCE_CONFIG_NAME + ".dbname",   m.getDatabaseName());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".user",     m.getSaUser());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".password", m.getSaPassword());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".charset",  "utf-8");
        },
        DB.INFORMIX, (conf, src) -> {
            // Single-user pattern (single-schema seed). Connecting as
            // main_user keeps unqualified SELECT working at export time —
            // CMT 's InformixSchemaFetcher writes empty schema="" on
            // each <table> entry in the source side of script.xml, which
            // works only when there is exactly one source schema visible
            // and it matches the connection user (so the JDBC SESSION
            // resolves the table from main_user 's namespace). Connecting
            // as the informix DBA exposes multiple schemas in catalog
            // metadata but breaks unqualified SELECT.
            //
            // The CMT InformixDatabase.makeUrl appends ":INFORMIXSERVER=informix"
            // to the URL — InformixContainer sets INFORMIXSERVER env to
            // "informix" so the two values match.
            InformixContainer i = (InformixContainer) src;
            appendBaseSource(conf, "informix", src);
            appendProperty(conf, SOURCE_CONFIG_NAME + ".dbname",   i.getDatabaseName());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".user",     i.getMainUser());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".password", i.getMainPassword());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".charset",  "utf-8");
        }
    );

    private static void appendBaseSource(StringBuilder conf, String type, DatabaseContainer src) {
        appendProperty(conf, SOURCE_CONFIG_NAME + ".type",   type);
        appendProperty(conf, SOURCE_CONFIG_NAME + ".driver", driverPath(src));
        appendProperty(conf, SOURCE_CONFIG_NAME + ".host",   src.getHost());
        appendProperty(conf, SOURCE_CONFIG_NAME + ".port",   src.getDatabasePort().toString());
    }

    /** Dump-file specific values keyed by source DB. */
    private record DumpfileTarget(String filePrefix, boolean oneTableOneFile) {}

    private static final Map<DB, DumpfileTarget> DUMPFILE_TARGETS = Map.of(
        DB.ORACLE,   new DumpfileTarget("XE",       true),
        DB.CUBRID,   new DumpfileTarget("demodb",   false),
        DB.MYSQL,    new DumpfileTarget("MYSQL",    false),
        DB.MARIADB,  new DumpfileTarget("MARIADB",  false),
        DB.MSSQL,    new DumpfileTarget("MSSQL",    false),
        DB.INFORMIX, new DumpfileTarget("INFORMIX", false)
    );

    private static String buildDbConf(DatabaseContainer source, DatabaseContainer target) {
        StringBuilder conf = new StringBuilder();
        SourceConfigWriter writer = SOURCE_CONFIGS.get(source.getDbType());
        if (writer == null) {
            throw new IllegalArgumentException("No source config for " + source.getDbType());
        }
        writer.write(conf, source);
        appendTargetConfig(conf, source, target);
        return conf.toString();
    }

    private static void appendTargetConfig(StringBuilder conf, DatabaseContainer source, DatabaseContainer target) {
        if (target != null) {
            // Online CUBRID target — single shape regardless of source DB.
            CubridContainer c = (CubridContainer) target;
            appendProperty(conf, TARGET_CONFIG_NAME + ".type",       "cubrid");
            appendProperty(conf, TARGET_CONFIG_NAME + ".driver",     driverPath(target));
            appendProperty(conf, TARGET_CONFIG_NAME + ".host",       target.getHost());
            appendProperty(conf, TARGET_CONFIG_NAME + ".port",       target.getDatabasePort().toString());
            appendProperty(conf, TARGET_CONFIG_NAME + ".dbname",     c.getDatabaseName());
            appendProperty(conf, TARGET_CONFIG_NAME + ".user",       "dba");
            appendProperty(conf, TARGET_CONFIG_NAME + ".password",   "");
            appendProperty(conf, TARGET_CONFIG_NAME + ".charset",    "utf-8");
            appendProperty(conf, TARGET_CONFIG_NAME + ".add_schema", "yes");
            return;
        }

        // Dump-file (unload) target — prefix/split policy varies per source DB.
        DumpfileTarget dump = DUMPFILE_TARGETS.get(source.getDbType());
        if (dump == null) {
            throw new IllegalArgumentException("No dumpfile target config for " + source.getDbType());
        }
        appendProperty(conf, TARGET_CONFIG_NAME + ".type",               "unload");
        appendProperty(conf, TARGET_CONFIG_NAME + ".output",             "./output");
        appendProperty(conf, TARGET_CONFIG_NAME + ".charset",            "utf-8");
        appendProperty(conf, TARGET_CONFIG_NAME + ".add_schema",         "yes");
        appendProperty(conf, TARGET_CONFIG_NAME + ".split_schema",       "yes");
        appendProperty(conf, TARGET_CONFIG_NAME + ".file_prefix",        dump.filePrefix());
        appendProperty(conf, TARGET_CONFIG_NAME + ".one_table_one_file", dump.oneTableOneFile() ? "yes" : "no");
    }

    private static String driverPath(DatabaseContainer c) {
        return JdbcDriverJars.latest(c.getDbType()).toAbsolutePath().toString();
    }

    private static void appendProperty(StringBuilder conf, String key, String value) {
        conf.append(key).append('=').append(value == null ? "" : value).append('\n');
    }

    // ---------------------------------------------------------------------------
    // CMT invocation
    // ---------------------------------------------------------------------------

    /**
     * Runs {@code migration.sh script -s <sourceConfig> -t <targetConfig> -o <outputDir>}
     * with a temporary {@code db.conf} written into {@code CMT_CONSOLE_HOME}.
     */
    private static Path runCmtScript(DatabaseContainer source, DatabaseContainer target, Scenario s)
            throws Exception {
        Path runDir = OUTPUT_BASE.resolve(s.id());
        Files.createDirectories(runDir);
        Path rawDir = runDir.resolve("raw");
        recreateDirectory(rawDir);

        ScriptCommand cmd = ScriptCommand.builder()
            .sourceConfig(SOURCE_CONFIG_NAME)
            .targetConfig(TARGET_CONFIG_NAME)
            .outputDir(rawDir.toAbsolutePath().toString())
            .build();

        String home = System.getenv("CMT_CONSOLE_HOME");
        CommandRunner runner = new CommandRunner(new File(home));
        CommandResult result = runWithTemporaryDbConf(
            Paths.get(home),
            buildDbConf(source, target),
            () -> runner.run(cmd));

        System.out.println("--- migration.sh script output ---");
        System.out.println(result.stdout());
        if (!result.stderr().isBlank()) {
            System.err.println(result.stderr());
        }
        if (result.exitCode() != 0) {
            throw new IllegalStateException("migration.sh script failed (exit " + result.exitCode() + ")");
        }

        try (var walk = Files.list(rawDir)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".xml"))
                .filter(p -> !p.getFileName().toString().equals("sanitized.xml"))
                .max(Comparator.comparingLong(RegenerateScripts::lastModified))
                .orElseThrow(() -> new IllegalStateException(
                    "CMT did not generate an XML file: " + rawDir));
        }
    }

    // ---------------------------------------------------------------------------
    // Sanitization: replace container-dependent values with %%PLACEHOLDER%%
    // ---------------------------------------------------------------------------

    /**
     * Replaces host/port/driver values in a CMT-generated {@code script.xml}
     * with {@code %%PLACEHOLDER%%}. The result is written as
     * {@code sanitized.xml} next to the input file.
     */
    private static Path sanitizeXml(
            Path rawXml, DatabaseContainer source, DatabaseContainer target, Scenario scenario)
            throws IOException {
        String content = Files.readString(rawXml);

        // Normalize volatile migration identifiers to the checked-in fixture values,
        // so diffs stay focused on real schema/configuration changes.
        content = preserveFixtureMigrationMetadata(content, scenario);

        content = replaceConnectionAttribute(content, "source", "host",   "%%SOURCE_HOST%%");
        content = replaceConnectionAttribute(content, "source", "port",   "%%SOURCE_PORT%%");
        content = replaceConnectionAttribute(content, "source", "driver", "%%SOURCE_DRIVER%%");

        if (target != null) {
            content = replaceConnectionAttribute(content, "target", "host",   "%%TARGET_HOST%%");
            content = replaceConnectionAttribute(content, "target", "port",   "%%TARGET_PORT%%");
            content = replaceConnectionAttribute(content, "target", "driver", "%%TARGET_DRIVER%%");
        }

        if (source.getDbType() == DB.INFORMIX) {
            content = sanitizeInformix(content);
        }

        Path out = rawXml.resolveSibling("sanitized.xml");
        Files.writeString(out, content);
        return out;
    }

    /**
     * Informix sanitize step has two parts:
     *
     * <p>(1) Drop the system DBA schema "INFORMIX" from the {@code <schemas>}
     * list. CMT 's {@code InformixSchemaFetcher.getSchemaNames} pulls it
     * in via JDBC {@code getMetaData().getSchemas()}, but the
     * informix-owned system tables are not part of our seed.
     * Leaving INFORMIX in the schemas list breaks record export
     * downstream ({@code MigrationConfiguration.buildTableCfg} processes
     * all listed source schemas; the INFORMIX entry pollutes
     * {@code SourceEntryTableConfig} owner reconciliation, which results
     * in every table-record lookup returning null with
     * "Table X was not found").
     *
     * <p>(2) Rewrite each source-side {@code <table>} {@code schema=""} attribute
     * to {@code schema="MAIN_USER"}. CMT 's InformixSchemaFetcher emits
     * {@code schema=""} on {@code <table>} elements even though the catalog
     * itself has the schema name; downstream
     * {@code MigrationConfiguration.getSrcTableSchema} only treats
     * {@code schema==null} as "use the default schema" — {@code schema==""}
     * is passed verbatim to {@code Catalog.getSchemaByName("")}, which
     * returns null and triggers the same "Table not found".
     */
    private static String sanitizeInformix(String content) {
        content = content.replaceAll("\\s*<schema source=\"INFORMIX\" target=\"INFORMIX\"/>\\R", "");
        content = content.replaceAll("(<table )schema=\"\"", "$1schema=\"MAIN_USER\"");
        return content;
    }

    private static void recreateDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to clean directory: " + dir, e);
                        }
                    });
            }
        }
        Files.createDirectories(dir);
    }

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

    private static String preserveFixtureMigrationMetadata(String content, Scenario scenario)
            throws IOException {
        Path fixture = scenario.fixtureDir().resolve("script.xml");
        if (!Files.exists(fixture)) {
            return content;
        }

        String fixtureContent = Files.readString(fixture);
        String fixtureName = extractAttribute(fixtureContent, "migration", "name");
        String fixtureStartTime = extractAttribute(fixtureContent, "migration", "wizard_start_date_time");

        if (fixtureName != null) {
            content = replaceTagAttribute(content, "migration", "name", fixtureName);
        }
        if (fixtureStartTime != null) {
            content = replaceTagAttribute(content, "migration", "wizard_start_date_time", fixtureStartTime);
        }
        return content;
    }

    private static String replaceConnectionAttribute(
            String content, String connectionId, String attribute, String replacement) {
        String pattern = "(<connection\\b(?=[^>]*\\bid=\"" + connectionId + "\")[^>]*\\b"
            + attribute + "=\")([^\"]*)(\")";
        return content.replaceAll(pattern, "$1" + Matcher.quoteReplacement(replacement) + "$3");
    }

    private static String replaceTagAttribute(
            String content, String tagName, String attribute, String replacement) {
        String pattern = "(<" + tagName + "\\b[^>]*\\b" + attribute + "=\")([^\"]*)(\")";
        return content.replaceAll(pattern, "$1" + Matcher.quoteReplacement(replacement) + "$3");
    }

    private static String extractAttribute(String content, String tagName, String attribute) {
        Matcher matcher = Pattern
            .compile("<" + tagName + "\\b[^>]*\\b" + attribute + "=\"([^\"]*)\"")
            .matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            throw new RuntimeException("Failed to inspect file timestamp: " + path, e);
        }
    }

    // ---------------------------------------------------------------------------
    // Preconditions
    // ---------------------------------------------------------------------------

    private static void ensureCmtConsoleHome() {
        String home = System.getenv("CMT_CONSOLE_HOME");
        if (home == null || home.isBlank()) {
            throw new IllegalStateException(
                "CMT_CONSOLE_HOME is not set. " +
                "Point it at the extracted CMT Console directory.");
        }
        if (!Files.isExecutable(Paths.get(home, "migration.sh"))) {
            throw new IllegalStateException(
                "CMT_CONSOLE_HOME is invalid (migration.sh not found): " + home);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
