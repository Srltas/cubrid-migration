# Oracle Seed Specification

이 문서는 Oracle source DB 용 시드 명세다. 공통 규칙은
`../COMMON_SEED_CONTRACT.md` 를 따른다.

## 1. Scope

Oracle seed 는 다음 기능을 검증한다.

| Area | Oracle implementation |
|---|---|
| Schema roles | `MAIN_SCHEMA`, `REF_SCHEMA` users |
| Cross-schema | grant + synonym |
| Key generation | Oracle sequence |
| Routine | PL/SQL procedure/function |
| Type extension | `NUMBER`, `TIMESTAMP WITH TIME ZONE`, `INTERVAL`, `ROWID/UROWID`, `CLOB/BLOB` |

Oracle Materialized View, Package, Package Body, Custom Type은 이 spec 에 포함하지
않는다. CMT fetcher/importer 지원 범위가 명확해진 뒤 별도 extension 으로 추가한다.

## 2. Schema Setup

| Schema | Role |
|---|---|
| `MAIN_SCHEMA` | main migration schema |
| `REF_SCHEMA` | cross-schema object and grant source |

`init` 단계는 user/schema 준비만 담당한다. `MAIN_SCHEMA` 에 대한 object grant 는
`REF_SCHEMA.e2e_ref_audit` 생성 이후 `ref_schema/V3__grants_to_main.sql` 에 둔다.

```sql
GRANT SELECT, INSERT, UPDATE, DELETE
ON REF_SCHEMA.e2e_ref_audit
TO MAIN_SCHEMA;
```

## 3. Business Graph

### 3.1 `MAIN_SCHEMA.e2e_customer`

| Column | Oracle type | Required data |
|---|---|---|
| `customer_id` | `NUMBER(10)` | PK |
| `customer_code` | `CHAR(4)` | unique, values `C001`.. |
| `customer_name` | `VARCHAR2(100)` | non-null |
| `customer_alias` | `NVARCHAR2(60)` | nullable unicode |
| `status` | `CHAR(1)` | `A`, `I`, `D` |
| `credit_limit` | `NUMBER(15,2)` | NULL, 0, normal, high |
| `created_on` | `DATE` | fixed date |
| `updated_on` | `DATE` | nullable |

Required constraints:

```sql
PRIMARY KEY (customer_id)
UNIQUE (customer_code)
CHECK (status IN ('A', 'I', 'D'))
```

Required rows:

| customer_id | customer_code | customer_name | customer_alias | status | credit_limit | created_on |
|---:|---|---|---|---|---:|---|
| 1 | `C001` | `ALPHA CUSTOMER` | `Alpha Alias` | `A` | 12500.75 | `2024-01-10` |
| 2 | `C002` | `BETA CUSTOMER` | NULL | `I` | NULL | `2024-02-29` |
| 3 | `C003` | `한국 고객` | `별칭-한글` | `D` | 0 | `2024-03-15` |
| 4 | `C004` | `HIGH LIMIT CUSTOMER` | `High` | `A` | 9999999999999.99 | `2024-04-20` |

### 3.2 `MAIN_SCHEMA.e2e_order`

| Column | Oracle type | Required data |
|---|---|---|
| `order_id` | `NUMBER(10)` | PK |
| `customer_id` | `NUMBER(10)` | FK to customer |
| `order_no` | `VARCHAR2(30)` | business key |
| `order_status` | `VARCHAR2(20)` | `NEW`, `HOLD`, `DONE`, `CANCEL` |
| `total_amount` | `NUMBER(18,2)` | nullable amount |
| `ordered_at` | `DATE` | fixed date |
| `settled_at` | `DATE` | nullable |
| `source_comment` | `VARCHAR2(200)` | blank/unicode/punctuation |

Required constraints/indexes:

```sql
PRIMARY KEY (order_id)
FOREIGN KEY (customer_id) REFERENCES MAIN_SCHEMA.e2e_customer(customer_id)
CHECK (order_status IN ('NEW', 'HOLD', 'DONE', 'CANCEL'))
CREATE INDEX idx_e2e_order_customer ON MAIN_SCHEMA.e2e_order(customer_id);
CREATE INDEX idxd_e2e_order_ordered_at ON MAIN_SCHEMA.e2e_order(ordered_at DESC);
CREATE INDEX idxf_e2e_order_upper_status ON MAIN_SCHEMA.e2e_order(UPPER(order_status));
```

Required rows:

