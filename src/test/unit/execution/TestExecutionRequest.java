package test.unit.execution;

import test.unit.config.Config;
import test.unit.core.Test;
import test.unit.core.TestSuite;
import java.util.Objects;

/**
 * Everything a {@link TestExecutionStrategy} needs to run one test.
 *
 * @param test The test to run.
 * @param locator Identity of the test for out-of-process execution; may be {@code null} when the test was not
 *                run through a {@link TestSuite} (then only SAME_JVM can run it).
 * @param config Configuration whose timeout is already the <em>resolved</em> timeout of this test.
 * @param pool Worker pool of the current run; only used (and required) by POOLED_JVM, otherwise may be {@code null}.
 * @author Pepe Gallardo & Gemini
 */
public record TestExecutionRequest(Test test, TestLocator locator, Config config, WorkerPool pool) {
    public TestExecutionRequest {
        Objects.requireNonNull(test, "test cannot be null");
        Objects.requireNonNull(config, "config cannot be null");
    }

    /** A request without a worker pool. */
    public TestExecutionRequest(Test test, TestLocator locator, Config config) {
        this(test, locator, config, null);
    }
}
