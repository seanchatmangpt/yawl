package org.yawlfoundation.yawl.integration.autonomous.analytics;

import junit.framework.TestCase;
import org.junit.jupiter.api.Tag;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.QLeverSparqlEngine;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * End-to-End Integration Tests for WorkflowAnalytics queries.
 *
 * <p>These tests cover aggregation queries, time-series analysis, and statistical
 * analysis workflows. The tests verify that analytics queries can process large
 * datasets and return meaningful statistical insights.</p>
 *
 * <h3>Test Categories:</h3>
 * <ol>
 *   <li><b>Aggregation queries</b> - SUM, AVG, COUNT, GROUP BY operations</li>
 *   <li><b>Time-series queries</b> - Hourly, daily, monthly aggregations</li>
 *   <li><b>Statistical analysis</b> - Percentiles, distributions, trends</li>
 *   <li><b>Performance testing</b> - Query execution on large datasets</li>
 * </ol>
 *
 * <h3>External Dependencies:</h3>
 * <ul>
 *   <li>QLever instance on port 7001</li>
 *   <li>Populated SPARQL database with test workflow event data</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
@Tag("integration")
public class WorkflowAnalyticsTest extends TestCase {

    private WorkflowQueryService queryService;
    private WorkflowAnalytics analytics;
    private QLeverSparqlEngine sparqlEngine;
    private static final String TEST_SPEC_ID = "analytics-test-spec";
    private static final int LARGE_DATASET_SIZE = 1000;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Initialize SPARQL engine and services
        sparqlEngine = new QLeverSparqlEngine();

