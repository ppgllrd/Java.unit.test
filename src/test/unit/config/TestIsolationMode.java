package test.unit.config;

import test.unit.core.TestSuite;
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
    PER_TEST_JVM,

    /**
     * Tests run in a pool of long-lived child JVMs that are reused across tests, so the JVM start-up and the
     * construction of the suites are paid once per worker instead of once per test. A timed-out, crashed or
     * exiting test only costs its worker, which is replaced. Static state is <em>not</em> isolated between tests that
     * share a worker. Requires {@link IsolationOptions#suiteProvider()}.
     */
    POOLED_JVM
}
