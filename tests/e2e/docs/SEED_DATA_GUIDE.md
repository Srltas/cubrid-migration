# CMT E2E Seed Dataset Strategy

이 문서는 CMT Console E2E 테스트에서 source DB 에 어떤 데이터셋을 만들어야
하는지 정의하는 전략 문서다. Oracle 전용 시드 설명이 아니라, 이후 MySQL,
MariaDB, MSSQL, Informix, Tibero 등 source DB 가 늘어나도 같은 기준으로
데이터셋을 설계할 수 있게 하는 것이 목적이다.

핵심 원칙은 다음과 같다.

> CMT 가 지원하는 기능을 먼저 나열하고, 각 기능을 실제 DB 위에서 검증할 수 있는
> 최소 데이터셋을 만든다.

이 문서는 **무엇을 왜 만들어야 하는가**를 설명한다. 실제 table/column/value 의
구체적인 SQL 작성 기준은 `seed/COMMON_SEED_CONTRACT.md` 와 DB별
`seed/<db>/SEED_SPEC.md` 를 따른다.

## 1. 문서 구조

| 문서 | 책임 |
|---|---|
| `SEED_DATA_GUIDE2.md` | 전략. CMT 기능과 필요한 데이터셋의 관계를 설명 |
| `seed/COMMON_SEED_CONTRACT.md` | 공통 계약. SQL 작성 시 반드시 지켜야 할 이름, row ID, 생성/제외 규칙 |
| `seed/<db>/SEED_SPEC.md` | DB별 시공 명세. 실제 DB 문법과 SQL-ready seed value 정의 |

시드 SQL 을 작성할 때는 이 순서로 확인한다.

1. `SEED_DATA_GUIDE2.md` 에서 목적과 범위를 확인한다.
2. `seed/COMMON_SEED_CONTRACT.md` 에서 공통 규칙을 확인한다.
3. 대상 DB 의 `seed/<db>/SEED_SPEC.md` 에서 실제 table/column/value 를 생성한다.

## 2. 최우선 원칙

### 2.1 Fetcher 우선

CMT 가 source DB 에서 추출하지 못하는 객체는 core dataset 에 넣지 않는다. 시드를
추가하기 전 해당 DB 의 schema fetcher 가 객체를 수집하는지 먼저 확인한다.

충돌 시 우선순위는 다음과 같다.

1. CMT fetcher/importer 가 지원하는가?
2. CMT 기능 검증에 필요한가?
3. 공통 dataset 으로 둘 수 있는가?
4. 아니면 DB-specific extension 으로 분리해야 하는가?

### 2.2 Core 와 Extension 분리

모든 source DB 가 가능한 한 같은 역할을 구현하는 데이터셋을 `Core Dataset` 으로
둔다. 특정 DB 에만 있는 타입/객체는 `DB-specific Extension` 으로 분리한다.

### 2.3 Naming 적용 범위

이 문서의 schema/file naming 은 새로 만들거나 재정비하는 dataset 에 적용한다.
기존 E2E 리소스가 다른 이름을 쓰고 있으면, 명시적인 마이그레이션 작업이 아닌 한
자동으로 이름을 바꾸지 않는다.

## 3. CMT 기능 요약

CMT E2E 데이터셋은 아래 기능을 검증할 수 있어야 한다.

| 기능 영역 | CMT 기능 | 데이터셋에 필요한 것 |
|---|---|---|
| Schema | source schema/user 를 target schema 로 매핑 | 역할이 다른 schema 2개 |
| Table | table 생성, table rename, create/replace/migrate_data 옵션 | 업무 테이블 여러 개와 안정적인 business key |
| Column | column 생성, column rename, nullable/default/comment/type 보존 | nullable/default/comment/rename 대상 컬럼 |
| Constraint | PK, FK, unique, check, composite key | master-child 관계와 복합키 테이블 |
| Index | normal, unique, composite, descending/function-based 등 DB 지원 index | 목적이 다른 index sample |
| Data | row count, 값 보존, NULL/empty/unicode/boundary 값 | 업무 데이터 + Type Test Tables |
| View | view DDL, 참조 table/schema 변환 | join 기반 view |
| Sequence | sequence/identity/serial, start value sync | 사용된 sequence 와 다음 값 검증 가능 데이터 |
| Synonym | cross-schema object reference | `MAIN_SCHEMA` synonym 이 `REF_SCHEMA` object 를 참조 |
| Grant | grantor/grantee/object owner/privilege 보존 | `REF_SCHEMA` object 에 대한 `MAIN_SCHEMA` 권한 |
| Routine | procedure/function 추출과 변환 | 작은 procedure 1개 + function 1개 |
| DB-specific | DB별 고유 타입/객체 | core dataset 과 분리된 extension table |

