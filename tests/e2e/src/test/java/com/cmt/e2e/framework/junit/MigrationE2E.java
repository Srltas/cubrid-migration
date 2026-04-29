package com.cmt.e2e.framework.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an E2E migration test class and binds it to a scenario name.
 * The name is the directory under {@code src/test/resources/snapshots/}
 * and {@code src/test/resources/queries/}.
 *
 * <p>Used by {@link AbstractMigrationE2E} (via {@link AbstractMigrationE2E#scenarioName()})
 * to resolve snapshot/query paths.
 *
 * <pre>{@code
 * @MigrationE2E(name = "oracle_to_cubrid")
 * class OracleToCubridTest extends AbstractMigrationE2E { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MigrationE2E {
    /** Scenario id, e.g. {@code "oracle_to_cubrid"}. */
    String name();
}
