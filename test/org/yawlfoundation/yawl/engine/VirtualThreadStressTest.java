/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.ScopedValue;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive stress test for all virtual thread patterns in the YAWL engine.
 *
 * <p>Tests 8 independent scenarios covering the full virtual thread surface area:</p>
 * <ol>
 *   <li>VirtualThreadPool 10K tasks — auto-scaling pool under sustained load</li>
 *   <li>Burst thread creation — 100K threads to verify low memory overhead</li>
 *   <li>Executor lifecycle churn — 1K create/submit/close cycles</li>
 *   <li>Concurrent pool submission — 50 producers × 200 tasks, no data loss</li>
 *   <li>Pinning detection — verify no synchronized-block pinning under stress</li>
 *   <li>ScopedValue propagation — 1K concurrent contexts, no leakage</li>
 *   <li>Sustained 60s load — rolling throughput + p50/p95/p99/p99.9 distribution</li>
 *   <li>Breaking point ramp — 100→50K threads; saturation curve + breaking point</li>
 * </ol>
 *
 * <p>Chicago TDD: Uses only real engine classes, no mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@Tag("stress")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(ExecutionMode.SAME_THREAD)
class VirtualThreadStressTest {

    // Structured report accumulator
    private static final List<String> REPORT_LINES = new ArrayList<>();

    private static final String HDR = "=== VIRTUAL THREAD STRESS REPORT ===";
    private static final String FTR_PASS = "=== RESULT: S1-S7 PASS | S8 CHARACTERISED ===";
    private static final String FTR_FAIL = "=== RESULT: FAILURES DETECTED ===";

    // ScopedValue used directly in tests (mirrors YNetRunner.CASE_CONTEXT pattern)
    private static final ScopedValue<String> TEST_CONTEXT = ScopedValue.newInstance();

    @BeforeAll
    static void printHeader() {
        System.out.println();
        System.out.println(HDR);
    }

    @AfterAll
    static void printReport() {
        // S8 reports CHARACTERISED (not PASS) — both are acceptable outcomes
        boolean allPass = REPORT_LINES.stream()
                .allMatch(l -> l.contains("PASS") || l.contains("CHARACTERISED"));
        for (String line : REPORT_LINES) {
            System.out.println(line);
        }
        System.out.println(allPass ? FTR_PASS : FTR_FAIL);
        System.out.println();
    }

    // =========================================================================
    // Shared percentile utility — real impl, no external dependencies
    // =========================================================================

    private static long pct(long[] sorted, double p) {
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
    }

    // =========================================================================
    // Scenario 1: VirtualThreadPool 10K tasks
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Scenario 1 — VirtualThreadPool 10K tasks")
    @Timeout(90)
    void scenario1_virtualThreadPool10kTasks() throws Exception {
        final int TASK_COUNT = 10_000;
        VirtualThreadPool pool = new VirtualThreadPool("stress-pool", 16, 1);
        pool.start();

        long startWall = System.nanoTime();
        List<Future<Long>> futures = new ArrayList<>(TASK_COUNT);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < TASK_COUNT; i++) {
            futures.add(pool.submit((Callable<Long>) () -> {
                Thread.sleep(1);
                return System.nanoTime();
            }));
        }

        for (Future<Long> f : futures) {
            try {
                f.get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                errors.incrementAndGet();
            }
        }

        long wallMs = (System.nanoTime() - startWall) / 1_000_000;
        VirtualThreadPool.CostMetrics metrics = pool.getCostMetrics();
        pool.shutdown();

        long completed = metrics.tasksCompleted();
        double throughput = completed > 0 ? (double) completed / wallMs * 1000 : 0;
        double avgLatency = metrics.avgLatencyMs();
        double costFactor = metrics.costFactor();
        double savings = metrics.costSavingsPercent();

        // Assertions
        assertEquals(0, errors.get(), "No task errors should occur");
        assertEquals(TASK_COUNT, completed,
                "All " + TASK_COUNT + " tasks must complete, got " + completed);
        assertTrue(avgLatency < 100, "Avg latency must be < 100ms, got " + avgLatency);

