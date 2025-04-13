package test.unit;

import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Function;

/**
 * Defines the interface for logging test execution progress and results.
 * Implementations determine the destination and format of the output
 * (e.g., console with/without color, silent).
 * <p>
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

    // Compile the pattern once for efficiency, escaping backslashes for Java strings
    Pattern SENTENCE_START_PATTERN =
        Pattern.compile("(?m)((?:^|[.\\n\\r]\\s*)(?:\\u001B\\[[0-9;]*m)*)([a-z])");
    // Group 1: Captures everything before the letter (start anchor OR punctuation/whitespace + ANSI codes)
    // Group 2: Captures the lowercase letter to be capitalized

    /**
     * Applies sentence capitalization to the given text.
     * This method capitalizes the first letter of each sentence
     * while ignoring ANSI color codes and whitespace.
     * <p>
     * It handles:
     * <ul>
     * <li>Beginning of the string (<code>^</code>).</li>
     * <li>After a period (<code>.</code>), newline (<code>\n</code>), or carriage return (<code>\r</code>).</li>
     * <li>Ignoring whitespace (<code>\\s*</code>) and line breaks (<code>\\R*</code>) in between.</li>
     * <li>Ignoring ANSI escape codes (<code>\\u001B\\[[0-9;]*m)*</code>).</li>
     * <li>Capitalizing the first lowercase letter found (<code>[a-z]</code>).</li>
     * </ul>
     * @param text The input text to be processed.
     * @return The text with the first letter of each sentence capitalized.
     */
    static String applySentenceCapitalization(String text) {
        if (text == null || text.isEmpty()) {
            return text; // Return null or empty strings as is
        }

        Matcher matcher = SENTENCE_START_PATTERN.matcher(text);
        // Use Java 9+'s replaceAll with a lambda (Function) to provide the replacement logic
        // The lambda receives a MatchResult object for each match found.
        Function<java.util.regex.MatchResult, String> replacer = matchResult -> {
            // Reconstruct the replacement:
            // Group 1 contains everything before the letter (start/punctuation/ANSI)
            // Group 2 contains the lowercase letter itself
            // We append Group 1 as is, and append the uppercase version of Group 2.
            return matchResult.group(1) + matchResult.group(2).toUpperCase();
        };
        return matcher.replaceAll(replacer);
    }

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
            var rawMessage = result.message(config);
            // Apply sentence capitalization to the message
            var capitalizedMessage = applySentenceCapitalization(rawMessage);
            println(capitalizedMessage);
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