| order_id | customer_id | order_no | order_status | total_amount | source_comment |
|---:|---:|---|---|---:|---|
| 1 | 1 | `ORD-2024-001` | `NEW` | 50.00 | `first order` |
| 2 | 1 | `ORD-2024-002` | `DONE` | 125.50 | `done-order` |
| 3 | 2 | `ORD-2024-003` | `HOLD` | NULL | `  padded comment  ` |
| 4 | 3 | `ORD-2024-004` | `CANCEL` | 0 | `취소 주문 !@#` |

### 3.3 `MAIN_SCHEMA.e2e_order_line`

Use composite PK.

| Column | Oracle type |
|---|---|
| `order_id` | `NUMBER(10)` |
| `line_no` | `NUMBER(5)` |
| `sku` | `VARCHAR2(30)` |
| `qty` | `NUMBER(10,0)` |
| `unit_price` | `NUMBER(15,2)` |
| `line_note` | `VARCHAR2(200)` |

Required constraints:

```sql
PRIMARY KEY (order_id, line_no)
FOREIGN KEY (order_id) REFERENCES MAIN_SCHEMA.e2e_order(order_id)
CHECK (qty > 0)
```

Required rows:

| order_id | line_no | sku | qty | unit_price | line_note |
|---:|---:|---|---:|---:|---|
| 1 | 1 | `SKU-ALPHA` | 2 | 25.00 | `normal` |
| 2 | 1 | `SKU-BETA` | 1 | 125.50 | NULL |
| 3 | 1 | `SKU-HOLD` | 10 | 9.99 | `hold line` |
| 4 | 1 | `SKU-CANCEL` | 1 | 0 | `cancelled` |

### 3.4 `MAIN_SCHEMA.e2e_employee`

Self-reference FK is required unless the Oracle fetcher path is intentionally disabled.

| Column | Oracle type | Required data |
|---|---|---|
| `employee_id` | `NUMBER(10)` | PK |
| `manager_id` | `NUMBER(10)` | nullable self-reference FK |
| `emp_name` | `VARCHAR2(100)` | non-null |
| `hired_on` | `DATE` | fixed date |

Required constraints:

```sql
PRIMARY KEY (employee_id)
FOREIGN KEY (manager_id) REFERENCES MAIN_SCHEMA.e2e_employee(employee_id)
```

Required rows:

| employee_id | manager_id | emp_name | hired_on |
|---:|---:|---|---|
| 1 | NULL | `CEO` | `2020-01-01` |
| 2 | 1 | `Manager A` | `2021-01-01` |
| 3 | 2 | `Staff A1` | `2022-01-01` |

### 3.5 `REF_SCHEMA.e2e_ref_audit`

| Column | Oracle type |
|---|---|
| `audit_id` | `NUMBER(10)` |
| `audit_message` | `VARCHAR2(200)` |
| `created_at` | `DATE` |

Required constraints:

```sql
PRIMARY KEY (audit_id)
```

Required row:

| audit_id | audit_message | created_at |
|---:|---|---|
| 1 | `reference audit row` | `2024-04-20` |

## 4. Object Samples

### 4.1 Sequence

Create at least:

```sql
CREATE SEQUENCE MAIN_SCHEMA.e2e_customer_seq START WITH 5 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE MAIN_SCHEMA.e2e_order_seq START WITH 5 INCREMENT BY 1 NOCACHE NOCYCLE;
```

The start value follows the required rows above. If inserted rows change, update sequence start
values accordingly.

### 4.2 View

```sql
CREATE OR REPLACE VIEW MAIN_SCHEMA.e2e_order_summary_v AS
SELECT o.order_id,
       o.order_no,
       c.customer_code,
       c.customer_name,
       o.order_status,
       o.total_amount,
       o.ordered_at
FROM MAIN_SCHEMA.e2e_order o
JOIN MAIN_SCHEMA.e2e_customer c
  ON c.customer_id = o.customer_id;
```

Use schema-qualified names. This prevents view migration from depending on the target connection
user.

### 4.3 Synonym

```sql
CREATE OR REPLACE SYNONYM MAIN_SCHEMA.e2e_ref_audit_syn
FOR REF_SCHEMA.e2e_ref_audit;
```

### 4.4 PL/SQL Routine

Create one function and one procedure only.

Function:

```sql
CREATE OR REPLACE FUNCTION MAIN_SCHEMA.e2e_customer_label_fn(p_customer_id IN NUMBER)
RETURN VARCHAR2
AS
  v_label VARCHAR2(200);
BEGIN
  SELECT customer_code || ':' || customer_name
    INTO v_label
    FROM MAIN_SCHEMA.e2e_customer
   WHERE customer_id = p_customer_id;
  RETURN v_label;
END;
/
```

Procedure:

