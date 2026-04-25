-- ============================================================
-- Oracle full_coverage / CMT_TEST schema: PL/SQL procedure/function
--
-- Use only types that the CMT Oracle PL/SQL parser explicitly supports:
--   VARCHAR2, NCHAR, NVARCHAR2, NUMBER, FLOAT,
--   BINARY_FLOAT, BINARY_DOUBLE, DATE
--
-- CMT_TEST owns every referenced object (ora_cov_customer,
-- ora_cov_program_log, and others), so no schema qualifier is needed.
-- Keep the objects simple and self-contained.
-- ============================================================

CREATE OR REPLACE PROCEDURE ora_cov_upsert_customer_p (
    p_customer_id    NUMBER,
    p_customer_name  VARCHAR2,
    p_display_name   NVARCHAR2,
    p_weight         BINARY_FLOAT,
    p_credit_limit   BINARY_DOUBLE,
    p_updated_on     DATE
)
AS
    v_payload VARCHAR2(200);
BEGIN
    UPDATE ora_cov_customer
       SET customer_name  = p_customer_name,
           customer_alias = p_display_name,
           credit_limit   = p_credit_limit,
           updated_on     = p_updated_on
     WHERE customer_id = p_customer_id;

    IF SQL%ROWCOUNT = 0 THEN
        INSERT INTO ora_cov_customer (
            customer_id,
            customer_code,
            customer_name,
            customer_alias,
            status,
            credit_limit,
            created_on,
            updated_on
        ) VALUES (
            p_customer_id,
            LPAD(TO_CHAR(p_customer_id), 4, '0'),
            p_customer_name,
            p_display_name,
            'A',
            p_credit_limit,
            p_updated_on,
            p_updated_on
        );
    END IF;

    v_payload := p_customer_name || ':' || TO_CHAR(p_weight);

    INSERT INTO ora_cov_program_log (
        log_id,
        proc_name,
        payload,
        created_on
    ) VALUES (
        ora_cov_log_seq.NEXTVAL,
        'UPSERT_CUSTOMER',
        v_payload,
        p_updated_on
    );
END;
/

CREATE OR REPLACE FUNCTION ora_cov_customer_label_f (
    p_customer_id    NUMBER,
    p_prefix         NCHAR,
    p_factor         FLOAT,
    p_created_on     DATE
) RETURN VARCHAR2
AS
    v_customer_name VARCHAR2(120);
BEGIN
    SELECT customer_name
      INTO v_customer_name
      FROM ora_cov_customer
     WHERE customer_id = p_customer_id;

    INSERT INTO ora_cov_program_log (
        log_id,
        proc_name,
        payload,
        created_on
    ) VALUES (
        ora_cov_log_seq.NEXTVAL,
        'CUSTOMER_LABEL_F',
        v_customer_name || ':' || TO_CHAR(p_factor),
        p_created_on
    );

    RETURN p_prefix || '-' || v_customer_name;
END;
/
