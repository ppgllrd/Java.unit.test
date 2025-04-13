package test.unit;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;


/**
 * Abstract base class for tests that verify an exception is thrown when evaluating an expression.
 * It handles the common logic for asynchronous execution, timeout, exception catching,
 * and checking the type and message of the thrown exception against expectations.
 * <p>
 * Subclasses must provide the specific logic for checking the exception type ({@code throwablePredicate})
 * and define how the expectation description ({@code helpKey}, {@code helpArgs}) is constructed.
 *
 * @param <T> The return type of the {@code toEvaluate} block (if it were to complete normally, which indicates a test failure).
 * @author Pepe Gallardo & Gemini
 */
public abstract class ExceptionBy<T> extends Test {

    protected final Supplier<T> toEvaluate;
    protected final Function<T, String> mkString;
    protected final Predicate<Throwable> throwablePredicate;
    protected final Optional<String> expectedMessage;
    protected final Predicate<String> messagePredicate;
    protected final Optional<String> predicateHelp;
    protected final String helpKey;
    protected final List<HelpArg> helpArgs;

    /**
     * Protected constructor for ExceptionBy tests. Use static factories in subclasses.
     *
     * @param name Test name.
     * @param toEvaluate Supplier expected to throw.
     * @param mkString Function to format result `T` if no exception is thrown.
     * @param throwablePredicate Predicate returning `true` if the thrown exception type is acceptable.
     * @param expectedMessage Optional exact message the thrown exception must have. Takes priority over `messagePredicate`.
     * @param messagePredicate Predicate applied to the thrown message if `expectedMessage` is empty.
     * @param predicateHelp Optional human-readable description of the `messagePredicate`.
     * @param helpKey Localization key for the overall description of the expectation.
     * @param helpArgs List of {@link HelpArg} arguments for formatting the `helpKey` message.
     * @param timeoutOverride Optional timeout override.
     */
    protected ExceptionBy(String name,
                          Supplier<T> toEvaluate,
                          Function<T, String> mkString,
                          Predicate<Throwable> throwablePredicate,
                          Optional<String> expectedMessage,
                          Predicate<String> messagePredicate,
                          Optional<String> predicateHelp,
                          String helpKey,
                          List<HelpArg> helpArgs,
                          Optional<Integer> timeoutOverride) {
        super(name, timeoutOverride);
        this.toEvaluate = Objects.requireNonNull(toEvaluate, "Supplier 'toEvaluate' cannot be null");
        this.mkString = Objects.requireNonNull(mkString, "Function 'mkString' cannot be null");
        this.throwablePredicate = Objects.requireNonNull(throwablePredicate, "Predicate 'throwablePredicate' cannot be null");
        this.expectedMessage = Objects.requireNonNull(expectedMessage, "Optional 'expectedMessage' cannot be null");
        this.messagePredicate = Objects.requireNonNull(messagePredicate, "Predicate 'messagePredicate' cannot be null");
        this.predicateHelp = Objects.requireNonNull(predicateHelp, "Optional 'predicateHelp' cannot be null");
        this.helpKey = Objects.requireNonNull(helpKey, "String 'helpKey' cannot be null");
        this.helpArgs = List.copyOf(Objects.requireNonNull(helpArgs, "List 'helpArgs' cannot be null")); // Ensure immutable

        // Validate consistency: if exact message is absent, predicate must be provided (unless default is sufficient)
        // And if predicate is used, help text is highly recommended for clarity.
         if (expectedMessage.isEmpty() && predicateHelp.isEmpty() && messagePredicate == DEFAULT_MSG_PREDICATE) {
              // This scenario (no exact msg, default predicate, no help) is valid, but less informative.
              // Consider adding a warning log here if desired.
         }
         if (expectedMessage.isPresent() && !messagePredicate.equals(DEFAULT_MSG_PREDICATE)) {
             // This is also valid, but potentially confusing as the predicate will be ignored.
             // Consider adding a warning log here if desired.
         }
    }

    // Default predicate that accepts any message
    protected static final Predicate<String> DEFAULT_MSG_PREDICATE = msg -> true;

