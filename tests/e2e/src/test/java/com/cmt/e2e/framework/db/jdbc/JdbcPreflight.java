package com.cmt.e2e.framework.db.jdbc;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.List;

import com.cmt.e2e.framework.db.jdbc.strategy.JdbcUrlStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JdbcPreflight {
    private static final Logger log = LoggerFactory.getLogger(JdbcPreflight.class);

    private JdbcPreflight() {}

    public static void run(Path driverJar, JdbcUrlStrategy strategy,
                           String host, int port, String dbname, String user, String pass, String charset) {
        log.debug("[JDBC-PREFLIGHT] jar={}", driverJar);
        log.debug("[JDBC-PREFLIGHT] base cfg host={}, port={}, dbname={}, user={}, pass={}, charset={}",
            host, port, dbname, user, mask(pass), charset);

        try (var cl = new URLClassLoader(new URL[]{driverJar.toUri().toURL()},
            Thread.currentThread().getContextClassLoader())) {
            Class<?> drvKlass = Class.forName(strategy.getDriverClassName(), true, cl);
            Driver drv = (Driver) drvKlass.getDeclaredConstructor().newInstance();

            List<JdbcConnectionTry> tries = strategy.buildConnectionAttempts(host, port, dbname, user, pass, charset);

            for (JdbcConnectionTry t : tries) {
                log.debug("\n[JDBC-PREFLIGHT] TRY {}", t.label());
                log.debug("[JDBC-PREFLIGHT] URL={}", t.url());
                log.debug("[JDBC-PREFLIGHT] PROPS={}", t.props());

                try (var conn = drv.connect(t.url(), t.props())) {
                    if (conn == null) throw new RuntimeException("Driver returned null connection");
                    log.debug("[JDBC-PREFLIGHT] OK {} :: {} {}", t.label(),
                        conn.getMetaData().getDatabaseProductName(),
                        conn.getMetaData().getDatabaseProductVersion());
                    return;
                } catch (SQLException se) {
                    log.debug("[JDBC-PREFLIGHT] FAIL {}", t.label());
                    log.debug("  SQLException: SQLState={}, errorCode={}, msg={}", se.getSQLState(), se.getErrorCode(), se.getMessage());
                    if (se.getCause() != null) log.debug("  cause", se.getCause());
                } catch (Exception e) {
                    log.debug("[JDBC-PREFLIGHT] FAIL {}", t.label());
                    log.debug("  CUBRIDException: {}", e.getMessage());
                    try {
                        var getErrorCode = e.getClass().getMethod("getErrorCode");
                        Object ec = getErrorCode.invoke(e);
                        log.debug("  getErrorCode={}", ec);
                    } catch (Throwable ignore) {}
                    if (e.getCause() != null) {
                        log.debug("  cause={}", e.getCause());
                    }
                } catch (Throwable th) {
                    log.debug("[JDBC-PREFLIGHT] FAIL {}", t.label());
                }
            }
            throw new AssertionError("[JDBC-PREFLIGHT] All JDBC connection attempts failed");
        } catch (Throwable t) {
            throw new AssertionError("[JDBC] direct connect FAILED  (jar=" + driverJar + ")", t);

        }
    }

    private static String mask(String s) {
        return s == null ? "" : s.replaceAll(".", "*");
    }
}
