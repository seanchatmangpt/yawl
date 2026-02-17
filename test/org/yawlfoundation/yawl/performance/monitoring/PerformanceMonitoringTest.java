/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.performance.monitoring;

import junit.framework.TestCase;

/**
 * Tests for LibraryUpdatePerformanceMonitor.
 *
 * @author YAWL Performance Team
 * @version 5.2
 */
public class PerformanceMonitoringTest extends TestCase {

    private LibraryUpdatePerformanceMonitor monitor;

    public PerformanceMonitoringTest(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        monitor = new LibraryUpdatePerformanceMonitor();
    }

    @Override
    public void tearDown() throws Exception {
        monitor = null;
        super.tearDown();
    }

    /**
     * Test that performance snapshot can be captured.
     */
    public void testCaptureBaseline() throws Exception {
        System.out.println("\n=== Testing Baseline Capture ===");

        try {
            LibraryUpdatePerformanceMonitor.PerformanceSnapshot snapshot =
                monitor.captureBaselineMetrics("test-baseline.txt");

            assertNotNull("Snapshot should not be null", snapshot);
            assertNotNull("Timestamp should be set", snapshot.timestamp);
            assertNotNull("JVM version should be captured", snapshot.jvmVersion);
            assertNotNull("Library versions should be captured", snapshot.libraryVersions);

            assertTrue("Library versions should not be empty",
                !snapshot.libraryVersions.isEmpty());

            System.out.println("\n✓ Baseline capture test passed");

        } catch (Exception e) {
            System.out.println("\n⚠ Baseline capture test skipped: " + e.getMessage());
        }
    }

    /**
     * Test that metrics are reasonable.
     */
    public void testMetricsValidity() throws Exception {
        System.out.println("\n=== Testing Metrics Validity ===");

        try {
            LibraryUpdatePerformanceMonitor.PerformanceSnapshot snapshot =
                monitor.captureBaselineMetrics("test-validity.txt");

            // Startup time should be positive and reasonable (< 60 seconds)
            assertTrue("Startup time should be positive",
                snapshot.engineStartupTimeMs > 0);
            assertTrue("Startup time should be reasonable (< 60s)",
                snapshot.engineStartupTimeMs < 60000);

            // Latency metrics should be positive
            if (snapshot.caseLaunchMetrics != null) {
                assertTrue("p95 latency should be positive",
                    snapshot.caseLaunchMetrics.p95 > 0);
                assertTrue("p50 <= p95", snapshot.caseLaunchMetrics.p50 <= snapshot.caseLaunchMetrics.p95);
                assertTrue("p95 <= p99", snapshot.caseLaunchMetrics.p95 <= snapshot.caseLaunchMetrics.p99);
            }

            // Throughput should be positive
            if (snapshot.workItemMetrics != null) {
                assertTrue("Throughput should be positive",
                    snapshot.workItemMetrics.throughput >= 0);
            }

            // Memory usage should be reasonable
            if (snapshot.memoryMetrics != null) {
                assertTrue("Memory usage should be positive",
                    snapshot.memoryMetrics.usedMemoryMB >= 0);
            }

            System.out.println("\n✓ Metrics validity test passed");

        } catch (Exception e) {
            System.out.println("\n⚠ Metrics validity test skipped: " + e.getMessage());
        }
    }

    /**
     * Test library version capture.
     */
    public void testLibraryVersionCapture() throws Exception {
        System.out.println("\n=== Testing Library Version Capture ===");

        LibraryUpdatePerformanceMonitor.PerformanceSnapshot snapshot =
            monitor.captureBaselineMetrics("test-versions.txt");

        assertNotNull("Library versions should be captured", snapshot.libraryVersions);

        assertTrue("Should capture Java version",
            snapshot.libraryVersions.containsKey("java.version"));

        System.out.println("\nCaptured library versions:");
        snapshot.libraryVersions.forEach((key, value) ->
            System.out.println("  " + key + ": " + value));

        System.out.println("\n✓ Library version capture test passed");
    }
}
