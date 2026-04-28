# MariaDB Seed Specification

> ## ⚠ 0. 현재 상태: 보류 (Deferred)
>
> **MariaDB E2E 테스트는 현재 실행 대상에서 제외되어 있다.** 시드 SQL,
> Container/Initializer, script.xml 픽스처, 테스트 클래스까지 모두 만들어
> 두었으나 `@Disabled` 처리 상태.
>
> ### 왜 보류했는가
>
> CMT MariaDB plugin 은 MySQL plugin 의 모든 결함을 그대로 상속받는다
> (`MariaDBSchemaFetcher` 가 `MySQLSchemaFetcher` 를 base 로 함).
> 따라서 `mysql/SEED_SPEC.md` §0 의 결함이 모두 적용된다 — connection
> user 를 schema 로 매핑하는 부모 default 미 override, view body
> translation 버그, JSON 매핑 부재, SET 변환 안 됨 등.
>
> MariaDB 만의 추가 결함:
>
> - MariaDB 11.4 의 `mysql.proc` 테이블이 정상 존재함에도 view body
>   변환 결함은 동일하게 발생 → MariaDB 가 더 나은 fetcher 를 갖지 못함
> - Functional index (`CREATE INDEX ... ON tbl(UPPER(col))`) syntax
>   거부 (`§1 anti-coverage` 참고)
>
> 결론: MySQL/MariaDB 는 본질적으로 **CMT 의 user-as-schema 가정 자체를
> 패치하지 않으면 production-grade 마이그레이션이 어려움**. MySQL fix 와
> 동일 작업으로 같이 풀린다.
>
> ### 보류 해제 조건
>
> MySQL SPEC §0 의 해제 조건과 동일. MariaDB 는 MySQL fix 가 포팅되는
> 시점에 같이 풀림. 추가로:
>
> - MariaDB 만의 functional index 처리 결정 (anti-coverage 유지 또는
>   CMT 패치)
>
> ### 지금까지 만들어 둔 것 (보류 해제 시 재사용)
>
> | 항목 | 상태 |
> |---|---|
> | 본 SPEC | 작성 완료 |
> | `db/mariadb/{init,main_schema}/*` 시드 SQL | 동작 확인됨 |
> | `MariaDbContainer.java`, `MariadbDatabaseInitializer.java` | 동작 |
> | `Drivers.MARIADB` + `pom.xml` (`mariadb-java-client`) | 동작 |
> | `RegenerateScripts` `MARIADB_TO_CUBRID` / `MARIADB_TO_DUMPFILE` | 동작 |
> | `script.xml` 픽스처 + dump goldens | 보존 |
> | `MariadbToCubridTest` / `MariadbToDumpTest` | `@Disabled("DEFERRED — see SEED_SPEC §0")` 상태 |
>
> ---

이 문서는 MariaDB source DB 용 시드 명세다. 공통 규칙은
`../COMMON_SEED_CONTRACT.md` 를 따른다.

대상 버전은 **MariaDB 11.4 (latest stable)**. 10.6/10.11 LTS 호환을
명시적으로 검증해야 할 때는 `bug_<jira-id>_*` 으로 분리한다.

본 spec 은 MySQL spec (`../mysql/SEED_SPEC.md`) 을 base 로 하되,
CMT MariaDB plugin 에서 fact-confirmed 된 차이를 반영한다 — 가장 큰
차이는 **stored routines 가 살아남는다**는 점이다 (MariaDB 가
`mysql.proc` 를 유지하므로 `MariaDBSchemaFetcher.buildProcedures` 가
정상 동작).

## 1. Scope

MariaDB seed 는 다음 기능을 검증한다.

