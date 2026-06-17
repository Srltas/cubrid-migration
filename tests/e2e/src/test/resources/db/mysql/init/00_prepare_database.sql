-- ============================================================
-- MySQL: prepare main_schema database and main_user
-- Per docs/seed/mysql/SEED_SPEC.md §2 Schema Setup
--
-- Run as root via the official mysql:8.0 image entrypoint
-- (mounted at /docker-entrypoint-initdb.d/) before Flyway main_schema
-- migrations execute. After this script:
--   - main_schema database exists
--   - main_user@% has full privileges on main_schema.*
--
-- ref_schema is intentionally absent (SEED_SPEC §1 anti-coverage):
-- CMT MySQL fetcher does not implement buildGrant / buildSynonym, and
-- collapses everything into a single connection-user "schema" namespace,
-- so cross-schema scenarios cannot be verified end-to-end.
-- ============================================================

CREATE DATABASE IF NOT EXISTS main_schema
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'main_user'@'%' IDENTIFIED BY 'cmt';

GRANT ALL PRIVILEGES ON main_schema.* TO 'main_user'@'%';

FLUSH PRIVILEGES;
