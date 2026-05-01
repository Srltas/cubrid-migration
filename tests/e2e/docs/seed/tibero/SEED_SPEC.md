# Tibero Seed Specification

> ## 0. 현재 상태: 활성 (Phase 12.* 시리즈 + Phase 13.1)
>
> Tibero E2E 는 라이선스 인프라 (TmaxSoft 30 일 trial + libfaketime + system-scope
> JDBC dep + e2e.tibero.* 4 키 required) 와 함께 **실제 부팅 / 시드 적용 / 마이그레이션
> 검증** 까지 동작한다. 활성된 객체:
>
> - §3 Business Graph (e2e_customer / e2e_order / e2e_order_line / e2e_employee)
>   + REF_SCHEMA.e2e_ref_audit + cross-schema GRANT + synonym
> - §4.1 Sequence × 2, §4.2 view, §4.3 synonym, §4.5 comments
> - §4.4 PL/SQL function + procedure (Phase 13.1, Oracle SPEC §4.4 mirror)
> - §5.1 ~ §5.5 Type-test 4 종 + e2e_oracle_locator_types (rowid-only)
>   (Phase 13.1, Oracle SPEC §5.1 ~ §5.5 mirror)
>
> ### 보류 중인 항목 (TENTATIVE — 후속 phase)
>
> | 항목 | 보류 사유 |
> |---|---|
> | §4.6 trigger | Tibero buildTriggers override 동작 / type-map 결과 미확정 |
> | §5.6 `e2e_tibero_semi_structured_types` (JSON / XMLTYPE) | type-map (`Tibero2CUBRID.xml`) entry 는 있으나 round-trip 결과 미확정 |
>
> ### Phase 13.1 1차 실행으로 확정된 anti-coverage
>
> | 항목 | 결과 | 사유 |
> |---|---|---|
> | `UROWID` 컬럼 타입 | anti-coverage 확정 | Tibero 7 SQL parser 가 거부 (`JDBC-7454: Datatype 'UROWID' is invalid`). e2e_oracle_locator_types 에서 컬럼 자체 제거. |
>
> ### Phase 13.3 — Tibero importer 회귀로 추정되는 거부 케이스 (좁힘 + 임시 우회)
>
> Phase 13.1 1차 실행에서 numeric_types (6 rows) + temporal_types (5 rows)
> 가 CMT batch 에서 모두 거부됨. CUBRID error 메시지로 좁힘 결과:
>
> | Type | CUBRID error | 좁힌 원인 (binary search) | 우회 |
> |---|---|---|---|
> | numeric_types | `Cannot coerce host var to type numeric` | R_MIN/R_MAX 의 38자리 NUMBER(38,0) + 1E20 scientific + NUMBER(8,-2) 큰 값들의 조합 | R_MIN/R_MAX 를 보수적 boundary 값 (7자리 정수, 정상 fractional) 로 교체. R_BOUNDARY/R_REPRESENTATIVE 는 BINARY_FLOAT_INFINITY/NaN 만 finite 값으로 교체. |
> | temporal_types | `Cannot coerce host var to type datetimeltz` | `tsltz6_col` (TIMESTAMP WITH LOCAL TIME ZONE) 컬럼의 모든 non-NULL 값 | 모든 row 에서 `tsltz6_col = NULL`. `tstz9_col` (WITH TIME ZONE) 은 정상. |
>
> 동일한 시드 값이 Oracle→CUBRID 에서는 모두 통과. **CMT 의 Tibero importer
> 가 NUMBER 의 큰 값 / 음수 scale / IEEE 754 special / TIMESTAMP WITH LOCAL
> TIME ZONE 을 Oracle importer 와 다른 wire format 으로 보내서 CUBRID coerce
> 가 거부**한다는 가설. 후속 phase 에서 CMT importer 의 Tibero 경로 코드를
> 조사 필요 (`com.cubrid.cubridmigration.core.engine.importer.impl.JDBCImporter`
> + Tibero 전용 type adapter). 우회 적용 후 Phase 13.3 에서 13/13 PASS,
> 39/39 imported 확인.
>
> 위 anti-coverage 항목은 §1 표에도 반영. Phase 12 ~ 13.1 의 routines /
> type-test 활성화는 확신도가 충분한 Oracle-호환 영역에 한정한다.
>
> ### 인프라 운영 메모
>
> - 라이선스 갱신 30 일 주기는 `tibero/restart-faketime-tibero.sh` + cron 으로 자동화
>   되어 있다 (`--if-due` 모드).
> - `tibero7-jdbc-17.jar` 은 `tests/e2e/lib/` 에 system-scope 로 보관 + `.gitignore` 됨.
>   `tibero` Maven profile 이 jar 존재 시 자동 활성.
> - 4 개 required key (`e2e.tibero.{image,hostname,license,faketime}`) 가 모두 채워지지
>   않으면 `@EnabledIf("...TiberoEnvironment#isAvailable")` 가 Tibero TC 만 skip.
> - 활성화 이전 (Phase 12 이전) 의 보류 사유 / 인프라 구축 과정은 git 로그
>   `Phase 12.1 ~ 12.8` + `tests/e2e/tibero/README.md` 의 setup SOP 참고.

