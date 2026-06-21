-- ============================================================
-- REF_SCHEMA: cross-schema reference object(s)
-- Per docs/seed/mssql/SEED_SPEC.md §3.5
--
-- This Flyway location runs as ref_user against e2e_db. The default
-- schema for a Flyway connection is set to ref_schema, so unqualified
-- table names land in that schema.
-- ============================================================

CREATE TABLE e2e_ref_audit (
    audit_id      INT          NOT NULL,
    audit_message NVARCHAR(200),
    created_at    DATETIME2(3),
    CONSTRAINT pk_e2e_ref_audit PRIMARY KEY (audit_id)
);
GO

-- Comment via extended property (TENTATIVE — first run confirms whether
-- CMT MSSQL fetcher reads MS_Description into column.comment).
EXEC sp_addextendedproperty
    @name      = N'MS_Description',
    @value     = N'Cross-schema reference audit table',
    @level0type= N'SCHEMA', @level0name= N'ref_schema',
    @level1type= N'TABLE',  @level1name= N'e2e_ref_audit';
GO

EXEC sp_addextendedproperty
    @name      = N'MS_Description',
    @value     = N'Audit row identifier',
    @level0type= N'SCHEMA', @level0name= N'ref_schema',
    @level1type= N'TABLE',  @level1name= N'e2e_ref_audit',
    @level2type= N'COLUMN', @level2name= N'audit_id';
GO

EXEC sp_addextendedproperty
    @name      = N'MS_Description',
    @value     = N'Audit message text',
    @level0type= N'SCHEMA', @level0name= N'ref_schema',
    @level1type= N'TABLE',  @level1name= N'e2e_ref_audit',
    @level2type= N'COLUMN', @level2name= N'audit_message';
GO
