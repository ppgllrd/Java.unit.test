import test.unit.assertions.Property;
import test.unit.config.Config;
import test.unit.config.IsolationOptions;
import test.unit.core.Test;
import test.unit.core.TestFactory;
import test.unit.core.TestSuite;
import test.unit.execution.TestSuiteProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * An example TestSuite demonstrating how to use the Java testing library
 * to test various functionalities (including some aspects of the library itself).
 *
 * @author Pepe Gallardo & Gemini
 */

public class Example implements TestSuiteProvider {

  // --- Define Individual Tests using TestFactory ---

  // 1. Equality Tests
  Test equalTest1 = TestFactory.equal(
          /* name */ "Simple Addition",
          /* toEvaluate */ () -> 2 + 2,
          /* expected */ 4
  );

  Test equalTest2 = TestFactory.equal(
          /* name */ "String Equality",
          /* toEvaluate */ "hello"::toUpperCase,
          /* expected */ "HELLO"
  );

  Test equalTest3 = TestFactory.equal(
          /* name */ "Null Equality",
          /* toEvaluate */ () -> (String) null, // Explicit cast needed for lambda return type
          /* expected */ null
  );

  // Custom equality (ignore case)
  BiPredicate<String, String> ignoreCase = String::equalsIgnoreCase;
  Test equalByTest = TestFactory.equalBy(
          /* name */ "String Ignore Case",
          /* toEvaluate */ () -> "Java",
          /* expected */ "java",
          /* equalsFn */ ignoreCase
  );

  // 2. Boolean Assertion Tests
  Test assertTest1 = TestFactory.assertTest(
          /* name */ "Is Positive",
          /* toEvaluate */ () -> 5 > 0
  );

  Test refuteTest1 = TestFactory.refuteTest(
          /* name */ "Is Not Negative",
          /* toEvaluate */ () -> 5 < 0
  );

  Test refuteTest2 = TestFactory.refuteTest(
          /* name */ "String Is Not Empty",
          /* toEvaluate */ "Hello"::isEmpty
  );


  // 3. Property Tests
  Predicate<Integer> isEven = x -> x != null && x % 2 == 0;
  Test propertyTest1 = TestFactory.property(
          /* name */ "Number Is Even",
          /* toEvaluate */ () -> 10,
          /* property */ isEven,
          /* help */ "value should be divisible by 2" // Optional help message
  );

  Test propertyTest2 = TestFactory.property(
          /* name */ "List Not Empty Property",
          /* toEvaluate */ () -> List.of(1, 2, 3),
          /* property */ list -> !list.isEmpty() // Predicate using lambda
          // Using default mkString and no help message
  );


  // 4. Exception Tests

  // Expect specific type
  Test expectEx1 = TestFactory.expectException(
          /* name */ "Arithmetic Exception",
          /* toEvaluate */ () -> 1 / 0, // Code throwing the exception
          /* expectedType */ ArithmeticException.class
  );

  // Expect specific type and exact message
  Test expectEx2 = TestFactory.expectException(
          /* name */ "Illegal Argument Exception with Message",
          /* toEvaluate */ () -> {
            String arg = null;
            if (arg == null) throw new IllegalArgumentException("Argument must not be null");
            return arg;
          },
          /* expectedMessage */ "Argument must not be null",
          /* expectedType */ IllegalArgumentException.class
  );

  // Expect one of several types
  Supplier<Object> listAccess = () -> List.of("a").get(1); // Will throw IndexOutOfBounds
  Test expectOneOfEx = TestFactory.expectExceptionOneOf(
          /* name */ "Index Out Of Bounds or Null Pointer",
          /* toEvaluate */ listAccess,
          /* expectedTypes */ IndexOutOfBoundsException.class, NullPointerException.class
  );

  // Expect any exception *except* a specific type
  Test expectExceptEx = TestFactory.expectExceptionExcept(
          /* name */ "Any Except NullPointerException",
          /* toEvaluate */ () -> 1 / 0, // Throws ArithmeticException
          /* excludedType */ NullPointerException.class
  );

  // Expect any exception *except* UnsupportedError
  Test anyButNIETest = TestFactory.anyExceptionButUnsupportedOperationException(
          /* name */ "Any But NIE (Using ArithmeticEx)",
          /* toEvaluate */ () -> 1 / 0
  );

  // 5. Test with Timeout (should pass quickly)
  Test quickTest = TestFactory.assertTest(
          /* name */ "Quick Calculation",
          /* toEvaluate */ () -> Math.pow(2, 10) == 1024,
          /* timeoutOverride */ Optional.of(1) // Set explicit 1 second timeout override
  );

  // Test with Timeout (should not pass)
  Test infiniteTest = TestFactory.assertTest(
          /* name */ "Infinite Calculation",
          /* toEvaluate */ () -> { while (true) { } },
          /* timeoutOverride */ Optional.of(1) // Set explicit 1 second timeout override
  );

  @Override
  public List<TestSuite> suites() {
    // --- Create the Test Suite ---
    TestSuite librarySuite = new TestSuite(
            "Core Testing Library Features", // Suite Name
            // Add all defined tests here
            equalTest1,
            equalTest2,
            equalTest3,
            equalByTest,
            assertTest1,
            refuteTest1,
            refuteTest2,
            propertyTest1,
            propertyTest2,
            expectEx1,
            expectEx2,
            expectOneOfEx,
            expectExceptEx,
            anyButNIETest,
            quickTest,
            infiniteTest
    );
    return List.of(librarySuite);
  }

  public static void main(String[] args) {
    Example provider = new Example();
    Config config = Config.DEFAULT.withTimeout(1);

    if (!Arrays.asList(args).contains("--no-isolation")) {
      config = config.withIsolation(IsolationOptions.pooledJvm(Example.class));
    }

    java.util.List<TestSuite> suites = provider.suites();

    java.util.Set<String> selectedSuites = new java.util.HashSet<>();
    for (String arg : args) {
      if (arg.startsWith("--suite=")) {
        String suiteName = arg.substring("--suite=".length());
        if (suiteName.isBlank()) {
          throw new IllegalArgumentException("Suite name cannot be empty");
        }
        if (suites.stream().noneMatch(suite -> suite.getName().equals(suiteName))) {
          throw new IllegalArgumentException("No suite named '" + suiteName + "'");
        }
        selectedSuites.add(suiteName);
      }
    }

    if (!selectedSuites.isEmpty()) {
      suites = suites.stream()
              .filter(suite -> selectedSuites.contains(suite.getName()))
              .toList();
    }

    System.out.println("Running Test Suite for the Testing Library...");
    TestSuite.runAll(config, suites.toArray(TestSuite[]::new));
    System.out.println("\nTest Suite Execution Finished.");
  }
}
