-- ============================================================
-- MAIN_SCHEMA: extensions (sequences)
-- Per docs/seed/informix/SEED_SPEC.md §4.1
--
-- Informix native CREATE SEQUENCE (11.70+). CMT
-- InformixSchemaFetcher.buildSequence reads syssequences.
-- Used as a separate auto-increment path from BIGSERIAL
-- (which is a column modifier, not a sequence object).
--
-- START WITH 5 keeps the seed rows in 1..4 untouched and
-- allows next nextval to assign 5 onwards (matches the
-- pattern in Oracle / Tibero specs).
-- ============================================================

CREATE SEQUENCE e2e_customer_seq
    START WITH 5
    INCREMENT BY 1
    NOCYCLE
    NOCACHE;
