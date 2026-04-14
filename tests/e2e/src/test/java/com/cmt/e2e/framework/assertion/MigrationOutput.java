package com.cmt.e2e.framework.assertion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import com.cmt.e2e.framework.assertion.strategies.ObjectFileVerificationStrategy;
import com.cmt.e2e.framework.assertion.strategies.PlainTextVerificationStrategy;

public class MigrationOutput {
    private final Path baseDir;
    private final Verifier verifier;

    public MigrationOutput(Path baseDir, Verifier verifier) {
        this.baseDir = baseDir;
        this.verifier = verifier;
    }

    public MigrationOutput assertFilesExist(List<String> expectedFiles) {
        assertThat(baseDir).as("Migration output directory").isDirectory();
        for (String f : expectedFiles) {
            assertThat(baseDir.resolve(f))
                .as("Migration file: %s", f)
                .exists()
                .isNotEmptyFile();
        }
        return this;
    }

    public MigrationOutput verifySchemaFile(String fileName, String answerFileName) throws IOException {
        Path actual = baseDir.resolve(fileName);
        verifier.verifyFileWith(actual, answerFileName, new PlainTextVerificationStrategy());
        return this;
    }

    public MigrationOutput verifyObjectFile(String fileName, String answerFileName) throws IOException {
        Path actual = baseDir.resolve(fileName);
        verifier.verifyFileWith(actual, answerFileName, new ObjectFileVerificationStrategy());
        return this;
    }
}
