# Informix Seed Specification

> ## ⚠ 0. 현재 상태: 부분 동작 (Partial)
>
> Informix E2E 인프라 (Container, Initializer, Drivers, pom.xml,
> RegenerateScripts, 시드 SQL) 는 모두 동작한다. CMT 의 마이그레이션도
> `MIGRATION RESULT: SUCCESS` 로 끝난다. 그러나 **소스 데이터의 record
> export 가 0 건**이다.
>
> ### 동작하는 부분
>
> - Container 부팅, OS user 생성 (sudo useradd), DB 초기화 (dbaccess)
> - Flyway migration 5 단계 (V1 ~ V99) 모두 main_user 로 정상 실행
> - CMT migration.sh script 가 source 메타데이터를 정상 추출 → script.xml 생성
> - CMT migration.sh start 가 다음을 정상 마이그레이션:
>   - **table 10**, **PK 10**, **FK 3**, **index 6**, **sequence 1**
>   - 즉 schema/DDL 마이그레이션은 완전히 동작
>
> ### 동작하지 않는 부분
>
> - **record export = 0**. 모든 테이블에서
>   `NormalMigrationException: Table <name> was not found` 발생
> - 원인: CMT 의 `JDBCExporter.exportTableRecords` 가 호출하는
>   `MigrationConfiguration.getSrcTableSchema(owner, name)` 가 null
>   을 반환. 이는 `MigrationConfiguration.srcCatalog` 가 런타임에
>   비어 있거나 Informix 의 source 객체를 담지 못해서 발생
> - script.xml 의 `<table schema="" ...>` 빈 schema 속성을
>   `MAIN_USER` 로 후처리해도 (RegenerateScripts.sanitizeXml 에서)
>   같은 에러가 지속됨 — 문제는 attribute parsing 단계가 아니라
>   srcCatalog 초기화 자체에 있음
>
> ### 영향
>
> - DumpTest: 골든 매니페스트의 `INFORMIX_main_user_*` 데이터 파일이
>   생성되지 않음 → 어셔션 실패
> - CubridTest: 타깃 CUBRID 의 모든 테이블이 비어 있음 (record 0) →
>   row count 어셔션 실패
> - 본 SPEC 의 §3 / §5 row 데이터는 source DB 에는 정상 적재되지만
>   CUBRID 까지 옮겨오지 않음
>
> ### 해제 조건 (둘 중 하나)
>
> 1. **CMT 측 패치** — `JDBCExporter.exportTableRecords` 또는
>    `MigrationConfiguration.getSrcTableSchema` 가 Informix source
>    의 schema 매핑을 처리하도록 수정
> 2. **runtime catalog 우회 경로** — script.xml 에 `<schema>` (singular)
>    CDATA 로 직렬화된 catalog 를 끼워 넣어 srcCatalog 가 비어 있지
>    않도록 강제
>
> ### 지금까지 만들어 둔 것
>
> | 항목 | 상태 |
> |---|---|
> | 본 SPEC | 작성 완료 |
> | `db/informix/{init,main_schema}/*` 시드 SQL | 동작 (Flyway 적용 정상) |
> | `InformixContainer` / `InformixDatabaseInitializer` | 동작 |
> | `Drivers.INFORMIX` / `pom.xml` (`com.ibm.informix:jdbc`, `flyway-database-informix`) | 동작 |
> | `RegenerateScripts` `INFORMIX_TO_CUBRID` / `INFORMIX_TO_DUMPFILE` | 동작 (script.xml 생성) |
> | `script.xml` 픽스처 | 동작 |
> | `InformixToCubridTest` / `InformixToDumpTest` | DDL 부분만 검증, record assertion 은 보류 |
>
> ---

이 문서는 Informix source DB 용 시드 명세다. 공통 규칙은
`../COMMON_SEED_CONTRACT.md` 를 따른다.

대상 버전은 **IBM Informix 14.10 Developer Edition**
(`icr.io/informix/informix-developer-database:14.10.FC7W1DE` — 구
`ibmcom/informix-developer-database` 는 deprecated). Developer Edition
은 free-for-development 라이선스로 `LICENSE=accept` env 만 주면 사용
가능하며, 별도 라이선스 파일이나 hostname 바인딩 절차가 필요 없다 (cf.
Tibero §0). JDBC 드라이버 (`com.ibm.informix:jdbc:4.50.3`) 는 Maven
Central 에 정식 배포되어 있어 dependency 한 줄로 가져올 수 있다.

본 spec 은 Oracle/MySQL spec 을 base 로 하되, **CMT 의 InformixSchemaFetcher
가 명시적으로 추출하지 않는 객체** (synonym, grant, comment, partition)
와 **CMT 의 InformixDataTypeHelper 가 collection 으로 안 보는 type**
(set / list / multiset) 을 anti-coverage 로 분리한다.

## 1. Scope

Informix seed 는 다음 기능을 검증한다.

