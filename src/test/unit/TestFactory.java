package test.unit;

import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Central factory class providing convenient static methods for creating
  * various types of {@link Test} instances (e.g., equality, property, exception tests).
  * <p>
  * This class delegates the actual creation to the static factory methods
  * of the specific test classes (like {@link Equal}, {@link Property}, {@link ExceptionOneOf}, etc.).
  * It aims to provide a simpler, unified entry point for test definition.
  *
  * @author Pepe Gallardo & Gemini
  */
public final class TestFactory {

    // Private constructor to prevent instantiation
    private TestFactory() {}

    // Default mkString helper
    private static <T> Function<T, String> defaultMkString() {
         return obj -> java.util.Objects.toString(obj, "null");
    }

    // --- Equality Tests --- (Delegating to Equal / EqualBy)

    /** Creates an {@link Equal} test verifying {@code toEvaluate.get()} equals {@code expected} using {@code Objects.equals}. */
    public static <T> Equal<T> equal(
            String name,
            Supplier<T> toEvaluate,
            T expected,
            Function<T, String> mkString,
            Optional<Integer> timeoutOverride) {
        return Equal.create(name, toEvaluate, expected, mkString, timeoutOverride);
    }
    // Overloads
    public static <T> Equal<T> equal(String name, Supplier<T> toEvaluate, T expected) {
        return Equal.create(name, toEvaluate, expected);
    }
    public static <T> Equal<T> equal(String name, Supplier<T> toEvaluate, T expected, Function<T, String> mkString) {
        return Equal.create(name, toEvaluate, expected, mkString);
    }
    public static <T> Equal<T> equal(String name, Supplier<T> toEvaluate, T expected, int timeoutOverride) {
        return Equal.create(name, toEvaluate, expected, timeoutOverride);
    }
     public static <T> Equal<T> equal(String name, Supplier<T> toEvaluate, T expected, Function<T, String> mkString, int timeoutOverride) {
        return Equal.create(name, toEvaluate, expected, mkString, timeoutOverride);
    }


    /** Creates an {@link EqualBy} test using a custom {@code equalsFn}. */
    public static <T> EqualBy<T> equalBy(
            String name,
            Supplier<T> toEvaluate,
            T expected,
            BiPredicate<T, T> equalsFn,
            Function<T, String> mkString,
            Optional<Integer> timeoutOverride) {
        return EqualBy.create(name, toEvaluate, expected, equalsFn, mkString, timeoutOverride);
    }
     // Overloads
    public static <T> EqualBy<T> equalBy(String name, Supplier<T> toEvaluate, T expected, BiPredicate<T, T> equalsFn) {
        return EqualBy.create(name, toEvaluate, expected, equalsFn);
    }
    public static <T> EqualBy<T> equalBy(String name, Supplier<T> toEvaluate, T expected, BiPredicate<T, T> equalsFn, Function<T, String> mkString) {
        return EqualBy.create(name, toEvaluate, expected, equalsFn, mkString);
    }
    public static <T> EqualBy<T> equalBy(String name, Supplier<T> toEvaluate, T expected, BiPredicate<T, T> equalsFn, int timeoutOverride) {
        return EqualBy.create(name, toEvaluate, expected, equalsFn, timeoutOverride);
    }
    public static <T> EqualBy<T> equalBy(String name, Supplier<T> toEvaluate, T expected, BiPredicate<T, T> equalsFn, Function<T, String> mkString, int timeoutOverride) {
         return EqualBy.create(name, toEvaluate, expected, equalsFn, mkString, timeoutOverride);
     }

    // --- Property Tests --- (Delegating to Property)

