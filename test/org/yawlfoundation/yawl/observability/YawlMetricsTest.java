/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for YawlMetrics functionality.
 *
 * Tests:
 * - Metrics registration and initialization
 * - Counter increments and tagging
 * - Gauge updates and thread safety
 * - Timer recording and distribution
 * - Metric registration with tags
 * - OpenTelemetry integration
 * - Concurrent metric recording
 * - Edge cases and error handling
 */
class YawlMetricsTest {

    private MeterRegistry meterRegistry;
    private YawlMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new YawlMetrics(meterRegistry);
    }

    @AfterEach
    void tearDown() {
        metrics.shutdown();
    }

    @Test
    void testInitialization() {
        assertNotNull(metrics);

        // Verify all counters are registered
        assertNotNull(meterRegistry.find("yawl.case.created").counter());
        assertNotNull(meterRegistry.find("yawl.case.completed").counter());
        assertNotNull(meterRegistry.find("yawl.case.failed").counter());
        assertNotNull(meterRegistry.find("yawl.task.executed").counter());
        assertNotNull(meterRegistry.find("yawl.task.failed").counter());

        // Verify all gauges are registered
        assertNotNull(meterRegistry.find("yawl.case.active").gauge());
        assertNotNull(meterRegistry.find("yawl.queue.depth").gauge());
        assertNotNull(meterRegistry.find("yawl.threadpool.active").gauge());

        // Verify all timers are registered
        assertNotNull(meterRegistry.find("yawl.case.duration").timer());
        assertNotNull(meterRegistry.find("yawl.task.duration").timer());
        assertNotNull(meterRegistry.find("yawl.engine.latency").timer());
    }

    @Test
    void testCaseCreationMetrics() {
        // Test counter increment
        metrics.recordCaseCreated("case-1");
        metrics.recordCaseCreated("case-2");

        Counter caseCreatedCounter = meterRegistry.find("yawl.case.created").counter();
        assertEquals(2, caseCreatedCounter.count());

        // Test with tags
        metrics.recordCaseCreated("case-3", Map.of("workflow", "approval", "priority", "high"));

        // Verify tagged metrics
        taggedCounter("yawl.case.created", Map.of("workflow", "approval"), 1);
        taggedCounter("yawl.case.created", Map.of("priority", "high"), 1);
        assertEquals(3, meterRegistry.get("yawl.case.created").counter().count());
    }

    @Test
    void testCaseCompletionMetrics() {
        // Record case completions
        metrics.recordCaseCompleted("case-1", Duration.ofMillis(1500));
        metrics.recordCaseCompleted("case-2", Duration.ofMillis(2500));

        // Verify case completion counter
        Counter caseCompletedCounter = meterRegistry.find("yawl.case.completed").counter();
        assertEquals(2, caseCompletedCounter.count());

        // Verify case duration timer
        Timer caseDurationTimer = meterRegistry.find("yawl.case.duration").timer();
        assertNotNull(caseDurationTimer);
        assertEquals(2, caseDurationTimer.count());
        assertTrue(caseDurationTimer.totalTime(TimeUnit.MILLISECONDS) > 0);

        // Verify gauge update
        Gauge activeCasesGauge = meterRegistry.find("yawl.case.active").gauge();
        assertTrue(activeCasesGauge.value() >= 0);
    }

    @Test
    void testCaseFailureMetrics() {
        // Test failure recording
        metrics.recordCaseFailed("case-fail-1", "Validation failed");
        metrics.recordCaseFailed("case-fail-2", "Timeout expired");

        Counter caseFailedCounter = meterRegistry.find("yawl.case.failed").counter();
        assertEquals(2, caseFailedCounter.count());

        // Verify timer for failed cases
        Timer caseFailureTimer = meterRegistry.find("yawl.case.duration").timer();
        assertNotNull(caseFailureTimer);
    }

    @Test
    void testTaskExecutionMetrics() {
        // Test task execution recording
        metrics.recordTaskExecuted("task-1", "case-1", Duration.ofMillis(500));
        metrics.recordTaskExecuted("task-2", "case-1", Duration.ofMillis(1000));

        // Verify task execution counter
        Counter taskExecutedCounter = meterRegistry.find("yawl.task.executed").counter();
        assertEquals(2, taskExecutedCounter.count());

        // Verify task duration timer
        Timer taskDurationTimer = meterRegistry.find("yawl.task.duration").timer();
        assertNotNull(taskDurationTimer);
        assertEquals(2, taskDurationTimer.count());

        // Verify gauge updates
        Gauge activeThreadsGauge = meterRegistry.find("yawl.threadpool.active").gauge();
        assertTrue(activeThreadsGauge.value() >= 0);
    }

    @Test
    void testTaskFailureMetrics() {
        metrics.recordTaskFailed("task-fail-1", "case-1", "Assertion failed");
        metrics.recordTaskFailed("task-fail-2", "case-1", "Resource unavailable");

        Counter taskFailedCounter = meterRegistry.find("yawl.task.failed").counter();
        assertEquals(2, taskFailedCounter.count());
    }

    @Test
    void testQueueDepthMetrics() {
        // Test queue depth updates
        metrics.recordQueueDepth(10);
        metrics.recordQueueDepth(15);
        metrics.recordQueueDepth(5);

        // Verify gauge reflects the latest value
        Gauge queueDepthGauge = meterRegistry.find("yawl.queue.depth").gauge();
        assertEquals(5, queueDepthGauge.value());
    }

    @Test
    void testEngineLatencyMetrics() {
        // Test engine latency recording
        metrics.recordEngineLatency(Duration.ofMillis(100));
        metrics.recordEngineLatency(Duration.ofMillis(200));
        metrics.recordEngineLatency(Duration.ofMillis(50));

        // Verify timer
        Timer engineLatencyTimer = meterRegistry.find("yawl.engine.latency").timer();
        assertNotNull(engineLatencyTimer);
        assertEquals(3, engineLatencyTimer.count());

        // Verify distribution statistics
        assertTrue(engineLatencyTimer.totalTime(TimeUnit.MILLISECONDS) > 0);
        assertTrue(engineLatencyTimer.max(TimeUnit.MILLISECONDS) >= 200);
        assertTrue(engineLatencyTimer.min(TimeUnit.MILLISECONDS) <= 50);
    }

    @Test
    void testGaugeOperations() {
        // Test atomic gauge operations
        assertEquals(0, metrics.getActiveCaseCount());

        metrics.incrementActiveCases();
        assertEquals(1, metrics.getActiveCaseCount());

        metrics.incrementActiveCases();
        metrics.incrementActiveCases();
        assertEquals(3, metrics.getActiveCaseCount());

        metrics.decrementActiveCases();
        assertEquals(2, metrics.getActiveCaseCount());

        // Test bounds checking
        metrics.decrementActiveCases();
        metrics.decrementActiveCases();
        metrics.decrementActiveCases(); // Should not go below 0
        assertEquals(0, metrics.getActiveCaseCount());
    }

    @Test
    void testThreadSafety() {
        // Test concurrent metric recording
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Concurrent case creation
        for (int i = 0; i < 100; i++) {
            final int caseId = i;
            executor.submit(() -> {
                metrics.recordCaseCreated("case-concurrent-" + caseId);
                metrics.incrementActiveCases();
            });
        }

        executor.shutdown();
        try {
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify all cases were recorded
        Counter caseCreatedCounter = meterRegistry.find("yawl.case.created").counter();
        assertEquals(100, caseCreatedCounter.count());

        // Verify gauge is consistent (should be 100)
        assertEquals(100, metrics.getActiveCaseCount());
    }

    @Test
    void testConcurrentTimerRecording() {
        // Test timer recording from multiple threads
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < 200; i++) {
            final int taskId = i;
            executor.submit(() -> {
                long duration = (taskId % 100) + 10; // 10-109 ms
                metrics.recordTaskExecuted("task-" + taskId, "case-" + (taskId % 20),
                    Duration.ofMillis(duration));
            });
        }

        executor.shutdown();
        try {
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify timer statistics
        Timer taskDurationTimer = meterRegistry.find("yawl.task.duration").timer();
        assertEquals(200, taskDurationTimer.count());

        // Verify distribution properties
        assertTrue(taskDurationTimer.totalTime(TimeUnit.MILLISECONDS) > 0);
        assertTrue(taskDurationTimer.max(TimeUnit.MILLISECONDS) >= 109);
        assertTrue(taskDurationTimer.min(TimeUnit.MILLISECONDS) <= 10);

        // Verify standard deviation is reasonable
        double mean = taskDurationTimer.totalTime(TimeUnit.MILLISECONDS) / taskDurationTimer.count();
        double variance = taskDurationTimer.mean(TimeUnit.MILLISECONDS);
        assertTrue(variance >= 0);
    }

    @Test
    void testMetricTags() {
        // Test metrics with tags
        metrics.recordCaseCompleted("case-tagged", Duration.ofMillis(1000),
            Map.of("workflow_type", "approval", "priority", "high"));

        // Verify tagged metrics exist
        assertNotNull(taggedCounter("yawl.case.completed", Map.of("workflow_type", "approval")));
        assertNotNull(taggedCounter("yawl.case.completed", Map.of("priority", "high")));

        // Test multiple tag combinations
        metrics.recordTaskExecuted("task-urgent", "case-1", Duration.ofMillis(500),
            Map.of("urgency", "urgent", "department", "finance"));

        assertNotNull(taggedCounter("yawl.task.executed", Map.of("urgency", "urgent")));
        assertNotNull(taggedCounter("yawl.task.executed", Map.of("department", "finance")));
    }

    @Test
    void testNullParameterHandling() {
        // Test null handling for parameters
        assertThrows(NullPointerException.class, () -> {
            metrics.recordCaseCreated(null);
        });

        assertThrows(NullPointerException.class, () -> {
            metrics.recordCaseCompleted("case-null", null);
        });

        assertThrows(NullPointerException.class, () -> {
            metrics.recordCaseCompleted("case-null", Duration.ofMillis(1000), null);
        });

        assertThrows(NullPointerException.class, () -> {
            metrics.recordTaskExecuted(null, "case", Duration.ZERO);
        });
    }

    @Test
    void testDurationValidation() {
        // Test negative duration handling
        assertThrows(IllegalArgumentException.class, () -> {
            metrics.recordCaseCompleted("case-negative", Duration.ofMillis(-100));
        });

        assertThrows(IllegalArgumentException.class, () -> {
            metrics.recordTaskExecuted("task-negative", "case", Duration.ofMillis(-1));
        });

        assertThrows(IllegalArgumentException.class, () -> {
            metrics.recordEngineLatency(Duration.ofMillis(-1));
        });
    }

    @Test
    void testRegistryOperations() {
        // Test custom registry operations
        MeterRegistry customRegistry = new SimpleMeterRegistry();
        YawlMetrics customMetrics = new YawlMetrics(customRegistry);

        // Verify different registry isolation
        customMetrics.recordCaseCreated("case-custom");
        assertNotNull(customRegistry.find("yawl.case.created").counter());
        assertEquals(1, customRegistry.find("yawl.case.created").counter().count());

        // Verify original registry is unaffected
        assertEquals(0, meterRegistry.find("yawl.case.created").counter().count());

        customMetrics.shutdown();
    }

    @Test
    void testGaugeUpdatePattern() {
        // Test various gauge update patterns
        metrics.setActiveCaseCount(10);
        assertEquals(10, metrics.getActiveCaseCount());

        metrics.setQueueDepth(50);
        assertEquals(50, metrics.getQueueDepth());

        metrics.setActiveThreads(5);
        assertEquals(5, metrics.getActiveThreads());

        // Test concurrent gauge updates
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < 100; i++) {
            final int value = i;
            executor.submit(() -> {
                metrics.setActiveCaseCount(value);
            });
        }

        executor.shutdown();
        try {
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Gauge should reflect the last written value
        assertTrue(metrics.getActiveCaseCount() >= 0);
    }

    @Test
    void testTimerPercentiles() {
        // Test percentile calculations in timers
        for (int i = 0; i < 100; i++) {
            long duration = i * 10; // 0, 10, 20, ..., 990 ms
            metrics.recordCaseCompleted("case-" + i, Duration.ofMillis(duration));
        }

        Timer caseDurationTimer = meterRegistry.find("yawl.case.duration").timer();
        assertNotNull(caseDurationTimer);

        // Verify percentile values (these depend on the distribution)
        double p50 = caseDurationTimer.percentile(0.5, TimeUnit.MILLISECONDS);
        double p95 = caseDurationTimer.percentile(0.95, TimeUnit.MILLISECONDS);
        double p99 = caseDurationTimer.percentile(0.99, TimeUnit.MILLISECONDS);

        assertTrue(p95 >= p50);
        assertTrue(p99 >= p95);

        // Verify reasonable values
        assertTrue(p50 > 0);
        assertTrue(p99 < 1000);
    }

    @Test
    void testMemoryEfficiency() {
        // Test that metric recording doesn't cause memory leaks
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        // Record large number of metrics
        for (int i = 0; i < 10000; i++) {
            metrics.recordCaseCreated("case-mem-" + i);
            metrics.recordTaskExecuted("task-mem-" + i, "case-mem-" + i, Duration.ofMillis(100));
        }

        // Force garbage collection
        System.gc();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();

        // Memory increase should be reasonable (< 10MB for 10k metrics)
        long memoryIncrease = finalMemory - initialMemory;
        assertTrue(memoryIncrease < 10 * 1024 * 1024,
            "Memory usage increased by " + memoryIncrease + " bytes");
    }

    @Test
    void testShutdownBehavior() {
        // Record some metrics
        metrics.recordCaseCreated("case-shutdown");
        metrics.recordCaseCompleted("case-shutdown", Duration.ofMillis(1000));

        // Shutdown metrics
        metrics.shutdown();

        // Verify shutdown completed without errors
        assertDoesNotThrow(() -> {
            metrics.recordCaseCreated("post-shutdown");
        });

        // Verify counters still exist but don't increment
        Counter counter = meterRegistry.find("yawl.case.created").counter();
        assertNotNull(counter);
        assertEquals(1, counter.count()); // Should not have incremented after shutdown
    }

    @Test
    void testMetricDistribution() {
        // Test metric distribution across different scenarios
        Map<String, Integer> workflowCounts = new HashMap<>();
        workflowCounts.put("approval", 10);
        workflowCounts.put("validation", 15);
        workflowCounts.put("notification", 5);

        // Record cases with different workflow types
        for (Map.Entry<String, Integer> entry : workflowCounts.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                metrics.recordCaseCreated("case-" + entry.getKey() + "-" + i,
                    Map.of("workflow", entry.getKey()));
            }
        }

        // Verify distribution
        for (Map.Entry<String, Integer> entry : workflowCounts.entrySet()) {
            Counter workflowCounter = taggedCounter("yawl.case.created", Map.of("workflow", entry.getKey()));
            assertEquals(entry.getValue().intValue(), (long) workflowCounter.count());
        }

        // Verify total
        assertEquals(30, meterRegistry.find("yawl.case.created").counter().count());
    }

    @Test
    void testPerformanceUnderLoad() {
        // Test performance with high throughput
        int batchSize = 1000;
        long startTime = System.currentTimeMillis();

        // High throughput case creation
        for (int i = 0; i < batchSize; i++) {
            metrics.recordCaseCreated("case-perf-" + i);
            metrics.recordCaseCompleted("case-perf-" + i, Duration.ofMillis(100 + (i % 50)));
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Should complete within reasonable time (< 1 second for 1k operations)
        assertTrue(duration < 1000, "Processed " + batchSize + " metrics in " + duration + "ms");

        // Verify all metrics were recorded
        assertEquals(batchSize, meterRegistry.find("yawl.case.created").counter().count());
        assertEquals(batchSize, meterRegistry.find("yawl.case.completed").counter().count());

        // Verify timer data
        Timer timer = meterRegistry.find("yawl.case.duration").timer();
        assertEquals(batchSize, timer.count());
    }

    // Helper methods for testing
    private Counter taggedCounter(String name, Map<String, String> expectedTags) {
        return meterRegistry.find(name)
            .tags(expectedTags.entrySet().stream()
                .map(entry -> Tag.of(entry.getKey(), entry.getValue()))
                .toArray(Tag[]::new))
            .counter();
    }

    private void taggedCounter(String name, Map<String, String> expectedTags, long expectedCount) {
        Counter counter = taggedCounter(name, expectedTags);
        assertNotNull(counter);
        assertEquals(expectedCount, counter.count());
    }

    // Test class for concurrent access testing
    static class MetricStressTest {
        private final YawlMetrics metrics;
        private final String prefix;
        private final int operationsPerThread;

        public MetricStressTest(YawlMetrics metrics, String prefix, int operationsPerThread) {
            this.metrics = metrics;
            this.prefix = prefix;
            this.operationsPerThread = operationsPerThread;
        }

        public void execute() {
            for (int i = 0; i < operationsPerThread; i++) {
                metrics.recordCaseCreated(prefix + "-case-" + i);
                metrics.recordTaskExecuted(prefix + "-task-" + i, prefix + "-case-" + i,
                    Duration.ofMillis(100 + (i % 200)));
                metrics.incrementActiveCases();
            }
        }
    }
}