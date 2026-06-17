-- ============================================================
-- MAIN_SCHEMA: MSSQL-only extension table
-- Per docs/seed/mssql/SEED_SPEC.md §5.5 (uniqueidentifier)
--
-- §5.6 e2e_mssql_xml_types is anti-coverage from the start (no CUBRID
-- equivalent for typed XML). MONEY / SMALLMONEY are folded into §5.2.
-- ============================================================

-- ====== §5.5 e2e_mssql_uniqueidentifier_types ====================
-- UNIQUEIDENTIFIER (GUID) is MSSQL-only. CMT type-map falls back to
-- CHAR(36) (string canonical form). Values cover NULL, all-zero,
-- random NEWID(), and a fixed canonical UUID.
CREATE TABLE e2e_mssql_uniqueidentifier_types (
    id      INT NOT NULL,
    uid_col UNIQUEIDENTIFIER,
    CONSTRAINT pk_e2e_mssql_uniqueidentifier_types PRIMARY KEY (id)
);
GO
