# MSSQL Seed Specification

> ## ⚠ 0. 현재 상태: 보류 (Deferred)
>
> **MSSQL E2E 테스트는 현재 실행 불가능한 상태이며, 이 spec 과 시드 SQL,
> Container/Initializer 코드까지만 만들어 두고 보류한다.**
>
> ### 왜 보류했는가
>
> SQL Server 2022 컨테이너는 자기가 생성한 self-signed TLS 인증서를
> 가지고 있다. JDBC 드라이버는 이런 인증서를 기본적으로 신뢰하지 않으므로
> "이 인증서는 신뢰함" 이라는 옵션 (`trustServerCertificate=true`) 을 켜
> 줘야 연결이 된다. 그런데 CMT 의 코드 두 곳에서 이 옵션을 켤 수 있는
> 통로가 막혀 있다:
>
> 1. **`MSSQLDatabase.makeUrl`** — JDBC URL 을 만들 때
>    `jdbc:sqlserver://host:port;databaseName=<db>` 형태만 만들고 TLS
>    옵션은 붙이지 않는다.
> 2. **`ScriptCommandHandler.getConParamFromDBProperties`** — db.conf 를
>    읽을 때 host / port / dbname / user / password / driver / charset
>    / timezone 만 읽고 `user_jdbc_url` 라인은 무시한다 (script.xml 의
>    동일 attribute 는 `ConnectionsNodeHandler` 가 정상 처리하지만 db.conf
>    에서는 안 됨).
>
> 결과적으로 `RegenerateScripts` 가 첫 단계 (source DB 연결) 에서
> `PKIX path building failed` 로 끝까지 실패한다 → script.xml 을 만들
> 수 없음 → E2E 테스트 자체를 만들 수 없음.
>
> ### 다른 source DB 는 왜 됐는가
>
> CUBRID, Oracle, MySQL 8.0, MariaDB 11.4 는 client 가 default 로 self-
> signed cert 검증을 strict 하게 하지 않거나 검증 우회 옵션이 충분히
> 노출되어 있어 그대로 동작했다. **MSSQL 만 12.x 부터 보안 default 가
> strict 해진 것 + CMT 의 db.conf parser 가 이 시나리오를 고려 못 하는 것**
> 이 겹쳐 막혔다.
>
> ### 보류 해제 조건 (둘 중 하나가 풀리면 즉시 진행 가능)
>
> 1. **CMT 코드 한 줄 수정** — 다음 중 하나:
>    - `MSSQLDatabase.makeUrl` 의 URL 패턴에 `;encrypt=false;trustServerCertificate=true`
>      를 default 로 추가 (production 영향 검토 필요)
>    - `ScriptCommandHandler.getConParamFromDBProperties` 에 db.conf 의
>      `user_jdbc_url` 을 읽어 `cp.setUserJDBCURL(...)` 호출하는 5줄 추가
>      (XML path 와 parity, production 영향 0)
> 2. **cmt-console 의 JRE truststore 에 매 테스트마다 SQL Server cert 를
>    동적으로 추가하는 인프라 구축** — 가능은 하지만 작업량 ↑
>
> ### 지금까지 만들어 둔 것 (활성화 시 재사용)
>
> | 항목 | 상태 |
> |---|---|
> | 본 SPEC | 작성 완료 — 모든 anti-coverage 결정과 tentative 항목 4개 명시 |
> | `db/mssql/{init,ref_schema,main_schema}/*` 시드 SQL | 직접 띄운 MSSQL 컨테이너에 Flyway 로 적용까지 정상 동작 확인 (CMT 부분만 막힘) |
> | `MsSqlContainer.java` | mcr.microsoft.com/mssql/server:2022-latest, sqlcmd init 자동화, ARM 이미지 8분 startup timeout 포함 |
> | `MssqlDatabaseInitializer.java` | Flyway + sqlserver community plugin, defaultSchema/schemas 매핑 |
> | `Drivers.java` `DB.MSSQL` enum + jar 패턴 | 추가됨 |
> | `pom.xml` mssql-jdbc 11.2.3.jre11 + flyway-sqlserver + jar copy | 추가됨 |
> | `RegenerateScripts.java` MSSQL_TO_CUBRID / MSSQL_TO_DUMPFILE 시나리오 | 추가됨 (compile 통과, 실행은 위 차단 요인으로 실패) |
> | script.xml 픽스처 / 테스트 클래스 / 골든 | **없음** — CMT 가 source 연결을 못 해 생성 불가 |
>
> 보류 해제 시 추가로 해야 할 작업: script.xml 생성, `MssqlToCubridTest`
> + `MssqlToDumpTest` 작성, tentative 4건 (multi-schema / view / synonym /
> comment via extended property) 첫 회 결과 확정.
>
> ---

