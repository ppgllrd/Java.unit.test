package test.unit;

import java.util.List;
import java.util.Objects;

/**
 * Represents distinct types of arguments used in constructing localized and
 * formatted descriptive messages (help strings) for exception tests
 * (subclasses of {@link ExceptionBy}).
 * <p>
 * Using these specific types allows the formatting logic (e.g., in {@link ExceptionBy#formattedHelp(Config)})
 * to apply appropriate styling (like colors) and joining logic based on the
 * argument's semantic meaning, decoupling it from the raw values.
 * This is implemented as a Java Sealed Interface with Record implementations.
 *
 * @author Pepe Gallardo & Gemini
 */
public sealed interface HelpArg
    permits HelpArg.TypeName, HelpArg.TypeNameList, HelpArg.ExactMessage, HelpArg.PredicateHelp {

    /** Represents a single exception type name (e.g., "IOException"). */
    record TypeName(String name) implements HelpArg {
        public TypeName { Objects.requireNonNull(name, "name cannot be null"); }
    }

    /**
     * Represents a list of multiple exception type names. These are typically
     * joined together using a localized connector (like " or ") during formatting.
     * (e.g., List.of("IOException", "SQLException")).
     */
    record TypeNameList(List<String> names) implements HelpArg {
         public TypeNameList {
             Objects.requireNonNull(names, "names list cannot be null");
             // Ensure the list is immutable and non-null elements
             names = List.copyOf(Objects.requireNonNull(names));
             if (names.stream().anyMatch(Objects::isNull)) {
                 throw new NullPointerException("names list cannot contain null elements");
             }
             if (names.isEmpty()) {
                 throw new IllegalArgumentException("names list cannot be empty");
             }
         }
    }

    /**
     * Represents an exact exception message string that was expected.
     * Formatting usually includes quoting the message (e.g., "\"Invalid input\"").
     */
    record ExactMessage(String message) implements HelpArg {
         public ExactMessage { Objects.requireNonNull(message, "message cannot be null"); }
    }

    /**
     * Represents the human-readable descriptive text associated with an
     * exception message predicate.
     */
    record PredicateHelp(String text) implements HelpArg {
         public PredicateHelp { Objects.requireNonNull(text, "text cannot be null"); }
    }
}
