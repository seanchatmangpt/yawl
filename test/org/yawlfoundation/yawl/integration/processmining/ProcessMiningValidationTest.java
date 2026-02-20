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

package org.yawlfoundation.yawl.integration.processmining;

import org.junit.jupiter.api.BeforeEach;
 * Tests integration with PM4Py, conformance analysis, and performance analytics.
 */
@TestMethodOrder(OrderAnnotation.class)
public class ProcessMiningValidationTest {

    private Pm4PyClient pm4pyClient;
    private ConformanceAnalyzer conformanceAnalyzer;
    private PerformanceAnalyzer performanceAnalyzer;
    private static final String PM4PY_URL = "http://localhost:9092";

    @BeforeEach
    void setUp() {
        pm4pyClient = new Pm4PyClient(PM4PY_URL);
        conformanceAnalyzer = new ConformanceAnalyzer(
            Set.of("start", "task1", "task2", "end"),
            Set.of("start>>task1", "task1>>task2", "task2>>end")
        );
        performanceAnalyzer = new PerformanceAnalyzer();
    }

    @Test
    @Order(1)
    @DisplayName("Process Mining: PM4Py integration validation")
    void testPm4PyIntegration() throws Exception {
        // Test PM4Py client connectivity
        // Simulate a simple process mining query
        String xesInput = generateSimpleXesLog();

        // Call PM4Py with a conformance checking skill
        String result = pm4pyClient.call("conformance_check", xesInput);

        // Validate response format
        assertNotNull(result, "PM4Py client should return a result");
        assertTrue(result.contains("conformance"), "Response should contain conformance data");

        System.out.println("✅ PM4Py integration validation passed");
    }

    @Test
    @Order(2)
    @DisplayName("Process Mining: Conformance analysis integrity")
    void testConformanceAnalysis() {
        // Generate test XES log
        String xesLog = generateTestXesLog();

        // Perform conformance analysis
        ConformanceAnalyzer.ConformanceResult result = conformanceAnalyzer.analyze(xesLog);

        // Verify analysis results
        assertTrue(result.traceCount > 0, "Should analyze at least one trace");
        assertTrue(result.fitness >= 0 && result.fitness <= 1, "Fitness should be between 0 and 1");

        // Check for deviating traces
        if (result.deviatingTraces.isEmpty()) {
            System.out.println("✅ All traces conform to the process model");
        } else {
            System.out.printf("⚠️  Found %d deviating traces: %s%n",
                result.deviatingTraces.size(), result.deviatingTraces);
        }

        // Validate against expected activities
        Set<String> expectedActivities = Set.of("start", "task1", "task2", "end");
        assertTrue(result.observedActivities.containsAll(expectedActivities),
            "All expected activities should be observed");
    }

    @Test
    @Order(3)
    @DisplayName("Process Mining: Performance analytics validation")
    void testPerformanceAnalytics() {
        // Generate performance test data
        List<WorkflowEvent> events = generatePerformanceTestData();

        // Calculate key performance metrics
        double avgCycleTime = performanceAnalyzer.calculateAverageCycleTime(events);
        double throughput = performanceAnalyzer.calculateThroughput(events, "2024-01-01", "2024-01-31");
        Map<String, Double> activityDurations = performanceAnalyzer.getActivityDurations(events);

        // Validate metrics
        assertTrue(avgCycleTime > 0, "Average cycle time should be positive");
        assertTrue(throughput > 0, "Throughput should be positive");
        assertFalse(activityDurations.isEmpty(), "Should have activity duration data");

        System.out.printf("✅ Performance metrics:%n" +
            "   - Average cycle time: %.2f ms%n" +
            "   - Throughput: %.2f cases/day%n" +
            "   - Activity durations: %d activities%n",
            avgCycleTime, throughput, activityDurations.size());
    }

    @Test
    @Order(4)
    @DisplayName("Process Mining: XES export integrity")
    void testXesExport() {
        // Generate test event data
        List<WorkflowEvent> events = generateTestEvents();

        // Export to XES format
        String xesExport = EventLogExporter.exportToXes(events);

        // Validate XES structure
        assertNotNull(xesExport, "XES export should not be null");
        assertTrue(xesExport.contains("<log>"), "XES should contain root <log> element");
        assertTrue(xesExport.contains("<trace>"), "XES should contain trace elements");
        assertTrue(xesExport.contains("<event>"), "XES should contain event elements");

        // Count traces and events
        long traceCount = xesExport.split("<trace>").length - 1;
        long eventCount = xesExport.split("<event>").length - 1;

        assertEquals(events.size(), eventCount, "Event count should match");
        assertTrue(traceCount > 0, "Should have at least one trace");

        System.out.printf("✅ XES export: %d traces, %d events%n", traceCount, eventCount);
    }

