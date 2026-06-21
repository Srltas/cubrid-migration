# Coverage Matrix — Research & Deferral Note

> **Status:** DEFERRED (2026-04-30). See ARCHITECTURE.md ADR D15.
> **Purpose:** when this work resumes, this document is the starter pack —
> the codebase enumeration is done, the design is sketched, the deferral
> reasons are recorded. Re-reading this should be enough to pick up
> without re-running the analysis.

---

## 0. Why this exists, why it's deferred

### 0.1 The original question

"우리 E2E 가 CMT Console 의 어떤 기능들을 *정확히* 검증하고 있는가?" —
not by intuition or self-reporting, but **measured against CMT's own code**
as the reference truth. The output is a Coverage Matrix: rows = CMT
capabilities (extracted from the codebase), columns = our scenarios,
cells = ✓ (verified) / ✗ (gap) / ⊘ (anti-coverage) / — (N/A).

### 0.2 Why deferred

The honest cost analysis (§7 of this document) showed:

- Building a working generator: **5–7 days** focused work
- Maintenance cost: **non-trivial** because CMT structure changes
  (XML format, setter naming, handler dispatch) silently break parsers
- Sweet-spot is a YAML-mediated semi-auto design (§8) — but still
  ~1–2 days build + ongoing YAML curation

Other priorities (CI/CD, test additions, framework polish) come first.
This document preserves the analysis so the resumption cost stays low —
roughly 1 day to re-validate the enumeration is current, then start
Phase 11.1.

### 0.3 What's preserved here

1. The enumeration of CMT's capability surface, with file:line citations
   (§3) — saves ~half a day re-doing this from scratch.
2. The enumeration of our E2E coverage at point-in-time (§4) — same.
3. The 5-sub-matrix design (§5) and the current cell values computed by
   hand (§6).
4. Five concrete gaps the matrix reveals *now* (§7) — these are real
   backlog items independent of whether the matrix is automated.
5. The build/maintain analysis (§8) and the four implementation options
   weighed (§9).
6. The recommended resumption path when this work is unblocked (§10).

---

## 1. The taxonomy — what a Coverage Matrix is

```
Coverage Matrix = (CMT capability surface)  ×  (our scenarios)  →  ✓ / ✗ / ⊘ / —
                   └─── ROWS ───┘             └── COLUMNS ──┘
```

Both axes must be **mechanically extracted from code**. Hand-curating either
side defeats the purpose — the matrix exists to detect drift between what
CMT can do and what we verify, and humans are bad at noticing what they
forgot to test.

| Axis | Source of truth |
|------|----------------|
| Rows | `cubrid-migration/plugins/com.cubrid.cubridmigration.core/` (XML mappings, `dbobject/`, `MigrationConfiguration`, `core/engine/config/`); console handlers in `plugins/com.cubrid.cubridmigration.command/` |
| Columns | `tests/e2e/src/test/java/com/cmt/e2e/tests/migration/` (annotations) + `tests/e2e/src/test/resources/db/<src>/**/V*.sql` (seed) + `tests/e2e/src/test/resources/snapshots/<scenario>/**` (verified facts) |

---

## 2. Five sub-matrices (one big table is unreadable)

Combining all dimensions into one matrix produces mostly N/A cells. The
design splits into 5 sub-matrices, each fitting in one screen:

| Sub-matrix | Row source | Column source | Answers |
|------------|------------|---------------|---------|
| **M1 Source × Target** | CMT plugin dirs (7 source DBs) × `DEST_*` (5 target shapes) | scenario state (Active / Deferred / Absent) | Which migration paths are verified |
| **M2 Object × Scenario** | `core/dbobject/*.java` (16 object kinds) | the active scenarios (Oracle/CUBRID/Tibero ON+unload) | Which DB object kinds are verified per scenario |
| **M3 Type × Scenario** | `<DB>2CUBRID.xml` mappings (Oracle 28, CUBRID 29, Tibero — see `Tibero2CUBRID.xml`) | the active scenarios | Which column types are seeded and verified |
| **M4 Option × Scenario** | `MigrationConfiguration.java` setters (~23 active options) | scenarios + their `@MigrationE2E.options[]` | Which CMT option values are explicitly verified |
| **M5 Subcommand × Test** | `DoMigration.handlerFactory` (4 handlers) + each handler's flags | CLI tests + migration tests | How thoroughly `migration.sh` dispatch and flags are verified |

---

## 3. CMT capability surface (rows) — codebase-grounded enumeration

> Source: deep codebase research, 2026-04-30. Every claim cites a file
> path. Re-validate citations before re-using — file structure may
> drift.

### 3.1 D1 — Source DB type catalogs

#### Oracle (28 source types)

Authoritative list lives in:
`plugins/com.cubrid.cubridmigration.core/src/com/cubrid/cubridmigration/oracle/trans/Oracle2CUBRID.xml`

Type categories and rows (line numbers from research run):

| # | Type | Category | First target mapping | XML line |
|---|------|----------|----------------------|----------|
| 1 | BFILE | LOB/FILE | blob | 4 |
| 2 | BLOB | LOB | bit varying / blob / varchar | 17 |
| 3 | RAW(n) | BINARY | bit varying(n) | 40 |
| 4 | LONG RAW | BINARY | bit varying / blob | 53 |
| 5 | LONG | LOB/CHAR | varchar / clob | 71 |
| 6 | CHAR(n) | CHAR | char / varchar | 89 |
| 7 | VARCHAR2(n) | CHAR | varchar | 107 |
| 8 | NCHAR(n) | CHAR | char / varchar | 120 |
| 9 | NVARCHAR2(n) | CHAR | varchar | 138 |
| 10 | CLOB | LOB | varchar / clob | 151 |
| 11 | NCLOB | LOB | varchar / clob | 169 |
| 12 | BINARY_DOUBLE | NUMBER | double / int / bigint | 187 |
| 13 | BINARY_FLOAT | NUMBER | float / double / int / bigint | 210 |
| 14 | DECIMAL(p,s) | NUMBER | numeric(p,s) / varchar(78) | 238 |
| 15 | INTEGER | NUMBER | int / bigint / numeric(38,0) / varchar(40) | 256 |
| 16 | FLOAT | NUMBER | double / float / int / bigint | 284 |
| 17 | REAL | NUMBER | float / double / int / bigint | 312 |
| 18 | NUMBER | NUMBER | numeric(38,15) / int / bigint / varchar(78) | 340 |
| 19 | NUMBER(p,s) | NUMBER | numeric(p,s) / int / bigint / varchar(78) | 368 |
| 20 | DATE | DATE/TIME | datetime / date / timestamp | 396 |
| 21 | TIMESTAMP | DATE/TIME | timestamp / datetime | 419 |
| 22 | TIMESTAMP WITH TIME ZONE | DATE/TIME | varchar(100) | 437 |
| 23 | TIMESTAMP WITH LOCAL TIME ZONE | DATE/TIME | datetime | 450 |
| 24 | INTERVAL DAY TO SECOND | INTERVAL | varchar(255) | 463 |
| 25 | INTERVAL YEAR TO MONTH | INTERVAL | varchar(255) | 476 |
| 26 | ROWID | OBJECT/ID | varchar(64) | 489 |
| 27 | UROWID | OBJECT/ID | varchar(4000) | 502 |
| 28 | ARRAY | COLLECTION | list(varchar) | 515 |

