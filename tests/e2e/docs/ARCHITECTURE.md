# CMT Console E2E — Architecture (v2)

> **Status:** Phase 0 (skeleton). Filled in as later phases land.
> **Branch:** `e2e-v2` from PoC tag `poc-final`.

This document is the source of truth for the v2 E2E framework. It captures
the design intent, the entry points, and the working agreements that
follow-up engineers should not break without explicit discussion.

---

## 0. Goals & non-goals

### Goals
1. **TC clarity.** A new engineer can read a single test class and, in
   under 30 seconds, list every fact the class verifies.
2. **CMT-change flexibility.** When CMT changes its output (bug fix,
   feature add, naming convention shift), updating the tests should be a
   *data* change (snapshot file diff) rather than a *code* change
   (multi-line Java edit).

These two goals drive every other decision in this document.

### Non-goals
- Generic database test framework. v2 is specific to CMT Console.
- Performance tests. v2 is correctness-only.
- Coverage of every CMT scenario. v2 starts with Oracle and CUBRID;
  other source DBs ship in PoC branch (frozen — see §3 D4).

---

## 1. Architectural decisions (locked in Phase 0)

| ID | Decision | Choice | Rationale |
|----|----------|--------|-----------|
| **D1** | PoC vs v2 coexistence | New `e2e-v2` branch from PoC tag `poc-final`. PoC branch frozen. | Avoids mixed code review. PoC stays available for cherry-pick. |
| **D2** | Snapshot file format | Aligned table text (`OWNER \| TABLE \| ...`). Framework pads columns. | Best human readability in PR diff. TSV/CSV/JSON loses for review. |
| **D3** | `script.xml` provisioning | `Migration.run()` generates it dynamically per test using `migration.sh script`. No checked-in fixtures. | Removes 12 fixture XML files + RegenerateScripts (~970 LOC). Couples tests to CMT's `script` subcommand contract — that coupling is intentional. |
| **D4** | DEFERRED DBs (MySQL/MariaDB/MSSQL/Informix) | Frozen on PoC branch (`tag poc-final`). Not imported into v2. | Per user direction — v2 ships with active scenarios only. Re-activation would cherry-pick from PoC. |

These four decisions are not up for reversal mid-implementation. If a
phase reveals one of them was wrong, the corresponding ADR (added to
this doc) records the override and the cost.

---

## 2. The two principles

### Principle 1 — TC declares, framework acts

A test method should read like a sentence:

```java
@Test
void serial_current_value_preserved() {
    run().catalog().matchesSnapshot("serials");
}
```

If a test method contains an `if`, a `for`, a cast, a 7-arg factory, a
JDBC SQL string, or a `for` loop building expectations — the framework
is missing an abstraction.

### Principle 2 — Snapshot first, rules second

Two complementary verification styles:

- **Snapshot:** capture CMT output as a human-readable text table; diff
  against checked-in golden. Catches "something changed" with surgical
  precision and PR-friendly review.
