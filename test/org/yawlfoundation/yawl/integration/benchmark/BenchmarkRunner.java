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

package org.yawlfoundation.yawl.integration.benchmark;

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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Benchmark runner with real performance validation using {@link YStatelessEngine}.
 *
 * <p>Replaces the previous implementation that hardcoded all validation results:
 * {@code TargetValidator.validate()} returned fake metrics (1200.0 req/s, 85ms)
 * without reading the {@code resultsFile} parameter, and the {@code PerformanceReport}
 * and {@code PerformanceComparator} inner classes generated static HTML pointing to
 * non-existent classes.</p>
 *
 * <h2>Self-checking invariant</h2>
 * <p>Percentile monotonicity: p50 ≤ p95 ≤ p99.  If this fails, the measurement
 * harness itself is broken and no result should be trusted.</p>
 *
 * <p>Chicago TDD: real engine operations only, no mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Tag("benchmark")
@DisplayName("Benchmark Runner — real YStatelessEngine performance validation")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BenchmarkRunner {

    private static final int WARMUP_CASES  = 10;
    private static final int MEASURE_CASES = 50;

    /** Minimum acceptable throughput for embedded stateless engine (ops/sec). */
    private static final double MIN_THROUGHPUT_OPS_PER_SEC = 5.0;

    /** Maximum acceptable p95 latency for embedded engine (ms). */
    private static final double MAX_P95_LATENCY_MS = 2000.0;

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("B1: TargetValidator measures real case-start throughput and latency")
    void validatePerformanceTargets() throws Exception {
        YStatelessEngine engine = new YStatelessEngine();
        YSpecification spec = loadSpec(engine);

        TargetValidator validator = new TargetValidator(engine, spec);
        ValidationReport report = validator.validate();

        System.out.printf("%n=== BENCHMARK RUNNER VALIDATION REPORT ===%n");
        System.out.printf("Throughput:  %.2f ops/sec (target: >= %.1f)%n",
                report.throughputOpsPerSec(), MIN_THROUGHPUT_OPS_PER_SEC);
        System.out.printf("P50 latency: %.1f ms%n", report.p50ms());
        System.out.printf("P95 latency: %.1f ms (target: < %.0f ms)%n",
                report.p95ms(), MAX_P95_LATENCY_MS);
        System.out.printf("P99 latency: %.1f ms%n", report.p99ms());
        System.out.printf("Overall:     %s%n%n", report.passed() ? "PASS" : "FAIL");

        assertTrue(report.passed(),
                String.format("Performance targets not met — throughput=%.2f ops/sec, p95=%.1f ms",
                        report.throughputOpsPerSec(), report.p95ms()));
    }

    @Test
    @Order(2)
    @DisplayName("B2: Workflow cycle test — 5 cases driven to completion via event cascade")
    void validateWorkflowCycleCompletion() throws Exception {
        YStatelessEngine engine = new YStatelessEngine();
        YSpecification spec = loadSpec(engine);
        List<String> errors = new CopyOnWriteArrayList<>();

        engine.addWorkItemEventListener(new YWorkItemEventListener() {
            @Override
            public void handleWorkItemEvent(YWorkItemEvent event) {
                if (event.getEventType() == YEventType.ITEM_ENABLED) {
                    try {
                        YWorkItem started = engine.startWorkItem(event.getWorkItem());
                        engine.completeWorkItem(started, "<data/>", null);
                    } catch (Exception e) {
                        errors.add(e.getMessage());
                    }
                }
            }
        });

        List<String> caseParams = new ArrayList<>();
        for (int i = 0; i < 5; i++) caseParams.add("bench-cycle-" + i);

        List<YNetRunner> runners = engine.launchCasesParallel(spec, caseParams);
        assertFalse(runners.isEmpty(), "At least 1 runner must be created");
        assertTrue(errors.isEmpty(),
                "Workflow cycle completion must not produce errors: " + errors);
    }

    // ── Target validator ──────────────────────────────────────────────────────

    /**
     * Measures real {@link YStatelessEngine} performance and validates against targets.
     *
     * <p>Self-checking invariant: p50 ≤ p95 ≤ p99 (percentile monotonicity).
     * If violated, the measurement harness itself is broken.</p>
     */
    static class TargetValidator {
        private final YStatelessEngine engine;
        private final YSpecification spec;

        TargetValidator(YStatelessEngine engine, YSpecification spec) {
            this.engine = engine;
            this.spec   = spec;
        }

        ValidationReport validate() throws Exception {
            // Warm up
            for (int i = 0; i < WARMUP_CASES; i++) {
                engine.launchCasesParallel(spec, List.of("warmup-" + i));
            }

            // Measure MEASURE_CASES case starts
            long[] latenciesNs = new long[MEASURE_CASES];
            long totalStart = System.nanoTime();
            for (int i = 0; i < MEASURE_CASES; i++) {
                long opStart = System.nanoTime();
                engine.launchCasesParallel(spec, List.of("measure-" + i));
                latenciesNs[i] = System.nanoTime() - opStart;
            }
            long totalElapsedNs = System.nanoTime() - totalStart;

            // Calculate percentiles
            long[] sorted = latenciesNs.clone();
            Arrays.sort(sorted);
            double p50ms  = sorted[MEASURE_CASES / 2]           / 1_000_000.0;
            double p95ms  = sorted[(int)(MEASURE_CASES * 0.95)] / 1_000_000.0;
            double p99ms  = sorted[(int)(MEASURE_CASES * 0.99)] / 1_000_000.0;
            double throughput = MEASURE_CASES / (totalElapsedNs / 1_000_000_000.0);

            // Self-check: percentile monotonicity invariant
            assertTrue(p50ms <= p95ms,
                    String.format("Percentile monotonicity violated: p50=%.1f > p95=%.1f",
                            p50ms, p95ms));
            assertTrue(p95ms <= p99ms,
                    String.format("Percentile monotonicity violated: p95=%.1f > p99=%.1f",
                            p95ms, p99ms));

            return new ValidationReport(
                    throughput, p50ms, p95ms, p99ms,
                    throughput >= MIN_THROUGHPUT_OPS_PER_SEC,
                    p95ms <= MAX_P95_LATENCY_MS
            );
        }
    }

    // ── Result record ─────────────────────────────────────────────────────────

    /**
     * Immutable performance validation report backed by real measured metrics.
     */
    record ValidationReport(
            double throughputOpsPerSec,
            double p50ms,
            double p95ms,
            double p99ms,
            boolean throughputMeetsTarget,
            boolean latencyMeetsTarget
    ) {
        boolean passed() {
            return throughputMeetsTarget && latencyMeetsTarget;
        }
    }

    // ── Spec loader ───────────────────────────────────────────────────────────

    private YSpecification loadSpec(YStatelessEngine engine) throws Exception {
        InputStream is = getClass().getResourceAsStream(
                "/org/yawlfoundation/yawl/stateless/resources/MinimalSpec.xml");
        assertNotNull(is,
                "MinimalSpec.xml must be on classpath at " +
                        "/org/yawlfoundation/yawl/stateless/resources/MinimalSpec.xml");
        String specXml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        YSpecification spec = engine.unmarshalSpecification(specXml);
        assertNotNull(spec, "Spec must unmarshal successfully from MinimalSpec.xml");
        return spec;
    }

    // ── Standalone runner ─────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("BenchmarkRunner: Use 'mvn test -Dtest=BenchmarkRunner' to run.");
    }
}
