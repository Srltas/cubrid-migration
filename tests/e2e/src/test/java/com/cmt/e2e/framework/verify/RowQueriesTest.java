package com.cmt.e2e.framework.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.cmt.e2e.framework.verify.RowQueries.LabeledQuery;
import org.junit.jupiter.api.Test;

class RowQueriesTest {

    @Test
    void parses_two_simple_queries() {
        String content = """
            -- @label customer
            SELECT customer_code, customer_name FROM e2e_customer WHERE customer_id = 1;

            -- @label order
            SELECT order_no FROM e2e_order WHERE order_id = 1;
            """;

        List<LabeledQuery> queries = RowQueries.parse(content);

        assertThat(queries).hasSize(2);
        assertThat(queries.get(0).label()).isEqualTo("customer");
        assertThat(queries.get(0).sql())
            .contains("SELECT customer_code, customer_name FROM e2e_customer")
            .doesNotEndWith(";");
        assertThat(queries.get(1).label()).isEqualTo("order");
    }

    @Test
    void allows_multiline_query_body() {
        String content = """
            -- @label join with order line
            SELECT c.customer_code, o.order_no, l.sku
            FROM e2e_customer c
            JOIN e2e_order o ON o.customer_id = c.customer_id
            JOIN e2e_order_line l ON l.order_id = o.order_id
            WHERE l.order_id = 1 AND l.line_no = 1;
            """;

        List<LabeledQuery> queries = RowQueries.parse(content);
        assertThat(queries).hasSize(1);
        assertThat(queries.get(0).sql())
            .contains("FROM e2e_customer c")
            .contains("JOIN e2e_order_line l")
            .doesNotEndWith(";");
    }

    @Test
    void skips_blank_lines_and_normal_comments_between_queries() {
        String content = """
            -- file header (this should be skipped)

            -- @label first
            SELECT 1;

            -- another normal comment

            -- @label second
            SELECT 2;
            """;
        List<LabeledQuery> queries = RowQueries.parse(content);
        assertThat(queries).hasSize(2);
    }

    @Test
    void rejects_sql_before_any_label() {
        String content = """
            SELECT 1;
            """;
        assertThatThrownBy(() -> RowQueries.parse(content))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("before any -- @label");
    }

    @Test
    void rejects_label_without_terminating_semicolon() {
        String content = """
            -- @label no_terminator
            SELECT 1
            """;
        assertThatThrownBy(() -> RowQueries.parse(content))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Missing trailing ';'");
    }

    @Test
    void rejects_blank_label() {
        String content = """
            -- @label
            SELECT 1;
            """;
        assertThatThrownBy(() -> RowQueries.parse(content))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must include a label name");
    }
}
