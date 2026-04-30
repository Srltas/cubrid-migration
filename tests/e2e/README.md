# CMT E2E Tests

End-to-end migration tests for CMT Console. Source DB
(Oracle / CUBRID) → `migration.sh` → target DB (online CUBRID
or dump file) round-trip verification.

> **Architecture:** see `docs/ARCHITECTURE.md` (single source of truth).
> This README is a quick-start.

---

## Active scenarios

| ID | Source | Target | Class |
|----|--------|--------|-------|
| `oracle_to_cubrid` | Oracle 11g XE | CUBRID 11.4 (online) | `OracleToCubridTest` |
| `oracle_to_dump` | Oracle 11g XE | dump file | `OracleToDumpTest` |
| `cubrid_to_cubrid` | CUBRID 11.4 | CUBRID 11.4 (online) | `CubridToCubridTest` |
| `cubrid_to_dump` | CUBRID 11.4 | dump file | `CubridToDumpTest` |

Plus `CliTest` (8 `@Test`) — `migration.sh` dispatch / first-run
filesystem contracts (no DB).

The e2e module hosts **only end-to-end tests**. Framework-internal
unit tests do not live here; sanitize regex regressions and similar
self-evident logic are caught at the next snapshot capture / regression
run (the integration suite is the safety net).

> MariaDB / MySQL / MSSQL / Informix were exercised in the PoC era but
> are frozen at the `poc-final` git tag — see `docs/ARCHITECTURE.md`
> §1 D4. Re-activation is a deliberate cherry-pick.

---

## Layered verification

Each `@Test` maps to exactly one layer. See `docs/ARCHITECTURE.md` §3.

| Layer | Question | Failure means |
|-------|----------|---------------|
| L1 Smoke | Did `migration.sh` exit 0 with no fatal stderr? | CMT itself broke |
| L2 Coverage | Are all expected objects present in the target? | CMT dropped something |
| L3 Fidelity | Are values & metadata correct? | CMT mistranslated |
| L4 Regression | Does a known bug stay fixed? | Tracked issue regressed |

Each test method is 1–3 lines. Catalog/data expectations live in text
snapshot files under `src/test/resources/snapshots/<scenario>/`, not
in Java code.

---

## Running tests

### Prerequisites

- Docker daemon running.
- CMT Console binary extracted; export `CMT_CONSOLE_HOME` to its path.
- The `migration.sh` script bundles a Linux x86_64 JRE — Apple Silicon
  hosts must run via `docker compose run --rm e2e-test ...` rather
  than directly on the host.

### Common invocations

```bash
# All v2 tests (regression mode — must match committed snapshots)
docker compose run --rm e2e-test mvn test

# Single scenario
docker compose run --rm e2e-test mvn test -Dtest=OracleToCubridTest

# A single fact within a scenario
docker compose run --rm e2e-test mvn test \
    -Dtest='OracleToCubridTest#serials_match_snapshot'
```

Without Docker (Linux x86_64 host or after replacing the bundled JRE):

```bash
export CMT_CONSOLE_HOME=/path/to/cmt-console
mvn test -Dtest=OracleToCubridTest
```

### Snapshot capture / refresh

When CMT output legitimately changes (bug fix, naming convention shift,
new column type), regenerate the snapshot files:

```bash
docker compose run --rm e2e-test mvn test \
    -Dtest='OracleToCubridTest,CubridToCubridTest' \
    -Dsnapshot.update=true
```

After regenerating, **review the snapshot file diff in your PR** and
explain the change in the commit message. CI runs without
`-Dsnapshot.update`, so a snapshot drift becomes a hard failure.

---

## Anti-coverage (CMT limits handled at framework level)

`framework/runner/ScriptXmlBuilder.sanitize()` strips a few CMT
quirks from the generated `script.xml` so the migration plan reflects
the seed contract rather than CMT artefacts:

| Stripped | Why | Source impact |
|----------|-----|---------------|
| `<migration name>` timestamp suffix | CMT wall-clock state | all sources |
| `wizard_start_date_time` | CMT wall-clock state | all sources |
| `flyway_schema_history` table refs | Flyway seed metadata, not migration contract | all sources |
| `<schema source="DBA"/>`, `<schema source="PUBLIC"/>` | CMT picks up CUBRID system namespaces when introspecting as `dba` | CUBRID source |
| `e2e_cubrid_collection_types` table refs | CMT cannot round-trip CUBRID SET/LIST/SEQUENCE | CUBRID source |
| `idxf_*` index refs | `CUBRIDSchemaFetcher` does not emit functional-index expression | CUBRID source |

See `docs/ARCHITECTURE.md` §9 ADR D6 / D7 / D8 / D9.

---

## Layout

```
tests/e2e/
├── docs/
│   ├── ARCHITECTURE.md                  ← v2 design + ADR (incl. §12 naming)
│   ├── SEED_DATA_GUIDE.md
│   └── seed/
│       ├── COMMON_SEED_CONTRACT.md
│       ├── oracle/SEED_SPEC.md
│       └── cubrid/SEED_SPEC.md
├── src/test/java/com/cmt/e2e/
│   ├── framework/                       ← v2 building blocks (9 packages)
│   └── tests/
│       ├── cli/
│       └── migration/
│           ├── oracle/
│           │   ├── *Test.java           ← migration TCs (peers, no "default")
│           │   └── regression/          ← L4 bug fixtures
│           └── cubrid/
│               ├── *Test.java
│               └── regression/
└── src/test/resources/
    ├── db/<engine>/{init,ref_schema,main_schema}/V*.sql   ← Flyway seed
    ├── queries/<scenario>.sql           ← labelled SQL for RowQueries
    └── snapshots/<scenario>/...         ← golden text tables + dump tree
```

---

## Naming convention (cheat sheet)

Full grammar: `docs/ARCHITECTURE.md` §12. Quick form:

```
scenario id  : <source>_to_<target>[__<discriminator>]
class name   : <Source>To<Target>[<Discriminator>]Test     (no underscores)
DisplayName  : <SRC>-<TGT>[-<DISCRIMINATOR>] [<purpose>]: <description>
```

**Guiding principle (§12.0):** there is **no "default" TC**. Every TC
explicitly declares the CMT options that affect its verification in
`@MigrationE2E.options[]`. CMT's built-in defaults can change between
releases — leaning on them silently shifts a test's meaning. So every
peer migration TC names its observable behaviour and lists its options.

| Kind | Scenario id | Class | Location |
|------|-------------|-------|----------|
| migration TC | `oracle_to_cubrid` | `OracleToCubridTest` | `migration/oracle/` |
| migration TC (sibling) | `oracle_to_dump__flat` | `OracleToDumpFlatTest` | `migration/oracle/` |
| regression | `oracle_to_cubrid__bug_cmt_1234` | `OracleToCubridBugCmt1234Test` | `migration/oracle/regression/` |

Discriminator is a **behaviour name** (`flat`, `per_table`, …), not a
flag value (`split_off`, `_yes/_no`, `_true/_false`). Bug regressions
preserve the original Jira id (`CMT-1234`) in the `[bug:...]` tail of
the DisplayName for traceability.
