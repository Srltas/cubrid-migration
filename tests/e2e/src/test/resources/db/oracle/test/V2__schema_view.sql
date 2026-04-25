-- ============================================================
-- Oracle full_coverage / CMT_TEST schema: view
--
-- No schema qualifier is needed because the view references only
-- CMT_TEST-owned tables.
-- When CMT exports CMT_TEST, this view is dumped into XE_CMT_TEST_vclass.
-- ============================================================

CREATE OR REPLACE VIEW ora_cov_order_summary_v AS
SELECT o.order_id,
       o.order_no,
       c.customer_name,
       c.customer_alias,
       o.order_status,
       o.total_amount,
       o.ordered_at
FROM   ora_cov_orders   o
JOIN   ora_cov_customer c
       ON c.customer_id = o.customer_id
WITH READ ONLY;
