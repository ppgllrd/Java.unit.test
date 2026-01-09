package test.unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A specific exception test that verifies an expression throws *any* {@code Throwable}
 * *except* for a specified excluded type {@code E}.
 * It optionally allows checking the thrown exception's message against an exact string or a predicate.
 * <p>
 * It extends {@link ExceptionBy}, providing a {@code throwablePredicate} that checks if the
 * thrown exception is *not* an instance of the excluded type {@code E}.
 * Instances should be created using the static factory methods.
 *
 * @param <T> The return type of {@code toEvaluate} (if it were to complete normally).
 * @param <E> The type of {@code Throwable} that is explicitly *not* expected.
 * @author Pepe Gallardo & Gemini
 */
public class ExceptionExcept<T, E extends Throwable> extends ExceptionBy<T> {

  /**
     * Private constructor. Use static factory methods.
     */
    private ExceptionExcept(String name,
                           Supplier<T> toEvaluate,
                           Function<T, String> mkString,
                           Class<E> excludedExceptionClass, // Takes Class object directly
                           Optional<String> expectedMessage,
                           Predicate<String> messagePredicate,
                           Optional<String> predicateHelp,
                           String helpKey,
                           List<HelpArg> helpArgs,
                           Optional<Integer> timeoutOverride) {
        super(name,
              toEvaluate,
              mkString,
              (thrown) -> !excludedExceptionClass.isInstance(thrown), // Predicate: NOT instance of E
              expectedMessage,
              messagePredicate,
              predicateHelp,
              helpKey,
              helpArgs,
              timeoutOverride);
    }

    // --- Static Factory Methods ---

    private static <T> Function<T, String> defaultMkString() {
        return obj -> Objects.toString(obj, "null");
    }

    /**
     * Creates an `ExceptionExcept` test instance, the most general factory method.
     * It determines the appropriate localization key (`helpKey`) and arguments (`helpArgs`)
     * based on whether an exact message, a predicate with help, or neither is specified.
     *
     * @param name The name of the test.
     * @param toEvaluate The supplier expected to throw an exception (but not `E`).
     * @param mkString Function to convert result `T` to string if no exception is thrown. Defaults to {@code Objects.toString}.
     * @param expectedMessage If present, requires the thrown exception's message to match exactly. Priority over `messagePredicate`.
     * @param messagePredicate If `expectedMessage` is empty, this predicate is applied to the message. Defaults to always true.
     * @param predicateHelp Description of the `messagePredicate` for error messages.
     * @param timeoutOverride Optional specific timeout duration in seconds.
     * @param excludedType The {@code Class} object of the exception type (`<: Throwable`) that is *not* expected.
     * @param <T> The return type of `toEvaluate`.
     * @param <E> The exception type not expected.
     * @return An {@code ExceptionExcept<T, E>} test instance.
     */
    public static <T, E extends Throwable> ExceptionExcept<T, E> create(
            String name,
            Supplier<T> toEvaluate,
            Function<T, String> mkString,
            Optional<String> expectedMessage,
            Predicate<String> messagePredicate,
            Optional<String> predicateHelp,
            Optional<Integer> timeoutOverride,
            Class<E> excludedType) { // Takes Class directly

        Objects.requireNonNull(excludedType, "excludedType cannot be null");
        String excludedTypeName = excludedType.getSimpleName();

        // Determine the localization key and formatting arguments based on message expectations
        String key;
        List<HelpArg> args = new ArrayList<>();
        args.add(new HelpArg.TypeName(excludedTypeName)); // Always add excluded type

        if (expectedMessage.isPresent()) {
            key = "exception.except.with.message.description";
            args.add(new HelpArg.ExactMessage(expectedMessage.get()));
        } else if (predicateHelp.isPresent()) {
            key = "exception.except.with.predicate.description";
            args.add(new HelpArg.PredicateHelp(predicateHelp.get()));
        } else {
            key = "exception.except.description";
            // Only the type name arg is needed
        }

        // Ensure messagePredicate is not null if expectedMessage is absent
        Predicate<String> finalMessagePredicate = expectedMessage.isPresent() ? DEFAULT_MSG_PREDICATE : Objects.requireNonNull(messagePredicate, "messagePredicate required when expectedMessage is empty");
        Optional<String> finalPredicateHelp = expectedMessage.isPresent() ? Optional.empty() : predicateHelp;


        // Call the private constructor with all parameters
        return new ExceptionExcept<>(
                name,
                toEvaluate,
                mkString,
                excludedType,
                expectedMessage,
                finalMessagePredicate, // Use the resolved predicate
                finalPredicateHelp,    // Use the resolved help optional
                key,
                args, // Pass determined args list
                timeoutOverride
        );
    }

