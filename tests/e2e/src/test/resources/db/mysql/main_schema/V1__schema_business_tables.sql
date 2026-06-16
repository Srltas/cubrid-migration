-- ============================================================
-- MAIN_SCHEMA: business graph tables, indexes, check constraints
-- Per docs/seed/mysql/SEED_SPEC.md §3.1 ~ §3.4 and §4.1
--
-- This Flyway location runs as main_user@% against the main_schema database,
-- so all object creation is unqualified. View, MySQL extension types, and
-- routines live in their own migration files (V2, V4, V5).
-- ============================================================

-- ====== §3.1 e2e_customer ========================================
-- AUTO_INCREMENT=5: §3.1 / §3.2 의 required row id 가 1..4 이므로 다음 발급 값.
-- (MySQL 의 AUTO_INCREMENT counter 는 explicit insert 가 현재 값보다 작거나
--  같으면 그대로 유지되므로, 1..4 explicit insert 후에도 5 가 그대로 보존된다.)
CREATE TABLE e2e_customer (
    customer_id    INT          NOT NULL AUTO_INCREMENT,
    customer_code  CHAR(4)      NOT NULL                COMMENT 'business key',
    customer_name  VARCHAR(100) NOT NULL,
    customer_alias VARCHAR(60)                          COMMENT '고객 별칭 (다국어 검증)',
    status         CHAR(1),
    credit_limit   DECIMAL(15, 2),
    created_on     DATETIME,
    updated_on     DATETIME,
    CONSTRAINT pk_e2e_customer        PRIMARY KEY (customer_id),
    CONSTRAINT uk_e2e_customer_code   UNIQUE      (customer_code),
    CONSTRAINT ck_e2e_customer_status CHECK       (status IN ('A', 'I', 'D'))
) AUTO_INCREMENT=5,
  COMMENT='E2E master customer table';

-- ====== §3.2 e2e_order ===========================================
CREATE TABLE e2e_order (
    order_id       INT          NOT NULL AUTO_INCREMENT,
    customer_id    INT          NOT NULL,
    order_no       VARCHAR(30),
    order_status   VARCHAR(20),
    total_amount   DECIMAL(18, 2),
    ordered_at     DATETIME,
    settled_at     DATETIME,
    source_comment VARCHAR(200)                         COMMENT '주문 비고: 다국어/특수문자 검증',
    CONSTRAINT pk_e2e_order          PRIMARY KEY (order_id),
    CONSTRAINT fk_e2e_order_customer FOREIGN KEY (customer_id) REFERENCES e2e_customer(customer_id),
    CONSTRAINT ck_e2e_order_status   CHECK       (order_status IN ('NEW', 'HOLD', 'DONE', 'CANCEL'))
) AUTO_INCREMENT=5,
  COMMENT='Customer order transactions';

CREATE INDEX idx_e2e_order_customer      ON e2e_order(customer_id);
CREATE INDEX idxd_e2e_order_ordered_at   ON e2e_order(ordered_at DESC);
-- Functional index requires MySQL 8.0.13+; expression must be wrapped in
-- two layers of parentheses (`((expr))`).
CREATE INDEX idxf_e2e_order_upper_status ON e2e_order((UPPER(order_status)));

-- ====== §3.3 e2e_order_line ======================================
CREATE TABLE e2e_order_line (
    order_id   INT          NOT NULL,
    line_no    SMALLINT     NOT NULL,
    sku        VARCHAR(30),
    qty        INT,
    unit_price DECIMAL(15, 2),
    line_note  VARCHAR(200),
    CONSTRAINT pk_e2e_order_line       PRIMARY KEY (order_id, line_no),
    CONSTRAINT fk_e2e_order_line_order FOREIGN KEY (order_id) REFERENCES e2e_order(order_id),
    CONSTRAINT ck_e2e_order_line_qty   CHECK       (qty > 0)
) COMMENT='Order line items (composite PK)';

-- ====== §3.4 e2e_employee ========================================
CREATE TABLE e2e_employee (
    employee_id INT          NOT NULL,
    manager_id  INT                                    COMMENT 'Self-FK to direct manager',
    emp_name    VARCHAR(100) NOT NULL,
    hired_on    DATE,
    CONSTRAINT pk_e2e_employee         PRIMARY KEY (employee_id),
    CONSTRAINT fk_e2e_employee_manager FOREIGN KEY (manager_id) REFERENCES e2e_employee(employee_id)
) COMMENT='Employee hierarchy (self-FK)';