이 문서는 Microsoft SQL Server source DB 용 시드 명세다. 공통 규칙은
`../COMMON_SEED_CONTRACT.md` 를 따른다.

대상 버전은 **SQL Server 2022 Developer Edition** (`mcr.microsoft.com/mssql/server:2022-latest`).
Developer Edition 은 무료이고 Enterprise Edition 과 기능 패리티가 있어
Windows Server / Linux 양쪽에서 동등한 검증이 가능하다.

본 spec 은 MySQL/MariaDB spec 의 base 위에서 MSSQL fetcher 의 fact-confirmed
차이를 반영한다. 가장 큰 차이는 **native SYNONYM 추출이 동작**한다는 점
이다 (`MSSQLSchemaFetcher.buildSynonym` override 됨, `getAllSynonym()`
호출). MariaDB/MySQL 에서는 둘 다 부모 default `// do nothing` 사용으로
synonym 추출이 빈 결과였던 반면, **MSSQL 에서는 synonym 시나리오가 살아남
는다** (위 차단 요인이 풀린 후 첫 회 테스트로 확정).

## 1. Scope

MSSQL seed 는 다음 기능을 검증한다.

| Area | MSSQL implementation |
|---|---|
| Schema role | `main_schema` schema in `e2e_db` database (single-database, multi-schema TENTATIVE) |
| Key generation | `IDENTITY(start, increment)` column (CMT MSSQL fetcher 가 native `SEQUENCE` 추출 미구현) |
| Comment | extended property `MS_Description` (`sp_addextendedproperty`) — CMT 가 어떻게 처리하는지 첫 회 확인 (TENTATIVE) |
| **Synonym** | ✓ 포함 — `buildSynonym` override 됨, MSSQL 의 native `CREATE SYNONYM` 객체를 추출 |
| Type extension | `UNIQUEIDENTIFIER`, `DATETIMEOFFSET`, `MONEY`/`SMALLMONEY`, `XML`, `NVARCHAR(MAX)`/`NTEXT`, `SMALLDATETIME` |

다음은 anti-coverage. 별도 결정/이슈로 등록되기 전까지는 seed 에 포함하지 않는다.

| Excluded | Reason |
|---|---|
| **Cross-schema GRANT** | `MSSQLSchemaFetcher` 가 `buildGrant` 를 override 하지 않음 → 부모 `AbstractJDBCSchemaFetcher` 의 `// do nothing` 기본 구현 사용 → 빈 결과 |
| **Stored procedure / function** | `buildProcedures` 미구현. MSSQL 의 `sys.procedures` / `sys.objects` 를 읽는 코드가 없음 → routine 추출 자체가 동작 안 함 |
| **Trigger / DDL trigger** | `buildTriggers` 미구현 |
| **Native `SEQUENCE` 객체** (2012+) | `buildSequence` 미구현. `CREATE SEQUENCE` 객체는 anti-coverage; key generation 은 IDENTITY 로 대체 |
| **CLR types** (`HIERARCHYID`, `GEOGRAPHY`, `GEOMETRY`, `SQL_VARIANT`) | type-map 표에 entry 는 있으나 실제 변환은 string fallback (의미 유실). 별도 회귀 케이스로 분리 |
| **`XML` 컬럼** (TENTATIVE 안 둠 — 처음부터 anti-coverage) | CUBRID 에 등가 type 부재. type-map 에서 STRING 으로 fallback 되지만 type-aware 검증 불가 |
| **Computed column** / `PERSISTED` | CMT 매핑 미확정 |
| **Filtered index**, indexed view (materialized), columnstore index | E2E 폭증 + CMT 매핑 미확정 |
| **Temporal tables** (system-versioned), Always Encrypted, Row-Level Security, Filestream | 엔터프라이즈 기능; CMT 매핑 미확정 |
| **`ROWVERSION` / `TIMESTAMP`** column | 자동 row versioning; semantic 이 일반 timestamp 와 달라 round-trip 의미 모호 |
| `XML INDEX` / spatial index | CMT 매핑 미확정 |

