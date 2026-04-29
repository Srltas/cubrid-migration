package com.cmt.e2e.framework.junit;

import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.target.Target;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for migration E2E tests. Owns the Source/Target lifecycle
 * with class-level scope (PER_CLASS) so containers + CMT execution run
 * once per test class and all {@code @Test} methods read a cached
 * outcome.
 *
 * <p>Subclass contract:
 * <ul>
 *   <li>Annotate with {@link MigrationE2E} to bind a scenario name.</li>
 *   <li>Implement {@link #source()} and {@link #target()} returning
 *       freshly-constructed (not yet started) instances.</li>
 *   <li>{@link #sourceInstance()} / {@link #targetInstance()} are
 *       available to verify-layer helpers; tests should not touch them
 *       directly.</li>
 * </ul>
 *
 * <p>The framework calls {@link Source#start()} / {@link Target#start()}
 * in {@link #e2eStartup()} and {@link AutoCloseable#close()} in
 * {@link #e2eShutdown()}. Migration execution is wired in Phase 2.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractMigrationE2E {

    private static final Logger log = LoggerFactory.getLogger(AbstractMigrationE2E.class);

    private Source source;
    private Target target;

    /** Subclass returns a freshly-constructed source. Called once per class. */
    protected abstract Source source();

    /** Subclass returns a freshly-constructed target. Called once per class. */
    protected abstract Target target();

    @BeforeAll
    final void e2eStartup() {
        this.source = source();
        this.target = target();
        log.info("[{}] starting source ({}) and target", scenarioName(), source.type());
        source.start();
        target.start();
        // Phase 2 lands here: cachedOutcome = new Migration(source, target).run();
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

    /** Verify-layer access. Tests should prefer the fluent {@code run()} API (Phase 2). */
    protected final Source sourceInstance() { return source; }

    /** Verify-layer access. Tests should prefer the fluent {@code run()} API (Phase 2). */
    protected final Target targetInstance() { return target; }
}
