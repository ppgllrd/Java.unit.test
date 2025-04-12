package test.unit;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A specific exception test that verifies an expression throws an exception whose type
 * is *one of* a specified set of allowed {@code Throwable} types.
 * It optionally allows checking the thrown exception's message against an exact string or a predicate.
 *
 * It extends {@link ExceptionBy}, providing a {@code throwablePredicate} that checks if the
 * thrown exception's type is present in the {@code expectedExceptionClasses} set.
 * Instances should be created using the static factory methods.
 *
 * @param <T> The return type of {@code toEvaluate} (if it were to complete normally).
 * @author Pepe Gallardo & Gemini
 */
public class ExceptionOneOf<T> extends ExceptionBy<T> {

  /**
     * Private constructor. Use static factory methods.
     */
    private ExceptionOneOf(String name,
                           Supplier<T> toEvaluate,
                           Function<T, String> mkString,
                           Set<Class<? extends Throwable>> expectedExceptionClasses, // Takes Set of Classes
                           Optional<String> expectedMessage,
                           Predicate<String> messagePredicate,
                           Optional<String> predicateHelp,
                           String helpKey,
                           List<HelpArg> helpArgs,
                           Optional<Integer> timeoutOverride) {
        super(name,
              toEvaluate,
              mkString,
              (thrown) -> expectedExceptionClasses.stream().anyMatch(cls -> cls.isInstance(thrown)), // Predicate checks set membership
              expectedMessage,
              messagePredicate,
              predicateHelp,
              helpKey,
              helpArgs,
              timeoutOverride);

        if (expectedExceptionClasses == null || expectedExceptionClasses.isEmpty()) {
             throw new IllegalArgumentException("expectedExceptionClasses set cannot be null or empty");
        }
      // Store expected classes
      Set<Class<? extends Throwable>> expectedExceptionClasses1 = Set.copyOf(expectedExceptionClasses); // Ensure immutable
    }

    // --- Static Factory Methods ---

    private static <T> Function<T, String> defaultMkString() {
        return obj -> Objects.toString(obj, "null");
    }

    /**
     * Creates an `ExceptionOneOf` test instance, the most general factory method.
     * Requires at least one expected exception type. It determines the appropriate
     * localization key (`helpKey`) and arguments (`helpArgs`) based on the number
     * of expected types and whether message checks (exact or predicate) are specified.
     *
     * @param name The name of the test.
     * @param toEvaluate The supplier expected to throw one of the `expectedTypes`.
     * @param mkString Function to convert result `T` to string if no exception is thrown. Defaults to {@code Objects.toString}.
     * @param expectedMessage If present, requires the thrown exception's message to match exactly. Priority over `messagePredicate`.
     * @param messagePredicate If `expectedMessage` is empty, this predicate is applied to the message. Defaults to always true.
     * @param predicateHelp Description of the `messagePredicate` for error messages.
     * @param timeoutOverride Optional specific timeout duration in seconds.
     * @param expectedTypes A varargs array of {@code Class} objects representing the allowed exception types (`<: Throwable`). Must not be empty.
     * @param <T> The return type of `toEvaluate`.
     * @return An {@code ExceptionOneOf<T>} test instance.
     * @throws IllegalArgumentException if `expectedTypes` is null or empty.
     */
    @SafeVarargs // Suppress warning for generic varargs array creation
    public static <T> ExceptionOneOf<T> create(
            String name,
            Supplier<T> toEvaluate,
            Function<T, String> mkString,
            Optional<String> expectedMessage,
            Predicate<String> messagePredicate,
            Optional<String> predicateHelp,
            Optional<Integer> timeoutOverride,
            Class<? extends Throwable>... expectedTypes) { // Takes Class varargs

        if (expectedTypes == null || expectedTypes.length == 0) {
            throw new IllegalArgumentException("Must provide at least one expected exception type for ExceptionOneOf.");
        }
        // Convert array to an immutable set
        Set<Class<? extends Throwable>> expectedClasses = Set.of(expectedTypes);

        // Get simple names for constructing help arguments, sorted for consistent order
        List<String> typeNamesSorted = expectedClasses.stream()
                .map(Class::getSimpleName)
                .sorted()
                .toList(); // Java 16+

        // Determine the localization key and formatting arguments based on expectations
        String key;
        List<HelpArg> args = new ArrayList<>();
        HelpArg typeArg = (typeNamesSorted.size() == 1)
                          ? new HelpArg.TypeName(typeNamesSorted.getFirst())
                          : new HelpArg.TypeNameList(typeNamesSorted);
        args.add(typeArg); // Always add type argument(s) first

        if (expectedMessage.isPresent()) {
            key = (expectedClasses.size() == 1) ? "exception.with.message.description" : "exception.oneof.with.message.description";
            args.add(new HelpArg.ExactMessage(expectedMessage.get()));
        } else if (predicateHelp.isPresent()) {
             key = (expectedClasses.size() == 1) ? "exception.with.predicate.description" : "exception.oneof.with.predicate.description";
             args.add(new HelpArg.PredicateHelp(predicateHelp.get()));
        } else {
             key = (expectedClasses.size() == 1) ? "exception.description" : "exception.oneof.description";
            // Only the type arg is needed
        }

        // Ensure messagePredicate is not null if expectedMessage is absent
        Predicate<String> finalMessagePredicate = expectedMessage.isPresent() ? DEFAULT_MSG_PREDICATE : Objects.requireNonNull(messagePredicate, "messagePredicate required when expectedMessage is empty");
         Optional<String> finalPredicateHelp = expectedMessage.isPresent() ? Optional.empty() : predicateHelp;


        // Call the private constructor
        return new ExceptionOneOf<>(
                name,
                toEvaluate,
                mkString,
                expectedClasses,
                expectedMessage,
                finalMessagePredicate,
                finalPredicateHelp,
                key,
                args,
                timeoutOverride
        );
    }

