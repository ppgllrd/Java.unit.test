package test.unit;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;


/**
 * Represents a test that verifies if the result of an evaluated expression
 * satisfies a given predicate function (the "property").
 * <p>
 * It supports providing a custom description of the property being tested,
 * either directly as a string ({@code help}) or via a localization key ({@code helpKey}).
 * Similarly, formatting the evaluated result (on failure) can be customized
 * using {@code mkString} or {@code mkStringKey}.
 * <p>
 * This class handles the asynchronous execution, timeout, and exception handling.
 *
 * @param <T> The type of the value produced by {@code toEvaluate}.
 * @author Pepe Gallardo & Gemini
 */
public class Property<T> extends Test {

    protected final Supplier<T> toEvaluate;
    protected final Predicate<T> property;
    protected final Optional<Function<T, String>> mkStringOpt;
    protected final Optional<Function<T, String>> mkStringKeyOpt; // Function returns the key string
    protected final Optional<String> helpOpt;
    protected final Optional<String> helpKeyOpt;

    /**
     * Protected constructor for Property tests. Use static factory methods.
     */
    protected Property(String name,
                       Supplier<T> toEvaluate,
                       Predicate<T> property,
                       Optional<Function<T, String>> mkStringOpt,
                       Optional<Function<T, String>> mkStringKeyOpt,
                       Optional<String> helpOpt,
                       Optional<String> helpKeyOpt,
                       Optional<Integer> timeoutOverride) {
        super(name, timeoutOverride);
        this.toEvaluate = Objects.requireNonNull(toEvaluate, "Supplier 'toEvaluate' cannot be null");
        this.property = Objects.requireNonNull(property, "Predicate 'property' cannot be null");
        this.mkStringOpt = Objects.requireNonNull(mkStringOpt, "Optional 'mkStringOpt' cannot be null");
        this.mkStringKeyOpt = Objects.requireNonNull(mkStringKeyOpt, "Optional 'mkStringKeyOpt' cannot be null");
        this.helpOpt = Objects.requireNonNull(helpOpt, "Optional 'helpOpt' cannot be null");
        this.helpKeyOpt = Objects.requireNonNull(helpKeyOpt, "Optional 'helpKeyOpt' cannot be null");
    }


    /**
     * Generates the description of the property being tested, used in failure messages.
     * It prioritizes the direct {@code help} string if provided, otherwise uses the {@code helpKey}
     * to look up a localized string. The description is formatted and potentially colored.
     *
     * @param config The configuration context providing localization and logger.
     * @return A formatted string describing the property expectation.
     */
    private String generatePropertyDescription(Config config) {
        var logger = config.logger();
        // Base failure message (e.g., "Does not verify expected property")
        var baseMessage = config.msg("property.failure.base");

        // Determine the help detail text, preferring direct 'help' over 'helpKey'
        Optional<String> helpDetailColoredOpt = helpOpt // Use direct help string if available
                .or(() -> helpKeyOpt.map(config::msg)) // Otherwise, use helpKey to get localized string
                .map(logger::green); // Apply green color to the detail text

        // Combine base message and the optional, colored help detail
        return helpDetailColoredOpt
                .map(detail -> baseMessage + config.msg("property.failure.suffix", detail)) // Append suffix + colored detail
                .orElse(baseMessage); // Just the base message
    }

    /**
     * Formats the evaluated result {@code T} into a string for reporting in failure messages.
     * It prioritizes {@code mkStringOpt} if provided, then {@code mkStringKeyOpt}, falling back to {@code Objects.toString(result)}.
     *
     * @param result The value of type {@code T} that was evaluated.
     * @param config The configuration context providing localization.
     * @return The formatted string representation of the result.
     */
    private String formatResult(T result, Config config) {
        return mkStringOpt
                .map(func -> func.apply(result)) // 1. Try direct mkString function
                .or(() -> mkStringKeyOpt.map(keyFunc -> config.msg(keyFunc.apply(result)))) // 2. Try mkStringKey function to get I18n key
                .orElse(Objects.toString(result, "null")); // 3. Fallback to standard toString (null-safe)
    }


