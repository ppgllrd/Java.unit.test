package test.unit.execution;

import test.unit.core.SuiteItem;
import test.unit.core.Test;
import test.unit.core.TestSuite;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Rebuilds suites through a {@link TestSuiteProvider} inside a child JVM and finds the test a {@link TestLocator} points to.
 * Shared by {@link IsolatedTestWorker} (once per test) and {@link PooledTestWorker} (once per worker).
 */
final class TestResolver {
    private TestResolver() {}

    /** The provider, or the locator, cannot be resolved to a test. Reported as a configuration error. */
    static final class ResolutionException extends Exception {
        private static final long serialVersionUID = 1L;
        ResolutionException(String message) { super(message); }
    }

    /** Instantiates the provider (public no-arg constructor) and returns its suites. */
    static List<TestSuite> loadSuites(String providerName) throws ResolutionException {
        Class<?> providerClass;
        try {
            providerClass = Class.forName(providerName, true, TestResolver.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            throw new ResolutionException("suite provider class cannot be loaded: " + providerName
                    + " (" + e + "). Is it on the child's class path?");
        }
        if (!TestSuiteProvider.class.isAssignableFrom(providerClass)) {
            throw new ResolutionException(providerName + " does not implement TestSuiteProvider");
        }
        List<TestSuite> suites;
        try {
            Constructor<?> constructor = providerClass.getDeclaredConstructor();
            suites = ((TestSuiteProvider) constructor.newInstance()).suites();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            throw new ResolutionException("suite provider " + providerName
                    + " cannot be instantiated (needs a public no-arg constructor) or its suites() failed: "
                    + stackTrace(e instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e));
        }
        if (suites == null) {
            throw new ResolutionException("suite provider returned null");
        }
        return suites;
    }

    /** Finds the test identified by the locator among the given suites, verifying it is the expected one. */
    static Test find(List<TestSuite> suites, TestLocator locator) throws ResolutionException {
        List<TestSuite> matching = suites.stream().filter(s -> s.getName().equals(locator.suiteName())).toList();
        if (matching.size() != 1) {
            throw new ResolutionException("expected exactly one suite named '" + locator.suiteName() + "' but the provider returned "
                    + matching.size() + "; suite names must be unique and deterministic");
        }
        List<SuiteItem> items = matching.get(0).getItems();
        if (locator.itemIndex() >= items.size() || !(items.get(locator.itemIndex()) instanceof Test test)) {
            throw new ResolutionException("item " + locator.itemIndex() + " of suite '" + locator.suiteName() + "' is not a test");
        }
        if (!test.getName().equals(locator.testName()) || !test.getClass().getName().equals(locator.testClass())) {
            throw new ResolutionException("item " + locator.itemIndex() + " of suite '" + locator.suiteName() + "' is "
                    + test.getClass().getName() + " '" + test.getName() + "', not the expected "
                    + locator.testClass() + " '" + locator.testName() + "'; the provider is not deterministic");
        }
        return test;
    }

    static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
