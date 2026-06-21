# MySQL Seed Specification

> ## ⚠ 0. 현재 상태: 보류 (Deferred)
>
> **MySQL E2E 테스트는 현재 실행 대상에서 제외되어 있다.** 시드 SQL,
> Container/Initializer, script.xml 픽스처, 테스트 클래스까지 모두 만들어
> 두었으나 `@Disabled` 처리 상태. CMT 의 MySQL 처리 결함이 production-grade
> 마이그레이션 검증을 어렵게 만드는 수준이라, 결함이 수정될 때까지 active
> 검증 대상에서 분리한다.
>
> ### 왜 보류했는가
>
> CMT 의 MySQL fetcher 가 부모 클래스 (`AbstractJDBCSchemaFetcher`) 의
> 기본 동작을 그대로 상속받아 **connection user 를 schema 이름으로
> 매핑**한다 (`AbstractJDBCSchemaFetcher.java:316`):
>
> ```java
> protected List<String> getSchemaNames(...) {
>     result.add(cp.getConUser().toUpperCase(Locale.US));
>     return result;
> }
> ```
>
> Oracle / CUBRID / Tibero / Informix 는 모두 자기 식대로 override 하지만
> **MySQL / MariaDB 만 override 안 함** → connection user `root` 가
> source schema `ROOT` 로 잘못 매핑된다.
>
> 그런데 MySQL 의 schema 의미는 user 가 아니라 **database** 이다 (Oracle
> 모델과 다름). MySQL 엔진은 `information_schema.views` 의 view body 를
> 자동으로 `` `main_schema`.`<table>` `` (database 이름 prefix) 로 확장해
> 저장하므로, CMT 가 만든 `<schema source="ROOT">` 매핑과 view body 의
> `main_schema.X` literal 이 의미적으로 충돌. CMT 가 그 본문을 그대로
> CUBRID 로 옮기면서 prefix 를 `dba.<table>` 로 잘못 매핑 → import 실패.
>
> 추가 anti-coverage 항목 (§1 표 참고): JSON 매핑 부재, SET 변환 안 됨,
> MySQL 8.0 의 `mysql.proc` 부재로 routine 추출 실패, view 마이그레이션
> 본문 변환 버그 등 — **마이그레이션 도구의 정확성 자체에 영향을 주는
> first-class 결함이 다수**.
>
> 이 상태로 E2E 테스트를 active 로 두면 "test green = MySQL 마이그레이션
> 정상" 이라는 잘못된 신호를 줄 수 있어 의도적으로 보류한다.
>
> ### 보류 해제 조건
>
> 다음을 만족해야 active 로 복귀:
>
> 1. **`MySQLSchemaFetcher.getSchemaNames` override** 추가해서
>    `cp.getDbName().toUpperCase()` 를 반환 (database-as-schema 로 정정)
> 2. **MySQL → CUBRID view body translator** 가 source DB 이름 prefix 를
>    target schema 로 정상 변환 (`main_schema.X` → `<target_schema>.X`)
> 3. JSON / SET / 8.0 routines 매핑 — 별도 anti-coverage 결정으로 명시
>    하거나 구현 (§1 표 참고). 모든 항목이 풀릴 필요는 없으나, 풀린
>    항목은 시드에 포함시킨 뒤 테스트 활성.
>
> ### 지금까지 만들어 둔 것 (보류 해제 시 재사용)
>
> | 항목 | 상태 |
> |---|---|
> | 본 SPEC | 작성 완료 — anti-coverage 표가 CMT 결함 카탈로그로 활용 가능 |
> | `db/mysql/{init,main_schema}/*` 시드 SQL | 동작 확인됨 |
> | `MySqlContainer.java`, `MysqlDatabaseInitializer.java` | 동작 |
> | `Drivers.MYSQL` + `pom.xml` (`mysql-connector-j`) | 동작 |
> | `RegenerateScripts` `MYSQL_TO_CUBRID` / `MYSQL_TO_DUMPFILE` | 동작 |
> | `script.xml` 픽스처 + dump goldens | 보존 (재 생성 시 재사용 가능) |
> | `MysqlToCubridTest` / `MysqlToDumpTest` | `@Disabled("DEFERRED — see SEED_SPEC §0")` 상태 |
>
> ---

이 문서는 MySQL source DB 용 시드 명세다. 공통 규칙은
`../COMMON_SEED_CONTRACT.md` 를 따른다.

MySQL 의 데이터베이스/유저/타입 모델로 `Business Graph`, `Type Test Tables`,
`Object Samples` 의 검증 역할을 구현한다.

