package com.cmt.e2e.tests.migration.cubrid;

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
import com.cmt.e2e.framework.db.init.ClasspathSqlRunner;
import com.cmt.e2e.framework.db.init.DatabaseInitializer;
import com.cmt.e2e.framework.junit.annotation.TestResources;
import com.cmt.e2e.framework.template.ResolvedScript;
import com.cmt.e2e.framework.template.ScriptTemplateResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.cmt.e2e.framework.assertion.CubridMetadataAsserts.clazz;
import static com.cmt.e2e.framework.assertion.CubridMetadataAsserts.column;
import static com.cmt.e2e.framework.assertion.CubridMetadataAsserts.grant;
import static com.cmt.e2e.framework.assertion.CubridMetadataAsserts.index;
import static com.cmt.e2e.framework.assertion.CubridMetadataAsserts.indexKey;
import static com.cmt.e2e.framework.assertion.CubridMetadataAsserts.serial;
import static com.cmt.e2e.framework.assertion.CubridMetadataAsserts.synonym;
import static com.cmt.e2e.framework.assertion.DatabaseAsserts.row;

/**
 * E2E test for CUBRID (e2e seed: REF_SCHEMA + MAIN_SCHEMA)
 * -> CUBRID online migration.
 *
 * <p>Source seed lives in {@code src/test/resources/db/cubrid/} and is
 * documented in {@code docs/seed/cubrid/SEED_SPEC.md}. The test bootstraps it
 * on the source container only:
 * <ol>
 *   <li>{@code init/00_prepare_database.sql} as {@code dba} (creates the
 *       {@code MAIN_SCHEMA} and {@code REF_SCHEMA} users).</li>
 *   <li>{@code ref_schema/V*.sql} as {@code REF_SCHEMA}.</li>
 *   <li>{@code main_schema/V*.sql} as {@code MAIN_SCHEMA}.</li>
 * </ol>
 *
 * <p>The target is an empty CUBRID instance — CMT creates the users, schema
 * objects, and rows online. Assertions blend row counts, representative data,
 * and CUBRID catalog metadata so the test catches both data loss and
 * schema-object regressions.
 *
 * <p><b>Note:</b> CUBRID-only types (collection / enum / json) round-trip
 * without translation, so the type-test tables are visible end-to-end. The
 * metadata assertions below are the best-known starting point; expect to
 * reconcile mismatches against the first successful run rather than treating
 * them as ground truth.
 */
@Tag("db-required")
@Testcontainers
@DisplayName("CUB-ON-01: CUBRID e2e dataset -> CUBRID online migration")
public class CubridToCubridTest {

    @RegisterExtension
    final CmtTestContext ctx = CmtTestContext.builder().build();

    @Container
    private final DatabaseContainer sourceDb = CubridContainer.withEmptyDb();

    @Container
    private final DatabaseContainer targetDb = CubridContainer.withEmptyDb();

