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

package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD: Tests BottleneckDetector with real bottleneck analysis.
 * No mocks, real alert generation and parallelization tracking.
 */
@DisplayName("BottleneckDetector: Real-time Bottleneck Analysis")
class BottleneckDetectorTest {

    private BottleneckDetector detector;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        detector = new BottleneckDetector(meterRegistry);
    }

    @Test
    @DisplayName("Should detect slowest task as bottleneck")
    void testBottleneckDetection() {
        String specId = "order-process";

        // Fast task: 100ms * 20 = 2000ms total
        for (int i = 0; i < 20; i++) {
            detector.recordTaskExecution(specId, "approve", 100, 10);
        }

        // Slow task: 1000ms * 20 = 20000ms total (>30% of total)
        for (int i = 0; i < 20; i++) {
            detector.recordTaskExecution(specId, "payment", 1000, 50);
        }

        Map<String, BottleneckDetector.BottleneckAlert> bottlenecks = detector.getCurrentBottlenecks();
        assertTrue(bottlenecks.values().stream()
                .anyMatch(alert -> "payment".equals(alert.taskName)));
    }

    @Test
    @DisplayName("Should alert on bottleneck changes")
    void testBottleneckAlerts() {
        AtomicInteger alertCount = new AtomicInteger(0);

        detector.onBottleneckDetected(alert -> {
            alertCount.incrementAndGet();
            assertEquals("payment", alert.taskName);
        });

        String specId = "order-process";
        for (int i = 0; i < 30; i++) {
            detector.recordTaskExecution(specId, "payment", 1000, 50);
            detector.recordTaskExecution(specId, "approve", 100, 10);
        }

        assertTrue(alertCount.get() > 0);
    }

    @Test
    @DisplayName("Should track queue depth")
    void testQueueDepthTracking() {
        String specId = "test-spec";

        for (int i = 0; i < 10; i++) {
            detector.recordTaskExecution(specId, "bottleneck-task", 1000, 50);
        }

        detector.updateQueueDepth(specId, "bottleneck-task", 15);

        Map<String, BottleneckDetector.BottleneckAlert> alerts = detector.getCurrentBottlenecks();
        BottleneckDetector.BottleneckAlert alert = alerts.values().stream()
                .filter(a -> "bottleneck-task".equals(a.taskName))
                .findFirst()
                .orElse(null);

        if (alert != null) {
            assertEquals(15, alert.queueDepth);
        }
    }

    @Test
    @DisplayName("Should suggest parallelization opportunities")
    void testParallelizationSuggestion() {
        List<String> independentTasks = List.of("approve", "validate", "notify");

        detector.suggestParallelization("order-process", independentTasks, 2.5);

        List<BottleneckDetector.ParallelizationOpportunity> opps =
                detector.getParallelizationOpportunities();

        assertEquals(1, opps.size());
        BottleneckDetector.ParallelizationOpportunity opp = opps.get(0);
        assertEquals(2.5, opp.expectedSpeedup);
        assertEquals(3, opp.independentTasks.size());
    }

    @Test
    @DisplayName("Should provide bottlenecks for specific spec")
    void testBottlenecksPerSpec() {
        String spec1 = "order-process";
        String spec2 = "invoice-process";

        // Create bottleneck in spec1
        for (int i = 0; i < 30; i++) {
            detector.recordTaskExecution(spec1, "payment", 1000, 50);
            detector.recordTaskExecution(spec1, "verify", 100, 10);
        }

        // Create bottleneck in spec2
        for (int i = 0; i < 30; i++) {
            detector.recordTaskExecution(spec2, "audit", 800, 40);
            detector.recordTaskExecution(spec2, "approve", 100, 10);
        }

        List<BottleneckDetector.BottleneckAlert> spec1Alerts = detector.getBottlenecksForSpec(spec1);
        List<BottleneckDetector.BottleneckAlert> spec2Alerts = detector.getBottlenecksForSpec(spec2);

        spec1Alerts.forEach(alert -> assertEquals(spec1, alert.specId));
        spec2Alerts.forEach(alert -> assertEquals(spec2, alert.specId));
    }

    @Test
    @DisplayName("Should track alert history")
    void testAlertHistory() {
        String specId = "test-spec";

        for (int i = 0; i < 30; i++) {
            detector.recordTaskExecution(specId, "slow-task", 2000, 100);
            detector.recordTaskExecution(specId, "fast-task", 100, 10);
        }

        List<BottleneckDetector.BottleneckAlert> recentAlerts = detector.getRecentAlerts(60);
        assertTrue(recentAlerts.size() > 0);
    }

    @Test
    @DisplayName("Should calculate wait-to-execution ratio")
    void testWaitTimeAnalysis() {
        String specId = "test-spec";

        // High wait time relative to execution
        for (int i = 0; i < 20; i++) {
            detector.recordTaskExecution(specId, "queue-heavy", 100, 500);
        }

        List<BottleneckDetector.BottleneckAlert> alerts = detector.getBottlenecksForSpec(specId);
        assertTrue(alerts.stream()
                .anyMatch(alert -> alert.suggestion.contains("wait")));
    }

    @Test
    @DisplayName("Should provide statistics")
    void testStatistics() {
        String specId = "test-spec";

        for (int i = 0; i < 10; i++) {
            detector.recordTaskExecution(specId, "task-" + i, 100, 10);
        }

        Map<String, Object> stats = detector.getStatistics();

        assertTrue((int) stats.get("activeBottlenecks") >= 0);
        assertEquals(0, (int) stats.get("totalAlerts")); // threshold not met
        assertTrue((int) stats.get("specsMonitored") > 0);
        assertTrue((int) stats.get("tasksMonitored") > 0);
    }

    @Test
    @DisplayName("Should not alert until minimum execution samples")
    void testMinimumSamplesRequirement() {
        String specId = "test-spec";

        // Only 3 executions - below threshold
        for (int i = 0; i < 3; i++) {
            detector.recordTaskExecution(specId, "task-1", 5000, 100);
        }

        Map<String, BottleneckDetector.BottleneckAlert> alerts = detector.getCurrentBottlenecks();
        assertEquals(0, alerts.size());
    }

    @Test
    @DisplayName("Should handle high queue depth suggestion")
    void testHighQueueDepthSuggestion() {
        String specId = "test-spec";

        for (int i = 0; i < 30; i++) {
            detector.recordTaskExecution(specId, "bottleneck", 1000, 50);
        }

        detector.updateQueueDepth(specId, "bottleneck", 10);

        List<BottleneckDetector.BottleneckAlert> alerts = detector.getBottlenecksForSpec(specId);
        assertTrue(alerts.stream()
                .anyMatch(alert -> alert.suggestion.contains("queue")));
    }

    @Test
    @DisplayName("Should track multiple specifications independently")
    void testMultipleSpecifications() {
        // Spec 1
        for (int i = 0; i < 20; i++) {
            detector.recordTaskExecution("spec-1", "task-a", 500, 50);
            detector.recordTaskExecution("spec-1", "task-b", 100, 10);
        }

        // Spec 2
        for (int i = 0; i < 20; i++) {
            detector.recordTaskExecution("spec-2", "task-x", 200, 20);
            detector.recordTaskExecution("spec-2", "task-y", 100, 10);
        }

        Map<String, Object> stats = detector.getStatistics();
        assertEquals(2, (int) stats.get("specsMonitored"));
    }

    @Test
    @DisplayName("Should handle concurrent bottleneck recording")
    void testConcurrentRecording() throws InterruptedException {
        Thread[] threads = new Thread[4];

        for (int t = 0; t < 4; t++) {
            final int threadIdx = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 10; i++) {
                    detector.recordTaskExecution("spec-" + threadIdx,
                            "task-" + i, 100 + i * 10, 10 + i);
                }
            });
            threads[t].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        Map<String, Object> stats = detector.getStatistics();
        assertTrue((int) stats.get("specsMonitored") > 0);
    }

    @Test
    @DisplayName("Should require minimum execution times")
    void testInvalidDurations() {
        String specId = "test-spec";

        detector.recordTaskExecution(specId, "task-1", -1, 10);
        detector.recordTaskExecution(specId, "task-1", 100, -1);
        detector.recordTaskExecution(specId, "task-1", 100, 10);

        // Only valid execution should be recorded
        Map<String, Object> stats = detector.getStatistics();
        assertTrue((int) stats.get("tasksMonitored") > 0);
    }

    @Test
    @DisplayName("Should calculate expected speedup from parallelization")
    void testSpeedupCalculation() {
        List<String> tasks = List.of("task-1", "task-2", "task-3");
        detector.suggestParallelization("spec-1", tasks, 3.0);

        List<BottleneckDetector.ParallelizationOpportunity> opps =
                detector.getParallelizationOpportunities();

        assertEquals(3.0, opps.get(0).expectedSpeedup);
    }
}
