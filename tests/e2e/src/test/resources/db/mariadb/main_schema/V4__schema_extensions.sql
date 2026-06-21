-- ============================================================
-- MAIN_SCHEMA: MariaDB extension tables
-- Per docs/seed/mariadb/SEED_SPEC.md §5.5 (enum), §5.6 (set TENTATIVE),
-- §5.7 (json TENTATIVE)
--
-- Tentative items (§5.6 SET, §5.7 JSON) are included for first-run
-- verification. MySQL marked both as anti-coverage:
--   - SET: CMT batch import fails with Communication error
--   - JSON: MySQL JSON has no MariaDB-specific mapping; MariaDB JSON
--           is internally a LONGTEXT alias so it might pass through
--           silently as LONGTEXT. The first MariaDB E2E run decides
--           whether these tables stay or move to anti-coverage.
-- ============================================================

-- ====== §5.5 e2e_mariadb_enum_types ==============================
CREATE TABLE e2e_mariadb_enum_types (
    id          INT NOT NULL,
    status_enum ENUM('NEW', 'HOLD', 'DONE', 'CANCEL'),
    CONSTRAINT pk_e2e_mariadb_enum_types PRIMARY KEY (id)
);

-- ====== §5.6 e2e_mariadb_set_types =====
-- SET is anti-coverage (same batch-import "Communication error" as MySQL).
-- See SEED_SPEC §1.

-- ====== §5.7 e2e_mariadb_json_types  (TENTATIVE) =================
-- MariaDB JSON is a LONGTEXT alias (with optional JSON_VALID CHECK
-- on 10.4.3+). Even though CMT MariaDBDataTypeMappingHelper has no
-- "json" keyword, the underlying LONGTEXT typing may let the column
-- pass through silently — different from MySQL where the typed JSON
-- column is dropped. First-run testing decides.
CREATE TABLE e2e_mariadb_json_types (
    id      INT NOT NULL,
    payload JSON,
    CONSTRAINT pk_e2e_mariadb_json_types PRIMARY KEY (id)
);