대상 버전은 **MySQL 8.0** 이다. 5.7 호환을 명시적으로 검증해야 할 때는
`bug_<jira-id>_*` 으로 분리한다.

## 1. Scope

MySQL seed 는 다음 기능을 검증한다.

| Area | MySQL implementation |
|---|---|
| Schema role | `main_schema` database (single-database; `ref_schema`는 anti-coverage — 아래 참조) |
| Key generation | `AUTO_INCREMENT` 컬럼 (MySQL core 8.x 에 SEQUENCE 객체 없음) |
| Comment | inline `COMMENT 'text'` (table/column 모두) |
| Type extension | `ENUM`, `BIT`, `YEAR`, `LONGTEXT`/`LONGBLOB` (JSON, SET, stored routines 는 anti-coverage) |

다음은 anti-coverage. 별도 결정/이슈로 등록되기 전까지는 seed 에 포함하지 않는다.

| Excluded | Reason |
|---|---|
| **Cross-schema (`ref_schema`, GRANT, SYNONYM)** | `MySQLSchemaFetcher` 가 `buildGrant` / `buildSynonym` 를 override 하지 않음 → 부모 `AbstractJDBCSchemaFetcher` 의 `// do nothing` 기본 구현이 사용되어 둘 다 빈 결과. 또한 CMT MySQL fetcher 는 connection 의 user/database 를 단일 schema 로 collapse 하여 multi-database 시나리오 자체를 표현 못 함. → ref_schema 와 e2e_ref_audit 자체를 seed 에서 제외 |
| **View** | CMT 의 MySQL → CUBRID view body 변환 버그: MySQL 은 view 정의를 카탈로그에 저장할 때 base table 을 `main_schema.<table>` 형태로 자동 확장한다. CMT 가 그 본문을 그대로 읽어 CUBRID `ALTER VCLASS ... ADD QUERY` 로 옮기면서 prefix 를 `dba.<table>` 로 잘못 매핑 (실제 target schema 는 `ROOT`) → `Unknown class dba.<table>` 로 import 실패. 시드의 `CREATE VIEW` 본문에서 prefix 를 빼도 MySQL 자동 확장으로 다시 붙으므로 seed-side 우회 불가 → CMT 패치 전까지 anti-coverage |
| **`JSON` 컬럼** | `MySQLDataTypeMappingHelper` 에 "json" 키워드 부재 → MySQL → CUBRID 타입 매핑 미구현 → target 에서 silently drop. 매핑이 추가될 때까지 anti-coverage |
| **`SET` 컬럼** | CUBRID 에 SET scalar 등가 타입이 없고, CMT 의 import 경로에서 `Communication error` 로 batch 가 통째로 거부됨. ENUM 은 동작하지만 SET 은 별도 회귀 케이스로 분리될 때까지 anti-coverage |
| **Stored routines (function / procedure) — MySQL 8.0** | `MySQLSchemaFetcher.buildProcedures` 가 MySQL 5.7 의 `mysql.proc` 테이블을 읽으려 하지만 MySQL 8.0 에서 이 테이블은 제거됨 (data dictionary 로 이전). 결과적으로 8.0 환경에서 routine 추출이 실패 (`Table 'mysql.proc' doesn't exist`) → 8.0 한정 anti-coverage. 5.7 회귀 검증이 필요해지면 별도 fixture 로 분리 |
| Spatial / `GEOMETRY` | CMT MySQL fetcher 매핑 미확정 |
| `ROW_FORMAT`, partitioning, `ENGINE=` 변형 | E2E 폭증 |
| `GENERATED` / `VIRTUAL` / `STORED` 컬럼 | CMT 매핑 미확정 |
| Trigger / Event scheduler | CUBRID seed 작성 단계에서 CMT fetcher 동작 모호로 제외; 모든 source DB seed 가 같은 비교 기준을 유지하기 위해 MySQL 도 동일 결정 |
| `ZEROFILL`, `UNSIGNED` 변형 | CMT 매핑이 sign-aware 가 아니어서 회귀 위험 모호 |

### 1.1 시드 작성 시 주의해야 할 MySQL 타입 동작

- **TIMESTAMP 유효 범위는 1970-01-01 00:00:01 UTC ~ 2038-01-19 03:14:07 UTC.**
  boundary 행은 이 범위 안으로 제한한다. 더 넓은 1000-01-01 ~ 9999-12-31 범위는
  `DATETIME` 컬럼에서만 사용한다.
- **MySQL `CHAR(n)` / `VARCHAR(n)` 은 character-counted** (기본 charset
  utf8mb4 기준 1 글자가 최대 4 byte). Oracle `CHAR(n CHAR)` 와 동일 의미이며,
  CUBRID 의 byte-counted 와 다르다. 결과적으로 R_MAX 행에 multi-byte 글자를
  넣어도 length 안에서 안전하다.