**Caveats:**
- `BFILE`, `ROWID`, `UROWID` return `null` JDBC ID at
  `OracleDataTypeHelper.java:172-175` — carried as fallback strings.
- Types not in this XML and likely **rejected at column level**: `STRUCT`,
  `REF`, `ANYDATA`, `XMLTYPE`, `SDO_GEOMETRY`, Oracle native `JSON`,
  `BOOLEAN`, `NUMBER` with negative scale beyond what the XML supports.
- The "true" supported set may be DB-version-dependent — `AbstractJDBCSchemaFetcher.getSupportedSqlTypes`
  (line 1180) reads from `DatabaseMetaData.getTypeInfo()` dynamically.

#### CUBRID (29 source types)

Authoritative list:
`plugins/com.cubrid.cubridmigration.core/src/com/cubrid/cubridmigration/cubrid/trans/CUBRID2CUBRID.xml`

Plus `CUBRIDDataTypeHelper.java:115-323` for type-symbol registry.

| # | Type | Category | XML line |
|---|------|----------|----------|
| 1 | short | NUMBER | 4 |
| 2 | int | NUMBER | 17 |
| 3 | bigint | NUMBER | 30 |
| 4 | numeric | NUMBER | 43 |
| 5 | float | NUMBER | 66 |
| 6 | double | NUMBER | 89 |
| 7 | char | CHAR | 112 |
| 8 | varchar | CHAR | 130 |
| 9 | nchar | CHAR | 143 |
| 10 | nvarchar | CHAR | 161 |
| 11 | time | DATE/TIME | 174 |
| 12 | date | DATE/TIME | 187 |
| 13 | datetime | DATE/TIME | 200 |
| 14 | timestamp | DATE/TIME | 213 |
| 15 | timestamptz | DATE/TIME | 231 |
| 16 | timestampltz | DATE/TIME | 244 |
| 17 | datetimetz | DATE/TIME | 257 |
| 18 | datetimeltz | DATE/TIME | 270 |
| 19 | bit | BIT | 283 |
| 20 | bit varying | BIT | 296 |
| 21 | monetary | NUMBER | 309 |
| 22 | set | COLLECTION | 327 |
| 23 | multiset | COLLECTION | 340 |
| 24 | list (sequence) | COLLECTION | 353 |
| 25 | glo | LOB (legacy) | 366 |
| 26 | clob | LOB | 389 |
| 27 | blob | LOB | 407 |
| 28 | enum | OTHER | 425 |
| 29 | json | OTHER | 443 |

**Caveats:**
- `object` is registered as `DataTypeSymbol` (`CUBRIDDataTypeHelper.java:296-299`)
  but has no XML mapping — likely silently dropped in migration.
- Legacy `fbo` is commented out (`CUBRIDDataTypeHelper.java:289-293`).

### 3.2 D2 — Schema-object kinds extracted

Canonical set lives in
`plugins/com.cubrid.cubridmigration.core/src/com/cubrid/cubridmigration/core/dbobject/`:

```
Catalog, Schema, SchemaCatalog, SchemaEntry  ← containers, not test targets
Table, View, Column, PK, FK, Index           ← structural
Sequence, Synonym, Trigger                   ← schema-level
Function, Procedure, PlcsqlFunction, PlcsqlProcedure  ← routines
Grant, PartitionInfo, PartitionTable         ← misc
Record                                        ← data row, fetched separately
```

The dispatcher `AbstractJDBCSchemaFetcher.buildSchemaObjects` (lines
291–300, 392–399) invokes:
`buildTables → buildViews → buildProcedures → buildTriggers → buildSequence → buildSynonym → buildGrant`.
Partitions are invoked separately by `buildCatalog`.

#### Object × Source extraction matrix (file:line evidence)

| Object kind | Oracle fetcher | CUBRID fetcher |
|-------------|----------------|----------------|
| Table | YES — `AbstractJDBCSchemaFetcher.buildTables` (line 886); table comments at `OracleSchemaFetcher.java:244` | YES — DDL set at `CUBRIDSchemaFetcher.java:155`; CUBRID-specific `buildTables` at line 1749 |
| View | YES — view query text set at `OracleSchemaFetcher.java:256` | YES — `buildViews` at `CUBRIDSchemaFetcher.java:1824`; v11.2+ uses `buildCUBRIDViews` (line 1884) |
| Column | YES — `buildTableColumns` `OracleSchemaFetcher.java:548`; `buildViewColumns` line 888 | YES — `buildTableColumns` `CUBRIDSchemaFetcher.java:1437`; `buildViewColumns` line 1809 |
| PK | YES — `buildTablePK` `OracleSchemaFetcher.java:646` | YES — `buildTablePK` `CUBRIDSchemaFetcher.java:1704` |
| FK | YES — `buildTableFKs` `OracleSchemaFetcher.java:694` | YES — `buildTableFKsWithUserSchema` `CUBRIDSchemaFetcher.java:903` |
| Index (incl. UK) | YES — `buildTableIndexes` `OracleSchemaFetcher.java:762` | YES — `buildTableIndexes` `CUBRIDSchemaFetcher.java:1466` |
| Sequence | YES — `buildSequence` `OracleSchemaFetcher.java:408` (USER_SEQUENCES) | YES — `buildSequence` `CUBRIDSchemaFetcher.java:1319` (`db_serial`) |
| Synonym | YES — `buildSynonym` `OracleSchemaFetcher.java:468` (DBA_SYNONYMS) | CONDITIONAL — `CUBRIDSchemaFetcher.java:1919-1925` returns empty if DB version < `USERSCHEMA_VERSION` (CUBRID 11.2). YES on 11.2+ |
| Trigger | YES — `buildTriggers` `OracleSchemaFetcher.java:864` (gated on Oracle major ≥ 5) | CONDITIONAL — `CUBRIDSchemaFetcher.java:1789` only when connected user is "DBA" (line 1793) |
| Procedure | YES (PL/SQL) — `buildProcedures` `OracleSchemaFetcher.java:368` populates `setPlcsqlProcedures`/`setPlcsqlFunctions` | YES (Java SP) — `buildProcedures` `CUBRIDSchemaFetcher.java:1294` populates `setProcedures`/`setFunctions` |
| Function | YES — folded into `buildProcedures` via `OraclePlsqlProcedure.getProcedureType()` (`OracleSchemaFetcher.java:380-385`) | YES — `getAllFunctions` `CUBRIDSchemaFetcher.java:2041` |
| Grant | YES — `buildGrant` `OracleSchemaFetcher.java:915`; supports table + view grants | YES — `buildGrant` `CUBRIDSchemaFetcher.java:1930` |
| PartitionInfo / PartitionTable | YES — `buildPartitions` `OracleSchemaFetcher.java:298`; range/list/hash + sub-partition | YES — `buildPartitions` `CUBRIDSchemaFetcher.java:1193` (`db_partition`); range/list/hash lines 1219-1242 |
| PlcsqlProcedure / PlcsqlFunction | YES — Oracle creates these as canonical procedure objects | NO — CUBRID source produces `Procedure`/`Function` only; `PlcsqlProcedure` is target-side construct |
| Record (data rows) | N/A in fetcher — extracted by `OracleExportHelper` in `oracle/export/` | N/A in fetcher — extracted by `CUBRIDExportHelper` in `cubrid/export/` |

