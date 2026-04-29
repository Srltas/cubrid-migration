package com.cmt.e2e.tests.migration.mariadb;

import java.util.List;
import java.util.Map;

import com.cmt.e2e.framework.assertion.CubridMetadataAsserts;
import com.cmt.e2e.framework.assertion.DatabaseAsserts;
import com.cmt.e2e.framework.assertion.DatabaseAsserts.QueryExpectation;
import com.cmt.e2e.framework.assertion.MigrationAsserts;
import com.cmt.e2e.framework.command.CommandResult;
import com.cmt.e2e.framework.command.StartCommand;
import com.cmt.e2e.framework.core.CmtTestContext;
import com.cmt.e2e.framework.db.containers.CubridContainer;
import com.cmt.e2e.framework.db.containers.DatabaseContainer;
import com.cmt.e2e.framework.db.containers.MariaDbContainer;
import com.cmt.e2e.framework.db.init.MariadbDatabaseInitializer;
import com.cmt.e2e.framework.core.TestResources;
import com.cmt.e2e.framework.template.ResolvedScript;
import com.cmt.e2e.framework.template.ScriptTemplateResolver;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.cmt.e2e.framework.assertion.CubridMetadataAsserts.clazz;
import static com.cmt.e2e.framework.assertion.DatabaseAsserts.row;

/**
 * E2E test for MariaDB 11 (e2e dataset, single-database) -> CUBRID online migration.
 *
 * <p>Like MySQL the seed has only a {@code main_schema} database (cross-schema
 * GRANT/SYNONYM are anti-coverage — see {@code docs/seed/mariadb/SEED_SPEC.md}
 * §1). Unlike MySQL the seed includes view, routines, SET, and JSON because
 * the CMT MariaDB plugin handles those differently than the MySQL one (notably
 * MariaDB still has the legacy {@code mysql.proc} system table that lets
 * {@code MariaDBSchemaFetcher.buildProcedures} succeed). The first run of this
 * test confirmed which "tentative" SPEC items survive and pinned them.
 *
 * <p>Schema name in the CUBRID target is {@code ROOT} because CMT 's MariaDB
 * fetcher uses the source connection user (root) as the schema namespace —
 * same single-namespace collapse seen in MySQL.
 */
@Disabled("DEFERRED — see docs/seed/mariadb/SEED_SPEC.md §0. MariaDB plugin "
    + "은 MySQL plugin 의 결함을 그대로 상속받는다. 해제는 MySQL fix 와 동시 진행.")
@Testcontainers
@DisplayName("MAR-ON-01: MariaDB e2e dataset -> CUBRID online migration")
public class MariadbToCubridTest {

    @RegisterExtension
    final CmtTestContext ctx = new CmtTestContext();

    @Container
    private final MariaDbContainer sourceDb = MariaDbContainer.withMainUser();

    @Container
    private final DatabaseContainer targetDb = CubridContainer.withEmptyDb();

    @Test
    @TestResources("migration/mariadb/mariadb_to_cubrid")
    @DisplayName("migrates main_schema into CUBRID")
    void should_migrateToCubrid_when_sourceIsMariadbE2eSeed() throws Exception {
        // Arrange: container entrypoint already created main_schema + main_user.
        MariadbDatabaseInitializer.of(sourceDb).migrateMain("mariadb/main_schema");

        ResolvedScript resolved = ScriptTemplateResolver.builder()
            .template(ctx.testPaths().getResourceDir().resolve("script.xml"))
            .source(sourceDb)
            .target(targetDb)
            .outputDir(ctx.testPaths().getArtifactDir())
            .scriptFileName("MariaDB_to_CUBRID.xml")
            .build()
            .resolve();

        // Act
        CommandResult result = ctx.commandRunner().run(
            StartCommand.builder().script(resolved.scriptPath()).build());

        // Assert
        MigrationAsserts.assertMigrationSucceeded(result);
        MigrationAsserts.assertNoFatalStderr(result);

        assertMigratedRows();
        assertRepresentativeData();
        assertMigratedClasses();
    }

    /**
     * Counts come from SEED_SPEC §3 / §5. Tentative items (view / SET / JSON)
     * are included optimistically; if the first run shows any of them dropped
     * by CMT, the corresponding row will be pinned to 0 (or the line removed)
     * with a SPEC update.
     */
    private void assertMigratedRows() {
        DatabaseAsserts.expectRecords(targetDb, "e2e_db", "ROOT", Map.ofEntries(
            Map.entry("e2e_customer",            4),
            Map.entry("e2e_order",               4),
            Map.entry("e2e_order_line",          4),
            Map.entry("e2e_employee",            3),
            Map.entry("e2e_text_types",          6),
            Map.entry("e2e_numeric_types",       6),
            Map.entry("e2e_temporal_types",      5),
            Map.entry("e2e_binary_types",        5),
            Map.entry("e2e_mariadb_enum_types",  4),
            Map.entry("e2e_mariadb_json_types",  5)
        ));
    }

    /** Sanity-check expected CUBRID classes. */
    private void assertMigratedClasses() {
        CubridMetadataAsserts.expectClasses(targetDb, "e2e_db", List.of(
            clazz("ROOT", "e2e_binary_types", "CLASS"),
            clazz("ROOT", "e2e_customer", "CLASS"),
            clazz("ROOT", "e2e_employee", "CLASS"),
            clazz("ROOT", "e2e_mariadb_enum_types", "CLASS"),
            clazz("ROOT", "e2e_mariadb_json_types", "CLASS"),
            clazz("ROOT", "e2e_numeric_types", "CLASS"),
            clazz("ROOT", "e2e_order", "CLASS"),
            clazz("ROOT", "e2e_order_line", "CLASS"),
            clazz("ROOT", "e2e_temporal_types", "CLASS"),
            clazz("ROOT", "e2e_text_types", "CLASS")
        ));
    }

    /** Spot-check a representative row in each migrated table. */
    private void assertRepresentativeData() {
        DatabaseAsserts.expectQueryResults(targetDb, "e2e_db", "ROOT", List.of(
            QueryExpectation.of(
                "customer business values",
                """
                SELECT customer_code, customer_name, customer_alias, status, credit_limit
                FROM e2e_customer
                WHERE customer_id = 1
                """,
                row("C001", "ALPHA CUSTOMER", "Alpha Alias", "A", "12500.75")
            ),
            QueryExpectation.of(
                "order-line composite-key relationship",
                """
                SELECT c.customer_code, o.order_no, l.sku, l.qty, l.unit_price
                FROM e2e_customer c
                JOIN e2e_order o ON o.customer_id = c.customer_id
                JOIN e2e_order_line l ON l.order_id = o.order_id
                WHERE l.order_id = 1 AND l.line_no = 1
                """,
                row("C001", "ORD-2024-001", "SKU-ALPHA", "2", "25.00")
            ),
            QueryExpectation.of(
                "employee self-reference (Manager A reports to CEO)",
                """
                SELECT e.emp_name, m.emp_name AS manager_name
                FROM e2e_employee e
                JOIN e2e_employee m ON m.employee_id = e.manager_id
                WHERE e.employee_id = 2
                """,
                row("Manager A", "CEO")
            ),
            QueryExpectation.of(
                "enum value migrates intact",
                """
                SELECT status_enum
                FROM e2e_mariadb_enum_types
                WHERE id = 2
                """,
                row("NEW")
            )
        ));
    }
}