```sql
CREATE OR REPLACE PROCEDURE MAIN_SCHEMA.e2e_upsert_customer_proc(
  p_customer_id IN NUMBER,
  p_customer_name IN VARCHAR2
)
AS
BEGIN
  UPDATE MAIN_SCHEMA.e2e_customer
     SET customer_name = p_customer_name
   WHERE customer_id = p_customer_id;
END;
/
```

Unsupported PL/SQL parameter behavior is not expanded per type. If needed, create one integrated
sample only and document why it is needed.

### 4.5 Comment

Oracle 의 standalone `COMMENT ON ...` 문을 사용해 table / view / column 메타데이터에
comment 를 부여한다. 적어도 한 column 에는 다국어 unicode comment 를 두어
charset 보존을 검증한다.

Required comments:

| Object | Comment |
|---|---|
| `REF_SCHEMA.e2e_ref_audit` (table) | `Cross-schema reference audit table` |
| `REF_SCHEMA.e2e_ref_audit.audit_id` | `Audit row identifier` |
| `REF_SCHEMA.e2e_ref_audit.audit_message` | `Audit message text` |
| `MAIN_SCHEMA.e2e_customer` (table) | `E2E master customer table` |
| `MAIN_SCHEMA.e2e_customer.customer_code` | `business key` |
| `MAIN_SCHEMA.e2e_customer.customer_alias` | `고객 별칭 (다국어 검증)` |
| `MAIN_SCHEMA.e2e_order` (table) | `Customer order transactions` |
| `MAIN_SCHEMA.e2e_order.source_comment` | `주문 비고: 다국어/특수문자 검증` |
| `MAIN_SCHEMA.e2e_order_line` (table) | `Order line items (composite PK)` |
| `MAIN_SCHEMA.e2e_employee` (table) | `Employee hierarchy (self-FK)` |
| `MAIN_SCHEMA.e2e_employee.manager_id` | `Self-FK to direct manager` |
| `MAIN_SCHEMA.e2e_order_summary_v` (view) | `Customer-order join summary view` |
| `MAIN_SCHEMA.e2e_order_summary_v.customer_code` | `Customer business key (from e2e_customer)` |

```sql
COMMENT ON TABLE  MAIN_SCHEMA.e2e_customer               IS 'E2E master customer table';
COMMENT ON COLUMN MAIN_SCHEMA.e2e_customer.customer_code IS 'business key';
```

Type Test Tables (`e2e_*_types`) 는 내부 검증용 scaffolding 이므로 별도 comment 를
부여하지 않는다.

## 5. Type Test Tables

### 5.1 `MAIN_SCHEMA.e2e_text_types`

Columns:

| Column | Oracle type |
|---|---|
| `id` | `NUMBER(10)` PK |
| `char_byte_col` | `CHAR(3 BYTE)` |
| `char_char_col` | `CHAR(3 CHAR)` |
| `varchar_byte_col` | `VARCHAR2(10 BYTE)` |
| `varchar_char_col` | `VARCHAR2(10 CHAR)` |
| `nchar_col` | `NCHAR(10)` |
| `nvarchar_col` | `NVARCHAR2(40)` |
| `clob_col` | `CLOB` |
| `nclob_col` | `NCLOB` |

SQL-ready values:

| id | row | char_byte_col | char_char_col | varchar_byte_col | varchar_char_col | nchar_col | nvarchar_col | clob_col | nclob_col |
|---:|---|---|---|---|---|---|---|---|---|
| 1 | `R_NULL` | NULL | NULL | NULL | NULL | NULL | NULL | NULL | NULL |
| 2 | `R_EMPTY` | `' '` | `' '` | `''` | `''` | `' '` | `''` | `EMPTY_CLOB()` | `EMPTY_CLOB()` |
| 3 | `R_MIN` | `'A'` | `'A'` | `'A'` | `'A'` | `N'A'` | `N'A'` | `'A'` | `N'A'` |
| 4 | `R_MAX` | `'XYZ'` | `'XYZ'` | `'ABCDEFGHIJ'` | `'ABCDEFGHIJ'` | `N'1234567890'` | `N'한국어 漢字 cafe'` | `TO_CLOB(RPAD('CLOB', 1000, 'X'))` | `TO_NCLOB(RPAD(N'NCLOB', 1000, N'가'))` |
| 5 | `R_UNICODE` | NULL | NULL | NULL | NULL | `N'한中Ω'` | `N'한국 漢字 cafe'` | `TO_CLOB('한국어 漢字 cafe punctuation !@#')` | `TO_NCLOB(N'한국어 漢字 cafe punctuation !@#')` |
| 6 | `R_BOUNDARY` | `'   '` | `'   '` | `' A '` | `'  A  '` | `N'   '` | `N' A '` | `TO_CLOB('line1'||CHR(10)||'line2')` | `TO_NCLOB(N'line1'||CHR(10)||N'line2')` |

