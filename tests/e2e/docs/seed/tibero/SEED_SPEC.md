# Tibero Seed Specification

> ## ⚠ 0. 현재 상태: 보류 (Deferred)
>
> **Tibero E2E 테스트는 현재 띄울 수 없는 상태이며, 이 spec 만 만들어 두고
> 보류한다.** 시드 SQL / Container / Initializer 등 실제 코드 자산은
> 라이선스 인프라가 갖춰진 뒤에 만든다.
>
> ### 왜 보류했는가 (쉽게)
>
> Tibero 는 **컨테이너를 띄우는 것 자체가 자동화 환경에서 어렵다**.
> 두 가지 이유:
>
> #### 1. 라이선스 파일이 있어야 부팅이 된다
>
> Tibero 는 무료 / 오픈 라이선스가 없다. 사용하려면 다음 중 하나가 필요하다:
>
> - **30 일 데모 라이선스** — TmaxSoft TechNet 에 회원가입하고 신청서 제출 후
>   사람이 발급
> - **유료 라이선스** — 회사 단위 구매
>
> 그리고 발급받은 `license.xml` 안에는 사용자가 신청 시 입력한 **hostname
> 이 박혀 있다**. 컨테이너의 hostname 과 정확히 똑같지 않으면 Tibero 는
> "이 라이선스는 다른 컴퓨터 거다" 라고 판단해서 **부팅 자체를 거부**한다.
>
> 그런데 우리의 테스트 환경 (Testcontainers) 은 매번 컨테이너를 새로 만들 때
> 임의의 hostname (`a3f2b1...` 같은 hash) 을 붙인다. 그러면 license.xml 의
> hostname 과 절대 일치할 수 없다 → 매번 부팅 실패.
>
> 우회는 가능하다: 코드에서 `withHostName("cmt-e2e-tibero")` 처럼 고정
> hostname 을 강제하고, 그 hostname 으로 미리 발급받은 license.xml 을
> 컨테이너 안에 마운트해 주면 된다. 다만 이건 **사람이 한 번 TechNet 에
> 가서 받아온 license 파일이 CI runner 에 깔려 있다**는 전제가 있어야
> 한다. 그리고 30 일마다 사람이 다시 받아와서 갱신해 줘야 한다.
>
> #### 2. JDBC 드라이버가 Maven Central 에 없다
>
> 다른 DB (Oracle, MySQL, MariaDB, MSSQL, CUBRID) 의 JDBC 드라이버는
> 모두 Maven Central 또는 공개 repository 에서 받을 수 있다. pom.xml 에
> dependency 한 줄 적으면 끝.
>
> Tibero 의 `tibero7-jdbc.jar` 은 **Maven 에 안 올라와 있다**. 받는 방법은
> 둘 중 하나:
>
> - Tibero 컨테이너를 띄워 놓고 `docker cp` 로 jar 를 빼낸다 (그런데 컨테이너를
>   띄우려면 라이선스가 있어야 한다 — 이유 1 의 닭-달걀 문제)
> - TmaxSoft TechNet 에서 라이선스와 함께 jar 를 다운로드한다
>
> 어느 쪽이든 **사람이 한 번 받아와서 빌드 환경의 로컬 Maven repository
> 에 등록해 둬야** (`mvn install:install-file`) 된다.
>
> ### 다른 source DB 는 왜 안 막혔는가
>
> | DB | 이미지 라이선스 | JDBC |
> |---|---|---|
> | CUBRID | 오픈, 무료 | Maven Central |
> | Oracle XE | 무료 (Express Edition) | Maven Central |
> | MySQL | 오픈 (GPL) | Maven Central |
> | MariaDB | 오픈 (GPL) | Maven Central |
> | MSSQL Developer | 무료 (개발용) | Maven Central |
> | **Tibero** | **유료/30일 데모, hostname 바인딩** | **Maven Central 미배포** |
>
> Tibero 만 두 가지 모두에서 "사람의 사전 작업"이 필요하다.
>
> ### 보류 해제 조건 (둘 다 충족돼야 진행 가능)
>
> 1. **고정 hostname 으로 발급된 Tibero 데모 라이선스** 가 CI runner 에
>    배포되어 있고, env (예: `TIBERO_LICENSE_PATH`) 로 위치가 전달된다.
>    30 일 갱신 절차도 정해져 있어야 한다.
> 2. **`tibero7-jdbc.jar` 이 빌드 환경의 로컬 Maven repository 에 등록**
>    되어 있다 (`com.tmax.tibero:tibero-jdbc:7` 같은 GAV; 정확한 좌표는
>    첫 회 진행 시 확정).
>
> 위 두 가지가 갖춰지면, 그 시점부터는 다른 source DB 와 동일한 패턴으로
> 진행한다 (시드 SQL → Container/Initializer → Drivers/pom.xml →
> RegenerateScripts → 테스트/golden).
>
> ### 지금까지 만들어 둔 것
>
> | 항목 | 상태 |
> |---|---|
> | 본 SPEC | 작성 완료 — Oracle SPEC 미러링 + Tibero 고유 항목 (native JSON, XMLTYPE) extension 분리, tentative 항목 5개 명시 |
> | `db/tibero/{init,ref_schema,main_schema}/*` 시드 SQL | **없음** — 라이선스 없이 실제 검증을 못 하므로 보류 해제 후 작성 |
> | `TiberoContainer.java` / `TiberoDatabaseInitializer.java` | **없음** — 동일 사유 |
> | `Drivers.TIBERO` / `pom.xml` JDBC dependency | **없음** — 동일 사유 |
> | `RegenerateScripts` 시나리오 / 테스트 / golden | **없음** — 동일 사유 |
>
> MSSQL 과 다르게 시드 SQL 도 만들지 않은 이유: MSSQL 은 **컨테이너는
> 정상적으로 띄워져 Flyway 적용까지 검증** 가능했고 (CMT 의 TLS 처리만
> 막힌 상태), Tibero 는 **컨테이너 자체가 안 떠서** Flyway 가 적용되는
> 모양조차 확인할 수 없다. 라이선스 / 드라이버 두 인프라 모두 갖춰지는
> 시점에 시드 SQL 을 한 번에 작성/검증한다.
>
> ---

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
| `UROWID` | TiberoDataTypeHelper 가 ROWID 만 명시 — UROWID 는 첫 회 결과로 확정 (TENTATIVE) |
| Function-based / Domain Index | Oracle 에서는 `idxf_*` 시도했으나 Tibero 의 fetcher 동작이 동일한지는 첫 회 결과로 확정 (TENTATIVE) |

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

- `idxf_e2e_order_upper_status` 는 Oracle 과 동일 syntax 로 시도하지만
  Tibero 의 fetcher / parser 가 동일하게 처리하는지 첫 회 결과로 확정
  (TENTATIVE — anti-coverage 또는 살아남는지 결정).
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

`UROWID` 컬럼은 첫 회 결과로 살릴지 anti-coverage 로 옮길지 확정 (TENTATIVE).

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
