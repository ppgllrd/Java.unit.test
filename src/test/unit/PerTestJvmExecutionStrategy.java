package test.unit;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Runs each test in its own child JVM and enforces the timeout from the parent process.
 * <p>
 * <b>Timeout semantics.</b> The clock ({@code System.nanoTime()}) starts right before the child process is
 * started and stops when the child has produced its complete result <em>and</em> exited. It therefore covers JVM
 * startup, class loading, suite construction by the {@link TestSuiteProvider}, the test itself and result
 * writing. If the deadline passes, the child is terminated ({@code destroy()} first only when a grace period is
 * configured, then {@code destroyForcibly()}), the parent waits a bounded time for it to die, and the result is
 * {@link TestResult.TimeoutFailure}. Inside the child the library's own in-JVM timeout is set well above the
 * deadline so that the parent is always the one enforcing it.
 * <p>
 * <b>Descendant processes.</b> Known descendants of the child are snapshotted via {@link ProcessHandle} and killed
 * on a best-effort basis. This is <em>not</em> a guarantee (races, processes that detach, OS differences):
 * for strong guarantees use containers, cgroups, Windows Job Objects or similar external isolation.
 * <p>
 * Stateless and thread-safe: every execution owns its process, temporary directory, nonce and collectors.
 *
 * @author Pepe Gallardo & Gemini
 */
public final class PerTestJvmExecutionStrategy implements TestExecutionStrategy {
    public static final PerTestJvmExecutionStrategy INSTANCE = new PerTestJvmExecutionStrategy();

    /** Maximum time to wait for a forcibly destroyed process to die. */
    static final long KILL_WAIT_MILLIS = 5_000;
    /** Maximum time to wait for the output readers once the process is dead. */
    static final long DRAIN_WAIT_MILLIS = 2_000;
    private static final int TAIL_CHARS = 2_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PerTestJvmExecutionStrategy() {}