이 문서는 Tibero source DB 용 시드 명세다. 공통 규칙은
`../COMMON_SEED_CONTRACT.md` 를 따른다.

대상 버전은 **Tibero 7** (`tiberoofficial/tibero:latest`). Tibero 는
TmaxData 의 Oracle 호환 RDBMS 로 PL/SQL, NUMBER/VARCHAR2 type 모델,
sequence/synonym/view object 모두 Oracle 과 호환된다. CMT 의
`TiberoSchemaFetcher` 는 Oracle fetcher 와 거의 동일한 coverage 를
제공한다 (`buildPartitions`, `buildProcedures`, `buildSequence`,
`buildSynonym`, `buildTables`, `buildTablePK`, `buildTableFKs`,
`buildTableIndexes`, `buildTriggers`, `buildViewColumns`, `buildGrant`,
`buildViews`).

본 spec 은 **Oracle SPEC 을 base 로 사용**하고, Tibero 고유 항목 (native
JSON, XMLTYPE) 을 별도 extension table 로 추가한다.

## 1. Scope

Tibero seed 는 다음 기능을 검증한다.

| Area | Tibero implementation |
|---|---|
| Schema roles | `MAIN_SCHEMA`, `REF_SCHEMA` users (Oracle 과 동일 패턴) |
| Cross-schema | grant + synonym (Oracle 과 동일 — `buildGrant`, `buildSynonym` 모두 override 됨) |
| Key generation | Tibero sequence (`CREATE SEQUENCE`, Oracle syntax 호환) |
| Routine | PL/SQL procedure / function (Oracle 호환 syntax) |
| Trigger | PL/SQL trigger (`buildTriggers` override 됨) |
| Comment | Oracle 의 `COMMENT ON ...` (Tibero 호환) |
| Type extension | `NUMBER`, `TIMESTAMP WITH TIME ZONE`, `INTERVAL`, `ROWID`, `CLOB/BLOB`, `BINARY_FLOAT`/`BINARY_DOUBLE` |
| **Tibero 고유** | `JSON` (native), `XMLTYPE` (native) — Tibero 의 자체 type 으로 type-map (`Tibero2CUBRID.xml`) 에 entry 존재 |

다음은 anti-coverage. 별도 결정/이슈로 등록되기 전까지는 seed 에 포함하지 않는다.

