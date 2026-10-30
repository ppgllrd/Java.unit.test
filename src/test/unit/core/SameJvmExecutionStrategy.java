package test.unit.core;

import test.unit.execution.ExecutionOutcome;
import test.unit.execution.TestExecutionRequest;

/**
 * Runs the test in the current JVM exactly as the library always did. Also used by the child JVM worker,
 * so both modes share the same execution semantics.
 *
 * @author Pepe Gallardo & Gemini
 */
public final class SameJvmExecutionStrategy implements TestExecutionStrategy {
    public static final SameJvmExecutionStrategy INSTANCE = new SameJvmExecutionStrategy();

    private SameJvmExecutionStrategy() {}

    @Override
    public ExecutionOutcome execute(TestExecutionRequest request) {
        long start = System.nanoTime();
        TestResult result = request.test().executeTest(request.config());
        return ExecutionOutcome.of(result, System.nanoTime() - start);
    }
}
