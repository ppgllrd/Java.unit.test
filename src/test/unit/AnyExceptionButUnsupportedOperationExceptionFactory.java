package test.unit;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Factory utility class for creating tests that expect any {@code Throwable} to be thrown,
 * *except* for {@code UnsupportedOperationException}. It allows specifying conditions
 * on the thrown exception's message (exact match or predicate).
 *
 * This class delegates the actual test creation to {@link ExceptionExcept}.
 *
 * @author Pepe Gallardo & Gemini
 */
public final class AnyExceptionButUnsupportedOperationExceptionFactory {

     private AnyExceptionButUnsupportedOperationExceptionFactory() {} // Prevent instantiation

    // Define the excluded type directly
    private static final Class<UnsupportedOperationException> EXCLUDED_TYPE = UnsupportedOperationException.class;

    private static <T> Function<T, String> defaultMkString() {
        return obj -> Objects.toString(obj, "null");
    }

    /**
     * Creates a test that expects any `Throwable` except `UnsupportedOperationException`.
     * This is the most general factory method, accepting all configuration options.
     * It delegates directly to the `ExceptionExcept` factory.
     *
     * @param name The name of the test.
     * @param toEvaluate The supplier expected to throw an exception (but not UnsupportedOperationException).
     * @param mkString A function to convert the result (if no exception is thrown) to a string. Defaults to {@code Objects.toString}.
     * @param expectedMessage If present, the thrown exception's message must match exactly. Takes priority over `messagePredicate`.
     * @param messagePredicate If `expectedMessage` is empty, this predicate is applied to the message. Defaults to always true.
     * @param predicateHelp If `messagePredicate` is used, provides a human-readable description.
     * @param timeoutOverride An optional duration in seconds to override the default test timeout.
     * @param <T> The return type of the `toEvaluate` supplier.
     * @return An {@code ExceptionExcept<T, UnsupportedOperationException>} test instance configured as specified.
     */
    public static <T> ExceptionExcept<T, UnsupportedOperationException> create(
            String name,
            Supplier<T> toEvaluate,
            Function<T, String> mkString,
            Optional<String> expectedMessage,
            Predicate<String> messagePredicate,
            Optional<String> predicateHelp,
            Optional<Integer> timeoutOverride) {
        // Delegate directly to ExceptionExcept's base factory method
        return ExceptionExcept.create(
                name,
                toEvaluate,
                mkString,
                expectedMessage,
                messagePredicate,
                predicateHelp,
                timeoutOverride,
                EXCLUDED_TYPE
        );
    }

    // --- Convenience Overloads ---

    /**
     * Creates a test expecting any `Throwable` except `UnsupportedOperationException`,
     * requiring the exception message to match the `expectedMessage` exactly.
     *
     * @param name The name of the test.
     * @param toEvaluate The supplier expected to throw an exception.
     * @param expectedMessage The exact string the thrown exception's message must equal.
     * @param <T> The return type of the `toEvaluate` supplier.
     * @return An {@code ExceptionExcept<T, UnsupportedOperationException>} test instance.
     */
    public static <T> ExceptionExcept<T, UnsupportedOperationException> create(
            String name,
            Supplier<T> toEvaluate,
            String expectedMessage // Exact message overload
            ) {
         return create(name, toEvaluate, defaultMkString(), Optional.of(expectedMessage), ExceptionBy.DEFAULT_MSG_PREDICATE, Optional.empty(), Optional.empty());
     }

     /**
     * Creates a test expecting any `Throwable` except `UnsupportedOperationException` (any message).
     *
     * @param name The name of the test.
     * @param toEvaluate The supplier expected to throw an exception.
     * @param <T> The return type of the `toEvaluate` supplier.
     * @return An {@code ExceptionExcept<T, UnsupportedOperationException>} test instance.
     */
    public static <T> ExceptionExcept<T, UnsupportedOperationException> create(
            String name,
            Supplier<T> toEvaluate // Basic overload
            ) {
         return create(name, toEvaluate, defaultMkString(), Optional.empty(), ExceptionBy.DEFAULT_MSG_PREDICATE, Optional.empty(), Optional.empty());
     }

