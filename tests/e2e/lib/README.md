# `tests/e2e/lib/`

Local-only directory for **dev-private** JDBC drivers that cannot be
fetched from Maven Central. Drivers placed here are **not committed**
(see project `.gitignore`).

## What goes here

| File | Required for | License |
|------|--------------|---------|
| `tibero7-jdbc-17.jar` | Tibero source DB scenario | TmaxSoft proprietary — redistribution restricted |

If the directory is empty, the build still works — the Tibero scenario
just skips at test time via `@EnabledIf` (see
`framework/db/containers/TiberoEnvironment`). All non-Tibero scenarios
(Oracle, CUBRID) are unaffected.

## Why a project-local directory instead of Maven Central or `~/.m2`

The TmaxSoft Tibero JDBC driver is not on Maven Central (license
restrictions). Two alternatives were considered and rejected:

1. **`mvn install:install-file` per dev machine** — works but every
   developer + CI runner has to repeat the install before the project
   builds. A small one-time barrier multiplied by everyone.
2. **Project-local Maven repo** (`lib-repo/com/tmax/...`) — committed
   POM stub + jar arrangement that Maven can pull from a `file://`
   repository. Works but commits the proprietary jar to the repo,
   which we want to avoid.

This directory is the smallest workable middle ground: each developer
places the jar locally, Maven's `tibero` profile auto-activates on
its presence, and the public clone has none of TmaxSoft's IP.

## How to obtain `tibero7-jdbc-17.jar`

The jar ships inside the Tibero container image at
`/opt/tibero7/client/lib/tibero7-jdbc.jar` (or one of the JDK-target
variants like `tibero7-jdbc-17.jar`). Internal devs can:

```bash
docker create --name tibero-jdbc-extract <internal-tibero-image>
docker cp tibero-jdbc-extract:/opt/tibero7/client/lib/tibero7-jdbc-17.jar \
          tests/e2e/lib/
docker rm tibero-jdbc-extract
```

Or download from TmaxSoft TechNet if you have an account.

## How the jar is wired (when present)

- `tests/e2e/pom.xml` declares the dependency inside a `<profile id="tibero">`
  that activates only when the jar exists at this path. Without the
  jar, the dep is not declared and the build proceeds without Tibero.
- The same profile copies the jar to `target/test-classes/driver/` so
  `JdbcDriverJars.latest(DB.TIBERO)` finds it next to the
  Maven-Central drivers.
- `TiberoEnvironment.isAvailable()` checks both
  `Class.forName("com.tmax.tibero.jdbc.TbDriver")` (driver on
  classpath) and the license file's existence; Tibero `@Test` methods
  are gated on this via `@EnabledIf`.
