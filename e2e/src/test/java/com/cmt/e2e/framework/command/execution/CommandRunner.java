package com.cmt.e2e.framework.command.execution;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.cmt.e2e.framework.command.Command;
import com.cmt.e2e.framework.command.InteractiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandRunner {
    private static final Logger log = LoggerFactory.getLogger(CommandRunner.class);

    private static final int DEFAULT_PROCESS_TIMEOUT_SECONDS = 300;
    private static final int STREAM_READ_TIMEOUT_SECONDS = 5;
    private final File workDir;

    /**
     * 생성자에서 작업 디렉터리를 미리 받아 저장
     * @param workDir 명령어를 실행할 작업 디렉터리
     */
    public CommandRunner(File workDir) {
        this.workDir = workDir;
    }

    public CommandResult run(Command command) throws IOException, InterruptedException {
        return run(command, DEFAULT_PROCESS_TIMEOUT_SECONDS);
    }

    public CommandResult run(Command command, long timeoutSeconds) throws IOException, InterruptedException {
        List<String> commandList = command.build();

        log.debug("Executing command list: {}", commandList);
        log.debug("CWD: {}", workDir.getAbsolutePath());
        log.debug("CMD: {}", String.join(" ", commandList));

        ProcessBuilder processBuilder = new ProcessBuilder(commandList);
        processBuilder.directory(workDir);
        Process process = processBuilder.start();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        SharedOutputBuffer buffer = new SharedOutputBuffer();

        try {
            Future<?> stdoutTask = executor.submit(() ->
                readStream(process.getInputStream(), buffer::appendStdoutLine, null));
            Future<?> stderrTask = executor.submit(() ->
                readStream(process.getErrorStream(), buffer::appendStderrLine, null));

            boolean finishedInTime = process.waitFor(timeoutSeconds, SECONDS);
            if (!finishedInTime) {
                process.destroyForcibly();
                process.waitFor();
            }

            waitForStreamReadersOrThrow(stdoutTask, stderrTask);

            CommandResult result = new CommandResult(
                buffer.stdout(),
                buffer.stderr(),
                buffer.combined(),
                finishedInTime ? process.exitValue() : -1,
                !finishedInTime
            );
            log.debug("Command finished with exitCode: {}, timeOut: {}", result.exitCode(), result.timedOut());
            log.debug("Command stdout: {}", result.stdout());
            log.debug("Command stderr: {}", result.stderr());
            return result;
        } finally {
            executor.shutdownNow();
        }
    }

    public CommandResult runInteractive(InteractiveCommand command) throws Exception {
        return runInteractive(command, command.getResponders(), DEFAULT_PROCESS_TIMEOUT_SECONDS);
    }

    public CommandResult runInteractive(Command command, Map<String, String> responders, long timeoutSeconds) throws Exception {
        List<String> commandList = command.build();
        ProcessBuilder processBuilder = new ProcessBuilder(commandList);
        processBuilder.directory(workDir);
        Process process = processBuilder.start();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        SharedOutputBuffer buffer = new SharedOutputBuffer();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), UTF_8))) {
            Future<?> stdoutTask = executor.submit(() ->
                readStream(process.getInputStream(), buffer::appendStdoutLine, line -> respondIfMatched(line, responders, writer)));
            Future<?> stderrTask = executor.submit(() ->
                readStream(process.getErrorStream(), buffer::appendStderrLine, line -> respondIfMatched(line, responders, writer)));

            boolean finishedInTime = process.waitFor(timeoutSeconds, SECONDS);
            if (!finishedInTime) {
                process.destroyForcibly();
                process.waitFor();
            }

            waitForStreamReaders(stdoutTask, stderrTask);

            return new CommandResult(
                buffer.stdout(),
                buffer.stderr(),
                buffer.combined(),
                finishedInTime ? process.exitValue() : -1,
                !finishedInTime
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private void readStream(InputStream stream, LineAppender appender, LineObserver observer) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appender.append(line);
                if (observer != null) {
                    observer.onLine(line);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read process stream", e);
        }
    }

    private void respondIfMatched(String line, Map<String, String> responders, BufferedWriter writer) {
        log.debug("Interactive output: {}", line);
        for (Map.Entry<String, String> entry : responders.entrySet()) {
            if (Pattern.compile(entry.getKey()).matcher(line).find()) {
                try {
                    synchronized (writer) {
                        writer.write(entry.getValue());
                        writer.flush();
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to write interactive response", e);
                }
                break;
            }
        }
    }

    private void waitForStreamReadersOrThrow(Future<?> stdoutTask, Future<?> stderrTask) throws IOException, InterruptedException {
        try {
            waitForStreamReaders(stdoutTask, stderrTask);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("Failed to capture process output", e);
        }
    }

    private void waitForStreamReaders(Future<?> stdoutTask, Future<?> stderrTask)
        throws InterruptedException, ExecutionException, TimeoutException {
        stdoutTask.get(STREAM_READ_TIMEOUT_SECONDS, SECONDS);
        stderrTask.get(STREAM_READ_TIMEOUT_SECONDS, SECONDS);
    }

    @FunctionalInterface
    private interface LineAppender {
        void append(String line);
    }

    @FunctionalInterface
    private interface LineObserver {
        void onLine(String line);
    }

    private static final class SharedOutputBuffer {
        private final StringBuilder stdout = new StringBuilder();
        private final StringBuilder stderr = new StringBuilder();
        private final StringBuilder combined = new StringBuilder();

        void appendStdoutLine(String line) {
            append(stdout, line);
            appendCombined(line);
        }

        void appendStderrLine(String line) {
            append(stderr, line);
            appendCombined(line);
        }

        synchronized String stdout() {
            return stdout.toString();
        }

        synchronized String stderr() {
            return stderr.toString();
        }

        synchronized String combined() {
            return combined.toString();
        }

        private synchronized void appendCombined(String line) {
            append(combined, line);
        }

        private void append(StringBuilder builder, String line) {
            synchronized (this) {
                builder.append(line).append("\n");
            }
        }
    }
}
