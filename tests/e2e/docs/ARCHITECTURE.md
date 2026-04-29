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

(Filled in Phase 7.)

---

## 9. ADR log

| ID | Phase | Date | Decision | Notes |
|----|-------|------|----------|-------|
| D1 | 0 | 2026-04-29 | New `e2e-v2` branch from `poc-final`; PoC frozen | §1 |
| D2 | 0 | 2026-04-29 | Aligned-table snapshot format | §1, §7 |
| D3 | 0 | 2026-04-29 | `script.xml` generated dynamically per test | §1 |
| D4 | 0 | 2026-04-29 | DEFERRED DBs frozen on PoC, not imported | §1 |

Mid-implementation overrides land here as new rows.

---

## 10. Phase roadmap

| Phase | Deliverable | Status |
|-------|-------------|--------|
| 0 | Branch + ARCHITECTURE skeleton | **in progress** |
| 1 | `framework/env`, `source`, `target`, `junit` (lifecycle) | pending |
| 2 | `framework/runner` — Migration + ScriptXmlBuilder | pending |
| 3 | `framework/verify` — SnapshotStore + CatalogQueries + Tabulator | pending |
| 4 | Oracle TC ×2 + first snapshot capture | pending |
| 5 | CUBRID TC ×2 + first snapshot capture | pending |
| 6 | PoC asset cleanup (template/, RegenerateScripts, fixtures) | pending |
| 7 | CI + local mvn profiles + final docs | pending |

Each phase ships as one or more commits on `e2e-v2`. Cutover to a PR
against the integration branch happens after Phase 6 passes
acceptance.

---

## 11. Acceptance criteria (end-state)

A v2 release is "done" when all hold:

1. **Readability gate.** Hand a colleague `OracleToCubridTest.java`. They
   list every fact verified within 30 seconds.
2. **Flexibility gate.** Simulate a CMT naming-convention change
   (`pk_T_C` → `pk_T`). The PR to absorb it is one snapshot file diff,
   zero Java code changes.
3. **Speed gate.** A single class executes in:
   - Oracle: < 5 minutes (container + CMT + 8 verifies)
   - CUBRID: < 2 minutes
4. **Active count.** ≥ 32 active migration tests + 8 CLI smoke = 40.
   Zero `@Disabled`.
5. **LOC.** v2 ≤ 3,800 LOC (vs. PoC `poc-final` ≈ 5,800).
