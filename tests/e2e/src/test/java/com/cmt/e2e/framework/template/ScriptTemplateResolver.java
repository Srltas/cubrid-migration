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
import com.cmt.e2e.framework.db.JdbcDriverJars;

/**
 * Replaces placeholders in a migration-script XML template with actual
 * container information and writes the final script into the artifact directory.
 *
 * <p>Usage example:
 * <pre>
 * ResolvedScript resolved = ScriptTemplateResolver.builder()
 *     .template(resourceDir.resolve("script.xml"))
 *     .source(sourceDb)
 *     .target(targetDb)           // optional for file targets
 *     .outputDir(artifactDir)
 *     .scriptFileName("CUBRID_to_CUBRID.xml")
 *     .build()
 *     .resolve();
 *
 * // Script path
 * StartCommand.builder().script(resolved.scriptPath()).build();
 *
 * // Output directory for dump target migration
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
     * Reads the template, replaces placeholders, writes the output file,
     * and returns a {@link ResolvedScript} containing the script path
     * and migration name.
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
     * Parses the {@code script.xml} template and returns the migration name.
     *
     * <p>The real Console writes file-target migration outputs under
     * {@code {file_repository.dir}/{migration.name}/{schema}/...}.
     * Tests should therefore use the exact {@code <migration name="...">}
     * value instead of inferring it from source connection data.
     *
     * <p>If the migration name is blank, this falls back to the script file name
     * without its extension, matching Console behavior.
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
                JdbcDriverJars.latest(source.getDbType()).toAbsolutePath().toString());
    }

    private String replaceTarget(String content) {
        return content
            .replace("%%TARGET_HOST%%", target.getHost())
            .replace("%%TARGET_PORT%%", target.getDatabasePort().toString())
            .replace("%%TARGET_DRIVER%%",
                JdbcDriverJars.latest(target.getDbType()).toAbsolutePath().toString());
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

        /** Set only when the target is an online DB. Omit for file targets such as CSV, SQL, or dump. */
        public Builder target(DatabaseContainer target) {
            this.target = target;
            return this;
        }

        public Builder outputDir(Path outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        /** Output script file name. Defaults to the template file name when omitted. */
        public Builder scriptFileName(String scriptFileName) {
            this.scriptFileName = scriptFileName;
            return this;
        }

        public ScriptTemplateResolver build() {
            return new ScriptTemplateResolver(this);
        }
    }
}
