-- ============================================================
-- MAIN_SCHEMA: seed data
-- Per docs/seed/informix/SEED_SPEC.md §3 (business graph) and §5 (type test)
--
-- Insert order: parents before children (FK chain).
--   1. e2e_customer            (parent of e2e_order)
--   2. e2e_order               (parent of e2e_order_line)
--   3. e2e_order_line          (leaf)
--   4. e2e_employee            (self-FK, root first)
--   5. e2e_text_types          (independent; type battery)
--   6. e2e_numeric_types       (independent)
--   7. e2e_temporal_types      (independent)
--   8. e2e_binary_types        (independent)
--   9. e2e_misc_types          (independent)
--
-- DATETIME literals follow Informix syntax:
--   DATETIME (YYYY-MM-DD HH:MM:SS) YEAR TO SECOND
--   DATETIME (YYYY-MM-DD HH:MM:SS.fff) YEAR TO FRACTION(3)
--
-- DATE literals use TO_DATE(string, format) instead of the
-- short DATE('MM/DD/YYYY') form. The short form depends on the
-- session 's DBDATE environment variable (which the Developer
-- Edition image does not pin to a known value); TO_DATE with
-- an explicit format string is locale-independent and works in
-- every Informix locale.
-- ============================================================

-- §3.1 e2e_customer (4 rows)
INSERT INTO e2e_customer (customer_id, customer_code, customer_name,  customer_alias, status, credit_limit, created_on,        updated_on) VALUES
    (1, 'C001', 'ALPHA CUSTOMER',     'Alpha Alias',   'A', 12500.75,         TO_DATE('2024-01-10', '%Y-%m-%d'), NULL);
INSERT INTO e2e_customer (customer_id, customer_code, customer_name,  customer_alias, status, credit_limit, created_on,        updated_on) VALUES
    (2, 'C002', 'BETA CUSTOMER',      NULL,            'I', NULL,             TO_DATE('2024-02-29', '%Y-%m-%d'), NULL);
INSERT INTO e2e_customer (customer_id, customer_code, customer_name,  customer_alias, status, credit_limit, created_on,        updated_on) VALUES
    (3, 'C003', '한국 고객',           '별칭-한글',     'D', 0,                TO_DATE('2024-03-15', '%Y-%m-%d'), NULL);
INSERT INTO e2e_customer (customer_id, customer_code, customer_name,  customer_alias, status, credit_limit, created_on,        updated_on) VALUES
    (4, 'C004', 'HIGH LIMIT CUSTOMER','High',          'A', 9999999999999.99, TO_DATE('2024-04-20', '%Y-%m-%d'), NULL);

-- §3.2 e2e_order (4 rows). order_id is BIGSERIAL: passing an
-- explicit non-zero value sets it without consuming the counter.
INSERT INTO e2e_order (order_id, customer_id, order_no,        order_status, total_amount, ordered_at,                                  source_comment) VALUES
    (1, 1, 'ORD-2024-001', 'NEW',    50.00,  DATETIME (2024-01-10 10:00:00) YEAR TO SECOND, 'first order');
INSERT INTO e2e_order (order_id, customer_id, order_no,        order_status, total_amount, ordered_at,                                  source_comment) VALUES
    (2, 1, 'ORD-2024-002', 'DONE',   125.50, DATETIME (2024-02-15 11:30:00) YEAR TO SECOND, 'done-order');
INSERT INTO e2e_order (order_id, customer_id, order_no,        order_status, total_amount, ordered_at,                                  source_comment) VALUES
    (3, 2, 'ORD-2024-003', 'HOLD',   NULL,   DATETIME (2024-02-29 14:15:00) YEAR TO SECOND, '  padded comment  ');
INSERT INTO e2e_order (order_id, customer_id, order_no,        order_status, total_amount, ordered_at,                                  source_comment) VALUES
    (4, 3, 'ORD-2024-004', 'CANCEL', 0,      DATETIME (2024-03-15 09:45:00) YEAR TO SECOND, '취소 주문 !@#');

-- §3.3 e2e_order_line (4 rows)
INSERT INTO e2e_order_line (order_id, line_no, sku,          qty, unit_price, line_note) VALUES
    (1, 1, 'SKU-ALPHA',  2,  25.00,  'normal');
INSERT INTO e2e_order_line (order_id, line_no, sku,          qty, unit_price, line_note) VALUES
    (2, 1, 'SKU-BETA',   1,  125.50, NULL);