**Notable absences:**
- `Synonym` empty for CUBRID < 11.2 (`CUBRIDSchemaFetcher.java:1922-1925`)
- `Trigger` empty for non-DBA CUBRID users (`CUBRIDSchemaFetcher.java:1793`)
- `Trigger` empty for Oracle < 5 (`OracleSchemaFetcher.java:871-873`)
- Base class `AbstractJDBCSchemaFetcher.buildGrant/buildSynonym/buildSequence`
  are explicit no-ops (lines 402, 417, 432) — DBs without overrides get zero

### 3.3 D3 — Migration options

Source: `plugins/com.cubrid.cubridmigration.core/src/com/cubrid/cubridmigration/core/engine/config/MigrationConfiguration.java`.
Defaults from field initializers (lines 165–285).

#### Performance / threading
| Option | Default | Affects | Field/Setter line |
|--------|---------|---------|-------------------|
| commitCount | 1000 | rows per JDBC commit (online) | 165 / setter 4754 |
| exportThreadCount | 4 (`DEFAULT_EXPORT_THREAD_COUNT`) | source-fetch parallelism | 167 / setter 4904 |
| importThreadCount | 3 (`DEFAULT_IMPORT_THREAD_COUNT`) | target-write parallelism | 168 / setter 5499 |
| pageFetchCount | 1000 | rows per JDBC fetch page | 270 / setter 4967 |

#### Output structure (dump)
| Option | Default | Affects | Field/Setter line |
|--------|---------|---------|-------------------|
| splitSchema | false | dump — split schema objects across files | 215 / setter 5329 |
| oneTableOneFile | false | dump — one data file per table | 263 / setter 4963 |
| maxCountPerFile | 0 (no split) | dump — rows per data file | 166 / setter 4927 |
| targetFilePrefix | (null) | dump — file name prefix | 211 / setter 5121 |
| targetCharSet | "UTF-8" | dump — encoding | 212 / setter 5089 |
| targetLOBRootPath | "" | dump — directory for LOB files | 213 / setter 5314 |
| targetFileTimeZone | "Default" | dump — TZ in formatted timestamps | 259 / setter 5125 |
| destType | (set later) | dump format: 0 LoadDB / 1 CSV / 2 SQL / 3 XLS / 4 ONLINE | 244 / setter 4776, 4786 |
| fileRepositroyPath | (null) | dump output directory | 185 / setter 4908 |
| deleteTempFile | true | clean up intermediate files | 170 / setter 4767 |

#### Online-target behavior
| Option | Default | Affects | Field/Setter line |
|--------|---------|---------|-------------------|
| addUserSchema | false | emit `CREATE USER` / qualify by user-schema | 214 / setter 5325 |
| createConstrainsBeforeData | false | apply PK/FK/Index before data load | 237 / setter 4758 |
| targetDBAGroup | false | assume DBA-group privileges | 216 / setter 5333 |
| createUserSQL | false | emit `CREATE USER` SQL | 217 / setter 5337 |
| updateStatistics | true | run `UPDATE STATISTICS` after data | 279 / setter 5495 |
| isWriteErrorRecords | false | save failed rows to error SQL file | 264 / setter 5346 |
| implicitEstimate | false | estimate row counts implicitly (perf) | 273 / setter 4918 |

#### Source-fetch filtering
| Option | Default | Affects | Field/Setter line |
|--------|---------|---------|-------------------|
| exportNoSupportObjects | true | include unsupported objects in dump for review | 172 / setter 4895 |
| selectedSrcSchemas | empty | which source schemas to fetch | 247 / setter 2558 |
| sourceType | `SOURCE_TYPE_CUBRID` (=2) | which source DB driver | 223 / setter 5013, 5020 |
| sourceFileEncoding | (null) | for offline source files | 240 / setter 4988 |
| sourceFileTimeZone | (null) | TZ of timestamps in source dumps | 242 / setter 5002 |
| sqlFiles | empty | run-SQL-then-migrate pre-step | 266 / setter 5040 |

#### Reporting / monitoring
| Option | Default | Affects | Field/Setter line |
|--------|---------|---------|-------------------|
| reportLevel | `RPT_LEVEL_INFO` (=2) | report verbosity | 271 / setter 4971 |
| name / oldName / wizardStartDateTime | (null) | naming for history file | 275-277 / setters 4936-4951 |

#### Console-only inputs (CLI-mapped)

From `StartCommandHandler.handleCommand` (`StartCommandHandler.java:380-413`):
- `-s`, `-t`, `-sd`, `-td`, `-tp`, `-xml` — paths
- `-mm` (monitorMode brief/error/info/debug)
- `-rm` (reportMode)
- `-do` (dataOnly migration) — **no field on `MigrationConfiguration`**, console-side only

From `ScriptCommandHandler.setOtherOptions` (`ScriptCommandHandler.java:405-422`):
- `-ha` → `setCreateConstrainsBeforeData`
- `-err` → `setWriteErrorRecords`
- `-tc` → `setExportThreadCount`
- `-pfc` → `setPageFetchCount`
- `-cc` → `setCommitCount`
- `-template` → load template
- `-schema` → include source schema in script (line 341)

File-target dispatch from `db.conf` (`ScriptCommandHandler.java:560-575`):
`<target>.add_schema`, `<target>.split_schema`, `<target>.one_table_one_file`
flow into `setAddUserSchema`/`setSplitSchema`/`setOneTableOneFile`.

### 3.4 D4 — Migration target shapes

`MigrationConfiguration.java:113-117`:

| Constant | int | Label | Extension | Output |
|----------|-----|-------|-----------|--------|
| `DEST_DB_UNLOAD` | 0 | LoadDB | (suffixed: `_object`, `_schema`, `_indexes`, …) | CUBRID `loaddb` / `unloaddb` files |
| `DEST_CSV` | 1 | CSV | .csv | CSV dump |
| `DEST_SQL` | 2 | SQL | .sql | SQL INSERT script |
| `DEST_XLS` | 3 | XLS | .xls | Excel workbook |
| `DEST_ONLINE` | 4 | (online) | n/a | Live JDBC writes |

