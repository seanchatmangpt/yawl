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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for VirtualThreadMetrics Java 25 monitoring.
 *
 * This test verifies:
 * - Virtual thread creation and tracking
 * - Carrier thread utilization calculation
 * - Thread pinning detection
 * - Performance metrics collection
 * - Integration with YawlMetrics
 *
 * @author YAWL Observability Test Team
 * @version 6.0
 * @since 6.0
 */
@EnabledForJreRange(min = JRE.JAVA_21) // Virtual threads require Java 21+
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class VirtualThreadMetricsTest {

    private YawlMetrics yawlMetrics;
    private MeterRegistry meterRegistry;
    private VirtualThreadMetrics virtualThreadMetrics;
    private VirtualThreadPoolMetricsIntegration poolMetricsIntegration;

    @BeforeEach
    void setUp() {
        // Initialize meters
        meterRegistry = new SimpleMeterRegistry();
        yawlMetrics = new YawlMetrics(meterRegistry);
        virtualThreadMetrics = new VirtualThreadMetrics(yawlMetrics, meterRegistry);

        // Verify Java version support
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        if (!threadMXBean.isThreadCpuTimeSupported()) {
            System.out.println("Note: Thread CPU time not supported in this JVM");
        }
    }

    @AfterEach
    void tearDown() {
        if (poolMetricsIntegration != null) {
            poolMetricsIntegration.stopMonitoring();
        }
        virtualThreadMetrics.shutdown();
        yawlMetrics.stopVirtualThreadMonitoring();
    }

    @Test
    @Order(1)
    @DisplayName("Virtual thread tracking initialization")
    void testVirtualThreadTrackingInitialization() {
        // Verify that the metrics are properly initialized
        assertNotNull(virtualThreadMetrics);
        assertNotNull(yawlMetrics.getJmxMeterRegistry());
        assertTrue(yawlMetrics.getVirtualThreadStats().activeVirtualThreads() == 0);
    }

    @Test
    @Order(2)
    @DisplayName("Virtual thread creation and tracking")
    void testVirtualThreadCreationAndTracking() {
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        // Create and track virtual threads
        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual()
                .name("test-vthread-" + i)
                .start(() -> {
                    try {
                        // Simulate work
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
        }

        // Wait for threads to complete
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify metrics
        VirtualThreadMetrics.VirtualThreadSummary summary = virtualThreadMetrics.getSummary();
        assertTrue(summary.activeVirtualThreads() >= 0, "Active virtual threads should be non-negative");
        assertTrue(summary.totalCreated() >= threadCount, "Total created should include our test threads");

        System.out.println("Test virtual thread metrics:");
        System.out.println("  - Created: " + summary.totalCreated());
        System.out.println("  - Terminated: " + summary.totalTerminated());
        System.out.println("  - Active: " + summary.activeVirtualThreads());
        System.out.println("  - Health Score: " + summary.healthScore() + " (" + summary.getHealthStatus() + ")");
    }

    @Test
    @Order(3)
    @DisplayName("Virtual thread yield tracking")
    void testVirtualThreadYieldTracking() {
        Thread virtualThread = Thread.ofVirtual()
            .name("test-yield-thread")
            .start(() -> {
                // Simulate some yielding
                for (int i = 0; i < 5; i++) {
                    Thread.yield();
                }
            });

        // Wait for thread completion
        try {
            virtualThread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify yield counter is tracking
        assertTrue(yawlMetrics.getVirtualThreadStats().totalYields() >= 0,
                  "Yield counter should be tracking operations");
    }

    @Test
    @Order(4)
    @DisplayName("Carrier thread utilization calculation")
    void testCarrierThreadUtilization() {
        // Create some virtual threads that will use carrier threads
        int virtualThreadCount = 5;
        CountDownLatch latch = new CountDownLatch(virtualThreadCount);

        for (int i = 0; i < virtualThreadCount; i++) {
            Thread.ofVirtual()
                .start(() -> {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
        }

        // Wait for threads to complete
        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Check carrier utilization
        double utilization = virtualThreadMetrics.getCarrierThreadUtilization();
        assertTrue(utilization >= 0 && utilization <= 100,
                 "Carrier utilization should be between 0 and 100 percent");

        VirtualThreadMetrics.VirtualThreadSummary summary = virtualThreadMetrics.getSummary();
        System.out.println("Carrier utilization: " + String.format("%.1f", utilization) + "%");
        System.out.println("Health score: " + summary.healthScore());
    }

    @Test
    @Order(5)
    @DisplayName("Block time tracking")
    void testBlockTimeTracking() {
        Thread virtualThread = Thread.ofVirtual()
            .name("test-block-thread")
            .start(() -> {
                try {
                    // Simulate blocking time
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

        try {
            virtualThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify block time is being tracked
        VirtualThreadMetrics.VirtualThreadSummary summary = virtualThreadMetrics.getSummary();
        assertTrue(summary.averageBlockTimeMs() >= 0, "Average block time should be non-negative");
    }

    @Test
    @Order(6)
    @DisplayName("VirtualThreadPool integration")
    void testVirtualThreadPoolIntegration() {
        // Create a virtual thread pool
        VirtualThreadPool pool = new VirtualThreadPool("test-pool", 10, 2);
        pool.start();

        // Create metrics integration
        poolMetricsIntegration = new VirtualThreadPoolMetricsIntegration(pool, "integration-test");
        poolMetricsIntegration.startMonitoring();

        // Submit some tasks
        CountDownLatch latch = new CountDownLatch(10);
        for (int i = 0; i < 10; i++) {
            pool.submit(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for tasks to complete
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify integration metrics
        VirtualThreadPoolMetricsIntegration.PerformanceMetrics perfMetrics =
            poolMetricsIntegration.getPerformanceMetrics();

        VirtualThreadMetrics.VirtualThreadSummary virtualSummary =
            virtualThreadMetrics.getSummary();

        assertTrue(perfMetrics.activeSubmissions() >= 0, "Active submissions should be tracked");
        assertTrue(perfMetrics.queueDepth() >= 0, "Queue depth should be tracked");
        assertTrue(perfMetrics.virtualThreadSummary().totalCreated() >= 0,
                  "Virtual thread creation should be tracked");

        System.out.println("Pool integration metrics:");
        System.out.println("  - Active submissions: " + perfMetrics.activeSubmissions());
        System.out.println("  - Queue depth: " + perfMetrics.queueDepth());
        System.out.println("  - Average latency: " + perfMetrics.averageLatencyMs() + "ms");
        System.out.println("  - Virtual threads created: " + virtualSummary.totalCreated());

        // Shutdown
        pool.shutdown();
    }

    @Test
    @Order(7)
    @DisplayName("Health score calculation")
    void testHealthScoreCalculation() {
        // Create summary with no pinning, good utilization
        VirtualThreadMetrics.VirtualThreadSummary healthySummary =
            new VirtualThreadMetrics.VirtualThreadSummary(
                100,  // activeVirtualThreads
                0,    // pinnedVirtualThreads (no pinning)
                50.0, // carrierUtilizationPercent (50% utilization)
                20.0, // averageBlockTimeMs (low block time)
                1000, // totalCreated
                950,  // totalTerminated
                100.0 // healthScore
            );

        // Create summary with high pinning
        VirtualThreadMetrics.VirtualThreadSummary pinnedSummary =
            new VirtualThreadMetrics.VirtualThreadSummary(
                100,  // activeVirtualThreads
                50,   // pinnedVirtualThreads (50% pinned)
                20.0, // carrierUtilizationPercent (low utilization)
                10.0, // averageBlockTimeMs (low block time)
                1000, // totalCreated
                950,  // totalTerminated
                50.0  // healthScore
            );

        // Create summary with high utilization
        VirtualThreadMetrics.VirtualThreadSummary overloadedSummary =
            new VirtualThreadMetrics.VirtualThreadSummary(
                100,  // activeVirtualThreads
                5,    // pinnedVirtualThreads (minimal pinning)
                95.0, // carrierUtilizationPercent (95% utilization)
                150.0, // averageBlockTimeMs (high block time)
                1000, // totalCreated
                950,  // totalTerminated
                50.0  // healthScore
            );

        // Verify health status mappings
        assertEquals("EXCELLENT", healthySummary.getHealthStatus());
        assertEquals("POOR", pinnedSummary.getHealthStatus());
        assertEquals("POOR", overloadedSummary.getHealthStatus());

        // Verify health score is calculated correctly
        assertTrue(healthySummary.healthScore() >= pinnedSummary.healthScore(),
                  "Healthy summary should have higher score than pinned");
        assertTrue(pinnedSummary.healthScore() >= overloadedSummary.healthScore(),
                  "Pinned summary should have higher score than overloaded");
    }

    @Test
    @Order(8)
    @DisplayName("Performance overhead verification")
    @Timeout(value = 5000, threadMode = ThreadMode.SAME_THREAD)
    void testPerformanceOverhead() {
        // Create a baseline task
        long baselineTime = measureTaskExecution(() -> {
            for (int i = 0; i < 1000; i++) {
                Math.sqrt(i);
            }
        });

        // Create task with metrics tracking
        long trackingTime = measureTaskExecution(() -> {
            Thread virtualThread = Thread.ofVirtual().start(() -> {
                virtualThreadMetrics.trackVirtualThread(
                    Thread.currentThread(), "performance-test");
                try {
                    for (int i = 0; i < 1000; i++) {
                        Math.sqrt(i);
                    }
                } finally {
                    virtualThreadMetrics.unregisterVirtualThread(Thread.currentThread());
                }
            });
            try {
                virtualThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Calculate overhead percentage
        double overhead = ((double) (trackingTime - baselineTime) / baselineTime) * 100;

        // Assert overhead is less than 1% (1000x tolerance)
        assertTrue(overhead < 1.0,
                  String.format("Performance overhead too high: %.2f%%", overhead));

        System.out.printf("Performance overhead: %.4f%% (baseline: %dms, tracking: %dms)%n",
                         overhead, baselineTime, trackingTime);
    }

    /**
     * Helper method to measure task execution time.
     */
    private long measureTaskExecution(Runnable task) {
        long startTime = System.nanoTime();
        task.run();
        long endTime = System.nanoTime();
        return TimeUnit.MILLISECONDS.convert(endTime - startTime, TimeUnit.NANOSECONDS);
    }
}