| Area | MariaDB implementation |
|---|---|
| Schema role | `main_schema` database (single-database; `ref_schema` 는 anti-coverage) |
| Key generation | `AUTO_INCREMENT` 컬럼 (CMT MariaDB fetcher 가 native `SEQUENCE` 추출 미지원) |
| Comment | inline `COMMENT 'text'` (table/column 모두) |
| **Routine** | **stored procedure / function (DEFINER 모드) — MySQL 과 다른 핵심 차이** |
| Type extension | `ENUM`, `BIT`, `YEAR`, `LONGTEXT`/`LONGBLOB` (JSON, SET 은 anti-coverage 후보 — 첫 회 테스트로 확정) |

다음은 anti-coverage. 별도 결정/이슈로 등록되기 전까지는 seed 에 포함하지 않는다.

| Excluded | Reason |
|---|---|
| **Cross-schema (`ref_schema`, GRANT, SYNONYM)** | `MariaDBSchemaFetcher` 가 `buildGrant` / `buildSynonym` 를 override 하지 않음 → 부모 `AbstractJDBCSchemaFetcher` 의 `// do nothing` 기본 구현 사용 → 둘 다 빈 결과 추출. 또한 connection user 단일 schema collapse 도 MySQL 과 동일 |
| **View** | 첫 회 테스트에서 확정: MySQL 과 동일한 view body 변환 버그가 MariaDB 에도 발생. CMT 가 `main_schema.<table>` 참조를 target 의 `dba.<table>` 로 잘못 매핑 → `Unknown class dba.<table>` 로 view import 실패. CMT 패치 전까지 anti-coverage |
| **`SET` 컬럼** | 첫 회 테스트에서 확정: MySQL 과 동일하게 CMT batch import 에서 `Communication error` 로 batch 거부. anti-coverage |
| Native `SEQUENCE` 객체 (10.3+) | MariaDB 는 `CREATE SEQUENCE` 를 지원하지만 `MariaDBSchemaFetcher` 가 `buildSequence` 를 override 하지 않음 → 추출 불가. 검증할 fetcher 경로가 없으므로 seed 에 두지 않음 |
| **Functional / expression index** (`idxf_*`) | MariaDB 11.x 는 MySQL 8.0.13+ 의 `CREATE INDEX ... ON tbl((expr))` 두 겹 괄호 구문을 거부 (syntax error). MariaDB 가 권장하는 우회는 virtual `GENERATED` column + plain index 인데 GENERATED column 자체가 anti-coverage 이므로 MariaDB seed 에서는 functional index 자체를 두지 않음. Oracle/CUBRID/MySQL 의 `idxf_e2e_order_upper_status` 가 MariaDB 에는 없는 이유 |
| System-versioned tables (temporal) | MariaDB 고유 기능. CMT 매핑 미확정 |
| Spatial / `GEOMETRY` | CMT MariaDB fetcher 매핑 미확정 |
| `ROW_FORMAT`, partitioning, `ENGINE=` 변형 | E2E 폭증 |
| `GENERATED` / `VIRTUAL` / `PERSISTENT` 컬럼 | CMT 매핑 미확정 |
| Trigger / Event scheduler | CUBRID/Oracle/MySQL seed 와 같은 비교 기준 유지 |
| `ZEROFILL`, `UNSIGNED` 변형 | CMT 매핑이 sign-aware 가 아니어서 회귀 위험 모호 |

> **첫 회 테스트로 확정된 결과** (`MariadbToCubridTest` 1차 실행 기반):
> - **View**: 실패 → anti-coverage. MySQL 과 동일한 CMT view body 변환 버그.
> - **SET**: 실패 → anti-coverage. MySQL 과 동일한 batch import `Communication error`.
> - **JSON**: **성공** → core 에 포함. MariaDB JSON 이 `LONGTEXT` alias 라
>   CMT 가 LONGTEXT 로 silent pass-through. **MySQL spec 과 다른 결과**.

### 1.1 시드 작성 시 주의해야 할 MariaDB 타입 동작

MySQL spec §1.1 의 모든 항목이 MariaDB 에도 동일하게 적용된다. 추가/차이점만 정리.

