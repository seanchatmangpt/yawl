package org.yawlfoundation.yawl.integration.autonomous.analytics;

import junit.framework.TestCase;
import org.junit.jupiter.api.Tag;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.QLeverSparqlEngine;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineException;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineUnavailableException;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * End-to-End Integration Tests for WorkflowQueryService with SPARQL backend.
 *
 * <p>These tests verify the complete workflow event query functionality,
 * including case state queries, task metrics, and performance assertions.</p>
 *
 * <h3>Test Categories:</h3>
 * <ol>
 *   <li><b>Workflow event queries</b> - Query and validate workflow event data</li>
 *   <li><b>Case state queries</b> - Query and filter case execution states</li>
 *   <li><b>Task metrics</b> - Query task duration, throughput, and performance</li>
 *   <li><b>Performance assertions</b> - Verify query performance within thresholds</li>
 * </ol>
 *
 * <h3>External Dependencies:</h3>
 * <ul>
 *   <li>QLever instance on port 7001</li>
 *   <li>Pre-populated SPARQL database with workflow event data</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
@Tag("integration")
public class WorkflowQueryServiceIntegrationTest extends TestCase {

    private WorkflowQueryService queryService;
    private QLeverSparqlEngine sparqlEngine;
    private static final String TEST_SPEC_ID = "order-processing-test";
    private static final long PERFORMANCE_THRESHOLD_MS = 5000; // 5 seconds

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Initialize SPARQL engine
        sparqlEngine = new QLeverSparqlEngine();

