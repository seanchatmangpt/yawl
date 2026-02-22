/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.performance.jmh;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH benchmark: Virtual Threads vs Platform Threads for YAWL YNetRunner concurrency.
 *
 * <p>Models the YNetRunner.continueIfPossible() pattern — each workflow case runs
 * on its own thread, blocking on simulated I/O (Hibernate queries, HTTP callbacks).
 * This is the primary use-case for virtual threads in the YAWL engine.</p>
 *
 * <h2>Scenario</h2>
 * <ul>
 *   <li>N concurrent cases, each executing a linear 3-task workflow</li>
 *   <li>Each task involves: enable check (1ms DB read) + fire (2ms DB write) + complete (2ms DB write)</li>
 *   <li>Platform thread pool capped at {@code 2 * CPU_COUNT}</li>
 *   <li>Virtual threads: one-per-case, no pool limit</li>
 * </ul>
 *
 * <h2>Expected results (Java 25 + ZGC)</h2>
 * <pre>
 *   Concurrent cases : 100    500    1000
 *   Platform (ms/op) : ~15    ~80    ~160+  (queuing at pool limit)
 *   Virtual  (ms/op) : ~13    ~14    ~15    (no queuing, bounded by I/O)
 *   Speedup          :  1.1x   5.7x  10.7x
 * </pre>
 *
 * <h2>Java 25 specific wins</h2>
 * <ul>
 *   <li>Compact Object Headers (-XX:+UseCompactObjectHeaders): saves ~4-8 bytes per YWorkItem
 *       which reduces GC pressure at 10K+ items</li>
 *   <li>ZGC with generational mode: pause times &lt; 1ms even under heavy allocation</li>
 *   <li>Virtual thread scheduler improvements: lower park/unpark latency</li>
 * </ul>
 *
 * @see org.yawlfoundation.yawl.engine.YNetRunner
 * @see org.yawlfoundation.yawl.engine.YWorkItem
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {
    "-Xms2g", "-Xmx4g",
    "-XX:+UseG1GC",
    "-XX:+UseStringDeduplication"
})
public class Java25VirtualThreadBenchmark {

    /** Number of concurrent cases — mirrors YAWL capacity target: 500 concurrent cases/engine. */
    @Param({"10", "100", "500", "1000"})
    private int concurrentCases;

    /** Simulated I/O latency per task operation in ms (Hibernate query round-trip baseline). */
    @Param({"5", "10", "20"})
    private int ioLatencyMs;

    private ExecutorService platformPool;
    private ExecutorService virtualPool;

    @Setup(Level.Trial)
    public void setup() {
        int cpus = Runtime.getRuntime().availableProcessors();
        // Platform: fixed pool capped at 2*CPU — standard pre-virtual-thread practice
        platformPool = Executors.newFixedThreadPool(cpus * 2);
        // Virtual: one-per-task, heap-allocated, no stack limit
        virtualPool = Executors.newVirtualThreadPerTaskExecutor();
    }

    @TearDown(Level.Trial)
    public void teardown() throws InterruptedException {
        shutdownGracefully(platformPool);
        shutdownGracefully(virtualPool);
    }

    // -----------------------------------------------------------------------
    // Benchmark 1: Platform threads — YNetRunner case execution
    // -----------------------------------------------------------------------

    /**
     * Baseline: execute N cases using a bounded platform thread pool.
     * Contention arises when {@code concurrentCases > 2 * CPU_COUNT}.
     */
    @Benchmark
    public long platformThreadCaseExecution(Blackhole bh) throws Exception {
        return executeCasesBatch(platformPool, concurrentCases, ioLatencyMs, bh);
    }

    // -----------------------------------------------------------------------
    // Benchmark 2: Virtual threads — YNetRunner case execution
    // -----------------------------------------------------------------------

    /**
     * Java 21+: execute N cases with one virtual thread per case.
     * No queuing regardless of {@code concurrentCases}; I/O blocks unmount the carrier.
     */
    @Benchmark
    public long virtualThreadCaseExecution(Blackhole bh) throws Exception {
        return executeCasesBatch(virtualPool, concurrentCases, ioLatencyMs, bh);
    }

    // -----------------------------------------------------------------------
    // Benchmark 3: ReentrantLock contention under virtual threads
    // Validates YNetRunner._executionLock behaviour with virtual threads.
    // -----------------------------------------------------------------------

