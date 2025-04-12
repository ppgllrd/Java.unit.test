package test.unit;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A specific type of test that verifies a boolean expression evaluates to {@code true}.
 * It is implemented as a specialized {@link Property} test.
 *
 * @author Pepe Gallardo & Gemini
 */
public class Assert extends Property<Boolean> {

    /**
     * Private constructor. Use static factory methods.
     */
    private Assert(String name,
                  Supplier<Boolean> toEvaluate,
                  Optional<Integer> timeoutOverride) {
        super(name,
              toEvaluate,
              result -> result != null && result, // Property: value must be non-null true
              Optional.empty(), // Use key-based mkString
              Optional.of(v -> "property.was." + v), // mkStringKey: "property.was.true" or "property.was.false"
              Optional.empty(), // Use key-based help
              Optional.of("property.must.be.true"), // helpKey
              timeoutOverride);
         Objects.requireNonNull(toEvaluate, "Supplier 'toEvaluate' cannot be null");
    }

    // --- Static Factory Methods ---

    /**
     * Creates an `Assert` test instance.
     *
     * @param name The descriptive name of the test case.
     * @param toEvaluate The supplier providing the boolean expression. Must evaluate to `true` to pass.
     * @param timeoutOverride Optional duration in seconds to override the default timeout.
     * @return An {@link Assert} test instance.
     */
    public static Assert create(String name,
                                Supplier<Boolean> toEvaluate,
                                Optional<Integer> timeoutOverride) {
        return new Assert(name, toEvaluate, timeoutOverride);
    }

    public static Assert create(String name, Supplier<Boolean> toEvaluate) {
        return create(name, toEvaluate, Optional.empty());
    }

    public static Assert create(String name,
                                Supplier<Boolean> toEvaluate,
                                int timeoutOverride) {
        return create(name, toEvaluate, Optional.of(timeoutOverride));
    }
}