- **TIMESTAMP 유효 범위는 1970-01-01 00:00:01 UTC ~ 2038-01-19 03:14:07 UTC.**
  MySQL 과 동일.
- **MariaDB `CHAR(n)` / `VARCHAR(n)` 은 character-counted** (utf8mb4 기준).
  MySQL 과 동일이지만 **CMT 매핑은 byte budget 그대로 옮기는** 동일 quirk
  → R_MAX/R_UNICODE 의 `char_col` / `varchar_col` 는 ASCII/NULL 로 둔다.
- **`''` 는 빈 문자열 보존** (MySQL 과 동일).
- **DATETIME(6) / TIMESTAMP(6) 6-digit precision**. CUBRID `DATETIME` 은
  3-digit 이라 truncate 가능 — R_BOUNDARY 에서 6-digit 값으로 검증.
- **`AUTO_INCREMENT` 컬럼에 explicit 값을 INSERT** 하면 internal counter
  는 입력값보다 작은 경우에만 그 값+1 로 갱신 (MySQL 과 동일).
- **MariaDB-specific collation default**: MariaDB 10.10+ 은 default 를
  `utf8mb4_uca1400_ai_ci` 로 도입했지만 **하위호환성 정책**으로 신규
  database 생성 시에는 `utf8mb4_general_ci` 가 server 기본인 경우가 많다.
  본 spec 은 이식성을 위해 `CREATE DATABASE ... COLLATE utf8mb4_general_ci`
  로 명시한다 — MySQL spec 의 `utf8mb4_0900_ai_ci` 와 다른 점.
- **`mysql.proc` 시스템 테이블 존재**: MariaDB 는 MySQL 5.7 시절의
  `mysql.proc` 테이블을 그대로 유지한다 (MySQL 8.0 에서 제거된 것과 대조).
  이 덕분에 `MariaDBSchemaFetcher.buildProcedures` 가 정상 동작하고
  stored function/procedure 가 시드에 들어갈 수 있다.
- **MariaDB-specific server variable `log_bin_trust_function_creators`**:
  MySQL 과 동일하게 binary logging 활성화 시 SUPER 권한 없는 user 의
  `CREATE FUNCTION` 을 거부한다. MariaDB 컨테이너에도 같은 기동 인자
  `--log-bin-trust-function-creators=ON` 을 줘야 한다.

## 2. Schema Setup

| Role | MariaDB identifier |
|---|---|
| `MAIN_SCHEMA` (role) | `main_schema` (database) |
| `REF_SCHEMA`  (role) | **N/A** — §1 anti-coverage |

`init/00_prepare_database.sql` 에서 root 권한으로 `main_schema` database
와 `main_user` 를 만든다.

```sql
-- init/00_prepare_database.sql (개념 예시)
CREATE DATABASE IF NOT EXISTS main_schema
    CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

CREATE USER IF NOT EXISTS 'main_user'@'%' IDENTIFIED BY 'cmt';

GRANT ALL PRIVILEGES ON main_schema.* TO 'main_user'@'%';
```

> MariaDB 의 `user@host` 모델은 MySQL 과 같다. host 는 `'%'` 로 통일.

## 3. Business Graph

§3.1 ~ §3.5 는 MySQL spec 과 **완전히 동일**하다 (테이블 이름, 컬럼 타입,
required row 구성). 차이가 없으므로 자세한 표는 `../mysql/SEED_SPEC.md`
§3 을 참조한다. 요약:

| Table | row 수 | 주요 속성 |
|---|---:|---|
| `main_schema.e2e_customer` | 4 | INT AUTO_INCREMENT (start=5), uk_e2e_customer_code, ck_e2e_customer_status, comments |
| `main_schema.e2e_order` | 4 | FK to e2e_customer, ck_e2e_order_status, idx/idxd/idxf indexes |
| `main_schema.e2e_order_line` | 4 | composite PK (order_id, line_no), FK, CHECK qty>0 |
| `main_schema.e2e_employee` | 3 | self-FK |

