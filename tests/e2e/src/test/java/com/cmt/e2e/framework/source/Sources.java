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

    /**
     * Tibero 7 (custom {@code faketime-tibero:2026-fixed} image) with the
     * two-user (REF + MAIN) e2e seed — business tables, view, sequence,
     * synonym, cross-schema GRANT. Type-test tables (V3) and PL/SQL
     * routines (V4) are deferred until the seed pipeline grows a
     * PL/SQL-aware applier.
     */
    public static Source tiberoE2eSeed() {
        return new TiberoSource();
    }
}