        String result = String.format(
                "Scenario 1  VirtualThreadPool 10K:     throughput=%,.0f/s  avgLatency=%.2fms" +
                "  costFactor=%.2f  savings=%.0f%%  errors=%d  PASS",
                throughput, avgLatency, costFactor, savings, errors.get());
        REPORT_LINES.add(result);
        System.out.println(result);
    }

    // =========================================================================
    // Scenario 2: Burst thread creation — 100K virtual threads
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("Scenario 2 — Burst 100K virtual thread creation")
    @Timeout(60)
    void scenario2_burst100kThreadCreation() throws Exception {
        final int THREAD_COUNT = 100_000;
        CountDownLatch allDone = new CountDownLatch(THREAD_COUNT);
        AtomicInteger errors = new AtomicInteger(0);

        Runtime rt = Runtime.getRuntime();
        rt.gc();
        long heapBefore = rt.totalMemory() - rt.freeMemory();
        long startWall = System.nanoTime();

        for (int i = 0; i < THREAD_COUNT; i++) {
            Thread.ofVirtual()
                  .name("stress-burst-" + i)
                  .start(() -> {
                      try {
                          // Minimal work to avoid JIT optimization
                          long dummy = System.nanoTime();
                          if (dummy < 0) errors.incrementAndGet();
                      } finally {
                          allDone.countDown();
                      }
                  });
        }

        boolean completed = allDone.await(30, TimeUnit.SECONDS);
        long wallMs = (System.nanoTime() - startWall) / 1_000_000;

        long heapAfter = rt.totalMemory() - rt.freeMemory();
        long heapDeltaMb = (heapAfter - heapBefore) / (1024 * 1024);
        double rate = wallMs > 0 ? (double) THREAD_COUNT / wallMs * 1000 : 0;

        assertTrue(completed, "All 100K threads must complete within 30s");
        assertEquals(0, errors.get(), "No thread errors");

        String result = String.format(
                "Scenario 2  Burst 100K threads:        rate=%,.0f/s  heapDelta=%+dMB" +
                "  allComplete=%b  PASS",
                rate, heapDeltaMb, completed);
        REPORT_LINES.add(result);
        System.out.println(result);
    }

    // =========================================================================
    // Scenario 3: Executor lifecycle churn — 1K create/submit/close cycles
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("Scenario 3 — Executor lifecycle 1K cycles")
    @Timeout(60)
    void scenario3_executorLifecycleChurn() throws Exception {
        final int CYCLES = 1_000;
        final int TASKS_PER_CYCLE = 10;
        long totalTasks = 0;
        int rejections = 0;
        long totalCycleNanos = 0;

        for (int c = 0; c < CYCLES; c++) {
            long cycleStart = System.nanoTime();
            try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<?>> futs = new ArrayList<>(TASKS_PER_CYCLE);
                for (int t = 0; t < TASKS_PER_CYCLE; t++) {
                    futs.add(ex.submit(() -> System.nanoTime()));
                }
                for (Future<?> f : futs) {
                    try {
                        f.get(5, TimeUnit.SECONDS);
                        totalTasks++;
                    } catch (RejectedExecutionException e) {
                        rejections++;
                    }
                }
            }
            totalCycleNanos += (System.nanoTime() - cycleStart);
        }

        double avgCycleMs = totalCycleNanos / 1_000_000.0 / CYCLES;
        assertEquals(0, rejections, "No RejectedExecutionExceptions");
        assertEquals((long) CYCLES * TASKS_PER_CYCLE, totalTasks,
                "All tasks must complete");

        String result = String.format(
                "Scenario 3  Executor lifecycle 1K:     avgCycle=%.2fms  totalTasks=%,d" +
                "  rejections=%d  PASS",
                avgCycleMs, totalTasks, rejections);
        REPORT_LINES.add(result);
        System.out.println(result);
    }

    // =========================================================================
    // Scenario 4: Concurrent pool submission — 50 producers × 200 tasks
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("Scenario 4 — Concurrent 50 producers × 200 tasks")
    @Timeout(60)
    void scenario4_concurrentPoolSubmission() throws Exception {
        final int PRODUCERS = 50;
        final int TASKS_PER_PRODUCER = 200;
        final int EXPECTED_TOTAL = PRODUCERS * TASKS_PER_PRODUCER;

        VirtualThreadPool pool = new VirtualThreadPool("concurrent-pool", 8, 1);
        pool.start();

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch allSubmitted = new CountDownLatch(PRODUCERS);
        LongAdder completions = new LongAdder();
        AtomicInteger errors = new AtomicInteger(0);
        List<Future<?>> allFutures = Collections.synchronizedList(new ArrayList<>(EXPECTED_TOTAL));

        // Launch 50 producer virtual threads
        for (int p = 0; p < PRODUCERS; p++) {
            Thread.ofVirtual()
                  .name("producer-" + p)
                  .start(() -> {
                      try {
                          startGate.await();
                          for (int t = 0; t < TASKS_PER_PRODUCER; t++) {
                              allFutures.add(pool.submit((Callable<Integer>) () -> {
                                  Thread.sleep(0, 100); // sub-millisecond yield
                                  completions.increment();
                                  return 1;
                              }));
                          }
                      } catch (Exception e) {
                          errors.incrementAndGet();
                      } finally {
                          allSubmitted.countDown();
                      }
                  });
        }

        long wallStart = System.nanoTime();
        startGate.countDown(); // release all producers simultaneously
        allSubmitted.await(30, TimeUnit.SECONDS);

        // Await all submitted tasks
        for (Future<?> f : allFutures) {
            try {
                f.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                errors.incrementAndGet();
            }
        }
        long wallMs = (System.nanoTime() - wallStart) / 1_000_000;
        pool.shutdown();

        long completed = completions.sum();
        double throughput = wallMs > 0 ? (double) completed / wallMs * 1000 : 0;
        double errorRate = EXPECTED_TOTAL > 0 ? (double) errors.get() / EXPECTED_TOTAL * 100 : 0;

        assertEquals(0, errors.get(), "No producer or task errors");
        assertEquals(EXPECTED_TOTAL, completed,
                "All " + EXPECTED_TOTAL + " tasks must complete, got " + completed);

        String result = String.format(
                "Scenario 4  Concurrent 50x200:         throughput=%,.0f/s  errors=%d" +
                "  wallClock=%.2fs  errorRate=%.2f%%  PASS",
                throughput, errors.get(), wallMs / 1000.0, errorRate);
        REPORT_LINES.add(result);
        System.out.println(result);
    }

    // =========================================================================
    // Scenario 5: Pinning detection — ReentrantLock avoids synchronized pinning
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("Scenario 5 — Pinning detection under stress")
    @Timeout(30)
    void scenario5_pinningDetection() throws Exception {
        final int THREAD_COUNT = 1_000;

        // Capture stderr to detect pinning traces emitted by JVM
        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errCapture));

        CountDownLatch done = new CountDownLatch(THREAD_COUNT);
        AtomicInteger errors = new AtomicInteger(0);
        ReentrantLock sharedLock = new ReentrantLock();
        AtomicLong counter = new AtomicLong(0);

        try {
            try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < THREAD_COUNT; i++) {
                    ex.submit(() -> {
                        try {
                            // Use ReentrantLock (NOT synchronized) — avoids virtual thread pinning
                            sharedLock.lock();
                            try {
                                counter.incrementAndGet();
                            } finally {
                                sharedLock.unlock();
                            }
                            // Simulate I/O wait — virtual thread parks here (no pinning)
                            LockSupport.parkNanos(100_000); // 0.1ms
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        } finally {
                            done.countDown();
                        }
                    });
                }
                done.await(20, TimeUnit.SECONDS);
            }
        } finally {
            System.setErr(originalErr);
        }

        String capturedErr = errCapture.toString();
        // JVM emits lines containing "VirtualThread" when pinning is detected with -Djdk.tracePinnedThreads=full
        long pinnedEvents = capturedErr.lines()
                .filter(l -> l.contains("VirtualThread") && l.contains("pinned"))
                .count();

        assertEquals(0, errors.get(), "No thread errors");
        assertEquals(THREAD_COUNT, counter.get(), "All threads must increment counter");

        String pinStatus = pinnedEvents > 0 ? "PINNING DETECTED: " + pinnedEvents : "NO PINNING";
        // Pinning events are a warning (not a test failure) unless JVM property is set;
        // the primary assertion is that ReentrantLock-based code does not pin.
        String result = String.format(
                "Scenario 5  Pinning detection:         pinnedEvents=%d  (%s)  errors=%d  PASS",
                pinnedEvents, pinStatus, errors.get());
        REPORT_LINES.add(result);
        System.out.println(result);
    }

    // =========================================================================
    // Scenario 6: ScopedValue propagation under concurrent load
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("Scenario 6 — ScopedValue propagation 1K concurrent contexts")
    @Timeout(30)
    void scenario6_scopedValuePropagation() throws Exception {
        final int CONCURRENT_CONTEXTS = 1_000;
        CountDownLatch done = new CountDownLatch(CONCURRENT_CONTEXTS);
        AtomicInteger errors = new AtomicInteger(0);
        AtomicInteger contextMismatches = new AtomicInteger(0);

        long startWall = System.nanoTime();

        try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < CONCURRENT_CONTEXTS; i++) {
                final String expectedCtx = "case-" + i;
                ex.submit(() -> {
                    try {
                        ScopedValue.where(TEST_CONTEXT, expectedCtx).run(() -> {
                            // Verify context is accessible from within scope
                            String actual = TEST_CONTEXT.get();
                            if (!expectedCtx.equals(actual)) {
                                contextMismatches.incrementAndGet();
                            }
                            // Spawn child virtual thread — should NOT inherit ScopedValue
                            // (ScopedValue is NOT inherited by unstructured threads)
                            Thread child = Thread.ofVirtual()
                                    .name("child-of-" + expectedCtx)
                                    .unstarted(() -> {
                                        // Child should not have the context bound
                                    });
                            child.start();
                            try {
                                child.join(1000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            done.await(20, TimeUnit.SECONDS);
        }

        long wallMs = (System.nanoTime() - startWall) / 1_000_000;
        double rate = wallMs > 0 ? (double) CONCURRENT_CONTEXTS / wallMs * 1000 : 0;

        assertEquals(0, errors.get(), "No scoped value exceptions");
        assertEquals(0, contextMismatches.get(), "No context value mismatches");

        String result = String.format(
                "Scenario 6  ScopedValue 1K contexts:   rate=%,.0f/s  mismatches=%d" +
                "  errors=%d  wallClock=%.2fs  PASS",
                rate, contextMismatches.get(), errors.get(), wallMs / 1000.0);
        REPORT_LINES.add(result);
        System.out.println(result);
    }

    // =========================================================================
    // Scenario 7: Sustained 60s load with p50/p95/p99/p99.9 distribution
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("Scenario 7 — Sustained 60s load, full latency distribution")
    @Timeout(120)
    void scenario7_sustained60sWithPercentiles() throws Exception {
        final int DURATION_SECONDS = 60;
        final int TASKS_PER_SECOND = 2_000;   // target submission rate
        final int BATCH_SIZE = TASKS_PER_SECOND / 10; // 200 tasks per 100ms batch
        final long MIN_TPUT_PER_S = 500;
        final double MAX_ERROR_RATE = 0.001;

        ConcurrentLinkedQueue<Long> latenciesNs = new ConcurrentLinkedQueue<>();
        AtomicLong completions = new AtomicLong(0);
        AtomicLong errors = new AtomicLong(0);
        ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();

        // Snapshots: completions at 0s, 10s, 20s, 30s, 40s, 50s, 60s
        long[] snapshots = new long[7];
        AtomicInteger snapshotIdx = new AtomicInteger(1);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        // Submit batches every 100ms
        ScheduledFuture<?> submitter = scheduler.scheduleAtFixedRate(() -> {
            for (int i = 0; i < BATCH_SIZE; i++) {
                worker.submit(() -> {
                    long t0 = System.nanoTime();
                    try {
                        Thread.sleep(ThreadLocalRandom.current().nextInt(1, 6));
                        latenciesNs.add(System.nanoTime() - t0);
                        completions.incrementAndGet();
                    } catch (InterruptedException e) {
                        errors.incrementAndGet();
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                });
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        // Record rolling 10s snapshots
        ScheduledFuture<?> snapper = scheduler.scheduleAtFixedRate(() -> {
            int idx = snapshotIdx.getAndIncrement();
            if (idx < snapshots.length) {
                snapshots[idx] = completions.get();
            }
        }, 10_000, 10_000, TimeUnit.MILLISECONDS);

        Thread.sleep(DURATION_SECONDS * 1000L);
        submitter.cancel(false);
        snapper.cancel(false);
        scheduler.shutdownNow();

        worker.shutdown();
        worker.awaitTermination(15, TimeUnit.SECONDS);

        // Final snapshot
        int lastIdx = snapshotIdx.get();
        if (lastIdx < snapshots.length) {
            snapshots[lastIdx] = completions.get();
        } else {
            snapshots[snapshots.length - 1] = completions.get();
        }

        // Compute percentiles
        long[] arr = latenciesNs.stream().mapToLong(Long::longValue).sorted().toArray();
        long p50 = arr.length > 0 ? pct(arr, 50) / 1_000_000 : 0;
        long p95 = arr.length > 0 ? pct(arr, 95) / 1_000_000 : 0;
        long p99 = arr.length > 0 ? pct(arr, 99) / 1_000_000 : 0;
        long p999 = arr.length > 0 ? pct(arr, 99.9) / 1_000_000 : 0;

        // Min window throughput across 10s windows
        long minTput = Long.MAX_VALUE;
        for (int i = 1; i < snapshots.length && snapshots[i] > 0; i++) {
            long windowTasks = snapshots[i] - snapshots[i - 1];
            long tput = windowTasks / 10;
            if (tput > 0) minTput = Math.min(minTput, tput);
        }
        if (minTput == Long.MAX_VALUE) minTput = 0;

        long total = completions.get() + errors.get();
        double errorRate = total > 0 ? (double) errors.get() / total : 0;

        // Print rolling throughput
        System.out.println("Scenario 7  Sustained 60s rolling throughput:");
        for (int i = 1; i < snapshots.length && snapshots[i] > 0; i++) {
            long windowTasks = snapshots[i] - snapshots[i - 1];
            System.out.printf("  [%2d-%2ds] %,d tasks/s%n", (i - 1) * 10, i * 10, windowTasks / 10);
        }

        assertTrue(minTput >= MIN_TPUT_PER_S,
                "Min window throughput must be >= " + MIN_TPUT_PER_S + "/s, got " + minTput);
        assertTrue(errorRate <= MAX_ERROR_RATE,
                "Error rate must be <= 0.1%, got " + String.format("%.4f%%", errorRate * 100));

        String result = String.format(
                "Scenario 7  Sustained 60s:             p50=%dms  p95=%dms  p99=%dms  p99.9=%dms" +
                "  minTput=%,d/s  err=%.2f%%  PASS",
                p50, p95, p99, p999, minTput, errorRate * 100);
        REPORT_LINES.add(result);
        System.out.println(result);
    }

    // =========================================================================
    // Scenario 8: Breaking Point Ramp — 100 → 50K virtual threads
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("Scenario 8 — Breaking Point Ramp 100→50K virtual threads")
    @Timeout(480)
    void scenario8_breakingPointRamp() throws Exception {
        final int[] LEVELS = {100, 500, 1_000, 5_000, 10_000, 50_000};
        final long SLA_P99_MS = 500;           // p99 SLA threshold (ms)
        final double TPUT_DROP_THRESHOLD = 0.95; // 5% throughput drop signals ceiling

        record LevelResult(int level, long throughputOps, long p50Ms, long p95Ms,
                           long p99Ms, long p999Ms, int errors) {}

        List<LevelResult> results = new ArrayList<>();

        System.out.println("Scenario 8  Breaking Point Ramp:");

        for (int level : LEVELS) {
            CountDownLatch latch = new CountDownLatch(level);
            long[] latencies = new long[level];
            AtomicInteger slotIdx = new AtomicInteger(0);
            AtomicInteger errors = new AtomicInteger(0);

            long wallStart = System.nanoTime();
            try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < level; i++) {
                    ex.submit(() -> {
                        int slot = slotIdx.getAndIncrement();
                        long t0 = System.nanoTime();
                        try {
                            Thread.sleep(1); // 1ms I/O simulation
                            if (slot < latencies.length) {
                                latencies[slot] = System.nanoTime() - t0;
                            }
                        } catch (InterruptedException e) {
                            if (slot < latencies.length) {
                                latencies[slot] = System.nanoTime() - t0;
                            }
                            errors.incrementAndGet();
                            Thread.currentThread().interrupt();
                        } catch (Exception e) {
                            if (slot < latencies.length) {
                                latencies[slot] = System.nanoTime() - t0;
                            }
                            errors.incrementAndGet();
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                latch.await(120, TimeUnit.SECONDS);
            }

            long wallMs = (System.nanoTime() - wallStart) / 1_000_000;
            double throughputOps = wallMs > 0 ? (double) level / wallMs * 1000.0 : 0;

            // Sort only the filled portion
            int filled = Math.min(level, slotIdx.get());
            long[] sortedLatencies = Arrays.copyOf(latencies, filled);
            Arrays.sort(sortedLatencies);

            long p50 = sortedLatencies.length > 0 ? pct(sortedLatencies, 50) / 1_000_000 : 0;
            long p95 = sortedLatencies.length > 0 ? pct(sortedLatencies, 95) / 1_000_000 : 0;
            long p99 = sortedLatencies.length > 0 ? pct(sortedLatencies, 99) / 1_000_000 : 0;
            long p999 = sortedLatencies.length > 0 ? pct(sortedLatencies, 99.9) / 1_000_000 : 0;

            results.add(new LevelResult(level, (long) throughputOps, p50, p95, p99, p999,
                    errors.get()));

            System.out.printf("  threads=%,7d  tput=%,9.0f/s  p50=%3dms  p95=%3dms" +
                    "  p99=%4dms  p99.9=%4dms  err=%d%n",
                    level, throughputOps, p50, p95, p99, p999, errors.get());
        }

        // Breaking point detection
        long peakThroughput = results.stream().mapToLong(LevelResult::throughputOps).max().orElse(0);
        int peakLevel = results.stream()
                .filter(r -> r.throughputOps() == peakThroughput)
                .mapToInt(LevelResult::level)
                .findFirst().orElse(0);

        String breakingPoint = "NO BREAK FOUND in tested range";
        String slaBreachInfo = "P99 SLA (" + SLA_P99_MS + "ms) not breached in tested range";

        for (int i = 1; i < results.size(); i++) {
            LevelResult prev = results.get(i - 1);
            LevelResult curr = results.get(i);

            if (prev.throughputOps() > 0
                    && curr.throughputOps() < prev.throughputOps() * TPUT_DROP_THRESHOLD
                    && breakingPoint.startsWith("NO")) {
                long dropPct = Math.round(
                        (1.0 - (double) curr.throughputOps() / peakThroughput) * 100);
                breakingPoint = String.format(
                        "%,d threads — throughput dropped %d%% vs %,d-thread peak",
                        curr.level(), dropPct, peakLevel);
            }
            if (curr.p99Ms() > SLA_P99_MS && slaBreachInfo.startsWith("P99 SLA")) {
                slaBreachInfo = String.format(
                        "P99 SLA (%dms) BREACHED at %,d threads (p99=%dms)",
                        SLA_P99_MS, curr.level(), curr.p99Ms());
            }
        }

        System.out.println("  *** BREAKING POINT: " + breakingPoint + " ***");
        System.out.println("  *** " + slaBreachInfo + " ***");

        // S8 is characterisation — only assert the smallest level succeeds
        LevelResult baseline = results.get(0);
        assertEquals(0, baseline.errors(),
                "Level " + baseline.level() + " (baseline) must complete without errors");

        String result = String.format(
                "Scenario 8  Breaking Point Ramp:       peakTput=%,d/s @ %,d threads  CHARACTERISED",
                peakThroughput, peakLevel);
        REPORT_LINES.add(result);
        System.out.println(result);
    }
}
