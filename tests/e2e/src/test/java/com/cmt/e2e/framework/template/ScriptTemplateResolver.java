package com.cmt.e2e.framework.template;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.cmt.e2e.framework.db.containers.DatabaseContainer;
import com.cmt.e2e.framework.db.driver.Drivers;

/**
 * 마이그레이션 스크립트 XML 템플릿의 플레이스홀더를 실제 컨테이너 정보로 치환하고
 * 아티팩트 디렉터리에 최종 스크립트 파일을 생성합니다.
 *
 * <p>사용 예:
 * <pre>
 * ResolvedScript resolved = ScriptTemplateResolver.builder()
 *     .template(resourceDir.resolve("script.xml"))
 *     .source(sourceDb)
 *     .target(targetDb)           // 파일 타겟이면 생략 가능
 *     .outputDir(artifactDir)
 *     .scriptFileName("CUBRID_to_CUBRID.xml")
 *     .build()
 *     .resolve();
 *
 * // 스크립트 경로
 * StartCommand.builder().script(resolved.scriptPath()).build();
 *
 * // Dump 타겟 마이그레이션 출력 디렉토리
 * ctx.migrationOutput(resolved.migrationName(), "PUBLIC")
 * </pre>
 */
public class ScriptTemplateResolver {

    private final Path template;
    private final DatabaseContainer source;
    private final DatabaseContainer target;
    private final Path outputDir;
    private final String scriptFileName;

    private ScriptTemplateResolver(Builder builder) {
        this.template = Objects.requireNonNull(builder.template, "template");
        this.source = Objects.requireNonNull(builder.source, "source");
        this.target = builder.target;
        this.outputDir = Objects.requireNonNull(builder.outputDir, "outputDir");
        this.scriptFileName = builder.scriptFileName != null
            ? builder.scriptFileName
            : template.getFileName().toString();
    }

    /**
     * 템플릿을 읽어 플레이스홀더를 치환한 뒤, 출력 디렉터리에 파일을 쓰고
     * 스크립트 경로와 migration 이름을 담은 {@link ResolvedScript}를 반환합니다.
     */
    public ResolvedScript resolve() throws IOException {
        String migrationName = parseMigrationName();

        String content = Files.readString(template);
        content = replaceSource(content);
        if (target != null) {
            content = replaceTarget(content);
        }
        Files.createDirectories(outputDir);
        Path output = outputDir.resolve(scriptFileName);
        Files.writeString(output, content);

        return new ResolvedScript(output, migrationName);
    }

    /**
     * script.xml 템플릿을 파싱하여 migration 이름을 반환합니다.
     *
     * <p>실제 Console은 파일 타겟 마이그레이션 산출물을
     * {@code {file_repository.dir}/{migration.name}/{schema}/...} 아래에 생성합니다.
     * 따라서 테스트도 source connection 정보를 추정하지 말고
     * {@code <migration name="...">} 값을 그대로 사용해야 합니다.
     *
     * <p>migration 이름이 비어 있으면 Console과 동일하게 스크립트 파일명(확장자 제외)을 fallback으로 사용합니다.
     */
    private String parseMigrationName() throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(template.toFile());

            NodeList migrations = doc.getElementsByTagName("migration");
            if (migrations.getLength() == 0) {
                throw new IOException("Cannot derive migration name: missing <migration> tag in " + template);
            }

            Element migration = (Element) migrations.item(0);
            String migrationName = migration.getAttribute("name");
            if (migrationName != null && !migrationName.isBlank()) {
                return migrationName;
            }

            String fileName = template.getFileName().toString();
            int dotIndex = fileName.lastIndexOf('.');
            return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(
                "Failed to parse migration script template: " + template, e);
        }
    }

    private String replaceSource(String content) {
        return content
            .replace("%%SOURCE_HOST%%", source.getHost())
            .replace("%%SOURCE_PORT%%", source.getDatabasePort().toString())
            .replace("%%SOURCE_DRIVER%%",
                Drivers.latest(source.getDbType()).toAbsolutePath().toString());
    }

    private String replaceTarget(String content) {
        return content
            .replace("%%TARGET_HOST%%", target.getHost())
            .replace("%%TARGET_PORT%%", target.getDatabasePort().toString())
            .replace("%%TARGET_DRIVER%%",
                Drivers.latest(target.getDbType()).toAbsolutePath().toString());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Path template;
        private DatabaseContainer source;
        private DatabaseContainer target;
        private Path outputDir;
        private String scriptFileName;

        public Builder template(Path template) {
            this.template = template;
            return this;
        }

        public Builder source(DatabaseContainer source) {
            this.source = source;
            return this;
        }

        /** 타겟이 온라인 DB인 경우에만 설정. 파일 타겟(CSV/SQL/Dump)이면 생략. */
        public Builder target(DatabaseContainer target) {
            this.target = target;
            return this;
        }

        public Builder outputDir(Path outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        /** 생성될 스크립트 파일명. 미지정 시 템플릿 파일명을 사용. */
        public Builder scriptFileName(String scriptFileName) {
            this.scriptFileName = scriptFileName;
            return this;
        }

        public ScriptTemplateResolver build() {
            return new ScriptTemplateResolver(this);
        }
    }
}
