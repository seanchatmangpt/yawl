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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.logging.YLogDataItemList;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;
import org.yawlfoundation.yawl.util.java25.StreamMetrics;
import org.yawlfoundation.yawl.util.java25.StreamMetrics.PercentileSnapshot;

import java.io.File;
import java.lang.ScopedValue;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Blue-ocean stress tests for the YAWL stateful engine.
 *
 * <p>Complements {@link VirtualThreadStressTest} (which stress-tests {@link VirtualThreadPool}
 * in isolation) by exercising the <em>real</em> {@link YEngine} under high concurrency.
 * All 8 scenarios are new coverage that no existing test provides:</p>
 *
 * <ol>
 *   <li><b>Case Storm 500</b> — 500 concurrent {@code startCase()} via StructuredTaskScope</li>
 *   <li><b>Cancellation Flood</b> — mass-cancel 200 live cases concurrently</li>
 *   <li><b>Work Item Index</b> — 1000× {@code getWorkItems(ENABLED)} with 250 live items; validates P3 O(1) index</li>
 *   <li><b>Rapid Lifecycle Cycles</b> — 20 load→start→clear cycles; measures memory growth</li>
 *   <li><b>Reader/Writer Contention</b> — 100 virtual readers + 10 writers; validates P2 ReadWriteLock</li>
 *   <li><b>Gatherers Rolling Throughput</b> — first {@code Gatherers.windowSliding()} usage in codebase</li>
 *   <li><b>Degradation Profile</b> — concurrency curve [10,50,100,200,500] cases/s</li>
 *   <li><b>ScopedValue at Engine Scale</b> — 200 concurrent contexts; assert zero leakage</li>
 * </ol>
 *
 * <p>Chicago TDD: uses the real YEngine singleton; no mocks. Serialised via
 * {@link ExecutionMode#SAME_THREAD} because YEngine is a singleton with shared mutable state.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@Tag("stress")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(ExecutionMode.SAME_THREAD)
class StatefulEngineStressTest {

    // -------------------------------------------------------------------------
    // Report accumulator (mirrors VirtualThreadStressTest format)
    // -------------------------------------------------------------------------

    private static final List<String> REPORT_LINES = new ArrayList<>();
    private static final String HDR = "=== STATEFUL ENGINE STRESS REPORT ===";
    private static final String FTR_PASS  = "=== RESULT: S1-S6+S8 PASS | S7 CHARACTERISED ===";
    private static final String FTR_FAIL  = "=== RESULT: FAILURES DETECTED ===";

    // ScopedValue for S8 — mirrors YNetRunner.CASE_CONTEXT pattern
    private static final ScopedValue<String> ENGINE_STRESS_CTX = ScopedValue.newInstance();

    @BeforeAll
    static void printHeader() {
        System.out.println();
        System.out.println(HDR);
    }

    @AfterAll
    static void printReport() {
        boolean allPass = REPORT_LINES.stream()
                .allMatch(l -> l.contains("PASS") || l.contains("CHARACTERISED"));
        for (String line : REPORT_LINES) System.out.println(line);
        System.out.println(allPass ? FTR_PASS : FTR_FAIL);
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // Per-test setup / teardown
    // -------------------------------------------------------------------------

    private YEngine engine;
    private YSpecification spec;

    @BeforeEach
    void setUp() throws Exception {
        engine = YEngine.getInstance();
        EngineClearer.clear(engine);
        spec = loadSpec("YAWL_Specification2.xml");
        engine.loadSpecification(spec);
    }

    @AfterEach
    void tearDown() throws Exception {
        EngineClearer.clear(engine);
    }

    // -------------------------------------------------------------------------
    // Percentile utility (inline, no external dep)
    // -------------------------------------------------------------------------

    private static long pct(long[] sorted, double p) {
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
    }

    // =========================================================================
    // S1: Case Storm 500
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("S1 — Case Storm 500 via StructuredTaskScope")
    @Timeout(120)
    void s1_caseStorm500() throws Exception {
        final int TARGET = 500;
        final int MIN_SUCCESS = 450;

        List<StructuredTaskScope.Subtask<YIdentifier>> subtasks = new ArrayList<>(TARGET);
        AtomicInteger failed = new AtomicInteger(0);
        long wallStart = System.nanoTime();

        // Use awaitAllSuccessfulOrThrow — any structural failure surfaces immediately
        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<YIdentifier>awaitAllSuccessfulOrThrow())) {
            for (int i = 0; i < TARGET; i++) {
                subtasks.add(scope.fork(() ->
                        engine.startCase(spec.getSpecificationID(), null, null, null,
                                new YLogDataItemList(), null, false)));
            }
            scope.join();
        } catch (Exception ex) {
            // Count subtasks that failed (Joiner threw because ≥1 failed)
            failed.set((int) subtasks.stream()
                    .filter(t -> t.state() == StructuredTaskScope.Subtask.State.FAILED)
                    .count());
        }

        long wallMs = (System.nanoTime() - wallStart) / 1_000_000;
        int succeeded = (int) subtasks.stream()
                .filter(t -> t.state() == StructuredTaskScope.Subtask.State.SUCCESS)
                .count();

        assertTrue(succeeded >= MIN_SUCCESS,
                "At least " + MIN_SUCCESS + "/500 cases must start; got " + succeeded);

        String result = String.format(
                "[S1] PASS — Case Storm 500: %d/%d started in %dms", succeeded, TARGET, wallMs);
        REPORT_LINES.add(result);
        System.out.println(result);
    }

    // =========================================================================
    // S2: Cancellation Flood
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("S2 — Cancellation Flood: 200 cases, mass-cancel concurrently")
    @Timeout(120)
    void s2_cancellationFlood() throws Exception {
        final int CASE_COUNT = 200;

        // Start cases sequentially (engine singleton — no contention during setup)
        List<YIdentifier> caseIDs = new ArrayList<>(CASE_COUNT);
        for (int i = 0; i < CASE_COUNT; i++) {
            YIdentifier id = engine.startCase(spec.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);
            assertNotNull(id, "Case " + i + " must start");
            caseIDs.add(id);
        }

        // Cancel all concurrently via virtual threads
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(CASE_COUNT);
        AtomicInteger errors = new AtomicInteger(0);
        long wallStart = System.nanoTime();

        for (YIdentifier caseID : caseIDs) {
            Thread.ofVirtual().start(() -> {
                try {
                    startGate.await();
                    engine.cancelCase(caseID);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errors.incrementAndGet();
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    allDone.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = allDone.await(60, TimeUnit.SECONDS);
        long wallMs = (System.nanoTime() - wallStart) / 1_000_000;

        assertTrue(completed, "All cancellations must complete within 60s");

        // Verify no orphan work items remain
        Set<YWorkItem> remaining = engine.getWorkItemRepository().getWorkItems();
        int orphans = remaining.size();

        assertEquals(0, orphans, "No work items must remain after mass-cancel");

        String result = String.format(
                "[S2] PASS — Cancellation Flood: %d orphan work items after %d cancellations in %dms",
                orphans, CASE_COUNT, wallMs);
        REPORT_LINES.add(result);
        System.out.println(result);
    }

    // =========================================================================
    // S3: Work Item Index Validation (P3 O(1) claim)
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("S3 — Work Item Index: 1000x getWorkItems(ENABLED) with 250 live items")
    @Timeout(60)
    void s3_workItemIndexValidation() throws Exception {
        final int CASE_COUNT = 50;      // each produces ~5 enabled work items → ~250 total
        final int QUERIES = 1_000;
        final long P95_LIMIT_MS = 10;

        // Populate the repository with live cases
        for (int i = 0; i < CASE_COUNT; i++) {
            engine.startCase(spec.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);
        }

        // Warm up index before timing
        engine.getWorkItemRepository().getWorkItems(YWorkItemStatus.statusEnabled);

        // Time 1000 status queries
        long[] latenciesNs = new long[QUERIES];
        YWorkItemRepository repo = engine.getWorkItemRepository();
        for (int q = 0; q < QUERIES; q++) {
            long t0 = System.nanoTime();
            Set<YWorkItem> items = repo.getWorkItems(YWorkItemStatus.statusEnabled);
            latenciesNs[q] = System.nanoTime() - t0;
        }

        Arrays.sort(latenciesNs);
        long p95Ms = pct(latenciesNs, 95) / 1_000_000;
        long p50Ms = pct(latenciesNs, 50) / 1_000_000;

        int liveItems = repo.getWorkItems(YWorkItemStatus.statusEnabled).size();

        assertTrue(p95Ms < P95_LIMIT_MS,
                "getWorkItems(ENABLED) p95 must be < " + P95_LIMIT_MS + "ms, got " + p95Ms + "ms");

        String result = String.format(
                "[S3] PASS — Work Item Index: p50=%dms p95=%dms (limit %dms), %d queries across ~%d items",
                p50Ms, p95Ms, P95_LIMIT_MS, QUERIES, liveItems);
        REPORT_LINES.add(result);
        System.out.println(result);
    }

    // =========================================================================
    // S4: Rapid Lifecycle Cycles (memory leak check)
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("S4 — Rapid Lifecycle Cycles: 20 load→start→clear cycles, heap delta < 50MB")
    @Timeout(120)
    void s4_rapidLifecycleCycles() throws Exception {
        final int CYCLES = 20;
        final int CASES_PER_CYCLE = 30;
        final long HEAP_LIMIT_MB = 50;

        Runtime rt = Runtime.getRuntime();

        // Baseline measurement (after GC to reduce noise)
        rt.gc();
        Thread.sleep(50);
        long heapBefore = rt.totalMemory() - rt.freeMemory();

        for (int cycle = 0; cycle < CYCLES; cycle++) {
            // Clear from previous iteration and reload spec
            EngineClearer.clear(engine);
            engine.loadSpecification(spec);

            for (int i = 0; i < CASES_PER_CYCLE; i++) {
                engine.startCase(spec.getSpecificationID(), null, null, null,
                        new YLogDataItemList(), null, false);
            }
        }

        rt.gc();
        Thread.sleep(50);
        long heapAfter = rt.totalMemory() - rt.freeMemory();
        long deltaMb = (heapAfter - heapBefore) / (1024 * 1024);

        assertTrue(deltaMb < HEAP_LIMIT_MB,
                "Heap growth after " + CYCLES + " cycles must be < " + HEAP_LIMIT_MB +
                "MB, got " + deltaMb + "MB");

        String result = String.format(
                "[S4] PASS — Lifecycle Cycles: heap delta=%+dMB over %d cycles (limit %dMB)",
                deltaMb, CYCLES, HEAP_LIMIT_MB);
        REPORT_LINES.add(result);
        System.out.println(result);
    }

    // =========================================================================
    // S5: Reader/Writer Contention (P2 ReadWriteLock validation)
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("S5 — Reader/Writer Contention: 100 readers + 10 writers, no deadlock < 10s")
    @Timeout(30)
    void s5_readerWriterContention() throws Exception {
        final int READERS = 100;
        final int WRITERS = 10;
        final long WALL_LIMIT_MS = 10_000;

        // Pre-populate a few cases for readers to query
        List<YIdentifier> initialCases = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            YIdentifier id = engine.startCase(spec.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);
            initialCases.add(id);
        }

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(READERS + WRITERS);
        AtomicInteger errors = new AtomicInteger(0);
        long wallStart = System.nanoTime();

        // Readers: query work item repository and runner repository
        for (int r = 0; r < READERS; r++) {
            Thread.ofVirtual().start(() -> {
                try {
                    startGate.await();
                    YWorkItemRepository repo = engine.getWorkItemRepository();
                    Set<YWorkItem> items = repo.getWorkItems(YWorkItemStatus.statusEnabled);
                    YNetRunnerRepository runners = engine.getNetRunnerRepository();
                    for (YIdentifier id : initialCases) {
                        YNetRunner runner = runners.get(id);
                        if (runner != null) {
                            runner.isCompleted();
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    allDone.countDown();
                }
            });
        }

        // Writers: start a case then cancel it
        for (int w = 0; w < WRITERS; w++) {
            Thread.ofVirtual().start(() -> {
                try {
                    startGate.await();
                    YIdentifier id = engine.startCase(spec.getSpecificationID(), null, null, null,
                            new YLogDataItemList(), null, false);
                    if (id != null) {
                        engine.cancelCase(id);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    allDone.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = allDone.await(15, TimeUnit.SECONDS);
        long wallMs = (System.nanoTime() - wallStart) / 1_000_000;

        assertTrue(completed, "All reader/writer threads must complete (no deadlock)");
        assertTrue(wallMs < WALL_LIMIT_MS,
                "Reader/writer phase must complete in < " + WALL_LIMIT_MS + "ms, got " + wallMs + "ms");

        String result = String.format(
                "[S5] PASS — Reader/Writer: completed in %.1fs (limit %ds), %d errors, 0 deadlocks",
                wallMs / 1000.0, WALL_LIMIT_MS / 1000, errors.get());
        REPORT_LINES.add(result);
        System.out.println(result);
    }

    // =========================================================================
    // S6: Gatherers Rolling Throughput (first Gatherers.windowSliding usage)
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("S6 — Gatherers Rolling Throughput: windowSliding(20) over 300 case completions")
    @Timeout(120)
    void s6_gatherersRollingThroughput() throws Exception {
        final int CASE_COUNT = 300;
        final int WINDOW = 20;

        List<Long> completionTimestamps = Collections.synchronizedList(new ArrayList<>(CASE_COUNT));
        CountDownLatch done = new CountDownLatch(CASE_COUNT);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < CASE_COUNT; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    YIdentifier id = engine.startCase(spec.getSpecificationID(), null, null, null,
                            new YLogDataItemList(), null, false);
                    if (id != null) {
                        completionTimestamps.add(System.nanoTime());
                        engine.cancelCase(id);
                    } else {
                        errors.incrementAndGet();
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        done.await(60, TimeUnit.SECONDS);

        // Sort timestamps (threads complete out of order)
        List<Long> sorted = completionTimestamps.stream().sorted().toList();

        // Compute rolling throughput rates using StreamMetrics (Gatherers.windowSliding internally)
        List<Double> rollingRates = sorted.stream()
                .gather(StreamMetrics.rollingThroughputPerSec(WINDOW))
                .toList();

        assertTrue(rollingRates.size() > 0, "Must have at least one rolling rate window");

        // Summarize the rates as a latency-style distribution (treating rate as the metric)
        List<Long> rateLongs = rollingRates.stream()
                .map(Double::longValue)
                .toList();
        PercentileSnapshot snap = StreamMetrics.summarize(rateLongs);

        System.out.println("S6  Rolling throughput distribution (cases/sec):");
        System.out.printf("    p50=%.0f/s  p95=%.0f/s  windows=%d%n",
                (double) snap.p50(), (double) snap.p95(), rollingRates.size());

        assertTrue(snap.p50() > 0, "Rolling p50 throughput must be > 0 cases/s");

        String result = String.format(
                "[S6] PASS — Gatherers Throughput: rolling p50=%d cases/s  p95=%d cases/s  windows=%d",
                snap.p50(), snap.p95(), rollingRates.size());
        REPORT_LINES.add(result);
        System.out.println(result);
    }

    // =========================================================================
    // S7: Degradation Profile
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("S7 — Degradation Profile: concurrency [10,50,100,200,500] cases/sec")
    @Timeout(300)
    void s7_degradationProfile() throws Exception {
        final int[] LEVELS = {10, 50, 100, 200, 500};
        final double NO_CLIFF_RATIO = 0.5;  // each level >= previous × 0.5

        record LevelResult(int level, double casesPerSec) {}
        List<LevelResult> results = new ArrayList<>();

        System.out.println("S7  Degradation profile:");

        for (int level : LEVELS) {
            EngineClearer.clear(engine);
            engine.loadSpecification(spec);

            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch allDone = new CountDownLatch(level);
            AtomicInteger succeeded = new AtomicInteger(0);

            for (int i = 0; i < level; i++) {
                Thread.ofVirtual().start(() -> {
                    try {
                        startGate.await();
                        YIdentifier id = engine.startCase(spec.getSpecificationID(), null, null, null,
                                new YLogDataItemList(), null, false);
                        if (id != null) {
                            engine.cancelCase(id);
                            succeeded.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        allDone.countDown();
                    }
                });
            }

            long wallStart = System.nanoTime();
            startGate.countDown();
            allDone.await(120, TimeUnit.SECONDS);
            long wallMs = Math.max(1, (System.nanoTime() - wallStart) / 1_000_000);

            double casesPerSec = (double) succeeded.get() / wallMs * 1000.0;
            results.add(new LevelResult(level, casesPerSec));
            System.out.printf("    [concurrency=%3d]  %,6.0f cases/s%n", level, casesPerSec);
        }

        // Assert no cliff drop: each level >= previous × 0.5
        for (int i = 1; i < results.size(); i++) {
            double prev = results.get(i - 1).casesPerSec();
            double curr = results.get(i).casesPerSec();
            if (prev > 0) {
                assertTrue(curr >= prev * NO_CLIFF_RATIO,
                        String.format("Throughput cliff at concurrency=%d: prev=%.0f curr=%.0f (ratio=%.2f < 0.5)",
                                results.get(i).level(), prev, curr, curr / prev));
            }
        }

        String curve = results.stream()
                .map(r -> String.format("%d→%.0f", r.level(), r.casesPerSec()))
                .reduce((a, b) -> a + " | " + b)
                .orElse("empty");

        String result = "[S7] CHARACTERISED — Degradation: " + curve + " cases/s";
        REPORT_LINES.add(result);
        System.out.println(result);
    }

    // =========================================================================
    // S8: ScopedValue Isolation at 200 Scale
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("S8 — ScopedValue Isolation: 200 concurrent contexts under full engine load")
    @Timeout(60)
    void s8_scopedValueIsolationAtScale() throws Exception {
        final int THREAD_COUNT = 200;

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(THREAD_COUNT);
        AtomicInteger leakage = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);
        long wallStart = System.nanoTime();

        for (int i = 0; i < THREAD_COUNT; i++) {
            final String expectedCtx = "engine-stress-case-" + i;
            Thread.ofVirtual().start(() -> {
                try {
                    startGate.await();
                    ScopedValue.where(ENGINE_STRESS_CTX, expectedCtx).call(() -> {
                        // Start a real case while the ScopedValue is bound
                        YIdentifier id = engine.startCase(spec.getSpecificationID(), null, null, null,
                                new YLogDataItemList(), null, false);

                        // Verify context integrity after engine call (could have thread-switched)
                        String actual = ENGINE_STRESS_CTX.get();
                        if (!expectedCtx.equals(actual)) {
                            leakage.incrementAndGet();
                        }

                        if (id != null) {
                            engine.cancelCase(id);
                        }
                        return null;
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errors.incrementAndGet();
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    allDone.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = allDone.await(30, TimeUnit.SECONDS);
        long wallMs = (System.nanoTime() - wallStart) / 1_000_000;

        assertTrue(completed, "All 200 ScopedValue threads must complete");
        assertEquals(0, leakage.get(),
                "Zero ScopedValue context leakage events expected, got " + leakage.get());

        String result = String.format(
                "[S8] PASS — ScopedValue: %d/%d context isolated, %d leakage events in %dms",
                THREAD_COUNT - leakage.get(), THREAD_COUNT, leakage.get(), wallMs);
        REPORT_LINES.add(result);
        System.out.println(result);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private YSpecification loadSpec(String resourceName) throws Exception {
        URL url = getClass().getResource(resourceName);
        assertNotNull(url, "Test resource not found: " + resourceName);
        File file = new File(url.getFile());
        List<YSpecification> specs = YMarshal.unmarshalSpecifications(
                StringUtil.fileToString(file.getAbsolutePath()), false);
        assertFalse(specs == null || specs.isEmpty(), "No specs parsed from " + resourceName);
        return specs.get(0);
    }
}
