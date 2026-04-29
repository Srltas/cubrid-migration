package com.cmt.e2e.framework.runner;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link ScriptXmlBuilder#sanitize(String)} — pure-string
 * regex behaviour. End-to-end {@code generate(...)} smoke is covered by
 * the Phase 2.4 integration smoke test.
 */
class ScriptXmlBuilderTest {

    @Test
    void strips_timestamp_suffix_from_migration_name() {
        String input  = "<migration name=\"CUBRID_e2e_db_202604062341\" version=\"11.1.0\" />";
        String output = ScriptXmlBuilder.sanitize(input);
        assertThat(output).contains("name=\"CUBRID_e2e_db\"")
                          .doesNotContain("_202604062341");
    }

    @Test
    void zeroes_wizard_start_date_time() {
        String input  = "<migration name=\"x\" wizard_start_date_time=\"202604062341\" />";
        String output = ScriptXmlBuilder.sanitize(input);
        assertThat(output).contains("wizard_start_date_time=\"000000000000\"")
                          .doesNotContain("202604062341");
    }

    @Test
    void leaves_unrelated_attributes_intact() {
        String input  = "<connection id=\"source\" host=\"h\" port=\"1521\" />";
        String output = ScriptXmlBuilder.sanitize(input);
        assertThat(output).isEqualTo(input);
    }

    @Test
    void migration_name_without_timestamp_unchanged() {
        // Defensive — if CMT ever emits a name without trailing _<14 digits>,
        // sanitize must leave it alone rather than silently corrupting it.
        String input  = "<migration name=\"plain_name\" />";
        String output = ScriptXmlBuilder.sanitize(input);
        assertThat(output).isEqualTo(input);
    }

    @Test
    void extractMigrationName_reads_attribute() {
        String xml = "<migration name=\"CUBRID_e2e_db\" version=\"11.1.0\"/>";
        assertThat(ScriptXmlBuilder.extractMigrationName(xml)).isEqualTo("CUBRID_e2e_db");
    }

    @Test
    void strips_flyway_schema_history_self_closing_entries() {
        String input = """
            <tables>
                <table name="e2e_customer" target="e2e_customer"/>
                <table name="flyway_schema_history" target="flyway_schema_history"/>
                <table name="e2e_order" target="e2e_order"/>
                <sourceTable name="flyway_schema_history" schema="MAIN_SCHEMA"/>
            </tables>
            """;
        String output = ScriptXmlBuilder.sanitize(input);
        assertThat(output)
            .contains("e2e_customer")
            .contains("e2e_order")
            .doesNotContain("flyway_schema_history");
    }

    @Test
    void strips_cubrid_system_schemas() {
        String input = """
            <schemas>
                <schema source="MAIN_SCHEMA" target="MAIN_SCHEMA"/>
                <schema source="REF_SCHEMA" target="REF_SCHEMA"/>
                <schema source="DBA" target="DBA"/>
                <schema source="PUBLIC" target="PUBLIC"/>
            </schemas>
            """;
        String output = ScriptXmlBuilder.sanitize(input);
        assertThat(output)
            .contains("MAIN_SCHEMA")
            .contains("REF_SCHEMA")
            .doesNotContain("source=\"DBA\"")
            .doesNotContain("source=\"PUBLIC\"");
    }

    @Test
    void strips_flyway_schema_history_multi_line_table_blocks() {
        // Mirrors CMT's actual output shape: <table ...>...</table>
        // with nested <columns> and <constraints>.
        String input = """
            <tables>
                <table schema="MAIN_SCHEMA" name="e2e_customer" target_name="e2e_customer">
                    <columns>
                        <column name="customer_id"/>
                    </columns>
                </table>
                <table schema="MAIN_SCHEMA" name="flyway_schema_history" target_name="flyway_schema_history">
                    <columns>
                        <column name="installed_rank"/>
                        <column name="version"/>
                    </columns>
                    <constraints>
                        <index name="flyway_schema_history_s_idx" target_name="flyway_schema_history_s_idx"/>
                    </constraints>
                </table>
                <table schema="MAIN_SCHEMA" name="e2e_order" target_name="e2e_order">
                    <columns>
                        <column name="order_id"/>
                    </columns>
                </table>
            </tables>
            """;
        String output = ScriptXmlBuilder.sanitize(input);
        assertThat(output)
            .contains("e2e_customer")
            .contains("e2e_order")
            .doesNotContain("flyway_schema_history")
            .doesNotContain("flyway_schema_history_s_idx")
            .doesNotContain("installed_rank");
    }
}
