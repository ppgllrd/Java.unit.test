package test.unit.core;

// Create a new file: SuiteItem.java
/**
 * A sealed marker interface for any item that can be included in a TestSuite.
 * This permits either a Test or an informational message.
 */
public sealed interface SuiteItem permits Test, InfoMessage {
    // This interface can be empty. Its purpose is to unify different types.
}