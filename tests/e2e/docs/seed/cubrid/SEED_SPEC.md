# CUBRID Seed Specification

이 문서는 CUBRID source DB 용 시드 명세다. 공통 규칙은
`../COMMON_SEED_CONTRACT.md` 를 따른다.

CUBRID 의 자체 타입/객체 모델로 `Business Graph`, `Type Test Tables`,
`Object Samples` 의 검증 역할을 구현한다.

## 1. Scope

CUBRID seed 는 다음 기능을 검증한다.

| Area | CUBRID implementation |
|---|---|
| Schema roles | `MAIN_SCHEMA`, `REF_SCHEMA` users (CUBRID 의 user = schema) |
| Cross-schema | grant + synonym (CUBRID 11+) |
| Key generation | CUBRID `SERIAL` |
| Comment | object/column comment (CUBRID 11+) |
| Type extension | `MONETARY`, `BIT`/`BIT VARYING`, `ENUM`, `JSON` (CUBRID 11+), 컬렉션 (`SET`/`MULTISET`/`SEQUENCE`), timezone-aware datetime |

CUBRID 의 stored procedure / function 추출 범위는 CMT fetcher 에서 명확히
지원된 것으로 확인되지 않았으므로 본 spec 의 core 에서 제외한다. 추후 별도
extension 또는 bug fixture 로 반영한다.

CUBRID 의 OBJECT (OO 참조) 타입, 외부 Java 클래스 사용, 사용자 정의 도메인
역시 anti-coverage 이며 별도 extension 으로 분리한다.

### 1.1 시드 작성 시 주의해야 할 CUBRID 타입 동작

- `TIMESTAMP` / `TIMESTAMPTZ` / `TIMESTAMPLTZ` 의 유효 범위는 **1970-01-02 ~
  2038-01-18**. boundary 행은 이 범위 안으로 제한한다. 더 넓은 범위 (1900~9999)
  는 `DATETIME` / `DATETIMETZ` / `DATETIMELTZ` 컬럼에서만 사용한다.
- `CHAR(n)` / `VARCHAR(n)` / `NCHAR(n)` 은 byte-counted. 멀티바이트 글자가 n
  byte 예산을 초과하면 INSERT 가 거부된다. `R_MAX` / `R_UNICODE` 행은 ASCII
  만 사용하고 멀티바이트 unicode 커버리지는 `STRING` / `CLOB` / `NCHAR VARYING`
  같은 폭이 넓은 컬럼에 위임한다.
- `NCHAR(n)` / `NCHAR VARYING(n)` 컬럼에 INSERT 할 때는 N-prefix literal
  (`N'text'`) 를 사용한다. 일반 `''` 는 `Cannot coerce '' to type nchar varying`
  로 거부된다. 빈 문자열은 `N''`, ASCII 는 `N'A'`, 다국어는 `N'한中Ω'` 처럼
  쓴다.

### Phase 15 audit 발견사항

- **CHECK constraint** — CMT plugins 전체에 처리 로직 부재 (`buildCheck` /
  `CheckConstraint` / `CHECK_CONS` 모두 0 references). 시드의
  `ck_e2e_customer_status` 등은 source 에 만들어지지만 target 으로
  round-trip 안 됨. **의도된 anti-coverage** — 검증 TC 추가하지 않음.
- CUBRID 의 시드는 한글 inline COMMENT 가 없어서 Phase 15.3 의 다국어
  COMMENT mojibake 회귀 (Oracle / Tibero 에서 발견) 가 직접 관찰되지
  않는다. 향후 시드에 한글 COMMENT 를 추가하면 같은 회귀가 노출될 수
  있음. 자세한 내용은 Oracle / Tibero SEED_SPEC.

## 2. Schema Setup

| Schema | Role |
|---|---|
| `MAIN_SCHEMA` | main migration schema |
| `REF_SCHEMA` | cross-schema object and grant source |

`init/00_prepare_database.sql` 에서 DBA 권한으로 두 사용자를 생성하고 각 사용자에
필요한 객체 생성 권한을 부여한다. `MAIN_SCHEMA` 에 대한 object grant 는
`REF_SCHEMA.e2e_ref_audit` 생성 이후 `ref_schema/V3__grants_to_main.sql` 에 둔다.

