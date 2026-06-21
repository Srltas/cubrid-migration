# Common Seed Contract

이 문서는 모든 source DB seed spec 이 반드시 지켜야 하는 공통 계약이다. 시드
SQL 을 작성할 때는 DB별 `SEED_SPEC.md` 보다 이 문서의 규칙을 먼저 따른다.

## 1. Priority Rules

규칙 충돌 시 아래 순서가 우선한다.

1. **Fetcher support first**: CMT 가 해당 source DB 에서 추출하지 못하는 객체는
   만들지 않는다.
2. **Core before extension**: 모든 DB 가 구현할 수 있는 역할은 core dataset 으로
   유지한다.
3. **Extension isolation**: DB 고유 타입/객체는 `e2e_<db>_...` 객체로 분리한다.
4. **No arbitrary values**: DB별 spec 에 없는 edge value 를 임의 생성하지
   않는다. 필요하면 `TODO` 로 남기고 spec 을 먼저 갱신한다.
5. **Small but meaningful**: 성공 E2E seed 는 전체 100행 미만을 기본 목표로 한다.

## 2. Recommended Names

### 2.1 Schemas

| Schema | 역할 |
|---|---|
| `MAIN_SCHEMA` | 주 마이그레이션 대상 |
| `REF_SCHEMA` | cross-schema grant/synonym/reference 검증 대상 |

DB 가 schema 개념을 다르게 쓰면 같은 역할을 database, owner, namespace, prefix
중 해당 DB 에 맞는 방식으로 구현한다.

### 2.2 Object Prefixes

| 대상 | 규칙 | 예 |
|---|---|---|
| core object | `e2e_` | `e2e_customer`, `e2e_text_types` |
| DB-specific extension | `e2e_<db>_` | `e2e_oracle_locator_types`, `e2e_mysql_enum_types` |
| bug reproduction | `bug_<jira-id>_` | `bug_tools_1234_order_amount` |
| feature-specific trial | `feat_<feature>_` | `feat_json_payload` |

`e2e_` 는 E2E 전용 seed object 라는 뜻이다. 실제 고객 업무 객체와 혼동하지 않게
하기 위한 prefix 다.

#### Jira issue ID 변환 규칙 — `bug_` prefix

`<jira-id>` 부분은 jira.org 의 이슈 ID (예: `TOOLS-1234`) 를 다음 규칙으로
DB identifier 로 변환한 값을 사용한다.

1. 전부 lowercase 로 바꾼다 (`TOOLS-1234` → `tools-1234`).
2. `-` 를 `_` 로 치환한다 (`tools-1234` → `tools_1234`). Oracle/MySQL/CUBRID
   identifier 에 dash 가 들어가면 quoting 이 필요해 fragile 해지므로 underscore 만
   사용한다.
3. 결과를 `bug_` prefix 와 합쳐 최종 prefix 를 만든다 (`bug_tools_1234_`).

| Jira ID | DB identifier 변환 | 예시 객체 명 |
|---|---|---|
| `TOOLS-1234` | `tools_1234` | `bug_tools_1234_parent`, `bug_tools_1234_child` |

원본 Jira ID (대문자 + dash 형태) 는 SEED_SPEC.md 의 bug fixture 등록 항목에 평문으로
함께 기록해 추적성을 유지한다 — DB identifier 가 변형되더라도 어떤 이슈를 재현하는지
사람이 한눈에 알 수 있어야 한다.

### 2.3 Constraint/Object Names

| 객체 | 규칙 | 예 |
|---|---|---|
| Primary key | `pk_<table>` | `pk_e2e_order` |
| Foreign key | `fk_<from>_<to>` | `fk_e2e_order_customer` |
| Unique constraint | `uk_<table>_<purpose>` | `uk_e2e_customer_code` |
| Normal index | `idx_<table>_<purpose>` | `idx_e2e_order_customer` |
| Function index | `idxf_<table>_<purpose>` | `idxf_e2e_order_upper_status` |
| Descending index | `idxd_<table>_<purpose>` | `idxd_e2e_order_ordered_at` |
| Check constraint | `ck_<table>_<rule>` | `ck_e2e_customer_status` |
| View | `<base>_v` | `e2e_order_summary_v` |
| Sequence/serial | `<table>_seq` | `e2e_order_seq` |
| Synonym | `<target>_syn` | `e2e_ref_audit_syn` |
| Function | `<purpose>_fn` | `e2e_customer_label_fn` |
| Procedure | `<purpose>_proc` | `e2e_upsert_customer_proc` |

