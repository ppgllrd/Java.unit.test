package test.unit.config;

import test.unit.core.Test;
import test.unit.core.TestSuite;
import test.unit.i18n.I18n;
import test.unit.i18n.Language;
import test.unit.logging.Logger;
import java.util.Locale;
import java.util.MissingFormatArgumentException;
import java.util.Objects;

/**
 * Holds configuration settings for running tests, including the logger,
 * language for messages, and the default timeout duration.
 * An instance of `Config` is typically passed to the `run` methods of {@link Test} and {@link TestSuite}.
 * This is implemented as a Java Record for immutability and conciseness.
 *
 * @param logger The {@link Logger} implementation to use for outputting test progress and results.
 * @param language The {@link Language} used for localizing messages from the {@link I18n} resource bundle.
 * @param timeout The default timeout duration in seconds for individual tests, used when a test doesn't specify its own `timeoutOverride`.
 * @param csvOutput Whether to output results in CSV format.
 * @param isolation How tests are isolated ({@link IsolationOptions}); defaults to {@link TestIsolationMode#SAME_JVM}.
 * @param parallelism Maximum number of tests of a run executing at the same time (1 = sequential, the default).
 *                    Independent of the isolation mode. Output is always reported in suite order.
 * @author Pepe Gallardo & Gemini
 */
public record Config(
        Logger logger,
        Language language,
        int timeout,
        boolean csvOutput,
        IsolationOptions isolation,
        int parallelism
) {
    // Default constructor is provided by the record

    /**
     * Canonical constructor with validations. This is invoked automatically.
     */
    public Config {
        Objects.requireNonNull(logger, "logger cannot be null");
        Objects.requireNonNull(language, "language cannot be null");
        if (timeout <= 0) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        Objects.requireNonNull(isolation, "isolation cannot be null");
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be at least 1");
        }
    }

    /**
     * Constructor with default parallelism (1, sequential).
     */
    public Config(Logger logger, Language language, int timeout, boolean csvOutput, IsolationOptions isolation) {
        this(logger, language, timeout, csvOutput, isolation, 1);
    }

    /**
     * Backwards-compatible constructor: same as the canonical one with default isolation (SAME_JVM).
     */
    public Config(Logger logger, Language language, int timeout, boolean csvOutput) {
        this(logger, language, timeout, csvOutput, IsolationOptions.DEFAULT, 1);
    }

    /**
     * No-argument constructor that provides default values.
     */
    public Config() {
        this(new Logger.AnsiConsoleLogger(), Language.ENGLISH, 3, false, IsolationOptions.DEFAULT, 1);
    }


    /**
     * Retrieves a localized message pattern for the given key and language,
     * then formats it using the provided arguments.
     * <p>
     * It uses {@code java.lang.String.format} with the {@code ROOT} locale for consistent
     * formatting behavior regardless of the system's default locale.
     *
     * @param key The key identifying the message pattern in the {@link I18n} resource bundle (e.g., "test.passed").
     * @param args The arguments to be substituted into the message pattern placeholders (e.g., %s, %d).
     * @return The formatted, localized message string.
     *         If a formatting error occurs (e.g., missing arguments), an error message string is returned.
     */
    public String msg(String key, Object... args) {
        String pattern = I18n.getMessage(key, this.language);
        if (args == null || args.length == 0) {
            return pattern;
        }
        try {
            // Use Locale.ROOT for consistent formatting regardless of the system's locale
            return String.format(Locale.ROOT, pattern, args);
        } catch (MissingFormatArgumentException e) {
            return String.format("ERROR: Formatting error for key '%s' [%s]: %s. Pattern: '%s', Args: %s",
                    key, this.language.toString().toLowerCase(), e.getMessage(), pattern, java.util.Arrays.toString(args));
        } catch (Exception e) { // Catch other potential formatting exceptions
            return String.format("ERROR: Generic formatting error for key '%s' [%s]: %s. Pattern: '%s', Args: %s",
                    key, this.language.toString().toLowerCase(), e.getMessage(), pattern, java.util.Arrays.toString(args));
        }
    }

    // --- Static Factories and Defaults ---

    /** A default configuration using {@code AnsiConsoleLogger}, {@code ENGLISH} language, and a 3-second timeout. */
    public static final Config DEFAULT = new Config();

    /**
     * Returns a copy of this configuration with a modified logger.
     *
     * @param logging If {@code false}, the logger is set to {@link Logger.SilentLogger}, disabling all output.
     *                If {@code true}, a console logger is used.
     * @param useAnsi If {@code logging} is {@code true}, this determines whether to use {@link Logger.AnsiConsoleLogger} (if {@code true})
     *                or {@link Logger.ConsoleLogger} (if {@code false}).
     */
    public Config withLogging(boolean logging, boolean useAnsi) {
        Logger newLogger = !logging ? new Logger.SilentLogger() :
                useAnsi ? new Logger.AnsiConsoleLogger() : new Logger.ConsoleLogger();
        return new Config(newLogger, language, timeout, csvOutput, isolation, parallelism);
    }

    /** Returns a copy of this configuration with logging enabled or disabled, using ANSI output when enabled. */
    public Config withLogging(boolean logging) {
        return withLogging(logging, true);
    }

    /**
     * Returns a copy of this configuration with a modified CSV output setting.
     *
     * @param csvOutput {@code true} to enable CSV output, {@code false} to disable it.
     */
    public Config withCsvOutput(boolean csvOutput) {
        return new Config(logger, language, timeout, csvOutput, isolation, parallelism);
    }

    /**
     * Returns a copy of this configuration with a modified language.
     *
     * @param language The new {@link Language} to use.
     */
    public Config withLanguage(Language language) {
        return new Config(logger, language, timeout, csvOutput, isolation, parallelism);
    }

    /**
     * Returns a copy of this configuration with a modified timeout.
     *
     * @param timeout The new timeout in seconds (must be positive).
     */
    public Config withTimeout(int timeout) {
        return new Config(logger, language, timeout, csvOutput, isolation, parallelism);
    }

    /**
     * Returns a copy of this configuration with different isolation options.
     *
     * @param isolation The new {@link IsolationOptions}.
     */
    public Config withIsolation(IsolationOptions isolation) {
        return new Config(logger, language, timeout, csvOutput, isolation, parallelism);
    }

    /**
     * Returns a copy of this configuration that runs up to {@code parallelism} tests at the same time.
     * Works with every {@link TestIsolationMode}; in SAME_JVM the concurrent tests share the JVM (and its static state).
     *
     * @param parallelism Maximum number of concurrent tests (at least 1).
     */
    public Config withParallelism(int parallelism) {
        return new Config(logger, language, timeout, csvOutput, isolation, parallelism);
    }
}