- **`''` 는 빈 문자열로 보존된다** (Oracle 처럼 NULL 로 변환되지 않음). 따라서
  `R_EMPTY` 행과 `R_NULL` 행이 의미상 분명히 분리된다.
- **`NCHAR` / `NVARCHAR` 는 별도 컬럼으로 두지 않는다.** MySQL 에서 이 alias
  는 `CHAR ... CHARACTER SET utf8` 으로 풀리는 동의어이므로 일반 `VARCHAR
  CHARACTER SET utf8mb4` 컬럼과 사실상 같다. 추가 차별 검증값이 없다.
- **DATETIME(6) / TIMESTAMP(6) 의 fractional seconds 는 6 자리** 까지 저장
  된다. CUBRID `DATETIME` 은 3 자리이므로 migration 시 truncate 가 발생할 수
  있다. Type Test Table 의 R_BOUNDARY 에서 6-digit 정밀도 값을 두어 truncate
  여부를 골든 검증으로 잡는다.
- **`utf8mb4_0900_ai_ci`** 가 MySQL 8 의 default collation. accent-insensitive
  + case-insensitive 이며, sort/equality 비교 결과가 utf8 (5.7) 시절과 다르다.
  이 spec 은 8.0 collation 을 기준으로 한다. 5.7 회귀가 필요하면
  `utf8mb4_general_ci` 를 사용하는 별도 fixture 로 분리한다.
- **`AUTO_INCREMENT` 컬럼에 explicit 값을 INSERT** 하면 MySQL 의 internal
  counter 는 입력값보다 작은 경우에만 그 값+1 로 갱신된다. 즉 R1..R4 에
  1..4 를 쓴 뒤 `AUTO_INCREMENT=5` 시작값을 그대로 유지하려면 `CREATE TABLE`
  옵션에 명시한다.

## 2. Schema Setup

| Role | MySQL identifier |
|---|---|
| `MAIN_SCHEMA` (role) | `main_schema` (database) |
| `REF_SCHEMA`  (role) | **N/A** — §1 anti-coverage |

`init/00_prepare_database.sql` 에서 root 권한으로 main_schema database 와
main_user 를 만든다.

```sql
-- init/00_prepare_database.sql (개념 예시)
CREATE DATABASE main_schema CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'main_user'@'%' IDENTIFIED BY 'cmt';

GRANT ALL PRIVILEGES ON main_schema.* TO 'main_user'@'%';
```

> MySQL 의 `user@host` 모델은 Oracle/CUBRID 와 다르다. 본 spec 은 host 부분을
> `'%'` 로 통일해 컨테이너 IP 와 무관하게 동작하도록 한다.

## 3. Business Graph

### 3.1 `main_schema.e2e_customer`

| Column | MySQL type | Required data |
|---|---|---|
| `customer_id` | `INT AUTO_INCREMENT` | PK; AUTO_INCREMENT 시작값 5 |
| `customer_code` | `CHAR(4)` | unique, values `C001`.. |
| `customer_name` | `VARCHAR(100)` | non-null |
| `customer_alias` | `VARCHAR(60)` | nullable, supports unicode (utf8mb4) |
| `status` | `CHAR(1)` | `A`, `I`, `D` |
| `credit_limit` | `DECIMAL(15,2)` | NULL, 0, normal, high |
| `created_on` | `DATETIME` | fixed datetime |
| `updated_on` | `DATETIME` | nullable |

Required constraints:

```sql
PRIMARY KEY (customer_id)
UNIQUE KEY uk_e2e_customer_code (customer_code)
CONSTRAINT ck_e2e_customer_status CHECK (status IN ('A', 'I', 'D'))
```

> CHECK constraint 는 MySQL **8.0.16+** 에서만 enforce 된다. 그 미만 버전에서는
> CMT 가 fetch 만 하고 enforce 는 되지 않는다. 본 spec 의 대상 버전 (8.0) 에서는
> enforce 동작을 가정한다.

MySQL 의 inline COMMENT 문법:

```sql
CREATE TABLE main_schema.e2e_customer (
    customer_id   INT NOT NULL AUTO_INCREMENT,
    customer_code CHAR(4) NOT NULL COMMENT 'business key',
    ...
    PRIMARY KEY (customer_id)
) AUTO_INCREMENT=5,
  COMMENT='E2E master customer table';
```

Required rows (id 1..4 는 explicit 값 사용; AUTO_INCREMENT counter 는 5 유지):

