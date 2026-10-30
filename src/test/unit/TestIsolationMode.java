package test.unit;

/**
 * Selects how the tests of a {@link TestSuite} are isolated from each other and from the JVM running the suite.
 * Configured through {@link IsolationOptions} (see {@link Config#isolation()}).
 *
 * @author Pepe Gallardo & Gemini
 */
public enum TestIsolationMode {
    /**
     * All tests run inside the JVM that runs the suite, using the library's classic mechanism
     * (asynchronous evaluation + {@code Future.get(timeout)} + interruption). This is the default.
     */
    SAME_JVM,

    /**
     * Every test runs in its own, freshly started child JVM. The parent JVM enforces the timeout and
     * forcibly terminates the child if needed. Requires {@link IsolationOptions#suiteProvider()}.
     */
    PER_TEST_JVM
}
