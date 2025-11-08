// Import static factory methods for convenience
import static test.unit.TestFactory.*;

// Import necessary classes from the library
import test.unit.Config;
import test.unit.Language;
import test.unit.TestSuite;
import test.unit.Logger;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Test suite specifically designed to verify the error reporting
 * of the different exception testing classes when failures occur.
 *
 * @author Pepe Gallardo & Gemini
 */
public class ExceptionTestSuite {

  // --- Helper methods to produce specific outcomes ---

  private static String noThrow() {
    return "Success";
  }

  // Overload with default message
  private static String throwArg() {
    return throwArg("Illegal Argument");
  }
  private static String throwArg(String msg) {
    throw new IllegalArgumentException(msg);
  }

  // Overload with default message
  private static String throwRuntime() {
    return throwRuntime("Runtime Error");
  }
  private static String throwRuntime(String msg) {
    throw new RuntimeException(msg);
  }

  static class IOException extends RuntimeException {
    public IOException(String msg) {
      super(msg);
    }
  }

  // Overload with default message
  private static String throwIO() {
    return throwIO("IO Error");
  }
  private static String throwIO(String msg) {
    throw new IOException(msg);
  }


  static class SQLException extends RuntimeException {
    public SQLException(String msg) {
      super(msg);
    }
  }

  // Overload with default message
  private static String throwSQL() {
    return throwSQL("SQL Error");
  }

  private static String throwSQL(String msg) {
    throw new SQLException(msg);
  }

  // Overload with default message
  private static String throwUnsupported() {
    return throwUnsupported("Not Implemented");
  }
  private static String throwUnsupported(String msg) {
    // Uses the custom error class defined in the library
    throw new UnsupportedOperationException(msg);
  }

  static class FileNotFoundException extends IOException {
    public FileNotFoundException(String msg) {
      super(msg);
    }
  }

  // Overload with default message
  private static String throwFileNotFound()  {
    return throwFileNotFound("File Not Found");
  }
  private static String throwFileNotFound(String msg) throws FileNotFoundException {
    throw new FileNotFoundException(msg);
  }

  // --- Slow helper methods ---

  // Overload with default message and delay
  private static String slowThrowArg() {
    return slowThrowArg("Slow Illegal Argument", 150);
  }
  private static String slowThrowArg(String msg, int delayMs) {
    sleepHandlingInterrupt(delayMs);
    return throwArg(msg); // Re-use throwing method
  }

  // Overload with default delay
  private static String slowNoThrow() {
    return slowNoThrow(150);
  }
  private static String slowNoThrow(int delayMs) {
    sleepHandlingInterrupt(delayMs);
    return noThrow();
  }

