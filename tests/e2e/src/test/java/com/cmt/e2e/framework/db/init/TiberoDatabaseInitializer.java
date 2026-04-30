package com.cmt.e2e.framework.db.init;

import com.cmt.e2e.framework.db.containers.TiberoContainer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Raw-JDBC seed driver for Tibero — Flyway has no Tibero database plugin
 * (community or vendor) so we drop down to {@link ClasspathSqlRunner},
 * which executes lexically-sorted {@code *.sql} files from a classpath
 * directory through plain JDBC.
 *
 * <p>Two-phase usage mirrors {@code OracleSource}:
 * <ol>
 *   <li>{@link #initAsSys(String)} — connect as {@code SYS} and run
 *       bootstrap scripts (typically the {@code CREATE USER MAIN_SCHEMA}
 *       file in {@code db/tibero/init/}). Tibero's Docker image has no
 *       {@code /container-entrypoint-initdb.d/} hook, so we run init
 *       through JDBC after the listener is up.</li>
 *   <li>{@link #migrateAs(String, String, String)} — connect as the
 *       freshly-created user and apply schema/data files in lexical
 *       order. Files use a {@code V*__} prefix for visual sorting and
 *       consistency with the Flyway-driven sources, even though we
 *       don't track a {@code flyway_schema_history}.</li>
 * </ol>
 *
 * <p>Failure mode: any single SQL statement that fails surfaces as a
 * {@link ClasspathSqlRunner.SqlRunnerException} naming the resource
 * path and statement number, so post-mortem from a stack trace lands
 * directly on the offending line.
 */
public final class TiberoDatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(TiberoDatabaseInitializer.class);

    private final TiberoContainer container;

    private TiberoDatabaseInitializer(TiberoContainer container) {
        this.container = container;
    }

    public static TiberoDatabaseInitializer of(TiberoContainer container) {
        if (container == null) throw new IllegalArgumentException("container must not be null");
        return new TiberoDatabaseInitializer(container);
    }

    /**
     * Run init scripts as {@code SYS}. Use for {@code CREATE USER ...}
     * and other DBA-only DDL that must precede {@link #migrateAs}.
     *
     * @param classpathDir resource directory under
     *                     {@code src/test/resources/}
     *                     (e.g. {@code "db/tibero/init"})
     */
    public TiberoDatabaseInitializer initAsSys(String classpathDir) {
        log.info("[TiberoDatabaseInitializer] init as SYS — classpathDir='{}'", classpathDir);
        ClasspathSqlRunner.runDirectory(
            container.getJdbcUrl(null, null),
            container.getDbaUser(),
            container.getDbaPassword(),
            classpathDir);
        return this;
    }

    /**
     * Apply schema/data scripts as a non-DBA user. Files run in lexical
     * order ({@code V1__} before {@code V99__}).
     *
     * @param user         login user (e.g. {@code MAIN_SCHEMA})
     * @param password     login password
     * @param classpathDir resource directory under
     *                     {@code src/test/resources/}
     *                     (e.g. {@code "db/tibero/main_schema"})
     */
    public TiberoDatabaseInitializer migrateAs(String user, String password, String classpathDir) {
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("user must not be blank");
        }
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }
        log.info("[TiberoDatabaseInitializer] migrate as user='{}' — classpathDir='{}'", user, classpathDir);
        ClasspathSqlRunner.runDirectory(
            container.getJdbcUrl(null, null),
            user, password, classpathDir);
        return this;
    }
}
