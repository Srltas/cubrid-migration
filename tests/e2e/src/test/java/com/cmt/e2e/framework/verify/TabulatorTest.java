package com.cmt.e2e.framework.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TabulatorTest {

    @Test
    void single_row_basic_layout() {
        String out = Tabulator.format(
            List.of("CUSTOMER_CODE", "STATUS"),
            List.of(List.of("C001", "A")));

        assertThat(out).isEqualTo("""
             CUSTOMER_CODE | STATUS
            ---------------+--------
             C001          | A
            """);
    }

    @Test
    void widens_column_to_longest_value() {
        String out = Tabulator.format(
            List.of("ID", "NAME"),
            List.of(
                List.of("1",   "ALPHA CUSTOMER"),
                List.of("100", "BETA")));

        // ID width grows to 3 (header is 2). NAME width grows to 14.
        assertThat(out).isEqualTo("""
             ID  | NAME
            -----+----------------
             1   | ALPHA CUSTOMER
             100 | BETA
            """);
    }

    @Test
    void empty_rows_renders_header_and_separator_only() {
        String out = Tabulator.format(List.of("A", "B"), List.of());

        assertThat(out).isEqualTo("""
             A | B
            ---+---
            """);
    }

    @Test
    void converts_caller_provided_null_sentinel_unchanged() {
        // Tabulator does not coerce nulls; caller (e.g. CatalogSnapshot)
        // converts SQL NULL to NULL_VALUE before calling.
        String out = Tabulator.format(
            List.of("VAL"),
            List.of(List.of(Tabulator.NULL_VALUE)));

        assertThat(out).contains("<NULL>");
    }

    @Test
    void rejects_empty_headers() {
        assertThatThrownBy(() -> Tabulator.format(List.of(), List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_row_with_mismatched_column_count() {
        assertThatThrownBy(() -> Tabulator.format(
                List.of("A", "B"),
                List.of(List.of("1"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("row size");
    }

    @Test
    void output_ends_with_newline() {
        String out = Tabulator.format(List.of("X"), List.of(List.of("y")));
        assertThat(out).endsWith("\n");
    }

    @Test
    void no_trailing_whitespace_on_any_line() {
        String out = Tabulator.format(
            List.of("SHORT", "LONGER_HEADER"),
            List.of(List.of("a", "b")));

        for (String line : out.split("\n", -1)) {
            assertThat(line)
                .as("line must not have trailing whitespace: '%s'", line)
                .isEqualTo(line.replaceAll("\\s+$", ""));
        }
    }
}