> **첫 회 테스트로 확정해야 할 항목**:
> 1. **Multi-schema (`main_schema` + `ref_schema`)**: MSSQL 은 한 database
>    안에 여러 schema 를 둘 수 있고, fetcher 의 `getAllTableNames` 가
>    schema-qualified 이름을 반환하므로 **multi-schema 시나리오가 살아남을
>    가능성**이 있다. CUBRID/Oracle 의 `MAIN_SCHEMA` + `REF_SCHEMA`
>    패턴을 적용해보고 첫 회 결과로 확정. 실패하면 MySQL/MariaDB 처럼
>    단일 schema 로 후퇴.
> 2. **View import**: MySQL/MariaDB 의 view body 변환 버그 (`main_schema.X`
>    → `dba.X` 잘못 매핑) 가 MSSQL 에도 발생하는지 확인.
> 3. **Synonym round-trip**: fetcher 가 synonym 을 추출은 하는데 CUBRID
>    target 으로 옮길 때 정상 import 되는지.
> 4. **Comment via extended property**: MSSQL 의 `MS_Description` 확장
>    프로퍼티를 CMT 가 column comment 로 인식하는지.
> 5. **TIMESTAMP/DATETIMEOFFSET R_MAX 값**: CMT type-map 동작 확인.
>
> 본 spec 의 §2 multi-schema, §4.2 view, §4.3 synonym, §4.4 comment 는
> "tentative" 표시로 두고 첫 회 결과로 확정.

### 1.1 시드 작성 시 주의해야 할 MSSQL 타입 동작

- **TIMESTAMP / ROWVERSION 은 시간형이 아님.** MSSQL 의 `TIMESTAMP` 는
  사실 `ROWVERSION` 의 deprecated alias (8 byte 자동 row counter). 시간
  값이 필요하면 `DATETIME` / `DATETIME2` / `SMALLDATETIME` /
  `DATETIMEOFFSET` 을 써야 한다. `TIMESTAMP` 컬럼은 anti-coverage.
- **DATETIME 범위는 1753-01-01 ~ 9999-12-31** (3.33ms 단위, 정확도 한계).
  더 정밀하거나 더 넓은 범위가 필요하면 `DATETIME2(7)` (0001-01-01 ~
  9999-12-31, 100ns 단위) 을 사용한다. R_MIN 의 lower bound 는
  `1753-01-01` (DATETIME 한계) 로 둔다.
- **`DATETIMEOFFSET` 은 timezone-aware DATETIME2** — Oracle 의
  `TIMESTAMP WITH TIME ZONE` 과 비슷. CUBRID 에서는 `datetimetz` 와
  매핑되는지 첫 회 확인.
- **`NVARCHAR(MAX)` / `VARCHAR(MAX)`** 는 LOB 처리 (~2 GB 한계). seed
  에서는 4 KB 정도로 제한 (MySQL spec §1.1 의 `Communication error`
  교훈 적용).
- **`NCHAR` / `NVARCHAR` 는 character-counted (UCS-2)**. MySQL/MariaDB
  처럼 byte-counted 가 아니라 글자 단위. 그러나 CMT 가 CUBRID 의
  byte-counted CHAR/VARCHAR 로 옮길 때 동일한 byte budget mismatch 가
  발생할 수 있어, R_MAX 의 `nchar_col` 도 ASCII 만 사용한다 (보수적).
- **`CHAR(n)` / `VARCHAR(n)` 은 byte-counted** (default code page 기준).
  multi-byte 글자가 들어가면 R_MAX 가 깨진다 — 동일 우회 적용.
- **MSSQL collation** 은 case-insensitive accent-sensitive
  (`SQL_Latin1_General_CP1_CI_AS`) 가 default 지만 식별자는
  case-preserving. 본 spec 은 lowercase 식별자로 통일 (`main_schema`,
  `e2e_customer`).
- **`IDENTITY(start, increment)` 컬럼**에 explicit 값을 INSERT 하려면
  `SET IDENTITY_INSERT <table> ON;` 을 먼저 호출해야 한다. seed 의
  `V99__data.sql` 에서 1..4 explicit IDs 를 넣을 때 필요. INSERT 후
  `SET IDENTITY_INSERT <table> OFF;` 로 복귀.
