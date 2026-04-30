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
 * <p><b>Scenario id grammar</b> (ARCHITECTURE.md §12):
 * <pre>
 *   &lt;source&gt;_to_&lt;target&gt;[__&lt;discriminator&gt;]
 * </pre>
 * Examples:
 * <ul>
 *   <li>{@code "oracle_to_cubrid"} — the only Oracle → CUBRID online TC</li>
 *   <li>{@code "oracle_to_dump__flat"} — Oracle → dump producing a flat
 *       single-file output</li>
 *   <li>{@code "oracle_to_cubrid__bug_cmt_1234"} — regression fixture</li>
 * </ul>
 *
 * <p><b>No "default" TC.</b> Every TC explicitly lists in
 * {@link #options()} the CMT option values that affect its verification.
 * CMT defaults can change between releases — relying on them silently
 * shifts a test's meaning. ARCHITECTURE.md §12.0.
 *
 * <pre>{@code
 * @MigrationE2E(
 *     name = "oracle_to_dump__flat",
 *     options = {
 *         "split_schema=false",
 *         "one_table_one_file=false",
 *         "file_prefix=XE",
 *     })
 * class OracleToDumpFlatTest extends AbstractMigrationE2E { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MigrationE2E {
    /** Scenario id, e.g. {@code "oracle_to_cubrid"}. See class javadoc for grammar. */
    String name();

    /**
     * Declarative list of CMT options that this TC explicitly configures
     * and whose values affect the verification. Free-form
     * {@code "key=value"} strings — metadata for reviewers and future
     * Coverage Matrix tooling. The actual values are still set by
     * {@link AbstractMigrationE2E#target()} (and where applicable
     * {@link AbstractMigrationE2E#source()}); this annotation must agree
     * with them.
     *
     * <p>List every option whose value matters to this TC's snapshots
     * or assertions. Do not list noise. Do not omit an option whose
     * value the test depends on, even if you happen to want today's
     * CMT default — that creates a silent dependency on CMT's default
     * which can shift between releases.
     */
    String[] options() default {};
}
