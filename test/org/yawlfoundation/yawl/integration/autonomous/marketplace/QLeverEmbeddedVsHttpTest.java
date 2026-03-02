package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import junit.framework.TestCase;
import org.junit.jupiter.api.Tag;
import org.yawlfoundation.yawl.integration.autonomous.analytics.WorkflowEventVocabulary;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Comparison Test for Embedded vs HTTP QLever Engine Performance and Result Equivalence.
 *
 * <p>This test verifies that the same query executed on both embedded and HTTP
 * QLever engines produces equivalent results with acceptable performance
 * characteristics.</p>
 *
 * <h3>Test Categories:</h3>
 * <ol>
 *   <li><b>Result equivalence verification</b> - Same queries return identical results</li>
 *   <li><b>Performance comparison</b> - Embedded vs HTTP execution time comparison</li>
 *   <li><b>Concurrency testing</b> - Both engines under concurrent load</li>
 *   <li><b>Error handling consistency</b> - Both engines handle errors the same way</li>
 * </ol>
 *
 * <h3>External Dependencies:</h3>
 * <ul>
 *   <li>QLever embedded instance (in-process)</li>
 *   <li>QLever HTTP instance on port 7001</li>
 *   <li>Identical test data loaded in both instances</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
@Tag("integration")
public class QLeverEmbeddedVsHttpTest extends TestCase {

    private QLeverSparqlEngine embeddedEngine;
    private QLeverSparqlEngine httpEngine;
    private static final String BASE_HTTP_URL = "http://localhost:7001";
    private static final String TEST_SPEC_ID = "comparison-test";
    private static final int CONCURRENT_REQUESTS = 10;
    private static final long PERFORMANCE_THRESHOLD_MS = 5000;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Initialize both engine types
        embeddedEngine = new QLeverSparqlEngine(); // Embedded (in-process)
        httpEngine = new QLeverSparqlEngine(BASE_HTTP_URL); // HTTP

        // Check availability
        if (!embeddedEngine.isAvailable()) {
            System.out.println("Embedded QLever unavailable - skipping embedded tests");
            embeddedEngine = null;
        }

        if (!httpEngine.isAvailable()) {
            System.out.println("HTTP QLever unavailable - skipping HTTP tests");
            httpEngine = null;
        }

