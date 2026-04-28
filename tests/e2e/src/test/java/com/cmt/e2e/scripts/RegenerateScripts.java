package com.cmt.e2e.scripts;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;

import com.cmt.e2e.framework.command.execution.CommandResult;
import com.cmt.e2e.framework.command.execution.CommandRunner;
import com.cmt.e2e.framework.command.impls.ScriptCommand;
import com.cmt.e2e.framework.db.containers.CubridContainer;
import com.cmt.e2e.framework.db.containers.DatabaseContainer;
import com.cmt.e2e.framework.db.containers.InformixContainer;
import com.cmt.e2e.framework.db.containers.MariaDbContainer;
import com.cmt.e2e.framework.db.containers.MsSqlContainer;
import com.cmt.e2e.framework.db.containers.MySqlContainer;
import com.cmt.e2e.framework.db.containers.OracleContainer;
import com.cmt.e2e.framework.db.driver.Drivers;
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
 * <h2>Current State</h2>
 * This is a developer utility, not part of the test execution path.
 * Use it only when schema changes require refreshing the checked-in
 * {@code script.xml} fixtures.
 */
public final class RegenerateScripts {

    private static final Path FIXTURE_BASE =
        Paths.get("src/test/resources/tests/migration");

    private static final Path OUTPUT_BASE =
        Paths.get("target/regenerate");
    private static final String SOURCE_CONFIG_NAME = "regen_source";
    private static final String TARGET_CONFIG_NAME = "regen_target";

    private RegenerateScripts() {}

