package test.unit.execution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Parent-side handle of one long-lived child JVM ({@link PooledTestWorker}). Owns the process, writes request lines to
 * its stdin and turns its stdout into a queue of protocol messages; any other stdout, and stderr, is only kept as a
 * short tail for diagnostics. Used by one thread at a time (the {@link WorkerPool} hands it out exclusively).
 */
final class PooledWorker {
    private static final int TAIL_CHARS = 2_000;

    /** What {@link #poll} found. */
    enum Kind { LINE, END_OF_STREAM, TIMEOUT }

    record Message(Kind kind, String payload) {}

    private final Process process;
    private final String nonce;
    private final BlockingQueue<Message> messages = new LinkedBlockingQueue<>();
    private final Tail tail = new Tail();
    private final Thread stdoutReader;
    private final Thread stderrReader;
    private OutputStream stdin;
    private long seq;
    private int testsRun;
    private boolean broken;

    PooledWorker(Process process, String nonce) {
        this.process = process;
        this.nonce = nonce;
        this.stdin = process.getOutputStream();
        this.stdoutReader = new Thread(this::readStdout, "test-unit-pool-stdout-" + process.pid());
        this.stderrReader = new Thread(this::readStderr, "test-unit-pool-stderr-" + process.pid());
        stdoutReader.setDaemon(true);
        stderrReader.setDaemon(true);
        stdoutReader.start();
        stderrReader.start();
    }

    String nonce() { return nonce; }

    Process process() { return process; }

    long pid() { return process.pid(); }

    long nextSeq() { return ++seq; }

    int testsRun() { return testsRun; }

    void testFinished() { testsRun++; }

    boolean broken() { return broken; }

    void markBroken() { broken = true; }

    /** Last diagnostic output of the worker, formatted for appending to an error message ("" if none). */
    String diagnostics() {
        return PerTestJvmExecutionStrategy.tail(tail.text());
    }

    void send(String line) throws IOException {
        stdin.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        stdin.flush();
    }

    /** Waits up to {@code nanos} for the next protocol message. */
    Message poll(long nanos) throws InterruptedException {
        Message m = messages.poll(Math.max(0, nanos), TimeUnit.NANOSECONDS);
        return m == null ? new Message(Kind.TIMEOUT, null) : m;
    }

    /** Waits (bounded) until the readers hit end of stream, so that diagnostics are complete. */
    void awaitReaders(long millis) {
        try {
            stdoutReader.join(millis);
            stderrReader.join(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Exit code if the process has exited within the (short) wait, otherwise -1. */
    int exitCode(long waitMillis) {
        try {
            return process.waitFor(waitMillis, TimeUnit.MILLISECONDS) ? process.exitValue() : -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    /** Asks the worker to exit by closing its stdin; kills it if it does not within a short time. */
    void shutdown(long gracePeriodMillis) {
        try {
            stdin.close();
        } catch (IOException ignored) {
            // already dead
        }
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                PerTestJvmExecutionStrategy.terminate(process, gracePeriodMillis);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            PerTestJvmExecutionStrategy.terminate(process, 0);
        }
        PerTestJvmExecutionStrategy.closeQuietly(process);
    }

    /** Kills the worker (and, best effort, its descendants); returns whether it is verified dead. */
    boolean kill(long gracePeriodMillis) {
        markBroken();
        boolean dead = PerTestJvmExecutionStrategy.terminate(process, gracePeriodMillis);
        awaitReaders(PerTestJvmExecutionStrategy.DRAIN_WAIT_MILLIS);
        PerTestJvmExecutionStrategy.closeQuietly(process);
        return dead;
    }

    private void readStdout() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int at = line.indexOf(IsolationProtocol.LINE_MARKER);
                if (at >= 0) {
                    messages.add(new Message(Kind.LINE, line.substring(at + IsolationProtocol.LINE_MARKER.length())));
                } else {
                    tail.append(line + "\n");
                }
            }
        } catch (IOException ignored) {
            // stream closed because the process died or was cleaned up
        } finally {
            messages.add(new Message(Kind.END_OF_STREAM, null));
        }
    }

    private void readStderr() {
        try (var reader = new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)) {
            char[] chunk = new char[4096];
            int n;
            while ((n = reader.read(chunk)) != -1) {
                tail.append(new String(chunk, 0, n));
            }
        } catch (IOException ignored) {
            // stream closed because the process died or was cleaned up
        }
    }

    /** The last {@value #TAIL_CHARS} characters written, thread-safe. */
    private static final class Tail {
        private final StringBuilder text = new StringBuilder();

        synchronized void append(String s) {
            text.append(s);
            if (text.length() > 2 * TAIL_CHARS) {
                text.delete(0, text.length() - TAIL_CHARS);
            }
        }

        synchronized String text() {
            return text.length() > TAIL_CHARS ? text.substring(text.length() - TAIL_CHARS) : text.toString();
        }
    }
}
