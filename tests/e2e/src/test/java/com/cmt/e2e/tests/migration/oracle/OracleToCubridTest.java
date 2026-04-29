package com.cmt.e2e.tests.migration.oracle;

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
import com.cmt.e2e.framework.db.containers.OracleContainer;
import com.cmt.e2e.framework.db.init.OracleDatabaseInitializer;
import com.cmt.e2e.framework.junit.TestResources;
import com.cmt.e2e.framework.template.ResolvedScript;
import com.cmt.e2e.framework.template.ScriptTemplateResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.cmt.e2e.framework.assertion.DatabaseAsserts.row;
import static com.cmt.e2e.framework.assertion.CubridMetadataAsserts.clazz;
import static com.cmt.e2e.framework.assertion.CubridMetadataAsserts.column;
import static com.cmt.e2e.framework.assertion.CubridMetadataAsserts.grant;
import static com.cmt.e2e.framework.assertion.CubridMetadataAsserts.index;
import static com.cmt.e2e.framework.assertion.CubridMetadataAsserts.indexKey;
import static com.cmt.e2e.framework.assertion.CubridMetadataAsserts.routine;
import static com.cmt.e2e.framework.assertion.CubridMetadataAsserts.serial;
import static com.cmt.e2e.framework.assertion.CubridMetadataAsserts.synonym;

/**
 * E2E test for Oracle 11g (two users, e2e dataset)
 * -> CUBRID online migration.
 *
 * <p>The assertions intentionally combine row counts, representative data, and
 * selected CUBRID catalog metadata so the test catches both data loss and
 * schema-object regressions.
 *
 * <p><b>Note (after dataset refresh):</b> some metadata assertions
 * (index naming, serial current value, grants, etc.) depend on how CMT
 * renames objects when migrating Oracle -> CUBRID. The values below are the
 * best-known starting point for the new dataset; expect to reconcile
 * mismatches against the first successful run rather than treating the
 * initial assertions as ground truth.
 */
@Testcontainers
@DisplayName("ORA-ON-01: Oracle e2e dataset -> CUBRID online migration")
public class OracleToCubridTest {

    @RegisterExtension
    final CmtTestContext ctx = new CmtTestContext();

    @Container
    private final OracleContainer sourceDb = OracleContainer.withTwoUsers();

    @Container
    private final DatabaseContainer targetDb = CubridContainer.withEmptyDb();

    @Test
    @TestResources("migration/oracle/oracle_to_cubrid")
    @DisplayName("migrates MAIN_SCHEMA + REF_SCHEMA into CUBRID")
    void should_migrateToCubrid_when_sourceIsOracleOnline() throws Exception {
        // Arrange: initialize two-user Oracle and resolve the script template
        OracleDatabaseInitializer.of(sourceDb)
            .migrateAs(sourceDb.getRefUser(),  sourceDb.getRefPassword(),  "oracle/ref_schema")
            .migrateAs(sourceDb.getMainUser(), sourceDb.getMainPassword(), "oracle/main_schema");

        ResolvedScript resolved = ScriptTemplateResolver.builder()
            .template(ctx.testPaths().getResourceDir().resolve("script.xml"))
            .source(sourceDb)
            .target(targetDb)
            .outputDir(ctx.testPaths().getArtifactDir())
            .scriptFileName("Oracle_to_CUBRID.xml")
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
        assertMigratedMetadata();
    }

