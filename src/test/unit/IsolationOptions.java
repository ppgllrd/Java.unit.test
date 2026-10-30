package test.unit;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Options controlling how tests are isolated. Carried by {@link Config#isolation()}.
 * The default ({@link #DEFAULT}) is {@link TestIsolationMode#SAME_JVM}, i.e. the classic behaviour.
 *
 * @param mode Isolation mode.
 * @param suiteProvider Fully qualified name of a {@link TestSuiteProvider}; required (only) for PER_TEST_JVM.
 * @param gracePeriodMillis In PER_TEST_JVM, time given to the child to exit after a normal termination request
 *                          ({@code Process.destroy()}) before {@code destroyForcibly()}. 0 means kill immediately.
 * @param maxOutputChars In PER_TEST_JVM, maximum number of characters kept from each of stdout and stderr of a test.
 *                       The streams keep being drained after the limit; the excess is discarded and flagged as truncated.
 * @param jvmArgs In PER_TEST_JVM, extra JVM arguments for the child (e.g. {@code -Xmx256m}); one element per argument.
 * @param commandFactory In PER_TEST_JVM, custom builder of the child command; {@code null} uses
 *                       {@link ChildJvmCommandFactory#standard()}.
 * @param workDirectory In PER_TEST_JVM, parent directory for the per-test temporary directories; {@code null}
 *                      uses the system temporary directory.
 * @author Pepe Gallardo & Gemini
 */
public record IsolationOptions(
        TestIsolationMode mode,
        String suiteProvider,
        long gracePeriodMillis,
        int maxOutputChars,
        List<String> jvmArgs,
        ChildJvmCommandFactory commandFactory,
        Path workDirectory
) {
    public static final long DEFAULT_GRACE_PERIOD_MILLIS = 0;
    public static final int DEFAULT_MAX_OUTPUT_CHARS = 100_000;

    public IsolationOptions {
        Objects.requireNonNull(mode, "mode cannot be null");
        if (suiteProvider != null && suiteProvider.isBlank()) {
            throw new IllegalArgumentException("suiteProvider cannot be blank");
        }
        if (gracePeriodMillis < 0) {
            throw new IllegalArgumentException("gracePeriodMillis must not be negative");
        }
        if (maxOutputChars < 0) {
            throw new IllegalArgumentException("maxOutputChars must not be negative");
        }
        Objects.requireNonNull(jvmArgs, "jvmArgs cannot be null");
        jvmArgs = List.copyOf(jvmArgs); // rejects null elements
    }

    /** Default options: SAME_JVM. */
    public IsolationOptions() {
        this(TestIsolationMode.SAME_JVM, null, DEFAULT_GRACE_PERIOD_MILLIS, DEFAULT_MAX_OUTPUT_CHARS,
                List.of(), null, null);
    }

    /** The default options (SAME_JVM). */
    public static final IsolationOptions DEFAULT = new IsolationOptions();

    /** PER_TEST_JVM options using the given provider class name and defaults for everything else. */
    public static IsolationOptions perTestJvm(String suiteProvider) {
        Objects.requireNonNull(suiteProvider, "suiteProvider cannot be null");
        return DEFAULT.withMode(TestIsolationMode.PER_TEST_JVM).withSuiteProvider(suiteProvider);
    }

    /** PER_TEST_JVM options using the given provider class. */
    public static IsolationOptions perTestJvm(Class<? extends TestSuiteProvider> provider) {
        return perTestJvm(provider.getName());
    }

    public IsolationOptions withMode(TestIsolationMode newMode) {
        return new IsolationOptions(newMode, suiteProvider, gracePeriodMillis, maxOutputChars, jvmArgs, commandFactory, workDirectory);
    }

    public IsolationOptions withSuiteProvider(String provider) {
        return new IsolationOptions(mode, provider, gracePeriodMillis, maxOutputChars, jvmArgs, commandFactory, workDirectory);
    }

    public IsolationOptions withGracePeriodMillis(long millis) {
        return new IsolationOptions(mode, suiteProvider, millis, maxOutputChars, jvmArgs, commandFactory, workDirectory);
    }

    public IsolationOptions withMaxOutputChars(int chars) {
        return new IsolationOptions(mode, suiteProvider, gracePeriodMillis, chars, jvmArgs, commandFactory, workDirectory);
    }

    public IsolationOptions withJvmArgs(List<String> args) {
        return new IsolationOptions(mode, suiteProvider, gracePeriodMillis, maxOutputChars, args, commandFactory, workDirectory);
    }

    public IsolationOptions withCommandFactory(ChildJvmCommandFactory factory) {
        return new IsolationOptions(mode, suiteProvider, gracePeriodMillis, maxOutputChars, jvmArgs, factory, workDirectory);
    }

    public IsolationOptions withWorkDirectory(Path directory) {
        return new IsolationOptions(mode, suiteProvider, gracePeriodMillis, maxOutputChars, jvmArgs, commandFactory, directory);
    }
}
