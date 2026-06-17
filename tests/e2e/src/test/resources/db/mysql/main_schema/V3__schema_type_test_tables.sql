-- ============================================================
-- MAIN_SCHEMA: type test tables (core; MySQL extensions live in V4)
-- Per docs/seed/mysql/SEED_SPEC.md §5.1 ~ §5.4
--
-- Each table holds the canonical R_NULL/R_EMPTY/R_MIN/R_MAX/(R_UNICODE)/
-- R_BOUNDARY (and §5.2 also R_REPRESENTATIVE) battery rows. The actual
-- row literals are inserted from main_schema/V99__data.sql.
-- ============================================================

-- ====== §5.1 e2e_text_types ======================================
-- MySQL CHAR(n)/VARCHAR(n) are character-counted (utf8mb4), so multi-byte
-- glyphs fit within the declared length. NCHAR/NVARCHAR aliases are not
-- modeled here per SEED_SPEC §1.1 (they collapse to VARCHAR utf8 + COLLATE
-- on MySQL).
CREATE TABLE e2e_text_types (
    id             INT NOT NULL,
    char_col       CHAR(3),
    varchar_col    VARCHAR(10),
    text_col       TEXT,
    mediumtext_col MEDIUMTEXT,
    longtext_col   LONGTEXT,
    CONSTRAINT pk_e2e_text_types PRIMARY KEY (id)
);

-- ====== §5.2 e2e_numeric_types ===================================
-- IEEE special values (Inf/NaN) are excluded from core: MySQL strict mode
-- (default in 8.0) rejects them on INSERT.
CREATE TABLE e2e_numeric_types (
    id             INT NOT NULL,
    tinyint_col    TINYINT,
    smallint_col   SMALLINT,
    mediumint_col  MEDIUMINT,
    int_col        INT,
    bigint_col     BIGINT,
    decimal_col    DECIMAL(20, 6),
    decimal_p0_col DECIMAL(38, 0),
    float_col      FLOAT,
    double_col     DOUBLE,
    CONSTRAINT pk_e2e_numeric_types PRIMARY KEY (id)
);

-- ====== §5.3 e2e_temporal_types ==================================
-- timestamp_col is constrained to 1970-01-01 ~ 2038-01-19 UTC (§1.1).
-- Wider dates (1000..9999) live in datetime_col. NULL DEFAULT NULL on
-- timestamp_col disables MySQL 's implicit DEFAULT CURRENT_TIMESTAMP /
-- ON UPDATE CURRENT_TIMESTAMP, which would otherwise rewrite the seed
-- value silently.
CREATE TABLE e2e_temporal_types (
    id            INT NOT NULL,
    date_col      DATE,
    time_col      TIME(6),
    datetime_col  DATETIME(6),
    timestamp_col TIMESTAMP(6) NULL DEFAULT NULL,
    year_col      YEAR,
    CONSTRAINT pk_e2e_temporal_types PRIMARY KEY (id)
);

-- ====== §5.4 e2e_binary_types ====================================
-- BINARY(n) right-pads with 0x00; VARBINARY(n) preserves exact length.
-- BIT(64) accepts both b'...' and 0x... literals.
CREATE TABLE e2e_binary_types (
    id            INT NOT NULL,
    binary_col    BINARY(8),
    varbinary_col VARBINARY(64),
    bit_col       BIT(64),
    blob_col      BLOB,
    longblob_col  LONGBLOB,
    CONSTRAINT pk_e2e_binary_types PRIMARY KEY (id)
);
