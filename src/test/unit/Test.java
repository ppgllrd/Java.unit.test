package test.unit;

import java.util.Objects;
import java.util.Optional;

/**
 * Abstract base class representing a single, executable test case.
 * Each concrete test class (e.g., {@link Equal}, {@link Assert}, {@link ExceptionOneOf}) extends this class.
 * <p>
 * It defines the common structure: a name, an optional timeout override, and the {@code run} method
 * which handles the execution lifecycle (logging start/end, timeout management) by calling
 * the abstract {@code executeTest} method implemented by subclasses.
 *
 * @author Pepe Gallardo & Gemini
 */
public abstract class Test {

    protected final String name;
    protected final Optional<Integer> timeoutOverride;

    /**
     * Base constructor for a Test.
     * @param name The descriptive name identifying this specific test case. Must not be null.
     * @param timeoutOverride An optional timeout duration (in seconds) specific to this test. Must not be null, can be empty. Negative values in Optional are invalid.
     */
    protected Test(String name, Optional<Integer> timeoutOverride) {
        this.name = Objects.requireNonNull(name, "Test name cannot be null");
        Objects.requireNonNull(timeoutOverride, "timeoutOverride Optional cannot be null");
        timeoutOverride.ifPresent(timeout -> {
            if (timeout <= 0) {
                throw new IllegalArgumentException("Timeout override must be positive if present");
            }
        });
        this.timeoutOverride = timeoutOverride;
    }

    /** Gets the name of this test case. */
    public String getName() {
        return name;
    }

    /** Gets the optional timeout override for this test. */
    public Optional<Integer> getTimeoutOverride() {
        return timeoutOverride;
    }

    /**
     * Executes this test case using the provided configuration.
     * This method orchestrates the test execution:
     * 1. Logs the start of the test using the configured logger.
     * 2. Determines the actual timeout value (using override or config default).
     * 3. Calls the specific {@code executeTest} method (implemented by subclasses)
     *    within the context of the resolved timeout and configuration.
     * 4. Logs the final {@link TestResult}.
     * 5. Flushes the logger.
     *
     * @param config The {@link Config} object providing the logger, language, default timeout, etc., for this run. Must not be null.
     * @return The {@link TestResult} indicating the outcome (Success or a specific Failure type).
     */
    public final TestResult run(Config config) {
        Objects.requireNonNull(config, "Config cannot be null for running test");
        var logger = config.logger();

        // 1. Log Start: Use bold style for the test name prefix
        logger.logStart(logger.bold(" " + this.name), config); // Pass config

        // 2. Resolve Timeout: Use override if present, otherwise use config default
        final int resolvedTimeout = this.timeoutOverride.orElse(config.timeout());

        // 3. Execute Core Logic: Call the abstract method, passing a config
        //    with the *resolved* timeout.
        Config executionConfig = new Config(config.logger(), config.language(), resolvedTimeout, config.csvOutput());
        TestResult result = executeTest(executionConfig);

        // 4. Log Result: Use the logger to print the formatted result message
        logger.logResult(result, config); // Pass original config for context
        logger.println(); // Add a blank line after each test result for readability

        // 5. Flush Logger: Ensure output is visible immediately
        logger.flush();

        // Return the outcome
        return result;
    }

    /**
     * Abstract method containing the core logic specific to this type of test.
     * Subclasses must implement this method to perform the actual test evaluation
     * (e.g., evaluate an expression, compare values, check for exceptions) and
     * return the appropriate {@link TestResult}.
     * <p>
     * This method is called by the {@code run} method and receives the final configuration context,
     * including the *resolved* timeout value for this specific test execution.
     *
     * @param config The configuration context for this test run, including the actual timeout to use.
     * @return A {@link TestResult} (e.g., {@link TestResult.Success}, {@link TestResult.EqualityFailure}) based on the evaluation.
     */
    protected abstract TestResult executeTest(Config config);
}
