package test.unit.execution;

import test.unit.config.Config;
import test.unit.config.IsolationOptions;
import test.unit.core.TestResult;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Pool of reusable child JVMs ({@link PooledTestWorker}) for {@link test.unit.config.TestIsolationMode#POOLED_JVM}.
 * At most {@code maxWorkers} workers exist at once; they are started lazily and handed out exclusively, one test at a time.
 * <p>
 * A worker that times out, crashes, exits, misbehaves, or has run {@link IsolationOptions#maxTestsPerWorker()} tests is
 * discarded and the next test gets a fresh one. The timeout of a test is measured from sending the request to receiving
 * the answer, so it does <em>not</em> include JVM start-up, which has its own limit ({@value #STARTUP_TIMEOUT_MILLIS} ms) and is
 * charged to the test that triggers it only as a failure. Created and closed by the code that runs the suites
 * ({@code TestSuite.run/runAll}); thread-safe.
 *
 * @author Pepe Gallardo & Gemini
 */
public final class WorkerPool implements AutoCloseable {
    static final long STARTUP_TIMEOUT_MILLIS = 60_000;

    private final IsolationOptions options;
    private final Semaphore permits;
    private final Deque<PooledWorker> idle = new ArrayDeque<>();
    private final Set<PooledWorker> all = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    /**
     * @param options Isolation options; {@link IsolationOptions#suiteProvider()} is required.
     * @param maxWorkers Maximum number of simultaneously running workers (at least 1).
     */
    public WorkerPool(IsolationOptions options, int maxWorkers) {
        if (options.suiteProvider() == null) {
            throw new IllegalArgumentException("a worker pool needs IsolationOptions.suiteProvider");
        }
        if (maxWorkers < 1) {
            throw new IllegalArgumentException("maxWorkers must be at least 1");
        }
        this.options = options;
        this.permits = new Semaphore(maxWorkers, true);
    }

    /** Runs one test on a pooled worker. Never throws for test or infrastructure failures. */
    public ExecutionOutcome execute(TestExecutionRequest request) {
        long start = System.nanoTime();
        if (closed) {
            return failure(TestResult.InfrastructureFailure.Kind.PROCESS_ERROR, "the worker pool is closed", start);
        }
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failure(TestResult.InfrastructureFailure.Kind.PROCESS_ERROR,
                    "the runner thread was interrupted while waiting for a worker", start);
        }
        PooledWorker worker = null;
        try {
            worker = takeIdle();
            if (worker == null) {
                try {
                    worker = startWorker();
                } catch (StartFailure f) {
                    return ExecutionOutcome.of(f.result, System.nanoTime() - start);
                }
            }
            return run(worker, request);
        } finally {
            if (worker != null) release(worker);
            permits.release();
        }
    }

    /** Terminates every worker. Safe to call more than once. */
    @Override
    public void close() {
        closed = true;
        for (PooledWorker w : List.copyOf(all)) {
            all.remove(w);
            w.shutdown(options.gracePeriodMillis());
        }
        synchronized (idle) {
            idle.clear();
        }
    }

    // --- running a test ---

    private ExecutionOutcome run(PooledWorker worker, TestExecutionRequest request) {
        Config config = request.config();
        TestLocator locator = request.locator();
        long seq = worker.nextSeq();
        var workerRequest = new IsolationProtocol.WorkerRequest(worker.nonce(), locator, config.language(),
                config.logger().supportsAnsiColors(), PerTestJvmExecutionStrategy.childInternalTimeout(config.timeout()));
        long timeoutNanos = TimeUnit.SECONDS.toNanos(config.timeout());

        long startedAt = System.nanoTime();
        try {
            worker.send(IsolationProtocol.toLine(IsolationProtocol.testFields(workerRequest, seq)));
        } catch (IOException e) {
            return lost(worker, startedAt, "cannot send the request to the worker JVM: " + e.getMessage());
        }

        long deadline = startedAt + timeoutNanos;
        PooledWorker.Message message;
        try {
            message = worker.poll(deadline - System.nanoTime());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            worker.kill(options.gracePeriodMillis());
            return outcome(worker, new TestResult.InfrastructureFailure(TestResult.InfrastructureFailure.Kind.PROCESS_ERROR,
                    "the runner thread was interrupted; the worker JVM was terminated"), startedAt, null, true);
        }

        switch (message.kind()) {
            case TIMEOUT -> {
                boolean dead = worker.kill(options.gracePeriodMillis());
                TestResult result = dead
                        ? new TestResult.TimeoutFailure(config.timeout(), locator.testName())
                        : PerTestJvmExecutionStrategy.processError("test timed out after " + config.timeout()
                                + "s and the worker JVM (pid " + worker.pid()
                                + ") could NOT be verified dead after destroyForcibly(); kill it manually");
                return outcome(worker, result, startedAt, null, dead);
            }
            case END_OF_STREAM -> {
                worker.markBroken();
                int exit = worker.exitCode(2_000);
                worker.awaitReaders(PerTestJvmExecutionStrategy.DRAIN_WAIT_MILLIS);
                return outcome(worker, PerTestJvmExecutionStrategy.processError("worker JVM exited"
                        + (exit >= 0 ? " (exit code " + exit + ")" : "") + " before reporting a result for the test. "
                        + "The test may have called System.exit/Runtime.halt or the JVM crashed."
                        + worker.diagnostics()), startedAt, null, true);
            }
            default -> {
                return answer(worker, message.payload(), seq, locator, startedAt);
            }
        }
    }

    private ExecutionOutcome answer(PooledWorker worker, String payload, long seq, TestLocator locator, long startedAt) {
        try {
            Map<String, String> m = IsolationProtocol.fromLine(payload);
            IsolationProtocol.checkVersionAndEnd(m);
            if (!IsolationProtocol.TYPE_RESULT.equals(m.get("type")) || !Long.toString(seq).equals(m.get("seq"))) {
                throw new IsolationProtocol.ProtocolException("unexpected message from the worker");
            }
            IsolationProtocol.ChildResult r = IsolationProtocol.parseResult(m, worker.nonce(), locator.id());
            worker.testFinished();
            long duration = System.nanoTime() - startedAt;
            return new ExecutionOutcome(PerTestJvmExecutionStrategy.toTestResult(r), duration,
                    IsolationProtocol.req(m, "stdout"), IsolationProtocol.req(m, "stderr"),
                    Boolean.parseBoolean(IsolationProtocol.req(m, "stdoutTruncated")),
                    Boolean.parseBoolean(IsolationProtocol.req(m, "stderrTruncated")),
                    OptionalLong.of(worker.pid()), true);
        } catch (IOException e) {
            worker.kill(options.gracePeriodMillis());
            return outcome(worker, PerTestJvmExecutionStrategy.processError("invalid answer from the worker JVM: "
                    + e.getMessage() + ". The worker was discarded." + worker.diagnostics()), startedAt, null, true);
        }
    }

    private ExecutionOutcome lost(PooledWorker worker, long startedAt, String detail) {
        worker.markBroken();
        int exit = worker.exitCode(1_000);
        worker.awaitReaders(PerTestJvmExecutionStrategy.DRAIN_WAIT_MILLIS);
        return outcome(worker, PerTestJvmExecutionStrategy.processError(detail
                + (exit >= 0 ? " (the worker already exited with code " + exit + ")" : "") + worker.diagnostics()),
                startedAt, null, true);
    }

    private static ExecutionOutcome outcome(PooledWorker worker, TestResult result, long startedAt, Void unused, boolean terminated) {
        return new ExecutionOutcome(result, System.nanoTime() - startedAt, "", "", false, false,
                OptionalLong.of(worker.pid()), terminated);
    }

    // --- worker lifecycle ---

    private PooledWorker takeIdle() {
        synchronized (idle) {
            while (!idle.isEmpty()) {
                PooledWorker w = idle.pop();
                if (!w.broken() && w.process().isAlive()) return w;
                discard(w);
            }
        }
        return null;
    }

    /** Returns a healthy worker to the pool, or retires it. */
    private void release(PooledWorker worker) {
        int limit = options.maxTestsPerWorker();
        boolean retire = worker.broken() || closed || (limit > 0 && worker.testsRun() >= limit);
        if (retire) {
            discard(worker);
        } else {
            synchronized (idle) {
                idle.push(worker);
            }
        }
    }

    private void discard(PooledWorker worker) {
        if (all.remove(worker)) {
            if (worker.broken()) {
                worker.kill(options.gracePeriodMillis());
            } else {
                worker.shutdown(options.gracePeriodMillis());
            }
        }
    }

    private PooledWorker startWorker() throws StartFailure {
        List<String> command;
        try {
            ChildJvmCommandFactory factory = options.commandFactory() != null
                    ? options.commandFactory() : ChildJvmCommandFactory.standard();
            command = List.copyOf(factory.command(new ChildJvmCommandFactory.Request(null, options.jvmArgs(), true)));
        } catch (RuntimeException e) {
            throw new StartFailure(TestResult.InfrastructureFailure.Kind.CONFIGURATION_ERROR,
                    "cannot build the worker JVM command: " + e);
        }
        Process process;
        try {
            process = new ProcessBuilder(command).start(); // each argument separate: no shell, no quoting
        } catch (IOException | RuntimeException e) {
            throw new StartFailure(TestResult.InfrastructureFailure.Kind.PROCESS_ERROR,
                    "failed to start the worker JVM: " + e.getMessage() + "\n  command: " + PerTestJvmExecutionStrategy.render(command)
                            + "\n  Check that java.home/bin/java exists and that the class path/JVM arguments are valid.");
        }
        String nonce = PerTestJvmExecutionStrategy.randomNonce();
        PooledWorker worker = new PooledWorker(process, nonce);
        all.add(worker);
        if (closed) {
            discardBroken(worker);
            throw new StartFailure(TestResult.InfrastructureFailure.Kind.PROCESS_ERROR, "the worker pool is closed");
        }

        try {
            worker.send(IsolationProtocol.toLine(IsolationProtocol.helloFields(nonce, options.suiteProvider(), options.maxOutputChars())));
            PooledWorker.Message ready = worker.poll(TimeUnit.MILLISECONDS.toNanos(STARTUP_TIMEOUT_MILLIS));
            switch (ready.kind()) {
                case TIMEOUT -> {
                    discardBroken(worker);
                    throw new StartFailure(TestResult.InfrastructureFailure.Kind.PROCESS_ERROR,
                            "the worker JVM did not become ready within " + STARTUP_TIMEOUT_MILLIS / 1000 + " s" + worker.diagnostics());
                }
                case END_OF_STREAM -> {
                    worker.markBroken();
                    int exit = worker.exitCode(2_000);
                    worker.awaitReaders(PerTestJvmExecutionStrategy.DRAIN_WAIT_MILLIS);
                    String diagnostics = worker.diagnostics();
                    discardBroken(worker);
                    throw new StartFailure(TestResult.InfrastructureFailure.Kind.PROCESS_ERROR,
                            "the worker JVM exited during start-up" + (exit >= 0 ? " (exit code " + exit + ")" : "")
                                    + ". Check the JVM arguments and that the provider is on the class path.\n  command: "
                                    + PerTestJvmExecutionStrategy.render(command) + diagnostics);
                }
                default -> checkReady(worker, ready.payload());
            }
            return worker;
        } catch (IOException e) {
            discardBroken(worker);
            throw new StartFailure(TestResult.InfrastructureFailure.Kind.PROCESS_ERROR,
                    "cannot talk to the worker JVM: " + e.getMessage() + worker.diagnostics());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            discardBroken(worker);
            throw new StartFailure(TestResult.InfrastructureFailure.Kind.PROCESS_ERROR,
                    "the runner thread was interrupted while starting a worker JVM");
        }
    }

    private void checkReady(PooledWorker worker, String payload) throws IOException {
        Map<String, String> m;
        try {
            m = IsolationProtocol.fromLine(payload);
            IsolationProtocol.checkVersionAndEnd(m);
            if (!IsolationProtocol.TYPE_READY.equals(m.get("type")) || !worker.nonce().equals(m.get("nonce"))
                    || !worker.nonce().equals(m.get("end"))) {
                throw new IsolationProtocol.ProtocolException("unexpected start-up message from the worker");
            }
        } catch (IOException e) {
            discardBroken(worker);
            throw new StartFailure(TestResult.InfrastructureFailure.Kind.PROCESS_ERROR,
                    "invalid start-up message from the worker JVM: " + e.getMessage() + worker.diagnostics());
        }
        String status = IsolationProtocol.req(m, "status");
        if (!"READY".equals(status)) {
            discardBroken(worker);
            throw new StartFailure(IsolationProtocol.Status.CONFIGURATION_ERROR.name().equals(status)
                    ? TestResult.InfrastructureFailure.Kind.CONFIGURATION_ERROR
                    : TestResult.InfrastructureFailure.Kind.PROCESS_ERROR, IsolationProtocol.req(m, "detail"));
        }
    }

    private void discardBroken(PooledWorker worker) {
        worker.markBroken();
        discard(worker);
    }

    private static ExecutionOutcome failure(TestResult.InfrastructureFailure.Kind kind, String detail, long start) {
        return ExecutionOutcome.of(new TestResult.InfrastructureFailure(kind, detail), System.nanoTime() - start);
    }

    /** A worker could not be started; carries the result to report for the test that needed it. */
    private static final class StartFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final transient TestResult result;

        StartFailure(TestResult.InfrastructureFailure.Kind kind, String detail) {
            super(detail, null, false, false);
            this.result = new TestResult.InfrastructureFailure(kind, detail);
        }
    }
}
