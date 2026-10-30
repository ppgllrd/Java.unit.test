package test.unit.core;

import test.unit.config.Config;
import test.unit.execution.ExecutionOutcome;
import test.unit.execution.TestLocator;
import test.unit.logging.Logger;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;
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
    private final List<SuiteItem> items; // Use immutable list

    /**
     * Creates a new TestSuite.
     * @param name The name identifying this suite of tests. Must not be null.
     * @param items The {@link SuiteItem} instances that constitute this suite (varargs). Must not be null or contain nulls.
     */
    public TestSuite(String name, SuiteItem... items) { // Change parameter type
        this.name = Objects.requireNonNull(name, "TestSuite name cannot be null");
        Objects.requireNonNull(items, "SuiteItem array cannot be null");
        if (Arrays.stream(items).anyMatch(Objects::isNull)) {
            throw new NullPointerException("SuiteItem array cannot contain null items");
        }
        this.items = List.of(items);
    }

     /**
     * Creates a new TestSuite from a List.
     * @param name The name identifying this suite of tests. Must not be null.
     * @param items The {@link SuiteItem} instances that constitute this suite. Must not be null or contain nulls. Copied.
     */
    public TestSuite(String name, List<SuiteItem> items) {
         this.name = Objects.requireNonNull(name, "TestSuite name cannot be null");
         Objects.requireNonNull(items, "SuiteItem list cannot be null");
          if (items.stream().anyMatch(Objects::isNull)) {
             throw new NullPointerException("SuiteItem list cannot contain null tests");
         }
         this.items = List.copyOf(items); // Create immutable copy
    }


    /** Gets the name of the test suite. */
    public String getName() {
        return name;
    }

    /** Gets an immutable list of the tests in this suite. */
    public List<SuiteItem> getItems() {
        return items;
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
        return runSuites(config, List.of(this)).get(0);
    }

    /**
     * Runs the given suites: all their tests are submitted first (concurrently when {@code config.parallelism() > 1}),
     * while reporting happens in order on the calling thread, so the output is the same as for a sequential run.
     */
    private static List<Results> runSuites(Config config, List<TestSuite> suites) {
        String provider = config.isolation().suiteProvider();
        try (RunScope scope = new RunScope(config)) {
            List<List<Supplier<ExecutionOutcome>>> pending = new ArrayList<>();
            for (TestSuite suite : suites) {
                List<Supplier<ExecutionOutcome>> outcomes = new ArrayList<>();
                for (int index = 0; index < suite.items.size(); index++) {
                    if (suite.items.get(index) instanceof Test test) {
                        TestLocator locator = provider == null ? null
                                : new TestLocator(provider, suite.name, index, test.getName(), test.getClass().getName());
                        outcomes.add(submit(scope, config, test, locator));
                    }
                }
                pending.add(outcomes);
            }
            List<Results> all = new ArrayList<>();
            for (int i = 0; i < suites.size(); i++) {
                all.add(suites.get(i).report(config, pending.get(i)));
            }
            return all;
        }
    }

    private static Supplier<ExecutionOutcome> submit(RunScope scope, Config config, Test test, TestLocator locator) {
        if (scope.executor() == null) {
            return () -> test.executeOutcome(config, locator, scope.pool()); // lazily, when reported
        }
        Future<ExecutionOutcome> future = scope.executor().submit(() -> test.executeOutcome(config, locator, scope.pool()));
        return () -> awaitOutcome(future);
    }

    private static ExecutionOutcome awaitOutcome(Future<ExecutionOutcome> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ExecutionOutcome.of(new TestResult.InfrastructureFailure(
                    TestResult.InfrastructureFailure.Kind.PROCESS_ERROR, "interrupted while waiting for the test"), 0);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            if (e.getCause() instanceof Error err) throw err;
            throw new IllegalStateException(e.getCause());
        }
    }

    private Results report(Config config, List<Supplier<ExecutionOutcome>> outcomes) {
        var logger = config.logger();

        // 1. Log Suite Header
        String headerMessage = config.msg("suite.for", this.name);
        if (logger.supportsAnsiColors()) {
            logger.println(logger.underline(logger.bold(logger.blue(headerMessage))));
        } else {
            logger.println(headerMessage);
            logger.println("=".repeat(headerMessage.length())); // Simple underline
        }

        // 2. Report Individual Tests and Collect Results
        List<TestResult> testResultsList = new ArrayList<>();
        int next = 0;
        for (SuiteItem item : this.items) {
            switch (item) {
                case Test test -> {
                    test.logStart(config);
                    ExecutionOutcome outcome = outcomes.get(next++).get();
                    test.logOutcome(config, outcome);
                    testResultsList.add(outcome.result());
                }
                case InfoMessage msg -> msg.print(config);
            }
        }

        // 3. Aggregate Results
        Results results = new Results(testResultsList);

        // 4. Log Suite Summary
        logger.println(String.format("\n%s\n", results.mkString(config))); // Add newlines
        logger.flush();
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
        logger.println(logger.bold(logger.blue(config.msg("summary.title"))));
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

    public static void printCSVSummary(List<Results> allResults, Config config) {
        Objects.requireNonNull(allResults, "allResults list cannot be null");
        Objects.requireNonNull(config, "Config cannot be null for printing summary");

        final var logger = config.logger();

        logger.println(
                allResults.stream()
                        .map(r -> r.getPassed() + "/" + r.getTotal())
                        .collect(Collectors.joining(" "))
        );

        logger.println(
                allResults.stream()
                        .map(Results::getDetails)
                        .map(details -> details.chars()
                                .mapToObj(c -> String.valueOf((char) c))
                                .collect(Collectors.joining(";")))
                        .collect(Collectors.joining(";;"))
        );

        logger.println(
                allResults.stream()
                        .map(r -> String.valueOf(r.getPassed()))
                        .collect(Collectors.joining(";"))
        );

        logger.println(
                allResults.stream()
                        .map(r -> String.format(Locale.US, "%.3f", (double) r.getPassed() / r.getTotal()))
                        .collect(Collectors.joining(";"))
        );

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
        List<Results> allResults = runSuites(config, List.of(testSuites));

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
