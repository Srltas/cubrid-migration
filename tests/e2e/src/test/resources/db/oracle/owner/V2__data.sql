-- ============================================================
-- Oracle full_coverage / CMT_OWNER schema: seed data (minimal)
-- ============================================================

INSERT INTO ora_cov_shared_audit (audit_id, audit_source, audit_note, created_on)
VALUES (
    ora_cov_shared_audit_seq.NEXTVAL,
    'BOOTSTRAP',
    'initial shared audit row',
    DATE '2024-01-01'
);
