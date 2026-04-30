-- ============================================================
-- MAIN_SCHEMA: seed data (business tables only — Phase 3b).
-- Type-test rows (V3 schema) come in a later phase. Identical row
-- values to db/oracle/main_schema/V99 §3.1–§3.4 so future Tibero
-- snapshots line up with Oracle's where types match 1:1.
-- ============================================================

-- =====================================================================
-- §3.1 e2e_customer
-- =====================================================================
INSERT INTO e2e_customer (customer_id, customer_code, customer_name, customer_alias, status, credit_limit, created_on, updated_on)
VALUES (1, 'C001', 'ALPHA CUSTOMER',      'Alpha Alias', 'A', 12500.75,           DATE '2024-01-10', NULL);
INSERT INTO e2e_customer (customer_id, customer_code, customer_name, customer_alias, status, credit_limit, created_on, updated_on)
VALUES (2, 'C002', 'BETA CUSTOMER',       NULL,          'I', NULL,                DATE '2024-02-29', NULL);
INSERT INTO e2e_customer (customer_id, customer_code, customer_name, customer_alias, status, credit_limit, created_on, updated_on)
VALUES (3, 'C003', '한국 고객',           '별칭-한글',   'D', 0,                   DATE '2024-03-15', NULL);
INSERT INTO e2e_customer (customer_id, customer_code, customer_name, customer_alias, status, credit_limit, created_on, updated_on)
VALUES (4, 'C004', 'HIGH LIMIT CUSTOMER', 'High',        'A', 9999999999999.99,    DATE '2024-04-20', NULL);

-- =====================================================================
-- §3.2 e2e_order
-- =====================================================================
INSERT INTO e2e_order (order_id, customer_id, order_no, order_status, total_amount, ordered_at, settled_at, source_comment)
VALUES (1, 1, 'ORD-2024-001', 'NEW',    50.00,  DATE '2024-01-15', NULL,              'first order');
INSERT INTO e2e_order (order_id, customer_id, order_no, order_status, total_amount, ordered_at, settled_at, source_comment)
VALUES (2, 1, 'ORD-2024-002', 'DONE',   125.50, DATE '2024-02-01', DATE '2024-02-05', 'done-order');
INSERT INTO e2e_order (order_id, customer_id, order_no, order_status, total_amount, ordered_at, settled_at, source_comment)
VALUES (3, 2, 'ORD-2024-003', 'HOLD',   NULL,   DATE '2024-03-01', NULL,              '  padded comment  ');
INSERT INTO e2e_order (order_id, customer_id, order_no, order_status, total_amount, ordered_at, settled_at, source_comment)
VALUES (4, 3, 'ORD-2024-004', 'CANCEL', 0,      DATE '2024-04-10', NULL,              '취소 주문 !@#');

-- =====================================================================
-- §3.3 e2e_order_line  (composite PK, distinct order_id-line_no pairs)
-- =====================================================================
INSERT INTO e2e_order_line (order_id, line_no, sku, qty, unit_price, line_note)
VALUES (1, 1, 'SKU-ALPHA',  2,  25.00,  'normal');
INSERT INTO e2e_order_line (order_id, line_no, sku, qty, unit_price, line_note)
VALUES (2, 1, 'SKU-BETA',   1,  125.50, NULL);
INSERT INTO e2e_order_line (order_id, line_no, sku, qty, unit_price, line_note)
VALUES (3, 1, 'SKU-HOLD',   10, 9.99,   'hold line');
INSERT INTO e2e_order_line (order_id, line_no, sku, qty, unit_price, line_note)
VALUES (4, 1, 'SKU-CANCEL', 1,  0,      'cancelled');

-- =====================================================================
-- §3.4 e2e_employee  (self-reference FK forms a 3-level chain)
-- =====================================================================
INSERT INTO e2e_employee (employee_id, manager_id, emp_name, hired_on)
VALUES (1, NULL, 'CEO',       DATE '2020-01-01');
INSERT INTO e2e_employee (employee_id, manager_id, emp_name, hired_on)
VALUES (2, 1,    'Manager A', DATE '2021-01-01');
INSERT INTO e2e_employee (employee_id, manager_id, emp_name, hired_on)
VALUES (3, 2,    'Staff A1',  DATE '2022-01-01');
