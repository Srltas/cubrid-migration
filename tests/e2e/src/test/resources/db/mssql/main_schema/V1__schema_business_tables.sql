-- ============================================================
-- MAIN_SCHEMA: business graph tables, indexes, check constraints
-- Per docs/seed/mssql/SEED_SPEC.md §3.1 ~ §3.4 and §4.1
--
-- This Flyway location runs as main_user against e2e_db. Default schema
-- is main_schema. View, type-test tables, MSSQL-extension types, and
-- synonyms live in their own migration files (V2, V3, V4, V5).
--
-- idxf_e2e_order_upper_status (functional index) is intentionally absent:
-- MSSQL requires a computed/persisted column + plain index pattern, and
-- computed columns are anti-coverage (SEED_SPEC §1).
-- ============================================================

-- ====== §3.1 e2e_customer ========================================
-- IDENTITY(5,1) makes the next-issued value 5; rows 1..4 are inserted
-- with explicit ids in V99 wrapped in SET IDENTITY_INSERT ON/OFF.
CREATE TABLE e2e_customer (
    customer_id    INT             NOT NULL IDENTITY(5, 1),
    customer_code  CHAR(4)         NOT NULL,
    customer_name  NVARCHAR(100)   NOT NULL,
    customer_alias NVARCHAR(60),
    status         CHAR(1),
    credit_limit   DECIMAL(15, 2),
    created_on     DATETIME2(3),
    updated_on     DATETIME2(3),
    CONSTRAINT pk_e2e_customer        PRIMARY KEY (customer_id),
    CONSTRAINT uk_e2e_customer_code   UNIQUE      (customer_code),
    CONSTRAINT ck_e2e_customer_status CHECK       (status IN ('A', 'I', 'D'))
);
GO

-- Extended-property comments (TENTATIVE — first run decides whether CMT
-- reads MS_Description into column.comment).
EXEC sp_addextendedproperty
    @name=N'MS_Description', @value=N'E2E master customer table',
    @level0type=N'SCHEMA', @level0name=N'main_schema',
    @level1type=N'TABLE',  @level1name=N'e2e_customer';
GO
EXEC sp_addextendedproperty
    @name=N'MS_Description', @value=N'business key',
    @level0type=N'SCHEMA', @level0name=N'main_schema',
    @level1type=N'TABLE',  @level1name=N'e2e_customer',
    @level2type=N'COLUMN', @level2name=N'customer_code';
GO
EXEC sp_addextendedproperty
    @name=N'MS_Description', @value=N'고객 별칭 (다국어 검증)',
    @level0type=N'SCHEMA', @level0name=N'main_schema',
    @level1type=N'TABLE',  @level1name=N'e2e_customer',
    @level2type=N'COLUMN', @level2name=N'customer_alias';
GO

-- ====== §3.2 e2e_order ===========================================
CREATE TABLE e2e_order (
    order_id       INT             NOT NULL IDENTITY(5, 1),
    customer_id    INT             NOT NULL,
    order_no       NVARCHAR(30),
    order_status   NVARCHAR(20),
    total_amount   DECIMAL(18, 2),
    ordered_at     DATETIME2(3),
    settled_at     DATETIME2(3),
    source_comment NVARCHAR(200),
    CONSTRAINT pk_e2e_order          PRIMARY KEY (order_id),
    CONSTRAINT fk_e2e_order_customer FOREIGN KEY (customer_id)
        REFERENCES main_schema.e2e_customer(customer_id),
    CONSTRAINT ck_e2e_order_status   CHECK (order_status IN
        (N'NEW', N'HOLD', N'DONE', N'CANCEL'))
);
GO

CREATE INDEX idx_e2e_order_customer    ON e2e_order(customer_id);
GO
CREATE INDEX idxd_e2e_order_ordered_at ON e2e_order(ordered_at DESC);
GO

EXEC sp_addextendedproperty
    @name=N'MS_Description', @value=N'Customer order transactions',
    @level0type=N'SCHEMA', @level0name=N'main_schema',
    @level1type=N'TABLE',  @level1name=N'e2e_order';
GO
EXEC sp_addextendedproperty
    @name=N'MS_Description', @value=N'주문 비고: 다국어/특수문자 검증',
    @level0type=N'SCHEMA', @level0name=N'main_schema',
    @level1type=N'TABLE',  @level1name=N'e2e_order',
    @level2type=N'COLUMN', @level2name=N'source_comment';
GO

-- ====== §3.3 e2e_order_line ======================================
CREATE TABLE e2e_order_line (
    order_id   INT           NOT NULL,
    line_no    SMALLINT      NOT NULL,
    sku        NVARCHAR(30),
    qty        INT,
    unit_price DECIMAL(15, 2),
    line_note  NVARCHAR(200),
    CONSTRAINT pk_e2e_order_line       PRIMARY KEY (order_id, line_no),
    CONSTRAINT fk_e2e_order_line_order FOREIGN KEY (order_id)
        REFERENCES main_schema.e2e_order(order_id),
    CONSTRAINT ck_e2e_order_line_qty   CHECK (qty > 0)
);
GO

EXEC sp_addextendedproperty
    @name=N'MS_Description', @value=N'Order line items (composite PK)',
    @level0type=N'SCHEMA', @level0name=N'main_schema',
    @level1type=N'TABLE',  @level1name=N'e2e_order_line';
GO

-- ====== §3.4 e2e_employee ========================================
CREATE TABLE e2e_employee (
    employee_id INT           NOT NULL,
    manager_id  INT,
    emp_name    NVARCHAR(100) NOT NULL,
    hired_on    DATE,
    CONSTRAINT pk_e2e_employee         PRIMARY KEY (employee_id),
    CONSTRAINT fk_e2e_employee_manager FOREIGN KEY (manager_id)
        REFERENCES main_schema.e2e_employee(employee_id)
);
GO

EXEC sp_addextendedproperty
    @name=N'MS_Description', @value=N'Employee hierarchy (self-FK)',
    @level0type=N'SCHEMA', @level0name=N'main_schema',
    @level1type=N'TABLE',  @level1name=N'e2e_employee';
GO
EXEC sp_addextendedproperty
    @name=N'MS_Description', @value=N'Self-FK to direct manager',
    @level0type=N'SCHEMA', @level0name=N'main_schema',
    @level1type=N'TABLE',  @level1name=N'e2e_employee',
    @level2type=N'COLUMN', @level2name=N'manager_id';
GO
