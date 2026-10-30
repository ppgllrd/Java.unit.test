package test.unit.execution;

import test.unit.config.Config;
import test.unit.config.IsolationOptions;
import test.unit.config.TestIsolationMode;
import test.unit.core.SameJvmExecutionStrategy;
import test.unit.core.Test;
import test.unit.core.TestResult;
import test.unit.logging.Logger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Entry point of the child JVM used by {@link TestIsolationMode#PER_TEST_JVM}. It runs <b>exactly one</b> test.
 * <p>
 * Usage (built by {@link ChildJvmCommandFactory}): {@code IsolatedTestWorker <protocolVersion> <resultFile>}; the
 * {@link IsolationProtocol.WorkerRequest} with the identity of the test is read from stdin. The result is
 * written to {@code resultFile}, never to stdout/stderr, which belong to the test.
 * <p>
 * Exit codes: 0 passed, 1 failed, 2 error, 3 timed out, 4 configuration error, 5 internal error,
 * 64 bad arguments, 65 unreadable request, 66 result could not be written. The parent never trusts the exit code
 * alone; it requires a valid result file.
 * <p>
 * The worker ends with {@code Runtime.halt} so lingering non-daemon threads or shutdown hooks of the test cannot keep it alive.
 *
 * @author Pepe Gallardo & Gemini
 */
public final class IsolatedTestWorker {
    private IsolatedTestWorker() {}

    public static void main(String[] args) {
        if (args.length != 2 || !Integer.toString(IsolationProtocol.VERSION).equals(args[0])) {
            System.err.println("IsolatedTestWorker: expected arguments <protocolVersion=" + IsolationProtocol.VERSION
                    + "> <resultFile>");
            halt(64);
        }
        Path resultFile = Path.of(args[1]);

        IsolationProtocol.WorkerRequest request = null;
        try {
            request = IsolationProtocol.WorkerRequest.decode(
                    new String(System.in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Throwable t) {
            System.err.println("IsolatedTestWorker: cannot read request: " + t);
            halt(65);
        }

        IsolationProtocol.ChildResult result;
        try {
            result = runTest(request);
        } catch (Throwable t) { // infrastructure problem, not a test failure
            result = IsolationProtocol.ChildResult.failure(IsolationProtocol.Status.INTERNAL_ERROR,
                    request.locator().id(), "unexpected worker error: " + TestResolver.stackTrace(t));
        }

        try {
            IsolationProtocol.writeResult(resultFile, request.nonce(), result);
        } catch (Throwable t) {
            System.err.println("IsolatedTestWorker: cannot write result: " + t);
            halt(66);
        }
        halt(result.status().exitCode);
    }

    private static IsolationProtocol.ChildResult runTest(IsolationProtocol.WorkerRequest request) throws Exception {
        TestLocator locator = request.locator();
        String id = locator.id();

        Test test;
        try {
            test = TestResolver.find(TestResolver.loadSuites(locator.providerClass()), locator);
        } catch (TestResolver.ResolutionException e) {
            return IsolationProtocol.ChildResult.failure(IsolationProtocol.Status.CONFIGURATION_ERROR, id, e.getMessage());
        }

        Logger logger = request.ansi() ? new Logger.AnsiConsoleLogger() : new Logger.ConsoleLogger();
        Config config = new Config(logger, request.language(), request.childTimeoutSeconds(), false, IsolationOptions.DEFAULT);

        // Same semantics as SAME_JVM: the shared strategy runs Test.executeTest
        ExecutionOutcome outcome = SameJvmExecutionStrategy.INSTANCE.execute(new TestExecutionRequest(test, locator, config));
        return toChildResult(outcome.result(), id, outcome.durationNanos(), config);
    }

    static IsolationProtocol.ChildResult toChildResult(TestResult r, String id, long nanos, Config config) {
        String message = r.message(config);
        if (r instanceof TestResult.Success) {
            return new IsolationProtocol.ChildResult(IsolationProtocol.Status.PASSED, id, nanos, "", "", "", message, "", 0, "");
        } else if (r instanceof TestResult.UnexpectedExceptionFailure u) {
            return new IsolationProtocol.ChildResult(IsolationProtocol.Status.ERROR, id, nanos,
                    u.thrown().getClass().getName(), String.valueOf(u.thrown().getMessage()), TestResolver.stackTrace(u.thrown()),
                    message, "", 0, "");
        } else if (r instanceof TestResult.TimeoutFailure t) {
            return new IsolationProtocol.ChildResult(IsolationProtocol.Status.TIMED_OUT, id, nanos, "", "", "",
                    message, t.expectedBehaviorDescription(), t.timeout(), "");
        }
        return new IsolationProtocol.ChildResult(IsolationProtocol.Status.FAILED, id, nanos, "", "", "", message, "", 0, "");
    }

    private static void halt(int code) {
        System.out.flush();
        System.err.flush();
        Runtime.getRuntime().halt(code);
    }
}
