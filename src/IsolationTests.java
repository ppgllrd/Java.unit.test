import test.unit.assertions.Assert;
import test.unit.assertions.Equal;
import test.unit.config.Config;
import test.unit.config.IsolationOptions;
import test.unit.config.TestIsolationMode;
import test.unit.core.Results;
import test.unit.core.SuiteItem;
import test.unit.core.Test;
import test.unit.core.TestResult;
import test.unit.core.TestSuite;
import test.unit.execution.ChildJvmCommandFactory;
import test.unit.execution.ExecutionOutcome;
import test.unit.execution.IsolatedTestWorker;
import test.unit.execution.PerTestJvmExecutionStrategy;
import test.unit.execution.TestExecutionRequest;
import test.unit.execution.TestLocator;
import test.unit.execution.TestSuiteProvider;
import test.unit.i18n.Language;
import test.unit.logging.Logger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Self-contained automated tests for SAME_JVM and PER_TEST_JVM (no external test framework needed).
 * Run with {@code java -cp <classes> IsolationTests}; exits with status 1 if any check fails.
 * Every case runs under an external watchdog so a bug in the implementation cannot hang the run.
 */
public class IsolationTests {

    // ------------------------------------------------------------------ fixtures (rebuilt in each child JVM)

    static int counter; // static state used to verify isolation

    static <T> T boom() {
        throw new IllegalStateException("boom");
    }

    public static final class Fixtures implements TestSuiteProvider {
        public Fixtures() {}

        @Override
        public List<TestSuite> suites() {
            return List.of(new TestSuite("Fixtures",
                Equal.create("pass", () -> 2 + 2, 4),
                Equal.create("fail", () -> 1 + 1, 3),
                Equal.create("unexpected", IsolationTests::<Integer>boom, 1),
                Assert.create("loop", () -> { while (true) { } }),
                Assert.create("ignore-interrupts", () -> {
                    while (true) {
                        if (Thread.currentThread().isInterrupted()) { /* deliberately ignored */ }
                    }
                }),
                Assert.create("swallow-interrupted", () -> {
                    while (true) {
                        try { Thread.sleep(20); } catch (InterruptedException ignored) { /* swallowed */ }
                    }
                }),
                Assert.create("deadlock", () -> {
                    Object a = new Object(), b = new Object();
                    Thread t1 = new Thread(() -> { synchronized (a) { pause(); synchronized (b) { } } });
                    Thread t2 = new Thread(() -> { synchronized (b) { pause(); synchronized (a) { } } });
                    t1.setDaemon(true); t2.setDaemon(true);
                    t1.start(); t2.start();
                    try { t1.join(); t2.join(); } catch (InterruptedException ignored) { /* swallowed */ }
                    return true;
                }),
                Assert.create("non-daemon", () -> {
                    Thread t = new Thread(() -> { while (true) { pause(); } });
                    t.setDaemon(false);
                    t.start();
                    return true;
                }),
                Assert.create("exit0", () -> { System.exit(0); return true; }),
                Assert.create("exit7", () -> { System.exit(7); return true; }),
                Assert.create("big-stdout", () -> {
                    String line = "0123456789".repeat(10);
                    for (int i = 0; i < 100_000; i++) System.out.println(line);
                    return true;
                }),
                Assert.create("big-stderr", () -> {
                    String line = "0123456789".repeat(10);
                    for (int i = 0; i < 100_000; i++) System.err.println(line);
                    return true;
                }),
                Equal.create("lookalike", () -> {
                    String fake = "version:MQ==\nstatus:UEFTU0VE\nend:abc\nRESULT PASSED";
                    System.out.println(fake);
                    System.err.println(fake);
                    System.out.println("h\u00e9llo \u20ac \uD83D\uDE00");
                    return 1;
                }, 2),
                Assert.create("static-inc", () -> { counter++; return true; }),
                Assert.create("static-check", () -> counter == 0),
                Assert.create("sysprop", () -> "hello world".equals(System.getProperty("isolation.test.prop")))
            ));
        }

        private static void pause() {
            try { Thread.sleep(100); } catch (InterruptedException ignored) { /* swallowed */ }
        }
    }

    public static final class Mixed implements TestSuiteProvider {
        public Mixed() {}

