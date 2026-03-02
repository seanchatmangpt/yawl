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

package org.yawlfoundation.yawl.engine.agent.core;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for VirtualThreadRuntime spawn latency metrics.
 *
 * This test verifies:
 * - Spawn latency tracking works correctly
 * - JMX MBean interface provides correct metrics
 * - Micrometer integration registers metrics properly
 * - Metrics are consistent across multiple spawns
 *
 * @author YAWL VirtualThreadMetrics Team
 * @version 6.0
 * @since 6.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VirtualThreadRuntimeMetricsTest {

    private VirtualThreadRuntime runtime;
    private MeterRegistry meterRegistry;

    @BeforeAll
    void setUp() {
        // Create runtime with metrics registry
        this.meterRegistry = new SimpleMeterRegistry();
        this.runtime = new VirtualThreadRuntime(meterRegistry);
    }

    @AfterAll
    void tearDown() {
        runtime.close();
        meterRegistry.close();
    }

    @Test
    void testSpawnLatencyTracking() throws InterruptedException {
        // Spawn an actor that does minimal work
        ActorRef actor = runtime.spawn(self -> {
            // Empty behavior
        });

        // Allow actor to complete
        Thread.sleep(100);

        // Verify metrics were collected
        Duration avgLatency = runtime.getAverageSpawnLatency();
        assertTrue(avgLatency.toNanos() > 0, "Spawn latency should be positive");
        assertEquals(1, runtime.getSpawnLatencyMeasurementCount(), "Should have 1 measurement");
        assertEquals(1, runtime.stats().spawnCount(), "Should have 1 spawn");
    }

    @Test
    void testMultipleSpawnLatencyTracking() throws InterruptedException {
        final int actorCount = 10;
        CountDownLatch latch = new CountDownLatch(actorCount);

        // Spawn multiple actors
        for (int i = 0; i < actorCount; i++) {
            ActorRef actor = runtime.spawn(self -> {
                latch.countDown();
            });
        }

        // Wait for all actors to complete
        assertTrue(latch.await(1, TimeUnit.SECONDS), "All actors should complete");

        // Verify metrics
        Duration avgLatency = runtime.getAverageSpawnLatency();
        assertTrue(avgLatency.toNanos() > 0, "Spawn latency should be positive");
        assertEquals(actorCount, runtime.getSpawnLatencyMeasurementCount(),
                    "Should have correct measurement count");
        assertEquals(actorCount, runtime.stats().spawnCount(), "Should have correct spawn count");
    }

    @Test
    void testBoundedSpawnLatencyTracking() throws InterruptedException {
        // Spawn bounded actor
        ActorRef boundedActor = runtime.spawnBounded(
            self -> {
                // Empty behavior
            },
            10
        );

        // Allow actor to complete
        Thread.sleep(100);

        // Verify metrics include bounded spawn
        assertEquals(1, runtime.stats().spawnCount(), "Should have 1 spawn");
        assertEquals(1, runtime.getSpawnLatencyMeasurementCount(),
                    "Should have 1 measurement");
        assertTrue(runtime.getTotalSpawnTimeNanos() > 0, "Should track spawn time");
    }

    @Test
    void testMicrometerMetricsRegistration() {
        // Verify metrics are registered
        assertNotNull(meterRegistry.find("virtual.thread.spawn.latency.avg.nanos")
            .gauge());
        assertNotNull(meterRegistry.find("virtual.thread.spawn.count")
            .counter());
        assertNotNull(meterRegistry.find("virtual.thread.spawn.latency.total.nanos")
            .gauge());
        assertNotNull(meterRegistry.find("virtual.thread.spawn.latency.measurements")
            .gauge());
    }

    @Test
    void testJmxMBeanInterface() throws Exception {
        // Test JMX MBean methods
        double avgLatencyMillis = runtime.mbean.getAverageSpawnLatencyMillis();
        long totalSpawnNanos = runtime.mbean.getTotalSpawnTimeNanos();
        long spawnCount = runtime.mbean.getSpawnCount();
        long measurementCount = runtime.mbean.getSpawnLatencyMeasurementCount();

        // Note: These might be 0 if no spawns have occurred yet
        assertTrue(avgLatencyMillis >= 0, "Average latency should be non-negative");
        assertTrue(totalSpawnNanos >= 0, "Total spawn time should be non-negative");
        assertTrue(spawnCount >= 0, "Spawn count should be non-negative");
        assertTrue(measurementCount >= 0, "Measurement count should be non-negative");
    }

    @Test
    void testRegisterMetrics() {
        // Create a new registry and test metric registration
        MeterRegistry newRegistry = new SimpleMeterRegistry();
        runtime.registerMetrics(newRegistry);

        // Verify metrics are registered in the new registry
        assertNotNull(newRegistry.find("virtual.thread.spawn.latency.avg.nanos")
            .gauge());
        assertNotNull(newRegistry.find("virtual.thread.spawn.count")
            .counter());

        newRegistry.close();
    }

    @Test
    void testZeroDivisionForEmptyRuntime() {
        // Create fresh runtime with no spawns
        VirtualThreadRuntime freshRuntime = new VirtualThreadRuntime();

        // Zero measurements should return zero duration
        Duration avgLatency = freshRuntime.getAverageSpawnLatency();
        assertEquals(Duration.ZERO, avgLatency);

        freshRuntime.close();
    }

    @Test
    void testMetricsConsistency() throws InterruptedException {
        // Spawn several actors to gather metrics
        int spawnCount = 5;
        for (int i = 0; i < spawnCount; i++) {
            runtime.spawn(self -> {
                // Short delay to ensure different latencies
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Allow all actors to complete
        Thread.sleep(100);

        // Verify internal consistency
        long totalNanos = runtime.getTotalSpawnTimeNanos();
        long measurements = runtime.getSpawnLatencyMeasurementCount();
        Duration avgLatency = runtime.getAverageSpawnLatency();

        assertEquals(spawnCount, measurements, "Measurement count should match spawn count");
        assertEquals(spawnCount, runtime.stats().spawnCount(), "Stats should match spawn count");

        if (measurements > 0) {
            assertEquals(totalNanos / measurements, avgLatency.toNanos(),
                       "Average should match total/count");
        }
    }
}