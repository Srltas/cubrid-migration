-- ============================================================
-- MAIN_SCHEMA: business graph tables
-- Per docs/seed/informix/SEED_SPEC.md §3.1 - §3.4
--
-- Runs as main_user @ e2e_db.
--
-- Notes:
--   - Informix 's owner = current user. All objects below are
--     owned by main_user.
--   - No COMMENT statements (anti-coverage; Informix engine
--     does not natively support comments).
--   - BIGSERIAL on e2e_order.order_id is Informix 's native
--     auto-increment for 64-bit integers. Sequence object for
--     e2e_customer is created in V4 alongside extensions.
-- ============================================================

-- Informix constraint syntax note: the CONSTRAINT <name> keyword goes
-- AFTER the constraint definition (PRIMARY KEY / FOREIGN KEY / UNIQUE /
-- CHECK), not before it. Source: IBM Informix Guide to SQL Syntax,
-- "CREATE TABLE": "constraint-name immediately follows the constraint
-- type clause and is preceded by the CONSTRAINT keyword".

CREATE TABLE e2e_customer (
    customer_id     INTEGER       NOT NULL,
    customer_code   CHAR(4)       NOT NULL,
    customer_name   VARCHAR(100)  NOT NULL,
    customer_alias  NVARCHAR(60),
    status          CHAR(1)       NOT NULL,
    credit_limit    DECIMAL(15,2),
    created_on      DATE          NOT NULL,
    updated_on      DATE,
    PRIMARY KEY (customer_id)              CONSTRAINT pk_e2e_customer,
    UNIQUE      (customer_code)            CONSTRAINT uk_e2e_customer_code,
    CHECK       (status IN ('A','I','D'))  CONSTRAINT ck_e2e_customer_stat
);

CREATE TABLE e2e_order (
    order_id        BIGSERIAL,
    customer_id     INTEGER        NOT NULL,
    order_no        VARCHAR(30)    NOT NULL,
    order_status    VARCHAR(20)    NOT NULL,
    total_amount    MONEY(18,2),
    ordered_at      DATETIME YEAR TO SECOND  NOT NULL,
    settled_at      DATETIME YEAR TO SECOND,
    source_comment  VARCHAR(200),
    PRIMARY KEY (order_id)                                      CONSTRAINT pk_e2e_order,
    FOREIGN KEY (customer_id) REFERENCES e2e_customer(customer_id)
                                                                CONSTRAINT fk_e2e_order_cust,
    CHECK       (order_status IN ('NEW','HOLD','DONE','CANCEL')) CONSTRAINT ck_e2e_order_stat
);

-- Informix auto-creates a non-unique index on every FOREIGN KEY column set
-- as part of the constraint. Adding an explicit
--     CREATE INDEX idx_e2e_order_customer ON e2e_order(customer_id);
-- on top of the e2e_order_cust FK fails with
--     -350: Index already exists on the column (or on the set of columns)
-- The FK 's auto-index already provides equivalent lookup performance for
-- customer_id, so the explicit secondary index is omitted in the Informix
-- seed (cf. Oracle / MSSQL where FK does NOT auto-index, so we keep the
-- explicit index there).
CREATE INDEX idxd_e2e_order_ordered_at
    ON e2e_order(ordered_at DESC);

CREATE TABLE e2e_order_line (
    order_id    BIGINT       NOT NULL,
    line_no     SMALLINT     NOT NULL,
    sku         VARCHAR(30)  NOT NULL,
    qty         INTEGER      NOT NULL,
    unit_price  DECIMAL(15,2) NOT NULL,
    line_note   VARCHAR(200),
    PRIMARY KEY (order_id, line_no)                          CONSTRAINT pk_e2e_order_line,
    FOREIGN KEY (order_id) REFERENCES e2e_order(order_id)    CONSTRAINT fk_e2e_order_line_ord,
    CHECK       (qty > 0)                                    CONSTRAINT ck_e2e_order_line_qty
);

CREATE TABLE e2e_employee (
    employee_id  INTEGER       NOT NULL,
    manager_id   INTEGER,
    emp_name     VARCHAR(100)  NOT NULL,
    hired_on     DATE          NOT NULL,
    PRIMARY KEY (employee_id)                                       CONSTRAINT pk_e2e_employee,
    FOREIGN KEY (manager_id) REFERENCES e2e_employee(employee_id)   CONSTRAINT fk_e2e_employee_mgr
);