- **Rule:** assert structural invariants that don't depend on naming
  ("every FK points to a valid PK", "every migrated table has the same
  row count as source"). Catches semantic regressions even when names
  shift.

Most v2 tests are snapshot-based. Rules cover correctness invariants
that snapshots can miss (e.g., FK target existence).

---

## 3. The four verification layers

Each test method maps to exactly one layer.

| Layer | Question | Failure means | Cost |
|-------|----------|---------------|------|
| **L1 Smoke** | Did `migration.sh` exit 0 with no fatal stderr? | CMT itself broke | instant |
| **L2 Coverage** | Are all expected objects present in the target? | CMT dropped something | fast |
| **L3 Fidelity** | Are values & metadata correct? | CMT mistranslated | medium |
| **L4 Regression** | Does a known bug stay fixed? | Regression of a tracked issue | fast |

A `@Test` mixing layers (e.g., row counts + index names + view body) is
a refactoring target.

---

## 4. Directory layout (target)

```
tests/e2e/
├── docs/
│   ├── ARCHITECTURE.md                  ← this file
│   └── seed/                            ← inherited from PoC (asset)
├── src/test/java/com/cmt/e2e/
│   ├── framework/
│   │   ├── env/                         ← CMT_CONSOLE_HOME validation
│   │   ├── source/                      ← Source + container + seed
│   │   ├── target/                      ← Target = CUBRID online | dump
│   │   ├── runner/                      ← Migration + ScriptXmlBuilder
│   │   ├── verify/                      ← SnapshotStore + CatalogQueries
│   │   └── junit/                       ← AbstractMigrationE2E lifecycle
│   └── tests/
│       ├── cli/CliSmokeTest.java
│       └── migration/
│           ├── oracle/{ToCubrid,ToDump}Test.java
│           └── cubrid/{ToCubrid,ToDump}Test.java
└── src/test/resources/
    ├── seed/                            ← inherited (Flyway V*.sql)
    ├── snapshots/<scenario>/<query>.txt ← NEW (golden text tables)
    └── queries/<scenario>.sql           ← NEW (representative SELECTs)
```

---

## 5. The five core abstractions

Detailed signatures land in their respective phase. Skeleton:

### `Source` — container + seed
Owns the source DB lifecycle. Hides Flyway, container start, init SQL.
Exposes only `connection()` (a `ConnectionConfig`) and `start()/close()`.

### `Target` — CUBRID online or dump file
Same lifecycle pattern. `isDumpfile()` selects between online and
dump-file. Online targets expose a JDBC connection; dump targets expose
the output directory.

### `Migration` — CMT runner
Builds `db.conf` from Source+Target → invokes `migration.sh script` to
generate `script.xml` → invokes `migration.sh start` with that script →
returns `MigrationOutcome`. One run per test class (`@BeforeAll`).

### `MigrationOutcome` — fluent verification entry
```
run().expectSuccess()                     // L1
     .catalog().matchesSnapshot("...")    // L2 / L3
     .rowCounts().matchesSnapshot("...")  // L3
     .query("SELECT ...").firstColumn()   // L4 regression
     .dumpfile().matchesSnapshot("...")   // dump-target verification
```

### `SnapshotStore` — diff & regen
```
SnapshotStore.match(path, actualText)
```
Default: diff against checked-in file → throw on mismatch.
With `-Dsnapshot.update=true`: write actual to file (no diff).
CI runs without that flag (regen is a developer/PR action).

---

## 6. Test class shape (canonical)

```java
@MigrationE2E(name = "oracle_to_cubrid")
public class OracleToCubridTest extends AbstractMigrationE2E {

    @Override protected Source source() { return Sources.oracleE2eSeed(); }
    @Override protected Target target() { return Targets.cubridOnline(); }

    @Test @DisplayName("CMT exits 0 with MIGRATION RESULT: SUCCESS")
    void migrationSucceeds() { run().expectSuccess(); }

    @Test @DisplayName("All user objects appear in target catalog")
    void allObjectsMigrated() {
        var c = run().catalog();
        c.matchesSnapshot("classes");
        c.matchesSnapshot("synonyms");
        c.matchesSnapshot("routines");
    }

    @Test @DisplayName("Row counts per table match source")
    void rowCountsMatch() {
        run().rowCounts("MAIN_SCHEMA", "REF_SCHEMA").matchesSnapshot("row_counts");
    }

    // ... one @Test per fact, one fact per layer
}
```

Lifecycle:
- `@TestInstance(PER_CLASS)` (set by `AbstractMigrationE2E`)
- `@BeforeAll`: source.start(), target.start(), migration.run() → cache outcome
- All `@Test` methods read the cached outcome (no extra container or CMT runs)
- `@AfterAll`: close in reverse order

---

## 7. Snapshot file conventions

### Format (D2: aligned table)
```
COLUMN_A    | COLUMN_B  | COLUMN_C
------------+-----------+---------
value-a-1   | value-b-1 | value-c-1
value-a-2   | value-b-2 | value-c-2
```

- Header line.
- Separator line with `+` at column boundaries, `-` filling each cell.
- Column widths = max(header, all values) + 2 padding spaces.
- NULL values represented as `<NULL>` (matches PoC `DatabaseAsserts`).
- `\n` line terminator (no `\r\n` regardless of OS).
- Trailing newline at EOF.

### Filename
`snapshots/<scenario>/<concern>.txt`, e.g.
`snapshots/oracle_to_cubrid/indexes.txt`.

### Determinism contract
The framework guarantees that snapshot output is deterministic:
- All catalog queries have explicit `ORDER BY`.
- Migration metadata fields with timestamps are normalized
  (`wizard_start_date_time`, generated `migration name` suffixes).
- Locale-dependent collation is pinned to the container's default UTF-8.

If a snapshot diff appears spurious, the determinism contract is
broken — fix the source, not the snapshot.

### Update flow
```
mvn test                         # default — diff mode, fails on mismatch
mvn -Dsnapshot.update=true test  # regen mode — overwrites snapshot files
```
After regen, the developer reviews the file diff in their PR and
explains the change in the commit message.

---

## 8. CI / local invocation

### Local — preferred (docker compose)

The CMT Console binary ships a Linux x86_64 JRE in
`$CMT_CONSOLE_HOME/jre/`. Apple Silicon hosts cannot execute it
directly; run the test JVM in the amd64 `e2e-test` service so
`migration.sh` works. (Linux x86_64 hosts may invoke `mvn` directly.)

```bash
export CMT_CONSOLE_HOME=/path/to/cmt-console

# Full v2 suite — regression mode (default)
docker compose run --rm e2e-test mvn test

# A single scenario
docker compose run --rm e2e-test mvn test -Dtest=OracleToCubridTest

# A single fact within a scenario
docker compose run --rm e2e-test mvn test \
    -Dtest='OracleToCubridTest#serials_match_snapshot'

# Snapshot capture / refresh (use only when CMT output legitimately
# changes — review the resulting file diff in your PR)
docker compose run --rm e2e-test mvn test \
    -Dtest='OracleToCubridTest,CubridToCubridTest' \
    -Dsnapshot.update=true
```

### CI — GitHub Actions

`.github/workflows/e2e-test.yml` runs on push to `develop` /
`release/**`, on every PR, on a 02:00 UTC nightly cron, and via
`workflow_dispatch`.

- Trigger → checkout → build CMT Console (`./build.sh -p c`) →
  extract → `mvn -B clean test` against the v2 suite.
- `JAVA_TOOL_OPTIONS=-Doracle.jdbc.timezoneAsRegion=false` — Oracle
  11g XE timezone-region workaround (ORA-01882). Inherited by every
  child JVM including `migration.sh`.
- `comment_mode: failures` — PR conversation stays clean on green
  runs; only failures attract a comment.
- Failure artifacts (zip):
  - `tests/e2e/target/e2e/<Class>/<method>/test.log` — Logback
    SiftingAppender per-test log (CMT stdout/stderr + framework
    debug, routed via the `testId` MDC key).
  - `tests/e2e/target/e2e-v2/<scenario>/{script.xml, raw/}` — the v2
    runner's per-scenario working dir: the dynamically generated
    sanitized `script.xml` plus the raw CMT output that fed into it.

`-Dsnapshot.update=true` is **never** passed in CI. Capturing /
refreshing snapshots is a deliberate developer action against a local
checkout, reviewed in the resulting PR diff.

### Update workflow (when CMT output legitimately changes)

1. Identify which scenario(s) need fresh snapshots.
2. Run capture mode locally:
   ```bash
   docker compose run --rm e2e-test mvn test \
       -Dtest='<Scenario>Test' \
       -Dsnapshot.update=true
   ```
3. Review the snapshot file diff (`git diff src/test/resources/snapshots/`).
   Each line of catalog/data change should map to a real CMT
   behavioural change you can articulate.
4. Rerun **without** `-Dsnapshot.update` to confirm the diff is
   deterministic:
   ```bash
   docker compose run --rm e2e-test mvn test -Dtest='<Scenario>Test'
   ```
5. Commit with a message that explains the upstream CMT change.

---

## 9. ADR log

| ID | Phase | Date | Decision | Notes |
|----|-------|------|----------|-------|
| D1 | 0 | 2026-04-29 | New `e2e-v2` branch from `poc-final`; PoC frozen | §1 |
| D2 | 0 | 2026-04-29 | Aligned-table snapshot format | §1, §7 |
| D3 | 0 | 2026-04-29 | `script.xml` generated dynamically per test | §1 |
| D4 | 0 | 2026-04-29 | DEFERRED DBs frozen on PoC, not imported | §1 |
| D5 | 4.4 | 2026-04-29 | `WorkspaceCleaner.cleanupOutput()` runs in `@BeforeAll` to defeat CMT's append-mode dump output between class runs | §10 Phase 4.4 |
| D6 | 4.4 | 2026-04-29 | `flyway_schema_history` is a seed implementation detail, not part of the migration contract — strip from generated `script.xml` and from catalog/RowCounts queries | §10 Phase 4.4 |
| D7 | 5.2 | 2026-04-29 | CUBRID system schemas (`DBA`, `PUBLIC`) auto-introspected by `dba` connect — stripped from `<schemas>` in generated `script.xml` | CUBRID source only |
| D8 | 5.2 | 2026-04-29 | CUBRID `e2e_cubrid_collection_types` (SET/LIST/SEQUENCE) is anti-coverage — CMT cannot round-trip CUBRID collection columns. Excluded at script-generation time | docs/seed/cubrid/SEED_SPEC.md anti-coverage |
| D9 | 5.2 | 2026-04-29 | CUBRID functional indexes (`idxf_*`) are anti-coverage — `CUBRIDSchemaFetcher` does not emit the function expression. Excluded at script-generation time | CUBRID source only |
| D10 | 5.3 | 2026-04-29 | CUBRID dump uses `one_table_one_file=true`. Single-file mode is non-deterministic (CMT does not stabilise the table order in the combined `_object` file) | overrides PoC default for CUBRID |
| D11 | 8.1 | 2026-04-29 | `CliSmokeTest` → `CliTest`. The class is naming-inconsistent with the other `*Test` migration TCs and its 8 cases are functional (dispatch + first-run filesystem) rather than mere smoke | also `CLI-SMOKE-NN` → `CLI-NN` IDs |
| D12 | 8.2 | 2026-04-29 | Framework unit tests for self-evident logic (`SnapshotStore`, `DbConfBuilder`, `MigrationOutcome`, `Tabulator`, `RowQueries.parse`) dropped — their failures surface in the integration suite without delay. `ScriptXmlBuilderTest` retained because sanitize regex bugs are silent (a corrupted `script.xml` still runs to completion with garbage output, so the explicit safety net carries its weight) | -30 unit `@Test` cases; framework smoke `MigrationRunnerSmokeIT` also dropped (redundant with active migration TCs) |
| D13 | 9 | 2026-04-29 | `ScriptXmlBuilderTest` also dropped. The e2e module is for end-to-end tests; framework-internal unit tests do not belong here. Sanitize regex regressions are caught at the next snapshot capture / regression run (snapshot diff fails when sanitize misbehaves). Visibility narrowed: `sanitize` and `extractMigrationName` are now `private static` | overrides D12 — supersedes the safety-net argument; the e2e module's purity outweighs the marginal earlier detection |
| D14 | 10 | 2026-04-30 | Naming convention for scenario id, test class, package, DisplayName + `@MigrationE2E.options[]` declarative metadata. **No "default" TC** — every TC explicitly declares the CMT options that affect its verification (§12.0). Variants for a source/target shape live as `@Nested` inner classes under one outer namespace class; flat classes are used only when there are no variants. `regression/` is the only sub-package. Target tokens align 1:1 with `MigrationConfiguration.DEST_*` (`cubrid` / `unload` / future `csv`/`sql`/`xls`) | §12; the outer+`@Nested` structure keeps class-name length bounded while making every variant's full options explicit at its own annotation |
| D15 | 11 | 2026-04-30 | **Coverage Matrix work deferred.** Two research agents enumerated CMT's capability surface (Oracle 28 types / CUBRID 29 types / 16 object kinds / ~23 migration options / 4 console subcommands) and our E2E coverage at point-in-time. The 5-sub-matrix design and current cell values (✓50/✗10/⊘2/—2 for objects; 3/19 options explicitly declared) are preserved in `docs/COVERAGE_RESEARCH.md`. Build cost ~1 sprint, maintenance non-trivial — other priorities first. Five concrete gaps (G1–G5) actionable independently of the matrix tooling | `docs/COVERAGE_RESEARCH.md` is the resumption starter pack. Recommended path on resume: Option B (YAML-mediated semi-auto) at `tools/coverage-matrix/`, not full auto-generator |
| D16 | 12 | 2026-05-01 | **Tibero source DB activated.** Custom `faketime-tibero:2026-fixed` image (`tiberoofficial/tibero:7.2.4` + `libfaketime` `LD_PRELOAD` `FAKETIME=-99d`) bypasses the 30-day trial license window. Hostname-bound license file mounted at runtime via `withCopyFileToContainer` (license file is `tibero-3-100`-bound; container hostname forced via `withCreateContainerCmdModifier` to match). `tibero7-jdbc-17.jar` committed to `tests/e2e/lib/` (system-scope dep) since Maven Central has no Tibero JDBC. **Raw-JDBC seed application** via `ClasspathSqlRunner` because Flyway has no Tibero plugin (community or vendor). Phase 3 lands 11 ON `@Test` + 2 unload `@Test` (= 13 new active tests); routines/representative_rows/V3 type-test tables deferred to follow-up batches | §12 / `tests/e2e/lib/README.md` / `tests/e2e/tibero/README.md`. Functional index (`idxf_*`) round-trip confirmed working — TENTATIVE in `seed/tibero/SEED_SPEC.md` flipped to confirmed-supported |

Mid-implementation overrides land here as new rows.

---

## 10. Phase roadmap

| Phase | Deliverable | Status |
|-------|-------------|--------|
| 0 | Branch + ARCHITECTURE skeleton | ✅ done |
| 1 | `framework/env`, `source`, `target`, `junit` (lifecycle) | ✅ done |
| 2 | `framework/runner` — Migration + ScriptXmlBuilder | ✅ done |
| 3 | `framework/verify` — SnapshotStore + CatalogQueries + Tabulator | ✅ done |
| 4 | Oracle TC ×2 + first snapshot capture | ✅ done |
| 5 | CUBRID TC ×2 + first snapshot capture | ✅ done |
| 6 | PoC asset cleanup (template/, RegenerateScripts, fixtures) | ✅ done |
| 7 | CI + local mvn profiles + final docs | ✅ done |
| 10 | Naming convention (D14, §12) — explicit-options-per-TC, no defaults; `_to_dumpfile` → `_to_dump` rename; `regression/` sub-package marker only | ✅ done |
| 11 | Coverage Matrix (D15) — capability-surface enumeration + 5-sub-matrix design preserved in `docs/COVERAGE_RESEARCH.md` | ⏸ deferred — resume per resumption checklist |
| 12 | Tibero source DB activation (D16) — custom faketime image, system-scope JDBC jar, raw-JDBC seed via `ClasspathSqlRunner`, 11 ON + 2 unload `@Test` | ✅ done — follow-up batches: V3 type-test tables, V4 PL/SQL routines (needs PL/SQL-aware applier), representative-rows queries |

Each phase ships as one or more commits on `e2e-v2`. Cutover to a PR
against the integration branch happens after Phase 6 passes
acceptance.

---

## 11. Acceptance criteria (end-state)

A v2 release is "done" when all hold. Status as of Phase 7:

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | **Readability** — colleague lists every fact in `OracleToCubridTest` within 30 s | ✅ | 9 `@Test` methods × 1–3 LOC each, each with a domain `@DisplayName`. Total class ≈ 95 LOC. |
| 2 | **Flexibility** — CMT-output change absorbed via snapshot diff, zero Java | ✅ | Validated during Phase 4.4 (flyway sanitize added → catalog/indexes/row_counts snapshots updated; only `ScriptXmlBuilder.sanitize` regex was Java code change, all per-test expectations were data-only). |
| 3 | **Speed** — single class runs Oracle < 5 min, CUBRID < 2 min | ✅ | Oracle ON 65 s, Oracle DO 70 s, CUBRID ON 28 s, CUBRID DO 30 s on Apple Silicon (amd64 emulation). |
| 4 | **Active test count** — ≥ 32 migration + 8 smoke | ✅ | 43 migration `@Test` (13 ON oracle + 2 unload-oracle.SplitPerTable + 13 ON cubrid + 2 unload-cubrid.SplitPerTable + 11 ON tibero + 2 unload-tibero.SplitPerTable) + 8 `CliTest` = **51 active**. Zero `@Disabled` in `tests/`. (Tibero ON has 11 instead of 13 because routines + representative_rows are deferred until V4 PL/SQL adapter and queries file land — V3 type-test tables also deferred.) |
| 5 | **LOC** — v2 ≤ 3,800 production Java | ⚠️ | 4,333 production Java in `framework/`. Slight overshoot — driven by anti-coverage sanitize comments (D6–D9) and explicit fluent API choices. Including the migration tests: 4,704 (vs. PoC `poc-final` ≈ 6,276 → **−25 %**). |

The LOC overshoot on (5) is intentional — every additional line over
the original 3,800 estimate ships as comment / regex / single-purpose
sanitize for a documented CMT quirk, not generic abstraction. We
prefer that to under-documented brevity.

---

## 12. Naming convention (TC / scenario / package)

Adopted in Phase 10 (ADR D14). Every new TC, every scenario id, and
every snapshot directory follows this section; PR review rejects
deviations.

### 12.0 Guiding principle — no implicit defaults

There is **no "default" TC**. Every migration TC explicitly declares
the CMT options that affect its observable output. A TC's identity is
the **set of options it has chosen** — not "what's left when the TC
takes nothing." This rule comes from a concrete failure mode: CMT can
change its built-in defaults across releases. If a TC silently leans
on those defaults, it silently changes meaning when CMT does — the
snapshot diff gets blamed on the test instead of the upstream change.

Practical consequences:
- No "core vs variant" framing. Every TC is a peer.
- Every TC's `@MigrationE2E.options[]` lists the option values that
  matter for *its* verification. If the test reads from a dump file,
  it lists every option that affects dump structure. If it queries a
  catalog, it lists every option that affects catalog content. If
  there's nothing to declare (e.g. an online migration with no
  configuration knobs being toggled), `options = {}` is accurate.