Dispatch:
- String→constant: `MigrationConfiguration.setDestTypeName` (lines 4786-4799)
  accepts `cubrid` (→ ONLINE), `csv`, `sql`, `unload` (→ DB_UNLOAD), `xls`.
- String comes from `db.conf` field `<target>.type` read in
  `ScriptCommandHandler.setTarget` (`ScriptCommandHandler.java:504-509`).
- Predicates: `targetIsOnline()`, `targetIsFile()`, `targetIsCSV()`,
  `targetIsSQL()`, `targetIsXLS()`, `targetIsUnload()`
  (`MigrationConfiguration.java:5398-5444`).

### 3.5 D5 — Console-specific surfaces

Entry: `DoMigration.handlerFactory` (`DoMigration.java:80-102`).

| Subcommand | Handler | File |
|------------|---------|------|
| `start` (default) | `StartCommandHandler` | `plugins/com.cubrid.cubridmigration.command/src/com/cubrid/cubridmigration/command/handler/StartCommandHandler.java` |
| `script` | `ScriptCommandHandler` | `…/handler/ScriptCommandHandler.java` |
| `log` | `LogCommandHandler` | `…/handler/LogCommandHandler.java` |
| `report` | `ReportCommandHandler` | `…/handler/ReportCommandHandler.java` |

`HistoryCommandHandler` is a base class only; never directly dispatched.

#### Per-subcommand flags

`start` (parsed in `StartCommandHandler.handleCommand` 380-413):
`-s`, `-t`, `-sd`, `-td`, `-tp`, `-xml`, `-mm` (brief|error|info|debug),
`-rm`, `-do`, plus positional `[script_file]`.

`script` (parsed in `ScriptCommandHandler` 79, 405-422):
- Documented in `help_script.txt`: `-s`, `-t`, `-o`, `-schema`, positional `[output_dir]`
- **Undocumented but active**: `-template`, `-ha`, `-err`, `-tc`, `-pfc`, `-cc`
- Reads from `db.conf`: `<target>.type`, `.file_prefix`, `.output`, `.charset`,
  `.add_schema`, `.split_schema`, `.one_table_one_file`
  (`ScriptCommandHandler.java:537-575`)

`log` (parsed in `LogCommandHandler.handleCommand` 63):
`-l`, `-ps`, positional `[mh_file]`

`report` (parsed in `ReportCommandHandler.handleCommand` 181):
`-l`, `-ao`, positional `[mh_file]`

Help texts:
`plugins/com.cubrid.cubridmigration.command/src/com/cubrid/cubridmigration/command/`
→ `help.txt`, `help_start.txt`, `help_script.txt`, `help_log.txt`, `help_report.txt`.

### 3.6 Flagged uncertainties (from research)

1. Oracle types **not exhaustively listed in code** — `Oracle2CUBRID.xml` (28
   entries) is the *transformable* set; JDBC may report more that get
   rejected at `OracleDataTypeHelper.java:190`. Treat XML count as
   authoritative for the matrix.
2. CUBRID `object` type — registered symbol but no XML mapping. Confirm
   in coverage testing whether it's silently dropped or rejected.
3. Procedure object type asymmetry — Oracle source produces `PlcsqlProcedure`/
   `PlcsqlFunction`; CUBRID source produces `Procedure`/`Function`. Matrix
   should distinguish.
4. CUBRID Trigger silently skipped for non-DBA users
   (`CUBRIDSchemaFetcher.java:1793`) — relevant for E2E that connects with
   non-DBA accounts.
5. `setExportNoSupportObjects` default `true` (line 172) — unsupported
   objects included in dumps as comments/skeletons. Worth a coverage row.
6. `script` undocumented flags (`-template`, `-ha`, `-err`, `-tc`, `-pfc`, `-cc`) —
   real and active, just not in `help_script.txt`. Internal/unstable
   or doc drift.

---

## 4. E2E coverage inventory (columns / cells) — point-in-time 2026-04-30

> Source: deep audit of `tests/e2e/`, 2026-04-30. Re-validate before
> reusing if seed/snapshots/scenarios changed.

### 4.1 E1 — Active scenarios

#### Migration tests (4 classes, 22 `@Test` total)

| Class | Scenario id | Source | Target | `options[]` | `@Test` |
|-------|-------------|--------|--------|-------------|---------|
| `oracle/OracleToCubridTest.java` | `oracle_to_cubrid` | `Sources.oracleE2eSeed()` | `Targets.cubridOnline()` | (none) | 9 |
| `oracle/OracleToUnloadTest.java` (outer + `@Nested SplitPerTable`) | `oracle_to_unload__split_per_table` | `Sources.oracleE2eSeed()` | `Targets.unload("XE", true)` | `file_prefix=XE`, `split_schema=true`, `one_table_one_file=true` | 2 |
| `cubrid/CubridToCubridTest.java` | `cubrid_to_cubrid` | `Sources.cubridE2eSeed()` | `Targets.cubridOnline()` | (none) | 9 |
| `cubrid/CubridToUnloadTest.java` (outer + `@Nested SplitPerTable`) | `cubrid_to_unload__split_per_table` | `Sources.cubridE2eSeed()` | `Targets.unload("demodb", true)` | `file_prefix=demodb`, `split_schema=true`, `one_table_one_file=true` | 2 |

`oracle/regression/`, `cubrid/regression/` exist with `package-info.java`
markers — no `@Test` yet.

#### CLI tests — `tests/e2e/src/test/java/com/cmt/e2e/tests/cli/CliTest.java` (8 `@Test`)

| ID | Method | Asserts |
|----|--------|---------|
| CLI-01 | `should_listAllSubcommands_when_calledWithoutArgs` | `./migration.sh` exits 0; output mentions `start`, `script`, `log`, `report` |
| CLI-02 | `should_dispatchToStartHandler_when_invokedWithStartSubcommand` | `start` subcommand reaches its help branch (`-sd` token) |
| CLI-03 | `should_dispatchToScriptHandler_when_invokedWithScriptSubcommand` | `script` reaches its help (`-schema`) |
| CLI-04 | `should_dispatchToLogHandler_when_invokedWithLogSubcommand` | `log` reaches its help (`-ps`) |
| CLI-05 | `should_dispatchToReportHandler_when_invokedWithReportSubcommand` | `report` reaches its help (`-ao`) |
| CLI-06 | `should_fallbackToStartHelp_when_unknownCommand` | bogus subcommand falls through to start help |
| CLI-07 | `should_createWorkspaceDirectories_onAnyInvocation` | `workspace/cmt/log` and `workspace/cmt/report` are directories |
| CLI-08 | `should_appendToLogFile_onAnyInvocation` | `workspace/cmt/log/cubrid-migration.log` exists and grew |

### 4.2 E2 — Snapshot fact inventory

#### Online targets (`snapshots/{oracle,cubrid}_to_cubrid/*.txt`)

10 snapshot files per scenario. Names are identical between Oracle and
CUBRID online:

| File | Concern | ora→cub rows | cub→cub rows |
|------|---------|--------------|--------------|
| `classes.txt` | Tables/views per owner | 11 | 12 |
| `columns.txt` | Column type translation | 70 | 69 |
| `grants.txt` | Cross-schema GRANT | 4 | 4 |
| `pk.txt` | PKs with key columns inlined (one row per key column; composite PK = N rows) | 11 | 12 |
| `fk.txt` | FK indexes with key columns inlined | 3 | 3 |
| `unique.txt` | Unique non-PK indexes with key columns inlined | 1 | 1 |
| `indexes.txt` | Plain indexes with key columns + `func` expression (incl. `idxd_*`, `idxf_*`) | 3 | 2 |
| `representative_rows.txt` | 8 business spot-checks | 37 | 37 |
| `routines.txt` | FUNCTION/PROCEDURE preservation | 2 | 0 |
| `row_counts.txt` | Per-table row count | 10 | 11 |
| `serials.txt` | SEQUENCE→SERIAL `current_val` | 2 | 2 |
| `synonyms.txt` | Cross-schema synonym | 1 | 1 |

#### Dump targets (`snapshots/{oracle,cubrid}_to_dump/dumpfile/`)

| | oracle_to_unload__split_per_table | cubrid_to_unload__split_per_table |
|-|----------------|----------------|
| Total files | 37 | 32 |
| Distinct artifact kinds | 19 (incl. routine dirs/files) | 15 (no FUNCTION/PROCEDURE) |
| Schema dirs | MAIN_SCHEMA, REF_SCHEMA | MAIN_SCHEMA, REF_SCHEMA |

Artifact kinds matrix:

| Artifact kind | oracle_to_unload__split_per_table | cubrid_to_unload__split_per_table |
|---------------|----------------|----------------|
| `_class` (table DDL) | yes | yes |
| `_pk` | yes | yes |
| `_fk` | yes | yes |
| `_uk` | yes | yes |
| `_indexes` | yes | yes |
| `_synonym` | yes | yes |
| `_serial` | yes | yes |
| `_vclass` (view DDL) | yes | yes |
| `_vclass_query_spec` | yes | yes |
| `_grant.<schema>` | yes (`grant.REF_SCHEMA`) | yes |
| `_function`, `_function_header` | yes | no |
| `_procedure`, `_procedure_header` | yes | no |
| `_info` | yes | yes |
| `_updatestatistic` | yes | yes |
| `<SCHEMA>_clear.sql`, `_truncate.sql` | yes | yes |
| `MAIN_SCHEMA_drop_fk.sql` | yes | yes |
| `objects/<prefix>_<schema>_<table>` | 9 files | 12 files |
| `FUNCTION/`, `PROCEDURE/` | yes (1+1) | no |
| `lob/` (empty placeholder) | yes | yes |

### 4.3 E3 — Seed object enumeration

#### Oracle (`tests/e2e/src/test/resources/db/oracle/`)

- **Tables (10)**:
  - MAIN_SCHEMA business (`main_schema/V1`): `e2e_customer`, `e2e_order`,
    `e2e_order_line`, `e2e_employee`
  - MAIN_SCHEMA type-test (`main_schema/V3`): `e2e_text_types`,
    `e2e_numeric_types`, `e2e_temporal_types`, `e2e_binary_types`,
    `e2e_oracle_locator_types`
  - REF_SCHEMA (`ref_schema/V1`): `e2e_ref_audit`
- **Views**: 1 — `e2e_order_summary_v` (`main_schema/V2`)
- **Sequences**: 2 — `e2e_customer_seq`, `e2e_order_seq` (both `START WITH 5`)
- **Synonyms**: 1 — `e2e_ref_audit_syn` → `REF_SCHEMA.e2e_ref_audit`
- **Functions**: 1 — `e2e_customer_label_fn(NUMBER) RETURN VARCHAR2`
- **Procedures**: 1 — `e2e_upsert_customer_proc(NUMBER, VARCHAR2)`
- **Triggers**: 0
- **Indexes**: 10 PK, 1 UK, 3 FK, 1 plain, 1 desc (`idxd_*`),
  1 functional (`idxf_*`), 0 reverse
- **Grants**: 1 cross-schema GRANT
- **Comments**: 9 (table + column comments)
- **Check constraints**: 3

##### Oracle `e2e_*_types` columns (file `main_schema/V3__schema_type_test_tables.sql`)

| Table | Columns (name : Oracle type) |
|-------|------------------------------|
| `e2e_text_types` | `id NUMBER(10)`, `char_byte_col CHAR(3 BYTE)`, `char_char_col CHAR(3 CHAR)`, `varchar_byte_col VARCHAR2(10 BYTE)`, `varchar_char_col VARCHAR2(10 CHAR)`, `nchar_col NCHAR(10)`, `nvarchar_col NVARCHAR2(40)`, `clob_col CLOB`, `nclob_col NCLOB` |
| `e2e_numeric_types` | `id NUMBER(10)`, `integer_col INTEGER`, `decimal_col DECIMAL(20,4)`, `number_p0_col NUMBER(38,0)`, `number_ps_col NUMBER(20,6)`, `number_round_col NUMBER(8,-2)`, `number_any_col NUMBER`, `float_col FLOAT(30)`, `real_col REAL`, `binary_float_col BINARY_FLOAT`, `binary_double_col BINARY_DOUBLE` |
| `e2e_temporal_types` | `id`, `date_col DATE`, `ts6_col TIMESTAMP(6)`, `ts9_col TIMESTAMP(9)`, `tsltz6_col TIMESTAMP(6) WITH LOCAL TIME ZONE`, `tstz9_col TIMESTAMP(9) WITH TIME ZONE`, `interval_ds_col INTERVAL DAY(2) TO SECOND(6)`, `interval_ym_col INTERVAL YEAR(4) TO MONTH` |
| `e2e_binary_types` | `id`, `raw_col RAW(16)`, `blob_col BLOB` |
| `e2e_oracle_locator_types` | `id`, `rowid_col ROWID`, `urowid_col UROWID` |

#### CUBRID (`tests/e2e/src/test/resources/db/cubrid/`)

- **Tables (12)**: 4 business + 4 core type-test + 3 extension type-test + 1 REF
- **Views**: 1 — `e2e_order_summary_v`
- **Serials**: 2 — `e2e_customer_seq`, `e2e_order_seq`
- **Synonyms**: 1 (CUBRID 11.2+)
- **Functions / Procedures**: 0 each
- **Triggers**: 0
- **Indexes**: 11 PK, 1 UK, 3 FK, 1 plain, 1 desc, 1 functional (anti-coverage stripped), 1 reverse (`idxr_*`)
- **Grants**: 1
- **Comments**: inline `COMMENT` on `e2e_customer` and column
- **Check constraints**: 3

##### CUBRID `e2e_*_types` columns