## 4. 권장 Schema 구조

스키마명은 역할이 드러나야 한다. `owner`, `test` 처럼 의미가 모호한 이름은 새
dataset 에서 피한다.

| Schema | 역할 |
|---|---|
| `MAIN_SCHEMA` | 주 마이그레이션 대상. 업무 테이블, Type Test Tables, view, routine 을 둔다 |
| `REF_SCHEMA` | 참조 대상 schema. cross-schema table/sequence 를 만들고 `MAIN_SCHEMA` 에 권한을 부여한다 |

권장 리소스 구조는 역할 기준으로 나눈다.
Flyway file 작성 규칙은 `seed/COMMON_SEED_CONTRACT.md` 의 `Flyway File Rules`
를 따른다.
`ref_schema` 와 `main_schema` 는 서로 다른 user/schema 로 별도 Flyway 실행되는
구조를 전제로 하므로, 각 directory 는 `V1` 부터 시작할 수 있다.

```text
tests/e2e/src/test/resources/db/<source-db>/
├── init/
│   └── 00_prepare_database.sql
├── ref_schema/
│   ├── V1__schema_ref_objects.sql
│   ├── V2__data_ref_objects.sql
│   └── V3__grants_to_main.sql
└── main_schema/
    ├── V1__schema_business_tables.sql
    ├── V2__schema_views.sql
    ├── V3__schema_type_test_tables.sql
    ├── V4__schema_routines.sql
    └── V99__data.sql
```

DB 가 다중 schema 를 지원하지 않으면 같은 역할을 database, owner, namespace,
table prefix 중 해당 DB 에 자연스러운 방식으로 표현한다.

## 5. Core Dataset

모든 online source DB 는 가능한 한 같은 Core Dataset 을 구현한다. DB 가 특정
객체를 지원하지 않거나 CMT 가 추출하지 않으면 `N/A` 로 명시하고 억지로 만들지
않는다.

### 5.1 Business Graph

| Object | 역할 | 필수 속성 |
|---|---|---|
| `e2e_customer` | master table | single PK, unique customer code, status, nullable alias, credit limit |
| `e2e_order` | transaction table | FK to customer, status distribution, amount, ordered/settled timestamp |
| `e2e_order_line` | child table | composite PK, FK to order, line number, quantity, unit price |
| `e2e_employee` | hierarchy table | self-reference FK. CMT fetcher 가 지원하지 않는 DB 에서만 `N/A` |
| `REF_SCHEMA.e2e_ref_audit` | reference table | `MAIN_SCHEMA` 에서 grant/synonym 으로 접근하는 cross-schema 대상 |

이 구조 하나로 table 생성, PK/FK, unique/index, view, grant/synonym, condition
옵션, row migration 을 모두 자극할 수 있다.

### 5.2 Business Data

업무 데이터는 많을 필요가 없다. 대신 옵션과 값 보존을 검증할 수 있는 분포가
있어야 한다.

| Column | 권장 분포 | 이유 |
|---|---|---|
| `customer.status` | `A`, `I`, `D` 각각 1개 이상 | 상태 필터와 check/domain 성격 검증 |
| `order.status` | `NEW`, `HOLD`, `DONE`, `CANCEL` 각각 1개 이상 | condition 결과 row count 검증 |
| `credit_limit` | NULL, 0, normal, high value | NULL/숫자/필터 검증 |
| `order_line.qty` | 1, normal, high value | numeric value 보존 검증 |
| note/text column | NULL, blank, ASCII, Korean/Unicode, punctuation | charset, trim, replace 검증 |
| timestamp column | fixed past date, leap day, timezone-sensitive value if supported | temporal conversion 검증 |

권장 행 수는 전체 100행 미만이다. E2E seed 는 부하 테스트용이 아니라 회귀 검출용
대표 데이터다.

### 5.3 Type Test Tables

`Type Test Tables` 는 타입 변환을 검증하기 위한 전용 테이블이다. 업무 테이블에
모든 경계값을 섞으면 시나리오가 읽기 어려워지므로, 업무 데이터와 타입 테스트
데이터를 분리한다.