- `target()` (and where applicable `source()`) sets those same option
  values explicitly. The annotation describes; `target()` enacts. They
  must agree — review catches drift.

### 12.1 Three identifying dimensions

A scenario is identified by these three dimensions. Class name,
scenario id, snapshot dir, and `DisplayName` prefix are all derived
from them.

| Dimension | Values (current / planned) | Notes |
|-----------|---------------------------|-------|
| **Source** | `oracle`, `cubrid`, `tibero`, `postgres`(future), DEFERRED: `mysql`,`mariadb`,`mssql`,`informix` | Origin DB. |
| **Target** | `cubrid`, `unload`, future `csv`/`sql`/`xls` | Aligned 1:1 with `MigrationConfiguration.DEST_*` enum. `cubrid` = `DEST_ONLINE`; `unload` = `DEST_DB_UNLOAD` (matches `setDestTypeName("unload")`). Naming the specific format avoids the implicit "dump" default — when CSV/SQL/XLS land they sit alongside as peers. |
| **Discriminator** | (omitted), `flat`, `per_table`, `bug_cmt_1234`, … | What distinguishes this TC from siblings sharing the same source/target. Free-form snake_case noun phrase that names the TC's *observable behaviour* or its *purpose* (regression). Omitted when there's only one TC for the source/target pair. |