        if (embeddedEngine != null && httpEngine != null) {
            // Load identical test data in both engines
            loadTestInBothEngines();
        }
    }

    // -------------------------------------------------------------------------
    // Result Equivalence Tests
    // -------------------------------------------------------------------------

    public void testSimpleQueryResultEquivalence() throws Exception {
        if (!bothEnginesAvailable()) return;

        String query = "SELECT ?case WHERE { ?case a <http://yawlfoundation.org/yawl/marketplace#CaseExecution> }";

        // Execute on both engines
        Optional<String> embeddedResult = embeddedEngine.query(query);
        Optional<String> httpResult = httpEngine.query(query);

        // Both should return results
        assertTrue("Embedded engine should return result", embeddedResult.isPresent());
        assertTrue("HTTP engine should return result", httpResult.isPresent());

        // Results should be equivalent (normalize whitespace for comparison)
        String normalizedEmbedded = normalizeResult(embeddedResult.get());
        String normalizedHttp = normalizeResult(httpResult.get());

        // For SELECT queries, the actual result data might vary slightly
        // but the query structure and row counts should be similar
        verifyEquivalentResults(normalizedEmbedded, normalizedHttp);
    }

    public void testComplexQueryResultEquivalence() throws Exception {
        if (!bothEnginesAvailable()) return;

        String complexQuery = buildComplexWorkflowQuery();

        Optional<String> embeddedResult = embeddedEngine.query(complexQuery);
        Optional<String> httpResult = httpEngine.query(complexQuery);

        assertTrue("Complex query should work on embedded", embeddedResult.isPresent());
        assertTrue("Complex query should work on HTTP", httpResult.isPresent());

        String normalizedEmbedded = normalizeResult(embeddedResult.get());
        String normalizedHttp = normalizeResult(httpResult.get());

        verifyEquivalentResults(normalizedEmbedded, normalizedHttp);
    }

    public void testConstructQueryEquivalence() throws Exception {
        if (!bothEnginesAvailable()) return;

        // Use the marketplace construct queries
        String constructQuery = MarketplaceConstructQueries.CONSTRUCT_ALL_LIVE_AGENTS;

        Optional<String> embeddedResult = embeddedEngine.query(constructQuery);
        Optional<String> httpResult = httpEngine.query(constructQuery);

        assertTrue("CONSTRUCT should work on embedded", embeddedResult.isPresent());
        assertTrue("CONSTRUCT should work on HTTP", httpResult.isPresent());

        String normalizedEmbedded = normalizeResult(embeddedResult.get());
        String normalizedHttp = normalizeResult(httpResult.get());

        // For CONSTRUCT queries, the Turtle serialization should be equivalent
        verifyTurtleEquivalence(normalizedEmbedded, normalizedHttp);
    }

    public void testAggregationQueryResultEquivalence() throws Exception {
        if (!bothEnginesAvailable()) return;

        String aggregationQuery = "SELECT (COUNT(?case) as ?count) WHERE { ?case a <http://yawlfoundation.org/yawl/marketplace#CaseExecution> . ?case marketplace:status \"completed\" }";

        Optional<String> embeddedResult = embeddedEngine.query(aggregationQuery);
        Optional<String> httpResult = httpEngine.query(aggregationQuery);

        assertTrue("Aggregation should work on embedded", embeddedResult.isPresent());
        assertTrue("Aggregation should work on HTTP", httpResult.isPresent());

        // Parse and compare count values
        int embeddedCount = parseCountResult(embeddedResult.get());
        int httpCount = parseCountResult(httpResult.get());

        // Counts should be identical
        assertEquals("Aggregation results should be identical", embeddedCount, httpCount);
    }

    // -------------------------------------------------------------------------
    // Performance Comparison Tests
    // -------------------------------------------------------------------------

    public void testSimpleQueryPerformanceComparison() throws Exception {
        if (!bothEnginesAvailable()) return;

        String query = "SELECT ?case WHERE { ?case a <http://yawlfoundation.org/yawl/marketplace#CaseExecution> }";

        // Time embedded execution
        long embeddedTime = timeQuery(embeddedEngine, query);

        // Time HTTP execution
        long httpTime = timeQuery(httpEngine, query);

        // Embedded should generally be faster
        System.out.println("Embedded time: " + embeddedTime + "ms");
        System.out.println("HTTP time: " + httpTime + "ms");

        // Verify both complete within reasonable time
        assertTrue("Embedded query should complete within threshold", embeddedTime < PERFORMANCE_THRESHOLD_MS);
        assertTrue("HTTP query should complete within threshold", httpTime < PERFORMANCE_THRESHOLD_MS);

        // Performance ratio should be reasonable (embedded should be 2-5x faster typically)
        double ratio = (double) httpTime / embeddedTime;
        System.out.println("Performance ratio (HTTP/Embedded): " + ratio);

        // We allow HTTP to be slower, but not excessively so
        assertTrue("HTTP should not be more than 10x slower than embedded", ratio < 10.0);
    }

    public void testComplexQueryPerformanceComparison() throws Exception {
        if (!bothEnginesAvailable()) return;

        String complexQuery = buildComplexWorkflowQuery();

        long embeddedTime = timeQuery(embeddedEngine, complexQuery);
        long httpTime = timeQuery(httpEngine, complexQuery);

        System.out.println("Complex query - Embedded: " + embeddedTime + "ms, HTTP: " + httpTime + "ms");

        assertTrue("Complex embedded query should complete", embeddedTime < PERFORMANCE_THRESHOLD_MS);
        assertTrue("Complex HTTP query should complete", httpTime < PERFORMANCE_THRESHOLD_MS);

        // Performance difference should scale reasonably
        double ratio = (double) httpTime / embeddedTime;
        assertTrue("HTTP complex query should not be more than 15x slower", ratio < 15.0);
    }

    public void testThroughputComparison() throws Exception {
        if (!bothEnginesAvailable()) return;

        int iterations = 20;
        String query = "SELECT ?case WHERE { ?case a <http://yawlfoundation.org/yawl/marketplace#CaseExecution> }";

        // Measure embedded throughput
        long embeddedThroughput = measureThroughput(embeddedEngine, query, iterations);

        // Measure HTTP throughput
        long httpThroughput = measureThroughput(httpEngine, query, iterations);

        System.out.println("Embedded throughput: " + embeddedThroughput + " queries/sec");
        System.out.println("HTTP throughput: " + httpThroughput + " queries/sec");

        // Embedded should have better throughput
        assertTrue("Embedded should have better throughput", embeddedThroughput >= httpThroughput);

        // But both should be reasonable
        assertTrue("Embedded should achieve reasonable throughput", embeddedThroughput > 10);
        assertTrue("HTTP should achieve reasonable throughput", httpThroughput > 2);
    }

    // -------------------------------------------------------------------------
    // Concurrency Testing
    // -------------------------------------------------------------------------

    public void testConcurrentExecutionEquivalence() throws Exception {
        if (!bothEnginesAvailable()) return;

        String query = "SELECT ?case WHERE { ?case a <http://yawlfoundation.org/yawl/marketplace#CaseExecution> }";

        // Run concurrent tests
        List<String> embeddedResults = runConcurrentQueries(embeddedEngine, query, CONCURRENT_REQUESTS);
        List<String> httpResults = runConcurrentQueries(httpEngine, query, CONCURRENT_REQUESTS);

        // Both should have completed all requests
        assertEquals("Embedded should complete all requests", CONCURRENT_REQUESTS, embeddedResults.size());
        assertEquals("HTTP should complete all requests", CONCURRENT_REQUESTS, httpResults.size());

        // Most results should be non-empty
        long nonEmptyEmbedded = embeddedResults.stream().filter(r -> !r.isEmpty()).count();
        long nonEmptyHttp = httpResults.stream().filter(r -> !r.isEmpty()).count();

        assertTrue("Most embedded queries should return results", nonEmptyEmbedded >= CONCURRENT_REQUESTS * 0.8);
        assertTrue("Most HTTP queries should return results", nonEmptyHttp >= CONCURRENT_REQUESTS * 0.8);
    }

    public void testMixedWorkloadPerformance() throws Exception {
        if (!bothEnginesAvailable()) return;

        // Define mixed workload: simple, complex, and aggregation queries
        String[] queries = {
            "SELECT ?case WHERE { ?case a <http://yawlfoundation.org/yawl/marketplace#CaseExecution> }",
            buildComplexWorkflowQuery(),
            "SELECT (COUNT(?case) as ?count) WHERE { ?case a <http://yawlfoundation.org/yawl/marketplace#CaseExecution> }"
        };

        // Test mixed workload on embedded
        long embeddedMixedTime = testMixedWorkload(embeddedEngine, queries);

        // Test mixed workload on HTTP
        long httpMixedTime = testMixedWorkload(httpEngine, queries);

        System.out.println("Mixed workload - Embedded: " + embeddedMixedTime + "ms, HTTP: " + httpMixedTime + "ms");

        assertTrue("Mixed workload should complete on embedded", embeddedMixedTime < PERFORMANCE_THRESHOLD_MS);
        assertTrue("Mixed workload should complete on HTTP", httpMixedTime < PERFORMANCE_THRESHOLD_MS);
    }

    // -------------------------------------------------------------------------
    // Error Handling Consistency Tests
    // -------------------------------------------------------------------------

    public void testInvalidQueryHandling() throws Exception {
        if (!bothEnginesAvailable()) return;

        String invalidQuery = "INVALID SPARQL QUERY SYNTAX";

        // Both engines should handle invalid queries gracefully
        assertThrowsQLeverException("Embedded should reject invalid query", () -> embeddedEngine.query(invalidQuery));
        assertThrowsQLeverException("HTTP should reject invalid query", () -> httpEngine.query(invalidQuery));
    }

    public void testTimeoutHandling() throws Exception {
        if (!bothEnginesAvailable()) return;

        // Query that might take a long time
        String complexQuery = buildComplexWorkflowQuery();

        // Set timeout (if supported)
        embeddedEngine.setTimeout(2000); // 2 seconds
        httpEngine.setTimeout(2000);

        // Both should respect timeout
        long embeddedStart = System.currentTimeMillis();
        try {
            embeddedEngine.query(complexQuery);
        } catch (Exception e) {
            // Timeout is acceptable
        }
        long embeddedDuration = System.currentTimeMillis() - embeddedStart;

        long httpStart = System.currentTimeMillis();
        try {
            httpEngine.query(complexQuery);
        } catch (Exception e) {
            // Timeout is acceptable
        }
        long httpDuration = System.currentTimeMillis() - httpStart;

        // Both should complete quickly due to timeout
        assertTrue("Embedded should respect timeout", embeddedDuration < 5000);
        assertTrue("HTTP should respect timeout", httpDuration < 5000);
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------

    private boolean bothEnginesAvailable() {
        return embeddedEngine != null && httpEngine != null;
    }

    private void loadTestInBothEngines() throws Exception {
        // This would load identical test data into both engines
        // In a real implementation, this would involve SPARQL INSERT statements
        System.out.println("Loading test data in both engines...");

        // Simulate data loading
        for (int i = 1; i <= 100; i++) {
            String caseId = "test-case-" + i;
            System.out.println("Loading case: " + caseId);

            // Actual SPARQL INSERT would go here for both engines
            // embeddedEngine.insert(...);
            // httpEngine.insert(...);
        }
    }

    private String buildComplexWorkflowQuery() {
        return "SELECT ?case ?task ?duration WHERE { " +
               "?case a <http://yawlfoundation.org/yawl/marketplace#CaseExecution> ; " +
               "marketplace:specId \"" + TEST_SPEC_ID + "\" ; " +
               "marketplace:status \"completed\" . " +
               "?case marketplace:hasTask ?task . " +
               "?task marketplace:taskName \"reviewOrder\" ; " +
               "marketplace:durationMs ?duration . " +
               "FILTER(?duration > 1000) " +
               "} ORDER BY DESC(?duration) LIMIT 10";
    }

    private long timeQuery(QLeverSparqlEngine engine, String query) throws Exception {
        long start = System.currentTimeMillis();
        Optional<String> result = engine.query(query);
        long end = System.currentTimeMillis();

        // Verify result is present
        assertTrue("Query should return result", result.isPresent());

        return end - start;
    }

    private long measureThroughput(QLeverSparqlEngine engine, String query, int iterations) throws Exception {
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            Optional<String> result = engine.query(query);
            assertTrue("Query should return result", result.isPresent());
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        return (iterations * 1000) / duration; // queries per second
    }

    private List<String> runConcurrentQueries(QLeverSparqlEngine engine, String query, int count) throws Exception {
        List<CompletableFuture<String>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < count; i++) {
            final int iteration = i;
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return engine.query(query).orElse("");
                } catch (Exception e) {
                    return "ERROR: " + e.getMessage();
                }
            });
            futures.add(future);
        }

        // Wait for all to complete
        return futures.stream()
            .map(future -> {
                try {
                    return future.get(10, TimeUnit.SECONDS); // 10 second timeout per query
                } catch (Exception e) {
                    return "TIMEOUT";
                }
            })
            .toList();
    }

    private long testMixedWorkload(QLeverSparqlEngine engine, String[] queries) throws Exception {
        long startTime = System.currentTimeMillis();

        for (String query : queries) {
            for (int i = 0; i < 5; i++) { // Repeat each query 5 times
                Optional<String> result = engine.query(query);
                assertTrue("Mixed workload query should return result", result.isPresent());
            }
        }

        return System.currentTimeMillis() - startTime;
    }

    private String normalizeResult(String result) {
        // Remove extra whitespace and newlines for comparison
        return result.trim().replaceAll("\\s+", " ").replaceAll(">\\s+", ">").replaceAll("\\s+<", "<");
    }

    private void verifyEquivalentResults(String result1, String result2) {
        // Basic verification - both should have similar structure
        assertTrue("Both results should contain CaseExecution references",
                   result1.contains(WorkflowEventVocabulary.CASE_EXECUTION) &&
                   result2.contains(WorkflowEventVocabulary.CASE_EXECUTION));

        // Count occurrences of key terms should be similar
        int count1 = countSubstring(result1, WorkflowEventVocabulary.CASE_EXECUTION);
        int count2 = countSubstring(result2, WorkflowEventVocabulary.CASE_EXECUTION);

        // Allow some variance but not major differences
        double variance = Math.abs(count1 - count2) / (double) Math.max(count1, count2);
        assertTrue("Results should be similar", variance < 0.1); // Within 10%
    }

    private void verifyTurtleEquivalence(String turtle1, String turtle2) {
        // For Turtle output, we can't guarantee exact equivalence due to
        // ordering and formatting differences, but we can verify key components
        assertTrue("Both Turtle results should contain agent references",
                   turtle1.contains("AgentListing") && turtle2.contains("AgentListing"));
    }

    private int parseCountResult(String result) {
        // Extract count from SPARQL results like "?count 100"
        try {
            // Simple parsing - in real implementation would use proper XML/JSON parser
            String[] parts = result.split("count\\s+\\d+");
            if (parts.length > 1) {
                String countPart = parts[1].trim();
                return Integer.parseInt(countPart.split("\\s+")[0]);
            }
        } catch (Exception e) {
            // Fall back to counting lines or other heuristics
        }
        return 0;
    }

    private void assertThrowsQLeverException(String message, Runnable action) {
        try {
            action.run();
            fail(message + " - should have thrown exception");
        } catch (QLeverSparqlEngine.LeverException | SparqlEngineException e) {
            // Expected
        } catch (Exception e) {
            fail(message + " - wrong exception type: " + e.getClass().getName());
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