        @Override
        public List<TestSuite> suites() {
            return List.of(new TestSuite("Mixed",
                Assert.create("first", () -> true),
                Assert.create("exits", () -> { System.exit(7); return true; }),
                Assert.create("hangs", () -> { while (true) { } }, 1),
                Equal.create("wrong", () -> 1, 2),
                Assert.create("last", () -> true)));
        }
    }

    /** Child "worker" that finishes without writing any result. */
    public static final class NoResultMain {
        public static void main(String[] args) { }
    }

    /** Child "worker" that writes a corrupt result file. */
    public static final class CorruptMain {
        public static void main(String[] args) throws IOException {
            Files.writeString(Path.of(args[1]), "this is not the protocol\n\u0000\u0001", StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------ helpers

    static int failures = 0;
    static int checks = 0;

    static void check(boolean condition, String description) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("    FAILED CHECK: " + description);
        }
    }

    static Test testNamed(String name) {
        return new Fixtures().suites().get(0).getItems().stream()
                .filter(i -> i instanceof Test t && t.getName().equals(name))
                .map(i -> (Test) i).findFirst().orElseThrow();
    }

    static Config config(IsolationOptions options, int timeoutSeconds) {
        return new Config(new Logger.SilentLogger(), Language.ENGLISH, timeoutSeconds, false, options);
    }

    static IsolationOptions perTest() {
        return IsolationOptions.perTestJvm(Fixtures.class);
    }