Layer (L1 / L2 / L3 / L4) is orthogonal — every `@Test` declares one
layer in its `@DisplayName`, but it does not enter the scenario
identifier (§3).

### 12.2 Scenario id grammar (`@MigrationE2E.name`)

```
<source>_to_<target>[__<discriminator>]
```

Rules:
- Dimension boundary: `__` (double underscore).
- Word boundary inside a token: `_` (single).
- The discriminator is a snake_case noun phrase describing the TC's
  observable behaviour (what makes it distinct from siblings). Do not
  encode literal flag values like `_true`/`_false`/`_yes`/`_no`/
  `_on`/`_off` — those make the test depend on the *flag* rather than
  the *behaviour*, and read awkwardly when promoted to PascalCase.
  Prefer behaviour names: `flat`, `per_table`, `merged_files`,
  `with_triggers`, …
- Bug fixtures use `bug_<lowercase Jira id with `-` → `_`>`:
  `CMT-1234` → `bug_cmt_1234`.

| Scenario id | Class (outer.Inner if `@Nested`) | Reading |
|-------------|----------------------------------|---------|
| `oracle_to_cubrid` | `OracleToCubridTest` | only Oracle → CUBRID online TC (no variants) |
| `oracle_to_unload__split_per_table` | `OracleToUnloadTest.SplitPerTable` | Oracle → unload, split_schema=true + one_table_one_file=true |
| `oracle_to_unload__flat_merged` | `OracleToUnloadTest.FlatMerged` | future variant — split=false, otof=false |
| `oracle_to_csv__per_table` | `OracleToCsvTest.PerTable` | future — `DEST_CSV` shape, per-table variant |
| `oracle_to_cubrid__bug_cmt_1234` | `OracleToCubridBugCmt1234Test` | regression for CMT-1234 (in `regression/`) |
| `postgres_to_cubrid` | `PostgresToCubridTest` | future source DB |

