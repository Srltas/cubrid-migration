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

When asked for hostname, enter exactly the value of
`e2e.tibero.hostname` (default `tibero-3-100`). Place the resulting
`license.xml` at `tests/e2e/tibero/license.xml`.

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

### Step 5 — Optional overrides

Three values are read by
`com.cmt.e2e.framework.core.E2eTestProperties` with precedence
**system property → `e2e-test.properties` → hardcoded default**.

**Properties file (preferred for persistent local config)**:

```bash
cp tests/e2e/e2e-test.properties.example tests/e2e/e2e-test.properties
# edit the new file — only fill in keys you actually want to override:
#   e2e.tibero.image=my-registry/tibero:custom-tag
#   e2e.tibero.hostname=my-license-hostname
#   e2e.tibero.license=/abs/path/to/license.xml
```

`tests/e2e/e2e-test.properties` is `.gitignore`d — your edits never
leak into the repo. Empty / blank values fall through to the next
level, so leaving a key with no value is the same as omitting it.

**Command-line system property (one-off / CI)**:

```bash
mvn test \
  -De2e.tibero.image=my-registry/tibero:custom-tag \
  -De2e.tibero.hostname=my-license-hostname \
  -De2e.tibero.license=/abs/path/to/license.xml
```

Wins over the file when both are set — handy for CI runs that should
not depend on a developer-machine file.

## Manual smoke (matches the Testcontainers config)

```bash
docker run --platform linux/amd64 \
  --name faketime-tibero -h tibero-3-100 \
  -p 8629:8629 -e TB_ROOT_PASSWORD=tibero123 \
  -v $(pwd)/tests/e2e/tibero/license.xml:/opt/tibero7/license/license.xml \
  -d faketime-tibero:2026-fixed
```

Wait until `docker logs` shows `Tibero is Ready To Use!` (~2 min) and
connect with the JDBC driver at `jdbc:tibero:thin:@localhost:8629:tibero`
(user `sys`, password `tibero123`).

## License renewal SOP (recurs every ~3 months)

The default image's `FAKETIME=-99d` keeps in-container time at
`host_now - 99 days`. The current example license is valid
2026-01-21 → 2026-02-19; the image effectively expires when
`host_now - 99 days` exceeds the license `<end_date>`, i.e. around
**2026-05-29** for that license. After that, Tibero boot fails.

When the time comes (or sooner):

1. Request a new trial license at TmaxSoft TechNet, using the same
   `e2e.tibero.hostname` value the codebase assumes.
2. Replace `tibero/license.xml` and **do not** commit (the path is
   `.gitignore`d for exactly this reason).
3. If needed, update the `FAKETIME` offset in `dockerfile` and rebuild
   the image so the in-container clock falls inside the new validity
   window. For a 30-day license issued today, `FAKETIME="-1d"` keeps
   the clock 1 day behind real time — comfortable for the next
   ~29 days.
4. Verify: `mvn test -Dtest=TiberoToCubridTest` should be green; if
   capturing fresh snapshots, ensure
   `git diff src/test/resources/snapshots/tibero_*` is empty after a
   second non-capture run.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `Tibero @Test` methods all show "skipped" | `TiberoEnvironment.isAvailable()` returned false | Check the jar exists at `lib/tibero7-jdbc-17.jar` and the license at the configured path |
| Container boot hangs / fails with "license invalid" | Hostname or license expired | Verify `license.xml` `<licensee>` matches `e2e.tibero.hostname`; check `<end_date>` vs `host_now - 99d` |
| "No suitable driver found for jdbc:tibero:..." | Maven `tibero` profile not active (jar missing or wrong path) | Confirm `lib/tibero7-jdbc-17.jar` exists; rebuild with `mvn clean test-compile` |
| Image not found | Custom image not built or wrong tag | Run Step 3 above, or pass `-De2e.tibero.image=<your-tag>` |