    static ExecutionOutcome exec(String name, IsolationOptions options, int timeoutSeconds) {
        List<SuiteItem> items = new Fixtures().suites().get(0).getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof Test t && t.getName().equals(name)) {
                var locator = new TestLocator(Fixtures.class.getName(), "Fixtures", i, name, t.getClass().getName());
                return PerTestJvmExecutionStrategy.INSTANCE.execute(
                        new TestExecutionRequest(t, locator, config(options, timeoutSeconds)));
            }
        }
        throw new AssertionError("no such fixture: " + name);
    }

    static boolean dead(ExecutionOutcome o) {
        return o.childPid().isEmpty() || ProcessHandle.of(o.childPid().getAsLong()).map(p -> !p.isAlive()).orElse(true);
    }

    static String infra(TestResult r) {
        return r instanceof TestResult.InfrastructureFailure f ? f.kind() + ": " + f.detail() : "";
    }

    /** Records every logger event, as a listener would. */
    static final class RecordingLogger implements Logger {
        final List<String> started = new ArrayList<>();
        final List<TestResult> results = new ArrayList<>();
        public boolean supportsAnsiColors() { return false; }
        public void print(Object any) { }
        public void println(Object any) { }
        public void logStart(String testName, Config config) { started.add(testName.strip()); }
        public void logResult(TestResult result, Config config) { results.add(result); }
        public void flush() { }
    }

    interface Case { void run() throws Exception; }

    static void runCase(String name, Case c) {
        System.out.println("- " + name);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try { c.run(); } catch (Throwable e) { error.set(e); }
        }, "case-" + name);
        t.setDaemon(true);
        t.start();
        try {
            t.join(120_000); // external limit: a stuck implementation fails the case instead of hanging the run
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (t.isAlive()) { failures++; System.out.println("    FAILED: case exceeded its 120s external limit"); }
        else if (error.get() != null) { failures++; System.out.println("    FAILED with " + error.get()); error.get().printStackTrace(System.out); }
    }

    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    // ------------------------------------------------------------------ cases

    public static void main(String[] args) throws Exception {
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(15 * 60_000L); } catch (InterruptedException e) { return; }
            System.out.println("GLOBAL WATCHDOG EXPIRED");
            Runtime.getRuntime().halt(2);
        });
        watchdog.setDaemon(true);
        watchdog.start();

        Path work = Files.createTempDirectory("isolation-tests-work-");

        runCase("23. defaults preserve SAME_JVM", () -> {
            check(Config.DEFAULT.isolation().mode() == TestIsolationMode.SAME_JVM, "Config.DEFAULT is SAME_JVM");
            check(new Config().isolation().mode() == TestIsolationMode.SAME_JVM, "new Config() is SAME_JVM");
            check(new Config(new Logger.SilentLogger(), Language.SPANISH, 5, true).isolation().mode() == TestIsolationMode.SAME_JVM,
                    "legacy 4-argument constructor is SAME_JVM");
            Config chained = Config.DEFAULT.withTimeout(9)
                    .withCsvOutput(true)
                    .withLanguage(Language.SPANISH)
                    .withLogging(false)
                    .withIsolation(perTest());
            check(chained.timeout() == 9 && chained.csvOutput() && chained.language() == Language.SPANISH
                            && chained.logger() instanceof Logger.SilentLogger
                            && chained.isolation().mode() == TestIsolationMode.PER_TEST_JVM,
                    "with* methods chain and preserve settings");
            TestResult r = testNamed("pass").run(
                    config(IsolationOptions.DEFAULT, 3).withIsolation(IsolationOptions.DEFAULT.withMode(TestIsolationMode.PER_TEST_JVM)));
            check(r instanceof TestResult.InfrastructureFailure f && f.kind() == TestResult.InfrastructureFailure.Kind.CONFIGURATION_ERROR,
                    "PER_TEST_JVM without provider is a CONFIGURATION_ERROR, got " + r);
        });

        runCase("1,2,21. SAME_JVM pass / assertion failure / shared static state", () -> {
            Config c = config(IsolationOptions.DEFAULT, 5);
            check(testNamed("pass").run(c) instanceof TestResult.Success, "pass passes");
            check(testNamed("fail").run(c) instanceof TestResult.EqualityFailure<?>, "fail is an EqualityFailure");
            check(testNamed("unexpected").run(c) instanceof TestResult.UnexpectedExceptionFailure, "unexpected");
            check(testNamed("static-inc").run(c).isSuccess(), "static-inc");
            check(!testNamed("static-check").run(c).isSuccess(), "SAME_JVM shares static state: counter is 1");
        });

        runCase("3. PER_TEST_JVM pass", () -> {
            var o = exec("pass", perTest(), 20);
            check(o.result() instanceof TestResult.Success, "pass: " + o.result() + " " + o.stderr());
            check(dead(o) && o.childTerminated(), "child is gone");
        });

        runCase("4,5. PER_TEST_JVM assertion failure and unexpected exception", () -> {
            var f = exec("fail", perTest(), 20);
            check(f.result() instanceof TestResult.RemoteFailure rf && rf.kind() == TestResult.RemoteFailure.Kind.FAILED, "FAILED: " + f.result());
            var sameJvm = testNamed("fail").run(config(IsolationOptions.DEFAULT, 5));
            Config plain = config(IsolationOptions.DEFAULT, 5);
            check(f.result().message(plain).equals(sameJvm.message(plain)), "same message as SAME_JVM");

            var u = exec("unexpected", perTest(), 20);
            check(u.result() instanceof TestResult.RemoteFailure rf && rf.kind() == TestResult.RemoteFailure.Kind.ERROR
                    && rf.exceptionType().equals("java.lang.IllegalStateException")
                    && rf.exceptionMessage().equals("boom") && rf.stackTrace().contains("boom"), "ERROR: " + u.result());
        });

        for (String hang : List.of("loop", "ignore-interrupts", "swallow-interrupted", "deadlock")) {
            runCase("6-9. PER_TEST_JVM timeout: " + hang, () -> {
                long t0 = System.nanoTime();
                var o = exec(hang, perTest(), 2);
                long secs = (System.nanoTime() - t0) / 1_000_000_000L;
                check(o.result() instanceof TestResult.TimeoutFailure tf && tf.timeout() == 2, "TIMED_OUT: " + o.result() + infra(o.result()));
                check(dead(o) && o.childTerminated(), "child killed");
                check(secs < 30, "returned promptly (" + secs + "s)");
            });
        }

        runCase("6b. timeout with a grace period", () -> {
            var o = exec("loop", perTest().withGracePeriodMillis(300), 2);
            check(o.result() instanceof TestResult.TimeoutFailure, "TIMED_OUT: " + o.result());
            check(dead(o), "child killed");
        });

        runCase("10. non-daemon thread does not keep the child alive", () -> {
            var o = exec("non-daemon", perTest(), 10);
            check(o.result() instanceof TestResult.Success, "passes: " + o.result() + infra(o.result()));
            check(dead(o), "child gone");
        });

        runCase("11,12. System.exit(0) and System.exit(7) are not passes", () -> {
            var z = exec("exit0", perTest(), 20);
            check(z.result() instanceof TestResult.InfrastructureFailure f && f.kind() == TestResult.InfrastructureFailure.Kind.PROCESS_ERROR
                    && f.detail().contains("exit code 0"), "exit0 => PROCESS_ERROR: " + z.result());
            var n = exec("exit7", perTest(), 20);
            check(n.result() instanceof TestResult.InfrastructureFailure f && f.kind() == TestResult.InfrastructureFailure.Kind.PROCESS_ERROR
                    && f.detail().contains("exit code 7"), "exit7 => PROCESS_ERROR: " + n.result());
        });

        runCase("13,14,25. large stdout/stderr is truncated without blocking", () -> {
            var o = exec("big-stdout", perTest().withMaxOutputChars(1000), 30);
            check(o.result() instanceof TestResult.Success, "passes: " + o.result());
            check(o.stdout().length() == 1000 && o.stdoutTruncated() && !o.stderrTruncated(), "stdout truncated to 1000: " + o.stdout().length());
            var e = exec("big-stderr", perTest().withMaxOutputChars(1000), 30);
            check(e.result() instanceof TestResult.Success, "passes: " + e.result());
            check(e.stderr().length() == 1000 && e.stderrTruncated(), "stderr truncated to 1000: " + e.stderr().length());
            var all = exec("big-stdout", perTest().withMaxOutputChars(100_000_000), 30);
            check(all.result().isSuccess() && !all.stdoutTruncated() && all.stdout().length() > 10_000_000, "complete when limit is large");
        });

        runCase("15. output resembling the protocol cannot forge or alter the result; UTF-8 is preserved", () -> {
            var o = exec("lookalike", perTest(), 20);
            check(o.result() instanceof TestResult.RemoteFailure rf && rf.kind() == TestResult.RemoteFailure.Kind.FAILED, "still FAILED: " + o.result());
            check(o.stdout().contains("status:UEFTU0VE") && o.stderr().contains("RESULT PASSED"), "fake text is just output");
            check(o.stdout().contains("h\u00e9llo \u20ac \uD83D\uDE00"), "multibyte characters survive: " + o.stdout());
        });

        runCase("16,17. missing and corrupt results", () -> {
            for (var main : List.of(NoResultMain.class, CorruptMain.class)) {
                var opts = perTest().withCommandFactory(ChildJvmCommandFactory.standard(System.getProperty("java.class.path"), main.getName()));
                var o = exec("pass", opts, 20);
                check(o.result() instanceof TestResult.InfrastructureFailure f && f.kind() == TestResult.InfrastructureFailure.Kind.PROCESS_ERROR
                        && f.detail().contains("without a valid result"), main.getSimpleName() + " => PROCESS_ERROR: " + o.result());
            }
        });

        runCase("18. child JVM fails to start", () -> {
            var opts = perTest().withCommandFactory(req -> List.of(work.resolve("no such dir").resolve("java").toString()));
            var o = exec("pass", opts, 20);
            check(o.result() instanceof TestResult.InfrastructureFailure f && f.kind() == TestResult.InfrastructureFailure.Kind.PROCESS_ERROR
                    && f.detail().contains("failed to start the child JVM") && f.detail().contains("no such dir"), "PROCESS_ERROR: " + o.result());
            var bad = perTest().withJvmArgs(List.of("-XX:+ThisFlagDoesNotExist"));
            var b = exec("pass", bad, 20);
            check(b.result() instanceof TestResult.InfrastructureFailure f && f.kind() == TestResult.InfrastructureFailure.Kind.PROCESS_ERROR
                    && f.detail().contains("ThisFlagDoesNotExist"), "bad JVM flag is diagnosed with stderr: " + o.result() + b.result());
        });

        runCase("19. class path with spaces and JVM arguments with spaces", () -> {
            Path spaced = work.resolve("dir with spaces");
            List<String> entries = new ArrayList<>();
            int n = 0;
            for (String entry : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
                Path src = Path.of(entry);
                Path dst = spaced.resolve("cp " + n++);
                if (Files.isDirectory(src)) {
                    try (Stream<Path> s = Files.walk(src)) {
                        for (Path p : (Iterable<Path>) s::iterator) {
                            Path target = dst.resolve(src.relativize(p).toString());
                            if (Files.isDirectory(p)) Files.createDirectories(target);
                            else { Files.createDirectories(target.getParent()); Files.copy(p, target); }
                        }
                    }
                    entries.add(dst.toString());
                } else {
                    Files.createDirectories(dst);
                    entries.add(Files.copy(src, dst.resolve(src.getFileName())).toString());
                }
            }
            String cp = String.join(java.io.File.pathSeparator, entries);
            var opts = perTest()
                    .withCommandFactory(ChildJvmCommandFactory.standard(cp, IsolatedTestWorker.class.getName()))
                    .withJvmArgs(List.of("-Disolation.test.prop=hello world"));
            var o = exec("sysprop", opts, 30);
            check(o.result() instanceof TestResult.Success, "works from a path with spaces: " + o.result() + infra(o.result()) + o.stderr());
            var d = exec("sysprop", perTest(), 30);
            check(d.result() instanceof TestResult.RemoteFailure, "property absent without the JVM arg (control): " + d.result());
        });

        runCase("21. static state is isolated between children", () -> {
            check(exec("static-inc", perTest(), 20).result().isSuccess(), "static-inc");
            check(exec("static-check", perTest(), 20).result().isSuccess(), "fresh JVM: counter is 0");
        });

        runCase("20,24. suite continues after exit, timeout and failure; listeners and Results", () -> {
            var logger = new RecordingLogger();
            Config c = new Config(logger, Language.ENGLISH, 20, false, IsolationOptions.perTestJvm(Mixed.class));
            Results r = new Mixed().suites().get(0).run(c);
            check(r.getTotal() == 5 && r.getPassed() == 2 && r.getFailed() == 3, "counts " + r.getPassed() + "/" + r.getTotal());
            check(r.getDetails().equals("+---+"), "details " + r.getDetails());
            check(logger.started.equals(List.of("first", "exits", "hangs", "wrong", "last")), "logStart events " + logger.started);
            check(logger.results.size() == 5 && logger.results.get(2) instanceof TestResult.TimeoutFailure
                    && logger.results.get(1) instanceof TestResult.InfrastructureFailure, "logResult events");
            List<Results> all = TestSuite.runAll(c.withLogging(false), new Mixed().suites().get(0));
            check(all.size() == 1 && all.get(0).getPassed() == 2, "runAll works");

            var same = new RecordingLogger();
            Results rs = new TestSuite("S", testNamed("pass"), testNamed("fail"))
                    .run(new Config(same, Language.ENGLISH, 5, false));
            check(rs.getDetails().equals("+-") && same.results.size() == 2, "SAME_JVM suite unchanged");
        });

        runCase("22. temporary files are removed (success, failure, timeout, crash)", () -> {
            Path dir = Files.createTempDirectory(work, "tmpcheck-");
            var opts = perTest().withWorkDirectory(dir);
            exec("pass", opts, 20);
            exec("fail", opts, 20);
            exec("loop", opts, 2);
            exec("exit0", opts, 20);
            exec("pass", opts.withCommandFactory(req -> List.of("/definitely/not/java")), 20);
            try (Stream<Path> s = Files.list(dir)) {
                check(s.findAny().isEmpty(), "work directory is empty after all runs");
            }
        });

        runCase("parallel runs do not interfere", () -> {
            List<Thread> threads = new ArrayList<>();
            List<String> bad = java.util.Collections.synchronizedList(new ArrayList<>());
            for (String name : List.of("pass", "fail", "pass", "fail")) {
                Thread t = new Thread(() -> {
                    var o = exec(name, perTest(), 30);
                    if (o.result().isSuccess() != name.equals("pass")) bad.add(name + ": " + o.result());
                });
                threads.add(t);
                t.start();
            }
            for (Thread t : threads) t.join(120_000);
            check(bad.isEmpty(), "parallel results " + bad);
        });

        deleteTree(work);
        System.out.println();
        System.out.println(checks + " checks, " + failures + " failures");
        Runtime.getRuntime().halt(failures == 0 ? 0 : 1);
    }
}
