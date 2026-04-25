-- ============================================================
-- Oracle full_coverage / CMT_TEST schema: datatype-heavy tables
--
-- Notes:
--   - ARRAY/collection types are intentionally excluded. CMT's
--     Oracle column metadata path is based on ALL_TAB_COLUMNS.DATA_TYPE,
--     which does not reliably expose named collection columns as ARRAY.
--   - BFILE is included at schema level, but seed data keeps it NULL
--     because app-user-only Flyway migrations cannot safely provision
--     Oracle DIRECTORY objects and external files.
-- ============================================================

CREATE TABLE ora_cov_text_types (
    text_id            NUMBER(10)        NOT NULL,
    char_byte_col      CHAR(3 BYTE),
    char_char_col      CHAR(3 CHAR),
    varchar_byte_col   VARCHAR2(10 BYTE),
    varchar_char_col   VARCHAR2(10 CHAR),
    nchar_col          NCHAR(10),
    nvarchar_col       NVARCHAR2(40),
    clob_col           CLOB,
    nclob_col          NCLOB,
    long_col           LONG,
    CONSTRAINT pk_cov_text_types PRIMARY KEY (text_id)
);

CREATE TABLE ora_cov_binary_types (
    binary_id          NUMBER(10)        NOT NULL,
    raw_col            RAW(16),
    long_raw_col       LONG RAW,
    blob_col           BLOB,
    bfile_col          BFILE,
    CONSTRAINT pk_cov_binary_types PRIMARY KEY (binary_id)
);

CREATE TABLE ora_cov_numeric_types (
    numeric_id         NUMBER(10)        NOT NULL,
    integer_col        INTEGER,
    decimal_col        DECIMAL(20, 4),
    number_p0_col      NUMBER(38, 0),
    number_ps_col      NUMBER(20, 6),
    number_round_col   NUMBER(8, -2),
    number_any_col     NUMBER,
    float_col          FLOAT(30),
    real_col           REAL,
    binary_float_col   BINARY_FLOAT,
    binary_double_col  BINARY_DOUBLE,
    CONSTRAINT pk_cov_numeric_types PRIMARY KEY (numeric_id)
);

CREATE TABLE ora_cov_temporal_types (
    temporal_id        NUMBER(10)                         NOT NULL,
    date_col           DATE,
    ts6_col            TIMESTAMP(6),
    ts9_col            TIMESTAMP(9),
    tsltz6_col         TIMESTAMP(6) WITH LOCAL TIME ZONE,
    tstz9_col          TIMESTAMP(9) WITH TIME ZONE,
    interval_ds_col    INTERVAL DAY(2) TO SECOND(6),
    interval_ym_col    INTERVAL YEAR(4) TO MONTH,
    CONSTRAINT pk_cov_temporal_types PRIMARY KEY (temporal_id)
);

CREATE TABLE ora_cov_locator_types (
    locator_id         NUMBER(10)        NOT NULL,
    rowid_col          ROWID,
    urowid_col         UROWID,
    CONSTRAINT pk_cov_locator_types PRIMARY KEY (locator_id)
);
