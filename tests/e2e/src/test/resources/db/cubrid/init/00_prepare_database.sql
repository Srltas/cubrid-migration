-- ============================================================
-- CUBRID two-user setup for E2E seed.
-- Executed as dba against an empty cubdb.
-- Per docs/seed/cubrid/SEED_SPEC.md §2.
-- ============================================================

CREATE USER REF_SCHEMA  PASSWORD 'cmt';
CREATE USER MAIN_SCHEMA PASSWORD 'cmt';