    /**
     * Creates a test expecting any `Throwable` except `UnsupportedOperationException`,
     * requiring the exception message to satisfy the given `messagePredicate`.
     * A help string describing the predicate is required.
     *
     * @param name The name of the test.
     * @param toEvaluate The supplier expected to throw an exception.
     * @param messagePredicate A predicate applied to the exception message.
     * @param predicateHelp A human-readable description of the predicate.
     * @param <T> The return type of the `toEvaluate` supplier.
     * @return An {@code ExceptionExcept<T, UnsupportedOperationException>} test instance.
     */
    public static <T> ExceptionExcept<T, UnsupportedOperationException> create(
            String name,
            Supplier<T> toEvaluate,
            Predicate<String> messagePredicate,
            String predicateHelp               // Help text REQUIRED
            ) {
         return create(name, toEvaluate, defaultMkString(), Optional.empty(), messagePredicate, Optional.of(predicateHelp), Optional.empty());
     }

    /**
     * Creates a test expecting any `Throwable` except `UnsupportedOperationException`,
     * requiring the exception message to satisfy the given `messagePredicate`.
     * No help string is provided for the predicate.
     *
     * @param name The name of the test.
     * @param toEvaluate The supplier expected to throw an exception.
     * @param messagePredicate A predicate applied to the exception message.
     * @param <T> The return type of the `toEvaluate` supplier.
     * @return An {@code ExceptionExcept<T, UnsupportedOperationException>} test instance.
     */
    public static <T> ExceptionExcept<T, UnsupportedOperationException> create(
            String name,
            Supplier<T> toEvaluate,
            Predicate<String> messagePredicate // Predicate overload (no help)
            ) {
         return create(name, toEvaluate, defaultMkString(), Optional.empty(), messagePredicate, Optional.empty(), Optional.empty());
     }

    // --- Timeout Overloads ---

     /** Creates test expecting not UnsupportedOperationException, *exact* message, and timeout. */
     public static <T> ExceptionExcept<T, UnsupportedOperationException> create(
             String name,
             Supplier<T> toEvaluate,
             String expectedMessage,
             int timeoutOverride) {
          return create(name, toEvaluate, defaultMkString(), Optional.of(expectedMessage), ExceptionBy.DEFAULT_MSG_PREDICATE, Optional.empty(), Optional.of(timeoutOverride));
      }

      /** Creates test expecting not UnsupportedOperationException, *predicate*, help text, and timeout. */
      public static <T> ExceptionExcept<T, UnsupportedOperationException> create(
              String name,
              Supplier<T> toEvaluate,
              Predicate<String> messagePredicate,
              String predicateHelp,
              int timeoutOverride) {
          return create(name, toEvaluate, defaultMkString(), Optional.empty(), messagePredicate, Optional.of(predicateHelp), Optional.of(timeoutOverride));
      }

      /** Creates test expecting not UnsupportedOperationException (any message) and timeout. */
      public static <T> ExceptionExcept<T, UnsupportedOperationException> create(
              String name,
              Supplier<T> toEvaluate,
              int timeoutOverride) {
           return create(name, toEvaluate, defaultMkString(), Optional.empty(), ExceptionBy.DEFAULT_MSG_PREDICATE, Optional.empty(), Optional.of(timeoutOverride));
       }

       /** Creates test expecting not UnsupportedOperationException, *predicate* (no help), and timeout. */
       public static <T> ExceptionExcept<T, UnsupportedOperationException> create(
              String name,
              Supplier<T> toEvaluate,
              Predicate<String> messagePredicate,
              int timeoutOverride) {
          return create(name, toEvaluate, defaultMkString(), Optional.empty(), messagePredicate, Optional.empty(), Optional.of(timeoutOverride));
      }
}
