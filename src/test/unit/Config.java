package test.unit;

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
 * @author Pepe Gallardo & Gemini
 */
public record Config(
    Logger logger,
    Language language,
    int timeout
) {
    // Default constructor provided by record

    /** Canonical constructor with defaults if not specified */
     public Config {
         Objects.requireNonNull(logger, "logger cannot be null");
         Objects.requireNonNull(language, "language cannot be null");
         if (timeout <= 0) {
             throw new IllegalArgumentException("timeout must be positive");
         }
     }

     // Constructor providing default values
     public Config() {
         this(new Logger.AnsiConsoleLogger(), Language.ENGLISH, 3);
     }


    /**
     * Retrieves a localized message pattern for the given key and language,
     * then formats it using the provided arguments.
     *
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
            // Use ROOT locale for consistent formatting regardless of system locale
            // Note: String.format requires Object[], not primitive arrays implicitly
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
     * Creates a new `Config` instance based on an existing one, but with potentially modified logging settings.
     *
     * @param logging If {@code false}, the logger is set to {@link Logger.SilentLogger}, disabling all output.
     *                If {@code true}, a console logger is used.
     * @param useAnsi If {@code logging} is {@code true}, this determines whether to use {@link Logger.AnsiConsoleLogger} (if {@code true})
     *                or {@link Logger.ConsoleLogger} (if {@code false}).
     * @param baseConfig The `Config` instance to use as a base. Defaults to {@link Config#DEFAULT}.
     * @return A new `Config` instance with the specified logger, inheriting other settings from `baseConfig`.
     */
    public static Config withLogging(boolean logging, boolean useAnsi, Config baseConfig) {
        Logger newLogger;
        if (!logging) {
            newLogger = new Logger.SilentLogger();
        } else {
            newLogger = useAnsi ? new Logger.AnsiConsoleLogger() : new Logger.ConsoleLogger();
        }
        // Create new record instance using with-pattern style (records are immutable)
        return new Config(newLogger, baseConfig.language(), baseConfig.timeout());
    }

     /** Overload for withLogging using default baseConfig */
     public static Config withLogging(boolean logging, boolean useAnsi) {
         return withLogging(logging, useAnsi, DEFAULT);
     }

     /** Overload for withLogging using default useAnsi=true */
     public static Config withLogging(boolean logging) {
         return withLogging(logging, true, DEFAULT);
     }

     /** Overload for withLogging using default useAnsi=true and default base config */
     public static Config withLogging(boolean logging, Config baseConfig) {
         return withLogging(logging, true, baseConfig);
     }
}
