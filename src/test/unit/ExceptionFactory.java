package test.unit;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.Objects;

/**
 * Factory utility class for creating tests that verify an expression throws an exception
 * of one *specific* type {@code E}. It optionally allows checking the thrown exception's
 * message against an *exact* string.
 *
 * This class acts as a convenience wrapper around {@link ExceptionOneOf}, simplifying
 * the common case of expecting a single, specific exception type. It does not
 * directly support message predicates (use {@link ExceptionOneOf} or {@link TestFactory} for that).
 *
 * @author Pepe Gallardo & Gemini
 */
public final class ExceptionFactory {

    private ExceptionFactory() {} // Prevent instantiation

    private static <T> Function<T, String> defaultMkString() {
        return obj -> Objects.toString(obj, "null");
    }

    /**
     * Base factory method for creating a test that expects a specific exception type {@code E}.
     * It delegates the actual test creation to {@link ExceptionOneOf}.
     * Primarily designed for checking an optional *exact* message.
     *
     * @param name The name of the test.
     * @param toEvaluate The supplier expected to throw an exception of type {@code E}.
     * @param mkString Function to convert result `T` to string if no exception is thrown. Defaults to {@code Objects.toString}.
     * @param expectedMessage If present, requires the thrown exception's message to match exactly.
     * @param timeoutOverride Optional specific timeout duration in seconds.
     * @param expectedType The specific exception type {@code Class} expected.
     * @param <T> The return type of `toEvaluate`.
     * @param <E> The specific exception type expected.
     * @return An {@link ExceptionOneOf}<T> test instance configured for the single type {@code E}.
     */
    public static <T, E extends Throwable> ExceptionOneOf<T> create(
            String name,
            Supplier<T> toEvaluate,
            Function<T, String> mkString,
            Optional<String> expectedMessage,
            Optional<Integer> timeoutOverride,
            Class<E> expectedType) {

        // Delegate to ExceptionOneOf's base factory, passing the single Class for E
        // Provide default predicate/help, which are effectively ignored by ExceptionOneOf
        // if expectedMessage is present.
        return ExceptionOneOf.create(
                name,
                toEvaluate,
                mkString,
                expectedMessage,
                ExceptionBy.DEFAULT_MSG_PREDICATE, // Default predicate
                Optional.empty(),            // Default predicate help
                timeoutOverride,
                expectedType // Pass the single Class for E
        );
    }

    // --- Convenience Overloads ---

    /**
     * Creates a test expecting a specific exception type `E` with an *exact* message.
     *
     * @param name Test name.
     * @param toEvaluate Supplier expected to throw exception `E`.
     * @param expectedMessage The exact required message of the thrown exception.
     * @param expectedType The specific exception type `Class` expected.
     * @param <T> Return type of `toEvaluate`.
     * @param <E> Specific expected exception type.
     * @return An {@link ExceptionOneOf}<T> test instance.
     */
    public static <T, E extends Throwable> ExceptionOneOf<T> create(
            String name,
            Supplier<T> toEvaluate,
            String expectedMessage, // Exact message overload
            Class<E> expectedType) {
        return create(name, toEvaluate, defaultMkString(), Optional.of(expectedMessage), Optional.empty(), expectedType);
    }

    /**
     * Creates a test expecting a specific exception type `E` (any message).
     *
     * @param name Test name.
     * @param toEvaluate Supplier expected to throw exception `E`.
     * @param expectedType The specific exception type `Class` expected.
     * @param <T> Return type of `toEvaluate`.
     * @param <E> Specific expected exception type.
     * @return An {@link ExceptionOneOf}<T> test instance.
     */
    public static <T, E extends Throwable> ExceptionOneOf<T> create(
            String name,
            Supplier<T> toEvaluate, // Type only overload
            Class<E> expectedType) {
        return create(name, toEvaluate, defaultMkString(), Optional.empty(), Optional.empty(), expectedType);
    }

    /**
     * Creates a test expecting a specific exception type `E`, an *exact* message, and a specific timeout.
     *
     * @param name Test name.
     * @param toEvaluate Supplier expected to throw exception `E`.
     * @param expectedMessage The exact required message of the thrown exception.
     * @param timeoutOverride Specific timeout duration in seconds.
     * @param expectedType The specific exception type `Class` expected.
     * @param <T> Return type of `toEvaluate`.
     * @param <E> Specific expected exception type.
     * @return An {@link ExceptionOneOf}<T> test instance.
     */
    public static <T, E extends Throwable> ExceptionOneOf<T> create(
            String name,
            Supplier<T> toEvaluate,
            String expectedMessage,
            int timeoutOverride,
            Class<E> expectedType) {
        return create(name, toEvaluate, defaultMkString(), Optional.of(expectedMessage), Optional.of(timeoutOverride), expectedType);
    }

    /**
     * Creates a test expecting a specific exception type `E` (any message) and a specific timeout.
     *
     * @param name Test name.
     * @param toEvaluate Supplier expected to throw exception `E`.
     * @param timeoutOverride Specific timeout duration in seconds.
     * @param expectedType The specific exception type `Class` expected.
     * @param <T> Return type of `toEvaluate`.
     * @param <E> Specific expected exception type.
     * @return An {@link ExceptionOneOf}<T> test instance.
     */
    public static <T, E extends Throwable> ExceptionOneOf<T> create(
            String name,
            Supplier<T> toEvaluate,
            int timeoutOverride,
            Class<E> expectedType) {
        return create(name, toEvaluate, defaultMkString(), Optional.empty(), Optional.of(timeoutOverride), expectedType);
    }
}
