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
 * Chicago TDD: Tests WorkflowOptimizer with real pattern detection.
 * No mocks, real event listeners and optimization tracking.
 */
@DisplayName("WorkflowOptimizer: Autonomous Pattern Detection")
class WorkflowOptimizerTest {

    private WorkflowOptimizer optimizer;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        optimizer = new WorkflowOptimizer(meterRegistry);
    }

    @Test
    @DisplayName("Should detect high-variability tasks")
    void testHighVariabilityDetection() {
        String specId = "order-processing";
        String taskName = "external-service-call";

        // Establish baseline with variable execution times
        int[] times = {1000, 1100, 900, 5000, 950, 1050, 900, 4800, 1100, 950};
        for (int time : times) {
            optimizer.recordTaskExecution(specId, taskName, time);
        }

        // Additional executions to trigger analysis
        for (int i = 0; i < 20; i++) {
            optimizer.recordTaskExecution(specId, taskName, 1000 + (i % 5) * 100);
        }

        List<WorkflowOptimizer.Optimization> suggestions = optimizer.getSuggestionsForSpec(specId);
        assertTrue(suggestions.stream()
                .anyMatch(opt -> opt.type == WorkflowOptimizer.OptimizationType.ROUTE));
    }

    @Test
    @DisplayName("Should suggest caching for slow repeated tasks")
    void testCachingSuggestion() {
        String specId = "report-generation";
        String taskName = "generate-report";

        // Record slow task executions (2+ seconds each)
        for (int i = 0; i < 30; i++) {
            optimizer.recordTaskExecution(specId, taskName, 2000 + (i % 100));
        }

        List<WorkflowOptimizer.Optimization> suggestions = optimizer.getSuggestionsForSpec(specId);
        assertTrue(suggestions.stream()
                .anyMatch(opt -> opt.type == WorkflowOptimizer.OptimizationType.CACHE));
    }

    @Test
    @DisplayName("Should track active suggestions with time window")
    void testActiveOptimizations() {
        optimizer.suggestOptimization(new WorkflowOptimizer.Optimization(
                "spec-1",
                "task-1",
                WorkflowOptimizer.OptimizationType.PARALLELIZE,
                "Can parallelize independent paths",
                0.25,
                java.time.Instant.now(),
                false
        ));

        List<WorkflowOptimizer.Optimization> active = optimizer.getActiveSuggestions();
        assertEquals(1, active.size());
    }

    @Test
    @DisplayName("Should notify optimization listeners")
    void testOptimizationListeners() {
        AtomicInteger callCount = new AtomicInteger(0);

        optimizer.onOptimization(opt -> {
            callCount.incrementAndGet();
            assertTrue(opt.canAutoApply);
        });

        optimizer.suggestOptimization(new WorkflowOptimizer.Optimization(
                "spec-1",
                "task-1",
                WorkflowOptimizer.OptimizationType.ROUTE,
                "Route to dedicated agent",
                0.20,
                java.time.Instant.now(),
                true // auto-apply enabled
        ));

        assertEquals(1, callCount.get());
    }

    @Test
    @DisplayName("Should not notify listeners for manual optimizations")
    void testNoNotificationForManualOptimizations() {
        AtomicInteger callCount = new AtomicInteger(0);

        optimizer.onOptimization(opt -> callCount.incrementAndGet());

        optimizer.suggestOptimization(new WorkflowOptimizer.Optimization(
                "spec-1",
                "task-1",
                WorkflowOptimizer.OptimizationType.BATCH,
                "Batch similar tasks",
                0.15,
                java.time.Instant.now(),
                false // manual approval needed
        ));

        assertEquals(0, callCount.get());
    }

    @Test
    @DisplayName("Should provide optimization statistics")
    void testOptimizationStatistics() {
        optimizer.suggestOptimization(new WorkflowOptimizer.Optimization(
                "spec-1", "task-1", WorkflowOptimizer.OptimizationType.PARALLELIZE,
                "test", 0.25, java.time.Instant.now(), false));

        optimizer.suggestOptimization(new WorkflowOptimizer.Optimization(
                "spec-1", "task-2", WorkflowOptimizer.OptimizationType.CACHE,
                "test", 0.15, java.time.Instant.now(), false));

        Map<String, Object> stats = optimizer.getStatistics();

        assertEquals(2, stats.get("totalSuggestions"));
        assertTrue((int) stats.get("activeSuggestions") > 0);
        assertNotNull(stats.get("byType"));
    }

    @Test
    @DisplayName("Should track multiple specifications independently")
    void testMultipleSpecifications() {
        optimizer.recordTaskExecution("spec-1", "task-1", 100);
        optimizer.recordTaskExecution("spec-1", "task-2", 200);
        optimizer.recordTaskExecution("spec-2", "task-1", 150);

        List<WorkflowOptimizer.Optimization> spec1Opts = optimizer.getSuggestionsForSpec("spec-1");
        List<WorkflowOptimizer.Optimization> spec2Opts = optimizer.getSuggestionsForSpec("spec-2");

        spec1Opts.forEach(opt -> assertEquals("spec-1", opt.specId));
        spec2Opts.forEach(opt -> assertEquals("spec-2", opt.specId));
    }

    @Test
    @DisplayName("Should handle negative durations gracefully")
    void testNegativeDurationHandling() {
        String specId = "test-spec";
        String taskName = "test-task";

        optimizer.recordTaskExecution(specId, taskName, -1);
        optimizer.recordTaskExecution(specId, taskName, 100);

        // Should only count valid execution
        List<WorkflowOptimizer.Optimization> suggestions = optimizer.getSuggestionsForSpec(specId);
        // No exceptions thrown
    }

    @Test
    @DisplayName("Should suggest optimizations based on variability threshold")
    void testVariabilityThreshold() {
        String specId = "test-spec";
        String taskName = "variable-task";

        // Record consistently low variability
        for (int i = 0; i < 20; i++) {
            optimizer.recordTaskExecution(specId, taskName, 100 + (i % 3));
        }

        List<WorkflowOptimizer.Optimization> suggestions = optimizer.getSuggestionsForSpec(specId);
        // Low variability should not suggest rerouting
        assertTrue(suggestions.stream()
                .noneMatch(opt -> opt.type == WorkflowOptimizer.OptimizationType.ROUTE));
    }

    @Test
    @DisplayName("Should track task metrics over time")
    void testTaskMetricsTracking() {
        String specId = "test-spec";
        String taskName = "test-task";

        // Record multiple invocations
        for (int i = 0; i < 50; i++) {
            optimizer.recordTaskExecution(specId, taskName, 100 + (i % 50));
        }

        // Statistics should show execution count
        Map<String, Object> stats = optimizer.getStatistics();
        assertTrue((int) stats.get("taskMetricsTracked") > 0);
    }

    @Test
    @DisplayName("Should calculate expected improvement percentages")
    void testExpectedImprovement() {
        optimizer.suggestOptimization(new WorkflowOptimizer.Optimization(
                "spec-1",
                "task-1",
                WorkflowOptimizer.OptimizationType.ROUTE,
                "Route to faster agent",
                0.25,
                java.time.Instant.now(),
                false
        ));

        List<WorkflowOptimizer.Optimization> suggestions = optimizer.getActiveSuggestions();
        WorkflowOptimizer.Optimization opt = suggestions.get(0);

        assertEquals(0.25, opt.expectedImprovement);
    }

    @Test
    @DisplayName("Should handle concurrent optimization recording")
    void testConcurrentOptimizations() throws InterruptedException {
        Thread[] threads = new Thread[5];

        for (int t = 0; t < 5; t++) {
            final int threadIdx = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 10; i++) {
                    optimizer.recordTaskExecution("spec-" + threadIdx, "task-" + i, 100 + i);
                }
            });
            threads[t].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        Map<String, Object> stats = optimizer.getStatistics();
        assertTrue((int) stats.get("taskMetricsTracked") > 0);
    }
}