    // --- Convenience Overloads ---

    @SafeVarargs
    public static <T> ExceptionOneOf<T> create(
            String name,
            Supplier<T> toEvaluate,
            Class<? extends Throwable>... expectedTypes) {
        return create(name, toEvaluate, defaultMkString(), Optional.empty(), DEFAULT_MSG_PREDICATE, Optional.empty(), Optional.empty(), expectedTypes);
    }

    @SafeVarargs
    public static <T> ExceptionOneOf<T> create(
            String name,
            Supplier<T> toEvaluate,
            String expectedMessage, // Exact message overload
            Class<? extends Throwable>... expectedTypes) {
        return create(name, toEvaluate, defaultMkString(), Optional.of(expectedMessage), DEFAULT_MSG_PREDICATE, Optional.empty(), Optional.empty(), expectedTypes);
    }

    @SafeVarargs
     public static <T> ExceptionOneOf<T> create(
             String name,
             Supplier<T> toEvaluate,
             Predicate<String> messagePredicate, // Predicate overload (no help)
             Class<? extends Throwable>... expectedTypes) {
         return create(name, toEvaluate, defaultMkString(), Optional.empty(), messagePredicate, Optional.empty(), Optional.empty(), expectedTypes);
     }

     @SafeVarargs
     public static <T> ExceptionOneOf<T> create(
            String name,
            Supplier<T> toEvaluate,
            Predicate<String> messagePredicate,
            String predicateHelp, // Predicate with help overload
            Class<? extends Throwable>... expectedTypes) {
         return create(name, toEvaluate, defaultMkString(), Optional.empty(), messagePredicate, Optional.of(predicateHelp), Optional.empty(), expectedTypes);
     }

    // --- Timeout Overloads ---

    @SafeVarargs
    public static <T> ExceptionOneOf<T> create(
            String name,
            Supplier<T> toEvaluate,
            int timeoutOverride,
            Class<? extends Throwable>... expectedTypes) {
         return create(name, toEvaluate, defaultMkString(), Optional.empty(), DEFAULT_MSG_PREDICATE, Optional.empty(), Optional.of(timeoutOverride), expectedTypes);
     }

     @SafeVarargs
    public static <T> ExceptionOneOf<T> create(
            String name,
            Supplier<T> toEvaluate,
            String expectedMessage,
            int timeoutOverride,
            Class<? extends Throwable>... expectedTypes) {
        return create(name, toEvaluate, defaultMkString(), Optional.of(expectedMessage), DEFAULT_MSG_PREDICATE, Optional.empty(), Optional.of(timeoutOverride), expectedTypes);
    }

    @SafeVarargs
    public static <T> ExceptionOneOf<T> create(
            String name,
            Supplier<T> toEvaluate,
            Predicate<String> messagePredicate,
            String predicateHelp,
            int timeoutOverride,
            Class<? extends Throwable>... expectedTypes) {
         return create(name, toEvaluate, defaultMkString(), Optional.empty(), messagePredicate, Optional.of(predicateHelp), Optional.of(timeoutOverride), expectedTypes);
     }

    @SafeVarargs
     public static <T> ExceptionOneOf<T> create(
             String name,
             Supplier<T> toEvaluate,
             Predicate<String> messagePredicate,
             int timeoutOverride,
             Class<? extends Throwable>... expectedTypes) {
         return create(name, toEvaluate, defaultMkString(), Optional.empty(), messagePredicate, Optional.empty(), Optional.of(timeoutOverride), expectedTypes);
     }
}