When a second TC lands for an existing source/target pair, **both**
TCs gain a discriminator (the originally unsuffixed one is renamed if
its identity is no longer obvious). This is a deliberate one-time
migration cost — the alternative (leaving the first one as
`<source>_to_<target>` while siblings get suffixes) creates an
implicit "this one is special" that contradicts §12.0.

The scenario id is the **single source of truth**. It drives:
- snapshot directory: `src/test/resources/snapshots/<id>/`
- queries file (when applicable): `src/test/resources/queries/<id>.sql`
- v2 runner working dir: `target/e2e-v2/<id>/`
- log MDC `testId` and per-class log filename

### 12.3 Class structure (outer + `@Nested` inner)

The class layout depends on whether the source/target shape carries
option variants:

**Single-shape, no variants** — flat top-level class:
```
<Source>To<Target>Test
```
Examples: `OracleToCubridTest`, `CubridToCubridTest`. The class
extends `AbstractMigrationE2E` directly and carries the
`@MigrationE2E` annotation, `source()`, `target()`, and `@Test`
methods at the top level.

**Multi-variant shape** — outer namespace + `@Nested` per variant:
```
<Source>To<Target>Test {              // outer (no extends, no @Test)
    @Nested
    class <VariantName> extends AbstractMigrationE2E { ... }
}
```
The **outer** is a namespace for the source/target shape — it does
not extend `AbstractMigrationE2E`, has no `@Test` methods, and
carries only an outer-level `@DisplayName`. Each variant lives as a
**non-static `@Nested` inner class** that extends
`AbstractMigrationE2E`, declaring its own `@MigrationE2E.name`,
`@MigrationE2E.options`, `@DisplayName`, `source()`, `target()`,
and `@Test` methods.

