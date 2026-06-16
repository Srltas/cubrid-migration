-- ============================================================
-- MAIN_SCHEMA: seed data (business + type test + extensions)
-- Per docs/seed/mysql/SEED_SPEC.md §3 (business) and §5 (type test).
--
-- Row IDs in the type test tables follow COMMON_SEED_CONTRACT.md §4:
--   1 = R_NULL  2 = R_EMPTY  3 = R_MIN  4 = R_MAX
--   5 = R_UNICODE (text only) / R_BOUNDARY (numeric, temporal, binary)
--   6 = R_BOUNDARY (text)     / R_REPRESENTATIVE (numeric)
--
-- TIMESTAMP columns are stored in UTC and converted on read/write based on
-- session time zone. Pin the session to UTC so seed values are deterministic
-- regardless of the container 's system_time_zone.
-- ============================================================
SET time_zone = '+00:00';

-- =====================================================================
-- §3.1 e2e_customer
-- =====================================================================
INSERT INTO e2e_customer (customer_id, customer_code, customer_name, customer_alias, status, credit_limit, created_on, updated_on) VALUES
(1, 'C001', 'ALPHA CUSTOMER',      'Alpha Alias', 'A', 12500.75,         '2024-01-10 00:00:00', NULL),
(2, 'C002', 'BETA CUSTOMER',       NULL,          'I', NULL,             '2024-02-29 00:00:00', NULL),
(3, 'C003', '한국 고객',           '별칭-한글',   'D', 0,                '2024-03-15 00:00:00', NULL),
(4, 'C004', 'HIGH LIMIT CUSTOMER', 'High',        'A', 9999999999999.99, '2024-04-20 00:00:00', NULL);

-- =====================================================================
-- §3.2 e2e_order
-- =====================================================================
INSERT INTO e2e_order (order_id, customer_id, order_no, order_status, total_amount, ordered_at, settled_at, source_comment) VALUES
(1, 1, 'ORD-2024-001', 'NEW',    50.00,  '2024-01-15 00:00:00', NULL,                  'first order'),
(2, 1, 'ORD-2024-002', 'DONE',   125.50, '2024-02-01 00:00:00', '2024-02-05 00:00:00', 'done-order'),
(3, 2, 'ORD-2024-003', 'HOLD',   NULL,   '2024-03-01 00:00:00', NULL,                  '  padded comment  '),
(4, 3, 'ORD-2024-004', 'CANCEL', 0,      '2024-04-10 00:00:00', NULL,                  '취소 주문 !@#');

-- =====================================================================
-- §3.3 e2e_order_line  (composite PK)
-- =====================================================================
INSERT INTO e2e_order_line (order_id, line_no, sku, qty, unit_price, line_note) VALUES
(1, 1, 'SKU-ALPHA',  2,  25.00,  'normal'),
(2, 1, 'SKU-BETA',   1,  125.50, NULL),
(3, 1, 'SKU-HOLD',   10, 9.99,   'hold line'),
(4, 1, 'SKU-CANCEL', 1,  0,      'cancelled');

-- =====================================================================
-- §3.4 e2e_employee  (self-FK chain CEO -> Manager A -> Staff A1)
-- =====================================================================
INSERT INTO e2e_employee (employee_id, manager_id, emp_name, hired_on) VALUES
(1, NULL, 'CEO',       '2020-01-01'),
(2, 1,    'Manager A', '2021-01-01'),
(3, 2,    'Staff A1',  '2022-01-01');

-- =====================================================================
-- §5.1 e2e_text_types  (R_NULL/EMPTY/MIN/MAX/UNICODE/BOUNDARY)
-- MySQL preserves '' as a real empty string (Oracle would coerce to NULL).
-- =====================================================================
-- id=1 R_NULL
INSERT INTO e2e_text_types (id, char_col, varchar_col, text_col, mediumtext_col, longtext_col)
VALUES (1, NULL, NULL, NULL, NULL, NULL);
-- id=2 R_EMPTY
INSERT INTO e2e_text_types (id, char_col, varchar_col, text_col, mediumtext_col, longtext_col)
VALUES (2, '', '', '', '', '');
-- id=3 R_MIN
INSERT INTO e2e_text_types (id, char_col, varchar_col, text_col, mediumtext_col, longtext_col)
VALUES (3, 'A', 'A', 'A', 'A', 'A');
-- id=4 R_MAX  (char_col stays ASCII to survive CMT MySQL → CUBRID byte-counted CHAR mapping)
INSERT INTO e2e_text_types (id, char_col, varchar_col, text_col, mediumtext_col, longtext_col)
VALUES (4,
        'XYZ',
        'ABCDEFGHIJ',
        REPEAT('X', 1000),
        REPEAT('Y', 4000),
        REPEAT('Z', 4000));
