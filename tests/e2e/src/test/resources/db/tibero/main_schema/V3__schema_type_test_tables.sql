-- ============================================================
-- MAIN_SCHEMA: type test tables.
-- Per docs/seed/tibero/SEED_SPEC.md §5.1 ~ §5.5 (Oracle SPEC §5.1 ~ §5.5
-- 정본 재사용). Tibero 의 NUMBER / VARCHAR2 / DATE / TIMESTAMP / INTERVAL /
-- CLOB / BLOB / RAW / BINARY_FLOAT / BINARY_DOUBLE / ROWID 은 Oracle 과
-- 동일한 의미를 가지므로 DDL 도 그대로다.
--
-- Battery row 의미는 COMMON_SEED_CONTRACT.md §4 의 canonical row IDs
-- (1=R_NULL, 2=R_EMPTY, 3=R_MIN, 4=R_MAX, 5=R_UNICODE/R_BOUNDARY,
-- 6=R_BOUNDARY/R_REPRESENTATIVE) 를 따르고, 실제 row 값은
-- main_schema/V99__data.sql 에 들어간다.
--
-- TENTATIVE (Phase 13.1 외 보류) — §0 참고:
--   • Tibero 고유 §5.6 e2e_tibero_semi_structured_types (JSON / XMLTYPE)
--     는 본 phase 에서 만들지 않는다.
--
-- Anti-coverage 확정 (Phase 13.1 1 차 실행 결과):
--   • UROWID 타입 — Tibero 7 SQL parser 가 컬럼 타입으로 거부
--     ("JDBC-7454: Datatype 'MAIN_SCHEMA.UROWID' is invalid"). 본 시드에서
--     아예 컬럼을 만들지 않는다. tibero/SEED_SPEC.md §1 anti-coverage
--     표에서 TENTATIVE 마크 제거.
-- ============================================================

-- ====== §5.1 e2e_text_types ======================================
CREATE TABLE e2e_text_types (
    id               NUMBER(10) NOT NULL,
    char_byte_col    CHAR(3 BYTE),
    char_char_col    CHAR(3 CHAR),
    varchar_byte_col VARCHAR2(10 BYTE),
    varchar_char_col VARCHAR2(10 CHAR),
    nchar_col        NCHAR(10),
    nvarchar_col     NVARCHAR2(40),
    clob_col         CLOB,
    nclob_col        NCLOB,
    CONSTRAINT pk_e2e_text_types PRIMARY KEY (id)
);

-- ====== §5.2 e2e_numeric_types ===================================
CREATE TABLE e2e_numeric_types (
    id                NUMBER(10) NOT NULL,
    integer_col       INTEGER,
    decimal_col       DECIMAL(20,4),
    number_p0_col     NUMBER(38,0),
    number_ps_col     NUMBER(20,6),
    number_round_col  NUMBER(8,-2),
    number_any_col    NUMBER,
    float_col         FLOAT(30),
    real_col          REAL,
    binary_float_col  BINARY_FLOAT,
    binary_double_col BINARY_DOUBLE,
    CONSTRAINT pk_e2e_numeric_types PRIMARY KEY (id)
);

-- ====== §5.3 e2e_temporal_types ==================================
CREATE TABLE e2e_temporal_types (
    id              NUMBER(10) NOT NULL,
    date_col        DATE,
    ts6_col         TIMESTAMP(6),
    ts9_col         TIMESTAMP(9),
    tsltz6_col      TIMESTAMP(6) WITH LOCAL TIME ZONE,
    tstz9_col       TIMESTAMP(9) WITH TIME ZONE,
    interval_ds_col INTERVAL DAY(2) TO SECOND(6),
    interval_ym_col INTERVAL YEAR(4) TO MONTH,
    CONSTRAINT pk_e2e_temporal_types PRIMARY KEY (id)
);

-- ====== §5.4 e2e_binary_types ====================================
CREATE TABLE e2e_binary_types (
    id       NUMBER(10) NOT NULL,
    raw_col  RAW(16),
    blob_col BLOB,
    CONSTRAINT pk_e2e_binary_types PRIMARY KEY (id)
);

-- ====== §5.5 e2e_oracle_locator_types (Oracle extension reused) ==
-- urowid_col UROWID 는 Phase 13.1 1차 실행에서 Tibero parser 가 거부 →
-- anti-coverage 로 flip (위 헤더 주석 참고). ROWID 만 남긴다.
CREATE TABLE e2e_oracle_locator_types (
    id         NUMBER(10) NOT NULL,
    rowid_col  ROWID,
    CONSTRAINT pk_e2e_oracle_locator_types PRIMARY KEY (id)
);