| customer_id | customer_code | customer_name | customer_alias | status | credit_limit | created_on |
|---:|---|---|---|---|---:|---|
| 1 | `C001` | `ALPHA CUSTOMER` | `Alpha Alias` | `A` | 12500.75 | `2024-01-10 00:00:00` |
| 2 | `C002` | `BETA CUSTOMER` | NULL | `I` | NULL | `2024-02-29 00:00:00` |
| 3 | `C003` | `한국 고객` | `별칭-한글` | `D` | 0 | `2024-03-15 00:00:00` |
| 4 | `C004` | `HIGH LIMIT CUSTOMER` | `High` | `A` | 9999999999999.99 | `2024-04-20 00:00:00` |

### 3.2 `main_schema.e2e_order`

| Column | MySQL type | Required data |
|---|---|---|
| `order_id` | `INT AUTO_INCREMENT` | PK; AUTO_INCREMENT 시작값 5 |
| `customer_id` | `INT` | FK to customer |
| `order_no` | `VARCHAR(30)` | business key |
| `order_status` | `VARCHAR(20)` | `NEW`, `HOLD`, `DONE`, `CANCEL` |
| `total_amount` | `DECIMAL(18,2)` | nullable amount |
| `ordered_at` | `DATETIME` | fixed datetime |
| `settled_at` | `DATETIME` | nullable |
| `source_comment` | `VARCHAR(200)` | blank/unicode/punctuation |

Required constraints/indexes:

```sql
PRIMARY KEY (order_id)
CONSTRAINT fk_e2e_order_customer FOREIGN KEY (customer_id)
    REFERENCES main_schema.e2e_customer(customer_id)
CONSTRAINT ck_e2e_order_status CHECK (order_status IN ('NEW','HOLD','DONE','CANCEL'))

CREATE INDEX idx_e2e_order_customer       ON main_schema.e2e_order(customer_id);
CREATE INDEX idxd_e2e_order_ordered_at    ON main_schema.e2e_order(ordered_at DESC);
CREATE INDEX idxf_e2e_order_upper_status  ON main_schema.e2e_order((UPPER(order_status)));
```

> Descending index 는 MySQL **8.0+**, functional index 는 MySQL **8.0.13+** 에서
> 지원된다. functional 표현식은 반드시 두 겹 괄호로 감싸야 한다 (`((expr))`).
> Reverse index 는 MySQL 에 개념이 없으므로 두지 않는다.

Required rows:

| order_id | customer_id | order_no | order_status | total_amount | ordered_at | source_comment |
|---:|---:|---|---|---:|---|---|
| 1 | 1 | `ORD-2024-001` | `NEW` | 50.00 | `2024-01-15 00:00:00` | `first order` |
| 2 | 1 | `ORD-2024-002` | `DONE` | 125.50 | `2024-02-01 00:00:00` | `done-order` |
| 3 | 2 | `ORD-2024-003` | `HOLD` | NULL | `2024-03-01 00:00:00` | `  padded comment  ` |
| 4 | 3 | `ORD-2024-004` | `CANCEL` | 0 | `2024-04-10 00:00:00` | `취소 주문 !@#` |

### 3.3 `main_schema.e2e_order_line`

Composite PK 사용.

| Column | MySQL type |
|---|---|
| `order_id` | `INT` |
| `line_no` | `SMALLINT` |
| `sku` | `VARCHAR(30)` |
| `qty` | `INT` |
| `unit_price` | `DECIMAL(15,2)` |
| `line_note` | `VARCHAR(200)` |

Required constraints:

```sql
PRIMARY KEY (order_id, line_no)
CONSTRAINT fk_e2e_order_line_order FOREIGN KEY (order_id)
    REFERENCES main_schema.e2e_order(order_id)
CONSTRAINT ck_e2e_order_line_qty CHECK (qty > 0)
```

Required rows:

| order_id | line_no | sku | qty | unit_price | line_note |
|---:|---:|---|---:|---:|---|
| 1 | 1 | `SKU-ALPHA` | 2 | 25.00 | `normal` |
| 2 | 1 | `SKU-BETA` | 1 | 125.50 | NULL |
| 3 | 1 | `SKU-HOLD` | 10 | 9.99 | `hold line` |
| 4 | 1 | `SKU-CANCEL` | 1 | 0 | `cancelled` |

### 3.4 `main_schema.e2e_employee`

Self-reference FK (MySQL 은 native 지원).

| Column | MySQL type | Required data |
|---|---|---|
| `employee_id` | `INT` | PK |
| `manager_id` | `INT` | nullable self-reference FK |
| `emp_name` | `VARCHAR(100)` | non-null |
| `hired_on` | `DATE` | fixed date |

