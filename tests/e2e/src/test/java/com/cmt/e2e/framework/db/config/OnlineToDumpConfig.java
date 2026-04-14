package com.cmt.e2e.framework.db.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import com.cmt.e2e.framework.db.driver.Drivers;
import com.cmt.e2e.framework.db.driver.Drivers.DB;
import com.cmt.e2e.framework.db.containers.DatabaseContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OnlineToDumpConfig extends DbConfig {
    private static final Logger log = LoggerFactory.getLogger(OnlineToDumpConfig.class);

    private OnlineToDumpConfig(Path confPath, String sourceName, String targetName) {
        super(confPath, sourceName, targetName);
    }

    /**
     * 템플릿 파일로부터 새로운 DbconfigSource 객체를 생성합니다.
     * 템플릿은 임시 파일로 복사되어 관리됩니다.
     */
    public static OnlineToDumpConfig fromTemplate(Path templatePath, String sourceName, String targetName) throws IOException {
        Path tempFile = Files.createTempFile("db", ".conf");
        Files.copy(templatePath, tempFile, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Created temporary config file {} from template {}", tempFile, templatePath);
        return new OnlineToDumpConfig(tempFile, sourceName, targetName);
    }

    public OnlineToDumpConfig patchWithContainer(DatabaseContainer container, DB sourceJDBCType, Path artifactDir) throws IOException {
        upsertProperty(this.sourceName + ".host", container.getHost());
        upsertProperty(this.sourceName + ".port", String.valueOf(container.getDatabasePort()));
        upsertProperty(this.sourceName + ".driver", Drivers.latest(sourceJDBCType).toAbsolutePath().toString());

        Files.createDirectories(artifactDir);
        upsertProperty(this.targetName + ".output", artifactDir.resolve("output").toString());

        return this;
    }

    public String getTargetOutput() throws IOException {
        return readProp(this.targetName + ".output");
    }
}
