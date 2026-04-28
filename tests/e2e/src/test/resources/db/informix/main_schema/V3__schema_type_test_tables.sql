-- ============================================================
-- MAIN_SCHEMA: type test tables
-- Per docs/seed/informix/SEED_SPEC.md §5.1 - §5.5
--
-- Each *_types table holds a battery of representative rows
-- for one type family (text / numeric / temporal / binary /
-- misc). Row IDs map to the COMMON_SEED_CONTRACT §4 row table:
--   1 = R_NULL, 2 = R_EMPTY, 3 = R_MIN, 4 = R_MAX,
--   5 = R_UNICODE, 6 = R_BOUNDARY, 7+ = R_REPRESENTATIVE.
--
-- Notes specific to Informix:
--   - SERIAL / SERIAL8 columns reset to user-supplied value when
--     the INSERT provides a non-zero value. Data inserts in V99
--     use explicit values to keep ordering deterministic.
--   - BYTE / BLOB are loaded with raw hex constants in V99.
--   - LVARCHAR(2048) is the maximum row-stored varchar; bigger
--     payloads use TEXT (row-overflow) or CLOB (smart blob).
--   - JSON / BSON are Informix-native semi-structured types
--     (CMT type-map maps both to CUBRID 's JSON; first run
--     confirms semantics — see SEED_SPEC §1 tentative item 4).
-- ============================================================

CREATE TABLE e2e_text_types (
    id            INTEGER       NOT NULL,
    char_col      CHAR(3),
    varchar_col   VARCHAR(10),
    nchar_col     NCHAR(10),
    nvarchar_col  NVARCHAR(40),
    lvarchar_col  LVARCHAR(2048),
    text_col      TEXT,
    clob_col      CLOB,
    PRIMARY KEY (id) CONSTRAINT pk_e2e_text_types
);

CREATE TABLE e2e_numeric_types (
    id              INTEGER         NOT NULL,
    smallint_col    SMALLINT,
    integer_col     INTEGER,
    bigint_col      BIGINT,
    int8_col        INT8,
    decimal_col     DECIMAL(20,4),
    numeric_col     NUMERIC(15,3),
    money_col       MONEY(15,2),
    smallfloat_col  SMALLFLOAT,
    float_col       FLOAT,
    serial_col      SERIAL,
    serial8_col     SERIAL8,
    PRIMARY KEY (id) CONSTRAINT pk_e2e_numeric_types
);

CREATE TABLE e2e_temporal_types (
    id              INTEGER  NOT NULL,
    date_col        DATE,
    dt_yts_col      DATETIME YEAR TO SECOND,
    dt_ytf3_col     DATETIME YEAR TO FRACTION(3),
    dt_ytf5_col     DATETIME YEAR TO FRACTION(5),
    interval_ds_col INTERVAL DAY(2) TO SECOND,
    interval_ym_col INTERVAL YEAR(4) TO MONTH,
    PRIMARY KEY (id) CONSTRAINT pk_e2e_temporal_types
);

CREATE TABLE e2e_binary_types (
    id        INTEGER NOT NULL,
    byte_col  BYTE,
    blob_col  BLOB,
    PRIMARY KEY (id) CONSTRAINT pk_e2e_binary_types
);

CREATE TABLE e2e_misc_types (
    id           INTEGER NOT NULL,
    boolean_col  BOOLEAN,
    json_col     JSON,
    bson_col     BSON,
    PRIMARY KEY (id) CONSTRAINT pk_e2e_misc_types
);
