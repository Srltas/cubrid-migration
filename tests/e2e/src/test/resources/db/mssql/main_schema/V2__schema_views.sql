-- ============================================================
-- MAIN_SCHEMA: views (TENTATIVE per SEED_SPEC §4.2)
--
-- First run confirms whether MSSQL hits the same CMT view-body
-- translator bug as MySQL/MariaDB (which mis-maps the source schema
-- name to "dba" on the CUBRID target). MSSQL has a stronger
-- schema/user separation, so the bug may not trigger — TBD.
--
-- Base table references stay schema-qualified because MSSQL stores
-- view definitions exactly as written (no auto-expansion like MySQL).
-- ============================================================

CREATE VIEW e2e_order_summary_v AS
SELECT o.order_id,
       o.order_no,
       c.customer_code,
       c.customer_name,
       o.order_status,
       o.total_amount,
       o.ordered_at
FROM   main_schema.e2e_order    AS o
INNER JOIN main_schema.e2e_customer AS c
       ON c.customer_id = o.customer_id;
GO
