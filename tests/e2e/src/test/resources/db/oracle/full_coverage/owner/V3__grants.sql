-- ============================================================
-- Oracle full_coverage / CMT_OWNER schema: grants to CMT_TEST
--
-- Execution context: CMT_OWNER (only the object owner can issue these GRANTs)
-- Purpose          : allow CMT_TEST to access shared tables owned by CMT_OWNER
--
-- When CMT migrates CMT_TEST, these GRANTs are exported as received privileges
-- and counted in the migration report grant totals.
-- (Dumped into the XE_CMT_TEST_grant.CMT_OWNER file)
-- ============================================================

-- Table privileges required to query through the synonym
GRANT SELECT                 ON ora_cov_shared_audit     TO CMT_TEST;
GRANT INSERT                 ON ora_cov_shared_audit     TO CMT_TEST;
GRANT UPDATE                 ON ora_cov_shared_audit     TO CMT_TEST;
GRANT DELETE                 ON ora_cov_shared_audit     TO CMT_TEST;
