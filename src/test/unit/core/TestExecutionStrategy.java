package test.unit.core;

import test.unit.config.TestIsolationMode;
import test.unit.execution.ExecutionOutcome;
import test.unit.execution.PerTestJvmExecutionStrategy;
import test.unit.execution.TestExecutionRequest;

/**
 * Strategy that decides <em>where</em> a test runs. {@link SameJvmExecutionStrategy} runs it in the current JVM;
 * {@link PerTestJvmExecutionStrategy} runs it in a dedicated child JVM. The execution semantics of the test itself
 * always come from {@code Test.executeTest}, shared by both.
 *
 * @author Pepe Gallardo & Gemini
 */
public interface TestExecutionStrategy {
    /** Runs one test. Must not throw for test or infrastructure failures: report them in the outcome. */
    ExecutionOutcome execute(TestExecutionRequest request);

    /** Selects the strategy for the given isolation mode. */
    static TestExecutionStrategy forMode(TestIsolationMode mode) {
        return switch (mode) {
            case SAME_JVM -> SameJvmExecutionStrategy.INSTANCE;
            case PER_TEST_JVM -> PerTestJvmExecutionStrategy.INSTANCE;
        };
    }
}
