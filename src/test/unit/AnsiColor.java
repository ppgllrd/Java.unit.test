package test.unit;

/**
 * Provides utilities for embedding ANSI escape codes in strings
 * to produce colored and styled console output.
 *
 * @author Pepe Gallardo & Gemini
 */
public final class AnsiColor {

    // Private constructor to prevent instantiation
    private AnsiColor() {}

    // --- Private ANSI Escape Codes ---
    private static final String RESET = "\u001B[0m";
    private static final String BLACK = "\u001B[30m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";
    private static final String BOLD = "\u001B[1m";
    private static final String UNDERLINE = "\u001b[4m";

    /**
     * Conditionally wraps the given text with ANSI color/style codes.
     * If coloring is disabled, the original text is returned unchanged.
     * The text is always reset to default formatting at the end.
     *
     * @param text The text to be colored or styled.
     * @param color The ANSI escape code string for the desired color or style.
     * @param enabled If true, apply the color/style; otherwise, return the original text.
     * @return The text, potentially wrapped with ANSI codes.
     */
    private static String colored(String text, String color, boolean enabled) {
        return enabled ? (color + text + RESET) : text;
    }

    // --- Public Coloring/Styling Methods ---

    /** Wraps text in red ANSI codes if enabled. */
    public static String red(String text, boolean enabled) {
        return colored(text, RED, enabled);
    }
    public static String red(String text) { return red(text, true); }

    /** Wraps text in green ANSI codes if enabled. */
    public static String green(String text, boolean enabled) {
        return colored(text, GREEN, enabled);
    }
    public static String green(String text) { return green(text, true); }

    /** Wraps text in yellow ANSI codes if enabled. */
    public static String yellow(String text, boolean enabled) {
        return colored(text, YELLOW, enabled);
    }
    public static String yellow(String text) { return yellow(text, true); }

    /** Wraps text in cyan ANSI codes if enabled. */
    public static String cyan(String text, boolean enabled) {
        return colored(text, CYAN, enabled);
    }
    public static String cyan(String text) { return cyan(text, true); }

    /** Wraps text in blue ANSI codes if enabled. */
    public static String blue(String text, boolean enabled) {
        return colored(text, BLUE, enabled);
    }
    public static String blue(String text) { return blue(text, true); }

    /** Wraps text in bold ANSI codes if enabled. */
    public static String bold(String text, boolean enabled) {
        return colored(text, BOLD, enabled);
    }
    public static String bold(String text) { return bold(text, true); }

    /** Wraps text in underline ANSI codes if enabled. */
    public static String underline(String text, boolean enabled) {
        return colored(text, UNDERLINE, enabled);
    }
    public static String underline(String text) { return underline(text, true); }
}
