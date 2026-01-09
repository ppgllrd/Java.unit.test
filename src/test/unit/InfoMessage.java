// Create a new file: InfoMessage.java
package test.unit;

import java.util.Objects;

/**
 * A suite item that simply prints a message to the logger when run.
 * It is not a test and does not produce a TestResult.
 */
public final class InfoMessage implements SuiteItem {

    private final String message;

    public InfoMessage(String message) {
        this.message = Objects.requireNonNull(message, "Message cannot be null");
    }

    public static InfoMessage create(String message) {
        return new InfoMessage(message);
    }

    /**
     * Prints the message to the logger using a distinct style.
     * @param config The configuration containing the logger.
     */
    public void print(Config config) {
        var logger = config.logger();
        // Use a distinct color like blue or cyan to differentiate from test names
        logger.println(" "+logger.blue(this.message));
        logger.flush();
    }
}