package test.unit;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents the possible outcomes of executing a {@link Test}.
 * This is a sealed interface with record implementations for specific results
 * like success, various types of failures (equality, property, exception-related, timeout), etc.
 * <p>
 * The {@code message(Config)} method provides a localized and potentially colored description
 * of the outcome, requiring a {@link Config} for formatting.
 *
 * @author Pepe Gallardo & Gemini
 */
public sealed interface TestResult
    permits TestResult.Success, TestResult.Failure, TestResult.PropertyFailure, TestResult.EqualityFailure,
            TestResult.NoExceptionFailure, TestResult.WrongExceptionTypeFailure, TestResult.WrongExceptionMessageFailure,
            TestResult.WrongExceptionAndMessageFailure, TestResult.TimeoutFailure, TestResult.UnexpectedExceptionFailure {

    /** Indicates whether this result represents a successful test execution. */
    boolean isSuccess();

    /**
     * Generates a formatted message describing the test outcome.
     * Uses the {@code config} for localization (via {@code config.msg}) and
     * coloring (via {@code config.logger}).
     *
     * @param config The configuration context.
     * @return A descriptive string message for this test result.
     */
    String message(Config config);

    // --- Concrete Result Implementations ---

    /** Represents a successful test execution. */
    record Success() implements TestResult {
        @Override
        public boolean isSuccess() { return true; }

        @Override
        public String message(Config config) {
            var logger = config.logger();
            // Indented, bold, green "PASSED" message from I18n
            return "\n   " + logger.bold(logger.green(config.msg("passed")));
        }
    }

    /** Base sealed interface for all failure results. */
    sealed interface Failure extends TestResult
        permits PropertyFailure, EqualityFailure, NoExceptionFailure, WrongExceptionTypeFailure,
                WrongExceptionMessageFailure, WrongExceptionAndMessageFailure, TimeoutFailure, UnexpectedExceptionFailure {

        @Override
        default boolean isSuccess() { return false; }

        /** Helper to get the standard "FAILED!" marker, localized and colored red/bold. */
        default String failedMarker(Config config) {
            return config.logger().bold(config.logger().red(config.msg("failed")));
        }
    }

    /** Failure because a property predicate returned `false`. */
    record PropertyFailure<T>(
        java.util.function.Function<T, String> mkString, // Function to format the result T to String
        String propertyDescription // Pre-formatted description of the expected property
    ) implements Failure, TestResult {
        public PropertyFailure {
            // result can be null
            Objects.requireNonNull(mkString, "mkString function cannot be null");
            Objects.requireNonNull(propertyDescription, "propertyDescription cannot be null");
        }
        @Override
        public String message(Config config) {
            var logger = config.logger();
            // Format the obtained result (colored red) using the provided mkString function
            return "\n   " + failedMarker(config) +
                   "\n   " + propertyDescription;
        }
    }

    /** Failure because the actual result was not equal to the expected value (using `==` or custom `equalsFn`). */
    record EqualityFailure<T>(
        T expected, // The expected value
        T actual, // The actual result obtained
        java.util.function.Function<T, String> mkString // Function to format T values to String
    ) implements Failure, TestResult {
        public EqualityFailure {
            // expected/actual can be null
             Objects.requireNonNull(mkString, "mkString function cannot be null");
        }
        @Override
        public String message(Config config) {
            var logger = config.logger();
            // Format expected (green) and actual (red) values
            var expectedMsg = config.msg("expected.result", logger.green(mkString.apply(expected)));
            var obtainedMsg = config.msg("obtained.result", logger.red(mkString.apply(actual)));
            return "\n   " + failedMarker(config) +
                   "\n   " + expectedMsg +
                   "\n   " + obtainedMsg;
        }
    }

    /** Failure because an exception was expected, but none was thrown. */
    record NoExceptionFailure<T>(
        T result, // The value returned instead of an exception
        java.util.function.Function<T, String> mkString, // Function to format the result T to String
        String expectedExceptionDescription // Pre-formatted description of the expected exception scenario
    ) implements Failure, TestResult {
        public NoExceptionFailure {
             // result can be null
             Objects.requireNonNull(mkString, "mkString function cannot be null");
             Objects.requireNonNull(expectedExceptionDescription, "expectedExceptionDescription cannot be null");
        }
        @Override
        public String message(Config config) {
            var logger = config.logger();
            // Format the obtained result (red)
            var obtainedMsg = config.msg("obtained.result", logger.red(mkString.apply(result)));
            // Get the base "no exception" message, including the expected description
            var baseMsg = config.msg("no.exception.basic", expectedExceptionDescription);
            return "\n   " + failedMarker(config) +
                   "\n   " + baseMsg +
                   "\n   " + obtainedMsg;
        }
    }

    /** Failure because an exception was thrown, but it was of the wrong type. */
    record WrongExceptionTypeFailure(
        Throwable thrown, // The actual exception that was thrown
        String expectedExceptionDescription // Pre-formatted description of the expected exception scenario
    ) implements Failure, TestResult {
         public WrongExceptionTypeFailure {
             Objects.requireNonNull(thrown, "thrown throwable cannot be null");
             Objects.requireNonNull(expectedExceptionDescription, "expectedExceptionDescription cannot be null");
         }
        @Override
        public String message(Config config) {
            var logger = config.logger();
            // Get actual exception type name (red)
            var thrownName = logger.red(thrown.getClass().getSimpleName());
            // Basic message indicating wrong type thrown
            var wrongTypeMsg = config.msg("wrong.exception.type.basic", thrownName);
            // Message indicating what was expected instead
            var butExpectedMsg = config.msg("but.expected", expectedExceptionDescription);
            return "\n   " + failedMarker(config) +
                   "\n   " + wrongTypeMsg +
                   "\n   " + butExpectedMsg;
        }
    }

    /** Failure because the correct type of exception was thrown, but its message did not match expectations. */
    record WrongExceptionMessageFailure(
        Throwable thrown, // The actual exception (correct type, wrong message)
        String expectedExceptionDescription, // Pre-formatted overall description of the expected scenario (includes type)
        Optional<String> detailedExpectation // Pre-formatted detail about *why* the message failed
    ) implements Failure, TestResult {
         public WrongExceptionMessageFailure {
             Objects.requireNonNull(thrown, "thrown throwable cannot be null");
             Objects.requireNonNull(expectedExceptionDescription, "expectedExceptionDescription cannot be null");
             Objects.requireNonNull(detailedExpectation, "detailedExpectation optional cannot be null");
         }
        @Override
        public String message(Config config) {
            var logger = config.logger();
            // Get thrown type name (green, because type was correct)
            var thrownName = logger.green(thrown.getClass().getSimpleName());
            // Get actual message, handle null, format as red quoted string
            var thrownMsg = String.valueOf(thrown.getMessage());
            var actualMsgStr = logger.red("\"" + thrownMsg + "\"");
            // Basic message indicating correct type but wrong message
            var wrongMsgBasic = config.msg("wrong.exception.message.basic", thrownName, actualMsgStr);
            // Get the specific reason for message failure, or a fallback if not provided
            var detailPart = detailedExpectation.orElse("(Reason for message failure not specified)");
            return "\n   " + failedMarker(config) +
                   "\n   " + wrongMsgBasic +
                   "\n   " + detailPart; // Appends the detailed reason
        }
    }

    /** Failure because both the type and the message of the thrown exception were incorrect. */
    record WrongExceptionAndMessageFailure(
        Throwable thrown, // The actual exception (wrong type and message)
        String expectedExceptionDescription // Pre-formatted description of the expected scenario
    ) implements Failure, TestResult {
         public WrongExceptionAndMessageFailure {
             Objects.requireNonNull(thrown, "thrown throwable cannot be null");
             Objects.requireNonNull(expectedExceptionDescription, "expectedExceptionDescription cannot be null");
         }
        @Override
        public String message(Config config) {
            var logger = config.logger();
            // Get actual type name (red)
            var thrownName = logger.red(thrown.getClass().getSimpleName());
            // Get actual message, handle null, format as red quoted string
            var thrownMsg = String.valueOf(thrown.getMessage());
            var actualMsgStr = logger.red("\"" + thrownMsg + "\"");
            // Basic message indicating wrong type and message thrown
            var wrongAllMsg = config.msg("wrong.exception.and.message.basic", thrownName, actualMsgStr);
            // Message indicating what was expected instead
            var butExpectedMsg = config.msg("but.expected", expectedExceptionDescription);
            return "\n   " + failedMarker(config) +
                   "\n   " + wrongAllMsg +
                   "\n   " + butExpectedMsg;
        }
    }

    /** Failure because the test execution exceeded the allowed time limit. */
    record TimeoutFailure(
        int timeout, // The timeout duration in seconds that was exceeded
        String expectedBehaviorDescription // Pre-formatted description of what the test was expected to do
    ) implements Failure, TestResult {
         public TimeoutFailure {
             if (timeout <= 0) throw new IllegalArgumentException("timeout must be positive");
             Objects.requireNonNull(expectedBehaviorDescription, "expectedBehaviorDescription cannot be null");
         }
        /**
         * Formats a timeout failure message.
         * Uses the I18n key "timeout".
         */
        @Override
        public String message(Config config) {
            // Use the specific I18n key "timeout", passing the expected behavior and duration
            var timeoutMsg = config.msg("timeout", expectedBehaviorDescription, timeout);
            return "\n   " + failedMarker(config) +
                   "\n   " + timeoutMsg; // Combine marker and formatted timeout message
        }
    }

    /** Failure due to an unexpected exception occurring during test execution. */
    record UnexpectedExceptionFailure(
        Throwable thrown, // The unexpected exception that was caught
        String originalExpectationDescription // Pre-formatted description of what the test was originally trying to achieve
    ) implements Failure, TestResult {
        public UnexpectedExceptionFailure {
            Objects.requireNonNull(thrown, "thrown throwable cannot be null");
            Objects.requireNonNull(originalExpectationDescription, "originalExpectationDescription cannot be null");
        }
        @Override
        public String message(Config config) {
            var logger = config.logger();
            // Get unexpected exception type name (red)
            var thrownName = logger.red(thrown.getClass().getSimpleName());
            // Get unexpected exception message, handle null, format as red quoted string
            var thrownMsg = String.valueOf(thrown.getMessage());
            var actualMsgStr = logger.red("\"" + thrownMsg + "\"");
            // Construct the message using the "unexpected.exception" key
            var unexpectedMsg = config.msg("unexpected.exception", originalExpectationDescription, thrownName, actualMsgStr);
            return "\n   " + failedMarker(config) +
                   "\n   " + unexpectedMsg;
        }
    }
}
