package test.unit;

import java.util.Objects;

/**
 * Unambiguous, reconstructible identity of one test execution: the provider that rebuilds the suites,
 * the suite name, the position of the test inside the suite items, its name and its concrete class.
 * Only these plain strings/ints cross the process boundary (never serialized objects).
 *
 * @param providerClass Fully qualified name of the {@link TestSuiteProvider} implementation.
 * @param suiteName Name of the suite (unique within the provider).
 * @param itemIndex Index of the test within {@link TestSuite#getItems()} (counting info messages too).
 * @param testName Name of the test.
 * @param testClass Fully qualified class name of the test, used as a consistency check.
 * @author Pepe Gallardo & Gemini
 */
public record TestLocator(String providerClass, String suiteName, int itemIndex, String testName, String testClass) {
    public TestLocator {
        Objects.requireNonNull(providerClass, "providerClass cannot be null");
        Objects.requireNonNull(suiteName, "suiteName cannot be null");
        Objects.requireNonNull(testName, "testName cannot be null");
        Objects.requireNonNull(testClass, "testClass cannot be null");
        if (itemIndex < 0) {
            throw new IllegalArgumentException("itemIndex must not be negative");
        }
    }

    /** A readable, stable identifier such as {@code com.acme.Suites#My Suite[3]:Simple Addition}. */
    public String id() {
        return providerClass + "#" + suiteName + "[" + itemIndex + "]:" + testName;
    }
}
