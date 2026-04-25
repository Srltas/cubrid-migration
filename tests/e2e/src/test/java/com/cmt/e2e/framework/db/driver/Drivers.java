package com.cmt.e2e.framework.db.driver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Drivers {
    private Drivers() {}

    /** Override with system property: -De2e.driver.dir=/abs/path/to/driver */
    private static final String PROP_DIR = "e2e.driver.dir";

    /**
     * Default driver directory: {@code target/test-classes/driver}.
     * The maven-dependency-plugin copies JDBC jars there during the build.
     * Override with {@code -De2e.driver.dir=/absolute/path}.
     */
    private static Path defaultDir() {
        Path base = Paths.get("").toAbsolutePath();
        Path d = base.resolve("target").resolve("test-classes").resolve("driver");
        String override = System.getProperty(PROP_DIR);
        Path candidate = override != null ? Paths.get(override).toAbsolutePath() : d;
        if (!Files.isDirectory(candidate)) {
            throw new IllegalStateException("Driver directory not found: " + candidate +
                "\nHint: run 'mvn generate-test-resources' first, " +
                "or override with -D" + PROP_DIR + "=/absolute/path");
        }
        return candidate;
    }

    public enum DB {
        CUBRID,
        ORACLE
    }

    private static final Map<DB, List<String>> PATTERNS = Map.of(
        DB.CUBRID, List.of("JDBC-*-cubrid.jar", "cubrid-jdbc-*.jar"),
        DB.ORACLE,  List.of("ojdbc8-*.jar", "ojdbc8.jar", "ojdbc*.jar")
    );

    private static final Map<String, Path> CACHE = new ConcurrentHashMap<>();

    private static String cacheKey(DB db, String versionOrNull) {
        return db.name() + "::" + (versionOrNull == null ? "<latest>" : versionOrNull);
    }

    public static Path latest(DB db) {
        return resolve(db, null);
    }

    public static Path version(DB db, String versionSubString) {
        Objects.requireNonNull(versionSubString, "versionSubString");
        return resolve(db, versionSubString);
    }

    public static Path copyToConsoleJdbc(Path consoleHome, DB db, String versionSubstringOrNull) throws IOException {
        Path jar = versionSubstringOrNull == null ? latest(db) : version(db, versionSubstringOrNull);
        Path targetDir = consoleHome.resolve("jdbc");
        Files.createDirectories(targetDir);
        Path target = targetDir.resolve(jar.getFileName());
        if (!Files.exists(target)) {
            Files.copy(jar, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target.toAbsolutePath().normalize();
    }

    private static Path resolve(DB db, String versionSubStringOrNull) {
        return CACHE.computeIfAbsent(cacheKey(db, versionSubStringOrNull), k -> {
            try {
                Path dir = defaultDir();
                List<Path> candidates = findCandidates(dir, db);

                if (candidates.isEmpty()) {
                    throw new IllegalStateException("No JDBC jar found for " + db +
                        " under " + dir + "\nLooked for patterns: " + PATTERNS.get(db));
                }

                Stream<Path> stream = candidates.stream();
                if (versionSubStringOrNull != null) {
                    stream = stream.filter(p -> p.getFileName().toString().contains(versionSubStringOrNull));
                }
                List<Path> filtered = stream.collect(Collectors.toList());
                if (filtered.isEmpty()) {
                    throw new IllegalStateException("No JDBC jar for " + db +
                        " matching version substring '" + versionSubStringOrNull +"' under " + dir);
                }

                filtered.sort(Comparator.comparing(Drivers::extractVersionTokens, Drivers::compareVersionLists).reversed());
                return filtered.get(0).toAbsolutePath().normalize();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static List<Path> findCandidates(Path dir, DB db) throws IOException {
        List<String> globs = PATTERNS.getOrDefault(db, List.of("*.jar"));
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> all = s.filter(Files::isRegularFile).collect(Collectors.toList());
            return all.stream()
                .filter(p -> matchAny(globs, p.getFileName().toString()))
                .collect(Collectors.toList());
        }
    }

    private static boolean matchAny(List<String> globs, String filename) {
        for (String g : globs) {
            String regex = globToRegex(g);
            if (filename.matches(regex)) return true;
        }
        return false;
    }

    private static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*': sb.append(".*"); break;
                case '?': sb.append('.'); break;
                case '.': sb.append("\\."); break;
                default:  sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        sb.append("$");
        return sb.toString();
    }

    private static List<Integer> extractVersionTokens(Path p) {
        String name = p.getFileName().toString();
        Matcher m = Pattern.compile("(\\d+(?:[._]\\d+)+)").matcher(name);
        if (m.find()) {
            String[] parts = m.group(1).replace('_', '.').split("\\.");
            List<Integer> nums = new ArrayList<>(parts.length);
            for (String part : parts) {
                try {
                    nums.add(Integer.parseInt(part));
                } catch (NumberFormatException e) {
                    nums.add(0);
                }
            }
            return nums;
        }
        return List.of();
    }

    private static int compareVersionLists(List<Integer> a, List<Integer> b) {
        int n = Math.max(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            int ai = i < a.size() ? a.get(i) : 0;
            int bi = i < b.size() ? b.get(i) : 0;
            int cmp = Integer.compare(ai, bi);
            if (cmp != 0) return cmp;
        }
        return 0;
    }
}
