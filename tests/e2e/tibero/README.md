# `tests/e2e/tibero/` — Tibero source-DB scaffolding

Custom Docker image + license file that make Tibero usable as an E2E
source database in our suite. Read this before touching anything in here.

## Why Tibero needs special handling

Tibero is the only source DB in our suite that requires *human-driven
infrastructure setup* before any test can run. Both blockers are
non-code:

1. **Hostname-bound license file.** Tibero refuses to boot unless the
   container's hostname matches the licensee string in `license.xml`.
   Our license is bound to `tibero-3-100`; `TiberoContainer` forces this
   hostname via `withCreateContainerCmdModifier(cmd.withHostName(...))`.
2. **JDBC driver not on Maven Central.** `tibero7-jdbc-17.jar` is
   committed to `tests/e2e/lib/` (system-scope dep) — see
   `tests/e2e/lib/README.md` for provenance.

The custom `faketime-tibero:2026-fixed` image addresses a third issue —
the trial license is 30-day. The image bakes `libfaketime` via
`LD_PRELOAD` (`FAKETIME=-99d`) so the in-container clock stays inside
the validity window indefinitely *as long as host-time minus 99 days
is still inside the license validity*.

## Files in this directory

| File | Purpose | Refresh trigger |
|------|---------|-----------------|
| `dockerfile` | Builds `faketime-tibero:2026-fixed` from `tiberoofficial/tibero:7.2.4` + `libfaketime` | Tibero base image bump or libfaketime bug |
| `license.xml` | Trial license for hostname `tibero-3-100`; valid 2026-01-21 → 2026-02-19 | License renewal (see SOP below) |

The `license.xml` is committed because losing it means the entire
Tibero scenario is unbootable until somebody re-requests one from
TmaxSoft TechNet. It is *trial-only* and not a production secret.

## Building the image

```bash
cd tests/e2e/tibero
docker build --platform linux/amd64 -f dockerfile -t faketime-tibero:2026-fixed .
```

The image is amd64-only; on Apple Silicon hosts everything runs under
emulation (Tibero boot ~120-150 s).

## Manual smoke (matches the Testcontainers config)

```bash
docker run --platform linux/amd64 \
  --name faketime-tibero -h tibero-3-100 \
  -p 8629:8629 -e TB_ROOT_PASSWORD=tibero123 \
  -v $(pwd)/tests/e2e/tibero/license.xml:/opt/tibero7/license/license.xml \
  -d faketime-tibero:2026-fixed
```

Wait until `docker logs` shows `Tibero is Ready To Use!` (~2 min) and
connect with `tibero7-jdbc-17.jar` at `jdbc:tibero:thin:@localhost:8629:tibero`
(user `sys`, password `tibero123`).

## License renewal SOP (recurs every ~3 months)

The current image's `FAKETIME=-99d` keeps in-container time at
`host_now - 99 days`. License validity is 2026-01-21 → 2026-02-19.
Therefore the image effectively expires when `host_now - 99 days`
exceeds 2026-02-19, i.e. around **2026-05-29**. After that, Tibero
boot fails with a license-expired error.

When that happens (or sooner):

1. **Request a new trial license** at
   <https://technet.tmaxsoft.com> → Tibero 7 → 30-day trial.
2. When asked for hostname, enter exactly `tibero-3-100` (the value
   the rest of the codebase assumes — see
   `TiberoContainer.FIXED_HOSTNAME`). Do **not** invent a new hostname
   without coordinating a code change.
3. Replace `license.xml` and commit.
4. Optionally, update the `FAKETIME` offset in `dockerfile` and rebuild
   the image so the offset still lands inside the new validity window.
   For a typical 30-day license issued today, `FAKETIME="-1d"` puts the
   in-container clock 1 day in the past relative to host time —
   compatible for the next ~29 days.
5. Verify by re-running `mvn test -Dtest=TiberoToCubridTest` in capture
   mode and checking `git diff src/test/resources/snapshots/tibero_*`
   is empty.

## What's NOT here

- **Tibero JDBC driver** — `../lib/tibero7-jdbc-17.jar`. Separate so
  it's discoverable via the standard `JdbcDriverJars` pattern.
- **Seed SQL** — `../src/test/resources/db/tibero/`. Standard layout.
- **Container class** — `../src/test/java/com/cmt/e2e/framework/db/containers/TiberoContainer.java`.
- **Testcase classes** — `../src/test/java/com/cmt/e2e/tests/migration/tibero/`.