| Area | Informix implementation |
|---|---|
| Schema role | `MAIN_SCHEMA` 단일 사용자. multi-schema 는 첫 회 결과로 anti-coverage 확정 (아래 표 참고) |
| Key generation | `SERIAL` / `BIGSERIAL` / `SERIAL8` (auto-increment), 그리고 `CREATE SEQUENCE` (`buildSequence` override 됨) — Informix 는 둘 다 native |
| Type extension | `DECIMAL(p,s)`, `MONEY(p,s)`, `LVARCHAR`, `TEXT`, `CLOB`, `BLOB`, `BYTE`, `INTERVAL`, `DATETIME ... TO FRACTION(n)`, `DATE`, `BOOLEAN`, `JSON`, `BSON` |
| View | `buildViews` + `buildViewDDL` override 됨 (`sysviews` 의 `viewtext` 직접 조립) |

다음은 anti-coverage. 별도 결정/이슈로 등록되기 전까지는 seed 에 포함하지 않는다.

| Excluded | Reason |
|---|---|
| **Procedure / Function (SPL)** | CMT 의 `MigrationTasksScheduler` 가 procedure/function body 를 변환하는 분기 (`createProcedureBodies`/`createFunctionBodies`) 는 **Oracle / Tibero 만** 진입 (`MigrationTasksScheduler.java:146-153`). 그 외 source DB (Informix 포함) 는 else 경로 `createProcedures()`/`createFunctions()` 로 떨어지고, 이들은 `ProcedureExportTask`/`FunctionExportTask` → `MigrationNoSupportEvent` 만 발행 (`ProcedureExportTask.java:55`, `FunctionExportTask.java:55`). 즉 Informix procedure/function 은 추출은 되지만 CUBRID 로 변환/마이그레이션되지 않음 → 시드에 두면 dump 에 등장하지 않거나 "no-support objects" txt 에만 남아 검증 의미가 없음 |
| **Trigger** | CMT 의 `TriggerExportTask` 는 source DB 에 관계없이 `MigrationNoSupportEvent` 만 발행 (`TriggerExportTask.java:55`). scheduler 는 `if (config.isExportNoSupportObjects())` 일 때만 `createTriggers()` 를 부르며 결과는 별도 txt 파일로만 남음 (`MigrationTasksScheduler.java:132-135`, 주석: "Export functions/procedures/triggers to a txt file"). CMT 는 모든 source DB 에서 trigger 를 마이그레이션 대상으로 삼지 않음 |
| **Comment** (`COMMENT ON TABLE/COLUMN/VIEW`) | Informix engine 이 native 로 comment 를 지원하지 않음. CMT 의 `getTableComment`/`getViewComment` 가 명시적으로 `return null` (`InformixSchemaFetcher.java:557, 564 — "informix doesn't support comment feature"`). 시드에 comment 자체를 둘 수 없음 |
| **Synonym** | `InformixSchemaFetcher` 가 `buildSynonym` 을 override 하지 않음 → 부모 `// do nothing` → 빈 결과 |
| **Cross-schema GRANT** | `buildGrant` 미 override → 빈 결과 |
| **Partition** (Fragmentation) | `buildPartitions` 본문이 `// do nothing / not yet implement` (line 539-543) |
| **Collection types** (`SET`, `LIST`, `MULTISET`) | type-map 에는 entry 가 있으나 `InformixDataTypeHelper.isCollection()` 이 항상 `false` 반환 (line 122 주석 처리됨) → 추출 시 정상 처리 안 됨. 회귀 폭증 위험 |
| **OPAQUE / Custom UDT / DISTINCT TYPE** | `CREATE TYPE` 객체 미추출 |
| **Materialized view-like 객체** (Informix 는 native MV 없음) | n/a |
| **TimeSeries / Spatial DataBlade** | 엔터프라이즈 DataBlade 기능; 표준 fetcher 추출 경로 없음 |
| **Multi-schema (cross-owner)** | 첫 회 결과로 확정. CMT 의 `InformixSchemaFetcher` 는 `<table>` 의 source-side `schema` 속성을 비워 두는 (다른 source DB 와 다른) 동작이고, 그 결과 `MigrationConfiguration.getSrcTableSchema("", name)` 가 `Schema.getSchemaByName("")` 호출에서 null 을 반환해 모든 record export 가 "Table not found" 로 실패. 다중 user 환경 자체가 catalog level metadata 에는 등장하지만 실제 export 는 single-user 만 가능. RegenerateScripts 의 sanitizeXml 단계에서 `schema=""` 를 `schema="MAIN_USER"` 로 후처리해 단일 사용자 시나리오를 살림. MySQL/MariaDB 와 동일한 single-schema 패턴으로 collapse |
| **TEXT / CLOB inline literal** | Informix simple-large-object (TEXT) / smart-large-object (CLOB) 는 SQL INSERT literal 로 문자열을 받지 않음 (-617). 시드의 `e2e_text_types` 의 text_col / clob_col 은 R_NULL 만 채우고 나머지는 NULL — 다른 in-row 텍스트 컬럼 (CHAR/VARCHAR/NCHAR/NVARCHAR/LVARCHAR) 은 정상 battery 적용. 비-NULL LOB 페이로드는 driver-side `setClob` 으로 후처리할 수 있으나 현 시점에서는 보류 |
| **BYTE / BLOB inline literal** | 위와 동일한 LOB 한계 — `e2e_binary_types` 는 R_NULL row 만 |
| **`BSON` 입력 변환** | Developer Edition 이미지의 `bson_input` / `bson_from_char` 함수가 모든 입력 (빈 `'{}'` 포함) 에 대해 `-937: bson_from_char failed` 를 반환. 명시적 `BSON()` 함수 호출은 `-674: Routine (bson) can not be resolved`, `'{}'::BSON` cast 도 동일한 -937. `e2e_misc_types.bson_col` 은 NULL 만 채움. 컬럼 자체는 metadata / type-map (`bson → json`) 검증을 위해 유지 |
| **JSON `JSON()` 함수** | Informix 에 callable `JSON()` 생성자 없음 (`-674`). bare-string implicit cast 만 지원. `e2e_misc_types.json_col` 은 bare 문자열 그대로 사용 |
| **`SMALLINT = -32768` 등 NULL marker 값** | Informix 는 정수 타입의 가장 negative bit pattern 을 NULL marker 로 예약 → `SMALLINT` 의 R_MIN 은 `-32767`, `INTEGER` 는 `-2147483647`, `BIGINT/INT8` 은 `-9223372036854775807`. 다른 DB 와 달리 표준 two ' s-complement minimum 을 사용 못 함 |
| **`DATE('MM/DD/YYYY')` short-form** | Informix 의 `DATE(string)` 함수는 세션 `DBDATE` 환경변수에 의존 — `icr.io/informix` Developer Edition 은 deterministic 한 default 가 없음. 시드는 locale-independent `TO_DATE(string, format)` 사용. 본 SPEC 의 ISO 표기 (`2024-04-20`) 은 모두 `TO_DATE('YYYY-MM-DD', '%Y-%m-%d')` 로 변환 |
| **`CREATE INDEX` on FK column** | Informix 는 FK 제약조건 생성 시 자동으로 비-unique 인덱스를 만들어 둠. 같은 컬럼 셋에 명시 인덱스를 또 생성하면 `-350: Index already exists`. 시드는 FK 칼럼에 명시 인덱스를 두지 않음 (Oracle/MSSQL 시드의 `idx_e2e_order_customer` 패턴은 Informix 에서 redundant) |