    /**
     * Generates a formatted, localized string describing the overall expectation of this exception test.
     * It uses the `helpKey` and `helpArgs` provided to the constructor, applying appropriate
     * formatting (like coloring) to the arguments based on their {@link HelpArg} type.
     *
     * @param config The configuration context providing localization and logger.
     * @return The formatted help string describing the expected exception scenario.
     */
    protected String formattedHelp(Config config) {
        var logger = config.logger();
        var orConnector = config.msg("connector.or"); // Localized " or "

        // Process each HelpArg, applying appropriate formatting/coloring
        Object[] processedArgs = helpArgs.stream().map(arg -> {
            // Use instanceof pattern matching (Java 16+) or switch (Java 17+)
          return switch (arg) {
            case HelpArg.TypeName tn -> logger.green(tn.name());
            case HelpArg.TypeNameList tnl -> tnl.names().stream()
                .map(logger::green)
                .collect(Collectors.joining(orConnector));
            case HelpArg.ExactMessage em -> logger.green("\"" + em.message() + "\"");
            case HelpArg.PredicateHelp ph -> logger.green(ph.text());
          };
        }).toArray(); // Convert stream to Object array for String.format

        // Format the localized message pattern (`helpKey`) with the processed arguments
        return config.msg(helpKey, processedArgs);
    }

    /**
     * Checks if the `actualMessage` matches the expectation.
     * If `expectedMessage` was defined (is present), it performs an exact string comparison.
     * Otherwise, it applies the `messagePredicate`.
     *
     * @param actualMessage The message string from the thrown exception (or "null" if the message was null). Must not be null itself.
     * @return {@code true} if the message meets the expectation, {@code false} otherwise.
     */
    protected final boolean checkMessage(String actualMessage) {
        Objects.requireNonNull(actualMessage, "actualMessage cannot be null for checkMessage");
        return expectedMessage
            .map(expected -> expected.equals(actualMessage)) // Check exact match if present
            .orElseGet(() -> messagePredicate.test(actualMessage)); // Otherwise, use predicate
    }

    /**
     * Executes the core logic of the exception test asynchronously.
     */
    @Override
    protected TestResult executeTest(Config config) {
        final String currentFormattedHelp = formattedHelp(config); // Generate once before async
        final String withExpectedCurrentFormattedHelp = config.msg("expected", currentFormattedHelp);

        CompletableFuture<TestResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                T result = toEvaluate.get(); // Evaluate the potentially exception-throwing code
                // If we reach here, no exception was thrown, which is a failure.
                return new TestResult.NoExceptionFailure<>(result, mkString, currentFormattedHelp);
            } catch (Throwable thrown) {
                // An exception was thrown. Now check type and message.
                if (thrown instanceof InterruptedException) {
                    Thread.currentThread().interrupt(); // Preserve interrupt status
                     // Treat interrupt during evaluation as unexpected for this test type
                    return new TestResult.UnexpectedExceptionFailure(thrown, currentFormattedHelp);
                }

                boolean passedType = throwablePredicate.test(thrown); // Check if the type is acceptable
                String actualMessage = String.valueOf(thrown.getMessage()); // Handle null messages safely
                boolean passedMessage = checkMessage(actualMessage); // Check if the message is acceptable

                if (passedType && passedMessage) {
                    // Both type and message match expectations.
                    return new TestResult.Success();
                } else if (!passedType) {
                    // Type mismatch. Message may or may not match.
                    if (!passedMessage) {
                        return new TestResult.WrongExceptionAndMessageFailure(thrown, currentFormattedHelp);
                    } else {
                        return new TestResult.WrongExceptionTypeFailure(thrown, currentFormattedHelp);
                    }
                } else { // Passed Type, Failed Message (!passedMessage must be true here)
                    // Type matched, but the message did not. Generate a detailed message failure reason.
                    var logger = config.logger();
                    Optional<String> detailMessageOpt = expectedMessage
                        .map(exactMsg -> config.msg(
                                "detail.expected_exact_message",
                                logger.green("\"" + exactMsg + "\"") // Show the expected message (colored green)
                        ))
                        .or(() -> predicateHelp.map(help -> config.msg( // Use or() on Optional
                                "detail.expected_predicate",
                                logger.green(help) // Show the predicate help text (colored green)
                        )));

                    // Return the failure result, including the specific detail about why the message failed.
                    return new TestResult.WrongExceptionMessageFailure(thrown, currentFormattedHelp, detailMessageOpt);
                }
            }
        });

        try {
            // Wait for the future to complete, respecting the timeout
            return future.get(config.timeout(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return new TestResult.TimeoutFailure(config.timeout(), withExpectedCurrentFormattedHelp);
        } catch (ExecutionException e) {
             Throwable cause = (e.getCause() != null) ? e.getCause() : e;
              if (cause instanceof InterruptedException) {
                  Thread.currentThread().interrupt();
              }
             // Treat exceptions during future execution itself as unexpected
             return new TestResult.UnexpectedExceptionFailure(cause, currentFormattedHelp);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
             return new TestResult.UnexpectedExceptionFailure(e, currentFormattedHelp);
        } catch (CancellationException e) {
             return new TestResult.TimeoutFailure(config.timeout(), withExpectedCurrentFormattedHelp);
        }
    }
}
