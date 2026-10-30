package test.unit.execution;

import test.unit.config.IsolationOptions;
import test.unit.config.TestIsolationMode;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the command line (executable plus one element per argument, never a shell string) used to start the
 * child JVM of {@link TestIsolationMode#PER_TEST_JVM}. Implement it to customize the invocation when the
 * default (current {@code java.class.path} / {@code jdk.module.path}) is not appropriate, e.g. for an
 * executable JAR layout, a different Java runtime or an unusual launcher.
 *
 * @author Pepe Gallardo & Gemini
 */
@FunctionalInterface
public interface ChildJvmCommandFactory {

    /**
     * Describes the child JVM to start.
     *
     * @param resultFile File where the worker must write its result; passed as the last argument to the main class.
     * @param jvmArgs Extra JVM arguments configured in {@link IsolationOptions#jvmArgs()}.
     */
    record Request(Path resultFile, List<String> jvmArgs) {
        public Request {
            Objects.requireNonNull(resultFile, "resultFile cannot be null");
            jvmArgs = List.copyOf(jvmArgs);
        }
    }

    /** Returns the complete command: executable first, then every argument as a separate element. */
    List<String> command(Request request);

    /** Default factory: current Java runtime, {@code java.class.path} and (if present) {@code jdk.module.path}. */
    static ChildJvmCommandFactory standard() {
        return standard(System.getProperty("java.class.path", ""), IsolatedTestWorker.class.getName());
    }

    /** Like {@link #standard()} but with an explicit class path and worker main class (mostly for custom setups). */
    static ChildJvmCommandFactory standard(String classPath, String workerMainClass) {
        Objects.requireNonNull(classPath, "classPath cannot be null");
        Objects.requireNonNull(workerMainClass, "workerMainClass cannot be null");
        return request -> {
            List<String> command = new ArrayList<>();
            command.add(javaExecutable().toString());
            command.add("-Dfile.encoding=UTF-8");
            command.add("-Dstdout.encoding=UTF-8");
            command.add("-Dstderr.encoding=UTF-8");
            String modulePath = System.getProperty("jdk.module.path");
            if (modulePath != null && !modulePath.isEmpty()) {
                command.add("--module-path");
                command.add(modulePath);
                command.add("--add-modules=ALL-MODULE-PATH");
            }
            command.addAll(request.jvmArgs());
            command.add("-cp");
            command.add(classPath);
            command.add(workerMainClass);
            command.add(Integer.toString(IsolationProtocol.VERSION));
            command.add(request.resultFile().toString());
            return command;
        };
    }

    /** Locates {@code <java.home>/bin/java} ({@code java.exe} on Windows). */
    static Path javaExecutable() {
        boolean windows = File.separatorChar == '\\';
        return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
    }
}