> **첫 회 테스트로 확정해야 할 항목**:
> 1. **Multi-schema (`MAIN_SCHEMA` + `REF_SCHEMA`)** — Informix 는 한
>    database 안에 여러 owner 를 둘 수 있고, `getSchemaNames` 는 JDBC
>    metadata 의 `TABLE_SCHEM` 을 그대로 받는다. CMT 가 schema-qualified
>    추출을 살리는지 첫 회 결과로 확정. 살아남지 못하면 MySQL/MariaDB 처럼
>    `REF_SCHEMA` 를 anti-coverage 로 후퇴.
> 2. **Sequence vs Serial 추출 차이** — Informix 는 두 가지 auto-increment
>    경로가 있다: `CREATE SEQUENCE` 와 `SERIAL`/`SERIAL8`/`BIGSERIAL`
>    컬럼 수정자. 둘 다 시드에 두고 dump 결과에서 어떻게 표현되는지
>    확정 (CUBRID 는 SERIAL/SEQUENCE 의 미묘한 차이가 있음).
> 3. **`DATETIME YEAR TO FRACTION(n)`** — `buildSQLTable` 이 `"datetime year"`
>    를 발견하면 `"datetime"` 으로 쳐서 보낸다 (line 125-128). FRACTION
>    precision 이 dump 에 살아남는지 첫 회 결과로 확정.
> 4. **`BSON` → `JSON` 매핑** — type-map 은 `bson → json` 으로 보내지만
>    실제 binary 데이터 변환 경로는 첫 회 결과로 확정.
> 5. **`LVARCHAR(n)` 와 `TEXT` 차이** — `LVARCHAR` 는 row-stored variable
>    길이 (CHAR/VARCHAR 가족), `TEXT` 는 row-overflow blob-style 큰 문자.
>    CUBRID target type 분기가 의도대로인지 확정.
> 6. **`MONEY(p,s)`** — `decimal/numeric/money` 동일 family 이지만 round
>    동작이 약간 다름. type-map 결과 확정.

## 2. Schema Setup

| Schema (Informix owner) | Role |
|---|---|
| `MAIN_SCHEMA` | main migration schema |
| `REF_SCHEMA` | cross-schema object source (TENTATIVE) |

Informix 는 schema = user (PostgreSQL/Oracle 과 유사). DB 생성과 user
발급은 `init` 단계에서 처리한다. Informix 는 `CREATE USER` 가 OS-level
account 을 요구하므로, Docker 컨테이너 안에서 다음 패턴을 사용:

```sh
# init 컨테이너에서 (root 권한, --privileged 필요)
useradd -m main_user && echo "main_user:CmtMain#2026" | chpasswd
useradd -m ref_user  && echo "ref_user:CmtRef#2026"   | chpasswd
```

그 뒤 dbaccess 로 DB 와 권한 부여:

```sql
CREATE DATABASE e2e_db WITH LOG;
GRANT CONNECT TO main_user;
GRANT CONNECT TO ref_user;
GRANT RESOURCE TO main_user;
GRANT RESOURCE TO ref_user;
```

JDBC URL 은 CMT 의 `InformixDatabase.makeUrl` 이 다음 패턴으로 만든다:

```
jdbc:informix-sqli://<host>:<port>/<dbname>:INFORMIXSERVER=informix
```

`INFORMIXSERVER=informix` 가 **하드코딩**되어 있으므로 컨테이너의
INFORMIXSERVER 환경변수를 `informix` 로 맞춘다 (Developer Edition 의
default 는 `dev` 또는 `informix` — 컨테이너 init script 로 명시 설정
필요).

`MAIN_SCHEMA` 에 대한 cross-schema object 권한:

```sql
GRANT SELECT, INSERT, UPDATE, DELETE
ON ref_schema.e2e_ref_audit
TO main_user;
```

위 GRANT 자체는 anti-coverage (CMT 가 `buildGrant` 미구현) 이지만 runtime
view/synonym 시나리오에서 필요할 수 있어 init 단계에 포함만 해 둔다.

## 3. Business Graph

### 3.1 `MAIN_SCHEMA.e2e_customer`

| Column | Informix type | Required data |
|---|---|---|
| `customer_id` | `INTEGER` | PK |
| `customer_code` | `CHAR(4)` | unique, `C001`.. |
| `customer_name` | `VARCHAR(100)` | non-null |
| `customer_alias` | `NVARCHAR(60)` | nullable unicode |
| `status` | `CHAR(1)` | `A`/`I`/`D` |
| `credit_limit` | `DECIMAL(15,2)` | NULL, 0, normal, high |
| `created_on` | `DATE` | fixed date |
| `updated_on` | `DATE` | nullable |

Required constraints:

```sql
PRIMARY KEY (customer_id) CONSTRAINT pk_e2e_customer
UNIQUE (customer_code)    CONSTRAINT uk_e2e_customer_code
CHECK (status IN ('A', 'I', 'D')) CONSTRAINT ck_e2e_customer_status
```

Required rows:

| customer_id | customer_code | customer_name | customer_alias | status | credit_limit | created_on |
|---:|---|---|---|---|---:|---|
| 1 | `C001` | `ALPHA CUSTOMER` | `Alpha Alias` | `A` | 12500.75 | `2024-01-10` |
| 2 | `C002` | `BETA CUSTOMER`  | NULL          | `I` | NULL     | `2024-02-29` |
| 3 | `C003` | `한국 고객`       | `별칭-한글`     | `D` | 0        | `2024-03-15` |
| 4 | `C004` | `HIGH LIMIT CUSTOMER` | `High`    | `A` | 9999999999999.99 | `2024-04-20` |

> Informix 의 `NVARCHAR` 는 GLS (Global Language Support) locale 에 맞춰
> 동작. 컨테이너의 `DB_LOCALE=en_us.utf8`, `CLIENT_LOCALE=en_us.utf8` 로
> UTF-8 encoding 강제.

### 3.2 `MAIN_SCHEMA.e2e_order`

| Column | Informix type | Required data |
|---|---|---|
| `order_id` | `BIGSERIAL` | PK (auto-increment, Informix 고유) |
| `customer_id` | `INTEGER` | FK to customer |
| `order_no` | `VARCHAR(30)` | business key |
| `order_status` | `VARCHAR(20)` | `NEW`/`HOLD`/`DONE`/`CANCEL` |
| `total_amount` | `MONEY(18,2)` | nullable amount |
| `ordered_at` | `DATETIME YEAR TO SECOND` | fixed |
| `settled_at` | `DATETIME YEAR TO SECOND` | nullable |
| `source_comment` | `VARCHAR(200)` | blank/unicode/punctuation |

Required constraints/indexes:

```sql
PRIMARY KEY (order_id) CONSTRAINT pk_e2e_order
FOREIGN KEY (customer_id) REFERENCES e2e_customer(customer_id) CONSTRAINT fk_e2e_order_customer
CHECK (order_status IN ('NEW','HOLD','DONE','CANCEL')) CONSTRAINT ck_e2e_order_status
CREATE INDEX idxd_e2e_order_ordered_at ON e2e_order(ordered_at DESC);
```

