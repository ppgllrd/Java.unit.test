package test.unit.core;

import test.unit.config.Config;
import test.unit.config.TestIsolationMode;
import test.unit.execution.WorkerPool;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Resources of one run of suites: the thread pool for parallel execution and the JVM worker pool, if any. */
final class RunScope implements AutoCloseable {
    private final ExecutorService executor; // null when sequential
    private final WorkerPool pool;          // null unless POOLED_JVM

    RunScope(Config config) {
        int parallelism = config.parallelism();
        if (parallelism > 1) {
            var counter = new AtomicInteger();
            executor = Executors.newFixedThreadPool(parallelism, r -> {
                Thread t = new Thread(r, "test-unit-runner-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            });
        } else {
            executor = null;
        }
        var isolation = config.isolation();
        pool = isolation.mode() == TestIsolationMode.POOLED_JVM && isolation.suiteProvider() != null
                ? new WorkerPool(isolation, parallelism) : null;
    }

    ExecutorService executor() { return executor; }

    WorkerPool pool() { return pool; }

    @Override
    public void close() {
        try {
            if (executor != null) {
                executor.shutdownNow();
                try {
                    executor.awaitTermination(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } finally {
            if (pool != null) pool.close();
        }
    }
}
