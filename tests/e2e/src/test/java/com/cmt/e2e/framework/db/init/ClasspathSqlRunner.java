package com.cmt.e2e.framework.db.init;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes ad-hoc SQL files from the test classpath via JDBC.
 *
 * <p>Used for seed-bootstrap steps that do not fit the Flyway model — most
 * importantly running CUBRID {@code CREATE USER ...} as {@code dba} before
 * Flyway runs as the freshly-created users. Flyway's {@code V*.sql} naming
 * convention does not apply here; files are executed in lexical filename order.
 *
 * <p>Each file is split on top-level {@code ;} terminators (with naive
 * single-line {@code --} comment stripping) and statements are executed
 * one at a time so a syntax error reports the failing statement.
 */
public final class ClasspathSqlRunner {

    private static final Logger log = LoggerFactory.getLogger(ClasspathSqlRunner.class);

    private ClasspathSqlRunner() {}

    /**
     * Loads every {@code *.sql} file under the given classpath directory and
     * executes its statements through a JDBC connection authenticated as the
     * supplied user.
     *
     * @param jdbcUrl       JDBC URL of the target database
     * @param user          login user
     * @param password      login password (may be empty for users without one)
     * @param classpathDir  directory under {@code src/test/resources/}
     *                      (e.g. {@code "db/cubrid/init"})
     * @throws SqlRunnerException wrapping any underlying I/O or SQL failure
     */
    public static void runDirectory(String jdbcUrl, String user, String password, String classpathDir) {
        List<String> resourcePaths = listSqlResources(classpathDir);
        if (resourcePaths.isEmpty()) {
            log.info("[ClasspathSqlRunner] no .sql files under '{}' (skipping)", classpathDir);
            return;
        }

        log.info("[ClasspathSqlRunner] running {} file(s) under '{}' as user='{}'",
            resourcePaths.size(), classpathDir, user);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
            conn.setAutoCommit(true);
            for (String path : resourcePaths) {
                runResource(conn, path);
            }
        } catch (SQLException e) {
            throw new SqlRunnerException(
                "Failed to open JDBC connection for SQL bootstrap '" + classpathDir
                    + "' as user '" + user + "': " + e.getMessage(), e);
        }
    }

    private static void runResource(Connection conn, String resourcePath) {
        String sql = readResource(resourcePath);
        List<String> statements = splitStatements(sql);
        log.debug("[ClasspathSqlRunner] {} ({} statement{})", resourcePath,
            statements.size(), statements.size() == 1 ? "" : "s");

        try (Statement st = conn.createStatement()) {
            for (int i = 0; i < statements.size(); i++) {
                String stmt = statements.get(i).trim();
                if (stmt.isEmpty()) continue;
                try {
                    st.execute(stmt);
                } catch (SQLException e) {
                    throw new SqlRunnerException(
                        "SQL bootstrap failed at " + resourcePath + " statement #" + (i + 1)
                            + " — " + firstLine(stmt) + ": " + e.getMessage(), e);
                }
            }
        } catch (SQLException e) {
            throw new SqlRunnerException(
                "Failed to create statement against " + resourcePath + ": " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers — classpath resource discovery + SQL splitting
    // -------------------------------------------------------------------------

    private static List<String> listSqlResources(String classpathDir) {
        URL url = Thread.currentThread().getContextClassLoader().getResource(classpathDir);
        if (url == null) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        try {
            URI uri = url.toURI();
            Path dir;
            FileSystem fsToClose = null;
            try {
                if ("jar".equals(uri.getScheme())) {
                    fsToClose = FileSystems.newFileSystem(uri, java.util.Map.of());
                    dir = fsToClose.getPath(classpathDir);
                } else {
                    dir = Paths.get(uri);
                }
                try (Stream<Path> walk = Files.list(dir)) {
                    walk.filter(p -> p.getFileName().toString().endsWith(".sql"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(p -> out.add(classpathDir + "/" + p.getFileName().toString()));
                }
            } finally {
                if (fsToClose != null) fsToClose.close();
            }
        } catch (Exception e) {
            throw new SqlRunnerException(
                "Failed to enumerate SQL resources under " + classpathDir + ": " + e.getMessage(), e);
        }
        return out;
    }

    private static String readResource(String resourcePath) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new SqlRunnerException("Resource not found: " + resourcePath, null);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SqlRunnerException("Failed to read " + resourcePath + ": " + e.getMessage(), e);
        }
    }

    /**
     * Splits a SQL file into individual statements.
     *
     * <p>Two modes — auto-selected per file:
     * <ul>
     *   <li><b>Semicolon mode</b> (default) — drops {@code --} line comments
     *       and splits on top-level {@code ;}.</li>
     *   <li><b>Slash mode</b> — triggered when the file contains at least one
     *       line consisting solely of {@code /} (Oracle / Tibero PL/SQL block
     *       terminator convention). In this mode a line of just {@code /}
     *       ends the current statement and the trailing {@code ;} inside the
     *       block is left intact. Required for {@code CREATE FUNCTION /
     *       PROCEDURE / TRIGGER / ANONYMOUS BLOCK} bodies that contain inner
     *       {@code BEGIN...END;} blocks; the inner {@code ;} would otherwise
     *       prematurely terminate the statement.</li>
     * </ul>
     *
     * <p>Limitations (kept narrow on purpose):
     * <ul>
     *   <li>String escape: only backslash escape ({@code \'}) is recognised.
     *       Doubled-quote escape ({@code ''}) is NOT — a {@code ;} inside such
     *       a literal would split the statement incorrectly in semicolon
     *       mode. Slash mode side-steps this for PL/SQL bodies.</li>
     *   <li>No support for {@code /* ... *}{@code /} block comments or
     *       dollar-quoting.</li>
     *   <li>Cannot mix the two modes in one file — once a {@code /} terminator
     *       line is seen, the whole file is treated as slash-mode.</li>
     * </ul>
     */
    static List<String> splitStatements(String sql) {
        StringBuilder cleaned = new StringBuilder();
        boolean slashMode = false;
        for (String line : sql.split("\n", -1)) {
            // "--" line comments are stripped uniformly. (We intentionally do
            // not strip them inside string literals — bootstrap scripts here
            // never put "--" inside a string and the simpler regex is easier
            // to reason about.)
            String stripped = line.replaceAll("--.*$", "");
            // Slash terminator detection runs on the trimmed *original* line
            // so an inline comment cannot accidentally strip a "/" off.
            if (line.trim().equals("/")) slashMode = true;
            cleaned.append(stripped).append('\n');
        }
        return slashMode ? splitOnSlash(cleaned.toString())
                         : splitOnSemicolon(cleaned.toString());
    }

    /**
     * Slash-mode splitter — statement boundary is a line that is just {@code /}.
     * The trailing {@code /} itself is dropped from the emitted statement;
     * inner {@code ;} (e.g. inside {@code BEGIN ... END;}) is preserved as
     * part of the body.
     */
    private static List<String> splitOnSlash(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : sql.split("\n", -1)) {
            if (line.trim().equals("/")) {
                String s = cur.toString().trim();
                // Drop the trailing ";" that often ends a PL/SQL body, since
                // some JDBC drivers reject it for "CREATE FUNCTION/PROCEDURE"
                // statements.
                if (s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();
                if (!s.isEmpty()) out.add(s);
                cur.setLength(0);
            } else {
                cur.append(line).append('\n');
            }
        }
        String tail = cur.toString().trim();
        if (!tail.isEmpty()) out.add(tail);
        return out;
    }

    private static List<String> splitOnSemicolon(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && (i == 0 || sql.charAt(i - 1) != '\\')) {
                inSingle = !inSingle;
            }
            if (c == ';' && !inSingle) {
                String s = cur.toString().trim();
                if (!s.isEmpty()) out.add(s);
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        String tail = cur.toString().trim();
        if (!tail.isEmpty()) out.add(tail);
        return out;
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        String first = (nl < 0 ? s : s.substring(0, nl)).trim();
        return first.length() > 80 ? first.substring(0, 80) + "..." : first;
    }

    /** Runtime wrapper to keep callers free of checked-exception clutter. */
    public static final class SqlRunnerException extends RuntimeException {
        public SqlRunnerException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