## 3. Core Dataset Objects

| Object | Required? | Notes |
|---|---|---|
| `MAIN_SCHEMA.e2e_customer` | Yes | master table |
| `MAIN_SCHEMA.e2e_order` | Yes | transaction table |
| `MAIN_SCHEMA.e2e_order_line` | Yes | child table with composite PK |
| `MAIN_SCHEMA.e2e_employee` | Yes | self-reference FK. Only mark `N/A` when the source DB fetcher cannot extract it |
| `REF_SCHEMA.e2e_ref_audit` | Yes if DB supports cross-schema | reference grant/synonym target |
| `MAIN_SCHEMA.e2e_text_types` | Yes | Type Test Table |
| `MAIN_SCHEMA.e2e_numeric_types` | Yes | Type Test Table |
| `MAIN_SCHEMA.e2e_temporal_types` | Yes | Type Test Table |
| `MAIN_SCHEMA.e2e_binary_types` | Yes | Type Test Table |
| `MAIN_SCHEMA.e2e_order_summary_v` | Yes if view is supported | join view |
| sequence/identity/serial for customer/order | Yes if supported | DB-specific implementation |
| synonym to reference object | Yes if supported | DB-specific implementation |
| grant on reference object | Yes if supported | DB-specific implementation |
| procedure/function sample | Yes if supported | DB-specific implementation |

Core Dataset 에 pseudo type 또는 DB-specific locator table 을 넣지 않는다. 예를
들어 Oracle `ROWID/UROWID` 는 `e2e_oracle_locator_types` 로 분리한다.

## 4. Canonical Row IDs

Type Test Tables 는 같은 row ID 를 사용한다. row ID 는 "무엇을 검증하는가"의
공통 의미이며, 실제 SQL literal 은 DB별 spec 이 정한다.

| ID | Name | 공통 의미 |
|---|---|---|
| 1 | `R_NULL` | PK 외 nullable 컬럼은 NULL |
| 2 | `R_EMPTY` | 비어 있거나 가장 작은 실제 표현. Oracle empty string 처럼 DB가 NULL로 저장하는 경우도 이 row에 기록 |
| 3 | `R_MIN` | 해당 타입군에서 작은 값, 음수, 이른 날짜 등 lower boundary 성격 |
| 4 | `R_MAX` | 해당 타입군에서 큰 값, 긴 문자열, 늦은 날짜 등 upper boundary 성격 |
| 5 | `R_UNICODE` | 다국어, 특수 문자, punctuation, newline 등 charset/escaping 검증 |
| 6 | `R_BOUNDARY` | DB별 변환 위험이 큰 특수 경계값 |
| 7+ | `R_REPRESENTATIVE` | 현실적인 업무형 sample 또는 특정 기능 확장 row |

`R_EMPTY` 는 DB별 저장 의미가 다를 수 있다. 예를 들어 Oracle `''` 는 NULL 로
저장될 수 있고, MySQL `''` 는 빈 문자열로 보존될 수 있다. 이 차이는 row ID 를
바꾸지 않고 DB별 `SEED_SPEC.md` 에 명시한다.

## 5. Business Data Contract

업무 데이터는 옵션 검증을 가능하게 하는 분포를 가져야 한다.

| Field | Required distribution |
|---|---|
| `customer.status` | `A`, `I`, `D` 각각 1개 이상 |
| `order.status` | `NEW`, `HOLD`, `DONE`, `CANCEL` 각각 1개 이상 |
| `credit_limit` | NULL, 0, normal positive, high positive |
| text note fields | NULL, blank, ASCII, Korean/Unicode, punctuation |
| timestamp fields | fixed date, leap day, optional timezone-sensitive value |

`replace` 검증은 source seed 만으로 끝나지 않는다. 테스트 헬퍼가 target DB 에
marker row 를 미리 넣고, seed 의 business key 로 marker row 삭제/보존을 확인한다.

`migrate_data=no` 검증은 schema-valid table 을 사용한다. seed 자체는 정상 row 를
가지되, 테스트 script 에서 data migration 을 끈 뒤 target row count 가 0인지 본다.

## 6. Anti-coverage

아래는 기본 seed 에 넣지 않는다.

