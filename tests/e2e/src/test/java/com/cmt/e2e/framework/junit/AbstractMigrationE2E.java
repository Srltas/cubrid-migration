package com.cmt.e2e.framework.junit;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.cmt.e2e.framework.runner.Migration;
import com.cmt.e2e.framework.runner.MigrationOutcome;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.target.Target;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for migration E2E tests. Owns the Source/Target lifecycle
 * with class-level scope (PER_CLASS) and runs the migration once in
 * {@link #e2eStartup()}, caching the outcome so that every {@code @Test}
 * verifies a single fact against the same migration result.
 *
 * <p>Subclass contract:
 * <ul>
 *   <li>Annotate with {@link MigrationE2E} to bind a scenario name.</li>
 *   <li>Implement {@link #source()} and {@link #target()} returning
 *       freshly-constructed (not yet started) instances.</li>
 *   <li>Use {@link #run()} inside test methods to access the cached
 *       {@link MigrationOutcome}.</li>
 * </ul>
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@code @BeforeAll}: source.start() → target.start() → migration.run()
 *       → cache outcome.</li>
 *   <li>each {@code @Test}: read cached outcome, assert one fact.</li>
 *   <li>{@code @AfterAll}: target.close() → source.close().</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractMigrationE2E {

    private static final Logger log = LoggerFactory.getLogger(AbstractMigrationE2E.class);

    /** Working dir root for v2 runner output (script.xml, raw CMT XML). */
    private static final Path WORK_ROOT = Paths.get("target", "e2e-v2");

    private Source source;
    private Target target;
    private MigrationOutcome cachedOutcome;

    /** Subclass returns a freshly-constructed source. Called once per class. */
    protected abstract Source source();

    /** Subclass returns a freshly-constructed target. Called once per class. */
    protected abstract Target target();

    @BeforeAll
    final void e2eStartup() throws Exception {
        this.source = source();
        this.target = target();
        log.info("[{}] starting source ({}) and target", scenarioName(), source.type());
        source.start();
        target.start();

        Path workDir = WORK_ROOT.resolve(scenarioName());
        log.info("[{}] running migration; workDir={}", scenarioName(), workDir);
        this.cachedOutcome = new Migration(source, target, scenarioName()).run(workDir);
        log.info("[{}] migration finished", scenarioName());
    }

    @AfterAll
    final void e2eShutdown() {
        if (target != null) {
            try { target.close(); } catch (Exception e) { log.warn("target.close failed", e); }
        }
        if (source != null) {
            try { source.close(); } catch (Exception e) { log.warn("source.close failed", e); }
        }
    }

    /**
     * Cached migration outcome. Available from every {@code @Test} method;
     * the actual migration ran once in {@code @BeforeAll}.
     */
    protected final MigrationOutcome run() {
        if (cachedOutcome == null) {
            throw new IllegalStateException(
                "run() called before @BeforeAll completed — possible JUnit lifecycle misconfiguration.");
        }
        return cachedOutcome;
    }

    /**
     * Scenario id from {@link MigrationE2E}. Used by the verify layer to
     * resolve {@code snapshots/<name>/} and {@code queries/<name>.sql}.
     */
    protected final String scenarioName() {
        MigrationE2E ann = getClass().getAnnotation(MigrationE2E.class);
        if (ann == null) {
            throw new IllegalStateException(
                "Test class " + getClass().getSimpleName()
                    + " is missing @MigrationE2E. Add @MigrationE2E(name = \"<scenario>\").");
        }
        return ann.name();
    }

    /** Verify-layer access. Tests should prefer the fluent {@link #run()} API. */
    protected final Source sourceInstance() { return source; }

    /** Verify-layer access. Tests should prefer the fluent {@link #run()} API. */
    protected final Target targetInstance() { return target; }
}
