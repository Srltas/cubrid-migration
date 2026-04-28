-- ============================================================
-- MAIN_SCHEMA: seed data (business + type test + extensions)
-- Per docs/seed/mssql/SEED_SPEC.md §3 / §5
--
-- IDENTITY_INSERT must be enabled per table to insert explicit values
-- into IDENTITY columns; remember to flip OFF afterward (only one
-- table at a time per session).
-- ============================================================

-- =====================================================================
-- §3.1 e2e_customer
-- =====================================================================
SET IDENTITY_INSERT e2e_customer ON;
GO
INSERT INTO e2e_customer (customer_id, customer_code, customer_name, customer_alias, status, credit_limit, created_on, updated_on) VALUES
(1, 'C001', N'ALPHA CUSTOMER',      N'Alpha Alias', 'A', 12500.75,         '2024-01-10 00:00:00', NULL),
(2, 'C002', N'BETA CUSTOMER',       NULL,           'I', NULL,             '2024-02-29 00:00:00', NULL),
(3, 'C003', N'한국 고객',           N'별칭-한글',   'D', 0,                '2024-03-15 00:00:00', NULL),
(4, 'C004', N'HIGH LIMIT CUSTOMER', N'High',        'A', 9999999999999.99, '2024-04-20 00:00:00', NULL);
GO
SET IDENTITY_INSERT e2e_customer OFF;
GO

-- =====================================================================
-- §3.2 e2e_order
-- =====================================================================
SET IDENTITY_INSERT e2e_order ON;
GO
INSERT INTO e2e_order (order_id, customer_id, order_no, order_status, total_amount, ordered_at, settled_at, source_comment) VALUES
(1, 1, N'ORD-2024-001', N'NEW',    50.00,  '2024-01-15 00:00:00', NULL,                  N'first order'),
(2, 1, N'ORD-2024-002', N'DONE',   125.50, '2024-02-01 00:00:00', '2024-02-05 00:00:00', N'done-order'),
(3, 2, N'ORD-2024-003', N'HOLD',   NULL,   '2024-03-01 00:00:00', NULL,                  N'  padded comment  '),
(4, 3, N'ORD-2024-004', N'CANCEL', 0,      '2024-04-10 00:00:00', NULL,                  N'취소 주문 !@#');
GO
SET IDENTITY_INSERT e2e_order OFF;
GO

-- =====================================================================
-- §3.3 e2e_order_line  (composite PK, no IDENTITY)
-- =====================================================================
INSERT INTO e2e_order_line (order_id, line_no, sku, qty, unit_price, line_note) VALUES
(1, 1, N'SKU-ALPHA',  2,  25.00,  N'normal'),
(2, 1, N'SKU-BETA',   1,  125.50, NULL),
(3, 1, N'SKU-HOLD',   10, 9.99,   N'hold line'),
(4, 1, N'SKU-CANCEL', 1,  0,      N'cancelled');
GO

-- =====================================================================
-- §3.4 e2e_employee  (self-FK chain CEO -> Manager A -> Staff A1)
-- =====================================================================
INSERT INTO e2e_employee (employee_id, manager_id, emp_name, hired_on) VALUES
(1, NULL, N'CEO',       '2020-01-01'),
(2, 1,    N'Manager A', '2021-01-01'),
(3, 2,    N'Staff A1',  '2022-01-01');
GO

-- =====================================================================
-- §5.1 e2e_text_types
-- char_col / varchar_col / nchar_col stay ASCII at R_MAX/R_UNICODE
-- because CMT moves the byte budget verbatim to CUBRID 's byte-counted
-- CHAR/VARCHAR.
-- =====================================================================
INSERT INTO e2e_text_types (id, char_col, varchar_col, nchar_col, nvarchar_col, text_col, ntext_col)
VALUES (1, NULL, NULL, NULL, NULL, NULL, NULL);
GO
INSERT INTO e2e_text_types (id, char_col, varchar_col, nchar_col, nvarchar_col, text_col, ntext_col)
VALUES (2, '', '', N'', N'', '', N'');
GO
INSERT INTO e2e_text_types (id, char_col, varchar_col, nchar_col, nvarchar_col, text_col, ntext_col)
VALUES (3, 'A', 'A', N'A', N'A', 'A', N'A');
GO
INSERT INTO e2e_text_types (id, char_col, varchar_col, nchar_col, nvarchar_col, text_col, ntext_col)
VALUES (4,
        'XYZ',
        'ABCDEFGHIJ',
        N'XYZ',
        N'한국어 漢字 cafe',
        REPLICATE('X', 1000),
        REPLICATE(N'Y', 4000));