  // Utility to sleep and handle interrupt gracefully for tests
  private static void sleepHandlingInterrupt(int delayMs) {
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); // Preserve interrupt status
      // Optional: Wrap in a runtime exception if interruption should fail the test setup itself
      // throw new RuntimeException("Test helper interrupted during sleep", e);
      System.err.println("WARN: Test helper sleep interrupted");
    }
  }


  public static void main(String[] args) {

    System.out.println("Running Exception Test Error Reporting Suite...");

    // --- Test Suite Definition ---
    TestSuite exceptionTests = new TestSuite(
        /* name */ "Exception Test Error Reporting",

        // === Testing `expectException` (delegates to ExceptionFactory/ExceptionOneOf) ===

        expectException(
            /* name */ "expectException: No Exception Thrown",
            /* toEvaluate */ (Supplier<String>) ExceptionTestSuite::noThrow, // Method reference
            /* expectedType */ IllegalArgumentException.class
            // Should fail: no exception
        ),
        expectException(
            /* name */ "expectException: Wrong Exception Type",
            /* toEvaluate */ (Supplier<String>) ExceptionTestSuite::throwRuntime,
            /* expectedType */ IllegalArgumentException.class
            // Should fail: wrong type
        ),
        expectException(
            /* name */ "expectException: Correct Type, Wrong Exact Message",
            /* toEvaluate */ (Supplier<String>) () -> throwArg("Actual message"), // Lambda
            /* expectedMessage */ "Expected message",
            /* expectedType */ IllegalArgumentException.class
            // Should fail: wrong message
        ),
        expectException(
            /* name */ "expectException: Timeout",
            /* toEvaluate */ (Supplier<String>) () -> slowThrowArg("Irrelevant", 2000),
            /* timeoutOverride */ 1, // Timeout in seconds
            /* expectedType */ IllegalArgumentException.class
            // Should fail: timeout (throws correct type, but too slow)
        ),

        // === Testing `expectExceptionOneOf` ===

        expectExceptionOneOf(
            /* name */ "expectOneOf: No Exception Thrown",
            /* toEvaluate */ (Supplier<String>) ExceptionTestSuite::noThrow,
            /* expectedTypes */ IOException.class, SQLException.class
            // Should fail: no exception
        ),
        expectExceptionOneOf(
            /* name */ "expectOneOf: Wrong Exception Type (Not in Set)",
            /* toEvaluate */ (Supplier<String>) ExceptionTestSuite::throwArg, // IllegalArgumentException is not IO or SQL
            /* expectedTypes */ IOException.class, SQLException.class
            // Should fail: wrong type
        ),
        expectExceptionOneOf(
            /* name */ "expectOneOf: Correct Type (Subclass), Wrong Exact Message",
            /* toEvaluate */ (Supplier<String>) () -> throwFileNotFound("Actual FNFE"), // FNFE is subclass of IO
            /* expectedMessage */ "Expected message",
            /* expectedTypes */ IOException.class, SQLException.class
            // Should fail: wrong message
        ),
        expectExceptionOneOf(
            /* name */ "expectOneOf: Correct Type, Wrong Exact Message",
            /* toEvaluate */ (Supplier<String>) () -> throwSQL("Actual SQL message"),
            /* expectedMessage */ "Expected message",
            /* expectedTypes */ IOException.class, SQLException.class
            // Should fail: wrong message
        ),
        expectExceptionOneOf(
            /* name */ "expectOneOf: Correct Type, Failed Predicate",
            /* toEvaluate */ (Supplier<String>) () -> throwIO("Actual IO message"),
            /* messagePredicate */ (Predicate<String>) msg -> msg.startsWith("Expected"), // Cast needed for clarity/overload resolution sometimes
            /* predicateHelp */ "starts with 'Expected'",
            /* expectedTypes */ IOException.class, SQLException.class
            // Should fail: predicate fails
        ),
        expectExceptionOneOf(
            /* name */ "expectOneOf: Timeout",
            /* toEvaluate */ (Supplier<String>) () -> slowThrowArg("Irrelevant type", 2000), // Throws wrong type, but timeout occurs first
            /* timeoutOverride */ 1, // Timeout in seconds
            /* expectedTypes */ IOException.class, SQLException.class
            // Should fail: timeout
        ),

        // === Testing `expectExceptionExcept` ===

        expectExceptionExcept(
            /* name */ "expectExcept: No Exception Thrown",
            /* toEvaluate */ (Supplier<String>) ExceptionTestSuite::noThrow,
            /* excludedType */ IOException.class
            // Should fail: no exception
        ),
        expectExceptionExcept(
            /* name */ "expectExcept: Excluded Type Thrown",
            /* toEvaluate */ (Supplier<String>) () -> throwIO("Throwing the excluded type"),
            /* excludedType */ IOException.class
            // Should fail: type predicate fails (threw the excluded type)
        ),
        expectExceptionExcept(
            /* name */ "expectExcept: Allowed Type, Wrong Exact Message",
            /* toEvaluate */ (Supplier<String>) () -> throwSQL("Actual SQL message"), // SQLException is allowed
            /* expectedMessage */ "Expected message",
            /* excludedType */ IOException.class
            // Should fail: wrong message
        ),
        expectExceptionExcept(
            /* name */ "expectExcept: Allowed Type, Failed Predicate",
            /* toEvaluate */ (Supplier<String>) () -> throwSQL("Actual SQL message"),    // SQLException is allowed
            /* messagePredicate */ (Predicate<String>) msg -> msg.contains("Expected"),
            /* predicateHelp */ "contains 'Expected'",
            /* excludedType */ IOException.class
            // Should fail: predicate fails
        ),
        expectExceptionExcept(
            /* name */ "expectExcept: Timeout",
            /* toEvaluate */ (Supplier<String>) () -> slowThrowArg("Irrelevant", 2000), // Type is allowed, but timeout
            /* timeoutOverride */ 1, // Timeout in seconds
            /* excludedType */ IOException.class
            // Should fail: timeout
        ),

        // === Testing `anyExceptionButNotImplementedError` ===

        anyExceptionButUnsupportedOperationException(
            /* name */ "anyExceptUOE: No Exception Thrown",
            /* toEvaluate */ (Supplier<String>) ExceptionTestSuite::noThrow
            // Should fail: no exception
        ),
        anyExceptionButUnsupportedOperationException(
            /* name */ "anyExceptUOE: Excluded Type (UOE) Thrown",
            /* toEvaluate */ (Supplier<String>) () -> throwUnsupported("Throwing UOE")
            // Should fail: type predicate fails (threw the excluded type)
        ),
        anyExceptionButUnsupportedOperationException(
            /* name */ "anyExceptUOE: Allowed Type, Wrong Exact Message",
            /* toEvaluate */ (Supplier<String>) () -> throwArg("Actual Arg message"), // Arg is allowed
            /* expectedMessage */ "Expected message"
            // Should fail: wrong message
        ),
        anyExceptionButUnsupportedOperationException(
            /* name */ "anyExceptUOE: Allowed Type, Failed Predicate",
            /* toEvaluate */ (Supplier<String>) () -> throwRuntime("Actual Runtime message"), // Runtime is allowed
            /* messagePredicate */ (Predicate<String>) msg -> msg.startsWith("X"),
            /* predicateHelp */ "starts with X"
            // Should fail: predicate fails
        ),
        anyExceptionButUnsupportedOperationException(
            /* name */ "anyExceptUOE: Timeout",
            /* toEvaluate */ (Supplier<String>) () -> slowThrowArg("Irrelevant", 2000), // Type is allowed, but timeout
            /* timeoutOverride */ 1 // Timeout in seconds
            // Should fail: timeout
        )
    ); // End of TestSuite definition

    // --- Run the Test Suite ---
    // Define your configuration (or use Config.DEFAULT)
    // Using English for potentially clearer error message comparison if needed
    Config config = new Config(new Logger.AnsiConsoleLogger(), Language.ENGLISH, 3, false);
    // Or use default: Config config = Config.DEFAULT;

    System.out.println("\nStarting Test Suite Run...\n");
    TestSuite.runAll(config, exceptionTests); // Pass config explicitly
    System.out.println("\nTest Suite Run Complete.");
  }
}