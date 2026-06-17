-- ============================================================
-- REF_SCHEMA: reference data
-- Per docs/seed/mssql/SEED_SPEC.md §3.5 required row
-- ============================================================

INSERT INTO e2e_ref_audit (audit_id, audit_message, created_at) VALUES
    (1, N'reference audit row', '2024-04-20 00:00:00');
GO
