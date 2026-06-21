-- ============================================================
-- MAIN_SCHEMA: type test tables (core; MSSQL-only extensions in V4)
-- Per docs/seed/mssql/SEED_SPEC.md §5.1 ~ §5.4
--
-- Each table holds the canonical R_NULL/R_EMPTY/R_MIN/R_MAX/(R_UNICODE)/
-- R_BOUNDARY (and §5.2 R_REPRESENTATIVE) battery rows. Row literals
-- live in V99__data.sql.
-- ============================================================

-- ====== §5.1 e2e_text_types ======================================
-- MSSQL CHAR/VARCHAR are byte-counted (default code page); NCHAR/NVARCHAR
-- are character-counted (UCS-2). CMT moves the byte budget verbatim to
-- CUBRID 's byte-counted CHAR/VARCHAR, so R_MAX/R_UNICODE keep multi-byte
-- content out of the byte-counted columns and put it in nvarchar /
-- VARCHAR(MAX) / NVARCHAR(MAX).
CREATE TABLE e2e_text_types (
    id           INT NOT NULL,
    char_col     CHAR(3),
    varchar_col  VARCHAR(10),
    nchar_col    NCHAR(3),
    nvarchar_col NVARCHAR(40),
    text_col     VARCHAR(MAX),
    ntext_col    NVARCHAR(MAX),
    CONSTRAINT pk_e2e_text_types PRIMARY KEY (id)
);
GO

-- ====== §5.2 e2e_numeric_types ===================================
-- MSSQL TINYINT is unsigned (0~255), unlike MySQL/MariaDB (signed).
-- MONEY (8 byte) and SMALLMONEY (4 byte) are MSSQL-only; the values
-- live here rather than in a separate extension table.
CREATE TABLE e2e_numeric_types (
    id              INT NOT NULL,
    tinyint_col     TINYINT,
    smallint_col    SMALLINT,
    int_col         INT,
    bigint_col      BIGINT,
    decimal_col     DECIMAL(20, 6),
    decimal_p0_col  DECIMAL(38, 0),
    float_col       FLOAT(24),  -- = REAL
    double_col      FLOAT(53),  -- = DOUBLE
    money_col       MONEY,
    smallmoney_col  SMALLMONEY,
    CONSTRAINT pk_e2e_numeric_types PRIMARY KEY (id)
);
GO

-- ====== §5.3 e2e_temporal_types ==================================
-- DATETIME range starts at 1753-01-01 (3.33ms precision). DATETIME2
-- supports 0001..9999 with 100ns precision; we keep R_MIN at 1753 to
-- stay valid for the DATETIME column on the same row.
-- DATETIMEOFFSET is timezone-aware (DATETIME2 + offset).
-- SMALLDATETIME has 1-minute precision (1900..2079).
CREATE TABLE e2e_temporal_types (
    id                  INT NOT NULL,
    date_col            DATE,
    time_col            TIME(7),
    datetime_col        DATETIME,
    datetime2_col       DATETIME2(7),
    smalldatetime_col   SMALLDATETIME,
    datetimeoffset_col  DATETIMEOFFSET(7),
    CONSTRAINT pk_e2e_temporal_types PRIMARY KEY (id)
);
GO

-- ====== §5.4 e2e_binary_types ====================================
-- BINARY(n) right-pads with 0x00; VARBINARY preserves exact length.
-- BIT is a single-bit boolean (0/1/NULL), unlike MySQL/MariaDB BIT(n).
-- IMAGE is deprecated but kept in CMT type-map.
CREATE TABLE e2e_binary_types (
    id                INT NOT NULL,
    binary_col        BINARY(8),
    varbinary_col     VARBINARY(64),
    varbinary_max_col VARBINARY(MAX),
    image_col         IMAGE,
    bit_col           BIT,
    CONSTRAINT pk_e2e_binary_types PRIMARY KEY (id)
);
GO
