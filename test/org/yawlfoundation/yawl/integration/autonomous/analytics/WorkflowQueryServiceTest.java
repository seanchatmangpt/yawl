package org.yawlfoundation.yawl.integration.autonomous.analytics;

import junit.framework.TestCase;
import org.junit.jupiter.api.Tag;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.QLeverSparqlEngine;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineException;

import java.util.List;
import java.util.Optional;

/**
 * Chicago TDD tests for {@link WorkflowQueryService} and {@link WorkflowAnalytics}.
 *
 * <p>Always-run tests verify SPARQL string structure and parser behaviour.
 * Self-skipping tests require a live QLever instance on port 7001.</p>
 *
 * @since YAWL 6.0
 */
@Tag("unit")
public class WorkflowQueryServiceTest extends TestCase {

    // -------------------------------------------------------------------------
    // Always-run: SPARQL query string verification (no QLever required)
    // -------------------------------------------------------------------------

    public void testBypassQueryContainsFilterNotExists() {
        QLeverSparqlEngine engine = new QLeverSparqlEngine("http://localhost:19877");
        WorkflowQueryService qs = new WorkflowQueryService(engine);

        String query = qs.bypassedTasksQuery("orderProcess", "validateOrder");

        assertTrue("Bypass query must use FILTER NOT EXISTS pattern",
                query.contains("FILTER NOT EXISTS"));
        assertTrue("Bypass query must filter by status=completed",
                query.contains("completed"));
        assertTrue("Bypass query must filter by specId",
                query.contains("\"orderProcess\""));
        assertTrue("Bypass query must filter by required taskId",
                query.contains("\"validateOrder\""));
        assertTrue("Bypass query must reference CaseExecution class",
                query.contains(WorkflowEventVocabulary.CASE_EXECUTION));
    }

    public void testAverageDurationQueryContainsAvgAggregate() {
        QLeverSparqlEngine engine = new QLeverSparqlEngine("http://localhost:19877");
        WorkflowQueryService qs = new WorkflowQueryService(engine);

        String query = qs.averageTaskDurationQuery("orderProcess", "reviewOrder");

        assertTrue("Average duration query must use AVG aggregate",
                query.contains("AVG"));
        assertTrue("Average duration query must use GROUP BY",
                query.contains("GROUP BY"));
        assertTrue("Average duration query must reference durationMs property",
                query.contains(WorkflowEventVocabulary.TASK_DURATION_MS));
        assertTrue("Average duration query must filter by taskId",
                query.contains("\"reviewOrder\""));
    }

    public void testBottleneckQueryOrdersByDescAvgDuration() {
        QLeverSparqlEngine engine = new QLeverSparqlEngine("http://localhost:19877");
        WorkflowQueryService qs = new WorkflowQueryService(engine);

        String query = qs.taskBottlenecksQuery("orderProcess", 5);

        assertTrue("Bottleneck query must ORDER BY DESC",
                query.contains("ORDER BY DESC"));
        assertTrue("Bottleneck query must LIMIT to topN results",
                query.contains("LIMIT 5"));
        assertTrue("Bottleneck query must use AVG aggregate",
                query.contains("AVG"));
    }

    public void testSlaViolationsQueryContainsDurationFilter() {
        QLeverSparqlEngine engine = new QLeverSparqlEngine("http://localhost:19877");
        WorkflowQueryService qs = new WorkflowQueryService(engine);

        String query = qs.slaViolationsQuery("orderProcess", 60000L);

        assertTrue("SLA violations query must contain FILTER on durationMs",
                query.contains("FILTER"));
        assertTrue("SLA violations query must include the SLA threshold value",
                query.contains("60000"));
        assertTrue("SLA violations query must filter for completed cases",
                query.contains("completed"));
    }

    public void testServiceReturnsEmptyWhenEngineUnavailable() throws SparqlEngineException {
        QLeverSparqlEngine engine = new QLeverSparqlEngine("http://localhost:19877");
        WorkflowQueryService qs = new WorkflowQueryService(engine);

        Optional<String> result = qs.activeCases("orderProcess");

        assertFalse("Service must return empty Optional when QLever is unavailable",
                result.isPresent());
    }

    public void testQueryNamesReturnsAllTen() {
        List<String> names = WorkflowQueryService.queryNames();
        assertEquals("There must be exactly 10 pre-built queries", 10, names.size());
    }

    public void testCasePathTraceQueryContainsOrderByStartTime() {
        QLeverSparqlEngine engine = new QLeverSparqlEngine("http://localhost:19877");
        WorkflowQueryService qs = new WorkflowQueryService(engine);

        String query = qs.casePathTraceQuery("orderProcess", "42");

        assertTrue("Path trace query must ORDER BY startTime for correct sequence",
                query.contains("ORDER BY"));
        assertTrue("Path trace query must reference taskStartTime property",
                query.contains(WorkflowEventVocabulary.TASK_START_TIME));
        assertTrue("Path trace query must filter by caseId",
                query.contains("\"42\""));
    }

    public void testParseTaskSummariesReturnsEmptyListForBlankInput() {
        List<WorkflowAnalytics.TaskSummary> result =
                WorkflowAnalytics.parseTaskSummaries(null);
        assertNotNull("parseTaskSummaries must never return null", result);
        assertTrue("parseTaskSummaries must return empty list for null input", result.isEmpty());

        result = WorkflowAnalytics.parseTaskSummaries("");
        assertTrue("parseTaskSummaries must return empty list for blank input", result.isEmpty());
    }

    public void testParseCaseSummariesReturnsEmptyListForBlankInput() {
        List<WorkflowAnalytics.CaseSummary> result =
                WorkflowAnalytics.parseCaseSummaries("");
        assertNotNull("parseCaseSummaries must never return null", result);
        assertTrue("parseCaseSummaries must return empty list for blank input", result.isEmpty());
    }

    public void testThroughputByHourQueryGroupsByHour() {
        QLeverSparqlEngine engine = new QLeverSparqlEngine("http://localhost:19877");
        WorkflowQueryService qs = new WorkflowQueryService(engine);

        String query = qs.throughputByHourQuery("orderProcess");

        assertTrue("Throughput query must GROUP BY hour",
                query.contains("GROUP BY"));
        assertTrue("Throughput query must COUNT completions",
                query.contains("COUNT"));
        assertTrue("Throughput query must use SUBSTR for hour extraction",
                query.contains("SUBSTR"));
    }

    // -------------------------------------------------------------------------
    // Self-skipping: live QLever roundtrip (port 7001)
    // -------------------------------------------------------------------------

    public void testActiveCasesQueryWhenQLeverRunning() throws Exception {
        QLeverSparqlEngine engine = new QLeverSparqlEngine();
        if (!engine.isAvailable()) return; // skip gracefully when QLever not running

        WorkflowQueryService qs = new WorkflowQueryService(engine);
        Optional<String> result = qs.activeCases("test-spec");

        assertTrue("Active cases query must return a result when QLever is running",
                result.isPresent());
        // Result may be empty string if no data yet — that's fine
        assertNotNull("Result string must not be null", result.get());
    }
}