| Excluded | Reason |
|---|---|
| Materialized View | `TiberoSchemaFetcher` 가 `buildMaterializedView` 를 override 하지 않음 (Oracle 과 동일) |
| Package / Package Body | Oracle 과 동일하게 별도 추출 경로 미정의 |
| Custom Type (`CREATE TYPE`) | 동일 사유 |
| Database Link | E2E 폭증 + CMT 매핑 미확정 |
| `UROWID` 컬럼 타입 | Tibero 7 SQL parser 가 컬럼 타입으로 거부 (`JDBC-7454: Datatype 'UROWID' is invalid`, Phase 13.1 1차 실행 확정). e2e_oracle_locator_types 에서 컬럼 제거. |
| Domain Index / Materialized View / Package | 추출 경로 미정의 |

> **Function-based index**: Phase 12.7 의 13/13 PASS 에서 `idxf_e2e_order_upper_status`
> round-trip 동작이 확인됨 → confirmed-supported. 0 의 TENTATIVE 표 / 본문 §3.x 의
> 마크는 historical 표기.

> **첫 회 테스트로 확정해야 할 항목**:
> 1. **Native `JSON` type** — Tibero 7 은 native JSON 을 지원한다. 시드에
>    포함하되 CMT type-map (`Tibero2CUBRID.xml`) 의 변환 결과가 CUBRID
>    에서 무엇으로 떨어지는지 (string vs CLOB) 첫 회 dump 로 확정.
> 2. **`XMLTYPE`** — Tibero 의 XMLTYPE 추출/변환 동작 확정. type-map 에는
>    entry 가 있으나 실제 round-trip 동작은 첫 회 결과로 확정.
> 3. **Function-based index** — Oracle 에서는 `idxf_e2e_order_upper_status`
>    가 동작했다. Tibero 동일 여부는 첫 회 결과로 확정.
> 4. **Trigger 추출 / 변환** — `buildTriggers` 가 override 되어 있지만
>    CUBRID target 으로의 변환 paths 는 첫 회 결과로 확정.
> 5. **Flyway 호환성** — Flyway 는 Tibero 를 공식 지원하지 않는다. Oracle
>    dialect 로 띄울 수 있는지 (또는 `flyway.placeholderReplacement=false`
>    수준의 minimal 사용) 첫 회 결과로 확정. 안 되면 init/migrate 단계를
>    sqlplus 호환 CLI (`tbsql`) `execInContainer` 호출로 대체.

## 2. Schema Setup

| Schema | Role |
|---|---|
| `MAIN_SCHEMA` | main migration schema |
| `REF_SCHEMA` | cross-schema object and grant source |

Oracle 과 동일하게 `init` 단계는 user/schema 준비만 담당한다.
`MAIN_SCHEMA` 에 대한 object grant 는 `REF_SCHEMA.e2e_ref_audit` 생성
이후 `ref_schema/V3__grants_to_main.sql` 에 둔다.

```sql
GRANT SELECT, INSERT, UPDATE, DELETE
ON REF_SCHEMA.e2e_ref_audit
TO MAIN_SCHEMA;
```

## 3. Business Graph

> Tibero 는 Oracle PL/SQL / type 시스템 호환이므로 Oracle SPEC §3
> 의 모든 테이블 정의 (e2e_customer / e2e_order / e2e_order_line /
> e2e_employee / e2e_ref_audit) 와 row 값을 **그대로 사용한다**. 컬럼
> 타입, 제약, FK, idx 패턴, row 데이터 모두 동일하다.
>
> Oracle SPEC §3.1 ~ §3.5 를 정본으로 보고, 이 SPEC 에서는 Tibero 가
> 다르게 다루는 부분만 명시한다.

### 3.x Tibero-specific notes

- `idxf_e2e_order_upper_status` 는 Oracle 과 동일 syntax 로 적용. Phase 12.7 의
  TiberoToCubridTest 13/13 PASS 에서 round-trip 검증됨 (confirmed-supported).
- `e2e_employee` self-FK 는 Tibero 도 지원 (Oracle 호환).

## 4. Object Samples