`<VariantName>` is a PascalCase noun phrase describing the variant's
observable behaviour (`SplitPerTable`, `FlatMerged`, `PerTable`,
`WithTriggers`, …) — same naming spirit as §12.2 discriminators,
applied to inner-class identifiers. Underscores are not used in
class names; the discriminator's word-boundary `_` from the scenario
id becomes a PascalCase break (`split_per_table` → `SplitPerTable`).

**Why `@Nested` for variants:**
- Avoids class-name proliferation (one outer per shape vs. N classes)
- Lets the outer carry the source/target identity once, while each
  inner carries the variant identity — a clean two-level discriminator
- Variants stay textually adjacent in one file; review sees them
  together
- Surefire/IDE display the hierarchy (`ORA-UN > ORA-UN-SPLIT-PER-TABLE
  > migration_succeeds`) which matches how a developer thinks about
  the dimensions
- `regression/` sub-package keeps using flat classes — bug fixtures
  are not "variants" of an outer shape; they're independent regression
  fixtures with shorter lifecycles

### 12.4 Package layout

Source DB is the only first-class taxonomic axis. Migration TCs sit
flat at the source-package top level. The single sub-package is
`regression/` for L4 bug/feature fixtures, isolated because their
lifecycle is shorter than the rest (a fixture can be retired once the
upstream change makes the bug structurally impossible).