| Table | Columns |
|-------|---------|
| `e2e_text_types` | `id INT`, `char_col CHAR(3)`, `varchar_col VARCHAR(10)`, `nchar_col NCHAR(3)`, `nvarchar_col NCHAR VARYING(40)`, `string_col STRING`, `clob_col CLOB` |
| `e2e_numeric_types` | `id`, `smallint_col SMALLINT`, `int_col INT`, `bigint_col BIGINT`, `numeric_col NUMERIC(20,6)`, `numeric_p0_col NUMERIC(38,0)`, `float_col FLOAT`, `double_col DOUBLE`, `monetary_col MONETARY` |
| `e2e_temporal_types` | `id`, `date_col DATE`, `time_col TIME`, `datetime_col DATETIME`, `timestamp_col TIMESTAMP`, `datetimetz_col DATETIMETZ`, `datetimeltz_col DATETIMELTZ`, `timestamptz_col TIMESTAMPTZ`, `timestampltz_col TIMESTAMPLTZ` |
| `e2e_binary_types` | `id`, `bit_col BIT(64)`, `varbit_col BIT VARYING(1024)`, `blob_col BLOB` |
| `e2e_cubrid_collection_types` | `id`, `set_int_col SET(INT)`, `multiset_str_col MULTISET(VARCHAR(20))`, `seq_double_col SEQUENCE(DOUBLE)` |
| `e2e_cubrid_enum_types` | `id`, `status_enum ENUM('NEW','HOLD','DONE','CANCEL')` |
| `e2e_cubrid_json_types` | `id`, `payload JSON` |

### 4.4 E4 — Representative-rows queries (8 per scenario)

| # | Label | Business fact verified |
|---|-------|------------------------|
| 1 | `customer business values (id=1)` | `customer_code/name/alias/status/credit_limit` round-trip |
| 2 | `order-line composite-key relationship (order_id=1, line_no=1)` | 3-table JOIN, composite PK relation intact |
| 3 | `employee self-reference (Manager A reports to CEO, employee_id=2)` | self-FK chain |
| 4 | `text type R_MIN row (id=3)` | text columns hold `'A'` literal |
| 5 | `numeric type R_REPRESENTATIVE row (id=6)` | numeric precision preserved |
| 6 | `temporal date R_EPOCH row (id=2)` | `date_col` = 1970-01-01 |
| 7 | `view is queryable (4 orders in dataset)` | `COUNT(*) FROM e2e_order_summary_v` |
| 8 | `ref_audit synonym access (1 row)` | `COUNT(*) FROM e2e_ref_audit_syn` |

Files: `tests/e2e/src/test/resources/queries/oracle_to_cubrid.sql`,
`cubrid_to_cubrid.sql`. All queries schema-qualify with
`"MAIN_SCHEMA"."<table>"` because verifier connects as `dba`.

### 4.5 E5 — Anti-coverage rules (sanitize)

Source: `tests/e2e/src/test/java/com/cmt/e2e/framework/runner/ScriptXmlBuilder.java`,
lines 106–169.

| # | Concern | Regex |
|---|---------|-------|
| 1 | Wall-clock timestamp suffix in `<migration name>` | `(<migration\s+name=\")([^\"]+?)_\d{12}(\")` |
| 2 | `wizard_start_date_time` value | `(wizard_start_date_time=\")\d{12}(\")` |
| 3a | `flyway_schema_history` multi-line `<table>` blocks | `(?s)\s*<table\b[^>]*\bname=\"flyway_schema_history\"[^>]*>.*?</table>\s*` |
| 3b | `flyway_schema_history` self-closing tags | `(?m)\s*<\w+\b[^>]*\bname=\"flyway_schema_history\"[^>]*/>\s*\R?` |
| 4 | CUBRID system schemas DBA/PUBLIC | `(?m)\s*<schema\s+source=\"(?:DBA\|PUBLIC)\"[^>]*/>\s*\R?` |
| 5a | `e2e_cubrid_collection_types` multi-line `<table>` | `(?s)\s*<table\b[^>]*\bname=\"e2e_cubrid_collection_types\"[^>]*>.*?</table>\s*` |
| 5b | `e2e_cubrid_collection_types` self-closing | `(?m)\s*<\w+\b[^>]*\bname=\"e2e_cubrid_collection_types\"[^>]*/>\s*\R?` |
| 6 | CUBRID functional indexes (`idxf_*`) | `(?m)\s*<index\b[^>]*\bname=\"idxf_[^\"]*\"[^>]*/>\s*\R?` |

Net: **5 distinct anti-coverage concerns** (timestamps, Flyway metadata,
CUBRID system schemas, CUBRID collection types, CUBRID functional
indexes), spawning 8 regex rules.

### 4.6 E6 — Counts summary

| Dimension | Oracle | CUBRID |
|-----------|--------|--------|
| Tables seeded | 10 | 12 |
| Views | 1 | 1 |
| Sequences / Serials | 2 | 2 |
| Synonyms | 1 | 1 |
| Functions | 1 | 0 |
| Procedures | 1 | 0 |
| Triggers | 0 | 0 |
| PK constraints | 10 | 11 |
| UK constraints | 1 | 1 |
| FK constraints | 3 | 3 |
| Plain indexes | 1 | 1 |
| Descending (`idxd_*`) | 1 | 1 |
| Functional (`idxf_*`) | 1 | 1 (anti-coverage) |
| Reverse (`idxr_*`) | 0 | 1 |
| Check constraints | 3 | 3 |
| Cross-schema GRANTs | 1 | 1 |
| `e2e_*_types` tables | 5 | 7 |

| Test counts | value |
|-------------|-------|
| Migration test classes | 6 (Oracle ON+unload, CUBRID ON+unload, Tibero ON+unload) |
| `@Test` (migration total) | 43 (13+2+13+2+11+2 — Tibero ON has 11 instead of 13: routines + representative_rows deferred) |
| CLI `@Test` | 8 |
| Online snapshot files per scenario | 12 (after index split + key-merge) |
| Dump artifact files (oracle_to_unload__split_per_table) | 37 |
| Dump artifact files (cubrid_to_unload__split_per_table) | 32 |
| Distinct dump artifact kinds (oracle) | 19 |
| Distinct dump artifact kinds (cubrid) | 15 |
| Representative-row labels per scenario | 8 |
| `sanitize()` regex rules | 8 |
| `sanitize()` distinct concerns | 5 |

---

## 5. The 5 sub-matrices — current state (computed by hand)

> Cell values evaluated 2026-04-30 from §3 (rows) × §4 (columns).
> ✓ verified · ✗ gap · ⊘ anti-coverage · — not applicable

### 5.1 M1 — Source × Target

7 source DBs × 5 target shapes = 35 cells. Currently 4 cells (11%) verified.

