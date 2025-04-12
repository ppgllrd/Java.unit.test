package test.unit;

import java.io.PrintStream;

/**
 * Defines the interface for logging test execution progress and results.
 * Implementations determine the destination and format of the output
 * (e.g., console with/without color, silent).
 *
 * Logger methods typically require a {@link Config} instance to access
 * localization settings and formatting preferences (like color support).
 *
 * @author Pepe Gallardo & Gemini
 */
public interface Logger {

    /**
     * Indicates whether this logger implementation supports rendering ANSI color codes.
     * Used by {@link Config} and other components to conditionally apply colors.
     *
     * @return {@code true} if ANSI colors are supported and should be used, {@code false} otherwise.
     */
    boolean supportsAnsiColors();

    // --- Convenience methods for applying colors ---

    /** Applies red color to the text if ANSI colors are supported. */
    default String red(String text) {
        return AnsiColor.red(text, supportsAnsiColors());
    }

    /** Applies green color to the text if ANSI colors are supported. */
    default String green(String text) {
        return AnsiColor.green(text, supportsAnsiColors());
    }

    /** Applies blue color to the text if ANSI colors are supported. */
    default String blue(String text) {
        return AnsiColor.blue(text, supportsAnsiColors());
    }

    /** Applies bold style to the text if ANSI colors are supported. */
    default String bold(String text) {
        return AnsiColor.bold(text, supportsAnsiColors());
    }

    /** Applies underline style to the text if ANSI colors are supported. */
    default String underline(String text) {
        return AnsiColor.underline(text, supportsAnsiColors());
    }

    // --- Core Logging Methods (to be implemented by subclasses) ---

    /** Prints the string representation of the given object to the output destination without a trailing newline. */
    void print(Object any);

    /** Prints the string representation of the given object to the output destination followed by a trailing newline. */
    void println(Object any);

    /** Prints a newline character to the output destination. */
    default void println() {
        println(""); // Default implementation
    }

    /**
     * Logs information indicating that a specific test is about to start execution.
     * Typically, includes printing the test name.
     *
     * @param testName The name of the test being started.
     * @param config The configuration context for the current test run (used for potential formatting).
     */
    void logStart(String testName, Config config);

    /**
     * Logs the final result of a completed test execution.
     * Implementations should use the provided `config` to format the `result`'s
     * message appropriately (handling localization and colors).
     *
     * @param result The {@link TestResult} object containing the outcome and details of the test.
     * @param config The configuration context for the current test run.
     */
    void logResult(TestResult result, Config config);

    /**
     * Ensures that any buffered output is written to the underlying destination (e.g., flushing a console stream).
     */
    void flush();

    // --- Concrete Logger Implementations ---

    /**
     * A {@link Logger} implementation that prints output to the standard console
     * (System.out) *without* using ANSI color codes.
     */
    class ConsoleLogger implements Logger {
        private final PrintStream out = System.out;

        @Override
        public boolean supportsAnsiColors() {
            return false;
        }

        @Override
        public void print(Object any) {
            out.print(any);
        }

        @Override
        public void println(Object any) {
            out.println(any);
        }

        @Override
        public void logStart(String testName, Config config) {
            // Simple prefix before result
            print(testName + ": ");
        }

        @Override
        public void logResult(TestResult result, Config config) {
            // The result.message method uses the config for localization and formatting (without color here)
            println(result.message(config));
        }

        @Override
        public void flush() {
            out.flush();
        }
    }

    /**
     * A {@link Logger} implementation that prints output to the standard console
     * (System.out) *with* ANSI color codes enabled.
     * It inherits basic printing logic from {@link ConsoleLogger}.
     */
    class AnsiConsoleLogger extends ConsoleLogger {
        @Override
        public boolean supportsAnsiColors() {
            return true;
            // Color methods (red, green, etc.) will now apply colors.
            // The result.message formatting will include colors when called with a config using this logger.
        }
    }

    /**
     * A {@link Logger} implementation that produces no output. Useful for suppressing
     * test logging entirely.
     */
    class SilentLogger implements Logger {
        @Override
        public boolean supportsAnsiColors() {
            return false;
        }

        @Override
        public void print(Object any) { /* Does nothing */ }

        @Override
        public void println(Object any) { /* Does nothing */ }

        @Override
        public void logStart(String testName, Config config) { /* Does nothing */ }

        @Override
        public void logResult(TestResult result, Config config) { /* Does nothing */ }

        @Override
        public void flush() { /* Does nothing */ }
    }
}
