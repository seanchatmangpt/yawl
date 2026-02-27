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

package org.yawlfoundation.yawl.integration.a2a.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VirtualThreadMetrics.
 *
 * <p>Tests verify:</p>
 * <ul>
 *   <li>Server lifecycle tracking</li>
 *   <li>Request counting</li>
 *   <li>Latency percentile calculations</li>
 *   <li>Thread safety under concurrent access</li>
 *   <li>JSON export format</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
class VirtualThreadMetricsTest {

    private VirtualThreadMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new VirtualThreadMetrics();
    }

    // =========================================================================
    // Server lifecycle tests
    // =========================================================================

    @Test
    @DisplayName("recordServerStart should set start time")
    void testRecordServerStart() {
        metrics.recordServerStart();

        VirtualThreadMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
        assertNotNull(snapshot.serverStartTime(), "Start time should be set");
        assertNull(snapshot.serverStopTime(), "Stop time should be null");
        assertTrue(snapshot.uptimeSeconds() >= 0, "Uptime should be non-negative");
    }

    @Test
    @DisplayName("recordServerStop should set stop time")
    void testRecordServerStop() {
        metrics.recordServerStart();

        // Wait a bit to ensure measurable uptime
        sleep(10);

        metrics.recordServerStop();

        VirtualThreadMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
        assertNotNull(snapshot.serverStartTime(), "Start time should still be set");
        assertNotNull(snapshot.serverStopTime(), "Stop time should be set");
        assertTrue(snapshot.uptimeSeconds() > 0, "Uptime should be positive");
    }

    // =========================================================================
    // Request counting tests
    // =========================================================================

    @Test
    @DisplayName("recordRequestStart should increment total and active requests")
    void testRecordRequestStart() {
        metrics.recordServerStart();

        metrics.recordRequestStart();
        metrics.recordRequestStart();

        VirtualThreadMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
        assertEquals(2, snapshot.totalRequests(), "Total requests should be 2");
        assertEquals(2, snapshot.activeRequests(), "Active requests should be 2");
    }

    @Test
    @DisplayName("recordRequestComplete should decrement active and increment successful")
    void testRecordRequestComplete() {
        metrics.recordServerStart();

        metrics.recordRequestStart();
        metrics.recordRequestComplete(1_000_000); // 1ms in nanos

        VirtualThreadMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
        assertEquals(1, snapshot.totalRequests(), "Total requests should be 1");
        assertEquals(1, snapshot.successfulRequests(), "Successful requests should be 1");
        assertEquals(0, snapshot.activeRequests(), "Active requests should be 0");
    }

    @Test
    @DisplayName("recordRequestFailure should decrement active and increment failed")
    void testRecordRequestFailure() {
        metrics.recordServerStart();

        metrics.recordRequestStart();
        metrics.recordRequestFailure();

        VirtualThreadMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
        assertEquals(1, snapshot.totalRequests(), "Total requests should be 1");
        assertEquals(1, snapshot.failedRequests(), "Failed requests should be 1");
        assertEquals(0, snapshot.activeRequests(), "Active requests should be 0");
    }

    // =========================================================================
    // Latency calculation tests
    // =========================================================================

    @Test
    @DisplayName("Latency percentiles should be calculated correctly")
    void testLatencyPercentiles() {
        metrics.recordServerStart();

        // Record 100 samples with latencies from 1ms to 100ms
        for (int i = 1; i <= 100; i++) {
            metrics.recordRequestStart();
            metrics.recordRequestComplete(i * 1_000_000L); // i ms in nanos
        }

        VirtualThreadMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();

        assertEquals(100, snapshot.latencySampleCount(), "Should have 100 samples");
        assertEquals(1.0, snapshot.minLatencyMillis(), 0.1, "Min should be ~1ms");
        assertEquals(100.0, snapshot.maxLatencyMillis(), 0.1, "Max should be ~100ms");

        // P50 should be around 50ms
        assertTrue(snapshot.p50LatencyMillis() >= 45 && snapshot.p50LatencyMillis() <= 55,
                   "P50 should be around 50ms, got " + snapshot.p50LatencyMillis());

        // P99 should be around 99ms
        assertTrue(snapshot.p99LatencyMillis() >= 95 && snapshot.p99LatencyMillis() <= 100,
                   "P99 should be around 99ms, got " + snapshot.p99LatencyMillis());
    }

    @Test
    @DisplayName("Empty latency samples should return zero")
    void testEmptyLatencySamples() {
        metrics.recordServerStart();

        VirtualThreadMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();

        assertEquals(0, snapshot.latencySampleCount(), "Sample count should be 0");
        assertEquals(0.0, snapshot.avgLatencyMillis(), 0.001, "Avg should be 0");
        assertEquals(0.0, snapshot.p50LatencyMillis(), 0.001, "P50 should be 0");
    }

    // =========================================================================
    // Thread safety tests
    // =========================================================================

    @Test
    @DisplayName("Metrics should be thread-safe under concurrent access")
    void testThreadSafety() throws Exception {
        metrics.recordServerStart();

        int numThreads = 100;
        int requestsPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger errors = new AtomicInteger(0);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < numThreads; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < requestsPerThread; i++) {
                            metrics.recordRequestStart();
                            // Simulate some work
                            long latency = (long) (Math.random() * 10_000_000);
                            metrics.recordRequestComplete(latency);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);

            assertTrue(completed, "All threads should complete");
            assertEquals(0, errors.get(), "No errors should occur");

            VirtualThreadMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
            assertEquals(numThreads * requestsPerThread, snapshot.totalRequests(),
                         "All requests should be counted");
            assertEquals(numThreads * requestsPerThread, snapshot.successfulRequests(),
                         "All requests should be successful");
            assertEquals(0, snapshot.activeRequests(),
                         "No active requests should remain after completion");
        }
    }

    // =========================================================================
    // Summary and JSON tests
    // =========================================================================

    @Test
    @DisplayName("getSummary should return formatted string")
    void testGetSummary() {
        metrics.recordServerStart();
        metrics.recordRequestStart();
        metrics.recordRequestComplete(5_000_000); // 5ms

        String summary = metrics.getSummary();

        assertNotNull(summary, "Summary should not be null");
        assertTrue(summary.contains("VirtualThreadMetrics"), "Summary should contain class name");
        assertTrue(summary.contains("requests=1"), "Summary should show request count");
    }

    @Test
    @DisplayName("toJson should return valid JSON")
    void testToJson() {
        metrics.recordServerStart();
        metrics.recordRequestStart();
        metrics.recordRequestComplete(5_000_000); // 5ms

        String json = metrics.toJson();

        assertNotNull(json, "JSON should not be null");
        assertTrue(json.startsWith("{"), "JSON should start with {");
        assertTrue(json.endsWith("}"), "JSON should end with }");
        assertTrue(json.contains("\"requests\":"), "JSON should contain requests section");
        assertTrue(json.contains("\"latency\":"), "JSON should contain latency section");
        assertTrue(json.contains("\"threads\":"), "JSON should contain threads section");
        assertTrue(json.contains("\"server\":"), "JSON should contain server section");
    }

    @Test
    @DisplayName("MetricsSnapshot helper methods should work correctly")
    void testMetricsSnapshotHelpers() {
        metrics.recordServerStart();

        // 100 requests over 10 seconds uptime
        for (int i = 0; i < 100; i++) {
            metrics.recordRequestStart();
            metrics.recordRequestComplete(1_000_000);
        }
        // 1 failure
        metrics.recordRequestStart();
        metrics.recordRequestFailure();

        VirtualThreadMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();

        // Test successRate
        double expectedRate = 100.0 * 100 / 101;
        assertEquals(expectedRate, snapshot.successRate(), 0.1,
                     "Success rate should be ~99%");
    }

    @Test
    @DisplayName("Metrics should respect MAX_LATENCY_SAMPLES bound")
    void testMaxLatencySamplesBound() {
        metrics.recordServerStart();

        // Record more samples than the maximum
        for (int i = 0; i < 15000; i++) {
            metrics.recordRequestStart();
            metrics.recordRequestComplete(1_000_000);
        }

        VirtualThreadMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();

        // Should be capped at MAX_LATENCY_SAMPLES (10000)
        assertTrue(snapshot.latencySampleCount() <= 10000,
                   "Sample count should be capped at 10000, got " + snapshot.latencySampleCount());
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