    // --- Convenience Overloads ---

     public static <T, E extends Throwable> ExceptionExcept<T, E> create(
            String name,
            Supplier<T> toEvaluate,
            Class<E> excludedType) {
         return create(name, toEvaluate, defaultMkString(), Optional.empty(), DEFAULT_MSG_PREDICATE, Optional.empty(), Optional.empty(), excludedType);
     }

    public static <T, E extends Throwable> ExceptionExcept<T, E> create(
            String name,
            Supplier<T> toEvaluate,
            String expectedMessage, // Exact message overload
            Class<E> excludedType) {
        return create(name, toEvaluate, defaultMkString(), Optional.of(expectedMessage), DEFAULT_MSG_PREDICATE, Optional.empty(), Optional.empty(), excludedType);
    }

     public static <T, E extends Throwable> ExceptionExcept<T, E> create(
             String name,
             Supplier<T> toEvaluate,
             Predicate<String> messagePredicate, // Predicate overload (no help)
             Class<E> excludedType) {
         return create(name, toEvaluate, defaultMkString(), Optional.empty(), messagePredicate, Optional.empty(), Optional.empty(), excludedType);
     }

     public static <T, E extends Throwable> ExceptionExcept<T, E> create(
            String name,
            Supplier<T> toEvaluate,
            Predicate<String> messagePredicate,
            String predicateHelp, // Predicate with help overload
            Class<E> excludedType) {
         return create(name, toEvaluate, defaultMkString(), Optional.empty(), messagePredicate, Optional.of(predicateHelp), Optional.empty(), excludedType);
     }

    // --- Timeout Overloads ---

    public static <T, E extends Throwable> ExceptionExcept<T, E> create(
            String name,
            Supplier<T> toEvaluate,
            int timeoutOverride,
            Class<E> excludedType) {
         return create(name, toEvaluate, defaultMkString(), Optional.empty(), DEFAULT_MSG_PREDICATE, Optional.empty(), Optional.of(timeoutOverride), excludedType);
     }

    public static <T, E extends Throwable> ExceptionExcept<T, E> create(
            String name,
            Supplier<T> toEvaluate,
            String expectedMessage,
            int timeoutOverride,
            Class<E> excludedType) {
        return create(name, toEvaluate, defaultMkString(), Optional.of(expectedMessage), DEFAULT_MSG_PREDICATE, Optional.empty(), Optional.of(timeoutOverride), excludedType);
    }

    public static <T, E extends Throwable> ExceptionExcept<T, E> create(
            String name,
            Supplier<T> toEvaluate,
            Predicate<String> messagePredicate,
            String predicateHelp,
            int timeoutOverride,
            Class<E> excludedType) {
         return create(name, toEvaluate, defaultMkString(), Optional.empty(), messagePredicate, Optional.of(predicateHelp), Optional.of(timeoutOverride), excludedType);
     }
      public static <T, E extends Throwable> ExceptionExcept<T, E> create(
             String name,
             Supplier<T> toEvaluate,
             Predicate<String> messagePredicate,
             int timeoutOverride,
             Class<E> excludedType) {
         return create(name, toEvaluate, defaultMkString(), Optional.empty(), messagePredicate, Optional.empty(), Optional.of(timeoutOverride), excludedType);
     }

}
