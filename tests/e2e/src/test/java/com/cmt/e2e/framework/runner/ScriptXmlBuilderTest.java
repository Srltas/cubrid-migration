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
}
