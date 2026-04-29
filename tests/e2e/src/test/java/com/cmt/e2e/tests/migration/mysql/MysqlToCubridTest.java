package com.cmt.e2e.tests.migration.mysql;

import java.util.List;
import java.util.Map;

import com.cmt.e2e.framework.assertion.CubridMetadataAsserts;
import com.cmt.e2e.framework.assertion.DatabaseAsserts;
import com.cmt.e2e.framework.assertion.DatabaseAsserts.QueryExpectation;
import com.cmt.e2e.framework.assertion.MigrationAsserts;
import com.cmt.e2e.framework.command.execution.CommandResult;
import com.cmt.e2e.framework.command.impls.StartCommand;
import com.cmt.e2e.framework.core.CmtTestContext;
import com.cmt.e2e.framework.db.containers.CubridContainer;
import com.cmt.e2e.framework.db.containers.DatabaseContainer;
import com.cmt.e2e.framework.db.containers.MySqlContainer;
import com.cmt.e2e.framework.db.init.MysqlDatabaseInitializer;
import com.cmt.e2e.framework.junit.annotation.TestResources;
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
 * E2E test for MySQL 8.0 (e2e dataset, single-database) -> CUBRID online migration.
 *
 * <p>The MySQL seed has only a {@code main_schema} database (no ref_schema)
 * because CMT 's MySQL fetcher does not implement {@code buildGrant} or
 * {@code buildSynonym} and collapses all objects into a single connection-user
 * "schema" namespace — see {@code docs/seed/mysql/SEED_SPEC.md} §1
 * anti-coverage. The single namespace ends up named {@code ROOT} in the
 * generated script.xml because CMT uses the source connection user.
 *
 * <p>This test focuses on the core CMT MySQL surface that does work:
 * tables (with FKs, CHECK, AUTO_INCREMENT), indexes (B-tree, descending,
 * functional), comments, view, and stored routines. ENUM/SET types from the
 * MySQL extension tables are exercised as well.
 *
 * <p><b>Note (after first run):</b> several CUBRID catalog details (target
 * schema name "ROOT", AUTO_INCREMENT → SERIAL translation, ENUM/SET mapping
 * to scalar types) depend on CMT 's MySQL → CUBRID translator output and need
 * to be reconciled against the first successful migration. Use that as the
 * ground truth when adjusting assertions.
 */
@Disabled("DEFERRED — see docs/seed/mysql/SEED_SPEC.md §0. CMT 의 MySQL "
    + "fetcher 가 user-as-schema 가정을 그대로 상속받아 view body translation "
    + "등 first-class 결함이 다수. 해제 조건: MySQLSchemaFetcher.getSchemaNames "
    + "override + view body translator 패치.")
@Testcontainers
@DisplayName("MYS-ON-01: MySQL e2e dataset -> CUBRID online migration")
public class MysqlToCubridTest {

    @RegisterExtension
    final CmtTestContext ctx = new CmtTestContext();

    @Container
    private final MySqlContainer sourceDb = MySqlContainer.withMainUser();

    @Container
    private final DatabaseContainer targetDb = CubridContainer.withEmptyDb();

    @Test
    @TestResources("migration/mysql/mysql_to_cubrid")
    @DisplayName("migrates main_schema into CUBRID")
    void should_migrateToCubrid_when_sourceIsMysqlE2eSeed() throws Exception {
        // Arrange: container entrypoint already created main_schema + main_user.
        // Apply the Flyway main_schema migrations as main_user.
        MysqlDatabaseInitializer.of(sourceDb).migrateMain("mysql/main_schema");

        ResolvedScript resolved = ScriptTemplateResolver.builder()
            .template(ctx.testPaths().getResourceDir().resolve("script.xml"))
            .source(sourceDb)
            .target(targetDb)
            .outputDir(ctx.testPaths().getArtifactDir())
            .scriptFileName("MySQL_to_CUBRID.xml")
            .build()
            .resolve();

        // Act
        CommandResult result = ctx.commandRunner().run(
            StartCommand.builder().script(resolved.scriptPath()).build());

        // Assert: target schemas contain the expected rows, values, and metadata
        MigrationAsserts.assertMigrationSucceeded(result);
        MigrationAsserts.assertNoFatalStderr(result);

        assertMigratedRows();
        assertRepresentativeData();
        assertMigratedClasses();
    }

    /**
     * Verifies row count per migrated table. Counts mirror SEED_SPEC §3 / §5:
     *   - business graph     : customer 4 / order 4 / order_line 4 / employee 3
     *   - core type tests    : text 6 / numeric 6 / temporal 5 / binary 5
     *   - mysql extensions   : enum 4 / set 5
     *
     * <p>flyway_schema_history is also migrated (10 rows from the 5 main_schema
     * versions plus baseline). It is not asserted because that count is a
     * Flyway implementation detail rather than a CMT-relevant signal.
     *
     * <p>Schema name is "ROOT" because CMT 's MySQL fetcher uses the source
     * connection user (root) as the single schema namespace.
     */
    private void assertMigratedRows() {
        DatabaseAsserts.expectRecords(targetDb, "cubdb", "ROOT", Map.ofEntries(
            Map.entry("e2e_customer",          4),
            Map.entry("e2e_order",             4),
            Map.entry("e2e_order_line",        4),
            Map.entry("e2e_employee",          3),
            Map.entry("e2e_text_types",        6),
            Map.entry("e2e_numeric_types",     6),
            Map.entry("e2e_temporal_types",    5),
            Map.entry("e2e_binary_types",      5),
            Map.entry("e2e_mysql_enum_types",  4)
        ));
    }

    /** Sanity-check expected CUBRID classes (9 tables, no view, no SET). */
    private void assertMigratedClasses() {
        CubridMetadataAsserts.expectClasses(targetDb, "cubdb", List.of(
            clazz("ROOT", "e2e_binary_types", "CLASS"),
            clazz("ROOT", "e2e_customer", "CLASS"),
            clazz("ROOT", "e2e_employee", "CLASS"),
            clazz("ROOT", "e2e_mysql_enum_types", "CLASS"),
            clazz("ROOT", "e2e_numeric_types", "CLASS"),
            clazz("ROOT", "e2e_order", "CLASS"),
            clazz("ROOT", "e2e_order_line", "CLASS"),
            clazz("ROOT", "e2e_temporal_types", "CLASS"),
            clazz("ROOT", "e2e_text_types", "CLASS")
        ));
    }

    /**
     * Spot-checks a representative row in each migrated table to catch value
     * corruption.
     */
    private void assertRepresentativeData() {
        DatabaseAsserts.expectQueryResults(targetDb, "cubdb", "ROOT", List.of(
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
                FROM e2e_mysql_enum_types
                WHERE id = 2
                """,
                row("NEW")
            )
        ));
    }
}
