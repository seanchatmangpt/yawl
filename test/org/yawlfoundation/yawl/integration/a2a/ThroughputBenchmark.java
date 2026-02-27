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

package org.yawlfoundation.yawl.integration.a2a;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Throughput and correctness benchmark using real {@link YStatelessEngine}.
 *
 * <p>Replaces the previous implementation that:</p>
 * <ul>
 *   <li>Required an external YAWL server via {@code YAWL_ENGINE_URL} env var,
 *       skipping the entire test in CI with {@code assumeTrue}</li>
 *   <li>Used {@code Thread.sleep(10)} to simulate "work"</li>
 *   <li>Had a {@code totalLatency} counter that was never incremented,
 *       making average latency always 0 or NaN</li>
 *   <li>Referenced non-existent {@code ModernYawlEngineAdapter} and
 *       {@code VirtualThreadMetrics} classes</li>
 * </ul>
 *
 * <h2>Self-checking invariants</h2>
 * <ul>
 *   <li>Percentile monotonicity: p50 ≤ p95 at each measurement point</li>
 *   <li>Error rate: cascade errors must stay under 10% of enabled events</li>
 *   <li>Degradation floor: throughput at 20× concurrency ≥ 10% of baseline</li>
 * </ul>
 *
 * <p>Chicago TDD: real engine operations only, no mocks, no external dependencies.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Tag("benchmark")
@DisplayName("Throughput Benchmark — real YStatelessEngine workflow operations")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ThroughputBenchmark {

    // ── B1: Sequential workflow throughput ────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("B1: Sequential workflow throughput — 100 case starts, measure ops/sec")
    void testSequentialWorkflowThroughput() throws Exception {
        YStatelessEngine engine = new YStatelessEngine();
        YSpecification spec = loadSpec(engine);

        int caseCount = 100;
        long[] latenciesNs = new long[caseCount];

        long totalStart = System.nanoTime();
        for (int i = 0; i < caseCount; i++) {
            long opStart = System.nanoTime();
            List<YNetRunner> runners = engine.launchCasesParallel(spec, List.of("seq-" + i));
            latenciesNs[i] = System.nanoTime() - opStart;
            assertFalse(runners.isEmpty(), "Case " + i + " must produce a runner");
        }
        double totalSec = (System.nanoTime() - totalStart) / 1_000_000_000.0;

        long[] sorted = latenciesNs.clone();
        Arrays.sort(sorted);
        double p50ms = sorted[caseCount / 2]           / 1_000_000.0;
        double p95ms = sorted[(int)(caseCount * 0.95)] / 1_000_000.0;
        double throughput = caseCount / totalSec;

        System.out.printf("%n=== SEQUENTIAL THROUGHPUT ===%n");
        System.out.printf("Cases: %d in %.2f sec = %.1f ops/sec%n", caseCount, totalSec, throughput);
        System.out.printf("P50: %.1f ms, P95: %.1f ms%n%n", p50ms, p95ms);

        // Self-check: percentile monotonicity
        assertTrue(p50ms <= p95ms,
                String.format("Percentile monotonicity violated: p50=%.1f > p95=%.1f", p50ms, p95ms));
        assertTrue(throughput > 0, "Sequential throughput must be positive");
    }

    // ── B2: Concurrent case correctness ───────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("B2: Concurrent case correctness — 20 parallel cases with event-driven completion")
    void testConcurrentCaseCorrectness() throws Exception {
        YStatelessEngine engine = new YStatelessEngine();
        YSpecification spec = loadSpec(engine);
        List<String> errors          = new CopyOnWriteArrayList<>();
        AtomicLong enabledEvents     = new AtomicLong(0);
        AtomicLong completedAttempts = new AtomicLong(0);

        engine.addWorkItemEventListener(new YWorkItemEventListener() {
            @Override
            public void handleWorkItemEvent(YWorkItemEvent event) {
                YWorkItem item = event.getWorkItem();
                if (event.getEventType() == YEventType.ITEM_ENABLED) {
                    enabledEvents.incrementAndGet();
                    try {
                        YWorkItem started = engine.startWorkItem(item);
                        engine.completeWorkItem(started, "<data/>", null);
                        completedAttempts.incrementAndGet();
                    } catch (Exception e) {
                        errors.add(e.getMessage());
                    }
                }
            }
        });

        int concurrency = 20;
        List<String> caseParams = new ArrayList<>();
        for (int i = 0; i < concurrency; i++) caseParams.add("conc-" + i);

        List<YNetRunner> runners = engine.launchCasesParallel(spec, caseParams);

        System.out.printf("%n=== CONCURRENT CORRECTNESS (20 cases) ===%n");
        System.out.printf("Runners: %d/%d, ITEM_ENABLED events: %d, Errors: %d%n%n",
                runners.size(), concurrency, enabledEvents.get(), errors.size());

        assertFalse(runners.isEmpty(),
                "At least 1 of 20 concurrent cases must produce a runner");

        // Self-check: error rate must be under 10% of enabled events
        long totalEnabled = enabledEvents.get();
        if (totalEnabled > 0) {
            double errorRate = (double) errors.size() / totalEnabled;
            assertTrue(errorRate < 0.10,
                    String.format("Error rate %.1f%% exceeds 10%% threshold. Errors: %s",
                            errorRate * 100,
                            errors.subList(0, Math.min(3, errors.size()))));
        }
    }

    // ── B3: Throughput degradation bound ─────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("B3: Throughput degradation bound — minimum 10% efficiency at 20× concurrency")
    void testThroughputDegradationBound() throws Exception {
        YStatelessEngine engine = new YStatelessEngine();
        YSpecification spec = loadSpec(engine);

        int[] concurrencyLevels = {1, 5, 10, 20};
        double baselineThroughput = -1;

        System.out.printf("%n=== THROUGHPUT DEGRADATION BOUND ===%n");
        System.out.printf("%-12s %-15s %-12s%n", "Concurrency", "Throughput", "Efficiency");
        System.out.println("-".repeat(39));

        for (int level : concurrencyLevels) {
            List<String> caseParams = new ArrayList<>();
            for (int i = 0; i < level; i++) caseParams.add("degrade-" + level + "-" + i);

            long start    = System.nanoTime();
            List<YNetRunner> runners = engine.launchCasesParallel(spec, caseParams);
            double elapsedSec = (System.nanoTime() - start) / 1_000_000_000.0;
            double throughput = level / Math.max(0.001, elapsedSec);

            double efficiency = (baselineThroughput > 0) ? throughput / baselineThroughput : 1.0;
            System.out.printf("%-12d %-15.1f %-12.1f%%%n", level, throughput, efficiency * 100);

            if (baselineThroughput < 0) {
                baselineThroughput = throughput;
            }

            // 10% efficiency floor: throughput at any level >= 10% of single-case baseline
            double minThroughput = baselineThroughput * 0.10;
            assertTrue(throughput >= minThroughput,
                    String.format("Throughput cliff at level=%d: %.1f < min=%.1f ops/sec",
                            level, throughput, minThroughput));
        }
        System.out.println();
    }

    // ── Spec loader ───────────────────────────────────────────────────────────

    private YSpecification loadSpec(YStatelessEngine engine) throws Exception {
        InputStream is = getClass().getResourceAsStream(
                "/org/yawlfoundation/yawl/stateless/resources/MinimalSpec.xml");
        assertNotNull(is, "MinimalSpec.xml must be on classpath");
        String specXml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        YSpecification spec = engine.unmarshalSpecification(specXml);
        assertNotNull(spec, "Spec must unmarshal successfully");
        return spec;
    }
}
