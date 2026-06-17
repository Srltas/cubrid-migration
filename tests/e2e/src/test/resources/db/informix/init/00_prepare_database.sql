-- ============================================================
-- Informix: prepare e2e_db database and grants for main_user / ref_user
-- Per docs/seed/informix/SEED_SPEC.md §2 Schema Setup
--
-- Executed via dbaccess inside the Informix container (called by
-- InformixContainer.start() after the server is ready).
--
-- Note: OS-level user creation (useradd main_user / ref_user) is done
-- in InformixContainer.start() via execInContainer because Informix
-- 's CONNECT/RESOURCE grants reference OS accounts. SQL below assumes
-- those OS accounts already exist.
--
-- Multi-schema (main_user owner + ref_user owner in one e2e_db) is
-- TENTATIVE per SEED_SPEC §1: first run confirms whether
-- InformixSchemaFetcher.getSchemaNames pulls both owners. If it
-- doesn 't, ref_schema collapses to anti-coverage.
--
-- Cross-schema GRANT statements at runtime (so view/synonym resolve)
-- but they do NOT appear in the dump because InformixSchemaFetcher
-- does not override buildGrant (anti-coverage).
-- ============================================================

CREATE DATABASE e2e_db WITH LOG;

-- Database-level grants. Informix gates DB access on CONNECT, and
-- DDL (CREATE TABLE / VIEW / SEQUENCE) on RESOURCE.
GRANT CONNECT  TO main_user;
GRANT CONNECT  TO ref_user;
GRANT RESOURCE TO main_user;
GRANT RESOURCE TO ref_user;