`ref_schema.e2e_ref_audit` 은 §1 anti-coverage 로 제외.

## 4. Object Samples

### 4.1 Auto-increment

MySQL spec §4.1 과 동일. `e2e_customer` / `e2e_order` 의 `CREATE TABLE`
에 `AUTO_INCREMENT=5` 옵션. CMT MariaDB fetcher 가 이를 어떻게 표현하는지
첫 회 테스트로 확인한다.

### 4.2 View — N/A

§1 anti-coverage 결정으로 view 는 seed 에 포함하지 않는다. 첫 회 테스트로
MySQL 과 동일한 CMT view body 변환 버그가 MariaDB 에도 발생한다는 사실이
확인됨: MariaDB 카탈로그가 view body 를 `main_schema.<table>` 로 자동
확장하고, CMT 가 이를 CUBRID target 의 `dba.<table>` 로 잘못 매핑 →
`Unknown class dba.<table>` 으로 view import 가 항상 실패한다.

### 4.3 Synonym — N/A

두 가지 이유로 N/A: (1) MariaDB 는 native synonym 객체가 없음. (2)
`MariaDBSchemaFetcher.buildSynonym()` 는 부모 클래스의 `// do nothing`
기본 구현을 그대로 사용해 어떤 synonym 도 추출하지 않는다.

### 4.4 Comment

MySQL spec §4.4 와 동일. inline `COMMENT 'text'` (column) /
`COMMENT='text'` (table) 형식. View 가 §4.2 에서 살아남는지에 따라
view-level/view-column comment 행 추가 여부가 결정된다 (현재는 8건 기준
— Oracle/CUBRID 13건 대비 view 미포함).

Required comments (§3.1 ~ §3.4 객체에 한정):

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

### 4.5 Stored Procedure / Function

**MariaDB seed 의 가장 큰 차이점.** MySQL spec 은 §4.5 가 N/A 이지만
MariaDB 는 살린다. 근거: MariaDB 가 MySQL 5.7 시절의 `mysql.proc` 시스템
테이블을 유지하고 있고 `MariaDBSchemaFetcher.buildProcedures` 가 그
테이블을 직접 읽도록 구현되어 있다 (`SELECT name FROM mysql.proc WHERE
db=? AND type=? ORDER BY name`).

함수 1개와 프로시저 1개만 만든다. 둘 다 `DETERMINISTIC` /
`READS SQL DATA` characteristic 명시 — binlog 환경에서도 동작.

Function:

```sql
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
DELIMITER ;
```

Procedure:

```sql
DELIMITER //
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
```

> 컨테이너 기동 시 `--log-bin-trust-function-creators=ON` 인자 필요. MySQL
> 과 동일한 이유 (binary logging + SUPER 부재 → ER_BINLOG_UNSAFE_ROUTINE).

## 5. Type Test Tables

### 5.1 `main_schema.e2e_text_types`

MySQL spec §5.1 과 동일.

| Column | MariaDB type |
|---|---|
| `id` | `INT` PK |
| `char_col` | `CHAR(3)` |
| `varchar_col` | `VARCHAR(10)` |
| `text_col` | `TEXT` |
| `mediumtext_col` | `MEDIUMTEXT` |
| `longtext_col` | `LONGTEXT` |

R_MAX 의 `char_col` 은 ASCII (`'XYZ'`), R_UNICODE 의 `char_col` /
`varchar_col` 은 NULL — CMT 의 byte-budget mapping 한계 회피. R_MAX
`mediumtext_col` / `longtext_col` 은 4 KB.

### 5.2 `main_schema.e2e_numeric_types`

MySQL spec §5.2 와 동일. `tinyint_col`, `smallint_col`, `mediumint_col`,
`int_col`, `bigint_col`, `decimal_col`, `decimal_p0_col`, `float_col`,
`double_col`. 6 row (R_NULL/R_EMPTY/R_MIN/R_MAX/R_BOUNDARY/R_REPRESENTATIVE).