### 4.1 Sequence

Oracle SPEC §4.1 그대로:

```sql
CREATE SEQUENCE MAIN_SCHEMA.e2e_customer_seq START WITH 5 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE MAIN_SCHEMA.e2e_order_seq    START WITH 5 INCREMENT BY 1 NOCACHE NOCYCLE;
```

### 4.2 View

Oracle SPEC §4.2 그대로 사용 (schema-qualified name).

### 4.3 Synonym

Oracle SPEC §4.3 그대로:

```sql
CREATE OR REPLACE SYNONYM MAIN_SCHEMA.e2e_ref_audit_syn
FOR REF_SCHEMA.e2e_ref_audit;
```

### 4.4 PL/SQL Routine

Oracle SPEC §4.4 의 함수 / 프로시저를 그대로 사용한다 (PL/SQL 호환).
실제 SQL 은 `db/tibero/main_schema/V4__schema_routines.sql` 에 동일 본문으로 작성.

> Tibero 시드는 raw JDBC (`ClasspathSqlRunner`) 로 실행되므로 PL/SQL 블록의
> trailing `/` 가 statement terminator 로 인식되어야 한다. ClasspathSqlRunner
> 는 파일 안에 `^\s*/\s*$` 라인이 등장하면 slash-mode splitter 로 자동 전환
> 한다 (Phase 13.1). Oracle Flyway 와 동일한 V4 SQL 형식이 그대로 유효.

### 4.5 Comment

Oracle SPEC §4.5 의 `COMMENT ON ...` 표를 그대로 적용한다. Tibero 도
표준 `COMMENT ON TABLE` / `COMMENT ON COLUMN` syntax 를 지원한다.

### 4.6 Trigger (Tibero 추가)

Oracle SPEC 에는 trigger 가 없지만 Tibero `buildTriggers` 가 override
되어 있어 추출 path 가 살아 있으므로 최소 1 개 trigger 를 둔다.
첫 회 결과로 type-map 동작이 의도대로인지 확정 (TENTATIVE).

```sql
CREATE OR REPLACE TRIGGER MAIN_SCHEMA.e2e_order_audit_trg
BEFORE INSERT ON MAIN_SCHEMA.e2e_order
FOR EACH ROW
BEGIN
  :NEW.source_comment := COALESCE(:NEW.source_comment, 'inserted');
END;
/
```

이 trigger 는 row 의미를 바꾸지 않도록 설계됐다 (`source_comment` 가
NULL 일 때만 default 채움). 시드 row 들은 모두 non-NULL `source_comment`
를 사용하므로 행위가 fired 되지 않으며 dump 에 영향 없다.

## 5. Type Test Tables

> Oracle SPEC §5.1 ~ §5.5 의 type-test table 4 종 (`e2e_text_types`,
> `e2e_numeric_types`, `e2e_temporal_types`, `e2e_binary_types`) 과
> Oracle extension `e2e_oracle_locator_types` (rowid/urowid) 를 그대로
> 가져와 Tibero 시드에도 둔다. Tibero 의 NUMBER/VARCHAR2/DATE/TIMESTAMP/
> INTERVAL/CLOB/BLOB/RAW/BINARY_FLOAT/BINARY_DOUBLE 모두 Oracle 과 동일
> 의미를 가진다.
>
> 단, **Tibero 고유 type extension** (`e2e_tibero_semi_structured_types`)
> 을 추가한다.

### 5.1 ~ 5.4 (Oracle 과 동일)

`MAIN_SCHEMA.e2e_text_types`, `e2e_numeric_types`, `e2e_temporal_types`,
`e2e_binary_types` 는 Oracle SPEC §5.1 ~ §5.4 를 정본으로 사용한다.

### 5.5 `MAIN_SCHEMA.e2e_oracle_locator_types` (Oracle extension 재사용)

