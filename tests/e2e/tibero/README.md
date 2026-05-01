# `tests/e2e/tibero/` — Tibero source-DB scaffolding

Tibero is the only source DB in our suite that requires *human-driven
infrastructure setup* before any test can run. The setup needs three
**dev-private** assets that cannot live in this repo:

1. A **TmaxSoft trial / commercial license** (hostname-bound).
2. The **TmaxSoft JDBC driver jar** (not on Maven Central).
3. A **custom Docker image** built from `tiberoofficial/tibero` +
   `libfaketime` so the in-container clock stays inside the trial
   license validity window.

Without all three, the Tibero `@Test` methods skip via
`@EnabledIf("...TiberoEnvironment#isAvailable")` and the rest of the
suite (Oracle, CUBRID) runs unchanged. The public clone is intended to
work in this skipped state.

## What's in this directory

| File | In repo? | Purpose | Refresh trigger |
|------|---------|---------|-----------------|
| `dockerfile` | ✓ | Recipe to rebuild the custom Tibero image. Just `FROM tiberoofficial/tibero:7.2.4` + libfaketime install — no secrets. Anyone with TmaxSoft Docker Hub access can rebuild. | Tibero base bump or libfaketime issue |
| `restart-faketime-tibero.sh` | ✓ | Recreates the local smoke-test Tibero container with a `FAKETIME` offset calculated from the current date to the license start date. | License window automation change |
| `license.xml` | ✗ (`.gitignore`) | TmaxSoft trial license, hostname-bound. Place here on internal dev machines. | License renewal (see SOP below) |
| `README.md` | ✓ | This file. | Process change |

## What's elsewhere

| Path | Purpose |
|------|---------|
| `../lib/tibero7-jdbc-17.jar` | TmaxSoft JDBC driver jar — `.gitignore`d. See `../lib/README.md`. |
| `../src/test/java/com/cmt/e2e/framework/db/containers/TiberoContainer.java` | Testcontainers wrapper |
| `../src/test/java/com/cmt/e2e/framework/db/containers/TiberoEnvironment.java` | `@EnabledIf` pre-flight check |
| `../src/test/java/com/cmt/e2e/framework/source/TiberoSource.java` | `Source` impl + lifecycle |
| `../src/test/java/com/cmt/e2e/framework/db/init/TiberoDatabaseInitializer.java` | Raw-JDBC seed runner |
| `../src/test/java/com/cmt/e2e/tests/migration/tibero/` | Migration TCs |
| `../src/test/resources/db/tibero/` | Seed SQL (`init/`, `ref_schema/`, `main_schema/`) |
| `../src/test/resources/snapshots/tibero_to_*/` | Captured golden snapshots |

## Internal dev setup (one-time per machine)

### Step 1 — Obtain the JDBC jar

Place at `tests/e2e/lib/tibero7-jdbc-17.jar`. Two paths:

- **From a running Tibero container**:
  ```bash
  docker create --name tibero-jdbc-extract <your-internal-tibero-image>
  docker cp tibero-jdbc-extract:/opt/tibero7/client/lib/tibero7-jdbc-17.jar \
            tests/e2e/lib/
  docker rm tibero-jdbc-extract
  ```
- **TmaxSoft TechNet**: download from the JDBC bundle for Tibero 7 if
  you have an account.

### Step 2 — Obtain a license

Request a 30-day trial at <https://technet.tmaxsoft.com> (Tibero 7).

When asked for hostname, enter the value you plan to set as
`e2e.tibero.hostname` in `e2e-test.properties` (the example uses
`tibero-3-100`). The value must match the `<licensee>` suffix in
`license.xml` exactly — Tibero binds the license to that hostname and
refuses to start otherwise. Place the resulting `license.xml` at
`tests/e2e/tibero/license.xml` (or anywhere you prefer; the path goes
into `e2e.tibero.license`).

### Step 3 — Build the custom image

```bash
cd tests/e2e/tibero
docker build --platform linux/amd64 -f dockerfile -t faketime-tibero:2026-fixed .
```

The image is amd64-only; on Apple Silicon hosts everything runs under
emulation (Tibero boot ~120-150 s).

### Step 4 — Run the suite

Standard:
```bash
mvn test -Dtest=TiberoToCubridTest
```

The Maven `tibero` profile auto-activates from the jar's presence.
`TiberoEnvironment.isAvailable()` returns true → tests run.

### Step 5 — Required configuration

Three values are read by
`com.cmt.e2e.framework.core.E2eTestProperties` with precedence
**system property → `e2e-test.properties` → (no default; missing means "skip")**.

All three are **required**. If any is missing, blank, or its license
file does not exist on disk, the Tibero `@Test` methods skip via
`@EnabledIf("...TiberoEnvironment#isAvailable")` and surefire logs the
specific reason. The rest of the suite (Oracle, CUBRID) is unaffected.

| Key | Purpose |
|-----|---------|
| `e2e.tibero.image` | Docker image tag (built in Step 3) |
| `e2e.tibero.hostname` | License-bound hostname; matches `<licensee>` in `license.xml` |
| `e2e.tibero.license` | Host-side path to `license.xml` (relative paths resolve against the e2e module root) |

