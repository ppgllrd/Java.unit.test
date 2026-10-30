package test.unit.core;

import test.unit.assertions.Assert;
import test.unit.assertions.Equal;
import test.unit.config.Config;
import test.unit.config.TestIsolationMode;
import test.unit.exceptions.ExceptionOneOf;
import test.unit.execution.ExecutionOutcome;
import test.unit.execution.TestExecutionRequest;
import test.unit.execution.WorkerPool;
import test.unit.execution.TestLocator;
import test.unit.logging.Logger;
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
public non-sealed abstract class Test implements SuiteItem  {

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
        return runLocated(config, null);
    }

    /**
     * Same as {@link #run(Config)}, but knowing the {@link TestLocator} of this test, which is what allows
     * {@link TestIsolationMode#PER_TEST_JVM} to run it in a child JVM. Called by {@link TestSuite}.
     */
    final TestResult runLocated(Config config, TestLocator locator) {
        Objects.requireNonNull(config, "Config cannot be null for running test");
        logStart(config);
        ExecutionOutcome outcome = executeOutcome(config, locator, null);
        logOutcome(config, outcome);
        return outcome.result();
    }

    /** Logs the start of this test (it is shown before the test runs). */
    final void logStart(Config config) {
        var logger = config.logger();
        logger.logStart(logger.bold(" " + this.name), config);
    }

    /** Runs this test through the configured strategy, with the *resolved* timeout. Does not log anything. */
    final ExecutionOutcome executeOutcome(Config config, TestLocator locator, WorkerPool pool) {
        final int resolvedTimeout = this.timeoutOverride.orElse(config.timeout());
        Config executionConfig = config.withTimeout(resolvedTimeout).withCsvOutput(false);
        return TestExecutionStrategy.forMode(config.isolation().mode())
                .execute(new TestExecutionRequest(this, locator, executionConfig, pool));
    }

    /** Logs the result of this test and whatever its child JVM printed. */
    final void logOutcome(Config config, ExecutionOutcome outcome) {
        var logger = config.logger();
        logger.logResult(outcome.result(), config);
        logger.println(); // Add a blank line after each test result for readability
        logChildOutput(outcome, config);
        logger.flush();
    }

    /** Shows what the child JVM printed (PER_TEST_JVM only; in SAME_JVM the test writes to the console itself). */
    private static void logChildOutput(ExecutionOutcome outcome, Config config) {
        var logger = config.logger();
        if (!outcome.stdout().isEmpty()) {
            logger.println("   [stdout" + (outcome.stdoutTruncated() ? ", truncated" : "") + "]");
            logger.println(outcome.stdout().stripTrailing());
        }
        if (!outcome.stderr().isEmpty()) {
            logger.println("   [stderr" + (outcome.stderrTruncated() ? ", truncated" : "") + "]");
            logger.println(outcome.stderr().stripTrailing());
        }
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