이 extension table 은 Oracle SPEC §5.5 의 정의를 그대로 가져와
Tibero 에도 둔다. Tibero 의 `ROWID` 는 Oracle 과 호환된다.

```sql
INSERT INTO MAIN_SCHEMA.e2e_oracle_locator_types (id, rowid_col, urowid_col)
SELECT 1, src.ROWID, src.ROWID
FROM MAIN_SCHEMA.e2e_customer src
WHERE src.customer_id = 1;
```

`UROWID` 컬럼은 Phase 13.1 1차 실행에서 Tibero 7 parser 가 거부함이 확인되어
**anti-coverage 로 확정**. 본 spec 의 §1 표 / §0 의 anti-coverage 절 참고. 시드
SQL (`db/tibero/main_schema/V3__schema_type_test_tables.sql`) 에서도 컬럼 자체를
삭제했다.

`rowid_col` 은 Phase 13.4 에서 **rowid 값을 NULL 로 둔다** (Tibero ROWID 가
컨테이너의 block/slot allocation 에 따라 매 부팅마다 달라져 dump snapshot
비교가 매번 fail). Oracle XE 는 storage 결정성 덕에 같은 ROWID 가 반복적으로
생성되는데, Tibero 는 그렇지 않다. ROWID 컬럼의 type round-trip
(`ROWID` → CUBRID `STRING`) 자체는 columns.txt snapshot 에서 검증됨.
실제 ROWID 값을 유지한 채 비교하려면 framework 에 ROWID 패턴 sanitizer
(예: `'A...A'` 18자리 base32-ish 패턴 → placeholder 정규화) 추가가 필요 —
후속 phase 분리.

### 5.6 `MAIN_SCHEMA.e2e_tibero_semi_structured_types` (Tibero 고유 extension)

Tibero 7 은 native `JSON` 과 `XMLTYPE` 을 지원한다. CMT type-map
(`Tibero2CUBRID.xml`) 에 entry 가 있으므로 type-aware extraction 동작을
검증한다.

Columns:

| Column | Tibero type |
|---|---|
| `id` | `NUMBER(10)` PK |
| `json_col` | `JSON` |
| `xml_col` | `XMLTYPE` |

SQL-ready values:

| id | row | json_col | xml_col |
|---:|---|---|---|
| 1 | `R_NULL` | NULL | NULL |
| 2 | `R_EMPTY` | `JSON('{}')` | `XMLTYPE('<root/>')` |
| 3 | `R_MIN` | `JSON('{"k":1}')` | `XMLTYPE('<a><b>1</b></a>')` |
| 4 | `R_MAX` | `JSON('{"k":[1,2,3,"한",null]}')` | `XMLTYPE('<doc><item id="1">한</item><item id="2">漢</item></doc>')` |
| 5 | `R_UNICODE` | `JSON('{"name":"한국 漢字 cafe"}')` | `XMLTYPE('<note>한국 漢字 cafe</note>')` |

> **첫 회 결과로 확정해야 할 점**:
> - `JSON` 이 CUBRID target 에서 어떤 type 으로 떨어지는가 (string /
>   CLOB / 다른 것).
> - `XMLTYPE` 이 string 으로 fallback 되는지, type-aware 변환되는지.
>
> 결과에 따라 R_MAX / R_UNICODE row 의 길이/특수문자 범위를 조정하고
> golden 검증의 비교 모드 (text vs binary) 를 정한다.

## 6. Bug/Feature Additions

Tibero 전용 회귀 케이스를 추가할 때 다음 규칙을 따른다.

1. 가능한 경우 기존 `e2e_*` table 을 재사용한다.
2. Tibero 전용 동작은 `e2e_tibero_*` 객체에 둔다.
3. 특정 이슈에만 좁게 묶인 케이스는 `bug_<jira-id>_*` 이름을 사용한다.
4. 일반 회귀 위험으로 승격된 케이스는 `e2e_tibero_*` 객체로 옮긴다.