    public static void main(String[] args) throws Exception {
        List<String> filter = List.of(args);

        ensureCmtConsoleHome();
        Files.createDirectories(OUTPUT_BASE);

        for (Scenario s : Scenario.values()) {
            if (!filter.isEmpty() && !filter.contains(s.id)) continue;
            System.out.println("\n=== Regenerating: " + s.id + " ===");
            try {
                s.run();
                System.out.println("[OK] generated: " + OUTPUT_BASE.resolve(s.id).resolve("script.xml").toAbsolutePath());
                System.out.println("[NEXT] diff and overwrite the fixture if acceptable:");
                System.out.println("  cp " + OUTPUT_BASE.resolve(s.id).resolve("script.xml")
                    + " " + s.fixtureDir().resolve("script.xml"));
            } catch (Exception e) {
                System.err.println("[FAIL] " + s.id + ": " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Scenarios
    // ---------------------------------------------------------------------------

    private enum Scenario {
        ORACLE_TO_CUBRID("oracle_to_cubrid", "oracle/oracle_to_cubrid") {
            @Override void run() throws Exception {
                try (OracleContainer source = OracleContainer.withTwoUsers();
                     CubridContainer target = CubridContainer.withEmptyDb()) {
                    source.start();
                    target.start();

                    OracleDatabaseInitializer.of(source)
                        .migrateAs(source.getRefUser(), source.getRefPassword(),
                                   "oracle/ref_schema")
                        .migrateAs(source.getMainUser(), source.getMainPassword(),
                                   "oracle/main_schema");

                    Path raw = runCmtScript(source, target, this);
                    Path sanitized = sanitizeXml(raw, source, target, this);
                    Files.move(sanitized, OUTPUT_BASE.resolve(id).resolve("script.xml"),
                        StandardCopyOption.REPLACE_EXISTING);
                }
            }
        },
        ORACLE_TO_DUMPFILE("oracle_to_dumpfile", "oracle/oracle_to_dumpfile") {
            @Override void run() throws Exception {
                try (OracleContainer source = OracleContainer.withTwoUsers()) {
                    source.start();

                    OracleDatabaseInitializer.of(source)
                        .migrateAs(source.getRefUser(), source.getRefPassword(),
                                   "oracle/ref_schema")
                        .migrateAs(source.getMainUser(), source.getMainPassword(),
                                   "oracle/main_schema");

                    Path raw = runCmtScript(source, null, this);
                    Path sanitized = sanitizeXml(raw, source, null, this);
                    Files.move(sanitized, OUTPUT_BASE.resolve(id).resolve("script.xml"),
                        StandardCopyOption.REPLACE_EXISTING);
                }
            }
        },
        CUBRID_TO_DUMPFILE("cubrid_to_dumpfile", "cubrid/cubrid_to_dumpfile") {
            @Override void run() throws Exception {
                try (CubridContainer source = CubridContainer.withEmptyDb()) {
                    source.start();

                    // Bootstrap the two-user e2e seed (REF_SCHEMA + MAIN_SCHEMA)
                    // before CMT inspects the source database.
                    String dbaUrl = source.getJdbcUrl("cubdb", "dba");
                    com.cmt.e2e.framework.db.init.ClasspathSqlRunner.runDirectory(
                        dbaUrl, "dba", "", "db/cubrid/init");

                    com.cmt.e2e.framework.db.init.DatabaseInitializer
                        .of(source, "cubdb", "REF_SCHEMA", "cmt")
                        .migrate("cubrid/ref_schema");
                    com.cmt.e2e.framework.db.init.DatabaseInitializer
                        .of(source, "cubdb", "MAIN_SCHEMA", "cmt")
                        .migrate("cubrid/main_schema");

                    Path raw = runCmtScript(source, null, this);
                    Path sanitized = sanitizeXml(raw, source, null, this);
                    Files.move(sanitized, OUTPUT_BASE.resolve(id).resolve("script.xml"),
                        StandardCopyOption.REPLACE_EXISTING);
                }
            }
        },
        CUBRID_TO_CUBRID("cubrid_to_cubrid", "cubrid/cubrid_to_cubrid") {
            @Override void run() throws Exception {
                try (CubridContainer source = CubridContainer.withEmptyDb();
                     CubridContainer target = CubridContainer.withEmptyDb()) {
                    source.start();
                    target.start();

                    // Bootstrap the two-user e2e seed on the source only;
                    // the target receives the schema via CMT online migration.
                    String dbaUrl = source.getJdbcUrl("cubdb", "dba");
                    com.cmt.e2e.framework.db.init.ClasspathSqlRunner.runDirectory(
                        dbaUrl, "dba", "", "db/cubrid/init");

                    com.cmt.e2e.framework.db.init.DatabaseInitializer
                        .of(source, "cubdb", "REF_SCHEMA", "cmt")
                        .migrate("cubrid/ref_schema");
                    com.cmt.e2e.framework.db.init.DatabaseInitializer
                        .of(source, "cubdb", "MAIN_SCHEMA", "cmt")
                        .migrate("cubrid/main_schema");

                    Path raw = runCmtScript(source, target, this);
                    Path sanitized = sanitizeXml(raw, source, target, this);
                    Files.move(sanitized, OUTPUT_BASE.resolve(id).resolve("script.xml"),
                        StandardCopyOption.REPLACE_EXISTING);
                }
            }
        },
        MYSQL_TO_CUBRID("mysql_to_cubrid", "mysql/mysql_to_cubrid") {
            @Override void run() throws Exception {
                try (MySqlContainer source = MySqlContainer.withMainUser();
                     CubridContainer target = CubridContainer.withEmptyDb()) {
                    source.start();
                    target.start();

                    // Container entrypoint already created main_schema database
                    // and main_user via init/00_prepare_database.sql.
                    MysqlDatabaseInitializer.of(source).migrateMain("mysql/main_schema");

                    Path raw = runCmtScript(source, target, this);
                    Path sanitized = sanitizeXml(raw, source, target, this);
                    Files.move(sanitized, OUTPUT_BASE.resolve(id).resolve("script.xml"),
                        StandardCopyOption.REPLACE_EXISTING);
                }
            }
        },
        MYSQL_TO_DUMPFILE("mysql_to_dumpfile", "mysql/mysql_to_dumpfile") {
            @Override void run() throws Exception {
                try (MySqlContainer source = MySqlContainer.withMainUser()) {
                    source.start();

                    MysqlDatabaseInitializer.of(source).migrateMain("mysql/main_schema");

                    Path raw = runCmtScript(source, null, this);
                    Path sanitized = sanitizeXml(raw, source, null, this);
                    Files.move(sanitized, OUTPUT_BASE.resolve(id).resolve("script.xml"),
                        StandardCopyOption.REPLACE_EXISTING);
                }
            }
        },
        MARIADB_TO_CUBRID("mariadb_to_cubrid", "mariadb/mariadb_to_cubrid") {
            @Override void run() throws Exception {
                try (MariaDbContainer source = MariaDbContainer.withMainUser();
                     CubridContainer target = CubridContainer.withEmptyDb()) {
                    source.start();
                    target.start();

                    MariadbDatabaseInitializer.of(source).migrateMain("mariadb/main_schema");

                    Path raw = runCmtScript(source, target, this);
                    Path sanitized = sanitizeXml(raw, source, target, this);
                    Files.move(sanitized, OUTPUT_BASE.resolve(id).resolve("script.xml"),
                        StandardCopyOption.REPLACE_EXISTING);
                }
            }
        },
        MARIADB_TO_DUMPFILE("mariadb_to_dumpfile", "mariadb/mariadb_to_dumpfile") {
            @Override void run() throws Exception {
                try (MariaDbContainer source = MariaDbContainer.withMainUser()) {
                    source.start();

                    MariadbDatabaseInitializer.of(source).migrateMain("mariadb/main_schema");

                    Path raw = runCmtScript(source, null, this);
                    Path sanitized = sanitizeXml(raw, source, null, this);
                    Files.move(sanitized, OUTPUT_BASE.resolve(id).resolve("script.xml"),
                        StandardCopyOption.REPLACE_EXISTING);
                }
            }
        },
        MSSQL_TO_CUBRID("mssql_to_cubrid", "mssql/mssql_to_cubrid") {
            @Override void run() throws Exception {
                try (MsSqlContainer source = MsSqlContainer.withMainUser();
                     CubridContainer target = CubridContainer.withEmptyDb()) {
                    source.start();
                    target.start();

                    MssqlDatabaseInitializer.of(source)
                        .migrateRef("mssql/ref_schema")
                        .migrateMain("mssql/main_schema");

                    Path raw = runCmtScript(source, target, this);
                    Path sanitized = sanitizeXml(raw, source, target, this);
                    Files.move(sanitized, OUTPUT_BASE.resolve(id).resolve("script.xml"),
                        StandardCopyOption.REPLACE_EXISTING);
                }
            }
        },
        MSSQL_TO_DUMPFILE("mssql_to_dumpfile", "mssql/mssql_to_dumpfile") {
            @Override void run() throws Exception {
                try (MsSqlContainer source = MsSqlContainer.withMainUser()) {
                    source.start();

                    MssqlDatabaseInitializer.of(source)
                        .migrateRef("mssql/ref_schema")
                        .migrateMain("mssql/main_schema");

                    Path raw = runCmtScript(source, null, this);
                    Path sanitized = sanitizeXml(raw, source, null, this);
                    Files.move(sanitized, OUTPUT_BASE.resolve(id).resolve("script.xml"),
                        StandardCopyOption.REPLACE_EXISTING);
                }
            }
        },
        INFORMIX_TO_CUBRID("informix_to_cubrid", "informix/informix_to_cubrid") {
            @Override void run() throws Exception {
                try (InformixContainer source = InformixContainer.withMainUser();
                     CubridContainer target = CubridContainer.withEmptyDb()) {
                    source.start();
                    target.start();

                    InformixDatabaseInitializer.of(source)
                        .migrateMain("informix/main_schema");

                    Path raw = runCmtScript(source, target, this);
                    Path sanitized = sanitizeXml(raw, source, target, this);
                    Files.move(sanitized, OUTPUT_BASE.resolve(id).resolve("script.xml"),
                        StandardCopyOption.REPLACE_EXISTING);
                }
            }
        },
        INFORMIX_TO_DUMPFILE("informix_to_dumpfile", "informix/informix_to_dumpfile") {
            @Override void run() throws Exception {
                try (InformixContainer source = InformixContainer.withMainUser()) {
                    source.start();

                    InformixDatabaseInitializer.of(source)
                        .migrateMain("informix/main_schema");

                    Path raw = runCmtScript(source, null, this);
                    Path sanitized = sanitizeXml(raw, source, null, this);
                    Files.move(sanitized, OUTPUT_BASE.resolve(id).resolve("script.xml"),
                        StandardCopyOption.REPLACE_EXISTING);
                }
            }
        };

        final String id;
        final String fixtureRel;

        Scenario(String id, String fixtureRel) {
            this.id = id;
            this.fixtureRel = fixtureRel;
        }

        Path fixtureDir() {
            return FIXTURE_BASE.resolve(fixtureRel);
        }

        abstract void run() throws Exception;
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
        Path runDir = OUTPUT_BASE.resolve(s.id);
        Files.createDirectories(runDir);
        Path rawDir = runDir.resolve("raw");
        recreateDirectory(rawDir);

        ScriptCommand.Builder builder = ScriptCommand.builder()
            .sourceConfig(SOURCE_CONFIG_NAME)
            .targetConfig(TARGET_CONFIG_NAME)
            .outputDir(rawDir.toAbsolutePath().toString());

        String home = System.getenv("CMT_CONSOLE_HOME");
        CommandRunner runner = new CommandRunner(new File(home));
        CommandResult result = runWithTemporaryDbConf(
            Paths.get(home),
            buildDbConf(s, source, target),
            () -> runner.run(builder.build()));

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

        content = replaceConnectionAttribute(content, "source", "host", "%%SOURCE_HOST%%");
        content = replaceConnectionAttribute(content, "source", "port", "%%SOURCE_PORT%%");
        content = replaceConnectionAttribute(content, "source", "driver", "%%SOURCE_DRIVER%%");

        if (target != null) {
            content = replaceConnectionAttribute(content, "target", "host", "%%TARGET_HOST%%");
            content = replaceConnectionAttribute(content, "target", "port", "%%TARGET_PORT%%");
            content = replaceConnectionAttribute(content, "target", "driver", "%%TARGET_DRIVER%%");
        }

        if (scenario == Scenario.INFORMIX_TO_CUBRID || scenario == Scenario.INFORMIX_TO_DUMPFILE) {
            // Informix sanitize step has two parts:
            //
            // (1) Drop the system DBA schema "INFORMIX" from the <schemas>
            //     list. CMT 's InformixSchemaFetcher.getSchemaNames pulls it
            //     in via JDBC getMetaData().getSchemas(), but the
            //     informix-owned system tables are not part of our seed.
            //     Leaving INFORMIX in the schemas list breaks record export
            //     downstream (MigrationConfiguration.buildTableCfg processes
            //     all listed source schemas; the INFORMIX entry pollutes
            //     SourceEntryTableConfig owner reconciliation, which results
            //     in every table-record lookup returning null with
            //     "Table X was not found").
            //
            // (2) Rewrite each source-side <table> schema="" attribute to
            //     schema="MAIN_USER". CMT 's InformixSchemaFetcher emits
            //     schema="" on <table> elements even though the catalog
            //     itself has the schema name; downstream
            //     MigrationConfiguration.getSrcTableSchema only treats
            //     schema==null as "use the default schema" — schema=="" is
            //     passed verbatim to Catalog.getSchemaByName(""), which
            //     returns null and triggers the same "Table not found".
            content = content.replaceAll(
                "\\s*<schema source=\"INFORMIX\" target=\"INFORMIX\"/>\\R",
                "");
            content = content.replaceAll(
                "(<table )schema=\"\"",
                "$1schema=\"MAIN_USER\"");
        }

        Path out = rawXml.resolveSibling("sanitized.xml");
        Files.writeString(out, content);
        return out;
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

    private static String buildDbConf(Scenario scenario, DatabaseContainer source, DatabaseContainer target) {
        StringBuilder conf = new StringBuilder();
        appendSourceConfig(conf, scenario, source);
        appendTargetConfig(conf, scenario, target);
        return conf.toString();
    }

    private static void appendSourceConfig(StringBuilder conf, Scenario scenario, DatabaseContainer source) {
        if (scenario == Scenario.ORACLE_TO_CUBRID || scenario == Scenario.ORACLE_TO_DUMPFILE) {
            OracleContainer oracle = (OracleContainer) source;
            appendProperty(conf, SOURCE_CONFIG_NAME + ".type", "oracle");
            appendProperty(conf, SOURCE_CONFIG_NAME + ".driver",
                Drivers.latest(source.getDbType()).toAbsolutePath().toString());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".host", oracle.getHost());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".port", oracle.getDatabasePort().toString());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".dbname", oracle.getSid());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".user", oracle.getMainUser());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".password", oracle.getMainPassword());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".charset", "AL32UTF8");
            appendProperty(conf, SOURCE_CONFIG_NAME + ".timezone", "GMT+00:00");
            return;
        }