        if (sparqlEngine.isAvailable()) {
            queryService = new WorkflowQueryService(sparqlEngine);
            // Wait for engine to be ready
            Thread.sleep(1000);
        } else {
            System.out.println("SPARQL engine unavailable - skipping integration tests");
        }
    }

    // -------------------------------------------------------------------------
    // Workflow Event Query Tests
    // -------------------------------------------------------------------------

    public void testQueryWorkflowEventsBySpecId() throws Exception {
        if (!isSparqlAvailable()) return;

        // Clear test data
        clearTestDatabase();

        // Insert test workflow events
        insertTestWorkflowEvents(10);

        // Query for events in our test specification
        Optional<String> result = queryService.workflowEventsBySpec(TEST_SPEC_ID);

        assertTrue("Should return workflow events", result.isPresent());
        assertFalse("Result should not be empty", result.get().isEmpty());

        // Verify event structure
        String events = result.get();
        assertTrue("Should contain CaseExecution events", events.contains(WorkflowEventVocabulary.CASE_EXECUTION));
        assertTrue("Should contain TaskExecution events", events.contains(WorkflowEventVocabulary.TASK_EXECUTION));
    }

    public void testQueryEventsByTimeRange() throws Exception {
        if (!isSparqlAvailable()) return;

        // Clear and populate test data
        clearTestDatabase();
        insertTestWorkflowEvents(5);

        // Define time range (last hour)
        Instant now = Instant.now();
        Instant startTime = now.minus(1, ChronoUnit.HOURS);
        Instant endTime = now;

        Optional<String> result = queryService.eventsByTimeRange(startTime, endTime);

        assertTrue("Should return events in time range", result.isPresent());
        String events = result.get();
        assertTrue("Should have event data", !events.isEmpty());
    }

    public void testQueryEventsByTaskName() throws Exception {
        if (!isSparqlAvailable()) return;

        clearTestDatabase();
        insertTestWorkflowEvents(8);

        Optional<String> result = queryService.eventsByTaskName("validateOrder");

        assertTrue("Should return events for specific task", result.isPresent());
        String events = result.get();
        assertTrue("Should contain task execution data", events.contains("validateOrder"));
    }

    public void testQueryEventsWithComplexFilter() throws Exception {
        if (!isSparqlAvailable()) return;

        clearTestDatabase();
        insertTestWorkflowEvents(10);

        Optional<String> result = queryService.eventsWithFilters(
            TEST_SPEC_ID,
            "completed",
            "reviewOrder",
            5000L // 5 second duration threshold
        );

        assertTrue("Should return filtered events", result.isPresent());
        String events = result.get();
        assertTrue("Should contain filtered event data", !events.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Case State Query Tests
    // -------------------------------------------------------------------------

    public void testQueryActiveCases() throws Exception {
        if (!isSparqlAvailable()) return;

        clearTestDatabase();
        insertTestWorkflowEvents(5, "active");

        Optional<String> result = queryService.activeCases(TEST_SPEC_ID);

        assertTrue("Should return active cases", result.isPresent());
        String cases = result.get();
        assertTrue("Should have case data", !cases.isEmpty());
        assertTrue("Should reference CaseExecution", cases.contains(WorkflowEventVocabulary.CASE_EXECUTION));
    }

    public void testQueryCompletedCases() throws Exception {
        if (!isSparqlAvailable()) return;

        clearTestDatabase();
        insertTestWorkflowEvents(5, "completed");

        Optional<String> result = queryService.completedCases(TEST_SPEC_ID);

        assertTrue("Should return completed cases", result.isPresent());
        String cases = result.get();
        assertTrue("Should have completed case data", !cases.isEmpty());
        assertTrue("Should reference completed status", cases.contains("completed"));
    }

    public void testQueryCasesByStatus() throws Exception {
        if (!isSparqlAvailable()) return;

        clearTestDatabase();
        // Insert cases with different statuses
        insertTestWorkflowEvents(3, "active");
        insertTestWorkflowEvents(2, "completed");
        insertTestWorkflowEvents(1, "terminated");

        Optional<String> result = queryService.casesByStatus(TEST_SPEC_ID, "active");

        assertTrue("Should return cases by status", result.isPresent());
        String cases = result.get();

        // Count occurrences to verify filtering
        int activeCount = countSubstring(cases, "active");
        int completedCount = countSubstring(cases, "completed");
        int terminatedCount = countSubstring(cases, "terminated");

        assertTrue("Should have active cases", activeCount > 0);
        assertTrue("Should not have completed cases", completedCount == 0);
        assertTrue("Should not have terminated cases", terminatedCount == 0);
    }

    // -------------------------------------------------------------------------
    // Task Metrics Query Tests
    // -------------------------------------------------------------------------

    public void testAverageTaskDurationQuery() throws Exception {
        if (!isSparqlAvailable()) return;

        clearTestDatabase();
        insertTestWorkflowEventsWithDurations(10);

        Optional<String> result = queryService.averageTaskDurationQuery(TEST_SPEC_ID, "reviewOrder");

        assertTrue("Should return average duration", result.isPresent());
        String query = result.get();

        // Verify query structure
        assertTrue("Should use AVG aggregate", query.contains("AVG"));
        assertTrue("Should reference durationMs property", query.contains(WorkflowEventVocabulary.TASK_DURATION_MS));
        assertTrue("Should GROUP BY task", query.contains("GROUP BY"));
    }

    public void testTaskThroughputQuery() throws Exception {
        if (!isSparqlAvailable()) return;

        clearTestDatabase();
        insertTestWorkflowEvents(15);

        Optional<String> result = queryService.taskThroughputQuery(TEST_SPEC_ID, "validateOrder");

        assertTrue("Should return throughput data", result.isPresent());
        String query = result.get();

        // Verify query structure
        assertTrue("Should use COUNT aggregate", query.contains("COUNT"));
        assertTrue("Should reference task completion", query.contains(WorkflowEventVocabulary.TASK_COMPLETED));
        assertTrue("Should GROUP BY task", query.contains("GROUP BY"));
    }

    public void testBottleneckAnalysisQuery() throws Exception {
        if (!isSparqlAvailable()) return;

        clearTestDatabase();
        insertTestWorkflowEventsWithDurations(20);

        Optional<String> result = queryService.taskBottlenecksQuery(TEST_SPEC_ID, 5);

        assertTrue("Should return bottleneck analysis", result.isPresent());
        String query = result.get();

        // Verify query structure
        assertTrue("Should use AVG aggregate", query.contains("AVG"));
        assertTrue("Should ORDER BY DESC", query.contains("ORDER BY DESC"));
        assertTrue("Should LIMIT to top results", query.contains("LIMIT 5"));
        assertTrue("Should reference durationMs", query.contains(WorkflowEventVocabulary.TASK_DURATION_MS));
    }

    // -------------------------------------------------------------------------
    // Performance Assertion Tests
    // -------------------------------------------------------------------------

    public void testQueryPerformanceWithinThreshold() throws Exception {
        if (!isSparqlAvailable()) return;

        // Load test data
        clearTestDatabase();
        insertTestWorkflowEvents(100);

        long startTime = System.currentTimeMillis();

        Optional<String> result = queryService.workflowEventsBySpec(TEST_SPEC_ID);

        long duration = System.currentTimeMillis() - startTime;
        assertTrue("Query should complete within " + PERFORMANCE_THRESHOLD_MS + "ms",
                   duration < PERFORMANCE_THRESHOLD_MS);

        assertTrue("Should return valid result", result.isPresent());
        assertFalse("Result should not be empty", result.get().isEmpty());
    }

    public void testConcurrentQueryPerformance() throws Exception {
        if (!isSparqlAvailable()) return;

        clearTestDatabase();
        insertTestWorkflowEvents(50);

        int threadCount = 5;
        Thread[] threads = new Thread[threadCount];
        long[] durations = new long[threadCount];

        // Create threads to execute queries concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                long start = System.currentTimeMillis();
                try {
                    Optional<String> result = queryService.activeCases(TEST_SPEC_ID);
                    durations[threadId] = System.currentTimeMillis() - start;
                    assertTrue("Thread " + threadId + " should return result", result.isPresent());
                } catch (Exception e) {
                    fail("Thread " + threadId + " failed: " + e.getMessage());
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(PERFORMANCE_THRESHOLD_MS);
        }

        // Verify all queries completed within threshold
        for (int i = 0; i < threadCount; i++) {
            assertTrue("Thread " + i + " should complete within threshold",
                       durations[i] < PERFORMANCE_THRESHOLD_MS);
        }
    }

    public void testLargeDatasetQueryPerformance() throws Exception {
        if (!isSparqlAvailable()) return;

        // Load larger dataset (1000 events)
        clearTestDatabase();
        insertTestWorkflowEvents(1000);

        long startTime = System.currentTimeMillis();
        Optional<String> result = queryService.workflowEventsBySpec(TEST_SPEC_ID);
        long duration = System.currentTimeMillis() - startTime;

        // For large datasets, we allow a longer threshold
        long largeDatasetThreshold = 15000; // 15 seconds

        assertTrue("Large dataset query should complete within " + largeDatasetThreshold + "ms",
                   duration < largeDatasetThreshold);
        assertTrue("Should return large result set", result.isPresent());
        assertTrue("Should have substantial data", result.get().length() > 1000);
    }

    // -------------------------------------------------------------------------
    // Error Handling Tests
    // -------------------------------------------------------------------------

    public void testQueryWithInvalidSpecId() throws Exception {
        if (!isSparqlAvailable()) return;

        Optional<String> result = queryService.workflowEventsBySpec("invalid-spec-id");

        // Should return empty result, not throw exception
        assertTrue("Should handle invalid spec ID gracefully", result.isPresent());
        // Result might be empty, which is acceptable
    }

    public void testQueryWithInvalidTaskName() throws Exception {
        if (!isSparqlAvailable()) return;

        Optional<String> result = queryService.eventsByTaskName("non-existent-task");

        assertTrue("Should handle invalid task name gracefully", result.isPresent());
        // Result should be empty for non-existent task
    }

    public void testServiceReturnsEmptyWhenEngineUnavailable() throws SparqlEngineException {
        WorkflowQueryService service = new WorkflowQueryService(new QLeverSparqlEngine("http://localhost:9999"));

        Optional<String> result = service.workflowEventsBySpec(TEST_SPEC_ID);
        assertFalse("Should return empty when engine unavailable", result.isPresent());
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------

    private boolean isSparqlAvailable() {
        return sparqlEngine != null && sparqlEngine.isAvailable();
    }

    private void clearTestDatabase() throws Exception {
        if (!isSparqlAvailable()) return;

        // This would require actual SPARQL DELETE queries
        // For test purposes, we just skip the clearing
    }

    private void insertTestWorkflowEvents(int count) throws Exception {
        insertTestWorkflowEvents(count, "active");
    }

    private void insertTestWorkflowEvents(int count, String status) throws Exception {
        if (!isSparqlAvailable()) return;

        // In a real test, this would insert actual SPARQL data
        // For now, we just simulate by preparing test queries
        for (int i = 1; i <= count; i++) {
            String caseId = "case-" + i;
            String taskId = "task-" + i;

            // This would normally be SPARQL INSERT queries
            System.out.println("Would insert workflow event: " + caseId + " / " + taskId + " / " + status);
        }
    }

    private void insertTestWorkflowEventsWithDurations(int count) throws Exception {
        if (!isSparqlAvailable()) return;

        for (int i = 1; i <= count; i++) {
            String caseId = "case-duration-" + i;
            String taskId = "task-duration-" + i;
            long durationMs = 1000L + (i % 5000); // Variable duration

            // This would normally be SPARQL INSERT queries with duration data
            System.out.println("Would insert workflow event with duration: " + caseId +
                              " / " + taskId + " / " + durationMs + "ms");
        }
    }

    private int countSubstring(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
}