    @Override
    public ExecutionOutcome execute(TestExecutionRequest request) {
        long start = System.nanoTime();
        Config config = request.config();
        IsolationOptions options = config.isolation();
        TestLocator locator = request.locator();

        if (locator == null || options.suiteProvider() == null) {
            return ExecutionOutcome.of(new TestResult.InfrastructureFailure(
                    TestResult.InfrastructureFailure.Kind.CONFIGURATION_ERROR,
                    "PER_TEST_JVM needs IsolationOptions.suiteProvider (a TestSuiteProvider class) and the test must run "
                            + "through TestSuite.run/runAll so that the child JVM can locate it"), System.nanoTime() - start);
        }

        Path dir = null;
        Process process = null;
        OutputCollector out = null;
        OutputCollector err = null;
        try {
            dir = options.workDirectory() == null
                    ? Files.createTempDirectory("test-unit-")
                    : Files.createTempDirectory(options.workDirectory(), "test-unit-");
            Path resultFile = dir.resolve("result.txt");

            List<String> command;
            try {
                ChildJvmCommandFactory factory = options.commandFactory() != null
                        ? options.commandFactory() : ChildJvmCommandFactory.standard();
                command = List.copyOf(factory.command(new ChildJvmCommandFactory.Request(resultFile, options.jvmArgs())));
            } catch (RuntimeException e) {
                return ExecutionOutcome.of(new TestResult.InfrastructureFailure(
                        TestResult.InfrastructureFailure.Kind.CONFIGURATION_ERROR,
                        "cannot build the child JVM command: " + e), System.nanoTime() - start);
            }

            String nonce = randomNonce();
            long timeoutNanos = TimeUnit.SECONDS.toNanos(config.timeout());
            var workerRequest = new IsolationProtocol.WorkerRequest(nonce, locator, config.language(),
                    config.logger().supportsAnsiColors(), childInternalTimeout(config.timeout()));

            long startedAt = System.nanoTime();
            try {
                process = new ProcessBuilder(command).start(); // each argument separate: no shell, no quoting
            } catch (IOException | RuntimeException e) {
                return ExecutionOutcome.of(new TestResult.InfrastructureFailure(
                        TestResult.InfrastructureFailure.Kind.PROCESS_ERROR,
                        "failed to start the child JVM: " + e.getMessage() + "\n  command: " + render(command)
                                + "\n  Check that java.home/bin/java exists and that the class path/JVM arguments are valid."),
                        System.nanoTime() - start);
            }

            out = new OutputCollector(process.getInputStream(), options.maxOutputChars(), "test-unit-stdout");
            err = new OutputCollector(process.getErrorStream(), options.maxOutputChars(), "test-unit-stderr");
            out.start();
            err.start();

            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(workerRequest.encode().getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // The child may have died already; the missing result is diagnosed below.
            }

            boolean exited;
            boolean interrupted = false;
            try {
                exited = process.waitFor(Math.max(0, timeoutNanos - (System.nanoTime() - startedAt)), TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exited = false;
                interrupted = true;
            }

            if (!exited) {
                boolean dead = terminate(process, options.gracePeriodMillis());
                long duration = System.nanoTime() - startedAt;
                out.awaitEnd(DRAIN_WAIT_MILLIS);
                err.awaitEnd(DRAIN_WAIT_MILLIS);
                TestResult result;
                if (interrupted) {
                    result = new TestResult.InfrastructureFailure(TestResult.InfrastructureFailure.Kind.PROCESS_ERROR,
                            "the runner thread was interrupted; the child JVM was terminated");
                } else if (!dead) {
                    result = new TestResult.InfrastructureFailure(TestResult.InfrastructureFailure.Kind.PROCESS_ERROR,
                            "test timed out after " + config.timeout() + "s and the child JVM (pid " + process.pid()
                                    + ") could NOT be verified dead after destroyForcibly(); kill it manually");
                } else {
                    result = new TestResult.TimeoutFailure(config.timeout(), locator.testName());
                }
                return new ExecutionOutcome(result, duration, out.text(), err.text(), out.truncated(), err.truncated(),
                        OptionalLong.of(process.pid()), dead);
            }

            long duration = System.nanoTime() - startedAt;
            out.awaitEnd(DRAIN_WAIT_MILLIS);
            err.awaitEnd(DRAIN_WAIT_MILLIS);
            int exitCode = process.exitValue();
            TestResult result = readResult(resultFile, nonce, locator, exitCode, err.text());
            return new ExecutionOutcome(result, duration, out.text(), err.text(), out.truncated(), err.truncated(),
                    OptionalLong.of(process.pid()), true);
        } catch (IOException e) {
            return ExecutionOutcome.of(new TestResult.InfrastructureFailure(
                    TestResult.InfrastructureFailure.Kind.PROCESS_ERROR,
                    "cannot create the temporary directory for the child JVM result: " + e), System.nanoTime() - start);
        } finally {
            if (process != null) {
                if (process.isAlive()) terminate(process, 0); // e.g. unexpected exception above
                closeQuietly(process);
            }
            if (dir != null) deleteRecursively(dir);
        }
    }

    private static TestResult readResult(Path resultFile, String nonce, TestLocator locator, int exitCode, String stderr) {
        IsolationProtocol.ChildResult r;
        try {
            r = IsolationProtocol.readResult(resultFile, nonce, locator.id());
        } catch (IOException e) {
            return processError("child JVM exited (exit code " + exitCode + ") without a valid result: " + e.getMessage()
                    + ". The test may have called System.exit/Runtime.halt or the JVM crashed."
                    + tail(stderr));
        }
        if (r.status().exitCode != exitCode) {
            return processError("child JVM exit code " + exitCode + " is inconsistent with the reported status "
                    + r.status() + " (expected " + r.status().exitCode + "); the result is not trusted." + tail(stderr));
        }
        return switch (r.status()) {
            case PASSED -> new TestResult.Success();
            case FAILED -> new TestResult.RemoteFailure(TestResult.RemoteFailure.Kind.FAILED, r.message(),
                    r.exceptionType(), r.exceptionMessage(), r.stackTrace());
            case ERROR -> new TestResult.RemoteFailure(TestResult.RemoteFailure.Kind.ERROR, r.message(),
                    r.exceptionType(), r.exceptionMessage(), r.stackTrace());
            case TIMED_OUT -> r.timeoutSeconds() > 0
                    ? new TestResult.TimeoutFailure(r.timeoutSeconds(), r.description())
                    : processError("child reported a timeout without a valid duration");
            case CONFIGURATION_ERROR -> new TestResult.InfrastructureFailure(
                    TestResult.InfrastructureFailure.Kind.CONFIGURATION_ERROR, r.detail());
            case INTERNAL_ERROR -> processError("child worker failed: " + r.detail());
        };
    }

    private static TestResult processError(String detail) {
        return new TestResult.InfrastructureFailure(TestResult.InfrastructureFailure.Kind.PROCESS_ERROR, detail);
    }

    private static String tail(String stderr) {
        if (stderr.isBlank()) return "";
        String t = stderr.length() > TAIL_CHARS ? "..." + stderr.substring(stderr.length() - TAIL_CHARS) : stderr;
        return "\nLast stderr output:\n" + t.stripTrailing();
    }

    /**
     * Terminates the child: optional graceful {@code destroy()} within the grace period, then
     * {@code destroyForcibly()}, then a bounded wait. Best-effort kill of descendants known at call time.
     *
     * @return {@code true} if the child is verified not alive.
     */
    static boolean terminate(Process process, long gracePeriodMillis) {
        List<ProcessHandle> descendants;
        try {
            descendants = process.descendants().toList();
        } catch (RuntimeException e) { // e.g. UnsupportedOperationException / SecurityException
            descendants = List.of();
        }
        boolean interrupted = false;
        try {
            if (gracePeriodMillis > 0) {
                process.destroy();
                try {
                    process.waitFor(gracePeriodMillis, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (process.isAlive()) {
                process.destroyForcibly(); // only starts the termination
                try {
                    process.waitFor(KILL_WAIT_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            for (ProcessHandle d : descendants) {
                try {
                    if (d.isAlive()) d.destroyForcibly();
                } catch (RuntimeException ignored) {
                    // best effort only
                }
            }
            return !process.isAlive();
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(Process process) {
        try { process.getInputStream().close(); } catch (IOException ignored) { }
        try { process.getErrorStream().close(); } catch (IOException ignored) { }
        try { process.getOutputStream().close(); } catch (IOException ignored) { }
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    System.err.println("test.unit: could not delete temporary file " + p + ": " + e);
                }
            });
        } catch (IOException e) {
            System.err.println("test.unit: could not delete temporary directory " + dir + ": " + e);
        }
    }

    private static int childInternalTimeout(int timeoutSeconds) {
        long t = 2L * timeoutSeconds + 30;
        return t > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) t;
    }

    private static String randomNonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String render(List<String> command) {
        StringBuilder sb = new StringBuilder();
        for (String arg : command) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(arg.isEmpty() || arg.chars().anyMatch(Character::isWhitespace) ? "'" + arg + "'" : arg);
        }
        return sb.toString();
    }
}
