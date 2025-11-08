package test.unit;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a named collection of related {@link Test} cases.
 * A `TestSuite` allows grouping tests and running them together, aggregating
 * their results.
 *
 * @author Pepe Gallardo & Gemini
 */
public final class TestSuite { // Made final

    private final String name;
    private final List<Test> tests; // Use immutable list

    /**
     * Creates a new TestSuite.
     * @param name The name identifying this suite of tests. Must not be null.
     * @param tests The {@link Test} instances that constitute this suite (varargs). Must not be null or contain nulls.
     */
    public TestSuite(String name, Test... tests) {
        this.name = Objects.requireNonNull(name, "TestSuite name cannot be null");
        Objects.requireNonNull(tests, "Test array cannot be null");
        if (Arrays.stream(tests).anyMatch(Objects::isNull)) {
            throw new NullPointerException("Test array cannot contain null tests");
        }
        this.tests = List.of(tests); // Create immutable list from varargs
    }

    /**
     * Creates a new TestSuite from a List.
     * @param name The name identifying this suite of tests. Must not be null.
     * @param tests The {@link Test} instances that constitute this suite. Must not be null or contain nulls. Copied.
     */
    public TestSuite(String name, List<Test> tests) {
        this.name = Objects.requireNonNull(name, "TestSuite name cannot be null");
        Objects.requireNonNull(tests, "Test list cannot be null");
        if (tests.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("Test list cannot contain null tests");
        }
        this.tests = List.copyOf(tests); // Create immutable copy
    }


    /** Gets the name of the test suite. */
    public String getName() {
        return name;
    }

    /** Gets an immutable list of the tests in this suite. */
    public List<Test> getTests() {
        return tests;
    }

    /**
     * Runs all the {@link Test} cases contained within this suite sequentially,
     * using the provided {@link Config}.
     *
     * @param config The {@link Config} object for this run. Must not be null.
     * @return A {@link Results} object summarizing the outcomes.
     */
    public Results run(Config config) {
        Objects.requireNonNull(config, "Config cannot be null for running suite");
        var logger = config.logger();

        // 1. Log Suite Header
        String headerMessage = config.msg("suite.for", this.name);
        if (logger.supportsAnsiColors()) {
            logger.println(logger.underline(logger.bold(logger.blue(headerMessage))));
        } else {
            logger.println(headerMessage);
            logger.println("=".repeat(headerMessage.length())); // Simple underline
        }

        // 2. Run Individual Tests and Collect Results
        List<TestResult> testResultsList = this.tests.stream()
                .map(test -> test.run(config)) // Run each test with the config
                .collect(Collectors.toList()); // Collect results into a list

        // 3. Aggregate Results
        Results results = new Results(testResultsList);

        // 4. Log Suite Summary
        logger.println(String.format("\n%s\n", results.mkString(config))); // Add newlines

        // 5. Flush Logger
        logger.flush();

        // 6. Return Aggregated Results
        return results;
    }

    // --- Static Utility Methods for Running Multiple Suites ---