```sql
PRIMARY KEY (employee_id)
CONSTRAINT fk_e2e_employee_manager FOREIGN KEY (manager_id)
    REFERENCES main_schema.e2e_employee(employee_id)
```

Required rows:

| employee_id | manager_id | emp_name | hired_on |
|---:|---:|---|---|
| 1 | NULL | `CEO` | `2020-01-01` |
| 2 | 1 | `Manager A` | `2021-01-01` |
| 3 | 2 | `Staff A1` | `2022-01-01` |

### 3.5 `ref_schema.e2e_ref_audit` — N/A

§1 anti-coverage 결정으로 ref_schema 자체와 cross-schema reference 객체는
seed 에 포함하지 않는다 (CMT MySQL fetcher 의 grant/synonym/multi-database
미지원).

## 4. Object Samples

### 4.1 Auto-increment (MySQL 의 sequence 등가물)

MySQL core 에는 별도 `SEQUENCE` 객체가 없으므로 sequence 검증은 PK 컬럼의
`AUTO_INCREMENT` 시작값으로 대체한다. `e2e_customer` / `e2e_order` 의
`CREATE TABLE` 절에 `AUTO_INCREMENT=5` 옵션을 둔다 (§3.1, §3.2 참조).

```sql
ALTER TABLE main_schema.e2e_customer AUTO_INCREMENT=5;
ALTER TABLE main_schema.e2e_order    AUTO_INCREMENT=5;
```

CMT 가 MySQL `AUTO_INCREMENT` 컬럼을 CUBRID `SERIAL` 로 변환하는지 또는
attribute 의 auto_increment 플래그로 보존하는지 여부는 첫 회 테스트에서
확인한다.

### 4.2 View — N/A

§1 anti-coverage 결정으로 view 도 seed 에 포함하지 않는다. 근거: MySQL 의
view body auto-expansion 으로 base table 참조가 카탈로그에 `main_schema.X`
형태로 저장되고, CMT 가 이 본문을 그대로 읽어 CUBRID `ALTER VCLASS ... ADD
QUERY` 로 변환할 때 prefix 를 `dba.X` 로 잘못 매핑 (실제 target schema 는
`ROOT`) → `Unknown class dba.<table>` 로 view import 가 항상 실패한다.
seed-side 에서 prefix 제거를 시도해도 MySQL 이 자동 확장으로 다시 붙이므로
우회 불가 → CMT 패치 전까지 보류.

### 4.3 Synonym — N/A

두 가지 이유로 N/A: (1) MySQL 은 native synonym 객체가 없음. (2) §1
anti-coverage — `MySQLSchemaFetcher.buildSynonym()` 는 부모 클래스의
`// do nothing` 기본 구현을 그대로 사용해 어떤 synonym 도 추출하지 않는다.

### 4.4 Comment

MySQL 의 inline `COMMENT` 절을 사용한다. 표준 SQL 의 standalone
`COMMENT ON TABLE/COLUMN` 은 MySQL 이 파싱하지 못한다. column 정의에는 따옴표
없는 키워드 `COMMENT 'text'`, table 정의 끝에는 `COMMENT='text'` (등호 사용)
형식을 쓴다 — 둘 다 정보 스키마 `INFORMATION_SCHEMA.COLUMNS.COLUMN_COMMENT`
및 `TABLES.TABLE_COMMENT` 에 그대로 보존된다.

§3.1 의 `e2e_customer` 외에 적어도 한 column 에는 다국어 unicode comment 를
부여한다 (charset 보존 검증).

Required comments:

| Object | Comment |
|---|---|
| `main_schema.e2e_customer` (table) | `E2E master customer table` |
| `main_schema.e2e_customer.customer_code` | `business key` |
| `main_schema.e2e_customer.customer_alias` | `고객 별칭 (다국어 검증)` |
| `main_schema.e2e_order` (table) | `Customer order transactions` |
| `main_schema.e2e_order.source_comment` | `주문 비고: 다국어/특수문자 검증` |
| `main_schema.e2e_order_line` (table) | `Order line items (composite PK)` |
| `main_schema.e2e_employee` (table) | `Employee hierarchy (self-FK)` |
| `main_schema.e2e_employee.manager_id` | `Self-FK to direct manager` |

> view (`e2e_order_summary_v`) 자체가 §1 anti-coverage 라 view 코멘트도
> 자동으로 제외된다. ref_schema 객체 (`e2e_ref_audit` 테이블/컬럼 코멘트
> 3건) 역시 §1 anti-coverage. 따라서 Oracle 13 건, CUBRID 13 건 대비 MySQL
> 은 8 건이 된다.

