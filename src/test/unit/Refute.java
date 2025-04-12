package test.unit;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A specific type of test that verifies a boolean expression evaluates to {@code false}.
 * It is implemented as a specialized {@link Property} test where the property is {@code !x}.
 *
 * @author Pepe Gallardo & Gemini
 */
public class Refute extends Property<Boolean> {

    /**
     * Private constructor. Use static factory methods.
     */
    private Refute(String name,
                  Supplier<Boolean> toEvaluate,
                  Optional<Integer> timeoutOverride) {
        super(name,
              toEvaluate,
              result -> result != null && !result, // Property: value must be non-null false
              Optional.empty(), // Use key-based mkString
              Optional.of(v -> "property.was." + v), // mkStringKey: "property.was.true" or "property.was.false"
              Optional.empty(), // Use key-based help
              Optional.of("property.must.be.false"), // helpKey
              timeoutOverride);
         Objects.requireNonNull(toEvaluate, "Supplier 'toEvaluate' cannot be null");
    }

    // --- Static Factory Methods ---

    /**
     * Creates a `Refute` test instance.
     *
     * @param name The descriptive name of the test case.
     * @param toEvaluate The supplier providing the boolean expression. Must evaluate to `false` to pass.
     * @param timeoutOverride Optional duration in seconds to override the default timeout.
     * @return A {@link Refute} test instance.
     */
    public static Refute create(String name,
                                Supplier<Boolean> toEvaluate,
                                Optional<Integer> timeoutOverride) {
        return new Refute(name, toEvaluate, timeoutOverride);
    }

    public static Refute create(String name, Supplier<Boolean> toEvaluate) {
        return create(name, toEvaluate, Optional.empty());
    }

    public static Refute create(String name,
                                Supplier<Boolean> toEvaluate,
                                int timeoutOverride) {
        return create(name, toEvaluate, Optional.of(timeoutOverride));
    }
}