### 5.3 `main_schema.e2e_temporal_types`

MySQL spec §5.3 과 동일. `date_col`, `time_col(6)`, `datetime_col(6)`,
`timestamp_col(6) NULL DEFAULT NULL`, `year_col`. 5 row.

### 5.4 `main_schema.e2e_binary_types`

MySQL spec §5.4 와 동일. `binary_col(8)`, `varbinary_col(64)`, `bit_col(64)`,
`blob_col`, `longblob_col`. 5 row.

### 5.5 `main_schema.e2e_mariadb_enum_types` (MariaDB extension)

MySQL spec §5.5 와 구조 동일, 객체명만 `e2e_mariadb_*` prefix 로 변경.

| Column | MariaDB type |
|---|---|
| `id` | `INT` PK |
| `status_enum` | `ENUM('NEW','HOLD','DONE','CANCEL')` |

| id | row | status_enum |
|---:|---|---|
| 1 | `R_NULL` | NULL |
| 2 | `R_FIRST` | `'NEW'` |
| 3 | `R_LAST` | `'CANCEL'` |
| 4 | `R_REPRESENTATIVE` | `'DONE'` |

### 5.6 `main_schema.e2e_mariadb_set_types` — N/A

§1 anti-coverage. 첫 회 테스트에서 MySQL 과 동일한 batch import
`Communication error` 가 확인됨. CUBRID 등가 SET scalar 타입 부재 + CMT
batch 거부 → 매핑/import 가 안정화될 때까지 별도 회귀 케이스로 분리.

### 5.7 `main_schema.e2e_mariadb_json_types`

**MariaDB seed 가 MySQL 과 갈리는 두 번째 핵심 차이** (첫 번째는 §4.5
routines). MariaDB 의 `JSON` 은 `LONGTEXT` alias 로 구현되어
`information_schema.columns` 가 `data_type=longtext` 로 보고하므로, CMT
의 MariaDBDataTypeMappingHelper 가 JSON 키워드를 모르더라도 LONGTEXT 로
silent pass-through 된다 — MySQL 의 typed JSON 컬럼이 silently drop 되는
것과 정반대 결과. 첫 회 테스트에서 5 row 모두 정상 마이그레이션 확인.

| Column | MariaDB type |
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

> CUBRID 측에서는 LONGTEXT 매핑이 `VARCHAR(1073741823)` (1G) 으로 들어간다.
> 즉 MariaDB JSON → CUBRID 입장에서는 type 이 JSON 이 아니라 string 으로
> 보존된다. 의미 보존 (parse-able JSON 텍스트) 은 통과하지만 type-aware
> 검증은 못 한다.

### 5.8 Locator-style types — N/A

MariaDB 에는 row 위치를 노출하는 pseudo locator 타입이 없다. Oracle 의
`e2e_oracle_locator_types` 같은 extension table 을 두지 않는다.

## 6. Bug/Feature Additions

MariaDB 전용 회귀 케이스를 추가할 때는 다음 규칙을 따른다.

1. 가능한 경우 기존 `e2e_*` table 을 재사용한다.
2. MariaDB 전용 동작은 `e2e_mariadb_*` 객체에 둔다 (예: native SEQUENCE,
   system-versioned table 검증이 가능해질 때).
3. 특정 이슈에만 좁게 묶인 케이스는 `bug_<jira-id>_...` 이름을 사용한다
   (`COMMON_SEED_CONTRACT.md` §2.2 의 Jira ID 변환 규칙 적용).
4. 일반 회귀 위험으로 승격된 케이스는 `e2e_mariadb_*` 객체로 옮긴다.
5. 추후 CMT 가 MariaDB 의 GENERATED column / spatial / partitioning /
   trigger / event / system versioning / native SEQUENCE 추출을 명확히
   지원하기 시작하면 본 spec 에 5.x 또는 별도 §7 로 흡수한다.
