-- ============================================================
-- MAIN_SCHEMA: synonym to ref_schema object (TENTATIVE)
-- Per docs/seed/mssql/SEED_SPEC.md §4.3
--
-- The cross-schema GRANT applied in init/00_prepare_database.sql gives
-- main_user SELECT/INSERT/UPDATE/DELETE on SCHEMA::ref_schema, which is
-- what lets this synonym resolve at runtime.
--
-- Why synonym is included for MSSQL but not for MySQL/MariaDB:
-- MSSQLSchemaFetcher overrides buildSynonym (line 619 of
-- MSSQLSchemaFetcher.java) — it actively reads the synonym catalog,
-- whereas the MySQL/MariaDB fetchers inherit the parent 's no-op
-- default. First run confirms whether the round-trip to CUBRID
-- (CREATE SYNONYM on the target) succeeds end-to-end.
-- ============================================================

CREATE SYNONYM main_schema.e2e_ref_audit_syn FOR ref_schema.e2e_ref_audit;
GO