    /**
     * Verifies row count per migrated table.
     * Counts come from the e2e dataset:
     *   - REF_SCHEMA.e2e_ref_audit         : 1 row
     *   - MAIN_SCHEMA business graph       : customer 4 / order 4 / order_line 4 / employee 3
     *   - MAIN_SCHEMA type test tables     : text 6 / numeric 6 / temporal 5 / binary 5
     *   - MAIN_SCHEMA Oracle extension     : oracle_locator_types 1
     */
    private void assertMigratedRows() {
        DatabaseAsserts.expectRecords(targetDb, "e2e_db", "REF_SCHEMA", Map.of(
            "e2e_ref_audit", 1
        ));
        DatabaseAsserts.expectRecords(targetDb, "e2e_db", "MAIN_SCHEMA", Map.ofEntries(
            Map.entry("e2e_customer",             4),
            Map.entry("e2e_order",                4),
            Map.entry("e2e_order_line",           4),
            Map.entry("e2e_employee",             3),
            Map.entry("e2e_text_types",           6),
            Map.entry("e2e_numeric_types",        6),
            Map.entry("e2e_temporal_types",       5),
            Map.entry("e2e_binary_types",         5),
            Map.entry("e2e_oracle_locator_types", 1)
        ));
    }

    /**
     * Verifies CUBRID catalog state after migration.
     *
     * <p>The values below mirror the expected CMT output for the new e2e
     * dataset. Index/PK names that CMT auto-derives ({@code pk_<table>_<col>}
     * style for migrated PKs etc.) are best-guess and may need to be
     * reconciled against the first run.
     */
    private void assertMigratedMetadata() {
        // Order matches the catalog query's ORDER BY owner_name, class_type, class_name:
        // MAIN_SCHEMA (alphabetically first) → CLASS rows → VCLASS row, then REF_SCHEMA.
        CubridMetadataAsserts.expectClasses(targetDb, "e2e_db", List.of(
            clazz("MAIN_SCHEMA", "e2e_binary_types", "CLASS"),
            clazz("MAIN_SCHEMA", "e2e_customer", "CLASS"),
            clazz("MAIN_SCHEMA", "e2e_employee", "CLASS"),
            clazz("MAIN_SCHEMA", "e2e_numeric_types", "CLASS"),
            clazz("MAIN_SCHEMA", "e2e_oracle_locator_types", "CLASS"),
            clazz("MAIN_SCHEMA", "e2e_order", "CLASS"),
            clazz("MAIN_SCHEMA", "e2e_order_line", "CLASS"),
            clazz("MAIN_SCHEMA", "e2e_temporal_types", "CLASS"),
            clazz("MAIN_SCHEMA", "e2e_text_types", "CLASS"),
            clazz("MAIN_SCHEMA", "e2e_order_summary_v", "VCLASS"),
            clazz("REF_SCHEMA", "e2e_ref_audit", "CLASS")
        ));

        // Order matches the catalog query's ORDER BY class_name, def_order:
        // e2e_customer → e2e_order → e2e_order_line → e2e_temporal_types alphabetically.
        CubridMetadataAsserts.expectColumns(targetDb, "e2e_db", "MAIN_SCHEMA", List.of(
            column("e2e_customer", "customer_id", 0, "NUMERIC", 10, 0, "NO"),
            column("e2e_customer", "customer_code", 1, "CHAR", 4, 0, "NO"),
            column("e2e_customer", "customer_name", 2, "STRING", 100, 0, "NO"),
            column("e2e_customer", "customer_alias", 3, "STRING", 60, 0, "YES"),
            column("e2e_customer", "status", 4, "CHAR", 1, 0, "YES"),
            column("e2e_customer", "credit_limit", 5, "NUMERIC", 15, 2, "YES"),
            column("e2e_customer", "created_on", 6, "DATETIME", 23, 3, "YES"),
            column("e2e_customer", "updated_on", 7, "DATETIME", 23, 3, "YES"),
            column("e2e_order", "order_id", 0, "NUMERIC", 10, 0, "NO"),
            column("e2e_order", "customer_id", 1, "NUMERIC", 10, 0, "NO"),
            column("e2e_order", "order_no", 2, "STRING", 30, 0, "YES"),
            column("e2e_order", "order_status", 3, "STRING", 20, 0, "YES"),
            column("e2e_order", "total_amount", 4, "NUMERIC", 18, 2, "YES"),
            column("e2e_order", "ordered_at", 5, "DATETIME", 23, 3, "YES"),
            column("e2e_order", "settled_at", 6, "DATETIME", 23, 3, "YES"),
            column("e2e_order", "source_comment", 7, "STRING", 200, 0, "YES"),
            column("e2e_order_line", "order_id", 0, "NUMERIC", 10, 0, "NO"),
            column("e2e_order_line", "line_no", 1, "NUMERIC", 5, 0, "NO"),
            column("e2e_order_line", "sku", 2, "STRING", 30, 0, "YES"),
            column("e2e_order_line", "qty", 3, "NUMERIC", 10, 0, "YES"),
            column("e2e_order_line", "unit_price", 4, "NUMERIC", 15, 2, "YES"),
            column("e2e_order_line", "line_note", 5, "STRING", 200, 0, "YES"),
            column("e2e_temporal_types", "id", 0, "NUMERIC", 10, 0, "NO"),
            column("e2e_temporal_types", "date_col", 1, "DATETIME", 23, 3, "YES"),
            column("e2e_temporal_types", "ts6_col", 2, "TIMESTAMP", 19, 0, "YES"),
            column("e2e_temporal_types", "ts9_col", 3, "TIMESTAMP", 19, 0, "YES"),
            column("e2e_temporal_types", "tsltz6_col", 4, "DATETIME", 23, 3, "YES"),
            column("e2e_temporal_types", "tstz9_col", 5, "STRING", 100, 0, "YES"),
            column("e2e_temporal_types", "interval_ds_col", 6, "STRING", 255, 0, "YES"),
            column("e2e_temporal_types", "interval_ym_col", 7, "STRING", 255, 0, "YES")
        ));

        // CMT renames migrated PKs to include the indexed column(s):
        //   pk_e2e_customer        -> pk_e2e_customer_customer_id
        //   pk_e2e_order_line      -> pk_e2e_order_line_order_id_line_no  (composite)
        // Order matches catalog query's ORDER BY class_name, index_name (alphabetical).
        CubridMetadataAsserts.expectIndexes(targetDb, "e2e_db", "MAIN_SCHEMA", List.of(
            index("e2e_customer", "pk_e2e_customer_customer_id", true, true, false, 1, false),
            index("e2e_customer", "uk_e2e_customer_code", true, false, false, 1, false),
            index("e2e_employee", "fk_e2e_employee_manager", false, false, true, 1, false),
            index("e2e_employee", "pk_e2e_employee_employee_id", true, true, false, 1, false),
            index("e2e_order", "fk_e2e_order_customer", false, false, true, 1, false),
            index("e2e_order", "idx_e2e_order_customer", false, false, false, 1, false),
            index("e2e_order", "idxd_e2e_order_ordered_at", false, false, false, 1, false),
            index("e2e_order", "idxf_e2e_order_upper_status", false, false, false, 1, true),
            index("e2e_order", "pk_e2e_order_order_id", true, true, false, 1, false),
            index("e2e_order_line", "fk_e2e_order_line_order", false, false, true, 1, false),
            index("e2e_order_line", "pk_e2e_order_line_order_id_line_no", true, true, false, 2, false)
        ));

        CubridMetadataAsserts.expectIndexKeys(targetDb, "e2e_db", "MAIN_SCHEMA", List.of(
            indexKey("e2e_customer", "pk_e2e_customer_customer_id", "customer_id", 0, "ASC"),
            indexKey("e2e_customer", "uk_e2e_customer_code", "customer_code", 0, "ASC"),
            indexKey("e2e_employee", "fk_e2e_employee_manager", "manager_id", 0, "ASC"),
            indexKey("e2e_employee", "pk_e2e_employee_employee_id", "employee_id", 0, "ASC"),
            indexKey("e2e_order", "fk_e2e_order_customer", "customer_id", 0, "ASC"),
            indexKey("e2e_order", "idx_e2e_order_customer", "customer_id", 0, "ASC"),
            indexKey("e2e_order", "idxd_e2e_order_ordered_at", "ordered_at", 0, "DESC"),
            indexKey("e2e_order", "pk_e2e_order_order_id", "order_id", 0, "ASC"),
            indexKey("e2e_order_line", "fk_e2e_order_line_order", "order_id", 0, "ASC"),
            indexKey("e2e_order_line", "pk_e2e_order_line_order_id_line_no", "order_id", 0, "ASC"),
            indexKey("e2e_order_line", "pk_e2e_order_line_order_id_line_no", "line_no", 1, "ASC")
        ));

        // Sequence current_val mirrors Oracle's start value (5) per
        // oracle/SEED_SPEC.md §4.1; CMT preserves it as the next-to-be-issued value.
        CubridMetadataAsserts.expectSerials(targetDb, "e2e_db", List.of(
            serial("e2e_customer_seq", "5", "1", "1"),
            serial("e2e_order_seq", "5", "1", "1")
        ));
        CubridMetadataAsserts.expectSynonyms(targetDb, "e2e_db", List.of(
            synonym("MAIN_SCHEMA", "e2e_ref_audit_syn", "REF_SCHEMA", "e2e_ref_audit")
        ));
        CubridMetadataAsserts.expectGrants(targetDb, "e2e_db", List.of(
            grant("REF_SCHEMA", "MAIN_SCHEMA", "CLASS", "e2e_ref_audit", "REF_SCHEMA", "DELETE", "NO"),
            grant("REF_SCHEMA", "MAIN_SCHEMA", "CLASS", "e2e_ref_audit", "REF_SCHEMA", "INSERT", "NO"),
            grant("REF_SCHEMA", "MAIN_SCHEMA", "CLASS", "e2e_ref_audit", "REF_SCHEMA", "SELECT", "NO"),
            grant("REF_SCHEMA", "MAIN_SCHEMA", "CLASS", "e2e_ref_audit", "REF_SCHEMA", "UPDATE", "NO")
        ));
        CubridMetadataAsserts.expectRoutines(targetDb, "e2e_db", List.of(
            routine("MAIN_SCHEMA", "e2e_customer_label_fn", "FUNCTION", "DEFINER"),
            routine("MAIN_SCHEMA", "e2e_upsert_customer_proc", "PROCEDURE", "DEFINER")
        ));
    }

