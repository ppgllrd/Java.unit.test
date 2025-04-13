package test.unit;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Represents a test that verifies if the result of an expression is considered equal
 * to an expected value, using a custom equality function ({@code equalsFn}).
 * <p>
 * This class serves as a base for equality tests and handles the asynchronous
 * execution, timeout, and exception handling logic common to tests.
 *
 * @param <T> The type of the value produced by the expression and the expected value.
 * @author Pepe Gallardo & Gemini
 */
public class EqualBy<T> extends Test {

    protected final Supplier<T> toEvaluate;
    protected final T expected;
    protected final BiPredicate<T, T> equalsFn;
    protected final Function<T, String> mkString;

    /**
     * Protected constructor for EqualBy test. Use static factory methods for creation.
     */
    protected EqualBy(String name,
                      Supplier<T> toEvaluate,
                      T expected,
                      BiPredicate<T, T> equalsFn,
                      Function<T, String> mkString,
                      Optional<Integer> timeoutOverride) {
        super(name, timeoutOverride);
        this.toEvaluate = Objects.requireNonNull(toEvaluate, "Supplier 'toEvaluate' cannot be null");
        this.expected = expected;// this.expected can be null
        this.equalsFn = Objects.requireNonNull(equalsFn, "BiPredicate 'equalsFn' cannot be null");
        this.mkString = Objects.requireNonNull(mkString, "Function 'mkString' cannot be null");
    }

    /**
     * Generates a localized description string indicating the expected value.
     * The expected value itself is colored using the logger's green color.
     *
     * @param config The configuration context providing localization and logger.
     * @return A formatted string like "Expected result was: [green]<expected_value>[reset]".
     */
    private String expectedDescription(Config config) {
        return config.msg("expected.result", config.logger().green(mkString.apply(expected)));
    }

    /**
     * Executes the core logic of the `EqualBy` test asynchronously.
     */
    @Override
    protected TestResult executeTest(Config config) {
        final String currentExpectedDesc = expectedDescription(config); // Generate once before async

        CompletableFuture<TestResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                T result = toEvaluate.get(); // Evaluate the expression
                if (equalsFn.test(result, expected)) {
                    return new TestResult.Success(); // Pass if the custom equality holds
                } else {
                    // Fail, providing expected, actual, and the string formatter
                    return new TestResult.EqualityFailure<>(expected, result, mkString);
                }
            } catch (Throwable t) {
                // Handle potential exceptions during evaluation (including InterruptedException)
                if (t instanceof InterruptedException) {
                    Thread.currentThread().interrupt(); // Preserve interrupt status
                }
                // Treat any exception during evaluation as unexpected for this test type
                return new TestResult.UnexpectedExceptionFailure(t, currentExpectedDesc);
            }
        });

        try {
            // Wait for the future to complete, respecting the timeout
            return future.get(config.timeout(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true); // Attempt to cancel the task
            return new TestResult.TimeoutFailure(config.timeout(), currentExpectedDesc);
        } catch (ExecutionException e) {
            // Exception occurred inside the future's execution, but wasn't caught by inner try-catch (should be rare)
            // It typically wraps the exception thrown inside the Supplier
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
            if (cause instanceof InterruptedException) {
                 Thread.currentThread().interrupt();
            }
            return new TestResult.UnexpectedExceptionFailure(cause, currentExpectedDesc);
        } catch (InterruptedException e) {
            // The waiting thread was interrupted
            Thread.currentThread().interrupt();
            future.cancel(true);
            return new TestResult.UnexpectedExceptionFailure(e, currentExpectedDesc);
        } catch (CancellationException e) {
            // The future was cancelled, likely due to timeout handling
            return new TestResult.TimeoutFailure(config.timeout(), currentExpectedDesc); // Treat as timeout
        }
    }

    // --- Static Factory Methods ---

    private static <T> Function<T, String> defaultMkString() {
        return obj -> Objects.toString(obj, "null"); // Default toString, handles null
    }

    /**
     * Creates an `EqualBy` test instance with a custom equality function.
     *
     * @param name The descriptive name of the test case.
     * @param toEvaluate The supplier whose result will be evaluated.
     * @param expected The value the result is expected to equal (via `equalsFn`).
     * @param equalsFn The custom equality function {@code (T, T) => Boolean}.
     * @param mkString Function to convert `T` to String for reporting. Defaults to {@code Objects.toString}.
     * @param timeoutOverride Optional specific timeout in seconds.
     * @param <T> The type of the expression and result.
     * @return An `EqualBy<T>` test instance.
     */
    public static <T> EqualBy<T> create(String name,
                                        Supplier<T> toEvaluate,
                                        T expected,
                                        BiPredicate<T, T> equalsFn,
                                        Function<T, String> mkString,
                                        Optional<Integer> timeoutOverride) {
        return new EqualBy<>(name, toEvaluate, expected, equalsFn, mkString, timeoutOverride);
    }

    // Overloads for convenience

    public static <T> EqualBy<T> create(String name,
                                        Supplier<T> toEvaluate,
                                        T expected,
                                        BiPredicate<T, T> equalsFn) {
        return create(name, toEvaluate, expected, equalsFn, defaultMkString(), Optional.empty());
    }

     public static <T> EqualBy<T> create(String name,
                                         Supplier<T> toEvaluate,
                                         T expected,
                                         BiPredicate<T, T> equalsFn,
                                         Function<T, String> mkString) {
         return create(name, toEvaluate, expected, equalsFn, mkString, Optional.empty());
     }

    public static <T> EqualBy<T> create(String name,
                                        Supplier<T> toEvaluate,
                                        T expected,
                                        BiPredicate<T, T> equalsFn,
                                        Optional<Integer> timeoutOverride) {
        return create(name, toEvaluate, expected, equalsFn, defaultMkString(), timeoutOverride);
    }

    public static <T> EqualBy<T> create(String name,
                                        Supplier<T> toEvaluate,
                                        T expected,
                                        BiPredicate<T, T> equalsFn,
                                        int timeoutOverride) {
        return create(name, toEvaluate, expected, equalsFn, defaultMkString(), Optional.of(timeoutOverride));
    }

     public static <T> EqualBy<T> create(String name,
                                         Supplier<T> toEvaluate,
                                         T expected,
                                         BiPredicate<T, T> equalsFn,
                                         Function<T, String> mkString,
                                         int timeoutOverride) {
         return create(name, toEvaluate, expected, equalsFn, mkString, Optional.of(timeoutOverride));
     }
}
