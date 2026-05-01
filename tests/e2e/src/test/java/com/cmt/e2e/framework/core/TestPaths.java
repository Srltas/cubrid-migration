package com.cmt.e2e.framework.core;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Per-test resource (input) and artifact (output) directories. Resource
 * dir is opt-in via {@link TestResources}; artifact dir is always
 * created at {@code target/e2e/<Class>/<method>/}.
 */
public class TestPaths {

    private final String testClassName;
    private final String testMethodName;
    private final Path resourceDir;     // null when @TestResources is absent
    private final Path artifactDir;

    public TestPaths(Class<?> testClass, Method testMethod) throws IOException {
        this.testClassName = testClass.getSimpleName();
        this.testMethodName = testMethod.getName();

        Optional<TestResources> annotation = findTestResourcesAnnotation(testClass, testMethod);
        this.resourceDir = annotation
            .map(a -> getPathFromResources("tests/" + a.value()))
            .orElse(null);

        this.artifactDir = Paths.get("target", "e2e", testClassName, testMethodName);
        Files.createDirectories(this.artifactDir);
    }

    /** @throws IllegalStateException if {@link TestResources} is not declared */
    public Path getResourceDir() {
        if (resourceDir == null) {
            throw new IllegalStateException(
                "Test " + testClassName + "#" + testMethodName
                + " uses test resources but has no @TestResources annotation. "
                + "Add @TestResources(\"...\") to the class or method.");
        }
        return resourceDir;
    }

    public Path getArtifactDir() {
        return artifactDir;
    }

    public Path getTestLogPath() {
        return artifactDir.resolve("test.log");
    }

    private Optional<TestResources> findTestResourcesAnnotation(Class<?> clazz, Method method) {
        TestResources methodAnnotation = method.getAnnotation(TestResources.class);
        if (methodAnnotation != null) {
            return Optional.of(methodAnnotation);
        }
        return Optional.ofNullable(clazz.getAnnotation(TestResources.class));
    }

    private Path getPathFromResources(String path) {
        try {
            URL url = getClass().getClassLoader().getResource(path);
            if (url == null) return Paths.get("src", "test", "resources", path);
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to get resource path", e);
        }
    }
}
