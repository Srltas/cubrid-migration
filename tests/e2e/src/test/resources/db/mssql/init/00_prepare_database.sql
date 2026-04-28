-- ============================================================
-- MSSQL: prepare e2e_db database, logins, users, and schemas
-- Per docs/seed/mssql/SEED_SPEC.md §2 Schema Setup
--
-- Executed via sqlcmd inside the MSSQL container (called by
-- MsSqlContainer.start() after the server is ready). sqlcmd handles
-- GO batch separators natively; do not use Flyway for this script.
--
-- After this script:
--   - e2e_db database exists
--   - main_user and ref_user logins/users exist
--   - main_schema (owned by main_user) and ref_schema (owned by ref_user)
--     exist in e2e_db
--   - main_user has SELECT/INSERT/UPDATE/DELETE on SCHEMA::ref_schema
--     so cross-schema synonym (main_schema.e2e_ref_audit_syn ->
--     ref_schema.e2e_ref_audit) can resolve at runtime
--
-- Multi-schema (main_schema + ref_schema in one e2e_db) is TENTATIVE per
-- SEED_SPEC §2: first run confirms whether MSSQLSchemaFetcher pulls
-- both schemas. If it doesn 't, ref_schema collapses to anti-coverage
-- like MySQL/MariaDB.
-- ============================================================

CREATE DATABASE e2e_db;
GO

USE e2e_db;
GO

-- Logins are server-scoped; passwords meet the default complexity policy
-- (8+ chars + 3 of {upper, lower, digit, special}).
CREATE LOGIN main_user WITH PASSWORD = N'CmtMain#2026', CHECK_POLICY = OFF;
CREATE LOGIN ref_user  WITH PASSWORD = N'CmtRef#2026',  CHECK_POLICY = OFF;
GO

-- Schemas first; database users below set their DEFAULT_SCHEMA to point
-- at the matching one so unqualified DDL/DML lands there.
CREATE SCHEMA main_schema;
GO
CREATE SCHEMA ref_schema;
GO

-- Database users mapped to logins. DEFAULT_SCHEMA is critical: Flyway 's
-- defaultSchema setting only affects which schema unqualified DDL
-- targets; the MSSQL session 's "current schema" follows the user 's
-- DEFAULT_SCHEMA. Without it, sp_addextendedproperty (which resolves
-- by current schema) fails with "Extended properties are not permitted
-- on ref_schema.<table>, or the object does not exist."
CREATE USER main_user FOR LOGIN main_user WITH DEFAULT_SCHEMA = main_schema;
CREATE USER ref_user  FOR LOGIN ref_user  WITH DEFAULT_SCHEMA = ref_schema;
GO

-- Hand schema ownership to the matching user.
ALTER AUTHORIZATION ON SCHEMA::main_schema TO main_user;
ALTER AUTHORIZATION ON SCHEMA::ref_schema  TO ref_user;
GO

-- Add both users to db_owner. SQL Server schema owners do NOT automatically
-- inherit CREATE TABLE / CREATE VIEW / CREATE SYNONYM privileges on the
-- database — those need an explicit GRANT or membership in a role that
-- carries them. db_owner is the simplest choice for an E2E seed where
-- both users need to create their own objects (Flyway also creates a
-- flyway_schema_history table per default schema). It does not affect
-- the cross-DB compare surface because GRANT is anti-coverage on MSSQL.
ALTER ROLE db_owner ADD MEMBER main_user;
ALTER ROLE db_owner ADD MEMBER ref_user;
GO

-- Cross-schema runtime access. CMT MSSQLSchemaFetcher does not extract
-- GRANT (anti-coverage), so this only matters for runtime synonym
-- resolution within MSSQL — it does not appear in the dump.
GRANT SELECT, INSERT, UPDATE, DELETE ON SCHEMA::ref_schema TO main_user;
GO
