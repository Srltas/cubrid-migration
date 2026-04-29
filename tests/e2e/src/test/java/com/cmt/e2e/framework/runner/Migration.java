package com.cmt.e2e.framework.runner;

import java.nio.file.Path;

import com.cmt.e2e.framework.command.CommandResult;
import com.cmt.e2e.framework.command.CommandRunner;
import com.cmt.e2e.framework.command.StartCommand;
import com.cmt.e2e.framework.env.CmtConsoleEnv;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.target.Target;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single-shot CMT runner. Composes the three pieces of a migration:
 * <ol>
 *   <li>Build {@code db.conf} from Source/Target.</li>
 *   <li>Generate {@code script.xml} via {@code migration.sh script}
 *       (with timestamp normalisation for snapshot determinism).</li>
 *   <li>Run {@code migration.sh start <script.xml>}.</li>
 * </ol>
 * The result is wrapped in {@link MigrationOutcome} for fluent
 * verification.
 *
 * <p>Source/Target are expected to be already started; {@code Migration}
 * does not own their lifecycle. {@link com.cmt.e2e.framework.junit.AbstractMigrationE2E}
 * handles container start/stop and calls {@link #run(Path)} once per
 * test class in {@code @BeforeAll}.
 */
public final class Migration {

    private static final Logger log = LoggerFactory.getLogger(Migration.class);

    private final Source source;
    private final Target target;
    private final String scenarioName;

    public Migration(Source source, Target target, String scenarioName) {
        if (source == null) throw new IllegalArgumentException("source");
        if (target == null) throw new IllegalArgumentException("target");
        if (scenarioName == null || scenarioName.isBlank()) {
            throw new IllegalArgumentException("scenarioName must not be blank");
        }
        this.source = source;
        this.target = target;
        this.scenarioName = scenarioName;
    }

    /**
     * Runs the migration end-to-end.
     *
     * @param workDir framework-owned scratch dir for this run; the generated
     *                {@code script.xml} and raw CMT output land under it
     */
    public MigrationOutcome run(Path workDir) throws Exception {
        Path consoleHome = CmtConsoleEnv.resolve();

        String dbConf = DbConfBuilder.build(source, target);
        log.debug("[Migration] db.conf built ({} chars)", dbConf.length());

        Path scriptXml = ScriptXmlBuilder.generate(consoleHome, dbConf, workDir);
        log.info("[Migration] script.xml generated: {}", scriptXml);

        StartCommand cmd = StartCommand.builder().script(scriptXml).build();
        CommandRunner runner = new CommandRunner(consoleHome.toFile());
        CommandResult result = runner.run(cmd);
        log.info("[Migration] start exited with {}", result.exitCode());

        return new MigrationOutcome(result, source, target, scriptXml, scenarioName);
    }
}
