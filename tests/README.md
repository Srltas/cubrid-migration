# CMT Tests

CUBRID Migration Toolkit (CMT) 의 테스트 인프라. 두 개의 독립 Maven 모듈로 구성된다.

```
tests/
├── unit-test/   ← 단일 클래스/메서드 단위 검증 (밀리초)
└── e2e/         ← 실제 DB → migration.sh → 실제 DB round-trip 검증 (분 단위)
```

> 참고: 레포 루트의 `test/` 디렉토리 (Eclipse plugin testfragment) 는 **legacy 이며
> 사용하지 않는다**. 새 테스트는 모두 `tests/` 아래에 작성한다.

---

## 두 모듈의 책임

| 모듈 | 위치 | 검증 대상 | 환경 | 도구 |
|---|---|---|---|---|
| `unit-test` | `tests/unit-test/` | 단일 클래스/메서드 input → output | JVM only (DB / 파일시스템 / 외부 프로세스 없음) | JUnit 5 + AssertJ + Mockito |
| `e2e` | `tests/e2e/` | source DB → `migration.sh` → target DB 의 round-trip + CLI 동작 | Testcontainers + CMT Console 바이너리 | JUnit 5 + AssertJ + Testcontainers |

두 모듈 모두 **JUnit 5 + AssertJ** 기반으로 toolchain 일치. Mockito 는 unit 전용.

---

## Unit vs E2E 결정 규칙

### 한 줄 결정

> **"컨테이너 / 실제 DB / 외부 프로세스 없이 fail-then-pass 가능한가?"**
>
> - YES → **`unit-test`**
> - NO → **`e2e`**

### 카테고리별

| 검증 대상 | 어디로 | 근거 |
|---|---|---|
| `XxxDataTypeHelper`, `XxxSQLHelper` 의 변환 함수 | unit | input/output 직접 |
| `XxxSchemaFetcher.buildXxx` (JDBC ResultSet 사용) | unit (Mockito) | `mock(ResultSet.class)` + `when(...).thenReturn(...)` 로 시뮬레이션 |
| `Xxx2CUBRIDTransformHelper` 의 type/value 변환 | unit | mock object 입력 |
| `MigrationConfiguration.buildTableCfg` 같은 internal config 빌더 | unit | catalog 객체 + 함수 호출 |
| `XxxCommandHandler` 의 인자 파싱 / dispatch | unit | argList 입력 + 결과 검증 |
| `migration.sh` launcher / dispatch 자체 | e2e (smoke) | 실제 process 실행 필요 |
| migration 전체 흐름 (`start`, `script`, dump, online) | e2e (full) | 컨테이너 두 개 + 실제 JDBC |
| Type round-trip 정확성 (Oracle CLOB → CUBRID 데이터 손실 없음) | e2e | 실제 DB 두 개 사이에서만 검증 가능 |
| Migration report 통계 / 로그 형식 | e2e | `migration.sh` stdout 캡처 |
| Schema diff (script.xml) 정확성 | e2e | 실제 fetcher → script.xml 출력까지 |

### 회색 지대 — JDBCExporter paging 같은 함수

함수 자체는 mock 으로 검증 가능하지만 실제 DB quirks 에서 발생하는 버그가 더 많은 경우:

→ **둘 다**. Unit 으로 함수 logic 검증, E2E 로 round-trip 검증. 다른 시점에서 다른 회귀를 잡음.

이건 **critical / customer-visible 영역에만** 적용. 평범한 영역은 한 layer 로 충분.

### 버그 회귀 테스트는 어디로?

```
[버그 발견]
   ↓
"unit test 로 fail-then-pass 가능?"
   ├─ YES → unit-test 에 추가 (1순위)
   └─ NO  → e2e 에 bug_<jira_id>_* 시드 + assertion 추가

   추가로 customer-facing critical 버그면 → 둘 다
```

**E2E 의 `bug_<jira_id>_*` 시드 패턴은** "실제 DB interaction 없이는 재현 불가"한
버그에만 사용. 함수 단위로 잡을 수 있는 버그를 E2E 시드에 넣는 건 anti-pattern (시드 비대화 + iteration 느려짐).

---

## E2E 안에서 — 기존 TC 수정 vs 신규 TC 작성

### 4 차원 결정 규칙

| 차원 | 같으면 → | 다르면 → |
|---|---|---|
| ① Source DB | 기존 수정 | 신규 |
| ② Migration 모드 (online / dump file) | 기존 수정 | 신규 |
| ③ Migration 옵션 (`-do`, `-mm`, `-tp` 등) | 기존 수정 | 신규 |
| ④ 시드 라이프사이클 (compatible / 격리 필요) | 기존 수정 | 신규 |

**4 차원 모두 같으면 기존 클래스 수정, 한 차원이라도 다르면 신규 클래스.**

### 시나리오 예시

