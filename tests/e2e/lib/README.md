# `tests/e2e/lib/`

Restricted-license JDBC drivers that are **not on Maven Central** and are
committed to the repository as a pragmatic compromise. The alternative
(per-machine `mvn install:install-file`) imposes setup friction every
developer machine + CI; for a single jar this trade-off is acceptable.

| File | Source | Used by |
|------|--------|---------|
| `tibero7-jdbc-17.jar` | TmaxSoft (license-restricted; bundled in `tiberoofficial/tibero` Docker image at `/opt/tibero7/client/lib/tibero7-jdbc.jar`) | `TiberoContainer` + `TiberoSource` |

## How the jar is wired

- `pom.xml` declares it as a `system`-scope dependency:
  ```xml
  <dependency>
    <groupId>com.tmax.tibero</groupId>
    <artifactId>tibero7-jdbc</artifactId>
    <version>17</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/tibero7-jdbc-17.jar</systemPath>
  </dependency>
  ```
  Maven warns that system-scope paths inside the project are non-portable;
  for our test-only module that's the intended trade-off (see
  `pom.xml` comment near the dep for the rationale).

- `maven-resources-plugin` copies the jar to
  `target/test-classes/driver/tibero7-jdbc-17.jar` during
  `generate-test-resources`, so `JdbcDriverJars.latest(DB.TIBERO)`
  finds it next to Maven-Central drivers without special-casing.

## Refreshing the jar

When TmaxSoft publishes a new patch:

1. Replace `tibero7-jdbc-17.jar` (keep the `-17` suffix — encodes JDK 17
   build target; matches our test JVM).
2. Run `mvn -q clean test-compile` to verify nothing else broke.
3. The variant suffix glob in `JdbcDriverJars.PATTERNS[DB.TIBERO]`
   accepts other `tibero7-jdbc-*.jar` filenames if you ever need to
   commit a different build target.
