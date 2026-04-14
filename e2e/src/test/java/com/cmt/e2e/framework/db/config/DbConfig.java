package com.cmt.e2e.framework.db.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.cmt.e2e.framework.db.driver.Drivers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * db.conf와 같은 속성 기반 설정 파일을 관리하는 범용적인 추상 클래스
 */
public abstract class DbConfig {
    private static final Logger log = LoggerFactory.getLogger(DbConfig.class);

    protected final Path confPath;
    protected final String sourceName;
    protected final String targetName;

    protected DbConfig(Path confPath, String sourceName, String targetName) {
        this.confPath = confPath;
        this.sourceName = sourceName;
        this.targetName = targetName;
    }

    public Path getFinalConfPath() {
        return confPath;
    }

    public String getSourceHost() throws IOException {
        return readProp(this.sourceName + ".host");
    }

    public int getSourcePort() throws IOException {
        return Integer.parseInt(readProp(this.sourceName + ".port"));
    }

    public String getSourceDbName() throws IOException {
        return readProp(this.sourceName + ".dbname");
    }

    public String getSourceUser() throws IOException {
        return readProp(this.sourceName + ".user");
    }

    public String getSourcePassword() throws IOException {
        return readProp(this.sourceName + ".password");
    }

    public String getSourceCharset() throws IOException {
        return readProp(this.sourceName + ".charset");
    }

    public Path getSourceDriverJarPath() {
        return Drivers.latest(Drivers.DB.CUBRID);
    }

    protected void upsertProperty(String key, String value) throws IOException {
        log.debug("Patching {} with: {}={}", confPath, key, value);
        Properties props = loadProperties();
        props.setProperty(key, value);
        storeProperties(props);
    }

    protected String readProp(String key) throws IOException {
        Properties props = loadProperties();
        String value = props.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Required property not found: " + key + " in " + confPath);
        }
        return value;
    }

    private Properties loadProperties() throws IOException {
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(confPath)) {
            props.load(reader);
        }
        return props;
    }

    private void storeProperties(Properties props) throws IOException {
        try (Writer writer = Files.newBufferedWriter(confPath)) {
            props.store(writer, null);
        }
    }
}
