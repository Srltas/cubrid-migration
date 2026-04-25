-- ============================================================
-- Oracle full_coverage / CMT_TEST schema: private synonyms (minimal)
--
-- In this scenario, the synonym exists only to validate the
-- cross-schema export path.
-- When CMT exports CMT_TEST, it is dumped into XE_CMT_TEST_synonym.
--
-- The required GRANTs are already applied in owner/V3__grants.sql.
-- CUBRID supports only private synonyms, so public synonyms are not used.
-- ============================================================

CREATE SYNONYM ora_cov_shared_audit_syn
    FOR CMT_OWNER.ora_cov_shared_audit;