    @Test
    @TestResources("migration/cubrid/cubrid_to_cubrid")
    @DisplayName("migrates MAIN_SCHEMA + REF_SCHEMA into another CUBRID instance")
    void should_migrateToCubrid_when_sourceIsCubridE2eSeed() throws Exception {
        // Arrange: bootstrap the two-user e2e seed on the source.
        CubridContainer source = (CubridContainer) sourceDb;
        String dbaUrl = source.getJdbcUrl("cubdb", "dba");
        ClasspathSqlRunner.runDirectory(dbaUrl, "dba", "", "db/cubrid/init");
        DatabaseInitializer.of(source, "cubdb", "REF_SCHEMA", "cmt").migrate("cubrid/ref_schema");
        DatabaseInitializer.of(source, "cubdb", "MAIN_SCHEMA", "cmt").migrate("cubrid/main_schema");

        ResolvedScript resolved = ScriptTemplateResolver.builder()
            .template(ctx.testPaths().getResourceDir().resolve("script.xml"))
            .source(sourceDb)
            .target(targetDb)
            .outputDir(ctx.testPaths().getArtifactDir())
            .scriptFileName("CUBRID_to_CUBRID.xml")
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
     * Verifies row count per migrated table. Counts come from the CUBRID e2e
     * dataset (docs/seed/cubrid/SEED_SPEC.md):
     *   - REF_SCHEMA.e2e_ref_audit             : 1 row
     *   - MAIN_SCHEMA business graph           : customer 4 / order 4 /
     *                                            order_line 4 / employee 3
     *   - MAIN_SCHEMA core type test tables    : text 6 / numeric 6 /
     *                                            temporal 5 / binary 5
     *   - MAIN_SCHEMA CUBRID-only extensions   : collection 0 (data skipped) /
     *                                            enum 4 / json 5
     *
     * <p>{@code e2e_cubrid_collection_types} is created on the target but its
     * rows are intentionally skipped — see the comment in script.xml: CMT's
     * online JDBC importer cannot batch CUBRID SET/MULTISET/SEQUENCE values,
     * so {@code migrate_data="no"} prevents a whole-batch failure. Collection
     * data round-trip is covered by the dump-file scenario.
     */
    private void assertMigratedRows() {
        DatabaseAsserts.expectRecords(targetDb, "cubdb", "REF_SCHEMA", Map.of(
            "e2e_ref_audit", 1
        ));
        DatabaseAsserts.expectRecords(targetDb, "cubdb", "MAIN_SCHEMA", Map.ofEntries(
            Map.entry("e2e_customer",                  4),
            Map.entry("e2e_order",                     4),
            Map.entry("e2e_order_line",                4),
            Map.entry("e2e_employee",                  3),
            Map.entry("e2e_text_types",                6),
            Map.entry("e2e_numeric_types",             6),
            Map.entry("e2e_temporal_types",            5),
            Map.entry("e2e_binary_types",              5),
            Map.entry("e2e_cubrid_collection_types",   0),
            Map.entry("e2e_cubrid_enum_types",         4),
            Map.entry("e2e_cubrid_json_types",         5)
        ));
    }

    /**
     * Verifies CUBRID catalog state after migration.
     *
     * <p>The values below mirror what CMT produces when a CUBRID source is
     * cloned to a CUBRID target online: PK names retain their source names,
     * indexes and FKs round-trip, and serials carry their next-issued value.
     * The CUBRID -> CUBRID path keeps user-defined PK names intact (no
     * {@code _<col>} suffix rewrite that the Oracle path applies).
     */
    private void assertMigratedMetadata() {
        // Order matches the catalog query's ORDER BY owner_name, class_type, class_name:
        // MAIN_SCHEMA (alphabetically first) -> CLASS rows -> VCLASS row, then REF_SCHEMA.
        CubridMetadataAsserts.expectClasses(targetDb, "cubdb", List.of(
            clazz("MAIN_SCHEMA", "e2e_binary_types", "CLASS"),
            clazz("MAIN_SCHEMA", "e2e_cubrid_collection_types", "CLASS"),
            clazz("MAIN_SCHEMA", "e2e_cubrid_enum_types", "CLASS"),
            clazz("MAIN_SCHEMA", "e2e_cubrid_json_types", "CLASS"),
            clazz("MAIN_SCHEMA", "e2e_customer", "CLASS"),
            clazz("MAIN_SCHEMA", "e2e_employee", "CLASS"),
            clazz("MAIN_SCHEMA", "e2e_numeric_types", "CLASS"),
            clazz("MAIN_SCHEMA", "e2e_order", "CLASS"),
            clazz("MAIN_SCHEMA", "e2e_order_line", "CLASS"),
            clazz("MAIN_SCHEMA", "e2e_temporal_types", "CLASS"),
            clazz("MAIN_SCHEMA", "e2e_text_types", "CLASS"),
            clazz("MAIN_SCHEMA", "e2e_order_summary_v", "VCLASS"),
            clazz("REF_SCHEMA", "e2e_ref_audit", "CLASS")
        ));

        // Order matches the catalog query's ORDER BY class_name, def_order:
        // e2e_customer -> e2e_order -> e2e_order_line -> e2e_temporal_types alphabetically.
        // Note: db_attribute reports CUBRID native precision (INTEGER=10, SHORT=5)
        // unlike Oracle's catalog which leaves prec=0 for those types.
        CubridMetadataAsserts.expectColumns(targetDb, "cubdb", "MAIN_SCHEMA", List.of(
            column("e2e_customer", "customer_id", 0, "INTEGER", 10, 0, "NO"),
            column("e2e_customer", "customer_code", 1, "CHAR", 4, 0, "NO"),
            column("e2e_customer", "customer_name", 2, "STRING", 100, 0, "NO"),
            column("e2e_customer", "customer_alias", 3, "STRING", 60, 0, "YES"),
            column("e2e_customer", "status", 4, "CHAR", 1, 0, "YES"),
            column("e2e_customer", "credit_limit", 5, "NUMERIC", 15, 2, "YES"),
            column("e2e_customer", "created_on", 6, "DATETIME", 23, 3, "YES"),
            column("e2e_customer", "updated_on", 7, "DATETIME", 23, 3, "YES"),
            column("e2e_order", "order_id", 0, "INTEGER", 10, 0, "NO"),
            column("e2e_order", "customer_id", 1, "INTEGER", 10, 0, "NO"),
            column("e2e_order", "order_no", 2, "STRING", 30, 0, "YES"),
            column("e2e_order", "order_status", 3, "STRING", 20, 0, "YES"),
            column("e2e_order", "total_amount", 4, "NUMERIC", 18, 2, "YES"),
            column("e2e_order", "ordered_at", 5, "DATETIME", 23, 3, "YES"),
            column("e2e_order", "settled_at", 6, "DATETIME", 23, 3, "YES"),
            column("e2e_order", "source_comment", 7, "STRING", 200, 0, "YES"),
            column("e2e_order_line", "order_id", 0, "INTEGER", 10, 0, "NO"),
            column("e2e_order_line", "line_no", 1, "SHORT", 5, 0, "NO"),
            column("e2e_order_line", "sku", 2, "STRING", 30, 0, "YES"),
            column("e2e_order_line", "qty", 3, "INTEGER", 10, 0, "YES"),
            column("e2e_order_line", "unit_price", 4, "NUMERIC", 15, 2, "YES"),
            column("e2e_order_line", "line_note", 5, "STRING", 200, 0, "YES"),
            column("e2e_temporal_types", "id", 0, "INTEGER", 10, 0, "NO"),
            column("e2e_temporal_types", "date_col", 1, "DATE", 10, 0, "YES"),
            column("e2e_temporal_types", "time_col", 2, "TIME", 8, 0, "YES"),
            column("e2e_temporal_types", "datetime_col", 3, "DATETIME", 23, 3, "YES"),
            column("e2e_temporal_types", "timestamp_col", 4, "TIMESTAMP", 19, 0, "YES"),
            column("e2e_temporal_types", "datetimetz_col", 5, "DATETIMETZ", 23, 3, "YES"),
            column("e2e_temporal_types", "datetimeltz_col", 6, "DATETIMELTZ", 23, 3, "YES"),
            column("e2e_temporal_types", "timestamptz_col", 7, "TIMESTAMPTZ", 19, 0, "YES"),
            column("e2e_temporal_types", "timestampltz_col", 8, "TIMESTAMPLTZ", 19, 0, "YES")
        ));

        // Note: CMT renames migrated PKs to include the indexed column(s),
        // e.g. pk_e2e_customer -> pk_e2e_customer_customer_id; composite PKs
        // get all columns appended (pk_e2e_order_line_order_id_line_no).
        // idxf_e2e_order_upper_status (function index) and idxr_e2e_order_no
        // (reverse index) are not migrated — CMT's CUBRID-source introspection
        // does not capture the function expression or the reverse flag, so the
        // script.xml fixture drops both. Only standard B-tree indexes survive
        // the round-trip. Order matches catalog ORDER BY class_name, index_name.
        CubridMetadataAsserts.expectIndexes(targetDb, "cubdb", "MAIN_SCHEMA", List.of(
            index("e2e_customer", "pk_e2e_customer_customer_id", true, true, false, 1, false),
            index("e2e_customer", "uk_e2e_customer_code", true, false, false, 1, false),
            index("e2e_employee", "fk_e2e_employee_manager", false, false, true, 1, false),
            index("e2e_employee", "pk_e2e_employee_employee_id", true, true, false, 1, false),
            index("e2e_order", "fk_e2e_order_customer", false, false, true, 1, false),
            index("e2e_order", "idx_e2e_order_customer", false, false, false, 1, false),
            index("e2e_order", "idxd_e2e_order_ordered_at", false, false, false, 1, false),
            index("e2e_order", "pk_e2e_order_order_id", true, true, false, 1, false),
            index("e2e_order_line", "fk_e2e_order_line_order", false, false, true, 1, false),
            index("e2e_order_line", "pk_e2e_order_line_order_id_line_no", true, true, false, 2, false)
        ));

        CubridMetadataAsserts.expectIndexKeys(targetDb, "cubdb", "MAIN_SCHEMA", List.of(
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

        // SERIAL current_val mirrors the CUBRID source seed (START WITH 5).
        // CMT preserves it as the next-to-be-issued value. min_val=5 reflects
        // CUBRID's default behavior: when CREATE SERIAL omits MIN VALUE the
        // server adopts START WITH as the minimum, and that round-trips.
        CubridMetadataAsserts.expectSerials(targetDb, "cubdb", List.of(
            serial("e2e_customer_seq", "5", "1", "5"),
            serial("e2e_order_seq", "5", "1", "5")
        ));
        CubridMetadataAsserts.expectSynonyms(targetDb, "cubdb", List.of(
            synonym("MAIN_SCHEMA", "e2e_ref_audit_syn", "REF_SCHEMA", "e2e_ref_audit")
        ));
        CubridMetadataAsserts.expectGrants(targetDb, "cubdb", List.of(
            grant("REF_SCHEMA", "MAIN_SCHEMA", "CLASS", "e2e_ref_audit", "REF_SCHEMA", "DELETE", "NO"),
            grant("REF_SCHEMA", "MAIN_SCHEMA", "CLASS", "e2e_ref_audit", "REF_SCHEMA", "INSERT", "NO"),
            grant("REF_SCHEMA", "MAIN_SCHEMA", "CLASS", "e2e_ref_audit", "REF_SCHEMA", "SELECT", "NO"),
            grant("REF_SCHEMA", "MAIN_SCHEMA", "CLASS", "e2e_ref_audit", "REF_SCHEMA", "UPDATE", "NO")
        ));
    }

    /**
     * Spot-checks a representative row in each migrated table to catch value
     * corruption. The row IDs target deterministic non-NULL rows from the
     * CUBRID e2e dataset — see SEED_SPEC §3 / §5 for the canonical battery.
     */
    private void assertRepresentativeData() {
        DatabaseAsserts.expectQueryResults(targetDb, "cubdb", "MAIN_SCHEMA", List.of(
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
                "text type R_MIN row (id=3) round-trips ASCII across CHAR/VARCHAR/NCHAR",
                """
                SELECT char_col, varchar_col, nchar_col, nvarchar_col
                FROM e2e_text_types
                WHERE id = 3
                """,
                row("A  ", "A", "A  ", "A")
            ),
            QueryExpectation.of(
                "numeric type R_REPRESENTATIVE row (id=6)",
                """
                SELECT int_col, numeric_col, monetary_col
                FROM e2e_numeric_types
                WHERE id = 6
                """,
                row("100", "42.424242", "99.99")
            ),
            QueryExpectation.of(
                "temporal R_EPOCH row (id=2)",
                """
                SELECT date_col, time_col, datetime_col
                FROM e2e_temporal_types
                WHERE id = 2
                """,
                row("1970-01-01", "00:00:00", "1970-01-01 00:00:00.000")
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
            ),
            QueryExpectation.of(
                "enum value migrates intact",
                """
                SELECT status_enum
                FROM e2e_cubrid_enum_types
                WHERE id = 2
                """,
                row("NEW")
            )
        ));
    }
}
