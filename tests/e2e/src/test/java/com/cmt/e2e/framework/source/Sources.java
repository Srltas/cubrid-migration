package com.cmt.e2e.framework.source;

/**
 * Factory for E2E source databases. Each method returns a fully-configured
 * {@link Source} with the canonical e2e seed for that engine.
 *
 * <p>Adding a new source DB means: (1) add a {@code XxxSource} class in
 * this package, (2) add a factory method here, (3) add the seed under
 * {@code src/test/resources/db/<engine>/}.
 */
public final class Sources {

    private Sources() {}

    /** Oracle 11g XE with the two-user (REF + MAIN) e2e seed. */
    public static Source oracleE2eSeed() {
        return new OracleSource();
    }

    /** CUBRID 11.4 with the two-user (REF + MAIN) e2e seed. */
    public static Source cubridE2eSeed() {
        return new CubridSource();
    }
}