- **Comment 은 extended property 로**: MSSQL 은 standalone `COMMENT ON`
  도 inline `COMMENT 'text'` 도 지원하지 않음. `EXEC
  sp_addextendedproperty @name=N'MS_Description', @value=N'...', ...`
  형식 사용. CMT 가 이를 어떻게 처리하는지 첫 회 확인.
- **MSSQL Docker image platform**: `mcr.microsoft.com/mssql/server` 는
  공식 이미지지만 **Apple Silicon (ARM64) 미지원**. ARM 환경에서는
  emulation 으로 동작하지만 매우 느림 (3-5 분 시작). 대안으로
  Microsoft 가 공개한 `mcr.microsoft.com/azure-sql-edge` 가 ARM
  네이티브이지만 일부 기능이 제한된다. 본 spec 은 정확도 우선으로
  `mcr.microsoft.com/mssql/server:2022-latest` 를 사용한다.

## 2. Schema Setup — *tentative (multi-schema vs single-schema)*

| Role | MSSQL identifier (TENTATIVE) |
|---|---|
| `MAIN_SCHEMA` (role) | schema `main_schema` in database `e2e_db` |
| `REF_SCHEMA`  (role) | schema `ref_schema` in database `e2e_db` |

MSSQL 은 한 database 안에 여러 schema 를 둘 수 있고, `MSSQLSchemaFetcher.
getAllTableNames` 가 schema-qualified 이름 (`schema.table`) 을 반환한다.
이 구조 덕분에 CUBRID/Oracle 처럼 MAIN_SCHEMA + REF_SCHEMA 시드를 하나의
database 안에 두 schema 로 구현해 볼 수 있다 — 첫 회 테스트로 fetcher 가
양쪽 schema 의 객체를 모두 추출하는지 확인한다.

```sql
-- init/00_prepare_database.sql (개념 예시; 실제 DDL 은 SQL Server 문법)
CREATE LOGIN main_user WITH PASSWORD = N'CmtMain#2026';
CREATE LOGIN ref_user  WITH PASSWORD = N'CmtRef#2026';

CREATE DATABASE e2e_db;
GO

USE e2e_db;
GO

CREATE USER main_user FOR LOGIN main_user;
CREATE USER ref_user  FOR LOGIN ref_user;

CREATE SCHEMA main_schema AUTHORIZATION main_user;
CREATE SCHEMA ref_schema  AUTHORIZATION ref_user;

-- main_user can SELECT from ref_schema.e2e_ref_audit through synonym
GRANT SELECT, INSERT, UPDATE, DELETE ON SCHEMA::ref_schema TO main_user;
```

> MSSQL password 정책 (Windows Authentication 미사용 시): 8자 이상 + 대문자
> + 소문자 + 숫자 + 특수문자 중 3가지. `CmtMain#2026` 형식.
>
> CMT MSSQL fetcher 가 cross-schema GRANT 를 추출하지 않으므로 (§1
> anti-coverage) cross-schema 검증 surface 는 SYNONYM 만 남는다. seed
> 자체에서는 GRANT 를 부여해 main_user 가 ref_schema.e2e_ref_audit 을
> 읽을 수 있게 하지만, 이 GRANT 자체는 dump 결과에 포함되지 않는다.

## 3. Business Graph

### 3.1 `main_schema.e2e_customer`

| Column | MSSQL type | Required data |
|---|---|---|
| `customer_id` | `INT IDENTITY(5, 1)` | PK; IDENTITY 시작값 5 (다음 자동발급 값) |
| `customer_code` | `CHAR(4)` | unique, values `C001`.. |
| `customer_name` | `NVARCHAR(100)` | non-null |
| `customer_alias` | `NVARCHAR(60)` | nullable, supports unicode |
| `status` | `CHAR(1)` | `A`, `I`, `D` |
| `credit_limit` | `DECIMAL(15, 2)` | NULL, 0, normal, high |
| `created_on` | `DATETIME2(3)` | fixed datetime |
| `updated_on` | `DATETIME2(3)` | nullable |

Required constraints:

```sql
CONSTRAINT pk_e2e_customer        PRIMARY KEY (customer_id)
CONSTRAINT uk_e2e_customer_code   UNIQUE      (customer_code)
CONSTRAINT ck_e2e_customer_status CHECK       (status IN ('A', 'I', 'D'))
```

