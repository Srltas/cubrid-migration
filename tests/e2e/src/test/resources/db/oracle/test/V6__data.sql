-- ============================================================
-- Oracle full_coverage / CMT_TEST schema: seed data
--
-- Insert order: customer -> orders -> order_line because of FK constraints.
-- ora_cov_locator_types references ora_cov_rowid_source.ROWID, so insert it
-- after rowid_source.
-- ============================================================

-- -------------------------------------------------------
-- Business data: customer (3 rows) -> orders (3 rows) -> order_line (4 rows)
-- -------------------------------------------------------
INSERT INTO ora_cov_customer (
    customer_id, customer_code, customer_name, customer_alias,
    status, credit_limit, created_on, updated_on
) VALUES (
    ora_cov_customer_seq.NEXTVAL,
    'C001', 'ALPHA CUSTOMER', 'Alpha Alias',
    'A', 12500.75, DATE '2024-01-01', DATE '2024-01-02'
);

INSERT INTO ora_cov_customer (
    customer_id, customer_code, customer_name, customer_alias,
    status, credit_limit, created_on, updated_on
) VALUES (
    ora_cov_customer_seq.NEXTVAL,
    'C002', 'BETA CUSTOMER', 'Beta Alias',
    'A', 24500.00, DATE '2024-02-01', DATE '2024-02-03'
);

INSERT INTO ora_cov_customer (
    customer_id, customer_code, customer_name, customer_alias,
    status, credit_limit, created_on, updated_on
) VALUES (
    ora_cov_customer_seq.NEXTVAL,
    'C003', 'GAMMA CUSTOMER', 'Gamma Alias',
    'I', 0, DATE '2024-03-01', DATE '2024-03-05'
);

INSERT INTO ora_cov_orders (
    order_id, customer_id, order_no, order_status,
    total_amount, ordered_at, settled_at, source_comment
) VALUES (
    ora_cov_order_seq.NEXTVAL, 1, 'ORD-2024-001', 'NEW',
    150.25, DATE '2024-01-10', DATE '2024-01-11', 'first order'
);

INSERT INTO ora_cov_orders (
    order_id, customer_id, order_no, order_status,
    total_amount, ordered_at, settled_at, source_comment
) VALUES (
    ora_cov_order_seq.NEXTVAL, 1, 'ORD-2024-002', 'HOLD',
    2999.99, DATE '2024-02-10', DATE '2024-02-12', 'second order'
);

INSERT INTO ora_cov_orders (
    order_id, customer_id, order_no, order_status,
    total_amount, ordered_at, settled_at, source_comment
) VALUES (
    ora_cov_order_seq.NEXTVAL, 2, 'ORD-2024-003', 'DONE',
    45.00, DATE '2024-03-15', DATE '2024-03-16', 'third order'
);

INSERT INTO ora_cov_order_line (line_id, order_id, line_no, sku, qty, unit_price, line_note)
    VALUES (1, 1, 1, 'SKU-ALPHA', 2,   25.00, 'alpha line');
INSERT INTO ora_cov_order_line (line_id, order_id, line_no, sku, qty, unit_price, line_note)
    VALUES (2, 1, 2, 'SKU-BETA',  1,  100.25, 'beta line');
INSERT INTO ora_cov_order_line (line_id, order_id, line_no, sku, qty, unit_price, line_note)
    VALUES (3, 2, 1, 'SKU-GAMMA', 3,  999.99, 'gamma line');
INSERT INTO ora_cov_order_line (line_id, order_id, line_no, sku, qty, unit_price, line_note)
    VALUES (4, 3, 1, 'SKU-DELTA', 1,   45.00, 'delta line');

-- -------------------------------------------------------
-- Auxiliary data
-- -------------------------------------------------------
INSERT INTO ora_cov_program_log (log_id, proc_name, payload, created_on)
VALUES (ora_cov_log_seq.NEXTVAL, 'SEED', 'initial log row', DATE '2024-01-01');

INSERT INTO ora_cov_rowid_source (source_id, source_name)
VALUES (1, 'rowid source');

INSERT INTO ora_cov_locator_types (locator_id, rowid_col, urowid_col)
SELECT 1, ROWID, ROWID
  FROM ora_cov_rowid_source
 WHERE source_id = 1;

-- -------------------------------------------------------
-- Type-coverage data (1 row each)
-- -------------------------------------------------------
INSERT INTO ora_cov_text_types (
    text_id, char_byte_col, char_char_col, varchar_byte_col, varchar_char_col,
    nchar_col, nvarchar_col, clob_col, nclob_col, long_col
) VALUES (
    1, 'ABC', 'XYZ', 'BYTE-TEXT', 'CHAR-TEXT',
    'NCHAR-TEXT', 'NVARCHAR sample',
    TO_CLOB('CLOB sample text for migration'),
    TO_NCLOB('NCLOB sample text for migration'),
    'LONG sample text for migration'
);

INSERT INTO ora_cov_binary_types (binary_id, raw_col, long_raw_col, blob_col, bfile_col)
VALUES (
    1,
    HEXTORAW('CAFEBABE00112233445566778899AABB'),
    HEXTORAW('DEADBEEF001122334455'),
    EMPTY_BLOB(),
    NULL
);

INSERT INTO ora_cov_numeric_types (
    numeric_id, integer_col, decimal_col, number_p0_col, number_ps_col,
    number_round_col, number_any_col, float_col, real_col,
    binary_float_col, binary_double_col
) VALUES (
    1, 42, 9876543210.1234,
    12345678901234567890123456789012345678,
    1234567890.123456, 123456, 12345.6789,
    3.141592, 2.718281, 1.25, 6.283185307179586
);

INSERT INTO ora_cov_temporal_types (
    temporal_id, date_col, ts6_col, ts9_col, tsltz6_col, tstz9_col,
    interval_ds_col, interval_ym_col
) VALUES (
    1,
    DATE '2024-04-20',
    TIMESTAMP '2024-04-20 09:10:11.123456',
    TIMESTAMP '2024-04-20 09:10:11.123456789',
    TO_TIMESTAMP_TZ('2024-04-20 09:10:11.123456 +09:00',  'YYYY-MM-DD HH24:MI:SS.FF TZH:TZM'),
    TO_TIMESTAMP_TZ('2024-04-20 09:10:11.123456789 +09:00','YYYY-MM-DD HH24:MI:SS.FF TZH:TZM'),
    INTERVAL '3 12:34:56.123456' DAY TO SECOND(6),
    INTERVAL '0012-05' YEAR(4) TO MONTH
);
