package test.unit.execution;

import test.unit.config.Config;
import test.unit.config.IsolationOptions;
import test.unit.config.TestIsolationMode;
import test.unit.core.SameJvmExecutionStrategy;
import test.unit.core.Test;
import test.unit.core.TestSuite;
import test.unit.logging.Logger;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Entry point of the long-lived child JVM used by {@link TestIsolationMode#POOLED_JVM}. It builds the suites of the
 * {@link TestSuiteProvider} once and then runs, one at a time, the tests requested by the parent.
 * <p>
 * Usage (built by {@link ChildJvmCommandFactory}): {@code PooledTestWorker <protocolVersion>}. Requests are read as
 * lines from the original stdin and answers written as marked lines to the original stdout (see {@link IsolationProtocol}).
 * Afterwards {@code System.in} is replaced by an empty stream and {@code System.out}/{@code System.err} by capturing
 * streams, so the test cannot steal requests or corrupt answers; what it prints is returned with its result.
 * <p>
 * The worker ends, with {@code Runtime.halt}, when stdin is closed. Exit code 0 is a normal shutdown, 64 bad
 * arguments, 65 unreadable first request, 66 answer could not be written.
 *
 * @author Pepe Gallardo & Gemini
 */
public final class PooledTestWorker {
    private PooledTestWorker() {}

    public static void main(String[] args) {
        if (args.length != 1 || !Integer.toString(IsolationProtocol.VERSION).equals(args[0])) {
            System.err.println("PooledTestWorker: expected argument <protocolVersion=" + IsolationProtocol.VERSION + ">");
            halt(64);
        }

        BufferedReader requests = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintStream control = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
        BoundedCapture stdout = new BoundedCapture();
        BoundedCapture stderr = new BoundedCapture();
        System.setIn(new ByteArrayInputStream(new byte[0]));

        String nonce = null;
        List<TestSuite> suites = null;
        int maxOutputChars = IsolationOptions.DEFAULT_MAX_OUTPUT_CHARS;
        try {
            Map<String, String> hello = IsolationProtocol.fromLine(requests.readLine());
            IsolationProtocol.checkVersionAndEnd(hello);
            if (!IsolationProtocol.TYPE_HELLO.equals(hello.get("type"))) {
                throw new IOException("expected a hello message");
            }
            nonce = IsolationProtocol.req(hello, "nonce");
            maxOutputChars = Integer.parseInt(IsolationProtocol.req(hello, "maxOutputChars"));
            try {
                suites = TestResolver.loadSuites(IsolationProtocol.req(hello, "provider"));
            } catch (TestResolver.ResolutionException e) {
                send(control, IsolationProtocol.readyFields(nonce, IsolationProtocol.Status.CONFIGURATION_ERROR, e.getMessage()));
                halt(IsolationProtocol.Status.CONFIGURATION_ERROR.exitCode);
            }
        } catch (Throwable t) {
            System.err.println("PooledTestWorker: cannot read hello: " + t);
            halt(65);
        }
        send(control, IsolationProtocol.readyFields(nonce, null, ""));

        System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));

        while (true) {
            String line;
            try {
                line = requests.readLine();
            } catch (IOException e) {
                line = null;
            }
            if (line == null) {
                halt(0); // the parent closed our stdin: normal shutdown
            }
            stdout.reset(maxOutputChars);
            stderr.reset(maxOutputChars);
            Map<String, String> answer = answer(line, nonce, suites, stdout, stderr);
            if (answer != null) {
                send(control, answer);
            }
        }
    }

    /** Runs one requested test and builds the answer; {@code null} if the request is unusable (the parent then times out). */
    private static Map<String, String> answer(String line, String nonce, List<TestSuite> suites,
                                              BoundedCapture stdout, BoundedCapture stderr) {
        IsolationProtocol.WorkerRequest request;
        long seq;
        try {
            Map<String, String> m = IsolationProtocol.fromLine(line);
            IsolationProtocol.checkVersionAndEnd(m);
            if (!IsolationProtocol.TYPE_TEST.equals(m.get("type")) || !nonce.equals(m.get("nonce")) || !nonce.equals(m.get("end"))) {
                return null;
            }
            seq = Long.parseLong(IsolationProtocol.req(m, "seq"));
            request = IsolationProtocol.WorkerRequest.fromFields(m);
        } catch (IOException | RuntimeException e) {
            return null;
        }

        String id = request.locator().id();
        IsolationProtocol.ChildResult result;
        try {
            Test test = TestResolver.find(suites, request.locator());
            Logger logger = request.ansi() ? new Logger.AnsiConsoleLogger() : new Logger.ConsoleLogger();
            Config config = new Config(logger, request.language(), request.childTimeoutSeconds(), false, IsolationOptions.DEFAULT);
            ExecutionOutcome outcome = SameJvmExecutionStrategy.INSTANCE
                    .execute(new TestExecutionRequest(test, request.locator(), config));
            result = IsolatedTestWorker.toChildResult(outcome.result(), id, outcome.durationNanos(), config);
        } catch (TestResolver.ResolutionException e) {
            result = IsolationProtocol.ChildResult.failure(IsolationProtocol.Status.CONFIGURATION_ERROR, id, e.getMessage());
        } catch (Throwable t) { // infrastructure problem, not a test failure
            result = IsolationProtocol.ChildResult.failure(IsolationProtocol.Status.INTERNAL_ERROR, id,
                    "unexpected worker error: " + TestResolver.stackTrace(t));
        }
        var captured = new IsolationProtocol.CapturedOutput(stdout.text(), stderr.text(), stdout.truncated(), stderr.truncated());
        return IsolationProtocol.pooledResultFields(nonce, seq, result, captured);
    }

    private static void send(PrintStream control, Map<String, String> fields) {
        control.print("\n" + IsolationProtocol.LINE_MARKER + IsolationProtocol.toLine(fields) + "\n");
        control.flush();
        if (control.checkError()) {
            halt(66);
        }
    }

    private static void halt(int code) {
        System.out.flush();
        System.err.flush();
        Runtime.getRuntime().halt(code);
    }

    /** Thread-safe in-memory sink keeping at most {@code maxChars} characters (approximated with a byte bound). */
    private static final class BoundedCapture extends OutputStream {
        private java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private long maxBytes = Long.MAX_VALUE;
        private int maxChars = Integer.MAX_VALUE;
        private boolean truncated;

        synchronized void reset(int maxChars) {
            this.maxChars = maxChars;
            this.maxBytes = 4L * maxChars; // at most 4 bytes per UTF-8 character
            this.bytes = new java.io.ByteArrayOutputStream();
            this.truncated = false;
        }

        @Override
        public synchronized void write(int b) {
            write(new byte[] {(byte) b}, 0, 1);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            long room = maxBytes - bytes.size();
            if (len <= room) {
                bytes.write(b, off, len);
            } else {
                if (room > 0) bytes.write(b, off, (int) room);
                truncated = true;
            }
        }

        synchronized String text() {
            String s = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            if (s.length() > maxChars) {
                truncated = true;
                return s.substring(0, maxChars);
            }
            return s;
        }

        synchronized boolean truncated() {
            return truncated;
        }
    }
}