Required rows (id 1..4 explicit values, IDENTITY counter advances to 5
automatically. `SET IDENTITY_INSERT main_schema.e2e_customer ON` 필수):

| customer_id | customer_code | customer_name | customer_alias | status | credit_limit | created_on |
|---:|---|---|---|---|---:|---|
| 1 | `C001` | `ALPHA CUSTOMER` | `Alpha Alias` | `A` | 12500.75 | `2024-01-10` |
| 2 | `C002` | `BETA CUSTOMER` | NULL | `I` | NULL | `2024-02-29` |
| 3 | `C003` | `한국 고객` | `별칭-한글` | `D` | 0 | `2024-03-15` |
| 4 | `C004` | `HIGH LIMIT CUSTOMER` | `High` | `A` | 9999999999999.99 | `2024-04-20` |

### 3.2 `main_schema.e2e_order`

| Column | MSSQL type | Required data |
|---|---|---|
| `order_id` | `INT IDENTITY(5, 1)` | PK |
| `customer_id` | `INT` | FK to customer |
| `order_no` | `NVARCHAR(30)` | business key |
| `order_status` | `NVARCHAR(20)` | `NEW`, `HOLD`, `DONE`, `CANCEL` |
| `total_amount` | `DECIMAL(18, 2)` | nullable amount |
| `ordered_at` | `DATETIME2(3)` | fixed datetime |
| `settled_at` | `DATETIME2(3)` | nullable |
| `source_comment` | `NVARCHAR(200)` | blank/unicode/punctuation |

Required constraints/indexes:

```sql
CONSTRAINT pk_e2e_order          PRIMARY KEY (order_id)
CONSTRAINT fk_e2e_order_customer FOREIGN KEY (customer_id) REFERENCES main_schema.e2e_customer(customer_id)
CONSTRAINT ck_e2e_order_status   CHECK (order_status IN (N'NEW', N'HOLD', N'DONE', N'CANCEL'))

CREATE INDEX idx_e2e_order_customer    ON main_schema.e2e_order(customer_id);
CREATE INDEX idxd_e2e_order_ordered_at ON main_schema.e2e_order(ordered_at DESC);
-- functional index: MSSQL needs a computed column + index pattern; computed
-- columns are anti-coverage (§1), so idxf_* is omitted. Same decision as
-- MariaDB. Plain B-tree only.
```

Required rows (4 rows, business / status 분포 동일).

### 3.3 `main_schema.e2e_order_line`

Composite PK 사용.

| Column | MSSQL type |
|---|---|
| `order_id` | `INT` |
| `line_no` | `SMALLINT` |
| `sku` | `NVARCHAR(30)` |
| `qty` | `INT` |
| `unit_price` | `DECIMAL(15, 2)` |
| `line_note` | `NVARCHAR(200)` |

```sql
CONSTRAINT pk_e2e_order_line       PRIMARY KEY (order_id, line_no)
CONSTRAINT fk_e2e_order_line_order FOREIGN KEY (order_id) REFERENCES main_schema.e2e_order(order_id)
CONSTRAINT ck_e2e_order_line_qty   CHECK (qty > 0)
```

### 3.4 `main_schema.e2e_employee`

Self-reference FK.

| Column | MSSQL type |
|---|---|
| `employee_id` | `INT` |
| `manager_id` | `INT` |
| `emp_name` | `NVARCHAR(100)` |
| `hired_on` | `DATE` |

### 3.5 `ref_schema.e2e_ref_audit` — *tentative*

§2 multi-schema 가 살아남으면 이 객체를 둔다.

| Column | MSSQL type |
|---|---|
| `audit_id` | `INT` |
| `audit_message` | `NVARCHAR(200)` |
| `created_at` | `DATETIME2(3)` |

multi-schema 가 첫 회 테스트에서 깨지면 `e2e_ref_audit` 도 anti-coverage
로 옮긴다.

## 4. Object Samples

### 4.1 IDENTITY (sequence 등가물)

§3.1 / §3.2 의 `INT IDENTITY(5, 1)` 컬럼이 sequence 검증 surface.
MSSQL native `CREATE SEQUENCE` 객체는 §1 anti-coverage.

### 4.2 View — *tentative*

