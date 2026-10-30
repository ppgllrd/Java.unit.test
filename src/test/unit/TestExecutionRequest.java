package test.unit;

import java.util.Objects;

/**
 * Everything a {@link TestExecutionStrategy} needs to run one test.
 *
 * @param test The test to run.
 * @param locator Identity of the test for out-of-process execution; may be {@code null} when the test was not
 *                run through a {@link TestSuite} (then only SAME_JVM can run it).
 * @param config Configuration whose timeout is already the <em>resolved</em> timeout of this test.
 * @author Pepe Gallardo & Gemini
 */
public record TestExecutionRequest(Test test, TestLocator locator, Config config) {
    public TestExecutionRequest {
        Objects.requireNonNull(test, "test cannot be null");
        Objects.requireNonNull(config, "config cannot be null");
    }
}
