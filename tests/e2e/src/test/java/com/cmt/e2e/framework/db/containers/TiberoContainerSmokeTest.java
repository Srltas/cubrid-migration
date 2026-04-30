package com.cmt.e2e.framework.db.containers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import com.cmt.e2e.framework.db.init.TiberoDatabaseInitializer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 verification — does Testcontainers actually launch the custom
 * {@code faketime-tibero:2026-fixed} image and accept JDBC connections?
 *
 * <p>This test exists only to prove the boot/connect path works before we
 * build {@code TiberoSource} / {@code TiberoDatabaseInitializer} on top of
 * it. Once Phase 2 (proper TC + seed) lands and a green {@code TiberoToCubridTest}
 * exists, this smoke test can be retired — its assertions become a strict
 * subset of the migration TC's preconditions.
 *
 * <p>Manual baseline (verified by user before this test was written):
 * <pre>
 * docker run --platform linux/amd64 \
 *   --name faketime-tibero -h tibero-3-100 \
 *   -p 8629:8629 -e TB_ROOT_PASSWORD=tibero123 \
 *   -v $(pwd)/tests/e2e/tibero/license.xml:/opt/tibero7/license/license.xml \
 *   -d faketime-tibero:2026-fixed
 * </pre>
 */
@DisplayName("TiberoContainer Phase 1 boot smoke")
class TiberoContainerSmokeTest {

    private static final Logger log = LoggerFactory.getLogger(TiberoContainerSmokeTest.class);

    @Test
    @DisplayName("Phase 2a: boot + init as SYS + seed as MAIN_SCHEMA + row count = 1")
    void seed_pipeline_works_end_to_end() throws Exception {
        TiberoContainer tibero = TiberoContainer.create();
        try {
            log.info("[smoke] starting Tibero container (this can take ~2 min on amd64 emulation) ...");
            long startMs = System.currentTimeMillis();
            tibero.start();
            log.info("[smoke] container up after {} ms — host={} port={}",
                System.currentTimeMillis() - startMs, tibero.getHost(), tibero.getDatabasePort());

            // Force-load the driver since we use system-scope (Class.forName ensures
            // the SPI is registered with DriverManager regardless of classloader).
            Class.forName("com.tmax.tibero.jdbc.TbDriver");

            // (1) Sanity — SYS can connect, SELECT 1 FROM DUAL returns 1.
            assertSysCanQueryDual(tibero);

            // (2) Init: SYS creates MAIN_SCHEMA user with privileges.
            // (3) Seed: MAIN_SCHEMA applies schema (V1) + data (V99).
            log.info("[smoke] applying seed via TiberoDatabaseInitializer ...");
            TiberoDatabaseInitializer.of(tibero)
                .initAsSys("db/tibero/init")
                .migrateAs(tibero.getMainUser(), tibero.getMainPassword(), "db/tibero/main_schema");

            // (4) Verify: e2e_customer holds exactly 1 row.
            assertMainSchemaSeeRow(tibero);
        } finally {
            tibero.close();
        }
    }

    private static void assertSysCanQueryDual(TiberoContainer tibero) throws Exception {
        try (Connection conn = DriverManager.getConnection(
                tibero.getJdbcUrl(null, null), tibero.getDbaUser(), tibero.getDbaPassword());
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 FROM DUAL")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    private static void assertMainSchemaSeeRow(TiberoContainer tibero) throws Exception {
        try (Connection conn = DriverManager.getConnection(
                tibero.getJdbcUrl(null, null), tibero.getMainUser(), tibero.getMainPassword());
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT customer_id, customer_code, customer_name FROM e2e_customer ORDER BY customer_id")) {
            assertThat(rs.next()).as("e2e_customer should have a row").isTrue();
            assertThat(rs.getInt("customer_id")).isEqualTo(1);
            assertThat(rs.getString("customer_code")).isEqualTo("C001");
            assertThat(rs.getString("customer_name")).isEqualTo("Acme Corp");
            assertThat(rs.next()).as("e2e_customer should have only 1 row").isFalse();
        }
    }
}