```sql
CREATE VIEW main_schema.e2e_order_summary_v AS
SELECT o.order_id,
       o.order_no,
       c.customer_code,
       c.customer_name,
       o.order_status,
       o.total_amount,
       o.ordered_at
FROM   main_schema.e2e_order    AS o
INNER JOIN main_schema.e2e_customer AS c
       ON c.customer_id = o.customer_id;
```

> 첫 회 테스트로 확인할 항목: CMT 가 view body 의 `main_schema.X` 참조를
> CUBRID target 의 `dba.X` 로 잘못 매핑하는 MySQL/MariaDB 의 그 버그가
> MSSQL fetcher 의 `buildViewDetails` 경로에도 발생하는지. MSSQL 은 schema
> 가 user 와 분리된 namespace 라 fetcher 가 다르게 처리할 가능성도 있음.

### 4.3 Synonym — *tentative*

```sql
CREATE SYNONYM main_schema.e2e_ref_audit_syn FOR ref_schema.e2e_ref_audit;
```

> **MSSQL seed 의 가장 큰 차이점**. MariaDB/MySQL 에서는 fetcher 가 빈
> 결과로 anti-coverage 였지만, MSSQL 은 `MSSQLSchemaFetcher.buildSynonym`
> 이 `getAllSynonym()` 을 호출해 synonym 을 정상 추출한다. 첫 회 테스트
> 로 round-trip (CUBRID 측에 SYNONYM 객체로 정상 import 되는지) 을 확인
> 하고, 성공하면 SYNONYM 을 core 에 포함, 실패하면 anti-coverage 로
> 옮긴다.
>
> Multi-schema (§2) 가 깨지면 ref_schema 자체가 없으므로 synonym 도
> 자동으로 anti-coverage.

### 4.4 Comment (extended property) — *tentative*

```sql
EXEC sp_addextendedproperty
    @name      = N'MS_Description',
    @value     = N'E2E master customer table',
    @level0type= N'SCHEMA', @level0name= N'main_schema',
    @level1type= N'TABLE',  @level1name= N'e2e_customer';

EXEC sp_addextendedproperty
    @name      = N'MS_Description',
    @value     = N'business key',
    @level0type= N'SCHEMA', @level0name= N'main_schema',
    @level1type= N'TABLE',  @level1name= N'e2e_customer',
    @level2type= N'COLUMN', @level2name= N'customer_code';
```

> MSSQL 은 standalone `COMMENT ON` / inline `COMMENT 'text'` 모두 미지원.
> 메타데이터 코멘트는 `MS_Description` extended property 로만 표현된다.
> CMT MSSQL fetcher 가 이 property 를 읽어 column.comment 로 채우는지
> 첫 회 테스트로 확인. 동작하면 Required comments 8건을 적용, 동작
> 안 하면 anti-coverage.

### 4.5 Stored Procedure / Function — N/A

§1 anti-coverage. `MSSQLSchemaFetcher.buildProcedures` 미구현으로 routine
추출 자체가 동작하지 않는다.

## 5. Type Test Tables

### 5.1 `main_schema.e2e_text_types`

| Column | MSSQL type |
|---|---|
| `id` | `INT` PK |
| `char_col` | `CHAR(3)` |
| `varchar_col` | `VARCHAR(10)` |
| `nchar_col` | `NCHAR(3)` |
| `nvarchar_col` | `NVARCHAR(40)` |
| `text_col` | `VARCHAR(MAX)` (TEXT 는 deprecated; MAX 사용 권장) |
| `ntext_col` | `NVARCHAR(MAX)` |

`MSSQLDataTypeMappingHelper` 가 NCHAR/NVARCHAR 를 CUBRID 로 매핑할 때
byte-vs-character budget 동일 함정. R_MAX 의 char/varchar/nchar 는 ASCII
만 사용하고 multi-byte 는 nvarchar / VARCHAR(MAX) / NVARCHAR(MAX) 로.

### 5.2 `main_schema.e2e_numeric_types`

| Column | MSSQL type |
|---|---|
| `id` | `INT` PK |
| `tinyint_col` | `TINYINT` (UNSIGNED 0-255 — MSSQL 만 unsigned tinyint) |
| `smallint_col` | `SMALLINT` |
| `int_col` | `INT` |
| `bigint_col` | `BIGINT` |
| `decimal_col` | `DECIMAL(20, 6)` |
| `decimal_p0_col` | `DECIMAL(38, 0)` |
| `float_col` | `FLOAT(24)` (= REAL) |
| `double_col` | `FLOAT(53)` (= DOUBLE) |
| `money_col` | `MONEY` (MSSQL 고유) |
| `smallmoney_col` | `SMALLMONEY` (MSSQL 고유) |