    /** Creates a {@link Property} test verifying {@code property.test(toEvaluate.get())} is true. */
    public static <T> Property<T> property(
            String name,
            Supplier<T> toEvaluate,
            Predicate<T> property,
            Optional<Function<T, String>> mkStringOpt,
            Optional<String> helpOpt,
            Optional<Integer> timeoutOverride) {
        return Property.create(name, toEvaluate, property, mkStringOpt, helpOpt, timeoutOverride);
    }
     // Overloads
    public static <T> Property<T> property(String name, Supplier<T> toEvaluate, Predicate<T> property) {
        return Property.create(name, toEvaluate, property);
    }
    public static <T> Property<T> property(String name, Supplier<T> toEvaluate, Predicate<T> property, String help) {
        return Property.create(name, toEvaluate, property, help);
    }
    public static <T> Property<T> property(String name, Supplier<T> toEvaluate, Predicate<T> property, Function<T, String> mkString) {
         return Property.create(name, toEvaluate, property, mkString);
     }
     public static <T> Property<T> property(String name, Supplier<T> toEvaluate, Predicate<T> property, Function<T, String> mkString, String help) {
          return Property.create(name, toEvaluate, property, mkString, help);
      }
     public static <T> Property<T> property(String name, Supplier<T> toEvaluate, Predicate<T> property, int timeoutOverride) {
         return Property.create(name, toEvaluate, property, timeoutOverride);
     }
     public static <T> Property<T> property(String name, Supplier<T> toEvaluate, Predicate<T> property, Function<T, String> mkString, int timeoutOverride) {
          return Property.create(name, toEvaluate, property, mkString, timeoutOverride);
      }
     public static <T> Property<T> property(String name, Supplier<T> toEvaluate, Predicate<T> property, String help, int timeoutOverride) {
          return Property.create(name, toEvaluate, property, help, timeoutOverride);
      }
      public static <T> Property<T> property(String name, Supplier<T> toEvaluate, Predicate<T> property, Function<T, String> mkString, String help, int timeoutOverride) {
          return Property.create(name, toEvaluate, property, mkString, help, timeoutOverride);
      }


    // --- Assert / Refute Tests --- (Delegating to Assert / Refute)

    /** Creates an {@link Assert} test verifying {@code toEvaluate.get()} is true. */
    public static Assert assertTest(
            String name,
            Supplier<Boolean> toEvaluate,
            Optional<Integer> timeoutOverride) {
        return Assert.create(name, toEvaluate, timeoutOverride);
    }
     // Overloads
    public static Assert assertTest(String name, Supplier<Boolean> toEvaluate) {
        return Assert.create(name, toEvaluate);
    }
    public static Assert assertTest(String name, Supplier<Boolean> toEvaluate, int timeoutOverride) {
        return Assert.create(name, toEvaluate, timeoutOverride);
    }

    /** Creates a {@link Refute} test verifying {@code toEvaluate.get()} is false. */
    public static Refute refuteTest(
            String name,
            Supplier<Boolean> toEvaluate,
            Optional<Integer> timeoutOverride) {
        return Refute.create(name, toEvaluate, timeoutOverride);
    }
    // Overloads
    public static Refute refuteTest(String name, Supplier<Boolean> toEvaluate) {
        return Refute.create(name, toEvaluate);
    }
    public static Refute refuteTest(String name, Supplier<Boolean> toEvaluate, int timeoutOverride) {
        return Refute.create(name, toEvaluate, timeoutOverride);
    }

    // --- Exception Tests ---

    /**
     * Creates a test expecting a *specific* exception type {@code E}.
     * Delegates to {@link ExceptionFactory}. Primarily supports checking for an *exact* message.
     *
     * @return An {@link ExceptionOneOf}<T> test instance.
     */
    public static <T, E extends Throwable> ExceptionOneOf<T> expectException(
            String name,
            Supplier<T> toEvaluate,
            Function<T, String> mkString,
            Optional<String> expectedMessage,
            Optional<Integer> timeoutOverride,
            Class<E> expectedType) {
        return ExceptionFactory.create(name, toEvaluate, mkString, expectedMessage, timeoutOverride, expectedType);
    }
     // Overloads for expectException
     public static <T, E extends Throwable> ExceptionOneOf<T> expectException(String name, Supplier<T> toEvaluate, Class<E> expectedType) {
         return ExceptionFactory.create(name, toEvaluate, expectedType);
     }
     public static <T, E extends Throwable> ExceptionOneOf<T> expectException(String name, Supplier<T> toEvaluate, String expectedMessage, Class<E> expectedType) {
         return ExceptionFactory.create(name, toEvaluate, expectedMessage, expectedType);
     }
     public static <T, E extends Throwable> ExceptionOneOf<T> expectException(String name, Supplier<T> toEvaluate, int timeoutOverride, Class<E> expectedType) {
         return ExceptionFactory.create(name, toEvaluate, timeoutOverride, expectedType);
     }
     public static <T, E extends Throwable> ExceptionOneOf<T> expectException(String name, Supplier<T> toEvaluate, String expectedMessage, int timeoutOverride, Class<E> expectedType) {
         return ExceptionFactory.create(name, toEvaluate, expectedMessage, timeoutOverride, expectedType);
     }


