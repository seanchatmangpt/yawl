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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for SLODashboard functionality.
 *
 * Tests:
 * - Dashboard initialization
 * - Data refresh and snapshot management
 * - HTML report generation
 * - JSON report generation
 * - Historical data queries
 * - Performance under load
 */
class SLODashboardTest {

    private MeterRegistry meterRegistry;
    private AndonCord andonCord;
    private SLOTracker sloTracker;
    private SLODashboard dashboard;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        andonCord = mock(AndonCord.class);
        sloTracker = new SLOTracker(meterRegistry, andonCord);
        dashboard = new SLODashboard(sloTracker, meterRegistry);
    }

    @AfterEach
    void tearDown() {
        dashboard.stop();
    }

    @Test
    void testInitialization() {
        assertNotNull(dashboard);
        assertEquals(5, dashboard.getCurrentSnapshot().getStatusMap().size());
    }

    @Test
    void testDashboardStartAndStop() {
        // Start dashboard
        dashboard.start();
        assertEquals(SLODashboard.DashboardSnapshot.Status.RUNNING, dashboard.getStatus());

        // Generate some data
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "test");
        sloTracker.recordCaseCompletion("case-dashboard-1", 1000, context);

        // Stop dashboard
        dashboard.stop();
        assertEquals(SLODashboard.DashboardSnapshot.Status.STOPPED, dashboard.getStatus());
    }

    @Test
    void testCurrentSnapshot() {
        // Generate some data
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "test");
        sloTracker.recordCaseCompletion("case-snapshot-1", 1000, context);

        // Get current snapshot
        SLODashboard.DashboardSnapshot snapshot = dashboard.getCurrentSnapshot();

        assertNotNull(snapshot);
        assertNotNull(snapshot.getStatusMap());
        assertFalse(snapshot.getStatusMap().isEmpty());
        assertEquals(Instant.class, snapshot.getTimestamp().getClass());
    }

    @Test
    void testHistoricalDataQuery() {
        // Generate some data
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "test");

        for (int i = 0; i < 10; i++) {
            sloTracker.recordCaseCompletion("case-hist-" + i, i * 1000, context);
        }

        // Query historical data
        Instant from = Instant.now().minusHours(1);
        Instant to = Instant.now();
        List<SLODashboard.DashboardSnapshot> historical = dashboard.getHistoricalData(from, to);

        assertNotNull(historical);
        assertFalse(historical.isEmpty());
    }

    @Test
    void testJsonReportGeneration() {
        // Generate some data
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "test");
        sloTracker.recordCaseCompletion("case-json-1", 1000, context);

        // Generate JSON report
        Instant from = Instant.now().minusHours(1);
        Instant to = Instant.now();
        String jsonReport = dashboard.generateJsonReport(from, to);

        assertNotNull(jsonReport);
        assertFalse(jsonReport.isEmpty());
        assertTrue(jsonReport.contains("\"current\""));
        assertTrue(jsonReport.contains("\"historical\""));
        assertTrue(jsonReport.contains("\"series\""));
    }

    @Test
    void testHtmlReportGeneration() {
        // Generate some data
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "test");
        sloTracker.recordCaseCompletion("case-html-1", 1000, context);

        // Generate HTML report
        String htmlReport = dashboard.generateHtmlReport();

        assertNotNull(htmlReport);
        assertFalse(htmlReport.isEmpty());
        assertTrue(htmlReport.contains("<!DOCTYPE html>"));
        assertTrue(htmlReport.contains("YAWL SLO Dashboard"));
        assertTrue(htmlReport.contains("chart-container"));
        assertTrue(htmlReport.contains("<canvas"));
    }

    @Test
    void testSeriesDataQuery() {
        // Generate some data
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "test");

        for (int i = 0; i < 20; i++) {
            sloTracker.recordCaseCompletion("case-series-" + i, i * 1000, context);
        }

        // Query series data
        Instant from = Instant.now().minusHours(1);
        Instant to = Instant.now();
        Map<String, List<SLODashboard.DataPoint>> seriesData =
            dashboard.getSeriesData(SLOTracker.SLO_CASE_COMPLETION, from, to);

        assertNotNull(seriesData);
        assertFalse(seriesData.isEmpty());
        assertTrue(seriesData.containsKey(SLOTracker.SLO_CASE_COMPLETION));
        assertFalse(seriesData.get(SLOTracker.SLO_CASE_COMPLETION).isEmpty());
    }

    @Test
    void testEmptyHistoricalData() {
        // Query historical data with no data
        Instant from = Instant.now().minusHours(1);
        Instant to = Instant.now();
        List<SLODashboard.DashboardSnapshot> historical = dashboard.getHistoricalData(from, to);

        assertNotNull(historical);
        assertTrue(historical.isEmpty());
    }

    @Test
    void testInvalidTimeRange() {
        // Query with invalid time range (to before from)
        Instant from = Instant.now();
        Instant to = from.minusHours(1);
        List<SLODashboard.DashboardSnapshot> historical = dashboard.getHistoricalData(from, to);

        assertNotNull(historical);
        assertTrue(historical.isEmpty());
    }

    @Test
    void testConcurrentAccess() {
        // Start dashboard
        dashboard.start();

        // Generate data concurrently
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < 100; i++) {
            final int threadId = i;
            executor.submit(() -> {
                Map<String, String> context = new HashMap<>();
                context.put("thread_id", String.valueOf(threadId));
                sloTracker.recordCaseCompletion("case-concurrent-" + threadId, threadId * 1000, context);
            });
        }

        // Query data concurrently
        for (int i = 0; i < 50; i++) {
            final int queryId = i;
            executor.submit(() -> {
                dashboard.getCurrentSnapshot();
                dashboard.generateHtmlReport();
            });
        }

        executor.shutdown();
        try {
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify dashboard is still functional
        SLODashboard.DashboardSnapshot snapshot = dashboard.getCurrentSnapshot();
        assertNotNull(snapshot);
    }

    @Test
    void testPerformanceUnderLoad() {
        // Start dashboard
        dashboard.start();

        // Generate large amount of data
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "load_test");

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            sloTracker.recordCaseCompletion("case-load-" + i, i * 1000, context);
        }
        long endTime = System.currentTimeMillis();

        System.out.println("Generated 1000 events in " + (endTime - startTime) + "ms");

        // Test report generation performance
        startTime = System.currentTimeMillis();
        String jsonReport = dashboard.generateJsonReport(
            Instant.now().minusHours(1),
            Instant.now()
        );
        endTime = System.currentTimeMillis();

        System.out.println("JSON report generated in " + (endTime - startTime) + "ms");
        assertNotNull(jsonReport);

        startTime = System.currentTimeMillis();
        String htmlReport = dashboard.generateHtmlReport();
        endTime = System.currentTimeMillis();

        System.out.println("HTML report generated in " + (endTime - startTime) + "ms");
        assertNotNull(htmlReport);
    }

    @Test
    void testDashboardMetrics() {
        // Generate some data to populate metrics
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "metrics_test");
        sloTracker.recordCaseCompletion("case-metrics-1", 1000, context);

        // Verify dashboard metrics are present
        assertNotNull(meterRegistry.find("yawl.dashboard.refresh"));
        assertNotNull(meterRegistry.find("yawl.dashboard.data_refreshes"));
        assertNotNull(meterRegistry.find("yawl.dashboard.requests"));
    }

    @Test
    void testReportContentValidity() {
        // Generate data with different scenarios
        Map<String, String> context = new HashMap<>();

        // Compliant case
        context.put("case_type", "compliant");
        sloTracker.recordCaseCompletion("case-good-1", 1000, context);

        // Violating case
        context.put("case_type", "violating");
        sloTracker.recordCaseCompletion("case-bad-1", 25 * 60 * 60 * 1000L, context); // 25 hours

        // Generate and validate JSON report
        String json = dashboard.generateJsonReport(
            Instant.now().minusHours(1),
            Instant.now()
        );

        assertTrue(json.contains("\"current\""));
        assertTrue(json.contains("\"historical\""));
        assertTrue(json.contains("\"series\""));
        assertTrue(json.contains("\"summary\""));

        // Generate and validate HTML report
        String html = dashboard.generateHtmlReport();
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("<title>YAWL SLO Dashboard</title>"));
        assertTrue(html.contains("compliant"));
        assertTrue(html.contains("at-risk"));
        assertTrue(html.contains("violation"));
    }
}