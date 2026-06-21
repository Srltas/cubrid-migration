-- ============================================================
-- MAIN_SCHEMA: MySQL-only extension table
-- Per docs/seed/mysql/SEED_SPEC.md §5.5 (enum)
--
-- §5.6 e2e_mysql_set_types and §5.7 e2e_mysql_json_types are intentionally
-- absent — both are SEED_SPEC §1 anti-coverage:
--   - SET: no CUBRID scalar equivalent; CMT batch import fails with a
--     Communication error when SET values are present.
--   - JSON: CMT 's MySQLDataTypeMappingHelper has no JSON token, so JSON
--     columns silently drop on MySQL → CUBRID translation.
-- ============================================================

-- ====== §5.5 e2e_mysql_enum_types ================================
CREATE TABLE e2e_mysql_enum_types (
    id          INT NOT NULL,
    status_enum ENUM('NEW', 'HOLD', 'DONE', 'CANCEL'),
    CONSTRAINT pk_e2e_mysql_enum_types PRIMARY KEY (id)
);