```sql
-- init/00_prepare_database.sql (개념 예시)
CREATE USER REF_SCHEMA  PASSWORD 'cmt';
CREATE USER MAIN_SCHEMA PASSWORD 'cmt';

-- ref_schema/V3__grants_to_main.sql
GRANT SELECT, INSERT, UPDATE, DELETE
    ON e2e_ref_audit
    TO MAIN_SCHEMA;
```

CUBRID 는 식별자를 unquoted 일 때 case-insensitive 처리하지만 내부 카탈로그에는
입력 그대로 보관된다. 본 spec 은 schema 이름을 대문자 (`MAIN_SCHEMA`,
`REF_SCHEMA`) 로 통일한다.

## 3. Business Graph

### 3.1 `MAIN_SCHEMA.e2e_customer`

| Column | CUBRID type | Required data |
|---|---|---|
| `customer_id` | `INT` | PK |
| `customer_code` | `CHAR(4)` | unique, values `C001`.. |
| `customer_name` | `VARCHAR(100)` | non-null |
| `customer_alias` | `VARCHAR(60)` | nullable, supports unicode (byte-budget 충분) |
| `status` | `CHAR(1)` | `A`, `I`, `D` |
| `credit_limit` | `NUMERIC(15,2)` | NULL, 0, normal, high |
| `created_on` | `DATETIME` | fixed datetime |
| `updated_on` | `DATETIME` | nullable |

Required constraints:

```sql
PRIMARY KEY (customer_id)
UNIQUE (customer_code)
CHECK (status IN ('A', 'I', 'D'))
```

CUBRID comment 는 inline 문법으로 표기한다 (standalone `COMMENT ON ...` 미지원):

```sql
CREATE TABLE e2e_customer (
    customer_code CHAR(4) NOT NULL COMMENT 'business key',
    ...
) COMMENT 'E2E master customer table';
```

Required rows:

| customer_id | customer_code | customer_name | customer_alias | status | credit_limit | created_on |
|---:|---|---|---|---|---:|---|
| 1 | `C001` | `ALPHA CUSTOMER` | `Alpha Alias` | `A` | 12500.75 | `2024-01-10 00:00:00` |
| 2 | `C002` | `BETA CUSTOMER` | NULL | `I` | NULL | `2024-02-29 00:00:00` |
| 3 | `C003` | `한국 고객` | `별칭-한글` | `D` | 0 | `2024-03-15 00:00:00` |
| 4 | `C004` | `HIGH LIMIT CUSTOMER` | `High` | `A` | 9999999999999.99 | `2024-04-20 00:00:00` |

### 3.2 `MAIN_SCHEMA.e2e_order`

| Column | CUBRID type | Required data |
|---|---|---|
| `order_id` | `INT` | PK |
| `customer_id` | `INT` | FK to customer |
| `order_no` | `VARCHAR(30)` | business key |
| `order_status` | `VARCHAR(20)` | `NEW`, `HOLD`, `DONE`, `CANCEL` |
| `total_amount` | `NUMERIC(18,2)` | nullable amount |
| `ordered_at` | `DATETIME` | fixed datetime |
| `settled_at` | `DATETIME` | nullable |
| `source_comment` | `VARCHAR(200)` | blank/unicode/punctuation |

Required constraints/indexes:

```sql
PRIMARY KEY (order_id)
FOREIGN KEY (customer_id) REFERENCES MAIN_SCHEMA.e2e_customer(customer_id)
CHECK (order_status IN ('NEW', 'HOLD', 'DONE', 'CANCEL'))
CREATE INDEX idx_e2e_order_customer       ON MAIN_SCHEMA.e2e_order(customer_id);
CREATE INDEX idxd_e2e_order_ordered_at    ON MAIN_SCHEMA.e2e_order(ordered_at DESC);
CREATE INDEX idxf_e2e_order_upper_status  ON MAIN_SCHEMA.e2e_order(UPPER(order_status));
CREATE REVERSE INDEX idxr_e2e_order_no    ON MAIN_SCHEMA.e2e_order(order_no);
```

`idxr_*` (reverse index) 는 CUBRID 고유 인덱스 종류로 CUBRID-source 시드에서만
다룬다.