```
tests/migration/
├── oracle/
│   ├── OracleToCubridTest.java         ← flat (no variants)
│   ├── OracleToUnloadTest.java         ← outer + @Nested variants:
│   │     └─ @Nested SplitPerTable      (split_schema=true, otof=true)
│   │     └─ @Nested FlatMerged         (split_schema=false, otof=false) — future
│   └── regression/
│       └── OracleToCubridBugCmt1234Test.java
├── cubrid/
│   ├── CubridToCubridTest.java
│   ├── CubridToUnloadTest.java         ← outer + @Nested variants
│   └── regression/
├── tibero/
│   ├── TiberoToCubridTest.java         ← flat (Phase 3b business + view + synonym; no routines yet)
│   └── TiberoToUnloadTest.java         ← outer + @Nested SplitPerTable
└── postgres/                           ← new source DBs add a peer dir
    └── PostgresToCubridTest.java
```

One file = one source/target shape. Variants live as `@Nested`
classes inside the file, NOT as peer top-level classes. `regression/`
carries a `package-info.java` describing what belongs inside; the
migration top level does not — the source package's name is its own
description.

### 12.5 `@Nested` is the variant standard (not an escape hatch)

For any source/target shape that carries multiple option variants,
`@Nested` is the **standard** way to express them — not an escape
hatch. Every variant is a non-static `@Nested` inner class that
extends `AbstractMigrationE2E` and carries its own
`@MigrationE2E(name=..., options={...})`, `@DisplayName`, `source()`,
`target()`, and `@Test` methods. The outer class is a namespace —
it does not extend `AbstractMigrationE2E`, has no `@Test` methods,
and is `package-private` final-by-convention.

Lifecycle: `AbstractMigrationE2E` carries `@TestInstance(PER_CLASS)`
which is inherited by each `@Nested` extending it. JUnit 5 treats
each `@Nested` as a full test class — its `@BeforeAll` runs
independently, so each variant runs its own migration with its own
options. There is no shared migration state between variants
(intentional — the whole point is option independence).

Single-variant shapes don't need `@Nested` yet — a flat top-level
class is fine (e.g. `OracleToCubridTest`). When a second variant
arrives, the existing flat class is converted: outer becomes the
namespace, the existing TC becomes the first `@Nested`, and the new
variant becomes the second `@Nested`. Both gain explicit variant
names in the same PR (§12.8 step 9 applies to this conversion).

### 12.6 `@DisplayName` prefix

Every migration TC declares a `@DisplayName` whose prefix encodes the
identity in shorthand. The prefix lets reports and IDE views sort and
filter by source/target without parsing.

```
<SRC>-<TGT>[-<DISCRIMINATOR>] [<purpose>]: <description>
```

| Token | Source code | Target code |
|-------|-------------|-------------|
| Source | `ORA`, `CUB`, `TIB`, `PG`, … (3-letter) | — |
| Target | — | `ON` (online), `UN` (unload), future `CSV`/`SQL`/`XLS` |

| Class | DisplayName |
|-------|-------------|
| `OracleToCubridTest` | `ORA-ON: Oracle e2e dataset → CUBRID online migration` |
| `OracleToUnloadTest` (outer) | `ORA-UN: Oracle e2e dataset → CMT unload (LoadDB) dump` |
| `OracleToUnloadTest.SplitPerTable` (`@Nested`) | `ORA-UN-SPLIT-PER-TABLE: split_schema=true, one_table_one_file=true` |
| `OracleToUnloadTest.FlatMerged` (`@Nested`, future) | `ORA-UN-FLAT-MERGED: split_schema=false, one_table_one_file=false` |
| `OracleToCubridBugCmt1234Test` | `ORA-ON [bug:CMT-1234]: FK ON DELETE CASCADE 회귀` |

