-- ============================================================
-- MAIN_SCHEMA: views
-- Per docs/seed/informix/SEED_SPEC.md §4.2
--
-- Schema-qualified column references with main_user as the
-- owner-prefix. CMT InformixSchemaFetcher.buildViewDDL strips
-- the leading "<owner>." prefix and stores the body only.
-- ============================================================

CREATE VIEW e2e_order_summary_v (
    order_id,
    order_no,
    customer_code,
    customer_name,
    order_status,
    total_amount,
    ordered_at
) AS
SELECT  o.order_id,
        o.order_no,
        c.customer_code,
        c.customer_name,
        o.order_status,
        o.total_amount,
        o.ordered_at
  FROM  e2e_order    o,
        e2e_customer c
  WHERE c.customer_id = o.customer_id;