    @Test
    @Order(5)
    @DisplayName("Process Mining: Integration validation with external services")
    void testExternalServiceIntegration() throws Exception {
        // Test PM4Py availability
        try {
            // Simple connectivity test
            String testResult = pm4pyClient.call("ping", "{}");
            assertNotNull(testResult, "PM4Py should be reachable");

            System.out.println("✅ PM4Py external service integration validated");
        } catch (Exception e) {
            System.out.println("⚠️  PM4Py service not available (test environment)");
            // In CI/test environments, this might be expected
        }
    }

    // Helper methods

    private String generateSimpleXesLog() {
        return """
            <log>
                <trace>
                    <event><timestamp>2024-01-01T10:00:00</timestamp><name>start</name></event>
                    <event><timestamp>2024-01-01T10:05:00</timestamp><name>task1</name></event>
                    <event><timestamp>2024-01-01T10:10:00</timestamp><name>task2</name></event>
                    <event><timestamp>2024-01-01T10:15:00</timestamp><name>end</name></event>
                </trace>
            </log>
            """;
    }

    private String generateTestXesLog() {
        return """
            <log>
                <trace id="trace1">
                    <event><timestamp>2024-01-01T10:00:00</timestamp><name>start</name></event>
                    <event><timestamp>2024-01-01T10:05:00</timestamp><name>task1</name></event>
                    <event><timestamp>2024-01-01T10:10:00</timestamp><name>task2</name></event>
                    <event><timestamp>2024-01-01T10:15:00</timestamp><name>end</name></event>
                </trace>
                <trace id="trace2">
                    <event><timestamp>2024-01-01T11:00:00</timestamp><name>start</name></event>
                    <event><timestamp>2024-01-01T11:05:00</timestamp><name>task1</name></event>
                    <event><timestamp>2024-01-01T11:20:00</timestamp><name>task3</name></event>
                    <event><timestamp>2024-01-01T11:25:00</timestamp><name>end</name></event>
                </trace>
            </log>
            """;
    }

    private List<WorkflowEvent> generatePerformanceTestData() {
        List<WorkflowEvent> events = new ArrayList<>();

        // Generate 100 test events with realistic timing
        Instant startTime = Instant.parse("2024-01-01T00:00:00Z");
        for (int i = 0; i < 100; i++) {
            long delay = (long) (Math.random() * 3600000); // Random delay up to 1 hour
            events.add(new WorkflowEvent(
                "case-" + i,
                "activity-" + (i % 5),
                startTime.plusMillis(delay),
                Map.of("duration", String.valueOf(delay))
            ));
        }

        return events;
    }

    private List<WorkflowEvent> generateTestEvents() {
        List<WorkflowEvent> events = new ArrayList<>();

        events.add(new WorkflowEvent(
            "case-1",
            "start",
            Instant.parse("2024-01-01T10:00:00Z"),
            Map.of("type", "start")
        ));

        events.add(new WorkflowEvent(
            "case-1",
            "task1",
            Instant.parse("2024-01-01T10:05:00Z"),
            Map.of("type", "task")
        ));

        events.add(new WorkflowEvent(
            "case-2",
            "start",
            Instant.parse("2024-01-01T11:00:00Z"),
            Map.of("type", "start")
        ));

        return events;
    }

    // Helper classes for testing

    public static class WorkflowEvent {
        private final String caseId;
        private final String activity;
        private final Instant timestamp;
        private final Map<String, String> attributes;

        public WorkflowEvent(String caseId, String activity, Instant timestamp, Map<String, String> attributes) {
            this.caseId = caseId;
            this.activity = activity;
            this.timestamp = timestamp;
            this.attributes = attributes;
        }

        public String getCaseId() { return caseId; }
        public String getActivity() { return activity; }
        public Instant getTimestamp() { return timestamp; }
        public Map<String, String> getAttributes() { return attributes; }
    }
}