        if (scenario == Scenario.CUBRID_TO_DUMPFILE || scenario == Scenario.CUBRID_TO_CUBRID) {
            // Connect as dba so CMT can discover both REF_SCHEMA and MAIN_SCHEMA
            // when scanning user objects of the e2e seed.
            appendProperty(conf, SOURCE_CONFIG_NAME + ".type", "cubrid");
            appendProperty(conf, SOURCE_CONFIG_NAME + ".driver",
                Drivers.latest(source.getDbType()).toAbsolutePath().toString());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".host", source.getHost());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".port", source.getDatabasePort().toString());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".dbname", "cubdb");
            appendProperty(conf, SOURCE_CONFIG_NAME + ".user", "dba");
            appendProperty(conf, SOURCE_CONFIG_NAME + ".password", "");
            appendProperty(conf, SOURCE_CONFIG_NAME + ".charset", "utf-8");
            return;
        }

        if (scenario == Scenario.MYSQL_TO_CUBRID || scenario == Scenario.MYSQL_TO_DUMPFILE) {
            // Connect as root so CMT can introspect both main_schema and ref_schema
            // databases. (main_user has table-level grants on ref_schema.e2e_ref_audit
            // but cannot enumerate the rest of the ref_schema namespace; root sees all.)
            MySqlContainer mysql = (MySqlContainer) source;
            appendProperty(conf, SOURCE_CONFIG_NAME + ".type", "mysql");
            appendProperty(conf, SOURCE_CONFIG_NAME + ".driver",
                Drivers.latest(source.getDbType()).toAbsolutePath().toString());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".host", source.getHost());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".port", source.getDatabasePort().toString());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".dbname", mysql.getMainDatabase());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".user", mysql.getRootUser());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".password", mysql.getRootPassword());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".charset", "utf-8");
            return;
        }

        if (scenario == Scenario.MARIADB_TO_CUBRID || scenario == Scenario.MARIADB_TO_DUMPFILE) {
            // Same single-database collapse as MySQL — connect as root so CMT
            // sees the full namespace. CMT 's MariaDB plugin uses db_type=mariadb
            // (separate from MySQL) which selects MariaDBSchemaFetcher.
            MariaDbContainer mariadb = (MariaDbContainer) source;
            appendProperty(conf, SOURCE_CONFIG_NAME + ".type", "mariadb");
            appendProperty(conf, SOURCE_CONFIG_NAME + ".driver",
                Drivers.latest(source.getDbType()).toAbsolutePath().toString());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".host", source.getHost());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".port", source.getDatabasePort().toString());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".dbname", mariadb.getMainDatabase());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".user", mariadb.getRootUser());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".password", mariadb.getRootPassword());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".charset", "utf-8");
            return;
        }

        if (scenario == Scenario.MSSQL_TO_CUBRID || scenario == Scenario.MSSQL_TO_DUMPFILE) {
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
            MsSqlContainer mssql = (MsSqlContainer) source;
            appendProperty(conf, SOURCE_CONFIG_NAME + ".type", "mssql");
            appendProperty(conf, SOURCE_CONFIG_NAME + ".driver",
                Drivers.latest(source.getDbType()).toAbsolutePath().toString());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".host", source.getHost());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".port", source.getDatabasePort().toString());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".dbname", mssql.getDatabaseName());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".user", mssql.getSaUser());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".password", mssql.getSaPassword());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".charset", "utf-8");
            return;
        }

        if (scenario == Scenario.INFORMIX_TO_CUBRID || scenario == Scenario.INFORMIX_TO_DUMPFILE) {
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
            InformixContainer informix = (InformixContainer) source;
            appendProperty(conf, SOURCE_CONFIG_NAME + ".type", "informix");
            appendProperty(conf, SOURCE_CONFIG_NAME + ".driver",
                Drivers.latest(source.getDbType()).toAbsolutePath().toString());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".host", source.getHost());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".port", source.getDatabasePort().toString());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".dbname", informix.getDatabaseName());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".user", informix.getMainUser());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".password", informix.getMainPassword());
            appendProperty(conf, SOURCE_CONFIG_NAME + ".charset", "utf-8");
            return;
        }

        throw new IllegalArgumentException("Unsupported scenario: " + scenario.id);
    }

    private static void appendTargetConfig(
            StringBuilder conf, Scenario scenario, DatabaseContainer target) {
        if (scenario == Scenario.ORACLE_TO_CUBRID
            || scenario == Scenario.CUBRID_TO_CUBRID
            || scenario == Scenario.MYSQL_TO_CUBRID
            || scenario == Scenario.MARIADB_TO_CUBRID
            || scenario == Scenario.MSSQL_TO_CUBRID
            || scenario == Scenario.INFORMIX_TO_CUBRID) {
            appendProperty(conf, TARGET_CONFIG_NAME + ".type", "cubrid");
            appendProperty(conf, TARGET_CONFIG_NAME + ".driver",
                Drivers.latest(target.getDbType()).toAbsolutePath().toString());
            appendProperty(conf, TARGET_CONFIG_NAME + ".host", target.getHost());
            appendProperty(conf, TARGET_CONFIG_NAME + ".port", target.getDatabasePort().toString());
            appendProperty(conf, TARGET_CONFIG_NAME + ".dbname", "cubdb");
            appendProperty(conf, TARGET_CONFIG_NAME + ".user", "dba");
            appendProperty(conf, TARGET_CONFIG_NAME + ".password", "");
            appendProperty(conf, TARGET_CONFIG_NAME + ".charset", "utf-8");
            appendProperty(conf, TARGET_CONFIG_NAME + ".add_schema", "yes");
            return;
        }

        appendProperty(conf, TARGET_CONFIG_NAME + ".type", "unload");
        appendProperty(conf, TARGET_CONFIG_NAME + ".output", "./output");
        appendProperty(conf, TARGET_CONFIG_NAME + ".charset", "utf-8");
        appendProperty(conf, TARGET_CONFIG_NAME + ".add_schema", "yes");
        appendProperty(conf, TARGET_CONFIG_NAME + ".split_schema", "yes");
        if (scenario == Scenario.ORACLE_TO_DUMPFILE) {
            appendProperty(conf, TARGET_CONFIG_NAME + ".file_prefix", "XE");
            appendProperty(conf, TARGET_CONFIG_NAME + ".one_table_one_file", "yes");
            return;
        }
        if (scenario == Scenario.CUBRID_TO_DUMPFILE) {
            appendProperty(conf, TARGET_CONFIG_NAME + ".file_prefix", "demodb");
            appendProperty(conf, TARGET_CONFIG_NAME + ".one_table_one_file", "no");
            return;
        }
        if (scenario == Scenario.MYSQL_TO_DUMPFILE) {
            appendProperty(conf, TARGET_CONFIG_NAME + ".file_prefix", "MYSQL");
            appendProperty(conf, TARGET_CONFIG_NAME + ".one_table_one_file", "no");
            return;
        }
        if (scenario == Scenario.MARIADB_TO_DUMPFILE) {
            appendProperty(conf, TARGET_CONFIG_NAME + ".file_prefix", "MARIADB");
            appendProperty(conf, TARGET_CONFIG_NAME + ".one_table_one_file", "no");
            return;
        }
        if (scenario == Scenario.MSSQL_TO_DUMPFILE) {
            appendProperty(conf, TARGET_CONFIG_NAME + ".file_prefix", "MSSQL");
            appendProperty(conf, TARGET_CONFIG_NAME + ".one_table_one_file", "no");
            return;
        }
        if (scenario == Scenario.INFORMIX_TO_DUMPFILE) {
            appendProperty(conf, TARGET_CONFIG_NAME + ".file_prefix", "INFORMIX");
            appendProperty(conf, TARGET_CONFIG_NAME + ".one_table_one_file", "no");
            return;
        }

        throw new IllegalArgumentException("Unsupported scenario: " + scenario.id);
    }

    private static void appendProperty(StringBuilder conf, String key, String value) {
        conf.append(key).append('=').append(value == null ? "" : value).append('\n');
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
        return content.replaceAll(pattern, "$1" + java.util.regex.Matcher.quoteReplacement(replacement) + "$3");
    }

    private static String replaceTagAttribute(
            String content, String tagName, String attribute, String replacement) {
        String pattern = "(<" + tagName + "\\b[^>]*\\b" + attribute + "=\")([^\"]*)(\")";
        return content.replaceAll(pattern, "$1" + java.util.regex.Matcher.quoteReplacement(replacement) + "$3");
    }

    private static String extractAttribute(String content, String tagName, String attribute) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
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