> Informix 는 FK 제약조건 생성 시 해당 컬럼 셋에 자동으로 비-unique
> 인덱스를 만든다 (Oracle / MSSQL 과 다른 점). 따라서 다른 source DB
> SPEC 에 있던 `CREATE INDEX idx_e2e_order_customer ON e2e_order(customer_id)`
> 명시 인덱스는 Informix 에서 **redundant 이며 `-350: Index already exists`
> 오류를 일으킨다**. FK 의 자동 인덱스가 동일한 lookup 성능을 제공하므로
> Informix seed 에서는 명시 인덱스를 생략한다.
>
> Function-based index 는 Informix 가 `CREATE INDEX ... ON tbl(UPPER(col))`
> syntax 를 받지만 `buildTableIndexes` 의 JDBC metadata 경로가 expression
> 을 살리는지는 **첫 회 결과로 확정 (TENTATIVE)** — Oracle 에서는 살아남았고
> MariaDB 에서는 anti-coverage 로 떨어졌다.

Required rows:

| order_id | customer_id | order_no | order_status | total_amount | source_comment |
|---:|---:|---|---|---:|---|
| 1 | 1 | `ORD-2024-001` | `NEW`    | 50.00  | `first order` |
| 2 | 1 | `ORD-2024-002` | `DONE`   | 125.50 | `done-order` |
| 3 | 2 | `ORD-2024-003` | `HOLD`   | NULL   | `  padded comment  ` |
| 4 | 3 | `ORD-2024-004` | `CANCEL` | 0      | `취소 주문 !@#` |

`BIGSERIAL` 의 starting value 는 row insert 후 5 가 되도록 설정.

### 3.3 `MAIN_SCHEMA.e2e_order_line`

Composite PK.

| Column | Informix type |
|---|---|
| `order_id` | `BIGINT` |
| `line_no` | `SMALLINT` |
| `sku` | `VARCHAR(30)` |
| `qty` | `INTEGER` |
| `unit_price` | `DECIMAL(15,2)` |
| `line_note` | `VARCHAR(200)` |

Required constraints:

```sql
PRIMARY KEY (order_id, line_no) CONSTRAINT pk_e2e_order_line
FOREIGN KEY (order_id) REFERENCES e2e_order(order_id) CONSTRAINT fk_e2e_order_line_order
CHECK (qty > 0) CONSTRAINT ck_e2e_order_line_qty
```

Required rows:

| order_id | line_no | sku | qty | unit_price | line_note |
|---:|---:|---|---:|---:|---|
| 1 | 1 | `SKU-ALPHA`  | 2  | 25.00  | `normal` |
| 2 | 1 | `SKU-BETA`   | 1  | 125.50 | NULL |
| 3 | 1 | `SKU-HOLD`   | 10 | 9.99   | `hold line` |
| 4 | 1 | `SKU-CANCEL` | 1  | 0      | `cancelled` |

### 3.4 `MAIN_SCHEMA.e2e_employee`

Self-reference FK.

| Column | Informix type | Required data |
|---|---|---|
| `employee_id` | `INTEGER` | PK |
| `manager_id` | `INTEGER` | nullable self-FK |
| `emp_name` | `VARCHAR(100)` | non-null |
| `hired_on` | `DATE` | fixed |

Required constraints:

```sql
PRIMARY KEY (employee_id) CONSTRAINT pk_e2e_employee
FOREIGN KEY (manager_id) REFERENCES e2e_employee(employee_id) CONSTRAINT fk_e2e_employee_manager
```

Required rows:

| employee_id | manager_id | emp_name    | hired_on    |
|---:|---:|---|---|
| 1 | NULL | `CEO`       | `2020-01-01` |
| 2 | 1    | `Manager A` | `2021-01-01` |
| 3 | 2    | `Staff A1`  | `2022-01-01` |

### 3.5 `REF_SCHEMA.e2e_ref_audit`

| Column | Informix type |
|---|---|
| `audit_id` | `INTEGER` |
| `audit_message` | `VARCHAR(200)` |
| `created_at` | `DATETIME YEAR TO SECOND` |

Required constraints:

```sql
PRIMARY KEY (audit_id) CONSTRAINT pk_e2e_ref_audit
```

Required row:

| audit_id | audit_message | created_at |
|---:|---|---|
| 1 | `reference audit row` | `2024-04-20 00:00:00` |

## 4. Object Samples

### 4.1 Sequence

Informix 11.70+ 는 `CREATE SEQUENCE` 객체를 native 지원하며 CMT
`buildSequence` 가 `syssequences` 를 직접 조회한다.

```sql
CREATE SEQUENCE main_schema.e2e_customer_seq START WITH 5 INCREMENT BY 1 NOCYCLE NOCACHE;
```

`order` 의 PK 는 `BIGSERIAL` 컬럼이므로 별도 sequence 객체를 두지 않는다
(Informix 의 SERIAL family 는 system-managed counter 로 `syssequences`
와 분리되어 있음).

### 4.2 View

```sql
CREATE VIEW main_schema.e2e_order_summary_v
       (order_id, order_no, customer_code, customer_name, order_status, total_amount, ordered_at)
AS
SELECT o.order_id,
       o.order_no,
       c.customer_code,
       c.customer_name,
       o.order_status,
       o.total_amount,
       o.ordered_at
  FROM main_schema.e2e_order o
  JOIN main_schema.e2e_customer c
    ON c.customer_id = o.customer_id;
```