    /**
     * Prints a final summary report aggregating results across multiple test suites.
     *
     * @param allResults A list of {@link Results} objects, one per suite run. Must not be null.
     * @param config The {@link Config} used for formatting the summary output. Must not be null.
     */
    private static void printAllResultsSummary(List<Results> allResults, Config config) {
        Objects.requireNonNull(allResults, "allResults list cannot be null");
        Objects.requireNonNull(config, "Config cannot be null for printing summary");

        // Calculate overall statistics
        int totalSuites = allResults.size();
        int totalTests = allResults.stream().mapToInt(Results::getTotal).sum();
        int totalPassed = allResults.stream().mapToInt(Results::getPassed).sum();
        int totalFailed = allResults.stream().mapToInt(Results::getFailed).sum();
        double overallRate = (totalTests == 0) ? 1.0 : (double) totalPassed / totalTests;

        var logger = config.logger();

        // Print the formatted summary block
        String separator = logger.bold(logger.blue("=".repeat(40)));
        logger.println(separator);
        logger.println(logger.bold(logger.blue(config.msg("summary.tittle"))));
        logger.println(separator);
        logger.println(config.msg("summary.suites.run", totalSuites));
        logger.println(config.msg("summary.total.tests", totalTests));
        logger.println(String.format("%s: %s",
                capitalize(config.msg("results.passed")), // Capitalize label
                logger.green(Integer.toString(totalPassed))));
        logger.println(String.format("%s: %s",
                capitalize(config.msg("results.failed")), // Capitalize label
                logger.red(Integer.toString(totalFailed))));
        logger.println(config.msg("summary.success.rate", overallRate * 100.0)); // Format rate
        logger.println(separator);
        logger.println(); // Extra newline
        logger.flush();

        // If CSV output is enabled, print the CSV summary
        if (config.csvOutput()) {
            printCSVSummary(allResults, config);
        }
    }

    private static void printCSVSummary(List<Results> allResults, Config config) {
        Objects.requireNonNull(allResults, "allResults list cannot be null");
        Objects.requireNonNull(config, "Config cannot be null for printing summary");

        var logger = config.logger();

        StringJoiner sj = new StringJoiner(" ");
        for (Results results : allResults) {
            sj.add(results.getPassed() + "/" + results.getTotal());
        }
        logger.println(sj.toString());

        sj = new StringJoiner(";;");
        for (Results results : allResults) {
            StringJoiner detailSj = new StringJoiner(";");
            for (char c : results.getDetails().toCharArray()) {
                detailSj.add(String.valueOf(c));
            }
            sj.add(detailSj.toString());
        }
        logger.println(sj.toString());

        sj = new StringJoiner(";");
        for (Results results : allResults) {
            sj.add(String.valueOf(results.getPassed()));
        }
        logger.println(sj.toString());

        sj = new StringJoiner(";");
        for (Results results : allResults) {
            sj.add(String.format(Locale.US, "%.3f", (double) results.getPassed() / results.getTotal()));
        }
        logger.println(sj.toString());

        logger.flush();
    }

    // Helper to capitalize the first letter of a string
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }


    /**
     * Runs multiple {@link TestSuite} instances sequentially using a specified {@link Config}.
     * After all suites have finished, it prints an overall summary report.
     *
     * @param config The {@link Config} to use for running suites and printing the summary. Must not be null.
     * @param testSuites The {@link TestSuite} instances to run (varargs). Must not be null or contain nulls.
     * @return An immutable list containing the {@link Results} object produced by each executed `TestSuite`.
     */
    public static List<Results> runAll(Config config, TestSuite... testSuites) {
        Objects.requireNonNull(config, "Config cannot be null for runAll");
        Objects.requireNonNull(testSuites, "TestSuite array cannot be null");
        if (Arrays.stream(testSuites).anyMatch(Objects::isNull)) {
            throw new NullPointerException("TestSuite array cannot contain null suites");
        }

        // Run each suite sequentially, collecting their Results objects
        List<Results> allResults = Stream.of(testSuites)
                .map(suite -> suite.run(config))
                .collect(Collectors.toList()); // Collect to mutable list first

        // Print the final overall summary
        printAllResultsSummary(allResults, config);

        // Return an immutable list of results
        return List.copyOf(allResults);
    }

    /**
     * Runs multiple {@link TestSuite} instances sequentially using the default configuration (`Config.DEFAULT`).
     * After all suites have finished, it prints an overall summary report using the default configuration.
     *
     * @param testSuites The {@link TestSuite} instances to run (varargs). Must not be null or contain nulls.
     * @return An immutable list containing the {@link Results} object produced by each executed `TestSuite`.
     */
    public static List<Results> runAll(TestSuite... testSuites) {
        // Delegate to the primary runAll method, explicitly passing Config.DEFAULT
        return runAll(Config.DEFAULT, testSuites);
    }
}