Oracle stores empty string as NULL. Keep `R_EMPTY` row because this behavior itself is important.

`char_char_col`, `varchar_char_col`, and `nchar_col` use ASCII at `R_MAX` (and
NULL at `R_UNICODE` for `char_char_col` / `varchar_char_col`) because CMT maps
Oracle `CHAR(n CHAR)`, `VARCHAR2(n CHAR)`, and `NCHAR(n)` to byte-counted CUBRID
`CHAR(n)` / `VARCHAR(n)`; n multi-byte chars overflow when n equals the byte
budget. Multi-byte unicode coverage is preserved in the wider `nvarchar_col`
(NVARCHAR2(40) → varchar(40)), `clob_col`, and `nclob_col` columns.

### 5.2 `MAIN_SCHEMA.e2e_numeric_types`

Columns:

| Column | Oracle type |
|---|---|
| `id` | `NUMBER(10)` PK |
| `integer_col` | `INTEGER` |
| `decimal_col` | `DECIMAL(20,4)` |
| `number_p0_col` | `NUMBER(38,0)` |
| `number_ps_col` | `NUMBER(20,6)` |
| `number_round_col` | `NUMBER(8,-2)` |
| `number_any_col` | `NUMBER` |
| `float_col` | `FLOAT(30)` |
| `real_col` | `REAL` |
| `binary_float_col` | `BINARY_FLOAT` |
| `binary_double_col` | `BINARY_DOUBLE` |

SQL-ready values:

| id | row | integer_col | decimal_col | number_p0_col | number_ps_col | number_round_col | number_any_col | float_col | real_col | binary_float_col | binary_double_col |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---|---|
| 1 | `R_NULL` | NULL | NULL | NULL | NULL | NULL | NULL | NULL | NULL | NULL | NULL |
| 2 | `R_EMPTY` | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3 | `R_MIN` | -2147483647 | -9999999999999999.9999 | -99999999999999999999999999999999999999 | -99999999999999.999999 | -99999900 | -1E20 | -1E20 | -1E20 | -3.4E38F | -1.7E308D |
| 4 | `R_MAX` | 2147483647 | 9999999999999999.9999 | 99999999999999999999999999999999999999 | 99999999999999.999999 | 99999900 | 1E20 | 1E20 | 1E20 | 3.4E38F | 1.7E308D |
| 5 | `R_BOUNDARY` | 42 | 9876543210.1234 | 12345678901234567890123456789012345678 | 1234567890.123456 | 1234.56 | 0.000001 | 3.14159265 | 2.7182818 | `BINARY_FLOAT_INFINITY` | `BINARY_DOUBLE_INFINITY` |
| 6 | `R_REPRESENTATIVE` | 7 | 100.2500 | 10000000000000000000000000000000000001 | 42.424242 | -1234.56 | -0.000001 | 6.283185 | 1.41421 | `BINARY_FLOAT_NAN` | `BINARY_DOUBLE_NAN` |

### 5.3 `MAIN_SCHEMA.e2e_temporal_types`

Columns:

| Column | Oracle type |
|---|---|
| `id` | `NUMBER(10)` PK |
| `date_col` | `DATE` |
| `ts6_col` | `TIMESTAMP(6)` |
| `ts9_col` | `TIMESTAMP(9)` |
| `tsltz6_col` | `TIMESTAMP(6) WITH LOCAL TIME ZONE` |
| `tstz9_col` | `TIMESTAMP(9) WITH TIME ZONE` |
| `interval_ds_col` | `INTERVAL DAY(2) TO SECOND(6)` |
| `interval_ym_col` | `INTERVAL YEAR(4) TO MONTH` |

SQL-ready values:

| id | row | date_col | ts6_col | ts9_col | tsltz6_col | tstz9_col | interval_ds_col | interval_ym_col |
|---:|---|---|---|---|---|---|---|---|
| 1 | `R_NULL` | NULL | NULL | NULL | NULL | NULL | NULL | NULL |
| 2 | `R_EMPTY` | `DATE '1970-01-01'` | `TIMESTAMP '1970-01-01 00:00:00.000000'` | `TIMESTAMP '1970-01-01 00:00:00.000000000'` | `TIMESTAMP '1970-01-01 00:00:00.000000'` | `TO_TIMESTAMP_TZ('1970-01-01 00:00:00 +00:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM')` | `INTERVAL '0 0:0:0.000000' DAY TO SECOND` | `INTERVAL '0-0' YEAR TO MONTH` |
| 3 | `R_MIN` | `DATE '1900-01-01'` | `TIMESTAMP '1970-01-02 00:00:00.000001'` | `TIMESTAMP '1970-01-02 00:00:00.000000001'` | `TIMESTAMP '1970-01-02 00:00:00.000001'` | `TO_TIMESTAMP_TZ('1970-01-02 00:00:00 +00:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM')` | `INTERVAL '-10 23:59:59.999999' DAY TO SECOND` | `INTERVAL '-10-11' YEAR TO MONTH` |
| 4 | `R_MAX` | `DATE '9999-12-31'` | `TIMESTAMP '2038-01-18 23:59:59.999999'` | `TIMESTAMP '2038-01-18 23:59:59.999999999'` | `TIMESTAMP '2038-01-18 23:59:59.999999'` | `TO_TIMESTAMP_TZ('2038-01-18 23:59:59 +14:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM')` | `INTERVAL '10 23:59:59.999999' DAY TO SECOND` | `INTERVAL '10-11' YEAR TO MONTH` |
| 5 | `R_BOUNDARY` | `DATE '2024-02-29'` | `TIMESTAMP '2024-02-29 12:34:56.123456'` | `TIMESTAMP '2024-02-29 12:34:56.123456789'` | `TIMESTAMP '2024-02-29 12:34:56.123456'` | `TO_TIMESTAMP_TZ('2024-04-20 12:00:00 +09:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM')` | `INTERVAL '1 02:03:04.567890' DAY TO SECOND` | `INTERVAL '2-6' YEAR TO MONTH` |

`ts*_col` and `tsltz6_col` boundary values are constrained to CUBRID `TIMESTAMP`'s
1970-01-02 to 2038-01-18 range. CMT maps Oracle `TIMESTAMP(n)` and `TIMESTAMP WITH
LOCAL TIME ZONE` to CUBRID `TIMESTAMP`, so values outside this range fail online
migration with a "Cannot coerce host var to type timestamp" error. `date_col` keeps
the broader 1900–9999 range because CMT maps Oracle `DATE` to CUBRID `DATETIME`,
which supports the full year range.

### 5.4 `MAIN_SCHEMA.e2e_binary_types`

Columns:

| Column | Oracle type |
|---|---|
| `id` | `NUMBER(10)` PK |
| `raw_col` | `RAW(16)` |
| `blob_col` | `BLOB` |

SQL-ready values:

| id | row | raw_col | blob_col |
|---:|---|---|---|
| 1 | `R_NULL` | NULL | NULL |
| 2 | `R_EMPTY` | `HEXTORAW('')` | `EMPTY_BLOB()` |
| 3 | `R_MIN` | `HEXTORAW('00')` | `TO_BLOB(HEXTORAW('00'))` |
| 4 | `R_MAX` | `HEXTORAW('00FF00FF00FF00FF00FF00FF00FF00FF')` | `TO_BLOB(HEXTORAW('00FF00FF00FF00FF'))` |
| 5 | `R_BOUNDARY` | `HEXTORAW('CAFEBABE')` | `TO_BLOB(HEXTORAW('CAFEBABE'))` |

### 5.5 `MAIN_SCHEMA.e2e_oracle_locator_types`

이 table 은 Oracle extension 이며 core dataset 이 아니다.

Columns:

| Column | Oracle type |
|---|---|
| `id` | `NUMBER(10)` PK |
| `rowid_col` | `ROWID` |
| `urowid_col` | `UROWID` |

Oracle `ROWID` 값은 고정 literal 로 만들지 말고 기존 table row 에서 조회해 넣는다.

```sql
INSERT INTO MAIN_SCHEMA.e2e_oracle_locator_types (id, rowid_col, urowid_col)
SELECT 1, src.ROWID, src.ROWID
FROM MAIN_SCHEMA.e2e_customer src
WHERE src.customer_id = 1;
```

## 6. Bug/Feature Additions

Oracle 전용 회귀 케이스를 추가할 때는 다음 규칙을 따른다.

1. 가능한 경우 기존 `e2e_*` table 을 재사용한다.
2. Oracle 전용 동작은 `e2e_oracle_*` 객체에 둔다.
3. 특정 이슈에만 좁게 묶인 케이스는 `bug_<issue>_...` 이름을 사용한다.
4. 일반 회귀 위험으로 승격된 케이스는 `e2e_oracle_*` 객체로 옮긴다.