> Informix `CREATE VIEW` 는 column 이름을 view 정의 시 명시할 수 있다
> (괄호 list). CMT `buildViewDDL` 은 `viewtext` 를 그대로 받아 `as` split
> 으로 body 만 남긴다 (line 408) — column list 가 dump 에 살아남는지
> 첫 회 확인.

### 4.3 SPL Routine — N/A (anti-coverage)

Informix 의 SPL procedure / function 은 시드에 포함하지 않는다. CMT 의
`MigrationTasksScheduler` 가 procedure/function body 를 변환하는 분기
(`createProcedureBodies`/`createFunctionBodies`) 는 Oracle / Tibero
source DB 만 진입하며, 그 외 (Informix 포함) 는 else 경로
`createProcedures()`/`createFunctions()` 로 떨어져
`ProcedureExportTask` / `FunctionExportTask` 가 `MigrationNoSupportEvent`
만 발행한다. 즉 추출은 되지만 변환되지 않으므로 시드 객체로 둘 의미가
없다. 자세한 코드 인용은 §1 anti-coverage 표 참고.

### 4.4 Trigger — N/A (anti-coverage)

CMT 의 `TriggerExportTask` 는 source DB 에 관계없이 항상
`MigrationNoSupportEvent` 만 발행한다. CMT 는 모든 source DB 에서
trigger 를 마이그레이션 대상으로 삼지 않으며, `isExportNoSupportObjects`
옵션이 켜져 있을 때만 별도 txt 파일에 dump 된다. 시드 객체로 둘 의미가
없다. 자세한 코드 인용은 §1 anti-coverage 표 참고.

### 4.5 Comment — N/A (anti-coverage)

Informix engine 은 `COMMENT ON TABLE/COLUMN/VIEW` syntax 를 지원하지 않으며,
CMT 의 `getTableComment`/`getViewComment` 는 명시적으로 `null` 을 반환한다
(`InformixSchemaFetcher.java:557, 564`). 따라서 다른 source DB 와 달리
**comment 시드를 두지 않는다**.

비교:
- Oracle / Tibero / CUBRID — `COMMENT ON ...` native, 시드에 포함
- MySQL / MariaDB — 컬럼 인라인 `COMMENT 'text'` native, 시드에 포함
- MSSQL — extended property 로 우회, 시드에 포함 (TENTATIVE)
- **Informix — 엔진이 지원 안 함, 시드 미포함**

### 4.6 Synonym — N/A (anti-coverage)

`buildSynonym` 미 override.

### 4.7 Cross-schema GRANT — runtime only

Init 단계에서 `GRANT SELECT,...` 를 실행해 view/synonym runtime 동작은
보장하되, dump 에는 등장하지 않는다 (`buildGrant` 미 override).

## 5. Type Test Tables

### 5.1 `MAIN_SCHEMA.e2e_text_types`

Columns:

| Column | Informix type |
|---|---|
| `id` | `INTEGER` PK |
| `char_col` | `CHAR(3)` |
| `varchar_col` | `VARCHAR(10)` |
| `nchar_col` | `NCHAR(10)` |
| `nvarchar_col` | `NVARCHAR(40)` |
| `lvarchar_col` | `LVARCHAR(2048)` |
| `text_col` | `TEXT` |
| `clob_col` | `CLOB` |

SQL-ready values:

| id | row | char_col | varchar_col | nchar_col | nvarchar_col | lvarchar_col | text_col | clob_col |
|---:|---|---|---|---|---|---|---|---|
| 1 | `R_NULL`      | NULL  | NULL    | NULL    | NULL              | NULL                                 | NULL                            | NULL |
| 2 | `R_EMPTY`     | `' '` | `''`    | `' '`   | `''`              | `''`                                 | `''`                            | `FILETOCLOB('/dev/null','client')` |
| 3 | `R_MIN`       | `'A'` | `'A'`   | `'A'`   | `'A'`             | `'A'`                                | `'A'`                           | `'A'::CLOB` |
| 4 | `R_MAX`       | `'XYZ'` | `'ABCDEFGHIJ'` | `'1234567890'` | `'한국어 漢字 cafe'` | repeat 256 chars                     | repeat 4000 chars                 | repeat 8000 chars |
| 5 | `R_UNICODE`   | NULL  | NULL    | `'한中Ω'` | `'한국 漢字 cafe'`  | `'한국어 漢字 cafe punctuation !@#'` | `'한국어 漢字 cafe punctuation !@#'` | `'한국어 漢字 cafe punctuation !@#'::CLOB` |
| 6 | `R_BOUNDARY`  | `'   '` | `' A '` | `'   '` | `' A '`          | `'line1' || CHR(10) || 'line2'`     | newline embedded text             | newline embedded clob |