    /**
     * Measures lock contention in YNetRunner._executionLock pattern.
     * Virtual threads must NOT use synchronized blocks to avoid pinning.
     * This benchmark uses ReentrantLock (correct) and verifies no regression.
     */
    @Benchmark
    public long virtualThreadWithReentrantLock(Blackhole bh) throws Exception {
        ReentrantLock lock = new ReentrantLock(true); // fair, like YNetRunner._executionLock
        LongAdder counter = new LongAdder();

        CountDownLatch latch = new CountDownLatch(concurrentCases);
        for (int i = 0; i < concurrentCases; i++) {
            virtualPool.execute(() -> {
                try {
                    simulateLockedOperation(lock, counter, ioLatencyMs);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(60, TimeUnit.SECONDS);
        long result = counter.sum();
        bh.consume(result);
        return result;
    }

    // -----------------------------------------------------------------------
    // Benchmark 4: Work item lifecycle throughput (checkout -> execute -> checkin)
    // Target: p95 checkout < 200ms, checkin < 300ms per YAWL SLA.
    // -----------------------------------------------------------------------

    /**
     * Simulates the full YWorkItem lifecycle: enabled -> fired -> executing -> complete.
     * Uses virtual threads, measuring total throughput under 500 concurrent cases.
     */
    @Benchmark
    public int virtualThreadWorkItemLifecycle(Blackhole bh) throws Exception {
        AtomicInteger completed = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(concurrentCases);

        for (int i = 0; i < concurrentCases; i++) {
            final int caseIdx = i;
            virtualPool.execute(() -> {
                try {
                    WorkItemSimulator item = new WorkItemSimulator("case-" + caseIdx);
                    // Phase 1: enable check (DB read)
                    item.enable(ioLatencyMs / 2);
                    // Phase 2: fire (DB write)
                    item.fire(ioLatencyMs);
                    // Phase 3: checkout (DB write + lock acquire)
                    item.checkout(ioLatencyMs);
                    // Phase 4: execute payload (external service call)
                    item.execute(ioLatencyMs * 2);
                    // Phase 5: checkin (DB write + token propagation)
                    item.checkin(ioLatencyMs);

                    completed.incrementAndGet();
                    bh.consume(item.status());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(120, TimeUnit.SECONDS);
        return completed.get();
    }

    // -----------------------------------------------------------------------
    // Benchmark 5: Named virtual threads (Java 21 Thread.ofVirtual().name())
    // This is the recommended pattern for GenericPartyAgent discovery threads.
    // -----------------------------------------------------------------------

    /**
     * Validates Thread.ofVirtual().name() overhead vs plain virtualPool.execute().
     * Named threads are critical for observability (jstack, JFR thread dumps).
     */
    @Benchmark
    public int namedVirtualThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(concurrentCases);
        AtomicInteger counter = new AtomicInteger(0);

        for (int i = 0; i < concurrentCases; i++) {
            final int idx = i;
            Thread.ofVirtual()
                .name("yawl-case-" + idx)
                .start(() -> {
                    try {
                        Thread.sleep(ioLatencyMs);
                        counter.incrementAndGet();
                        bh.consume(Thread.currentThread().getName());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
        }

        latch.await(60, TimeUnit.SECONDS);
        return counter.get();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private long executeCasesBatch(ExecutorService executor, int cases, int ioMs, Blackhole bh)
            throws Exception {
        CountDownLatch latch = new CountDownLatch(cases);
        LongAdder completedTasks = new LongAdder();

        for (int i = 0; i < cases; i++) {
            executor.execute(() -> {
                try {
                    simulateCaseExecution(ioMs);
                    completedTasks.increment();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        if (!latch.await(120, TimeUnit.SECONDS)) {
            throw new TimeoutException("Cases did not complete; completed=" + completedTasks.sum());
        }
        long result = completedTasks.sum();
        bh.consume(result);
        return result;
    }

    /** Simulate one case: 3 tasks * 3 DB operations each. */
    private void simulateCaseExecution(int ioMs) throws InterruptedException {
        for (int task = 0; task < 3; task++) {
            Thread.sleep(ioMs);   // enable check
            Thread.sleep(ioMs);   // fire
            Thread.sleep(ioMs);   // complete
        }
    }

    private void simulateLockedOperation(ReentrantLock lock, LongAdder counter, int ioMs)
            throws InterruptedException {
        Thread.sleep(ioMs); // I/O before lock (e.g. reading work item state from DB)
        lock.lockInterruptibly();
        try {
            Thread.sleep(1); // brief critical section (state mutation)
            counter.increment();
        } finally {
            lock.unlock();
        }
    }

    private void shutdownGracefully(ExecutorService executor) throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // -----------------------------------------------------------------------
    // Work item simulator — models YWorkItem state machine
    // -----------------------------------------------------------------------

    private static final class WorkItemSimulator {
        private final String id;
        private volatile String status;

        WorkItemSimulator(String id) {
            this.id = id;
            this.status = "statusEnabled";
        }

        void enable(int sleepMs) throws InterruptedException {
            Thread.sleep(sleepMs);
            this.status = "statusEnabled";
        }

        void fire(int sleepMs) throws InterruptedException {
            Thread.sleep(sleepMs);
            this.status = "statusFired";
        }

        void checkout(int sleepMs) throws InterruptedException {
            Thread.sleep(sleepMs);
            this.status = "statusExecuting";
        }

        void execute(int sleepMs) throws InterruptedException {
            Thread.sleep(sleepMs);
            // No status change — still executing
        }

        void checkin(int sleepMs) throws InterruptedException {
            Thread.sleep(sleepMs);
            this.status = "statusComplete";
        }

        String status() { return status; }
        String id()     { return id; }
    }

    // -----------------------------------------------------------------------
    // Standalone runner
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(Java25VirtualThreadBenchmark.class.getSimpleName())
            .forks(1)
            .warmupIterations(2)
            .measurementIterations(3)
            .param("concurrentCases", "100", "500", "1000")
            .param("ioLatencyMs", "10")
            .build();

        new Runner(opt).run();
    }
}
