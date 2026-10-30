package test.unit;

import java.util.List;

/**
 * Rebuilds the suites of a project inside a child JVM when running with {@link TestIsolationMode#PER_TEST_JVM}.
 * <p>
 * Tests are made of lambdas and cannot be shipped to another JVM, so each child JVM re-creates the suites
 * through a provider and selects the one test it must run by {@link TestLocator}. Implementations must be
 * public classes with a public no-argument constructor, and must be deterministic: they must return the same
 * suites (same names, same items in the same order) every time, and suite names must be unique within the list.
 * The same provider should be used by the main program to obtain the suites it runs.
 *
 * @author Pepe Gallardo & Gemini
 */
public interface TestSuiteProvider {
    /** Builds the suites. Must be deterministic and free of side effects other than creating the tests. */
    List<TestSuite> suites();
}