> `LVARCHAR` 는 row-stored (varchar 가족), `TEXT` 와 `CLOB` 은 row-overflow
> blob 가족 — Informix 는 두 buffer 형태가 분리되어 있어 type-map 의
> CUBRID target 분기가 의도대로인지 확인.

### 5.2 `MAIN_SCHEMA.e2e_numeric_types`

| Column | Informix type |
|---|---|
| `id` | `INTEGER` PK |
| `smallint_col` | `SMALLINT` |
| `integer_col` | `INTEGER` |
| `bigint_col` | `BIGINT` |
| `int8_col` | `INT8` (legacy bigint) |
| `decimal_col` | `DECIMAL(20,4)` |
| `numeric_col` | `NUMERIC(15,3)` |
| `money_col` | `MONEY(15,2)` |
| `smallfloat_col` | `SMALLFLOAT` |
| `float_col` | `FLOAT` |
| `serial_col` | `SERIAL` |
| `serial8_col` | `SERIAL8` |

SQL-ready values:

| id | row | smallint_col | integer_col | bigint_col | int8_col | decimal_col | numeric_col | money_col | smallfloat_col | float_col | serial_col | serial8_col |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | `R_NULL`      | NULL   | NULL        | NULL                    | NULL                    | NULL                | NULL          | NULL          | NULL    | NULL    | 0 | 0 |
| 2 | `R_EMPTY`     | 0      | 0           | 0                       | 0                       | 0                   | 0             | 0             | 0       | 0       | 0 | 0 |
| 3 | `R_MIN`       | -32768 | -2147483647 | -9223372036854775807    | -9223372036854775807    | -9999999999999999.9999 | -999999999999.999 | -9999999999999.99 | -1E20 | -1E20 | 0 | 0 |
| 4 | `R_MAX`       | 32767  | 2147483647  | 9223372036854775807     | 9223372036854775807     | 9999999999999999.9999  | 999999999999.999  | 9999999999999.99  | 1E20  | 1E20  | 0 | 0 |
| 5 | `R_BOUNDARY`  | 42     | 100         | 9876543210              | 9876543210              | 9876543210.1234     | 1234567890.123 | 1234.56       | 3.14    | 3.141593 | 0 | 0 |
| 6 | `R_REPRESENTATIVE` | 7 | 7        | 7                       | 7                       | 100.2500            | 100.250        | 100.25        | 6.28    | 2.71828 | 0 | 0 |

> `SERIAL` / `SERIAL8` 컬럼은 INSERT 시 0 을 주면 system-managed 값으로
> 채워진다. 시드 시 `SET SERIAL VALUE` 로 시작 값 고정.

### 5.3 `MAIN_SCHEMA.e2e_temporal_types`

| Column | Informix type |
|---|---|
| `id` | `INTEGER` PK |
| `date_col` | `DATE` |
| `dt_yts_col` | `DATETIME YEAR TO SECOND` |
| `dt_ytf3_col` | `DATETIME YEAR TO FRACTION(3)` |
| `dt_ytf5_col` | `DATETIME YEAR TO FRACTION(5)` |
| `interval_ds_col` | `INTERVAL DAY(2) TO SECOND` |
| `interval_ym_col` | `INTERVAL YEAR(4) TO MONTH` |

SQL-ready values:

| id | row | date_col | dt_yts_col | dt_ytf3_col | dt_ytf5_col | interval_ds_col | interval_ym_col |
|---:|---|---|---|---|---|---|---|
| 1 | `R_NULL`     | NULL                       | NULL                                       | NULL                                          | NULL                                          | NULL                                          | NULL |
| 2 | `R_EMPTY`    | `DATE('01/01/1970')`       | `DATETIME (1970-01-01 00:00:00) YEAR TO SECOND` | `DATETIME (1970-01-01 00:00:00.000) YEAR TO FRACTION(3)` | `DATETIME (1970-01-01 00:00:00.00000) YEAR TO FRACTION(5)` | `INTERVAL (0 0:0:0) DAY(2) TO SECOND` | `INTERVAL (0-0) YEAR(4) TO MONTH` |
| 3 | `R_MIN`      | `DATE('01/01/1900')`       | `DATETIME (1970-01-02 00:00:01) YEAR TO SECOND` | `DATETIME (1970-01-02 00:00:00.001) YEAR TO FRACTION(3)` | `DATETIME (1970-01-02 00:00:00.00001) YEAR TO FRACTION(5)` | `INTERVAL (-99 23:59:59) DAY(2) TO SECOND` | `INTERVAL (-9999-11) YEAR(4) TO MONTH` |
| 4 | `R_MAX`      | `DATE('12/31/9999')`       | `DATETIME (2038-01-18 23:59:59) YEAR TO SECOND` | `DATETIME (2038-01-18 23:59:59.999) YEAR TO FRACTION(3)` | `DATETIME (2038-01-18 23:59:59.99999) YEAR TO FRACTION(5)` | `INTERVAL (99 23:59:59) DAY(2) TO SECOND` | `INTERVAL (9999-11) YEAR(4) TO MONTH` |
| 5 | `R_BOUNDARY` | `DATE('02/29/2024')`       | `DATETIME (2024-02-29 12:34:56) YEAR TO SECOND` | `DATETIME (2024-02-29 12:34:56.123) YEAR TO FRACTION(3)` | `DATETIME (2024-02-29 12:34:56.12345) YEAR TO FRACTION(5)` | `INTERVAL (1 02:03:04) DAY(2) TO SECOND` | `INTERVAL (2-6) YEAR(4) TO MONTH` |