Discriminator tokens use `-` (not `_`) inside the prefix and
uppercase throughout. For `@Nested` variants, the **outer**
`@DisplayName` carries only the source/target shape (no options) —
options live in the **inner** display name. JUnit composes them
hierarchically:
```
ORA-UN > ORA-UN-SPLIT-PER-TABLE > migration_succeeds
```
The DisplayName description should mention the key option values in
plain English so the test's contract is readable in surefire / IDE
without opening the class. The original Jira id (`CMT-1234`) is
preserved in the `[bug:...]` tail so reviewers see the source ticket
directly.

### 12.7 `@MigrationE2E.options[]`

Every TC explicitly lists the CMT options that influence the output it
verifies. The annotation is metadata; the runtime behaviour is still
set by `target()` (and where applicable `source()`). They must agree.

```java
@DisplayName("ORA-UN: Oracle e2e dataset → CMT unload (LoadDB) dump")
class OracleToUnloadTest {

    @Nested
    @MigrationE2E(
        name = "oracle_to_unload__split_per_table",
        options = {
            "file_prefix=XE",
            "split_schema=true",
            "one_table_one_file=true",
        })
    @DisplayName("ORA-UN-SPLIT-PER-TABLE: split_schema=true, one_table_one_file=true")
    class SplitPerTable extends AbstractMigrationE2E {
        @Override protected Source source() { return Sources.oracleE2eSeed(); }
        @Override protected Target target() { return Targets.unload("XE", true); }
        // @Test methods ...
    }

    @Nested
    @MigrationE2E(
        name = "oracle_to_unload__flat_merged",
        options = {
            "file_prefix=XE",
            "split_schema=false",
            "one_table_one_file=false",
        })
    @DisplayName("ORA-UN-FLAT-MERGED: split_schema=false, one_table_one_file=false")
    class FlatMerged extends AbstractMigrationE2E {
        @Override protected Source source() { return Sources.oracleE2eSeed(); }
        @Override protected Target target() { return Targets.unload("XE", false).withSplitSchema(false); }
        // @Test methods ...
    }
}
```

What to list:
- Every option whose value affects something the snapshots or
  assertions read. If you're verifying dump structure, list every
  option that influences file layout / naming / content.
- Do not list options whose values are irrelevant to the assertions —
  noise hides the meaningful ones. (`file_prefix` is borderline:
  list it for dump TCs because file names appear in the snapshot
  tree; omit it for online TCs.)

What not to do:
- Do not write `options = {}` and lean on CMT defaults for an option
  the test cares about. If `split_schema`'s value matters, set it
  explicitly even if you happen to want today's CMT default.

The framework does **not** parse `options[]` at runtime. Drift between
the annotation and `target()` is caught at review, not by JVM error.

### 12.8 Putting it together — checklist for a new TC

When you write a new TC:

1. Pick the source / target / discriminator (§12.1, §12.2).
2. Decide the class structure (§12.3, §12.5):
   - **No variants for this source/target shape** → flat top-level
     class extending `AbstractMigrationE2E`.
   - **One or more variants** → outer namespace class with each
     variant as a `@Nested` inner class extending `AbstractMigrationE2E`.
3. Compose the scenario id (§12.2). For `@Nested` variants the id
   carries the variant token (`oracle_to_unload__split_per_table`).
4. Place the class file:
   - migration → `tests/migration/<source>/<OuterOrFlat>.java`
   - regression → `tests/migration/<source>/regression/<Class>.java`
5. Annotate **the inner `@Nested`** (or the flat class) with
   `@MigrationE2E(name=..., options={...})` listing every option
   whose value matters to the verification (§12.7).
6. Have `target()` (and `source()` if relevant) set those same option
   values explicitly. Do not depend on CMT defaults for any option
   you cared enough to list.
7. Add two `@DisplayName`s for `@Nested` variants:
   - **outer** — source/target shape only (no options), e.g.
     `ORA-UN: Oracle e2e dataset → CMT unload (LoadDB) dump`
   - **inner** — full §12.6 prefix with options spelled out, e.g.
     `ORA-UN-SPLIT-PER-TABLE: split_schema=true, one_table_one_file=true`
   For flat classes, just one `@DisplayName` covering everything.
8. Create the snapshot directory `snapshots/<scenario_id>/` (capture
   mode will populate it on first run).
9. Update `README.md` table of active scenarios.
10. **Converting flat → outer+`@Nested`** (when a second variant lands
    on a previously single-variant shape):
    - Move the existing flat class body into a new `@Nested` inner.
    - Pick a behaviour-name for the existing variant (it had none
      before — §12.0 forbids implicit "this is the default").
    - Add the new variant as a sibling `@Nested`.
    - Rename the existing scenario id to include the variant token
      (`oracle_to_unload` → `oracle_to_unload__<existing_variant>`)
      and the snapshot dir.
    - Land the conversion + the new variant in the same PR.