| Source ↓ \ Target → | ONLINE | DB_UNLOAD | CSV | SQL | XLS |
|---------------------|--------|-----------|-----|-----|-----|
| oracle | ✓ | ✓ | ✗ | ✗ | ✗ |
| cubrid | ✓ | ✓ | ✗ | ✗ | ✗ |
| mssql | DEFERRED | DEFERRED | ✗ | ✗ | ✗ |
| mariadb | DEFERRED | DEFERRED | ✗ | ✗ | ✗ |
| mysql | DEFERRED | DEFERRED | ✗ | ✗ | ✗ |
| informix | DEFERRED | DEFERRED | ✗ | ✗ | ✗ |
| tibero | ✓ (since Phase 12 / D16) | ✓ | ✗ | ✗ | ✗ |

### 5.2 M2 — Object × Scenario

16 objects × 4 scenarios = 64 cells. ✓ 50, ✗ 10, ⊘ 2, — 2.

| Object ↓ \ Scenario → | ora→cub | ora→dump | cub→cub | cub→dump |
|----------------------|---------|----------|---------|----------|
| Table | ✓ classes.txt | ✓ `_class` | ✓ | ✓ |
| View | ✓ classes.txt(VIEW) | ✓ `_vclass`, `_vclass_query_spec` | ✓ | ✓ |
| Column | ✓ columns.txt | ✓ inside `_class` | ✓ | ✓ |
| PK | ✓ pk.txt | ✓ `_pk` | ✓ | ✓ |
| FK | ✓ fk.txt | ✓ `_fk` + drop_fk.sql | ✓ | ✓ |
| UK | ✓ unique.txt | ✓ `_uk` | ✓ | ✓ |
| Index — plain | ✓ | ✓ `_indexes` | ✓ | ✓ |
| Index — descending | ✓ indexes.txt (asc_desc=DESC) | ✓ | ✓ | ✓ |
| Index — functional | ✓ | ✓ | ⊘ D9 | ⊘ |
| Index — reverse | — | — | ✓ in seed (snapshot impact unverified) | ✓? |
| Sequence / Serial | ✓ serials.txt | ✓ `_serial` | ✓ | ✓ |
| Synonym | ✓ synonyms.txt | ✓ `_synonym` | ✓ (11.2+) | ✓ |
| **Trigger** | **✗** | **✗** | **✗** | **✗** |
| Function | ✓ routines.txt | ✓ `FUNCTION/` | ✗ seed=0 | ✗ |
| Procedure | ✓ routines.txt | ✓ `PROCEDURE/` | ✗ seed=0 | ✗ |
| Grant | ✓ grants.txt | ✓ `_grant.*` | ✓ | ✓ |
| **PartitionInfo / PartitionTable** | **✗** | **✗** | **✗** | **✗** |
| Record (rows) | ✓ row_counts + representative_rows | ✓ `objects/<table>` | ✓ | ✓ |

### 5.3 M3 — Type × Scenario

By source DB (the type catalog is source-side):

#### Oracle: 28 types, 24 ✓ / 4 ✗

Gaps: BFILE, LONG RAW, LONG, ARRAY (VARRAY/nested table). LONG RAW/LONG
are deprecated 11g — likely intentional skip. BFILE and VARRAY are real
gaps.

#### CUBRID: 29 types, 27 ✓ (3 are ⊘) / 2 ✗

Gaps: glo (legacy, intentional skip), object (no XML mapping — verify
behavior).
Anti-coverage: set, multiset, list (sequence) — stripped by D8 because
CMT cannot round-trip CUBRID collection columns.

### 5.4 M4 — Option × Scenario ★ (the most empty matrix)

19 correctness-affecting options × 4 scenarios. Only 3 options are
explicitly declared in any scenario's `@MigrationE2E.options[]`:

| Option ↓ \ Scenario → | ora→cub | ora→dump | cub→cub | cub→dump |
|----------------------|---------|----------|---------|----------|
| **destType** | ONLINE ✓ | DB_UNLOAD ✓ | ONLINE ✓ | DB_UNLOAD ✓ |
| **splitSchema** | (N/A) | ✓ true | (N/A) | ✓ true |
| **oneTableOneFile** | (N/A) | ✓ true | (N/A) | ✓ true |
| **targetFilePrefix** | (N/A) | ✓ "XE" | (N/A) | ✓ "demodb" |
| targetCharSet | implicit default | implicit | implicit | implicit |
| targetFileTimeZone | implicit | implicit | implicit | implicit |
| maxCountPerFile | (N/A) | implicit (0) | (N/A) | implicit |
| destType ∈ {CSV, SQL, XLS} | — | ✗ | — | ✗ |
| addUserSchema | implicit | implicit | implicit | implicit |
| createConstrainsBeforeData | implicit | implicit | implicit | implicit |
| updateStatistics | implicit | indirectly via `_updatestatistic` artifact | implicit | indirect |
| isWriteErrorRecords | implicit | implicit | implicit | implicit |
| exportNoSupportObjects | implicit | implicit | implicit | implicit |
| selectedSrcSchemas | ✓ MAIN+REF | ✓ | ✓ | ✓ |

→ **3 explicitly declared / 16 implicit-default-dependent** — direct
violation of §12.0 "no implicit defaults". This is the single most
valuable thing the matrix exposes today.

### 5.5 M5 — Subcommand × Test

| Subcommand | dispatch | flag-level | output-content |
|------------|----------|------------|----------------|
| `start` | ✓ CLI-02 | ✗ (`-s,-t,-sd,-td,-tp,-xml,-mm,-rm,-do` not flag-tested) | partial (migration TCs verify outcome but not start-handler-specific behavior) |
| `script` | ✓ CLI-03 | ✗ (documented `-s,-t,-o,-schema` and undocumented `-template,-ha,-err,-tc,-pfc,-cc`) | partial (ScriptXmlBuilder verifies sanitized output, no flag toggling) |
| `log` | ✓ CLI-04 | ✗ (`-l,-ps,<mh_file>`) | ✗ (rendering unverified) |
| `report` | ✓ CLI-05 | ✗ (`-l,-ao,<mh_file>`) | ✗ (rendering unverified) |

`log` / `report` rendering is the **core function of those handlers** —
currently only their dispatch is verified.

---

## 6. Concrete gaps the matrix reveals (5 backlog items)

These are real, independent of whether the matrix is automated. Captured
here so the value of the analysis isn't lost during the deferral.

| # | Gap | From | Why it matters |
|---|-----|------|----------------|
| **G1** | 16 options leaning on implicit CMT defaults | M4 | Direct violation of self-imposed §12.0 policy. Highest-priority "we said we'd do this and we don't." |
| **G2** | Trigger has 0 seed across both source DBs | M2 row 13 | `OracleSchemaFetcher.buildTriggers` (line 864) and `CUBRIDSchemaFetcher.buildTriggers` (line 1789) actively fetch them — not exotic |
| **G3** | Partition has 0 seed | M2 row 17 | `buildPartitions` (Oracle 298, CUBRID 1193) supports range/list/hash + sub-partition. Big surface, untouched. |
| **G4** | CSV/SQL/XLS dump targets unsupported in framework | M1 cols 3-5 | `Targets` factory only emits ONLINE + DB_UNLOAD. Each missing format is a backlog item. |
| **G5** | `log` / `report` content rendering unverified | M5 rows 3-4 | The handlers' primary responsibility. Dispatch-only coverage is shallow. |