The in-container license path is fixed at
`/opt/tibero7/license/license.xml` (Tibero 7 install convention) and
lives as a hardcoded constant in `TiberoContainer`. Changing it would
require a custom dockerfile + listener config edit, so it is not
configurable here.

**Properties file (preferred for persistent local config)**:

```bash
cp tests/e2e/e2e-test.properties.example tests/e2e/e2e-test.properties
# edit the new file — fill in all three keys:
#   e2e.tibero.image=faketime-tibero:2026-fixed
#   e2e.tibero.hostname=tibero-3-100
#   e2e.tibero.license=tibero/license.xml
```

`tests/e2e/e2e-test.properties` is `.gitignore`d — your edits never
leak into the repo. Empty / blank values fall through to the next
level, so leaving a key with no value is the same as omitting it.

**Command-line system property (one-off / CI)**:

```bash
mvn test \
  -De2e.tibero.image=faketime-tibero:2026-fixed \
  -De2e.tibero.hostname=tibero-3-100 \
  -De2e.tibero.license=tibero/license.xml
```

Wins over the file when both are set — handy for CI runs that should
not depend on a developer-machine file.

## Manual smoke (matches the Testcontainers config)

```bash
docker run --platform linux/amd64 \
  --name faketime-tibero -h tibero-3-100 \
  -p 8629:8629 -e TB_ROOT_PASSWORD=tibero123 \
  -e FAKETIME="-100d" \
  -v $(pwd)/tests/e2e/tibero/license.xml:/opt/tibero7/license/license.xml \
  -d faketime-tibero:2026-fixed
```

Wait until `docker logs` shows `Tibero is Ready To Use!` (~2 min) and
connect with the JDBC driver at `jdbc:tibero:thin:@localhost:8629:tibero`
(user `sys`, password `tibero123`).

## Local smoke container refresh

The local smoke container should be recreated before the fake in-container
date reaches the license end date. Use the helper script instead of editing
`dockerfile` and rebuilding just to change `FAKETIME`.

```bash
cd tests/e2e/tibero
./restart-faketime-tibero.sh
```

The script:

1. Reads `start_date`, `end_date`, and `identified_by_host` from
   `license.xml`.
2. Calculates `FAKETIME` from today's local date to the license start
   date. With `start_date=2026-01-21`, running on `2026-05-01` becomes
   `FAKETIME=-100d`.
3. Checks whether a container named `faketime-tibero` exists.
4. If it is running, stops and removes it; if it exists but is stopped,
   removes it.
5. Creates a new container with the calculated `FAKETIME`, the license
   hostname, the mounted license file, and 1 GB shared memory.

The current example license is valid `2026-01-21` → `2026-02-19`.
That gives 29 days before the end date. With the script's fixed
2-day safety margin, recreate the container every 27 days. If run on
`2026-05-01`, the next safe refresh date is `2026-05-28`.

For a persistent local automation, schedule the script daily and let
`--if-due` skip until the 27-day refresh point:

```cron
0 10 * * * /Users/cubrid/Devel/cmt-console-e2e/cubrid-migration/tests/e2e/tibero/restart-faketime-tibero.sh --if-due >> /tmp/faketime-tibero-refresh.log 2>&1
```

The script uses the server's local date. If the machine must use a specific
date basis, set `TZ` in the cron line rather than adding another script option:

```cron
0 10 * * * TZ=Asia/Seoul /Users/cubrid/Devel/cmt-console-e2e/cubrid-migration/tests/e2e/tibero/restart-faketime-tibero.sh --if-due >> /tmp/faketime-tibero-refresh.log 2>&1
```

Useful overrides:

```bash
TIBERO_IMAGE=faketime-tibero:required-faketime ./restart-faketime-tibero.sh
TIBERO_LICENSE_PATH=/abs/path/to/license.xml ./restart-faketime-tibero.sh --plan
```

## License renewal SOP

When the time comes (or sooner):

1. Request a new trial license at TmaxSoft TechNet, using the same
   `e2e.tibero.hostname` value the codebase assumes.
2. Replace `tibero/license.xml` and **do not** commit (the path is
   `.gitignore`d for exactly this reason).
3. Run `./restart-faketime-tibero.sh --plan` to confirm the new calculated
   `FAKETIME` and refresh interval.
4. Run `./restart-faketime-tibero.sh` to recreate the local smoke
   container with the new calculated `FAKETIME`.
5. Verify: `mvn test -Dtest=TiberoToCubridTest` should be green; if
   capturing fresh snapshots, ensure
   `git diff src/test/resources/snapshots/tibero_*` is empty after a
   second non-capture run.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `Tibero @Test` methods all show "skipped" | `TiberoEnvironment.isAvailable()` returned false | Check the jar exists at `lib/tibero7-jdbc-17.jar` and the license at the configured path |
| Container boot hangs / fails with "license invalid" | Hostname or license expired | Verify `license.xml` `<identified_by_host>` matches the container hostname; run `restart-faketime-tibero.sh --plan` and check the fake date window |
| "No suitable driver found for jdbc:tibero:..." | Maven `tibero` profile not active (jar missing or wrong path) | Confirm `lib/tibero7-jdbc-17.jar` exists; rebuild with `mvn clean test-compile` |
| Image not found | Custom image not built or wrong tag | Run Step 3 above, or pass `-De2e.tibero.image=<your-tag>` |