| Exclusion | Reason |
|---|---|
| 객체 fetcher 가 추출하지 못하는 객체 | CMT 경로를 검증하지 못함 |
| 모든 타입 조합 매트릭스 | E2E 폭증. 단위/변환 테스트 책임 |
| 매우 큰 데이터 | 성능/부하 테스트 책임 |
| 실패 유도 데이터 | 성공 E2E 오염. negative fixture 로 분리 |
| DB-specific 객체를 core table 에 혼합 | source DB 간 공통 기준을 흐림 |
| unsupported routine parameter 별 개별 sample | 폭증 방지. 필요하면 통합 sample 1개만 둠 |

## 7. Flyway File Rules

Seed dataset 은 Flyway migration file 로 구현한다. Dataset 을 추가하거나 변경할 때는
다음 규칙을 지킨다.

1. 파일명은 `V<version>__<purpose>.sql` 형식을 사용한다. `<purpose>` 는
   lower_snake_case 로 작성하고, 예시는 `schema_business_tables`,
   `schema_type_test_tables`, `data_business_rows`, `data_type_rows`,
   `grants_to_main`, `synonyms`, `routines` 이다.
2. Version number 는 같은 Flyway execution/history table 안에서만 unique 해야 한다.
   서로 다른 user/schema 에 대해 별도 Flyway 실행으로 적용되는 directory 는 각각
   `V1` 부터 시작할 수 있다. 여러 directory 를 한 번의 Flyway 실행에 함께 넘기는
   구조라면 전체 location 집합 안에서 version number 가 충돌하지 않아야 한다.
3. 파일은 lifecycle 기준으로 나눈다. DB/user 준비는 `init`, reference schema 객체는
   `ref_schema`, main schema 객체는 `main_schema` 에 둔다. 기존 resource 구조가
   다르면 같은 역할을 가진 현재 directory 에 둔다.
4. DDL, data, grant, synonym, routine 은 가능한 한 별도 파일로 분리한다. 한 파일에
   섞어야 하는 DB 제약이 있으면 파일 상단 주석에 이유를 적는다.
5. 이미 공유된 migration file 은 unrelated change 로 renumber 하거나 rewrite 하지
   않는다. 기존 seed 를 확장할 때는 다음 version file 을 추가한다. 아직 공유되지
   않은 작업 중 파일은 정리 목적으로 수정할 수 있다.
6. SQL 은 deterministic 해야 한다. `SYSDATE`, random value, current user,
   container host/port/path, 실행 시점에 달라지는 값은 seed value 로 사용하지
   않는다.
7. Constraint, index, sequence, view, synonym, routine 이름은 명시적으로 지정한다.
   DB가 자동 생성한 이름에 의존하지 않는다.
8. Cross-schema object 는 schema-qualified name 을 사용한다. View, grant, synonym,
   FK reference 는 `MAIN_SCHEMA.<object>` 또는 `REF_SCHEMA.<object>` 형태로
   명확하게 작성한다.
9. Grant file 은 grant 대상 object 가 생성된 뒤 실행되어야 한다. 예를 들어
   `grants_to_main` 은 `ref_schema` 의 table/sequence 생성 file 이후 version 에 둔다.
10. 모든 migration file 은 clean DB 에서 Flyway version order 로 실행 가능해야 한다.
   수동 선행 작업에 의존하지 않는다. 예외가 필요하면 `init` 단계에 명시한다.
11. Dataset object, column, row 를 추가하거나 의미를 바꾸면 해당 DB 의
    `SEED_SPEC.md` 도 같은 변경으로 갱신한다.
12. Bug/feature 전용 seed 는 object 또는 file purpose 에 `bug_<jira-id>` 또는
    `feat_<feature>` 를 포함한다. `<jira-id>` 는 §2.2 의 Jira issue ID 변환 규칙을
    따른다 (lowercase + `-`→`_`, 예: `TOOLS-1234` → `tools_1234`). 일반 회귀 검증으로
    승격되면 `e2e_` 또는 `e2e_<db>_` 객체로 정리한다.

## 8. SQL Generation Rules

Seed SQL 을 작성할 때는 다음을 지킨다.

1. 이 문서에 없는 core object 를 임의로 추가하지 않는다.
2. DB별 spec 에 없는 edge literal 을 임의로 만들지 않는다.
3. DB-specific 기능은 `e2e_<db>_...` extension 으로 분리한다.
4. fetcher 지원 여부가 불명확한 객체는 SQL 을 만들지 말고 `N/A` 또는 `TODO` 로
   남긴다.
5. 새 bug/feature row 는 기존 table 에 넣을 수 있는지 먼저 판단한다.
6. 새 table 이 필요하면 어떤 CMT 기능을 검증하는지 주석 또는 spec 에 기록한다.