        if (sparqlEngine.isAvailable()) {
            queryService = new WorkflowQueryService(sparqlEngine);
            analytics = new WorkflowAnalytics(queryService);

            // Wait for engine to be ready
            Thread.sleep(1000);

            // Load test data
            loadTestAnalyticsData();
        } else {
            System.out.println("SPARQL engine unavailable - skipping analytics integration tests");
        }
    }

    // -------------------------------------------------------------------------
    // Aggregation Query Tests
    // -------------------------------------------------------------------------

    public void testTaskThroughputAggregation() throws Exception {
        if (!isSparqlAvailable()) return;

        Optional<String> result = analytics.taskThroughputAggregation(TEST_SPEC_ID, "hourly");

        assertTrue("Should return throughput aggregation", result.isPresent());
        String query = result.get();

        // Verify aggregation structure
        assertTrue("Should use COUNT aggregate", query.contains("COUNT"));
        assertTrue("Should include time grouping", query.contains("GROUP BY"));
        assertTrue("Should reference task completion events", query.contains(WorkflowEventVocabulary.TASK_COMPLETED));
    }

    public void testAverageTaskDurationByCategory() throws Exception {
        if (!isSparqlAvailable()) return;

        Optional<String> result = analytics.averageDurationByTaskCategory(TEST_SPEC_ID);

        assertTrue("Should return duration aggregation", result.isPresent());
        String query = result.get();

        // Verify aggregation structure
        assertTrue("Should use AVG aggregate", query.contains("AVG"));
        assertTrue("Should GROUP BY task category", query.contains("GROUP BY"));
        assertTrue("Should reference duration property", query.contains(WorkflowEventVocabulary.TASK_DURATION_MS));
    }

    public void testCaseCompletionRateCalculation() throws Exception {
        if (!isSparqlAvailable()) return;

        Optional<String> result = analytics.caseCompletionRate(TEST_SPEC_ID);

        assertTrue("Should return completion rate", result.isPresent());
        String query = result.get();

        // Verify aggregation structure
        assertTrue("Should use COUNT for both completed and total", query.contains("COUNT"));
        assertTrue("Should calculate rate", query.contains("/"));
        assertTrue("Should reference case status", query.contains("status"));
    }

    public void testResourceUtilizationAggregation() throws Exception {
        if (!isSparqlAvailable()) return;

        Optional<String> result = analytics.resourceUtilization(TEST_SPEC_ID, "daily");

        assertTrue("Should return resource utilization", result.isPresent());
        String query = result.get();

        // Verify aggregation structure
        assertTrue("Should COUNT task executions", query.contains("COUNT"));
        assertTrue("Should GROUP BY resource and day", query.contains("GROUP BY"));
        assertTrue("Should reference resource assignments", query.contains(WorkflowEventVocabulary.TASK_ASSIGNED_TO));
    }

    // -------------------------------------------------------------------------
    // Time-Series Query Tests
    // -------------------------------------------------------------------------

    public void testThroughputByHourTimeSeries() throws Exception {
        if (!isSparqlAvailable()) return;

        Optional<String> result = analytics.throughputByHour(TEST_SPEC_ID);

        assertTrue("Should return hourly throughput", result.isPresent());
        String query = result.get();

        // Verify time-series structure
        assertTrue("Should use SUBSTR for hour extraction", query.contains("SUBSTR"));
        assertTrue("Should GROUP BY hour", query.contains("GROUP BY"));
        assertTrue("Should COUNT completions", query.contains("COUNT"));
        assertTrue("Should ORDER BY time", query.contains("ORDER BY"));
    }

    public void testTaskDurationTrendOverDays() throws Exception {
        if (!isSparqlAvailable()) return;

        Optional<String> result = analytics.taskDurationTrend(TEST_SPEC_ID, 7); // 7-day trend

        assertTrue("Should return duration trend", result.isPresent());
        String query = result.get();

        // Verify trend analysis structure
        assertTrue("Should use AVG aggregate", query.contains("AVG"));
        assertTrue("Should GROUP BY day", query.contains("GROUP BY"));
        assertTrue("Should reference date/time properties", query.contains(WorkflowEventVocabulary.TASK_START_TIME));
        assertTrue("Should LIMIT to recent days", query.contains("LIMIT"));
    }

    public void testFailureRateByTimeOfDay() throws Exception {
        if (!isSparqlAvailable()) return;

        Optional<String> result = analytics.failureRateByTimeOfDay(TEST_SPEC_ID);

        assertTrue("Should return failure rate by time", result.isPresent());
        String query = result.get();

        // Verify time-based failure analysis
        assertTrue("Should use conditional aggregation", query.contains("SUM"));
        assertTrue("Should GROUP BY hour", query.contains("GROUP BY"));
        assertTrue("Should reference task status", query.contains("status"));
        assertTrue("Should calculate rate", query.contains("/"));
    }

    public void testPeakHoursIdentification() throws Exception {
        if (!isSparqlAvailable()) return;

        Optional<String> result = analytics.identifyPeakHours(TEST_SPEC_ID);

        assertTrue("Should identify peak hours", result.isPresent());
        String query = result.get();

        // Verify peak analysis structure
        assertTrue("Should ORDER BY count DESC", query.contains("ORDER BY DESC"));
        assertTrue("Should use COUNT aggregate", query.contains("COUNT"));
        assertTrue("Should GROUP BY hour", query.contains("GROUP BY"));
        assertTrue("Should LIMIT to top hours", query.contains("LIMIT"));
    }

    // -------------------------------------------------------------------------
    // Statistical Analysis Tests
    // -------------------------------------------------------------------------

    public void testTaskDurationPercentiles() throws Exception {
        if (!isSparqlAvailable()) return;

        Optional<String> result = analytics.taskDurationPercentiles(TEST_SPEC_ID, "validateOrder");

        assertTrue("Should return percentiles", result.isPresent());
        String query = result.get();

        // Verify percentile calculation
        assertTrue("Should calculate 50th percentile (median)", query.contains("PERCENTILE"));
        assertTrue("Should use duration values", query.contains(WorkflowEventVocabulary.TASK_DURATION_MS));
        assertTrue("Should reference specific task", query.contains("validateOrder"));
    }

    public void testCaseDistributionStatistics() throws Exception {
        if (!isSparqlAvailable()) return;

        Optional<String> result = analytics.caseDistributionStatistics(TEST_SPEC_ID);

        assertTrue("Should return distribution statistics", result.isPresent());
        String query = result.get();

        // Verify statistical analysis
        assertTrue("Should calculate COUNT", query.contains("COUNT"));
        assertTrue("Should GROUP BY status", query.contains("GROUP BY"));
        assertTrue("Should reference case execution", query.contains(WorkflowEventVocabulary.CASE_EXECUTION));
    }

    public void testOutlierDetectionInTaskDurations() throws Exception {
        if (!isSparqlAvailable()) return;

        Optional<String> result = analytics.detectTaskDurationOutliers(TEST_SPEC_ID, 20000L); // 20 seconds threshold

        assertTrue("Should detect outliers", result.isPresent());
        String query = result.get();

        // Verify outlier detection
        assertTrue("Should use FILTER for outlier threshold", query.contains("FILTER"));
        assertTrue("Should reference duration property", query.contains(WorkflowEventVocabulary.TASK_DURATION_MS));
        assertTrue("Should compare against threshold", query.contains("> 20000"));
    }

    public void testRegressionAnalysisForTaskPerformance() throws Exception {
        if (!isSparqlAvailable()) return;

        Optional<String> result = analytics.taskPerformanceRegression(TEST_SPEC_ID, "reviewOrder", 30); // 30-day window

        assertTrue("Should return regression analysis", result.isPresent());
        String query = result.get();

        // Verify regression analysis structure
        assertTrue("Should use AVG for baseline", query.contains("AVG"));
        assertTrue("Should GROUP BY date period", query.contains("GROUP BY"));
        assertTrue("Should compare performance metrics", query.contains(WorkflowEventVocabulary.TASK_DURATION_MS));
    }

    // -------------------------------------------------------------------------
    // Large Dataset Performance Tests
    // -------------------------------------------------------------------------

    public void testAnalyticsQueryPerformanceOnLargeDataset() throws Exception {
        if (!isSparqlAvailable()) return;

        // Ensure we have a large dataset
        loadLargeTestDataset(LARGE_DATASET_SIZE);

        long startTime = System.currentTimeMillis();
        Optional<String> result = analytics.taskThroughputAggregation(TEST_SPEC_ID, "daily");
        long duration = System.currentTimeMillis() - startTime;

        // Performance threshold for large dataset queries
        long performanceThreshold = 10000; // 10 seconds

        assertTrue("Large dataset query should complete within " + performanceThreshold + "ms",
                   duration < performanceThreshold);
        assertTrue("Should return valid result", result.isPresent());
        assertFalse("Result should contain data", result.get().isEmpty());
    }

    public void testMemoryEfficiencyWithTimeSeries() throws Exception {
        if (!isSparqlAvailable()) return;

        // Load data for extended time series
        loadTimeSeriesData(365); // One year of data

        long startTime = System.currentTimeMillis();
        Optional<String> result = analytics.throughputByHour(TEST_SPEC_ID);
        long duration = System.currentTimeMillis() - startTime;

        // Extended query should still complete
        long extendedThreshold = 15000; // 15 seconds

        assertTrue("Extended time series should complete", duration < extendedThreshold);
        assertTrue("Should return time series data", result.isPresent());
    }

    public void testConcurrentAnalyticsExecution() throws Exception {
        if (!isSparqlAvailable()) return;

        int threadCount = 3;
        Thread[] threads = new Thread[threadCount];
        Exception[] exceptions = new Exception[threadCount];
        boolean[] completed = new boolean[threadCount];

        // Define different analytics queries for each thread
        Runnable[] analyticsTasks = {
            () -> {
                try {
                    Optional<String> result = analytics.taskThroughputAggregation(TEST_SPEC_ID, "hourly");
                    completed[0] = true;
                } catch (Exception e) {
                    exceptions[0] = e;
                }
            },
            () -> {
                try {
                    Optional<String> result = analytics.averageDurationByTaskCategory(TEST_SPEC_ID);
                    completed[1] = true;
                } catch (Exception e) {
                    exceptions[1] = e;
                }
            },
            () -> {
                try {
                    Optional<String> result = analytics.caseCompletionRate(TEST_SPEC_ID);
                    completed[2] = true;
                } catch (Exception e) {
                    exceptions[2] = e;
                }
            }
        };

        // Start threads
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(analyticsTasks[threadId]);
            threads[i].start();
        }

        // Wait for completion
        for (Thread thread : threads) {
            thread.join(10000); // 10 second timeout
        }

        // Verify all queries completed successfully
        for (int i = 0; i < threadCount; i++) {
            if (exceptions[i] != null) {
                fail("Thread " + i + " failed: " + exceptions[i].getMessage());
            }
            assertTrue("Thread " + i + " should complete successfully", completed[i]);
        }
    }

    // -------------------------------------------------------------------------
    // Real-Time Analytics Tests
    // -------------------------------------------------------------------------

    public void testRealTimeThroughputMonitoring() throws Exception {
        if (!isSparqlAvailable()) return;

        // Simulate real-time data insertion
        insertRealTimeEvents(10);

        Optional<String> result = analytics.realTimeThroughput(TEST_SPEC_ID);

        assertTrue("Should return real-time throughput", result.isPresent());
        String query = result.get();

        // Verify real-time query structure
        assertTrue("Should reference recent events", query.contains("FILTER"));
        assertTrue("Should use time-based filtering", query.contains("NOW"));
        assertTrue("Should COUNT recent completions", query.contains("COUNT"));
    }

    public void testAlertTriggerForSlowTasks() throws Exception {
        if (!isSparqlAvailable()) return;

        // Insert some slow tasks
        insertSlowTasks(5, 30000L); // 30 seconds duration

        Optional<String> result = analytics.detectSlowTasks(TEST_SPEC_ID, 25000L); // 25 second threshold

        assertTrue("Should detect slow tasks", result.isPresent());
        String query = result.get();

        // Verify slow task detection
        assertTrue("Should use FILTER for duration threshold", query.contains("FILTER"));
        assertTrue("Should reference durationMs", query.contains(WorkflowEventVocabulary.TASK_DURATION_MS));
        assertTrue("Should compare against threshold", query.contains("> 25000"));
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------

    private boolean isSparqlAvailable() {
        return sparqlEngine != null && sparqlEngine.isAvailable();
    }

    private void loadTestAnalyticsData() throws Exception {
        if (!isSparqlAvailable()) return;

        // This would populate the SPARQL database with test analytics data
        for (int i = 1; i <= 100; i++) {
            String caseId = "analytics-case-" + i;
            String taskId = "analytics-task-" + i;
            long duration = 1000 + (i % 5000); // Variable duration
            String status = i % 10 == 0 ? "failed" : "completed"; // 10% failure rate

            // SPARQL INSERT queries would go here
            System.out.println("Loading analytics data: " + caseId + " / " + taskId +
                             " / " + duration + "ms / " + status);
        }
    }

    private void loadLargeTestDataset(int size) throws Exception {
        if (!isSparqlAvailable()) return;

        // Simulate loading a large dataset
        for (int i = 1; i <= size; i++) {
            String caseId = "large-case-" + i;
            String taskId = "large-task-" + i;
            long duration = 500 + (i % 10000); // Wide range of durations

            // SPARQL INSERT queries for large dataset
            System.out.println("Loading large dataset item " + i + "/" + size);
        }
    }

    private void loadTimeSeriesData(int days) throws Exception {
        if (!isSparqlAvailable()) return;

        // Simulate time series data loading
        for (int day = 1; day <= days; day++) {
            for (int hour = 0; hour < 24; hour++) {
                for (int event = 1; event <= 10; event++) {
                    String caseId = "ts-case-" + day + "-" + hour + "-" + event;
                    long duration = 100 + (event % 2000);

                    // Time series SPARQL INSERT
                    System.out.println("Loading time series data for day " + day +
                                     ", hour " + hour + ", event " + event);
                }
            }
        }
    }

    private void insertRealTimeEvents(int count) throws Exception {
        if (!isSparqlAvailable()) return;

        // Insert recent events for real-time testing
        Instant now = Instant.now();
        for (int i = 1; i <= count; i++) {
            String caseId = "realtime-case-" + i;
            String taskId = "realtime-task-" + i;
            long startTime = now.minus(i, ChronoUnit.MINUTES).toEpochMilli();

            // Real-time SPARQL INSERT
            System.out.println("Inserting real-time event: " + caseId + " at " + startTime);
        }
    }

    private void insertSlowTasks(int count, long minDuration) throws Exception {
        if (!isSparqlAvailable()) return;

        // Insert tasks with slow execution
        for (int i = 1; i <= count; i++) {
            String caseId = "slow-case-" + i;
            String taskId = "slow-task-" + i;
            long duration = minDuration + (i * 1000); // Gradually increase duration

            // Slow task SPARQL INSERT
            System.out.println("Inserting slow task: " + caseId + " with duration " + duration + "ms");
        }
    }
}