-- =============================================================
-- Oracle two-user container initialization script
-- Execution context: runs automatically as SYSDBA when gvenzl/oracle-xe starts
--                    (/container-entrypoint-initdb.d/ -> alphabetically last)
-- Purpose          : create the CMT_OWNER user and grant required system privileges
-- Note             : object-level GRANTs (for example,
--                    GRANT SELECT ON customer TO CMT_TEST) are handled in the
--                    Flyway owner/V3__grants.sql script, not here.
--                    The tables do not exist yet at this stage.
-- =============================================================

-- CMT_OWNER: owner schema for shared tables, grants access to other users
CREATE USER CMT_OWNER IDENTIFIED BY cmt;
GRANT CONNECT                     TO CMT_OWNER;
GRANT RESOURCE                    TO CMT_OWNER;
GRANT UNLIMITED TABLESPACE        TO CMT_OWNER;
GRANT CREATE TABLE                TO CMT_OWNER;
GRANT CREATE SEQUENCE             TO CMT_OWNER;
GRANT CREATE VIEW                 TO CMT_OWNER;
GRANT CREATE SYNONYM              TO CMT_OWNER;
GRANT CREATE PROCEDURE            TO CMT_OWNER;

-- Allow CMT_TEST to create private synonyms that point to CMT_OWNER objects.
-- CMT_TEST is already created through the gvenzl APP_USER mechanism.
GRANT CREATE SYNONYM              TO CMT_TEST;
