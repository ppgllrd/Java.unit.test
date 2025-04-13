package test.unit;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Encapsulates the aggregated results of running a {@link TestSuite}.
 * It stores the individual {@link TestResult} outcomes and provides methods
 * to query statistics like passed/failed counts and success rate.
 * <p>
 * Generating a string representation ({@code mkString} or {@code toString}) requires
 * a {@link Config} instance to handle localization and coloring of the summary.
 *
 * @author Pepe Gallardo & Gemini
 */
public final class Results { // Made final as it represents a completed state

    private final List<TestResult> results; // Use immutable List internally
    private final int passed;
    private final int failed;
    private final int total;
    private final double successRate;
    private final String details;

    /**
     * Constructor for Results. It calculates statistics immediately.
     * @param resultsList The list of individual test results. Must not be null. Copied defensively.
     */
    public Results(List<TestResult> resultsList) {
        Objects.requireNonNull(resultsList, "resultsList cannot be null");
        this.results = List.copyOf(resultsList); // Create immutable copy

        this.passed = (int) this.results.stream().filter(TestResult::isSuccess).count();
        this.failed = this.results.size() - this.passed;
        this.total = this.results.size();
        this.successRate = (total == 0) ? 1.0 : (double) passed / total;
        this.details = this.results.stream()
                                   .map(result -> result.isSuccess() ? "+" : "-")
                                   .collect(Collectors.joining());
    }

    /** Returns the total number of tests that passed successfully. */
    public int getPassed() { return passed; }

    /** Returns the total number of tests that failed. */
    public int getFailed() { return failed; }

    /** Returns the total number of tests executed in the suite. */
    public int getTotal() { return total; }

    /**
     * Returns a compact string showing the outcome of each test.
     * Uses '+' for success and '-' for failure. Does not include color.
     * Example: {@code "+-++-"}
     *
     * @return A string summarizing individual test outcomes.
     */
    public String getDetails() { return details; }

    /**
     * Checks if all tests within the suite passed.
     *
     * @return {@code true} if {@code getFailed} is 0, {@code false} otherwise.
     */
    public boolean isSuccessful() { return failed == 0; }

    /**
     * Returns the success rate as a fraction between 0.0 and 1.0.
     * Returns 1.0 if no tests were run.
     *
     * @return The success rate (passed / total).
     */
    public double getSuccessRate() { return successRate; }

    /**
     * Generates a formatted, localized, and potentially colored string summarizing
     * the test suite results (passed, failed, total counts, and details).
     * <p>
     * Requires a {@link Config} to access the logger (for coloring) and
     * localization messages (for labels like "Passed", "Failed").
     *
     * @param config The configuration context. Must not be null.
     * @return The formatted summary string.
     */
    public String mkString(Config config) {
        Objects.requireNonNull(config, "Config cannot be null for mkString");
        var logger = config.logger();

        // Get localized labels from config
        var passedLabel = config.msg("results.passed");
        var failedLabel = config.msg("results.failed");
        var totalLabel = config.msg("results.total");
        var detailLabel = config.msg("results.detail");

        // Format parts with appropriate colors using the logger
        var passedPart = String.format("%s: %s", logger.green(passedLabel), logger.green(Integer.toString(passed)));
        var failedPart = String.format("%s: %s", logger.red(failedLabel), logger.red(Integer.toString(failed)));

        // Color the detail string (+/-) based on individual results
        // The details string is pre-calculated, just need to color it
        StringBuilder coloredDetails = new StringBuilder();
         for (int i = 0; i < this.details.length(); i++) {
             if (this.details.charAt(i) == '+') {
                 coloredDetails.append(logger.green("+"));
             } else {
                 coloredDetails.append(logger.red("-"));
             }
         }

        // Combine all parts into the final summary string
        return String.format("%s, %s, %s: %d, %s: %s",
                             passedPart, failedPart, totalLabel, total, detailLabel, coloredDetails);
    }

    /**
     * Provides a default string representation using {@code mkString} with {@link Config#DEFAULT}.
     * Note: This will use default language (English) and ANSI colors if the default logger supports them.
     * For controlled formatting, use {@code mkString(specificConfig)}.
     *
     * @return The formatted summary string using the default configuration.
     */
    @Override
    public String toString() {
        return mkString(Config.DEFAULT); // Use default config for basic toString
    }

    /**
     * Checks for equality with another object.
     * Two `Results` instances are considered equal if they contain the same sequence
     * of `TestResult` objects in the same order.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Results results1 = (Results) o;
        // Compare the immutable list directly
        return Objects.equals(results, results1.results);
    }

    /**
     * Computes the hash code based on the content of the results list.
     */
    @Override
    public int hashCode() {
        return Objects.hash(results);
    }
}
