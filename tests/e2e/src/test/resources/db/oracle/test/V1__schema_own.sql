-- ============================================================
-- Oracle full_coverage / CMT_TEST schema: own tables (full spec)
--
-- CMT_TEST is the primary migration target, so the main coverage objects
-- such as foreign keys, indexes, and views live here. CMT_OWNER keeps only
-- one shared_audit table, referenced through a CMT_TEST synonym
-- (test/V4__synonyms.sql).
--
-- Index coverage:
--   - UNIQUE index         (uk_cov_customer_code)
--   - FK supporting index  (idx_cov_orders_customer)
--   - COMPOSITE index      (idx_cov_line_order)
--   - DESCENDING index     (idx_cov_orders_ordered_desc)
--   - FUNCTION-BASED index (idx_cov_orders_upper_status)
--   - REVERSE index        (idx_cov_orders_no_rev) - Oracle-specific
-- ============================================================

-- -------------------------------------------------------
-- Sequences
-- -------------------------------------------------------
CREATE SEQUENCE ora_cov_customer_seq
    START WITH 1 INCREMENT BY 1 MINVALUE 1 NOMAXVALUE NOCACHE NOCYCLE;

CREATE SEQUENCE ora_cov_order_seq
    START WITH 1 INCREMENT BY 1 MINVALUE 1 NOMAXVALUE NOCACHE NOCYCLE;

CREATE SEQUENCE ora_cov_log_seq
    START WITH 1000 INCREMENT BY 1 MINVALUE 1 NOMAXVALUE NOCACHE NOCYCLE;

-- -------------------------------------------------------
-- Business tables (FK chain: customer → orders → order_line)
-- -------------------------------------------------------
CREATE TABLE ora_cov_customer (
    customer_id    NUMBER(10)       NOT NULL,
    customer_code  CHAR(4)          NOT NULL,
    customer_name  VARCHAR2(100)    NOT NULL,
    customer_alias NVARCHAR2(60),
    status         CHAR(1)          DEFAULT 'A' NOT NULL,
    credit_limit   NUMBER(15, 2),
    created_on     DATE             DEFAULT SYSDATE NOT NULL,
    updated_on     DATE             DEFAULT CURRENT_DATE,
    CONSTRAINT pk_cov_customer PRIMARY KEY (customer_id)
);

CREATE UNIQUE INDEX uk_cov_customer_code
    ON ora_cov_customer(customer_code);

CREATE TABLE ora_cov_orders (
    order_id        NUMBER(10)      NOT NULL,
    customer_id     NUMBER(10)      NOT NULL,
    order_no        VARCHAR2(30)    NOT NULL,
    order_status    VARCHAR2(20)    DEFAULT 'NEW' NOT NULL,
    total_amount    NUMBER(18, 2),
    ordered_at      DATE            DEFAULT SYSDATE NOT NULL,
    settled_at      DATE            DEFAULT CURRENT_DATE,
    source_comment  VARCHAR2(200),
    CONSTRAINT pk_cov_orders PRIMARY KEY (order_id),
    CONSTRAINT fk_cov_orders_customer FOREIGN KEY (customer_id)
        REFERENCES ora_cov_customer(customer_id)
);

CREATE INDEX idx_cov_orders_customer
    ON ora_cov_orders(customer_id);

CREATE INDEX idx_cov_orders_ordered_desc
    ON ora_cov_orders(ordered_at DESC);

CREATE INDEX idx_cov_orders_upper_status
    ON ora_cov_orders(UPPER(order_status));

CREATE INDEX idx_cov_orders_no_rev
    ON ora_cov_orders(order_no) REVERSE;

CREATE TABLE ora_cov_order_line (
    line_id      NUMBER(10)      NOT NULL,
    order_id     NUMBER(10)      NOT NULL,
    line_no      NUMBER(5)       NOT NULL,
    sku          VARCHAR2(30)    NOT NULL,
    qty          NUMBER(10, 0)   NOT NULL,
    unit_price   NUMBER(15, 2)   NOT NULL,
    line_note    VARCHAR2(200),
    CONSTRAINT pk_cov_order_line PRIMARY KEY (line_id),
    CONSTRAINT fk_cov_line_order FOREIGN KEY (order_id)
        REFERENCES ora_cov_orders(order_id)
);

CREATE INDEX idx_cov_line_order
    ON ora_cov_order_line(order_id, line_no);

-- -------------------------------------------------------
-- Auxiliary tables (no FK, simple)
-- -------------------------------------------------------
-- Audit log table written by the PL/SQL procedure/function
CREATE TABLE ora_cov_program_log (
    log_id       NUMBER(10)      NOT NULL,
    proc_name    VARCHAR2(40)    NOT NULL,
    payload      VARCHAR2(200),
    created_on   DATE            DEFAULT SYSDATE NOT NULL,
    CONSTRAINT pk_cov_program_log PRIMARY KEY (log_id)
);

-- Auxiliary table for ROWID / UROWID type coverage
CREATE TABLE ora_cov_rowid_source (
    source_id    NUMBER(10)      NOT NULL,
    source_name  VARCHAR2(40)    NOT NULL,
    CONSTRAINT pk_cov_rowid_source PRIMARY KEY (source_id)
);