> Informix `DATETIME ... TO FRACTION(n)` 은 n 자리 fractional second 를
> 가진다. CMT 의 `buildSQLTable` 이 `"datetime year"` 를 발견하면
> 일반 `"datetime"` 로 코어싱한다 (line 125-128) — FRACTION precision
> 보존 여부가 첫 회 확인 대상.
>
> `dt_yts_col`, `dt_ytf3_col`, `dt_ytf5_col` 의 boundary 는 CUBRID
> `TIMESTAMP` 의 1970-01-02 ~ 2038-01-18 범위에 맞춤. CUBRID 가 wider
> precision 을 받으면 ranges 확장.

### 5.4 `MAIN_SCHEMA.e2e_binary_types`

| Column | Informix type |
|---|---|
| `id` | `INTEGER` PK |
| `byte_col` | `BYTE` |
| `blob_col` | `BLOB` |

SQL-ready values:

| id | row | byte_col | blob_col |
|---:|---|---|---|
| 1 | `R_NULL`     | NULL                                     | NULL |
| 2 | `R_EMPTY`    | binary 0 byte                            | empty blob |
| 3 | `R_MIN`      | binary `\x00`                            | binary `\x00` |
| 4 | `R_MAX`      | binary `\x00FF` repeated to fill         | binary `\x00FF` repeated |
| 5 | `R_BOUNDARY` | binary `CAFEBABE`                        | binary `CAFEBABE` |

> `BYTE` 는 row-overflow blob (legacy), `BLOB` 은 SmartBlob (modern).
> 둘 다 INSERT 시 `LOAD FROM` 또는 `FILETOBLOB('/path','client')` 사용.
> 시드 SQL 단계에서 `FILETOBLOB` 으로 컨테이너 내부 작은 파일을 가리키게
> 하거나 driver-side 로 binary literal 사용 (Flyway placeholder 한계
> 첫 회 확인).

### 5.5 `MAIN_SCHEMA.e2e_misc_types`

Informix 고유 type 묶음.

| Column | Informix type |
|---|---|
| `id` | `INTEGER` PK |
| `boolean_col` | `BOOLEAN` |
| `json_col` | `JSON` |
| `bson_col` | `BSON` |

SQL-ready values:

| id | row | boolean_col | json_col | bson_col |
|---:|---|---|---|---|
| 1 | `R_NULL`     | NULL    | NULL                                                      | NULL |
| 2 | `R_EMPTY`    | `'f'`   | `'{}'::JSON`                                              | `'{}'::BSON` |
| 3 | `R_MIN`      | `'f'`   | `'{"k":1}'::JSON`                                         | `'{"k":1}'::BSON` |
| 4 | `R_MAX`      | `'t'`   | `'{"k":[1,2,3,"한",null]}'::JSON`                         | `'{"k":[1,2,3,"한",null]}'::BSON` |
| 5 | `R_UNICODE`  | `'t'`   | `'{"name":"한국 漢字 cafe"}'::JSON`                        | `'{"name":"한국 漢字 cafe"}'::BSON` |

> Informix BOOLEAN 은 character `'t'`/`'f'`/NULL 셋 중 하나로 표현.
> JSON 은 type-map 에서 `JSON` 또는 `VARCHAR` 둘 중 하나로 fallback;
> BSON 은 `JSON` 으로만 mapping.

### 5.6 Anti-coverage 명시 — collection types

`SET`, `LIST`, `MULTISET` 은 시드에 포함하지 않는다. 이유:

- type-map 에 entry 는 있으나 `InformixDataTypeHelper.isCollection()` 이
  항상 `false` 를 반환 (line 122 주석 처리됨)
- buildSQLTable 에서 `"sendreceive"` 를 `"set"` 으로 바꾸는 로직만 있고
  실제 collection 추출 경로는 미연결
- 결과적으로 dump 에 정상적 표현이 안 됨 → 회귀 폭증 위험

테스트 요구사항이 생기면 별도 extension 으로 분리.

## 6. Bug/Feature Additions

Informix 전용 회귀 케이스를 추가할 때 다음 규칙을 따른다.

1. 가능한 경우 기존 `e2e_*` table 을 재사용한다.
2. Informix 전용 동작은 `e2e_informix_*` 객체에 둔다.
3. 특정 이슈에만 좁게 묶인 케이스는 `bug_<jira-id>_*` 이름을 사용한다
   (COMMON_SEED_CONTRACT §2.2 변환 규칙).
4. 일반 회귀 위험으로 승격된 케이스는 `e2e_informix_*` 객체로 옮긴다.