    /**
     * Spot-checks a representative row in each migrated table to catch value
     * corruption. The row IDs target deterministic non-NULL rows from the
     * e2e dataset — see SEED_SPEC §5 for the canonical battery rows.
     */
    private void assertRepresentativeData() {
        DatabaseAsserts.expectQueryResults(targetDb, "e2e_db", "MAIN_SCHEMA", List.of(
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
                "text type R_MIN row (id=3)",
                """
                SELECT char_byte_col, varchar_char_col, nchar_col, nvarchar_col
                FROM e2e_text_types
                WHERE id = 3
                """,
                row("A  ", "A", "A         ", "A")
            ),
            QueryExpectation.of(
                "numeric type R_REPRESENTATIVE row (id=6)",
                """
                SELECT integer_col, decimal_col, number_ps_col
                FROM e2e_numeric_types
                WHERE id = 6
                """,
                row("7", "100.2500", "42.424242")
            ),
            QueryExpectation.of(
                "temporal date R_EPOCH row (id=2)",
                """
                SELECT date_col
                FROM e2e_temporal_types
                WHERE id = 2
                """,
                row("1970-01-01 00:00:00.000")
            ),
            QueryExpectation.of(
                "view is queryable (4 orders in dataset)",
                """
                SELECT COUNT(*)
                FROM e2e_order_summary_v
                """,
                row("4")
            ),
            QueryExpectation.of(
                "ref_audit synonym access",
                """
                SELECT COUNT(*)
                FROM e2e_ref_audit_syn
                """,
                row("1")
            )
        ));
    }
}