INSERT INTO e2e_order_line (order_id, line_no, sku,          qty, unit_price, line_note) VALUES
    (3, 1, 'SKU-HOLD',   10, 9.99,   'hold line');
INSERT INTO e2e_order_line (order_id, line_no, sku,          qty, unit_price, line_note) VALUES
    (4, 1, 'SKU-CANCEL', 1,  0.01,   'cancelled');
-- ck_e2e_order_line_qty requires qty > 0; SKU-CANCEL therefore
-- uses unit_price=0.01 instead of 0 to keep "near zero" semantics.

-- §3.4 e2e_employee (3 rows; root first to satisfy self-FK)
INSERT INTO e2e_employee (employee_id, manager_id, emp_name,    hired_on) VALUES
    (1, NULL, 'CEO',       TO_DATE('2020-01-01', '%Y-%m-%d'));
INSERT INTO e2e_employee (employee_id, manager_id, emp_name,    hired_on) VALUES
    (2, 1,    'Manager A', TO_DATE('2021-01-01', '%Y-%m-%d'));
INSERT INTO e2e_employee (employee_id, manager_id, emp_name,    hired_on) VALUES
    (3, 2,    'Staff A1',  TO_DATE('2022-01-01', '%Y-%m-%d'));

-- ============================================================
-- §5.1 e2e_text_types (R1 R_NULL - R6 R_BOUNDARY)
--
-- Informix TEXT and CLOB are simple-large-object / smart-large-object
-- types. They DO NOT accept inline string literals in SQL INSERT
-- statements (failing with "-617: A blob data type must be supplied
-- within this context"). Valid pathways are:
--   - NULL literal (works in pure SQL)
--   - FILETOCLOB( '/path' , 'client' ) — requires a file in the container
--   - Driver-side PreparedStatement.setClob / setCharacterStream
--
-- Per SEED_SPEC §5.1, we keep the 6-row battery for the in-row text
-- columns (char / varchar / nchar / nvarchar / lvarchar) and set
-- text_col / clob_col to NULL across all rows. Real text/clob payload
-- is TENTATIVE — populated post-Flyway by a future driver-side step
-- when the LOB type-test path is activated.
-- ============================================================
INSERT INTO e2e_text_types (id, char_col, varchar_col, nchar_col, nvarchar_col, lvarchar_col, text_col, clob_col) VALUES
    (1, NULL,  NULL,        NULL,        NULL,                NULL,                                 NULL, NULL);
INSERT INTO e2e_text_types (id, char_col, varchar_col, nchar_col, nvarchar_col, lvarchar_col, text_col, clob_col) VALUES
    (2, ' ',   '',          ' ',         '',                  '',                                   NULL, NULL);
INSERT INTO e2e_text_types (id, char_col, varchar_col, nchar_col, nvarchar_col, lvarchar_col, text_col, clob_col) VALUES
    (3, 'A',   'A',         'A',         'A',                 'A',                                  NULL, NULL);
INSERT INTO e2e_text_types (id, char_col, varchar_col, nchar_col, nvarchar_col, lvarchar_col, text_col, clob_col) VALUES
    (4, 'XYZ', 'ABCDEFGHIJ','1234567890','한국어 漢字 cafe',  'lvarchar-max-payload-ascii-only-32',  NULL, NULL);
INSERT INTO e2e_text_types (id, char_col, varchar_col, nchar_col, nvarchar_col, lvarchar_col, text_col, clob_col) VALUES
    (5, NULL,  NULL,        '한中Ω',    '한국 漢字 cafe',    '한국어 漢字 cafe punctuation !@#',     NULL, NULL);
INSERT INTO e2e_text_types (id, char_col, varchar_col, nchar_col, nvarchar_col, lvarchar_col, text_col, clob_col) VALUES
    (6, '   ', ' A ',       '   ',       ' A ',               'line1' || CHR(10) || 'line2',         NULL, NULL);

-- ============================================================
-- §5.2 e2e_numeric_types (R1 - R6)
-- SERIAL / SERIAL8 columns: 0 means "auto-assign"; the seed
-- relies on insert order, so columns are deterministic 1..6.
-- ============================================================
INSERT INTO e2e_numeric_types (id, smallint_col, integer_col, bigint_col,           int8_col,             decimal_col,             numeric_col,        money_col,        smallfloat_col, float_col, serial_col, serial8_col) VALUES
    (1, NULL,        NULL,         NULL,                 NULL,                 NULL,                    NULL,               NULL,             NULL,           NULL,      0,          0);
INSERT INTO e2e_numeric_types (id, smallint_col, integer_col, bigint_col,           int8_col,             decimal_col,             numeric_col,        money_col,        smallfloat_col, float_col, serial_col, serial8_col) VALUES
    (2, 0,           0,            0,                    0,                    0,                       0,                  0,                0,              0,         0,          0);
INSERT INTO e2e_numeric_types (id, smallint_col, integer_col, bigint_col,           int8_col,             decimal_col,             numeric_col,        money_col,        smallfloat_col, float_col, serial_col, serial8_col) VALUES
    -- Informix reserves the most-negative bit pattern of each integer
    -- type as a NULL marker, so the practical R_MIN is one above the
    -- standard two ' s-complement minimum:
    --   SMALLINT: -32767 (not -32768)
    --   INTEGER : -2147483647 (not -2147483648)
    --   BIGINT  : -9223372036854775807 (not -9223372036854775808)
    (3, -32767,      -2147483647,  -9223372036854775807, -9223372036854775807, -9999999999999999.9999,  -999999999999.999,  -9999999999999.99, -1.0e20,       -1.0e20,   0,          0);
INSERT INTO e2e_numeric_types (id, smallint_col, integer_col, bigint_col,           int8_col,             decimal_col,             numeric_col,        money_col,        smallfloat_col, float_col, serial_col, serial8_col) VALUES
    (4, 32767,       2147483647,   9223372036854775807,  9223372036854775807,  9999999999999999.9999,   999999999999.999,   9999999999999.99,  1.0e20,        1.0e20,    0,          0);
INSERT INTO e2e_numeric_types (id, smallint_col, integer_col, bigint_col,           int8_col,             decimal_col,             numeric_col,        money_col,        smallfloat_col, float_col, serial_col, serial8_col) VALUES
    (5, 42,          100,          9876543210,           9876543210,           9876543210.1234,         1234567890.123,     1234.56,          3.14,          3.141593,  0,          0);
INSERT INTO e2e_numeric_types (id, smallint_col, integer_col, bigint_col,           int8_col,             decimal_col,             numeric_col,        money_col,        smallfloat_col, float_col, serial_col, serial8_col) VALUES
    (6, 7,           7,            7,                    7,                    100.2500,                100.250,            100.25,           6.28,          2.71828,   0,          0);

-- ============================================================
-- §5.3 e2e_temporal_types (R1 - R5)
-- DATETIME literals constrained to the CUBRID TIMESTAMP target
-- range (1970-01-02 .. 2038-01-18) per SEED_SPEC §5.3.
-- DATE accepts the wider range because CMT maps DATE→DATETIME.
-- ============================================================
INSERT INTO e2e_temporal_types (id, date_col,             dt_yts_col,                                   dt_ytf3_col,                                          dt_ytf5_col,                                            interval_ds_col,                                  interval_ym_col) VALUES
    (1, NULL,                 NULL,                                         NULL,                                                  NULL,                                                   NULL,                                             NULL);
INSERT INTO e2e_temporal_types (id, date_col,             dt_yts_col,                                   dt_ytf3_col,                                          dt_ytf5_col,                                            interval_ds_col,                                  interval_ym_col) VALUES
    (2, TO_DATE('1970-01-01', '%Y-%m-%d'),   DATETIME (1970-01-01 00:00:00) YEAR TO SECOND,DATETIME (1970-01-01 00:00:00.000) YEAR TO FRACTION(3),DATETIME (1970-01-01 00:00:00.00000) YEAR TO FRACTION(5),INTERVAL ( 0 0:0:0) DAY(2) TO SECOND,             INTERVAL (   0-0)   YEAR(4) TO MONTH);
INSERT INTO e2e_temporal_types (id, date_col,             dt_yts_col,                                   dt_ytf3_col,                                          dt_ytf5_col,                                            interval_ds_col,                                  interval_ym_col) VALUES
    (3, TO_DATE('1900-01-01', '%Y-%m-%d'),   DATETIME (1970-01-02 00:00:01) YEAR TO SECOND,DATETIME (1970-01-02 00:00:00.001) YEAR TO FRACTION(3),DATETIME (1970-01-02 00:00:00.00001) YEAR TO FRACTION(5),INTERVAL (-99 23:59:59) DAY(2) TO SECOND,         INTERVAL (-9999-11) YEAR(4) TO MONTH);
INSERT INTO e2e_temporal_types (id, date_col,             dt_yts_col,                                   dt_ytf3_col,                                          dt_ytf5_col,                                            interval_ds_col,                                  interval_ym_col) VALUES
    (4, TO_DATE('9999-12-31', '%Y-%m-%d'),   DATETIME (2038-01-18 23:59:59) YEAR TO SECOND,DATETIME (2038-01-18 23:59:59.999) YEAR TO FRACTION(3),DATETIME (2038-01-18 23:59:59.99999) YEAR TO FRACTION(5),INTERVAL ( 99 23:59:59) DAY(2) TO SECOND,         INTERVAL ( 9999-11) YEAR(4) TO MONTH);
INSERT INTO e2e_temporal_types (id, date_col,             dt_yts_col,                                   dt_ytf3_col,                                          dt_ytf5_col,                                            interval_ds_col,                                  interval_ym_col) VALUES
    (5, TO_DATE('2024-02-29', '%Y-%m-%d'),   DATETIME (2024-02-29 12:34:56) YEAR TO SECOND,DATETIME (2024-02-29 12:34:56.123) YEAR TO FRACTION(3),DATETIME (2024-02-29 12:34:56.12345) YEAR TO FRACTION(5),INTERVAL (  1  2:03:04) DAY(2) TO SECOND,         INTERVAL (   2-6)   YEAR(4) TO MONTH);

-- ============================================================
-- §5.4 e2e_binary_types (R1 R_NULL only)
--
-- Informix does NOT accept inline hex literals for BYTE / BLOB
-- in SQL INSERT statements. Valid pathways are:
--   - NULL literal (works in pure SQL)
--   - FILETOBYTE(' /path ', ' client ') / FILETOBLOB
--   - Driver-side PreparedStatement.setBytes / setBlob
--
-- Per SEED_SPEC §5.4, R_EMPTY / R_MIN / R_MAX / R_BOUNDARY
-- rows are TENTATIVE — populated post-Flyway by
-- InformixDatabaseInitializer.populateBinaryData() via JDBC
-- when the binary type-test path is activated. The single
-- R_NULL row below is enough to verify metadata extraction
-- (column types, nullability).
-- ============================================================
INSERT INTO e2e_binary_types (id, byte_col, blob_col) VALUES
    (1, NULL, NULL);

-- ============================================================
-- §5.5 e2e_misc_types (R1 - R5)
-- BOOLEAN: 't' / 'f' / NULL
-- JSON / BSON: native semi-structured types
--
-- BSON insert in this Informix Developer Edition image is broken at the
-- catalog level:
--   - BSON() / bson() callable function does not exist
--     ("-674: Routine (bson) can not be resolved")
--   - bare-string implicit cast triggers
--     "-937: bson_input: bson_from_char failed!" for every value
--     including non-empty documents
--   - explicit '{...}'::BSON cast triggers the same -937
--
-- The bson_input function is registered (Informix calls it on assignment)
-- but its char→BSON conversion implementation appears stripped or
-- incomplete in the icr.io/informix Developer Edition build.
--
-- Pragmatic decision: keep the bson_col definition so column metadata,
-- JDBC type code, and CMT type-map (bson → json) are still exercised,
-- but write NULL across all rows. The non-NULL battery for BSON is
-- TENTATIVE — populated by a driver-side step (PreparedStatement.setObject
-- with a binary BSON document) when/if the path is needed.
--
-- JSON 's auto-conversion accepts bare-string literals fine, so the
-- json_col battery stays intact (R_EMPTY = '{}', etc.).
-- ============================================================
INSERT INTO e2e_misc_types (id, boolean_col, json_col,                          bson_col) VALUES
    (1, NULL,    NULL,                                       NULL);
INSERT INTO e2e_misc_types (id, boolean_col, json_col,                          bson_col) VALUES
    (2, 'f',     '{}',                                       NULL);
INSERT INTO e2e_misc_types (id, boolean_col, json_col,                          bson_col) VALUES
    (3, 'f',     '{"k":1}',                                  NULL);
INSERT INTO e2e_misc_types (id, boolean_col, json_col,                          bson_col) VALUES
    (4, 't',     '{"k":[1,2,3,"한",null]}',                  NULL);
INSERT INTO e2e_misc_types (id, boolean_col, json_col,                          bson_col) VALUES
    (5, 't',     '{"name":"한국 漢字 cafe"}',                NULL);