Type Test Tables (`e2e_*_types`) 는 내부 검증용 scaffolding 이므로 별도
comment 를 부여하지 않는다.

### 4.5 Stored Procedure / Function — N/A (MySQL 8.0)

§1 anti-coverage 결정으로 stored function / procedure 는 seed 에 포함하지
않는다. 근거: `MySQLSchemaFetcher.buildProcedures` 가 MySQL 5.7 의
`mysql.proc` 테이블을 읽으려 하지만 MySQL 8.0 에서 이 테이블은 제거됨
(`Table 'mysql.proc' doesn't exist`). fetcher 가 MySQL 8.0 의 새 data
dictionary (`information_schema.routines`) 를 인식하도록 패치되기 전까지는
routine 추출 자체가 동작하지 않는다.

## 5. Type Test Tables

### 5.1 `main_schema.e2e_text_types`

Columns:

| Column | MySQL type |
|---|---|
| `id` | `INT` PK |
| `char_col` | `CHAR(3)` |
| `varchar_col` | `VARCHAR(10)` |
| `text_col` | `TEXT` |
| `mediumtext_col` | `MEDIUMTEXT` |
| `longtext_col` | `LONGTEXT` |

> MySQL 의 `CHAR(n)` / `VARCHAR(n)` 은 character-counted (utf8mb4 기준 글자 수).
> Oracle `CHAR(n CHAR)` 와 동일하므로 multi-byte 글자도 length 안에서 안전하다.
> CUBRID 의 byte-counted CHAR/VARCHAR 와 다른 점이다. 따라서 R_MAX 행에 multi-byte
> 글자를 직접 넣어 테스트한다.

> `NCHAR` / `NVARCHAR` 는 `CHAR ... CHARACTER SET utf8` alias 이므로 별도
> 컬럼으로 두지 않는다 (§1.1 참조).

SQL-ready values:

| id | row | char_col | varchar_col | text_col | mediumtext_col | longtext_col |
|---:|---|---|---|---|---|---|
| 1 | `R_NULL` | NULL | NULL | NULL | NULL | NULL |
| 2 | `R_EMPTY` | `''` | `''` | `''` | `''` | `''` |
| 3 | `R_MIN` | `'A'` | `'A'` | `'A'` | `'A'` | `'A'` |
| 4 | `R_MAX` | `'XYZ'` | `'ABCDEFGHIJ'` | `REPEAT('X', 1000)` | `REPEAT('Y', 4000)` | `REPEAT('Z', 4000)` |
| 5 | `R_UNICODE` | NULL | NULL | `'한국어 漢字 🚀 café'` | `'한국어 漢字 cafe punctuation !@#'` | `'한국어 漢字 cafe punctuation !@#'` |
| 6 | `R_BOUNDARY` | `'   '` | `' A '` | `CONCAT('line1', CHAR(10), 'line2')` | `CONCAT('line1', CHAR(10), 'line2')` | `CONCAT('line1', CHAR(10), 'line2')` |

> `char_col` (CHAR(3)) 의 R_MAX 는 `'XYZ'`, R_UNICODE 는 NULL 로 둔다. MySQL
> 자체는 character-counted 라 multi-byte 글자도 들어가지만, **CMT MySQL →
> CUBRID 매핑은 CHAR 의 byte budget 을 그대로 옮긴다** (CUBRID 의 CHAR 는
> byte-counted). multi-byte 값은 batch insert 시 `Cannot coerce host var
> to type char` 로 거부되므로 char_col 에는 ASCII 만 둔다. 다국어 unicode
> 커버리지는 폭이 넓은 `varchar_col` (VARCHAR(10) 안에서 ASCII 10자) 보다도
> `text_col`/`mediumtext_col`/`longtext_col` 에서 검증한다.
>
> `MEDIUMTEXT` / `LONGTEXT` 의 R_MAX 는 4 KB (`REPEAT('Y'/'Z', 4000)`) 로 둔다.
> 더 큰 페이로드 (수십 KB ~ MB 급) 는 CMT 의 batch import 단계에서
> `Communication error` 를 일으켜 batch 가 통째로 거부되는 사례가 관찰됨 →
> E2E 의 round-trip 검증 목적에는 4 KB 로 충분하고, 대용량 페이로드 검증은
> 별도 부하 테스트의 책임으로 분리한다.

### 5.2 `main_schema.e2e_numeric_types`

Columns:

| Column | MySQL type |
|---|---|
| `id` | `INT` PK |
| `tinyint_col` | `TINYINT` |
| `smallint_col` | `SMALLINT` |
| `mediumint_col` | `MEDIUMINT` |
| `int_col` | `INT` |
| `bigint_col` | `BIGINT` |
| `decimal_col` | `DECIMAL(20,6)` |
| `decimal_p0_col` | `DECIMAL(38,0)` |
| `float_col` | `FLOAT` |
| `double_col` | `DOUBLE` |