---

## 7. Build/maintain analysis

### 7.1 Difficulty per piece

| Piece | Time | Difficulty | Risk |
|-------|------|------------|------|
| `dbobject/*.java` enumerate | 30 min | ★ | very low |
| Type catalog XML parsing | 2 hr | ★ | low (XML stable) |
| `@MigrationE2E` annotation parsing | 1 hr | ★ | low (regex sufficient) |
| `snapshots/<id>/*.txt` walk | 30 min | ★ | very low |
| Seed SQL → object/type extraction | 4–8 hr | ★★ | DDL regex brittle (Oracle `NUMBER(p,s)` commas, multi-line) |
| `MigrationConfiguration.java` setter grep | 4 hr | ★★ | vestigial setters get included |
| Cell evaluation rules (object × scenario) | 1–2 days | ★★★ | "tested" definition varies per object kind (e.g. `routines.txt` covers both Function and Procedure) |
| Annotation ↔ `target()` drift lint | 1–2 days | ★★★★ | needs real Java parsing (javaparser/tree-sitter) |
| Anti-coverage rule extraction | 4 hr | ★★ | meta-regex parsing of `sanitize()` |
| Console flag exercise vs presence | 0.5–1 day | ★★★ | `CliTest` asserts `output.contains("-sd")` — hard to auto-classify as "exercising" vs "presence-asserting" |

**Total for working generator: 5–7 days**
**Plus lint + CI integration: +3–5 days**
**Plus stabilization: +α**

### 7.2 Maintenance cost

Generator breaks silently when CMT changes:
- `MigrationConfiguration.java` setter rename → option row vanishes
- `Oracle2CUBRID.xml` structure shift → type parser breaks
- New source DB plugin with different XML naming → not auto-detected
- New `dbobject/` class → row auto-added but no judgment whether it
  should be tested

Detecting silent drift requires **expected lower-bound counts** baked
into the generator, which is itself maintenance.

### 7.3 Implementation options weighed

| | A. Full auto | B. Semi-auto (YAML-mediated) | C. Handwritten + lint | D. Defer |
|-|--------------|------------------------------|-----------------------|----------|
| Initial cost | 5–7 days | 1–2 days | 0.5 day | 2–3 hr (this doc) |
| Maintenance cost | high | low (YAML edit only) | very low | very low (stale) |
| Drift detection | automatic | our side auto, CMT side via PR review | E2E side only | none |
| First value | week 1 | day 2 | day 0.5 | immediate |
| False ✓ risk | medium (silent breakage) | low (YAML explicit) | low | dependent on humans |

---

## 8. Recommended path on resumption

**Option B (semi-auto) is the sweet spot** — committed to record now,
applied when work resumes.

Architecture:

```
tools/coverage-matrix/                                 ← new module
├── README.md
├── generate.sh                                        ← entry point
├── lib/
│   ├── extract_e2e_coverage.py                        ← scans tests/e2e/
│   │   ├─ regex parse @MigrationE2E + @DisplayName
│   │   ├─ scan db/<src>/{init,ref_schema,main_schema}/V*.sql
│   │   └─ scan snapshots/<scenario>/*.txt → verified concerns
│   └── render_matrix.py                               ← Markdown renderer
└── coverage/
    └── cmt_capability_surface.yaml                    ← hand-curated
        # rows of each sub-matrix, in YAML
        # version:  CMT 11.4
        # oracle_types: [BFILE, BLOB, RAW, ...]      (28 entries)
        # cubrid_types: [...]                        (29 entries)
        # object_kinds: [Table, View, ...]           (16 entries)
        # migration_options:                         (~23)
        #   - {name: splitSchema, default: false, category: dump}
        #   - ...
        # console_subcommands: [start, script, log, report]
```

Why YAML wins:
1. No CMT parser needed — eliminates the brittlest layer
2. CMT version bump → YAML edit only (1 PR, explicit)
3. YAML diff in PR review = visible CMT-surface change
4. `coverage/cmt_capability_surface.yaml` itself becomes a committed
   *definition of the CMT surface we agreed to test* — aligns with §12.0
   no-implicit-defaults principle

Output: `tests/e2e/docs/COVERAGE_MATRIX.md` (5 sub-matrix tables).

CI:
```yaml
- name: Coverage Matrix drift check
  run: |
    cd tools/coverage-matrix && ./generate.sh
    git diff --exit-code tests/e2e/docs/COVERAGE_MATRIX.md
```

---

## 9. Resumption checklist

When picking this back up:

1. **Re-validate enumeration** — read this document's §3 and §4.
   Spot-check 5 file:line citations to confirm CMT and E2E structure
   haven't drifted significantly. (Half a day if structure is stable;
   re-run the agents if not.)
2. **Decide entry point**:
   - (a) Phase 11.1 only — handwritten matrix v1 (1 day): immediate
     value from G1–G5 visibility, no infra change.
   - (b) Phase 11.1 + 11.2-Option-B (3–4 days combined): handwritten v1
     becomes the YAML, generator emits it byte-identical.
3. **If skipping straight to (b):** §8's directory layout is the target;
   §3 rows become the YAML content; §4 extraction logic becomes the
   Python scripts.
4. **Don't skip to Option A (full auto)** — diminishing returns vs B,
   high maintenance, brittle.

---

## 10. References

- `docs/ARCHITECTURE.md` §12 — naming convention & §12.0 no-implicit-defaults
  principle (closely related)
- `docs/ARCHITECTURE.md` D14 — naming-convention ADR (precedes the
  matrix work)
- `docs/ARCHITECTURE.md` D15 — this deferral ADR
- `docs/SEED_DATA_GUIDE.md` — strategy for seed evolution (matrix gaps
  G2/G3 will trigger seed work)
- CMT plugin source: `cubrid-migration/plugins/com.cubrid.cubridmigration.core/`
- Console source: `cubrid-migration/plugins/com.cubrid.cubridmigration.command/`

---

## Appendix A. Conversation context

This document captures a conversation that happened on 2026-04-30 in
the same session as Phase 10 (naming convention work). Sequence:

1. Naming convention §12 finalized (TC names, package layout,
   `@MigrationE2E.options[]`, no-implicit-defaults principle as §12.0).
2. User asked: "Matrix 측정 — how to do this rigorously, not by guessing?"
3. Two research agents enumerated CMT capabilities and our E2E coverage
   from the codebase (output preserved in §3 and §4).
4. 5-sub-matrix design proposed (§5).
5. User asked: "Is auto-tooling for this hard?"
6. Build/maintain analysis (§7) showed it's a 1-sprint effort with
   non-trivial maintenance.
7. User chose to defer and asked for this document.

The deferral is a conscious cost/value choice, not abandonment. The
matrix gaps G1–G5 (§6) are real and actionable independent of the
tooling — work on those can proceed without the matrix being automated.