GO
INSERT INTO e2e_text_types (id, char_col, varchar_col, nchar_col, nvarchar_col, text_col, ntext_col)
VALUES (5,
        NULL,
        NULL,
        NULL,
        N'한中Ω 漢字',
        N'한국어 漢字 🚀 café',
        N'한국어 漢字 cafe punctuation !@#');
GO
INSERT INTO e2e_text_types (id, char_col, varchar_col, nchar_col, nvarchar_col, text_col, ntext_col)
VALUES (6,
        '   ',
        ' A ',
        N'   ',
        N' A ',
        'line1' + CHAR(10) + 'line2',
        N'line1' + NCHAR(10) + N'line2');
GO

-- =====================================================================
-- §5.2 e2e_numeric_types
-- TINYINT in MSSQL is unsigned 0..255 (different from MySQL/MariaDB).
-- BIGINT min/max literal works directly on MSSQL (no parser overflow
-- issue like MySQL had).
-- MONEY range: ±922,337,203,685,477.5807; SMALLMONEY: ±214,748.3647.
-- =====================================================================
INSERT INTO e2e_numeric_types (id, tinyint_col, smallint_col, int_col, bigint_col, decimal_col, decimal_p0_col, float_col, double_col, money_col, smallmoney_col)
VALUES (1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
GO
INSERT INTO e2e_numeric_types (id, tinyint_col, smallint_col, int_col, bigint_col, decimal_col, decimal_p0_col, float_col, double_col, money_col, smallmoney_col)
VALUES (2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
GO
INSERT INTO e2e_numeric_types (id, tinyint_col, smallint_col, int_col, bigint_col, decimal_col, decimal_p0_col, float_col, double_col, money_col, smallmoney_col)
VALUES (3, 0, -32768, -2147483648, -9223372036854775808,
        -99999999999999.999999,
        -99999999999999999999999999999999999999,
        -3.4E38, -1.7E308,
        -922337203685477.5807,
        -214748.3647);
GO
INSERT INTO e2e_numeric_types (id, tinyint_col, smallint_col, int_col, bigint_col, decimal_col, decimal_p0_col, float_col, double_col, money_col, smallmoney_col)
VALUES (4, 255, 32767, 2147483647, 9223372036854775807,
        99999999999999.999999,
        99999999999999999999999999999999999999,
        3.4E38, 1.7E308,
        922337203685477.5807,
        214748.3647);
GO
INSERT INTO e2e_numeric_types (id, tinyint_col, smallint_col, int_col, bigint_col, decimal_col, decimal_p0_col, float_col, double_col, money_col, smallmoney_col)
VALUES (5, 100, 100, 42, 100000000000,
        1234567890.123456,
        12345678901234567890,
        3.14159, 2.7182818,
        1234.56,
        12.34);
GO
INSERT INTO e2e_numeric_types (id, tinyint_col, smallint_col, int_col, bigint_col, decimal_col, decimal_p0_col, float_col, double_col, money_col, smallmoney_col)
VALUES (6, 7, 7, 100, 1000,
        42.424242,
        10000000000,
        6.283185, 1.41421356,
        99.99,
        9.99);
GO

-- =====================================================================
-- §5.3 e2e_temporal_types
-- DATETIME range starts at 1753-01-01; DATETIME2 supports 0001..9999
-- but we keep R_MIN at 1753 for the same row 's DATETIME value.
-- SMALLDATETIME range: 1900-01-01 ~ 2079-06-06 (1-minute precision).
-- DATETIMEOFFSET stores both a UTC instant and the original offset.
-- =====================================================================
INSERT INTO e2e_temporal_types (id, date_col, time_col, datetime_col, datetime2_col, smalldatetime_col, datetimeoffset_col)
VALUES (1, NULL, NULL, NULL, NULL, NULL, NULL);
GO
INSERT INTO e2e_temporal_types (id, date_col, time_col, datetime_col, datetime2_col, smalldatetime_col, datetimeoffset_col)
VALUES (2,
        '1900-01-01',
        '00:00:00.0000000',
        '1900-01-01 00:00:00.000',
        '1900-01-01 00:00:00.0000000',
        '1900-01-01 00:00:00',
        '1900-01-01 00:00:00.0000000 +00:00');
GO
INSERT INTO e2e_temporal_types (id, date_col, time_col, datetime_col, datetime2_col, smalldatetime_col, datetimeoffset_col)
VALUES (3,
        '1753-01-01',
        '00:00:00.0000001',
        '1753-01-01 00:00:00.000',
        '1753-01-01 00:00:00.0000001',
        '1900-01-01 00:01:00',
        '1753-01-01 00:00:00.0000001 +00:00');
GO
INSERT INTO e2e_temporal_types (id, date_col, time_col, datetime_col, datetime2_col, smalldatetime_col, datetimeoffset_col)
VALUES (4,
        '9999-12-31',
        '23:59:59.9999999',
        '9999-12-31 23:59:59.997',
        '9999-12-31 23:59:59.9999999',
        '2079-06-06 23:59:00',
        '9999-12-31 23:59:59.9999999 +14:00');
GO
INSERT INTO e2e_temporal_types (id, date_col, time_col, datetime_col, datetime2_col, smalldatetime_col, datetimeoffset_col)
VALUES (5,
        '2024-02-29',
        '12:34:56.1234567',
        '2024-02-29 12:34:56.123',
        '2024-02-29 12:34:56.1234567',
        '2024-02-29 12:35:00',
        '2024-04-20 12:00:00.0000000 +09:00');
GO

-- =====================================================================
-- §5.4 e2e_binary_types
-- BIT is single-bit boolean (0/1/NULL). BINARY(n) right-pads with 0x00.
-- VARBINARY(MAX) capped at 4 KB per SEED_SPEC §1.1.
-- =====================================================================
INSERT INTO e2e_binary_types (id, binary_col, varbinary_col, varbinary_max_col, image_col, bit_col)
VALUES (1, NULL, NULL, NULL, NULL, NULL);
GO
INSERT INTO e2e_binary_types (id, binary_col, varbinary_col, varbinary_max_col, image_col, bit_col)
VALUES (2, 0x0000000000000000, 0x, 0x, 0x, 0);
GO
INSERT INTO e2e_binary_types (id, binary_col, varbinary_col, varbinary_max_col, image_col, bit_col)
VALUES (3, 0x0000000000000000, 0x00, 0x00, 0x00, 0);
GO
-- varbinary_max_col R_MAX kept compact (32 bytes) because MSSQL 's REPLICATE
-- only operates on character types, not VARBINARY. Building a kilobyte-scale
-- binary literal here would require CONVERT(VARBINARY(MAX), REPLICATE(CONVERT(
-- VARCHAR, 'CAFEBABE'), 1000), 2) — extra complexity for no extra E2E signal.
INSERT INTO e2e_binary_types (id, binary_col, varbinary_col, varbinary_max_col, image_col, bit_col)
VALUES (4,
        0xFFFFFFFFFFFFFFFF,
        0x00FF00FF00FF00FF00FF00FF00FF00FF,
        0x00FF00FF00FF00FF00FF00FF00FF00FF00FF00FF00FF00FF00FF00FF00FF00FF,
        0x00FF00FF00FF00FF00FF00FF00FF00FF,
        1);
GO
INSERT INTO e2e_binary_types (id, binary_col, varbinary_col, varbinary_max_col, image_col, bit_col)
VALUES (5,
        0x00000000CAFEBABE,
        0xCAFEBABE,
        0xCAFEBABE,
        0xCAFEBABE,
        1);
GO

-- =====================================================================
-- §5.5 e2e_mssql_uniqueidentifier_types  (MSSQL extension)
-- =====================================================================
INSERT INTO e2e_mssql_uniqueidentifier_types (id, uid_col) VALUES
(1, NULL),
(2, '00000000-0000-0000-0000-000000000000'),
(3, NEWID()),
(4, 'a1b2c3d4-e5f6-7890-1234-567890abcdef');
GO
