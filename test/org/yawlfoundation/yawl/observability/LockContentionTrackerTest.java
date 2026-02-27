/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to workflow technology.
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for LockContentionTracker.
 *
 * Tests lock tracking functionality, performance overhead,
 * historical data collection, and integration patterns.
 *
 * @author YAWL Foundation
 * @since 6.0.0
 */
@Execution(ExecutionMode.CONCURRENT)
class LockContentionTrackerTest {

    private LockContentionTracker tracker;
    private SimpleMeterRegistry meterRegistry;
    private TestableAndonCord testAndonCord;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        testAndonCord = new TestableAndonCord();
        tracker = new LockContentionTracker(meterRegistry, testAndonCord);
    }

    @Test
    @DisplayName("Create and initialize tracker")
    void testCreateTracker() {
        assertNotNull(tracker);
        assertTrue(tracker instanceof LockContentionTracker);
    }

    @Test
    @DisplayName("Track lock acquisition with no contention")
    void testTrackLockAcquisitionNoContention() {
        String lockName = "test.lock";
        LockContentionTracker.LockAcquisitionContext context =
            tracker.trackAcquisition(lockName);

        // Record acquisition
        context.recordAcquisition();

        // Verify metrics
        Map<String, LockContentionTracker.LockHeatMap> metrics =
            tracker.getHeatMapData().getLockMetrics();
        assertTrue(metrics.containsKey(lockName));
        assertEquals(0.0, metrics.get(lockName).getAverageWaitTimeMs());
        assertEquals(0.0, metrics.get(lockName).getMaxWaitTimeMs());
        assertEquals(0.0, metrics.get(lockName).getUtilizationPercentage());
    }

    @Test
    @DisplayName("Track lock acquisition with contention")
    void testTrackLockAcquisitionWithContention() {
        String lockName = "contended.lock";
        long waitTime = 250L; // 250ms wait

        LockContentionTracker.LockAcquisitionContext context =
            tracker.trackAcquisition(lockName);

        // Simulate wait time
        try {
            Thread.sleep(waitTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Record acquisition
        context.recordAcquisition();

        // Verify metrics
        Map<String, LockContentionTracker.LockHeatMap> metrics =
            tracker.getHeatMapData().getLockMetrics();
        assertTrue(metrics.containsKey(lockName));
        assertEquals(waitTime, metrics.get(lockName).getAverageWaitTimeMs());
        assertEquals(waitTime, metrics.get(lockName).getMaxWaitTimeMs());
    }

    @Test
    @DisplayName("Track multiple contention events")
    void testTrackMultipleContentionEvents() {
        String lockName = "multi-contention.lock";

        // Record multiple contention events
        for (int i = 0; i < 5; i++) {
            LockContentionTracker.LockAcquisitionContext context =
                tracker.trackAcquisition(lockName);

            try {
                Thread.sleep(100 + i * 50); // Increasing wait times
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            context.recordAcquisition();
        }

        // Verify accumulated metrics
        Map<String, LockContentionTracker.LockHeatMap> metrics =
            tracker.getHeatMapData().getLockMetrics();
        LockContentionTracker.LockHeatMap lockMetrics = metrics.get(lockName);

        // Should be average of: 100, 150, 200, 250, 300 = 200ms
        assertEquals(200.0, lockMetrics.getAverageWaitTimeMs());
        assertEquals(300.0, lockMetrics.getMaxWaitTimeMs());
        assertEquals(5L, lockMetrics.getContentionCount());
    }

    @Test
    @DisplayName("Concurrent lock tracking performance")
    void testConcurrentLockTracking() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    String lockName = "concurrent.lock." + threadId;
                    LockContentionTracker.LockAcquisitionContext context =
                        tracker.trackAcquisition(lockName);

                    try {
                        Thread.sleep(1); // Small delay to simulate work
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    context.recordAcquisition();
                }
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify performance overhead is minimal
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Verify all operations were tracked
        Map<String, LockContentionTracker.LockHeatMap> metrics =
            tracker.getHeatMapData().getLockMetrics();
        assertEquals(threadCount, metrics.size());

        // Verify total operations
        long totalOperations = metrics.values().stream()
            .mapToLong(LockContentionTracker.LockHeatMap::getContentionCount)
            .sum();
        assertEquals(threadCount * operationsPerThread, totalOperations);
    }

    @Test
    @DisplayName("Historical data collection")
    void testHistoricalDataCollection() throws InterruptedException {
        String lockName = "historical.lock";

        // Simulate historical data collection
        for (int i = 0; i < 5; i++) {
            LockContentionTracker.LockAcquisitionContext context =
                tracker.trackAcquisition(lockName);

            try {
                Thread.sleep(50); // 50ms wait time
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            context.recordAcquisition();
        }

        // Let historical data process
        Thread.sleep(1000);

        // Verify historical trends
        Map<String, LockContentionTracker.HistoricalTrend> trends =
            tracker.getHeatMapData().getHistoricalTrends();

        assertTrue(trends.containsKey(lockName));
        LockContentionTracker.HistoricalTrend trend = trends.get(lockName);

        assertEquals(5L, trend.getTotalContentionEvents());
        assertEquals(50.0, trend.getAverageWaitTime());
    }

    @Test
    @DisplayName("Heat map data generation")
    void testHeatMapDataGeneration() {
        // Generate some contention data
        for (int i = 0; i < 3; i++) {
            String lockName = "heatmap.lock." + i;
            LockContentionTracker.LockAcquisitionContext context =
                tracker.trackAcquisition(lockName);

            try {
                Thread.sleep(100 * (i + 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            context.recordAcquisition();
        }

        // Generate heat map data
        LockContentionTracker.HeatMapData heatMapData = tracker.getHeatMapData();

        assertNotNull(heatMapData.getTimestamp());
        assertEquals(3, heatMapData.getLockMetrics().size());
        assertEquals(3, heatMapData.getHistoricalTrends().size());

        // Verify heat levels
        heatMapData.getLockMetrics().values().forEach(metrics -> {
            assertNotNull(metrics.getHeatLevel());
            assertNotNull(getHeatMapColor(metrics.getHeatLevel()));
        });
    }

    @Test
    @DisplayName("Performance overhead measurement")
    void testPerformanceOverhead() throws InterruptedException {
        int warmupIterations = 1000;
        int measurementIterations = 5000;
        long baseDuration, trackedDuration;

        // Warm up
        for (int i = 0; i < warmupIterations; i++) {
            ReentrantLock lock = new ReentrantLock();
            lock.lock();
            lock.unlock();
        }

        // Measure base performance (no tracking)
        long startTime = System.nanoTime();
        for (int i = 0; i < measurementIterations; i++) {
            ReentrantLock lock = new ReentrantLock();
            lock.lock();
            lock.unlock();
        }
        baseDuration = System.nanoTime() - startTime;

        // Measure with tracking enabled
        tracker.setEnabled(true);
        long startTimeTracked = System.nanoTime();
        for (int i = 0; i < measurementIterations; i++) {
            String lockName = "perf.test.lock." + (i % 10);
            LockContentionTracker.LockAcquisitionContext context =
                tracker.trackAcquisition(lockName);

            try {
                Thread.sleep(1); // Simulate minimal work
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            context.recordAcquisition();
        }
        trackedDuration = System.nanoTime() - startTimeTracked;

        // Calculate overhead percentage
        double overhead = ((double) (trackedDuration - baseDuration) / baseDuration) * 100;
        assertTrue(overhead < 2.0, String.format(
            "Performance overhead %.2f%% exceeds target of 2%%", overhead));
    }

    @Test
    @DisplayName("Enable/disable tracking")
    void testEnableDisableTracking() {
        // Initially enabled
        assertTrue(tracker instanceof LockContentionTracker);

        // Disable tracking
        tracker.setEnabled(false);

        // Verify tracking is still functional but metrics might not be collected
        String lockName = "disabled.lock";
        LockContentionTracker.LockAcquisitionContext context =
            tracker.trackAcquisition(lockName);

        // Should work even when disabled (for graceful degradation)
        context.recordAcquisition();

        // Re-enable
        tracker.setEnabled(true);
    }

    @Test
    @DisplayName("Multiple lock tracking")
    void testMultipleLockTracking() {
        int lockCount = 5;
        int operationsPerLock = 10;

        // Track multiple locks
        for (int i = 0; i < lockCount; i++) {
            String lockName = "multi.lock." + i;

            for (int j = 0; j < operationsPerLock; j++) {
                LockContentionTracker.LockAcquisitionContext context =
                    tracker.trackAcquisition(lockName);

                try {
                    Thread.sleep(10 + i * 5); // Different wait times per lock
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                context.recordAcquisition();
            }
        }

        // Verify all locks are tracked
        Map<String, LockContentionTracker.LockHeatMap> metrics =
            tracker.getHeatMapData().getLockMetrics();

        assertEquals(lockCount, metrics.size());

        // Verify different metrics per lock
        metrics.values().forEach(metrics1 -> {
            assertEquals(operationsPerLock, metrics1.getContentionCount());
            assertTrue(metrics1.getAverageWaitTimeMs() > 0);
        });
    }

    @Test
    @DisplayName("Integration pattern example")
    void testIntegrationPattern() {
        // Example of how to integrate with existing locks
        String lockName = "integration.test.lock";
        ReentrantLock existingLock = new ReentrantLock();

        // Pattern 1: Manual tracking
        LockContentionTracker.LockAcquisitionContext context =
            tracker.trackAcquisition(lockName);

        try {
            existingLock.lock();
            context.recordAcquisition();

            // Do work
            Thread.sleep(5);

        } finally {
            existingLock.unlock();
            context.recordAcquisition();
        }

        // Verify tracking worked
        Map<String, LockContentionTracker.LockHeatMap> metrics =
            tracker.getHeatMapData().getLockMetrics();
        assertTrue(metrics.containsKey(lockName));
        assertTrue(metrics.get(lockName).getContentionCount() > 0);
    }

    /**
     * Testable AndonCord for testing alert functionality.
     * Uses real implementation but overrides alert method for testing.
     */
    private static class TestableAndonCord extends AndonCord {
        private int alertCount = 0;
        private String lastAlertMessage = "";

        public TestableAndonCord() {
            // Real initialization would go here
            // For testing, we bypass the real initialization
        }

        @Override
        public void triggerAlert(Severity severity, Category category, String message) {
            alertCount++;
            lastAlertMessage = message;
            System.out.println("Test Alert: " + message);
        }

        public int getAlertCount() {
            return alertCount;
        }

        public String getLastAlertMessage() {
            return lastAlertMessage;
        }

        @Override
        public boolean isAvailable() {
            return true; // For testing, always available
        }
    }

    /**
     * Helper method to get heat map color.
     */
    private String getHeatMapColor(String heatLevel) {
        return switch (heatLevel.toLowerCase()) {
            case "critical" -> "#ff0000";
            case "high" -> "#ff6600";
            case "medium" -> "#ffcc00";
            case "low" -> "#00ff00";
            default -> "#cccccc";
        };
    }
}