Required rows:

| order_id | customer_id | order_no | order_status | total_amount | source_comment |
|---:|---:|---|---|---:|---|
| 1 | 1 | `ORD-2024-001` | `NEW` | 50.00 | `first order` |
| 2 | 1 | `ORD-2024-002` | `DONE` | 125.50 | `done-order` |
| 3 | 2 | `ORD-2024-003` | `HOLD` | NULL | `  padded comment  ` |
| 4 | 3 | `ORD-2024-004` | `CANCEL` | 0 | `취소 주문 !@#` |

### 3.3 `MAIN_SCHEMA.e2e_order_line`

Composite PK 사용.

| Column | CUBRID type |
|---|---|
| `order_id` | `INT` |
| `line_no` | `SMALLINT` |
| `sku` | `VARCHAR(30)` |
| `qty` | `INT` |
| `unit_price` | `NUMERIC(15,2)` |
| `line_note` | `VARCHAR(200)` |

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

Self-reference FK (CMT CUBRID fetcher 가 self-FK 추출을 지원할 때만 적용; 미지원 시 `N/A`).

| Column | CUBRID type | Required data |
|---|---|---|
| `employee_id` | `INT` | PK |
| `manager_id` | `INT` | nullable self-reference FK |
| `emp_name` | `VARCHAR(100)` | non-null |
| `hired_on` | `DATE` | fixed date |

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

| Column | CUBRID type |
|---|---|
| `audit_id` | `INT` |
| `audit_message` | `VARCHAR(200)` |
| `created_at` | `DATETIME` |

```sql
PRIMARY KEY (audit_id)
```

Required row:

| audit_id | audit_message | created_at |
|---:|---|---|
| 1 | `reference audit row` | `2024-04-20 00:00:00` |

## 4. Object Samples

### 4.1 SERIAL (sequence)

```sql
CREATE SERIAL e2e_customer_seq START WITH 5 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SERIAL e2e_order_seq    START WITH 5 INCREMENT BY 1 NOCACHE NOCYCLE;
```

start value 는 §3.1 / §3.2 의 row id 다음 값 (5) 으로 둔다.

### 4.2 View

```sql
CREATE VIEW MAIN_SCHEMA.e2e_order_summary_v AS
SELECT o.order_id,
       o.order_no,
       c.customer_code,
       c.customer_name,
       o.order_status,
       o.total_amount,
       o.ordered_at
FROM MAIN_SCHEMA.e2e_order o
INNER JOIN MAIN_SCHEMA.e2e_customer c
    ON c.customer_id = o.customer_id;
```

CUBRID 의 view 는 `vclass` 로 카탈로그에 노출된다.

### 4.3 Synonym (CUBRID 11+)

```sql
CREATE SYNONYM MAIN_SCHEMA.e2e_ref_audit_syn
FOR REF_SCHEMA.e2e_ref_audit;
```

CUBRID 11 미만 버전을 검증해야 할 경우 이 객체를 `N/A` 로 표시하고 spec 의
"불가" 사실을 명시한다.

### 4.4 Comment

CUBRID 11+ 의 inline comment 문법을 사용한다 (`COMMENT 'text'` 절). 표준 SQL
의 standalone `COMMENT ON TABLE/COLUMN` 은 CUBRID 가 파싱하지 못한다.

§3.1 의 `e2e_customer` 에 적힌 table/column comment 외에 적어도 한 column 에
대해 다국어 unicode comment 를 둔다 (carrier 가 byte-budget 부족으로 잘리지
않는지 검증). column 정의 부분에 inline 으로 부착한다:

```sql
source_comment VARCHAR(200) COMMENT '주문 비고: 다국어/특수문자 검증',
```

### 4.5 Stored Procedure / Function

본 spec 의 core 에서는 제외한다 (§1 anti-coverage 참조). 별도 결정에 따라
extension 으로 추가한다.

## 5. Type Test Tables

### 5.1 `MAIN_SCHEMA.e2e_text_types`

Columns:

| Column | CUBRID type |
|---|---|
| `id` | `INT` PK |
| `char_col` | `CHAR(3)` |
| `varchar_col` | `VARCHAR(10)` |
| `nchar_col` | `NCHAR(3)` |
| `nvarchar_col` | `NCHAR VARYING(40)` |
| `string_col` | `STRING` (= `VARCHAR(1G)`) |
| `clob_col` | `CLOB` |

> CUBRID `CHAR(n)` / `VARCHAR(n)` / `NCHAR(n)` 은 byte-counted. multi-byte
> 글자가 n byte 예산을 초과하면 INSERT 거부. `R_MAX` 행은 ASCII 만 사용하고
> 멀티바이트 unicode 커버리지는 `nvarchar_col` (40 byte 예산), `string_col`,
> `clob_col` 에 둔다.

SQL-ready values:

| id | row | char_col | varchar_col | nchar_col | nvarchar_col | string_col | clob_col |
|---:|---|---|---|---|---|---|---|
| 1 | `R_NULL` | NULL | NULL | NULL | NULL | NULL | NULL |
| 2 | `R_EMPTY` | `''` | `''` | `''` | `''` | `''` | `''` |
| 3 | `R_MIN` | `'A'` | `'A'` | `'A'` | `'A'` | `'A'` | `'A'` |
| 4 | `R_MAX` | `'XYZ'` | `'ABCDEFGHIJ'` | `'XYZ'` | `'한국어 漢字 cafe'` | `RPAD('STR', 1000, 'X')` | `RPAD('CLOB', 1000, 'X')` |
| 5 | `R_UNICODE` | NULL | NULL | NULL | `'한中Ω 漢字'` | `'한국어 漢字 🚀 café'` | `'한국어 漢字 cafe punctuation !@#'` |
| 6 | `R_BOUNDARY` | `'   '` | `' A '` | `'   '` | `' A '` | `'line1' + CHR(10) + 'line2'` | `'line1' + CHR(10) + 'line2'` |

CUBRID 는 `''` 를 진짜 빈 문자열로 보존하므로 `R_EMPTY` 와 `R_NULL` 행이 의미상
명확히 구분된다.

### 5.2 `MAIN_SCHEMA.e2e_numeric_types`

Columns:

| Column | CUBRID type |
|---|---|
| `id` | `INT` PK |
| `smallint_col` | `SMALLINT` |
| `int_col` | `INT` |
| `bigint_col` | `BIGINT` |
| `numeric_col` | `NUMERIC(20,6)` |
| `numeric_p0_col` | `NUMERIC(38,0)` |
| `float_col` | `FLOAT` |
| `double_col` | `DOUBLE` |
| `monetary_col` | `MONETARY` |

CUBRID 는 IEEE 754 의 ±Infinity / NaN 을 `FLOAT` / `DOUBLE` literal 로 직접
표기하는 표준 syntax 를 제공하지 않는다 (런타임에 발생할 수 있지만 INSERT 로
명시 불가). 본 spec 의 core 에서는 IEEE special row 를 두지 않는다. 필요 시
`bug_<jira-id>_*` 또는 `e2e_cubrid_*` extension 으로 분리.

SQL-ready values:

| id | row | smallint_col | int_col | bigint_col | numeric_col | numeric_p0_col | float_col | double_col | monetary_col |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | `R_NULL` | NULL | NULL | NULL | NULL | NULL | NULL | NULL | NULL |
| 2 | `R_EMPTY` | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3 | `R_MIN` | -32768 | -2147483648 | -9223372036854775808 | -99999999999999.999999 | -99999999999999999999999999999999999999 | -3.4E38 | -1.7E308 | -1234567890.99 |
| 4 | `R_MAX` | 32767 | 2147483647 | 9223372036854775807 | 99999999999999.999999 | 99999999999999999999999999999999999999 | 3.4E38 | 1.7E308 | 1234567890.99 |
| 5 | `R_BOUNDARY` | 100 | 42 | 100000000000 | 1234567890.123456 | 12345678901234567890 | 3.14159 | 2.7182818 | 1234.56 |
| 6 | `R_REPRESENTATIVE` | 7 | 100 | 1000 | 42.424242 | 10000000000 | 6.283185 | 1.41421356 | 99.99 |

### 5.3 `MAIN_SCHEMA.e2e_temporal_types`

Columns:

| Column | CUBRID type |
|---|---|
| `id` | `INT` PK |
| `date_col` | `DATE` |
| `time_col` | `TIME` |
| `datetime_col` | `DATETIME` |
| `timestamp_col` | `TIMESTAMP` |
| `datetimetz_col` | `DATETIMETZ` |
| `datetimeltz_col` | `DATETIMELTZ` |
| `timestamptz_col` | `TIMESTAMPTZ` |
| `timestampltz_col` | `TIMESTAMPLTZ` |

> `timestamp_col` / `timestamptz_col` / `timestampltz_col` 의 유효 범위는
> 1970-01-02 ~ 2038-01-18. boundary 행은 이 범위 안으로 제한한다. `date_col`
> / `datetime_col` / `datetimetz_col` / `datetimeltz_col` 은 0001 ~ 9999 의
> 넓은 범위를 사용한다.

SQL-ready values:

| id | row | date_col | time_col | datetime_col | timestamp_col | datetimetz_col | datetimeltz_col | timestamptz_col | timestampltz_col |
|---:|---|---|---|---|---|---|---|---|---|
| 1 | `R_NULL` | NULL | NULL | NULL | NULL | NULL | NULL | NULL | NULL |
| 2 | `R_EMPTY` | `DATE '1970-01-01'` | `TIME '00:00:00'` | `DATETIME '1970-01-01 00:00:00.000'` | `TIMESTAMP '1970-01-02 00:00:00'` | `DATETIMETZ '1970-01-01 00:00:00.000 +00:00'` | `DATETIMELTZ '1970-01-01 00:00:00.000'` | `TIMESTAMPTZ '1970-01-02 00:00:00 +00:00'` | `TIMESTAMPLTZ '1970-01-02 00:00:00'` |
| 3 | `R_MIN` | `DATE '1900-01-01'` | `TIME '00:00:01'` | `DATETIME '1900-01-01 00:00:00.001'` | `TIMESTAMP '1970-01-02 00:00:01'` | `DATETIMETZ '1900-01-01 00:00:00.001 +00:00'` | `DATETIMELTZ '1900-01-01 00:00:00.001'` | `TIMESTAMPTZ '1970-01-02 00:00:01 +00:00'` | `TIMESTAMPLTZ '1970-01-02 00:00:01'` |
| 4 | `R_MAX` | `DATE '9999-12-31'` | `TIME '23:59:59'` | `DATETIME '9999-12-31 23:59:59.999'` | `TIMESTAMP '2038-01-18 23:59:59'` | `DATETIMETZ '9999-12-31 23:59:59.999 +14:00'` | `DATETIMELTZ '9999-12-31 23:59:59.999'` | `TIMESTAMPTZ '2038-01-18 23:59:59 +14:00'` | `TIMESTAMPLTZ '2038-01-18 23:59:59'` |
| 5 | `R_BOUNDARY` | `DATE '2024-02-29'` | `TIME '12:34:56'` | `DATETIME '2024-02-29 12:34:56.123'` | `TIMESTAMP '2024-02-29 12:34:56'` | `DATETIMETZ '2024-04-20 12:00:00.123 Asia/Seoul'` | `DATETIMELTZ '2024-04-20 12:00:00.123'` | `TIMESTAMPTZ '2024-04-20 12:00:00 +09:00'` | `TIMESTAMPLTZ '2024-04-20 12:00:00'` |

### 5.4 `MAIN_SCHEMA.e2e_binary_types`

Columns:

| Column | CUBRID type |
|---|---|
| `id` | `INT` PK |
| `bit_col` | `BIT(64)` |
| `varbit_col` | `BIT VARYING(1024)` |
| `blob_col` | `BLOB` |

CUBRID 의 `BIT(n)` 은 `n` bit (not byte) 길이; `BIT VARYING` 은 가변 길이.
literal 은 16진수 `X'...'` 또는 binary 형식 `B'...'` 사용.

SQL-ready values:

