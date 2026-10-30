# Test isolation: `SAME_JVM`, `PER_TEST_JVM` and `POOLED_JVM`

## Modes

| Mode | Behaviour |
|------|-----------|
| `SAME_JVM` (**default**) | Every test runs in the JVM that runs the suite, exactly as before (async evaluation, `Future.get(timeout)`, interruption). Fast, but a test can hang a thread, loop forever, call `System.exit` or leave static state behind. |
| `PER_TEST_JVM` | Every test runs in its own freshly started child JVM. The parent enforces the timeout and kills the child with `destroyForcibly()`. |

Existing code needs no change: `Config.DEFAULT`, `new Config()` and the 4-argument `Config` constructor all mean `SAME_JVM`.
Library classes are organized under `test.unit` subpackages such as `config`, `core`, `assertions` and `execution`; use imports from those packages.

## Configuration

Tests are lambdas and cannot be sent to another JVM. Each child JVM therefore **rebuilds the suites** through a
`TestSuiteProvider` and runs the single test identified by a `TestLocator`
(provider class, suite name, item index, test name, test class).

```java
import test.unit.assertions.Equal;
import test.unit.config.Config;
import test.unit.config.IsolationOptions;
import test.unit.core.TestSuite;
import test.unit.execution.TestSuiteProvider;

import java.util.List;

public class MySuites implements TestSuiteProvider {          // public class, public no-arg constructor
    @Override public List<TestSuite> suites() {
        return List.of(new TestSuite("Core", Equal.create("2+2", () -> 2 + 2, 4) /* ... */));
    }
}

public static void main(String[] args) {
    IsolationOptions isolation = IsolationOptions.perTestJvm(MySuites.class)
            .withGracePeriodMillis(200)            // destroy() first, destroyForcibly() after 200 ms (default 0)
            .withMaxOutputChars(50_000)            // per test and per stream (default 100 000)
            .withJvmArgs(List.of("-Xmx256m"));     // one list element per argument
    Config config = Config.DEFAULT.withIsolation(isolation).withTimeout(5);
    TestSuite.runAll(config, new MySuites().suites().toArray(TestSuite[]::new));
}
```

Requirements: `suites()` must be deterministic and suite names unique within the provider. Use the same provider in the
parent to obtain the suites you run. Running a test with `PER_TEST_JVM` outside a `TestSuite` (plain `Test.run`),
or without a `suiteProvider`, yields a `CONFIGURATION_ERROR` result.

Other options: `withCommandFactory(...)` (see below), `withWorkDirectory(path)` (parent of the per-test temporary directories).

## Speed: `POOLED_JVM` and `parallelism`

Starting one JVM per test is slow with many tests. Two independent options reduce that cost:

* **`POOLED_JVM`** (`IsolationOptions.pooledJvm(Provider.class)`): tests run in a pool of long-lived worker JVMs that load
  the suite provider once. Needs a `suiteProvider`, like `PER_TEST_JVM`. A worker that times out, crashes, calls
  `System.exit` or has run `maxTestsPerWorker` tests (`withMaxTestsPerWorker(n)`, `0` = unlimited, the default) is replaced.
  Caveats: static state is shared by the tests of the same worker (use `withMaxTestsPerWorker(1)` for a fresh JVM per test);
  stdout/stderr are captured through `System.out/err` (native output is not); the timeout counts from sending the
  test, not JVM start-up (which has a fixed 60 s limit); a timed-out test loses its captured output;
  `workDirectory` is ignored.
* **`Config.withParallelism(n)`** (default 1): runs up to `n` tests concurrently, in any mode. Output is still reported in
  suite order. In `POOLED_JVM` the pool has `n` workers; in `PER_TEST_JVM`, up to `n` child JVMs. With `SAME_JVM`
  the tests share the JVM and static state (they must be thread-safe) and their console output may interleave.
  Timeouts count from when a test starts running, not from when it was queued.

```java
Config config = Config.DEFAULT
        .withIsolation(IsolationOptions.pooledJvm(MySuites.class))
        .withParallelism(4);
```

## Timeout semantics

The timeout (per-test override, else `Config.timeout()`) is measured with `System.nanoTime()` from just before the
child process is started until it has written a complete result **and exited**. It therefore includes JVM startup, class
loading, provider construction, the test and result writing. Choose timeouts with JVM startup (typically 0.1–1 s) in mind.
On expiry: the child is terminated (optional `destroy()` during the grace period, then `destroyForcibly()`), the parent waits at
most 5 s for it to die and verifies `isAlive()`, streams are closed, temporary files deleted, and the result is `TimeoutFailure`.
If the child cannot be verified dead, the result is a `PROCESS_ERROR` saying so, and the runner continues. The library's own
in-JVM timeout inside the child is set far above the deadline, so the parent is always the one enforcing it.

## Results

