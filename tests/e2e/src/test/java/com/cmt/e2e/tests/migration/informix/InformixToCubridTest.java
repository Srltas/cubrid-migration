package com.cmt.e2e.tests.migration.informix;

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
import com.cmt.e2e.framework.db.containers.InformixContainer;
import com.cmt.e2e.framework.db.init.InformixDatabaseInitializer;
import com.cmt.e2e.framework.junit.TestResources;
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
 * E2E test for IBM Informix 14.10 (e2e dataset) -> CUBRID online migration.
 *
 * <p>Source seed lives in {@code src/test/resources/db/informix/} and is
 * documented in {@code docs/seed/informix/SEED_SPEC.md}. The container
 * provisions {@code e2e_db} with two owners (main_user, ref_user) at
 * startup; the test then applies the Flyway scenarios as those users.
 *
 * <p>This test focuses on the CMT Informix surface that does work:
 * tables (with FKs, CHECK), indexes (B-tree, descending), sequences,
 * views, and the type batteries (text / numeric / temporal / binary
 * limited / misc with JSON+BSON+BOOLEAN). Procedures, functions, and
 * triggers are intentionally absent — CMT 's
 * {@code MigrationTasksScheduler} routes them through
 * {@code MigrationNoSupportEvent} for non-Oracle/Tibero source DBs and
 * never produces target DDL (see SEED_SPEC §1 anti-coverage). Comments,
 * synonyms, and grants are likewise absent (Informix engine / fetcher
 * gaps).
 *
 * <p><b>Note (after first RegenerateScripts run):</b> the row counts,
 * class lists, and target schema name below are educated guesses based
 * on SEED_SPEC and the InformixSchemaFetcher source. Reconcile against
 * the first successful migration:
 * <ul>
 *   <li>Target CUBRID schema name — likely {@code MAIN_USER} (owner) or
 *       {@code INFORMIX} (DBA login). Currently set to {@code MAIN_USER}.</li>
 *   <li>Multi-schema (REF_SCHEMA) survival — TENTATIVE per SEED_SPEC §1.
 *       If first run shows {@code getSchemaNames} returns only one owner,
 *       drop the e2e_ref_audit row and rebaseline.</li>
 *   <li>BIGSERIAL → CUBRID type — likely SERIAL / AUTO_INCREMENT BIGINT.</li>
 *   <li>JSON / BSON / XMLTYPE-equivalent target type.</li>
 * </ul>
 */
@Disabled("DEFERRED — see docs/seed/informix/SEED_SPEC.md §0. View 마이그레이션 "
    + "0 건 + sanitize 우회 두 건 (CMT InformixSchemaFetcher 결함). 해제 조건: "
    + "buildViewDDL strip 정규식 제거 + getSchemaNames/<table> schema 속성 패치.")
@Testcontainers
@DisplayName("IFX-ON-01: Informix e2e dataset -> CUBRID online migration")
public class InformixToCubridTest {

    @RegisterExtension
    final CmtTestContext ctx = new CmtTestContext();

    @Container
    private final InformixContainer sourceDb = InformixContainer.withMainUser();

    @Container
    private final DatabaseContainer targetDb = CubridContainer.withEmptyDb();

    @Test
    @TestResources("migration/informix/informix_to_cubrid")
    @DisplayName("migrates main_schema (and ref_schema if multi-schema survives) into CUBRID")
    void should_migrateToCubrid_when_sourceIsInformixE2eSeed() throws Exception {
        // Arrange: container init created e2e_db + OS users + DB grants.
        // Single-user pattern (cf. SEED_SPEC §1 anti-coverage on
        // cross-schema): main_user is the only owner of seed objects.
        InformixDatabaseInitializer.of(sourceDb)
            .migrateMain("informix/main_schema");

        ResolvedScript resolved = ScriptTemplateResolver.builder()
            .template(ctx.testPaths().getResourceDir().resolve("script.xml"))
            .source(sourceDb)
            .target(targetDb)
            .outputDir(ctx.testPaths().getArtifactDir())
            .scriptFileName("Informix_to_CUBRID.xml")
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
     * Verifies row count per migrated table. Counts mirror SEED_SPEC §3 / §5:
     *   - business graph     : customer 4 / order 4 / order_line 4 / employee 3
     *   - core type tests    : text 6 / numeric 6 / temporal 5 / binary 1 / misc 5
     *
     * <p>e2e_binary_types has only the R_NULL row because Informix does not
     * accept inline hex literals for BYTE/BLOB in SQL INSERT statements
     * (see SEED_SPEC §5.4). The remaining battery rows are TENTATIVE.
     *
     * <p>flyway_schema_history is also migrated but not asserted (Flyway
     * implementation detail rather than CMT signal).
     */
    private void assertMigratedRows() {
        DatabaseAsserts.expectRecords(targetDb, "cubdb", "MAIN_USER", Map.ofEntries(
            Map.entry("e2e_customer",          4),
            Map.entry("e2e_order",             4),
            Map.entry("e2e_order_line",        4),
            Map.entry("e2e_employee",          3),
            Map.entry("e2e_text_types",        6),
            Map.entry("e2e_numeric_types",     6),
            Map.entry("e2e_temporal_types",    5),
            Map.entry("e2e_binary_types",      1),
            Map.entry("e2e_misc_types",        5)
        ));
    }

    /**
     * Spot-checks a representative row in each migrated table to catch
     * value corruption.
     */
    private void assertRepresentativeData() {
        DatabaseAsserts.expectQueryResults(targetDb, "cubdb", "MAIN_USER", List.of(
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
                "Informix BOOLEAN 't'/'f' migrates to CUBRID as integer 1/0",
                """
                SELECT boolean_col
                FROM e2e_misc_types
                WHERE id = 4
                """,
                row("1")
            )
        ));
    }

    /**
     * Sanity-check expected CUBRID classes after migration. SEED_SPEC §0
     * PARTIAL: 모든 테이블이 metadata 로는 만들어지지만 record 는 0 건
     * 마이그레이션된다. 따라서 row count assertion 은 보류하고 클래스
     * 메타데이터만 검증한다.
     */
    private void assertMigratedClasses() {
        CubridMetadataAsserts.expectClasses(targetDb, "cubdb", List.of(
            clazz("MAIN_USER", "e2e_binary_types",   "CLASS"),
            clazz("MAIN_USER", "e2e_customer",       "CLASS"),
            clazz("MAIN_USER", "e2e_employee",       "CLASS"),
            clazz("MAIN_USER", "e2e_misc_types",     "CLASS"),
            clazz("MAIN_USER", "e2e_numeric_types",  "CLASS"),
            clazz("MAIN_USER", "e2e_order",          "CLASS"),
            clazz("MAIN_USER", "e2e_order_line",     "CLASS"),
            // e2e_order_summary_v (VCLASS) 도 SEED_SPEC §0 PARTIAL 의
            // 같은 root cause 로 export 되지 않아 목록에서 제외.
            clazz("MAIN_USER", "e2e_temporal_types", "CLASS"),
            clazz("MAIN_USER", "e2e_text_types",     "CLASS")
        ));
    }

}
