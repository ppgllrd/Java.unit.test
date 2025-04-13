package test.unit;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A specific type of test that verifies if the result of an evaluated expression
 * is equal to an expected value using the standard equality method ({@code Objects.equals}).
 * <p>
 * It extends {@link EqualBy}, providing {@code Objects.equals(a, b)} as the equality function.
 *
 * @param <T> The type of the value produced by the expression and the expected value.
 * @author Pepe Gallardo & Gemini
 */
public class Equal<T> extends EqualBy<T> {

    /**
     * Protected constructor for Equal test. Use static factory methods for creation.
     */
    protected Equal(String name,
                    Supplier<T> toEvaluate,
                    T expected,
                    Function<T, String> mkString,
                    Optional<Integer> timeoutOverride) {
        super(name,
              toEvaluate,
              expected,
              Objects::equals, // Use standard Objects.equals for null-safe comparison
              mkString,
              timeoutOverride);
    }

    // --- Static Factory Methods ---

    private static <T> Function<T, String> defaultMkString() {
         return obj -> Objects.toString(obj, "null");
    }

    /**
     * Creates an `Equal` test instance using standard {@code Objects.equals} comparison.
     *
     * @param name Test name.
     * @param toEvaluate Supplier for the expression to evaluate.
     * @param expected Expected value (compared using {@code Objects.equals}).
     * @param mkString Function to convert `T` to String. Defaults to {@code Objects.toString}.
     * @param timeoutOverride Optional specific timeout in seconds.
     * @param <T> Type of the expression and result.
     * @return An `Equal<T>` test instance.
     */
    public static <T> Equal<T> create(String name,
                                      Supplier<T> toEvaluate,
                                      T expected,
                                      Function<T, String> mkString,
                                      Optional<Integer> timeoutOverride) {
        return new Equal<>(name, toEvaluate, expected, mkString, timeoutOverride);
    }

    // Overloads for convenience

    public static <T> Equal<T> create(String name,
                                      Supplier<T> toEvaluate,
                                      T expected) {
        return create(name, toEvaluate, expected, defaultMkString(), Optional.empty());
    }

     public static <T> Equal<T> create(String name,
                                       Supplier<T> toEvaluate,
                                       T expected,
                                       Function<T, String> mkString) {
         return create(name, toEvaluate, expected, mkString, Optional.empty());
     }

    public static <T> Equal<T> create(String name,
                                      Supplier<T> toEvaluate,
                                      T expected,
                                      Optional<Integer> timeoutOverride) {
        return create(name, toEvaluate, expected, defaultMkString(), timeoutOverride);
    }

    public static <T> Equal<T> create(String name,
                                      Supplier<T> toEvaluate,
                                      T expected,
                                      int timeoutOverride) {
        return create(name, toEvaluate, expected, defaultMkString(), Optional.of(timeoutOverride));
    }

     public static <T> Equal<T> create(String name,
                                       Supplier<T> toEvaluate,
                                       T expected,
                                       Function<T, String> mkString,
                                       int timeoutOverride) {
         return create(name, toEvaluate, expected, mkString, Optional.of(timeoutOverride));
     }
}
