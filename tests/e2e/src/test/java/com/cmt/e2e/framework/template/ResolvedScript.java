package com.cmt.e2e.framework.template;

import java.nio.file.Path;

/**
 * Return value of {@link ScriptTemplateResolver#resolve()}.
 *
 * <p>Contains the fully resolved script file path and the output directory name
 * that CMT Console creates for dump migrations.
 *
 * <p>For file-target migrations, CMT Console creates directories like:
 * <pre>
 * {file_repository.dir}/{migration.name}/{schema}/...
 * Example: ./output/CUBRID_demodb_202604062341/PUBLIC/...
 * </pre>
 *
 * <p>This object therefore exposes the script-defined migration name
 * ({@code <migration name="...">}) unchanged, and
 * {@code CmtTestContext.migrationOutput()} uses it as the output directory name.
 */
public class ResolvedScript {

    private final Path scriptPath;
    private final String migrationName;

    ResolvedScript(Path scriptPath, String migrationName) {
        this.scriptPath = scriptPath;
        this.migrationName = migrationName;
    }

    /** Path to the fully resolved script file */
    public Path scriptPath() {
        return scriptPath;
    }

    /**
     * Output directory name created by CMT Console for file-target migrations.
     * <p>Format: {@code migration/@name}
     * <br>Example: {@code "CUBRID_demodb_202604062341"}
     *
     * <p>The full output path is
     * {@code {file_repository.dir}/{migrationName}/{schema}}, and
     * {@code CmtTestContext.migrationOutput(migrationName, schema)} computes it.
     */
    public String migrationName() {
        return migrationName;
    }
}