| 시나리오 | 결정 |
|---|---|
| Oracle 시드에 `BINARY_DOUBLE` 컬럼 추가 검증 | 기존 `OracleToCubridTest` 수정 |
| Oracle 시드에 view 1 개 추가 | 기존 `OracleToCubridTest` 수정 |
| 특정 jira 버그 회귀 (기존 컨테이너 라이프사이클로 재현 가능) | 기존 + `bug_<jira>_*` 시드 |
| Oracle → CUBRID **dump file** 검증 | 신규 `OracleToDumpTest` (모드 다름) |
| Oracle → CUBRID **`-do yes`** 옵션 검증 | 신규 `OracleToCubridDataOnlyTest` (옵션 다름) |
| Oracle **19c** 호환성 (현재 11g XE) | 신규 (image 다름) |
| **큰 데이터셋 (1M rows)** 마이그레이션 | 신규 (시드 라이프사이클 격리) |
| 다른 timezone 환경 검증 | 신규 (옵션/환경 다름) |

### 한 줄 가이드

> **기존 시드 + 기존 컨테이너 라이프사이클로 검증 가능하면 기존 클래스 수정.**
> **격리가 필요하면 신규.**

---

## 새 source DB 추가 — 단계별

(현재 Oracle / CUBRID 활성. 나머지 source DB 는 PoC tag `poc-final`
에 동결되어 있으며 v2 branch 에서는 사용하지 않는다. 새 DB 추가 시 동일 흐름.)

1. CMT plugin 분석 — fetcher 의 anti-coverage 식별 (`buildSynonym` / `buildGrant` / 등 미구현 부분)
2. `tests/e2e/docs/seed/<dbname>/SEED_SPEC.md` 작성 — 시드 설계 + anti-coverage 표
3. `tests/e2e/src/test/resources/db/<dbname>/{init,main_schema}/V*.sql` 시드 SQL
4. `tests/e2e/src/test/java/com/cmt/e2e/framework/db/containers/<XxxContainer>.java` + `init/<XxxDatabaseInitializer>.java`
5. `JdbcDriverJars.DB.<XXX>` enum + `pom.xml` JDBC dependency
6. `framework/source/<XxxSource>.java` + `Sources.xxxE2eSeed()` 팩토리
7. 테스트 클래스 (`<DbName>To{Cubrid,Dump}Test.java`) — `AbstractMigrationE2E` 상속
8. `tests/e2e/src/test/resources/queries/<scenario>.sql` (representative SELECTs)
9. 첫 capture: `mvn -Dsnapshot.update=true test -Dtest=<DbName>ToCubridTest`
10. snapshot 파일 review → SEED_SPEC tentative 항목 확정

---

## 실행 방법

### Unit tests

```bash
cd tests/unit-test
mvn test
```

DB / 컨테이너 불필요. 빠름 (전체 < 1 분 예상).

### E2E tests

```bash
# 사전: CMT Console 바이너리 빌드
cd <repo-root>
./build.sh -p c
mkdir -p cmt-console
tar xzf target/CUBRID-Migration-Toolkit-console-*.tar.gz \
    --strip-components=1 -C cmt-console

# E2E 실행 (Apple Silicon 은 docker compose 권장 — bundled JRE 가 amd64)
export CMT_CONSOLE_HOME=<repo-root>/cmt-console
cd tests/e2e
docker compose run --rm e2e-test mvn test
```

특정 시나리오만:

```bash
docker compose run --rm e2e-test mvn test -Dtest=OracleToCubridTest
docker compose run --rm e2e-test mvn test -Dtest=CliSmokeTest
```

Snapshot 캡처 / 갱신 (CMT 출력 변경을 의도적으로 받아들일 때):

```bash
docker compose run --rm e2e-test mvn test \
    -Dtest='OracleToCubridTest,CubridToCubridTest' \
    -Dsnapshot.update=true
```

자세한 내용은 `tests/e2e/README.md` + `tests/e2e/docs/ARCHITECTURE.md`.

---

## CI

GitHub Actions (`.github/workflows/e2e-test.yml`):

- **Trigger**: `push` (develop, release/**) / `pull_request` / nightly cron (02:00 UTC)
- **실행**: 모든 active 테스트 (smoke + Oracle/CUBRID migration)
- **피드백**: PR 코멘트 (실패 시만), check_run annotation, surefire reports artifact, e2e failure artifact

`unit-test` 모듈은 현재 GHA workflow 에 명시 wiring 안 되어 있음 — 향후 분리 stage 로 추가 권장.

---

## 더 자세한 문서

- 시드 설계 전반: `tests/e2e/docs/SEED_DATA_GUIDE.md`
- 공통 시드 contract: `tests/e2e/docs/seed/COMMON_SEED_CONTRACT.md`
- DB 별 시드 명세: `tests/e2e/docs/seed/<dbname>/SEED_SPEC.md`
- DEFERRED 상태 정보: 각 SEED_SPEC 의 §0 섹션 참고