    /**
     * Executes the core logic of the `Property` test asynchronously.
     */
    @Override
    protected TestResult executeTest(Config config) {
        final String currentPropertyDesc = generatePropertyDescription(config); // Generate once before async

        CompletableFuture<TestResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                T result = toEvaluate.get(); // Evaluate the expression
                if (property.test(result)) {
                    // Property holds true
                    return new TestResult.Success();
                } else {
                    // Property failed
                    // Need a final variable for lambda capture if using direct lambda
                    final T finalResult = result;
                    Function<T, String> formatter = r -> formatResult(r, config); // Use the helper method
                    return new TestResult.PropertyFailure<>(finalResult, formatter, currentPropertyDesc);
                }
            } catch (Throwable t) {
                 // Handle potential exceptions during evaluation
                 if (t instanceof InterruptedException) {
                     Thread.currentThread().interrupt();
                 }
                 return new TestResult.UnexpectedExceptionFailure(t, currentPropertyDesc);
            }
        });

         try {
            // Wait for the future to complete, respecting the timeout
            return future.get(config.timeout(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return new TestResult.TimeoutFailure(config.timeout(), currentPropertyDesc);
        } catch (ExecutionException e) {
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
             if (cause instanceof InterruptedException) {
                 Thread.currentThread().interrupt();
             }
            return new TestResult.UnexpectedExceptionFailure(cause, currentPropertyDesc);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
             return new TestResult.UnexpectedExceptionFailure(e, currentPropertyDesc);
        } catch (CancellationException e) {
             return new TestResult.TimeoutFailure(config.timeout(), currentPropertyDesc);
        }
    }

    // --- Static Factory Methods ---

    private static <T> Function<T, String> defaultMkString() {
         return obj -> Objects.toString(obj, "null");
    }

    /**
     * Internal factory method used by Assert/Refute which provide key-based formatting/help.
     *
     * @return A `Property<T>` test instance configured with key-based formatting/help.
     */
    static <T> Property<T> fromKeyBased(String name,
                                        Supplier<T> toEvaluate,
                                        Predicate<T> property,
                                        Function<T, String> mkStringKey, // function returns the I18N key
                                        String helpKey,
                                        Optional<Integer> timeoutOverride) {
        return new Property<>(
                name,
                toEvaluate,
                property,
                Optional.empty(), // No direct mkString
                Optional.of(mkStringKey), // Use key-based function
                Optional.empty(), // No direct help text
                Optional.of(helpKey), // Use key-based help
                timeoutOverride
        );
    }

    /**
     * Creates a `Property` test using a custom predicate. Allows specifying an optional
     * custom `mkString` function, an optional `help` string, and an optional timeout override.
     *
     * @return A `Property<T>` test instance.
     */
    public static <T> Property<T> create(String name,
                                         Supplier<T> toEvaluate,
                                         Predicate<T> property,
                                         Optional<Function<T, String>> mkStringOpt,
                                         Optional<String> helpOpt,
                                         Optional<Integer> timeoutOverride) {
         // Use default toString only if NO mkStringOpt AND NO key options are provided (which is not possible via this public factory)
         // Assert/Refute use the key-based factory, so this default logic is simpler here.
         Optional<Function<T, String>> finalMkString = mkStringOpt.isPresent() ? mkStringOpt : Optional.of(defaultMkString());

         return new Property<>(name, toEvaluate, property, finalMkString, Optional.empty(), helpOpt, Optional.empty(), timeoutOverride);
    }

    // Convenience Overloads

    public static <T> Property<T> create(String name,
                                         Supplier<T> toEvaluate,
                                         Predicate<T> property) {
        return create(name, toEvaluate, property, Optional.empty(), Optional.empty(), Optional.empty());
    }

     public static <T> Property<T> create(String name,
                                          Supplier<T> toEvaluate,
                                          Predicate<T> property,
                                          Function<T, String> mkString) {
         return create(name, toEvaluate, property, Optional.of(mkString), Optional.empty(), Optional.empty());
     }

     public static <T> Property<T> create(String name,
                                          Supplier<T> toEvaluate,
                                          Predicate<T> property,
                                          String help) {
         return create(name, toEvaluate, property, Optional.empty(), Optional.of(help), Optional.empty());
     }

     public static <T> Property<T> create(String name,
                                          Supplier<T> toEvaluate,
                                          Predicate<T> property,
                                          Function<T, String> mkString,
                                          String help) {
         return create(name, toEvaluate, property, Optional.of(mkString), Optional.of(help), Optional.empty());
     }

     public static <T> Property<T> create(String name,
                                          Supplier<T> toEvaluate,
                                          Predicate<T> property,
                                          int timeoutOverride) {
         return create(name, toEvaluate, property, Optional.empty(), Optional.empty(), Optional.of(timeoutOverride));
     }

     public static <T> Property<T> create(String name,
                                          Supplier<T> toEvaluate,
                                          Predicate<T> property,
                                          Function<T, String> mkString,
                                          int timeoutOverride) {
          return create(name, toEvaluate, property, Optional.of(mkString), Optional.empty(), Optional.of(timeoutOverride));
     }

     public static <T> Property<T> create(String name,
                                          Supplier<T> toEvaluate,
                                          Predicate<T> property,
                                          String help,
                                          int timeoutOverride) {
          return create(name, toEvaluate, property, Optional.empty(), Optional.of(help), Optional.of(timeoutOverride));
     }

     public static <T> Property<T> create(String name,
                                          Supplier<T> toEvaluate,
                                          Predicate<T> property,
                                          Function<T, String> mkString,
                                          String help,
                                          int timeoutOverride) {
          return create(name, toEvaluate, property, Optional.of(mkString), Optional.of(help), Optional.of(timeoutOverride));
     }
}
