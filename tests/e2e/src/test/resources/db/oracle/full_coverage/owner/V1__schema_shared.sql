-- ============================================================
-- Oracle full_coverage / CMT_OWNER schema: shared table (minimal)
--
-- This file has one purpose only: validate the two-user cross-schema path.
--   1. One shared table lives in CMT_OWNER
--   2. V3__grants.sql grants access to CMT_TEST
--   3. CMT_TEST references it through a private synonym in test/V4__synonyms.sql
--
-- The full business schema (customer/orders/order_line, FK, indexes, view)
-- now lives under CMT_TEST, which is the primary migration target. The owner
-- schema covers only the cross-schema grant/synonym export path.
-- ============================================================

CREATE SEQUENCE ora_cov_shared_audit_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NOMAXVALUE
    NOCACHE
    NOCYCLE;

CREATE TABLE ora_cov_shared_audit (
    audit_id     NUMBER(10)      NOT NULL,
    audit_source VARCHAR2(40)    NOT NULL,
    audit_note   VARCHAR2(200),
    created_on   DATE            DEFAULT SYSDATE NOT NULL,
    CONSTRAINT pk_cov_shared_audit PRIMARY KEY (audit_id)
);
