package test.unit;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Outcome of executing one test through a {@link TestExecutionStrategy}: the {@link TestResult} plus
 * information that only exists for out-of-process executions.
 *
 * @param result The test result.
 * @param durationNanos Wall-clock duration measured with {@code System.nanoTime()}.
 * @param stdout Captured standard output of the child JVM (empty in SAME_JVM, where the test writes directly to the console).
 * @param stderr Captured standard error of the child JVM.
 * @param stdoutTruncated Whether stdout exceeded {@link IsolationOptions#maxOutputChars()} and was cut.
 * @param stderrTruncated Whether stderr exceeded the limit and was cut.
 * @param childPid PID of the child JVM, if one was started.
 * @param childTerminated {@code false} only if a child JVM could not be verified dead (infrastructure problem).
 * @author Pepe Gallardo & Gemini
 */
public record ExecutionOutcome(
        TestResult result,
        long durationNanos,
        String stdout,
        String stderr,
        boolean stdoutTruncated,
        boolean stderrTruncated,
        OptionalLong childPid,
        boolean childTerminated
) {
    public ExecutionOutcome {
        Objects.requireNonNull(result, "result cannot be null");
        Objects.requireNonNull(stdout, "stdout cannot be null");
        Objects.requireNonNull(stderr, "stderr cannot be null");
        Objects.requireNonNull(childPid, "childPid cannot be null");
    }

    /** Outcome without any captured output or child process. */
    public static ExecutionOutcome of(TestResult result, long durationNanos) {
        return new ExecutionOutcome(result, durationNanos, "", "", false, false, OptionalLong.empty(), true);
    }
}
