package test.unit.execution;

import test.unit.config.TestIsolationMode;
import test.unit.core.TestExecutionStrategy;
import test.unit.core.TestResult;

/**
 * Strategy of {@link TestIsolationMode#POOLED_JVM}: runs the test on a worker of the {@link WorkerPool} carried by the
 * request. Without a pool (the test was not run through {@code TestSuite.run/runAll}) or without a suite provider it
 * reports a configuration error.
 *
 * @author Pepe Gallardo & Gemini
 */
public final class PooledJvmExecutionStrategy implements TestExecutionStrategy {
    public static final PooledJvmExecutionStrategy INSTANCE = new PooledJvmExecutionStrategy();

    private PooledJvmExecutionStrategy() {}

    @Override
    public ExecutionOutcome execute(TestExecutionRequest request) {
        if (request.pool() == null || request.locator() == null) {
            return ExecutionOutcome.of(new TestResult.InfrastructureFailure(
                    TestResult.InfrastructureFailure.Kind.CONFIGURATION_ERROR,
                    "POOLED_JVM needs IsolationOptions.suiteProvider (a TestSuiteProvider class) and the test must run "
                            + "through TestSuite.run/runAll so that a worker pool exists and the worker can locate the test"), 0);
        }
        return request.pool().execute(request);
    }
}