| Table | 포함할 타입군 | 공통 row pattern |
|---|---|---|
| `e2e_text_types` | char, varchar, nchar, text, clob 계열 | `R_NULL`, `R_EMPTY`, `R_MIN`, `R_MAX`, `R_UNICODE`, `R_BOUNDARY` |
| `e2e_numeric_types` | integer, decimal, float 계열 | `R_NULL`, `R_EMPTY`, `R_MIN`, `R_MAX`, `R_BOUNDARY` |
| `e2e_temporal_types` | date, time, timestamp, timezone 계열 | `R_NULL`, `R_EMPTY`, `R_MIN`, `R_MAX`, `R_BOUNDARY` |
| `e2e_binary_types` | raw, bit, blob 계열 | `R_NULL`, `R_EMPTY`, `R_MIN`, `R_MAX`, `R_BOUNDARY` |

Core Dataset 에 DB-specific pseudo type 을 넣지 않는다. 예를 들어 Oracle
`ROWID/UROWID` 는 Oracle extension 명세에서 다룬다.

### 5.4 Object Samples

| Object | Seed rule | 검증 이유 |
|---|---|---|
| View | `e2e_customer` + `e2e_order` 를 join 하는 summary view | view DDL 과 schema-qualified reference 변환 |
| Sequence/identity/serial | customer/order key 생성용 1~2개 | current/start/increment 보존 |
| Synonym | 지원 DB 에서 `MAIN_SCHEMA` synonym 이 `REF_SCHEMA` table 을 참조 | cross-schema name resolution |
| Grant | reference object 에 대한 `SELECT/INSERT/UPDATE/DELETE` 중 지원 권한 | grantor/grantee/object owner/privilege 보존 |
| Routine | scalar function 1개, procedure 1개 | routine DDL 추출과 변환 |
| Comment | table comment 1개, column comment 1개 | metadata 보존 |

## 6. DB-specific Extension

Core Dataset 은 모든 DB 에 공통으로 유지하고, DB 고유 기능은 extension 으로
분리한다. DB별 기능을 core table 에 섞으면 다른 DB 와 비교하기 어렵다.

| Source DB | Extension 후보 |
|---|---|
| Oracle | schema owner, sequence, synonym, grant, PL/SQL, `NUMBER` precision/scale, `TIMESTAMP WITH TIME ZONE`, `INTERVAL`, `ROWID`, `CLOB/BLOB` |
| Tibero | Oracle 유사 객체를 우선 사용하되, Tibero fetcher 가 실제 추출하는 객체만 포함 |
| MySQL | `AUTO_INCREMENT`, `ENUM`, `SET`, `JSON`, `TEXT/BLOB`, generated/default expression 지원 범위 |
| MariaDB | MySQL core + MariaDB 고유 JSON/virtual column/sequence 지원 여부 확인 후 추가 |
| MSSQL | schema, identity, `NVARCHAR`, `DATETIME2`, `DATETIMEOFFSET`, `UNIQUEIDENTIFIER`, computed column, default/check constraint |
| Informix | owner/schema, `SERIAL`, `LVARCHAR`, `CLOB/BLOB`, SPL routine, Informix date/time 타입 |
| CUBRID | collection, enum, json, monetary, bit/varbit, timezone datetime, comment, serial |

## 7. Anti-coverage

아래는 기본 seed 에 넣지 않는다.

| 제외 항목 | 이유 |
|---|---|
| CMT fetcher 가 추출하지 못하는 객체 | seed 해도 CMT 경로를 검증하지 못함 |
| 모든 타입 조합 매트릭스 | 단위 테스트/변환 테스트의 책임. E2E 는 대표값만 검증 |
| 매우 큰 데이터 | 성능/부하 테스트로 분리 |
| 실패 유도 데이터 | 성공 E2E 를 오염시킴. negative fixture 로 분리 |
| DB-specific 객체를 core table 에 혼합 | source DB 간 공통 비교 기준을 흐림 |
| unsupported routine parameter 별 개별 sample | 폭증 방지. 필요하면 통합 sample 1개만 둠 |

## 8. Bug 또는 신규 기능 추가 원칙

버그나 신규 기능 때문에 데이터셋을 추가할 때는 아래 순서를 따른다.

1. 기존 Core Dataset 으로 재현 가능한지 확인한다.
2. 기존 row 값만 조정해서 검증 가능하면 새 table 을 만들지 않는다.
3. 기존 table 에 column 1개를 추가하면 충분한지 검토한다.
4. DB-specific 기능이면 `e2e_<db>_...` extension table 로 분리한다.
5. 특정 버그 재현용이면 `bug_<issue>_...` 객체 또는 row 를 사용한다.
6. 버그가 일반 회귀 위험으로 판단되면 나중에 `bug_` 객체를 `e2e_` core/extension 으로 승격한다.
7. 추가한 객체/row 가 어떤 CMT 기능을 검증하는지 문서에 한 줄로 남긴다.