Child → parent protocol (version 1): the parent sends a request through the child's **stdin**; the child writes a versioned
`key:base64` text file in a private, unique temporary directory (atomically renamed, closed by an `end` marker echoing a random
per-run nonce). Stdout/stderr are never parsed. The parent checks existence, completeness, version, nonce, test id and that the
exit code agrees with the reported status. Exit code 0 alone never means "passed".

| Situation | `TestResult` |
|-----------|--------------|
| passed | `Success` |
| assertion/property/equality failure | `RemoteFailure` (`FAILED`; same rendered message as in `SAME_JVM`) |
| unexpected exception | `RemoteFailure` (`ERROR`, with exception type, message, stack trace) |
| deadline exceeded | `TimeoutFailure` |
| JVM did not start; no/corrupt/inconsistent result (also `System.exit(0)`); worker crash | `InfrastructureFailure(PROCESS_ERROR)` |
| bad options, provider missing/non-deterministic | `InfrastructureFailure(CONFIGURATION_ERROR)` |

Captured stdout/stderr (UTF-8, bounded by `maxOutputChars`, still fully drained afterwards, flagged when truncated) are printed
through the logger after the result, and available programmatically via `PerTestJvmExecutionStrategy.INSTANCE.execute(...)`
→ `ExecutionOutcome`. All failures count as failed in `Results`.

## Differences from `SAME_JVM`

* **Performance**: one JVM start per test (roughly 0.1–1 s each) versus microseconds.
* **State**: static state, system properties, threads, files in memory are not shared between tests; every child starts clean.
* **Lifecycle**: the library has no fixtures, `@Before/@After`, class-level setup or parameterised tests; every test is an
  independent lambda, so nothing that should run "once" is run once per test. The only repeated work is building the suites in
  the provider (once per child JVM): keep it cheap and side-effect free. Closures captured by a test are recreated in the child,
  not transferred, so mutations made in the parent after building the suite are not seen.
* **Output**: the child's stdout/stderr are shown after the test result instead of interleaved.
* **Results**: `RemoteFailure` carries text, not the original `Throwable` or the compared values.

## Child JVM command, class path, modules

By default the child is `<java.home>/bin/java` (`java.exe` on Windows) with `-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8
-Dstderr.encoding=UTF-8`, your extra JVM args, `-cp <java.class.path>` and, if `jdk.module.path` is set, `--module-path` plus
`--add-modules=ALL-MODULE-PATH`. Every argument is a separate `ProcessBuilder` element (no shell, no quoting), so spaces in paths are fine.
Works the same from Maven/Gradle forks, IDEs, scripts and executable JARs (the JAR is `java.class.path`). When that is not
enough (custom launcher, very long class paths on Windows, another runtime) implement `ChildJvmCommandFactory`:

```java
IsolationOptions.perTestJvm(MySuites.class).withCommandFactory(req -> List.of(
        "/opt/jdk/bin/java", "-cp", "libs/*:classes", IsolatedTestWorker.class.getName(),
        "1", req.resultFile().toString()));   // the worker takes <protocolVersion> <resultFile>
```

## Diagnosing start-up failures

`PROCESS_ERROR: failed to start the child JVM ...` shows the full command (arguments with spaces are quoted for display only)
and the cause. `... without a valid result ...` includes the exit code and the tail of the child's stderr (bad JVM flag,
`ClassNotFoundException` for the provider, test called `System.exit`, JVM crash). `CONFIGURATION_ERROR` explains provider problems.

## Limitations and security

* `PER_TEST_JVM` gives process isolation and strong termination of the test's JVM. It is **not a security sandbox**: the test runs
  with the same OS user, file system, network and environment as the parent and could, for example, tamper with other files.
  The nonce and private directory protect against accidents (printing look-alike output, `System.exit(0)`, leftovers), not against
  deliberately hostile code. For hostile code or hard limits on **processes, memory, CPU, disk, network or system access** use
  external isolation (containers, cgroups, Windows Job Objects, sandboxing) around the whole runner.
* Killing the child through the standard Java API does **not** portably guarantee that external processes started by the test
  disappear. The library snapshots `ProcessHandle.descendants()` when the timeout fires and kills those on a best-effort basis; this is
  subject to races (processes spawned during the snapshot, detached/re-parented processes) and OS differences. Nothing is done
  for descendants once the child has exited on its own.
* Only portable Java APIs are used (Linux, Windows, macOS); no OS-specific code.
* `destroyForcibly()` only starts termination; the parent waits (bounded) and verifies.

## Build and run the tests

```sh
cd <repo>
mkdir -p out
javac -d out $(find src -name '*.java')              # Windows (PowerShell): javac -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
java -cp out IsolationTests                          # exit status 0 = all checks passed (takes about a minute)
java -cp out Example                                 # existing example (SAME_JVM)
```