> MSSQL `TINYINT` 는 unsigned (0~255). MySQL `TINYINT` 는 signed
> (-128~127). 같은 키워드인데 의미가 다름 — R_MIN/R_MAX 값 다름.

### 5.3 `main_schema.e2e_temporal_types`

| Column | MSSQL type |
|---|---|
| `id` | `INT` PK |
| `date_col` | `DATE` |
| `time_col` | `TIME(7)` (100ns 정밀도) |
| `datetime_col` | `DATETIME` (1753..9999, 3.33ms) |
| `datetime2_col` | `DATETIME2(7)` (0001..9999, 100ns) |
| `smalldatetime_col` | `SMALLDATETIME` (1900..2079, 1분) |
| `datetimeoffset_col` | `DATETIMEOFFSET(7)` (timezone-aware) |

> R_MIN 은 `DATETIME` 의 lower bound 1753-01-01 로 둔다 (DATETIME2 는
> 0001-01-01 까지 가능하지만 같은 row 에서 통일).

### 5.4 `main_schema.e2e_binary_types`

| Column | MSSQL type |
|---|---|
| `id` | `INT` PK |
| `binary_col` | `BINARY(8)` |
| `varbinary_col` | `VARBINARY(64)` |
| `varbinary_max_col` | `VARBINARY(MAX)` (4 KB 제한) |
| `image_col` | `IMAGE` (deprecated 지만 type-map 에 있음) |
| `bit_col` | `BIT` (boolean) |

> `BIT` 는 MySQL/MariaDB 의 BIT(n) 과 다르게 단일 boolean (NULL/0/1).
> CUBRID 매핑은 SHORT 또는 INT.

### 5.5 `main_schema.e2e_mssql_uniqueidentifier_types` (MSSQL extension)

`UNIQUEIDENTIFIER` (GUID) — MSSQL 고유.

| Column | MSSQL type |
|---|---|
| `id` | `INT` PK |
| `uid_col` | `UNIQUEIDENTIFIER` |

| id | row | uid_col |
|---:|---|---|
| 1 | `R_NULL` | NULL |
| 2 | `R_FIXED` | `'00000000-0000-0000-0000-000000000000'` |
| 3 | `R_NEWID` | `NEWID()` |
| 4 | `R_REPRESENTATIVE` | `'a1b2c3d4-e5f6-7890-1234-567890abcdef'` |

> CMT type-map 에서 `uniqueidentifier` 는 string fallback. 36자 char 로
> 보존되는지 첫 회 확인.

### 5.6 `main_schema.e2e_mssql_xml_types` — N/A

§1 anti-coverage. CUBRID 에 XML scalar type 부재. type-map 의 fallback 은
string 이지만 type-aware 검증 불가.

### 5.7 `main_schema.e2e_mssql_money_types` — *folded into §5.2*

`MONEY` / `SMALLMONEY` 는 §5.2 `e2e_numeric_types` 의 `money_col` /
`smallmoney_col` 에 통합. 별도 extension table 안 둠.

### 5.8 Locator-style types — N/A

MSSQL 도 row 위치 pseudo type 부재. `ROWVERSION`/`TIMESTAMP` 는 row
versioning 용으로 의미가 다르고 anti-coverage.

## 6. Bug/Feature Additions

MSSQL 전용 회귀 케이스 추가 시:

1. 가능한 경우 기존 `e2e_*` table 을 재사용.
2. MSSQL 전용 동작은 `e2e_mssql_*` 객체에 둔다.
3. 특정 이슈에만 좁게 묶인 케이스는 `bug_<jira-id>_...` 이름을 사용
   (`COMMON_SEED_CONTRACT.md` §2.2 의 Jira ID 변환 규칙 적용).
4. 일반 회귀 위험으로 승격된 케이스는 `e2e_mssql_*` 객체로 옮긴다.
5. 추후 CMT MSSQL fetcher 가 stored procedure / function / trigger /
   sequence / extended property comment / temporal table / spatial
   추출을 명확히 지원하기 시작하면 본 spec 에 5.x 또는 별도 §7 로
   흡수한다.