| id | row | bit_col | varbit_col | blob_col |
|---:|---|---|---|---|
| 1 | `R_NULL` | NULL | NULL | NULL |
| 2 | `R_EMPTY` | `B''` | `B''` | NULL |
| 3 | `R_MIN` | `X'00'` (필요 시 좌패딩) | `X'00'` | NULL |
| 4 | `R_MAX` | `X'FFFFFFFFFFFFFFFF'` (= 64 bit) | 1 KB hex pattern | 1 KB binary blob |
| 5 | `R_BOUNDARY` | `X'CAFEBABE'` | `X'CAFEBABE'` | `X'CAFEBABE'` |

`BLOB` 의 NULL/EMPTY 및 literal 표기 (예: `BIT_TO_BLOB(...)` 또는 직접 hex literal)
는 spec 작성 시점에 실측으로 확정한다.

### 5.5 `MAIN_SCHEMA.e2e_cubrid_collection_types` (CUBRID extension)

CUBRID 고유 타입. core dataset 이 아니다.

| Column | CUBRID type |
|---|---|
| `id` | `INT` PK |
| `set_int_col` | `SET(INT)` |
| `multiset_str_col` | `MULTISET(VARCHAR(20))` |
| `seq_double_col` | `SEQUENCE(DOUBLE)` |

SQL-ready values:

| id | row | set_int_col | multiset_str_col | seq_double_col |
|---:|---|---|---|---|
| 1 | `R_NULL` | NULL | NULL | NULL |
| 2 | `R_EMPTY` | `{}` | `{}` | `{}` |
| 3 | `R_SINGLE` | `{42}` | `{'one'}` | `{1.5}` |
| 4 | `R_MULTI` | `{1, 2, 3, 4, 5}` | `{'a', 'b', 'a', 'c', 'a'}` (multiset, 중복 허용) | `{1.1, 2.2, 3.3}` |
| 5 | `R_UNICODE` | NULL | `{'한국', '漢字', '🚀'}` | NULL |

### 5.6 `MAIN_SCHEMA.e2e_cubrid_enum_types` (CUBRID extension)

| Column | CUBRID type |
|---|---|
| `id` | `INT` PK |
| `status_enum` | `ENUM('NEW','HOLD','DONE','CANCEL')` |

| id | row | status_enum |
|---:|---|---|
| 1 | `R_NULL` | NULL |
| 2 | `R_FIRST` | `'NEW'` |
| 3 | `R_LAST` | `'CANCEL'` |
| 4 | `R_REPRESENTATIVE` | `'DONE'` |

ENUM 의 잘못된 값 (`'INVALID'`) 은 DB 가 거부하므로 seed 에 두지 않는다.

### 5.7 `MAIN_SCHEMA.e2e_cubrid_json_types` (CUBRID 11+ extension)

| Column | CUBRID type |
|---|---|
| `id` | `INT` PK |
| `payload` | `JSON` |

| id | row | payload |
|---:|---|---|
| 1 | `R_NULL` | NULL |
| 2 | `R_EMPTY_OBJECT` | `'{}'` |
| 3 | `R_EMPTY_ARRAY` | `'[]'` |
| 4 | `R_NESTED` | `'{"a":[1,2,{"b":"한국"}]}'` |
| 5 | `R_LARGE` | 5 KB 깊이 중첩 JSON |

CUBRID JSON literal 은 문자열로 표기 후 자동 파싱된다.

### 5.8 Locator-style types — 없음

CUBRID 에는 row 위치를 노출하는 pseudo locator 타입이 없으므로 별도 extension
테이블을 두지 않는다.

## 6. Bug/Feature Additions

CUBRID 전용 회귀 케이스를 추가할 때는 다음 규칙을 따른다.

1. 가능한 경우 기존 `e2e_*` table 을 재사용한다.
2. CUBRID 전용 동작은 `e2e_cubrid_*` 객체에 둔다.
3. 특정 이슈에만 좁게 묶인 케이스는 `bug_<jira-id>_...` 이름을 사용한다
   (`COMMON_SEED_CONTRACT.md` §2.2 의 Jira ID 변환 규칙 적용).
4. 일반 회귀 위험으로 승격된 케이스는 `e2e_cubrid_*` 객체로 옮긴다.
5. 추후 CMT 가 CUBRID 에서 stored procedure / function / Java SP / 외부
   클래스 / OBJECT 타입 추출을 명확히 지원하기 시작하면 본 spec 에 5.x 또는
   별도 §7 로 흡수한다.