    /**
     * Creates an {@link ExceptionOneOf} test expecting one of the specified {@code expectedTypes}.
     * Allows checking the message via exact match or predicate.
     *
     * @return An {@link ExceptionOneOf}<T> test instance.
     */
    @SafeVarargs
    public static <T> ExceptionOneOf<T> expectExceptionOneOf(
            String name,
            Supplier<T> toEvaluate,
            Function<T, String> mkString,
            Optional<String> expectedMessage,
            Predicate<String> messagePredicate,
            Optional<String> predicateHelp,
            Optional<Integer> timeoutOverride,
            Class<? extends Throwable>... expectedTypes) {
        return ExceptionOneOf.create(name, toEvaluate, mkString, expectedMessage, messagePredicate, predicateHelp, timeoutOverride, expectedTypes);
    }
     // Overloads for expectExceptionOneOf
     @SafeVarargs
     public static <T> ExceptionOneOf<T> expectExceptionOneOf(String name, Supplier<T> toEvaluate, Class<? extends Throwable>... expectedTypes) {
         return ExceptionOneOf.create(name, toEvaluate, expectedTypes);
     }
     @SafeVarargs
     public static <T> ExceptionOneOf<T> expectExceptionOneOf(String name, Supplier<T> toEvaluate, String expectedMessage, Class<? extends Throwable>... expectedTypes) {
         return ExceptionOneOf.create(name, toEvaluate, expectedMessage, expectedTypes);
     }
     @SafeVarargs
      public static <T> ExceptionOneOf<T> expectExceptionOneOf(String name, Supplier<T> toEvaluate, Predicate<String> messagePredicate, String predicateHelp, Class<? extends Throwable>... expectedTypes) {
          return ExceptionOneOf.create(name, toEvaluate, messagePredicate, predicateHelp, expectedTypes);
      }
      @SafeVarargs
      public static <T> ExceptionOneOf<T> expectExceptionOneOf(String name, Supplier<T> toEvaluate, Predicate<String> messagePredicate, Class<? extends Throwable>... expectedTypes) {
           return ExceptionOneOf.create(name, toEvaluate, messagePredicate, expectedTypes);
       }
      @SafeVarargs
      public static <T> ExceptionOneOf<T> expectExceptionOneOf(String name, Supplier<T> toEvaluate, int timeoutOverride, Class<? extends Throwable>... expectedTypes) {
           return ExceptionOneOf.create(name, toEvaluate, timeoutOverride, expectedTypes);
       }
     // Add more timeout overloads if needed...