SQL-ready values:

| id | row | tinyint_col | smallint_col | mediumint_col | int_col | bigint_col | decimal_col | decimal_p0_col | float_col | double_col |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | `R_NULL` | NULL | NULL | NULL | NULL | NULL | NULL | NULL | NULL | NULL |
| 2 | `R_EMPTY` | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3 | `R_MIN` | -128 | -32768 | -8388608 | -2147483648 | -9223372036854775808 | -99999999999999.999999 | -99999999999999999999999999999999999999 | -3.4E38 | -1.7E308 |
| 4 | `R_MAX` | 127 | 32767 | 8388607 | 2147483647 | 9223372036854775807 | 99999999999999.999999 | 99999999999999999999999999999999999999 | 3.4E38 | 1.7E308 |
| 5 | `R_BOUNDARY` | 100 | 100 | 100 | 42 | 100000000000 | 1234567890.123456 | 12345678901234567890 | 3.14159 | 2.7182818 |
| 6 | `R_REPRESENTATIVE` | 7 | 7 | 7 | 100 | 1000 | 42.424242 | 10000000000 | 6.283185 | 1.41421356 |

> MySQL 은 IEEE special (`+Inf`/`-Inf`/`NaN`) literal 을 직접 INSERT 하면 0
> 또는 truncate 처리한다 (`ER_TRUNCATED_WRONG_VALUE_FOR_FIELD`). core seed 에서는
> 두지 않는다. 필요 시 `bug_<jira-id>_*` 또는 `e2e_mysql_*` extension 으로 분리.
>
> `UNSIGNED`, `ZEROFILL` 변형은 §1 anti-coverage. 부호 있는 컬럼만 둔다.

### 5.3 `main_schema.e2e_temporal_types`

Columns:

| Column | MySQL type |
|---|---|
| `id` | `INT` PK |
| `date_col` | `DATE` |
| `time_col` | `TIME(6)` |
| `datetime_col` | `DATETIME(6)` |
| `timestamp_col` | `TIMESTAMP(6)` |
| `year_col` | `YEAR` |

> `timestamp_col` 의 유효 범위는 1970-01-01 00:00:01 UTC ~ 2038-01-19 03:14:07 UTC.
> boundary 행은 이 범위 안으로 제한한다. `date_col` / `datetime_col` 은 1000-01-01
> ~ 9999-12-31 의 넓은 범위를 사용한다.
>
> `YEAR` 는 MySQL 고유 4-digit 타입 (1901..2155, 그리고 0). CUBRID/Oracle 에는
> 직접 등가 타입이 없으므로 CMT 는 `INT` 또는 `SMALLINT` 로 매핑할 가능성이 높다.
> migration 시 의미 보존 여부를 골든으로 검증한다.

SQL-ready values:

| id | row | date_col | time_col | datetime_col | timestamp_col | year_col |
|---:|---|---|---|---|---|---:|
| 1 | `R_NULL` | NULL | NULL | NULL | NULL | NULL |
| 2 | `R_EMPTY` | `'1970-01-01'` | `'00:00:00.000000'` | `'1970-01-01 00:00:00.000000'` | `'1970-01-01 00:00:01.000000'` | `1970` |
| 3 | `R_MIN` | `'1000-01-01'` | `'00:00:00.000001'` | `'1000-01-01 00:00:00.000001'` | `'1970-01-01 00:00:02.000001'` | `1901` |
| 4 | `R_MAX` | `'9999-12-31'` | `'23:59:59.999999'` | `'9999-12-31 23:59:59.999999'` | `'2038-01-19 03:14:07.999999'` | `2155` |
| 5 | `R_BOUNDARY` | `'2024-02-29'` | `'12:34:56.123456'` | `'2024-02-29 12:34:56.123456'` | `'2024-02-29 12:34:56.123456'` | `2024` |

> `time_col` 의 R_MIN 으로 `'-838:59:59.000000'` (MySQL 의 음수 시간 lower bound)
> 같은 음수 값을 두는 것도 가능하지만 CUBRID `TIME` 에 등가 표현이 없어 migration
> 결과가 fail 한다. core seed 에는 0 이상 값만 둔다. 음수 시간 회귀가 필요하면
> `bug_<jira-id>_*` 로 분리.

### 5.4 `main_schema.e2e_binary_types`

Columns:

| Column | MySQL type |
|---|---|
| `id` | `INT` PK |
| `binary_col` | `BINARY(8)` |
| `varbinary_col` | `VARBINARY(64)` |
| `bit_col` | `BIT(64)` |
| `blob_col` | `BLOB` |
| `longblob_col` | `LONGBLOB` |

SQL-ready values:

| id | row | binary_col | varbinary_col | bit_col | blob_col | longblob_col |
|---:|---|---|---|---|---|---|
| 1 | `R_NULL` | NULL | NULL | NULL | NULL | NULL |
| 2 | `R_EMPTY` | `X'0000000000000000'` | `X''` | `b'0'` | `X''` | NULL |
| 3 | `R_MIN` | `X'0000000000000000'` | `X'00'` | `0x0000000000000000` | `X'00'` | NULL |
| 4 | `R_MAX` | `X'FFFFFFFFFFFFFFFF'` | `X'00FF00FF00FF00FF00FF00FF00FF00FF'` | `0xFFFFFFFFFFFFFFFF` | `X'00FF00FF00FF00FF00FF00FF00FF00FF'` | `REPEAT(X'CAFEBABE', 100000)` |
| 5 | `R_BOUNDARY` | `X'00000000CAFEBABE'` | `X'CAFEBABE'` | `0x00000000CAFEBABE` | `X'CAFEBABE'` | `X'CAFEBABE'` |

> MySQL `BINARY(n)` 은 fixed length 이므로 짧은 값은 `0x00` 으로 right-pad 된다.
> R_EMPTY 의 binary_col 이 `X'0000000000000000'` 인 이유.
>
> `BIT(n)` literal 은 `b'<0/1 bits>'` 형태와 hex `0x...` / `X'...'` 둘 다 허용된다.
> 16진수가 64-bit 표현에 더 읽기 편하므로 R_MIN/R_MAX/R_BOUNDARY 는 `0x` 형식을
> 사용했다 (R_EMPTY 의 `b'0'` 은 짧은 binary literal 검증 차원에서 그대로 둔다).

### 5.5 `main_schema.e2e_mysql_enum_types` (MySQL extension)

MySQL 고유의 ENUM 컬럼. core dataset 이 아니다.

| Column | MySQL type |
|---|---|
| `id` | `INT` PK |
| `status_enum` | `ENUM('NEW','HOLD','DONE','CANCEL')` |

| id | row | status_enum |
|---:|---|---|
| 1 | `R_NULL` | NULL |
| 2 | `R_FIRST` | `'NEW'` |
| 3 | `R_LAST` | `'CANCEL'` |
| 4 | `R_REPRESENTATIVE` | `'DONE'` |

ENUM 의 잘못된 값 (`'INVALID'`) 은 strict mode 가 활성화된 8.0 default 에서
거부되므로 seed 에 두지 않는다.

### 5.6 `main_schema.e2e_mysql_set_types` — N/A

§1 anti-coverage 결정으로 SET 컬럼은 seed 에 포함하지 않는다. 근거: CUBRID 에
SET scalar 등가 타입이 없고, CMT 의 import 경로에서 SET 값을 포함한 batch
가 `Communication error` 로 통째로 거부됨. 매핑/import 가 안정화될 때까지
별도 회귀 케이스로 분리.

### 5.7 `main_schema.e2e_mysql_json_types` — N/A

§1 anti-coverage 결정으로 JSON 컬럼은 seed 에 포함하지 않는다 — CMT 의
`MySQLDataTypeMappingHelper` 에 `JSON` 타입 매핑이 없어 target 에서 silently
drop 되며, 매핑이 추가될 때까지 검증 의미가 없다.

### 5.8 Locator-style types — N/A

MySQL 에는 row 위치를 노출하는 pseudo locator 타입이 없다. 따라서 Oracle 의
`e2e_oracle_locator_types` 같은 extension table 을 두지 않는다.

## 6. Bug/Feature Additions

MySQL 전용 회귀 케이스를 추가할 때는 다음 규칙을 따른다.

1. 가능한 경우 기존 `e2e_*` table 을 재사용한다.
2. MySQL 전용 동작은 `e2e_mysql_*` 객체에 둔다.
3. 특정 이슈에만 좁게 묶인 케이스는 `bug_<jira-id>_...` 이름을 사용한다
   (`COMMON_SEED_CONTRACT.md` §2.2 의 Jira ID 변환 규칙 적용).
4. 일반 회귀 위험으로 승격된 케이스는 `e2e_mysql_*` 객체로 옮긴다.
5. 추후 CMT 가 MySQL 의 GENERATED column / spatial / partitioning / event /
   trigger 추출을 명확히 지원하기 시작하면 본 spec 에 5.x 또는 별도 §7 로
   흡수한다.