-- id=5 R_UNICODE  (char_col / varchar_col NULL because CMT maps both to byte-counted CUBRID
--                  CHAR/VARCHAR; multi-byte coverage is preserved in TEXT/MEDIUMTEXT/LONGTEXT)
INSERT INTO e2e_text_types (id, char_col, varchar_col, text_col, mediumtext_col, longtext_col)
VALUES (5,
        NULL,
        NULL,
        '한국어 漢字 🚀 café',
        '한국어 漢字 cafe punctuation !@#',
        '한국어 漢字 cafe punctuation !@#');
-- id=6 R_BOUNDARY  (whitespace + embedded newlines)
INSERT INTO e2e_text_types (id, char_col, varchar_col, text_col, mediumtext_col, longtext_col)
VALUES (6,
        '   ',
        ' A ',
        CONCAT('line1', CHAR(10), 'line2'),
        CONCAT('line1', CHAR(10), 'line2'),
        CONCAT('line1', CHAR(10), 'line2'));

-- =====================================================================
-- §5.2 e2e_numeric_types  (R_NULL/EMPTY/MIN/MAX/BOUNDARY/REPRESENTATIVE)
-- BIGINT R_MIN uses (-9223372036854775807-1) because MySQL parses the
-- bare literal -9223372036854775808 as -(9223372036854775808) and the
-- inner positive token overflows.
-- =====================================================================
-- id=1 R_NULL
INSERT INTO e2e_numeric_types (id, tinyint_col, smallint_col, mediumint_col, int_col, bigint_col, decimal_col, decimal_p0_col, float_col, double_col)
VALUES (1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
-- id=2 R_EMPTY
INSERT INTO e2e_numeric_types (id, tinyint_col, smallint_col, mediumint_col, int_col, bigint_col, decimal_col, decimal_p0_col, float_col, double_col)
VALUES (2, 0, 0, 0, 0, 0, 0, 0, 0, 0);
-- id=3 R_MIN
INSERT INTO e2e_numeric_types (id, tinyint_col, smallint_col, mediumint_col, int_col, bigint_col, decimal_col, decimal_p0_col, float_col, double_col)
VALUES (3, -128, -32768, -8388608, -2147483648, (-9223372036854775807-1),
        -99999999999999.999999,
        -99999999999999999999999999999999999999,
        -3.4E38, -1.7E308);
-- id=4 R_MAX
INSERT INTO e2e_numeric_types (id, tinyint_col, smallint_col, mediumint_col, int_col, bigint_col, decimal_col, decimal_p0_col, float_col, double_col)
VALUES (4, 127, 32767, 8388607, 2147483647, 9223372036854775807,
        99999999999999.999999,
        99999999999999999999999999999999999999,
        3.4E38, 1.7E308);
-- id=5 R_BOUNDARY
INSERT INTO e2e_numeric_types (id, tinyint_col, smallint_col, mediumint_col, int_col, bigint_col, decimal_col, decimal_p0_col, float_col, double_col)
VALUES (5, 100, 100, 100, 42, 100000000000,
        1234567890.123456,
        12345678901234567890,
        3.14159, 2.7182818);
-- id=6 R_REPRESENTATIVE
INSERT INTO e2e_numeric_types (id, tinyint_col, smallint_col, mediumint_col, int_col, bigint_col, decimal_col, decimal_p0_col, float_col, double_col)
VALUES (6, 7, 7, 7, 100, 1000,
        42.424242,
        10000000000,
        6.283185, 1.41421356);

-- =====================================================================
-- §5.3 e2e_temporal_types  (R_NULL/EMPTY/MIN/MAX/BOUNDARY)
-- timestamp_col stays within MySQL 's 1970-01-01 ~ 2038-01-19 UTC range.
-- datetime_col uses the broader 1000..9999 range.
-- =====================================================================
-- id=1 R_NULL
INSERT INTO e2e_temporal_types (id, date_col, time_col, datetime_col, timestamp_col, year_col)
VALUES (1, NULL, NULL, NULL, NULL, NULL);
-- id=2 R_EMPTY
INSERT INTO e2e_temporal_types (id, date_col, time_col, datetime_col, timestamp_col, year_col)
VALUES (2,
        '1970-01-01',
        '00:00:00.000000',
        '1970-01-01 00:00:00.000000',
        '1970-01-01 00:00:01.000000',
        1970);
-- id=3 R_MIN
INSERT INTO e2e_temporal_types (id, date_col, time_col, datetime_col, timestamp_col, year_col)
VALUES (3,
        '1000-01-01',
        '00:00:00.000001',
        '1000-01-01 00:00:00.000001',
        '1970-01-01 00:00:02.000001',
        1901);
-- id=4 R_MAX
INSERT INTO e2e_temporal_types (id, date_col, time_col, datetime_col, timestamp_col, year_col)
VALUES (4,
        '9999-12-31',
        '23:59:59.999999',
        '9999-12-31 23:59:59.999999',
        '2038-01-19 03:14:07.999999',
        2155);
-- id=5 R_BOUNDARY  (leap day)
INSERT INTO e2e_temporal_types (id, date_col, time_col, datetime_col, timestamp_col, year_col)
VALUES (5,
        '2024-02-29',
        '12:34:56.123456',
        '2024-02-29 12:34:56.123456',
        '2024-02-29 12:34:56.123456',
        2024);

-- =====================================================================
-- §5.4 e2e_binary_types  (R_NULL/EMPTY/MIN/MAX/BOUNDARY)
-- BINARY(8) right-pads with 0x00; VARBINARY preserves exact length;
-- BIT(64) accepts both b'...' and 0x... literals.
-- =====================================================================
-- id=1 R_NULL
INSERT INTO e2e_binary_types (id, binary_col, varbinary_col, bit_col, blob_col, longblob_col)
VALUES (1, NULL, NULL, NULL, NULL, NULL);
-- id=2 R_EMPTY
INSERT INTO e2e_binary_types (id, binary_col, varbinary_col, bit_col, blob_col, longblob_col)
VALUES (2, X'0000000000000000', X'', b'0', X'', NULL);
-- id=3 R_MIN
INSERT INTO e2e_binary_types (id, binary_col, varbinary_col, bit_col, blob_col, longblob_col)
VALUES (3, X'0000000000000000', X'00', 0x0000000000000000, X'00', NULL);
-- id=4 R_MAX
INSERT INTO e2e_binary_types (id, binary_col, varbinary_col, bit_col, blob_col, longblob_col)
VALUES (4,
        X'FFFFFFFFFFFFFFFF',
        X'00FF00FF00FF00FF00FF00FF00FF00FF',
        0xFFFFFFFFFFFFFFFF,
        X'00FF00FF00FF00FF00FF00FF00FF00FF',
        REPEAT(X'CAFEBABE', 100000));
-- id=5 R_BOUNDARY
INSERT INTO e2e_binary_types (id, binary_col, varbinary_col, bit_col, blob_col, longblob_col)
VALUES (5,
        X'00000000CAFEBABE',
        X'CAFEBABE',
        0x00000000CAFEBABE,
        X'CAFEBABE',
        X'CAFEBABE');

-- =====================================================================
-- §5.5 e2e_mariadb_enum_types  (MariaDB extension)
-- Invalid ENUM strings are rejected by MariaDB 's strict mode; not seeded.
-- =====================================================================
INSERT INTO e2e_mariadb_enum_types (id, status_enum) VALUES
(1, NULL),
(2, 'NEW'),
(3, 'CANCEL'),
(4, 'DONE');

-- =====================================================================
-- §5.6 e2e_mariadb_set_types is anti-coverage (no table, no inserts).
-- See SEED_SPEC §1 / V4__schema_extensions.sql for the rationale.
-- =====================================================================

-- =====================================================================
-- §5.7 e2e_mariadb_json_types  (TENTATIVE — MariaDB extension)
-- MariaDB JSON is a LONGTEXT alias; CMT may pass it through silently.
-- =====================================================================
INSERT INTO e2e_mariadb_json_types (id, payload) VALUES
(1, NULL),
(2, '{}'),
(3, '[]'),
(4, '{"a":[1,2,{"b":"한국"}]}'),
(5, '{"deeply":{"nested":{"json":[{"value":1},{"value":2},{"value":3},{"value":4},{"value":5}]}},"unicode":["한국","漢字","emoji 🚀","accents café"],"numbers":[1.5,2.5,3.5,1e10,-1e-10],"flags":[true,false,null]}');
