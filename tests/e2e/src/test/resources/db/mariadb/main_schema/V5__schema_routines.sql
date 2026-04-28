-- ============================================================
-- MAIN_SCHEMA: stored routine samples (function + procedure)
-- Per docs/seed/mariadb/SEED_SPEC.md §4.5
--
-- Both routines depend on e2e_customer (V1), so this file must run
-- after V1. Body separators are emitted via DELIMITER directives,
-- which Flyway 's MariaDB parser handles natively.
--
-- Why MariaDB has routines and MySQL 8.0 does not:
-- MariaDB still maintains the legacy mysql.proc system table that
-- MySQL 5.7 had, while MySQL 8.0 removed it in favor of the data
-- dictionary. CMT 's MariaDBSchemaFetcher.buildProcedures reads from
-- mysql.proc directly, so it works on MariaDB but breaks on MySQL 8.0.
--
-- DEFINER is intentionally omitted: the connecting user (main_user@%)
-- is recorded as the definer by default, which is what we want
-- because CMT captures the routine owner from there.
--
-- The container must run mysqld with --log-bin-trust-function-creators=ON
-- because binary logging is enabled by default; otherwise main_user
-- (without SUPER privilege) cannot CREATE FUNCTION.
-- ============================================================

DELIMITER //

CREATE FUNCTION e2e_customer_label_fn(p_customer_id INT)
RETURNS VARCHAR(200)
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_label VARCHAR(200);
    SELECT CONCAT(customer_code, ':', customer_name)
      INTO v_label
      FROM e2e_customer
     WHERE customer_id = p_customer_id;
    RETURN v_label;
END //

CREATE PROCEDURE e2e_upsert_customer_proc(
    IN p_customer_id   INT,
    IN p_customer_name VARCHAR(100)
)
MODIFIES SQL DATA
BEGIN
    UPDATE e2e_customer
       SET customer_name = p_customer_name
     WHERE customer_id = p_customer_id;
END //

DELIMITER ;