    /**
     * Creates an {@link ExceptionExcept} test expecting any exception *except* type {@code E}.
     * Allows checking the message via exact match or predicate.
     *
     * @return An {@link ExceptionExcept}<T, E> test instance.
     */
    public static <T, E extends Throwable> ExceptionExcept<T, E> expectExceptionExcept(
            String name,
            Supplier<T> toEvaluate,
            Function<T, String> mkString,
            Optional<String> expectedMessage,
            Predicate<String> messagePredicate,
            Optional<String> predicateHelp,
            Optional<Integer> timeoutOverride,
            Class<E> excludedType) {
        return ExceptionExcept.create(name, toEvaluate, mkString, expectedMessage, messagePredicate, predicateHelp, timeoutOverride, excludedType);
    }
     // Overloads for expectExceptionExcept
     public static <T, E extends Throwable> ExceptionExcept<T, E> expectExceptionExcept(String name, Supplier<T> toEvaluate, Class<E> excludedType) {
         return ExceptionExcept.create(name, toEvaluate, excludedType);
     }
      public static <T, E extends Throwable> ExceptionExcept<T, E> expectExceptionExcept(String name, Supplier<T> toEvaluate, String expectedMessage, Class<E> excludedType) {
          return ExceptionExcept.create(name, toEvaluate, expectedMessage, excludedType);
      }
       public static <T, E extends Throwable> ExceptionExcept<T, E> expectExceptionExcept(String name, Supplier<T> toEvaluate, Predicate<String> messagePredicate, String predicateHelp, Class<E> excludedType) {
           return ExceptionExcept.create(name, toEvaluate, messagePredicate, predicateHelp, excludedType);
       }
      public static <T, E extends Throwable> ExceptionExcept<T, E> expectExceptionExcept(String name, Supplier<T> toEvaluate, Predicate<String> messagePredicate, Class<E> excludedType) {
          return ExceptionExcept.create(name, toEvaluate, messagePredicate, excludedType);
      }
      public static <T, E extends Throwable> ExceptionExcept<T, E> expectExceptionExcept(String name, Supplier<T> toEvaluate, int timeoutOverride, Class<E> excludedType) {
           return ExceptionExcept.create(name, toEvaluate, timeoutOverride, excludedType);
       }
     // Add more timeout overloads if needed...


    /**
     * Creates a test expecting any exception *except* `UnsupportedOperationException`.
     * Delegates to {@link AnyExceptionButUnsupportedOperationExceptionFactory}.
     * Allows checking the message via exact match or predicate.
     *
     * @return An {@link ExceptionExcept}<T, UnsupportedOperationException> test instance.
     */
    public static <T> ExceptionExcept<T, UnsupportedOperationException> anyExceptionButUnsupportedOperationException(
            String name,
            Supplier<T> toEvaluate,
            Function<T, String> mkString,
            Optional<String> expectedMessage,
            Predicate<String> messagePredicate,
            Optional<String> predicateHelp,
            Optional<Integer> timeoutOverride) {
        return AnyExceptionButUnsupportedOperationExceptionFactory.create(name, toEvaluate, mkString, expectedMessage, messagePredicate, predicateHelp, timeoutOverride);
    }
     // Overloads for anyExceptionButUnsupportedOperationException
     public static <T> ExceptionExcept<T, UnsupportedOperationException> anyExceptionButUnsupportedOperationException(String name, Supplier<T> toEvaluate) {
          return AnyExceptionButUnsupportedOperationExceptionFactory.create(name, toEvaluate);
      }
     public static <T> ExceptionExcept<T, UnsupportedOperationException> anyExceptionButUnsupportedOperationException(String name, Supplier<T> toEvaluate, String expectedMessage) {
          return AnyExceptionButUnsupportedOperationExceptionFactory.create(name, toEvaluate, expectedMessage);
      }
      public static <T> ExceptionExcept<T, UnsupportedOperationException> anyExceptionButUnsupportedOperationException(String name, Supplier<T> toEvaluate, Predicate<String> messagePredicate, String predicateHelp) {
          return AnyExceptionButUnsupportedOperationExceptionFactory.create(name, toEvaluate, messagePredicate, predicateHelp);
      }
      public static <T> ExceptionExcept<T, UnsupportedOperationException> anyExceptionButUnsupportedOperationException(String name, Supplier<T> toEvaluate, Predicate<String> messagePredicate) {
          return AnyExceptionButUnsupportedOperationExceptionFactory.create(name, toEvaluate, messagePredicate);
      }
      public static <T> ExceptionExcept<T, UnsupportedOperationException> anyExceptionButUnsupportedOperationException(String name, Supplier<T> toEvaluate, int timeoutOverride) {
          return AnyExceptionButUnsupportedOperationExceptionFactory.create(name, toEvaluate, timeoutOverride);
      }
     // Add more timeout overloads if needed...

}
