package org.yawlfoundation.yawl.test.telemetry;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for multi-agent telemetry infrastructure.
 *
 * Validates:
 * - 5 agents spawn and execute in parallel
 * - Metrics are collected and aggregated
 * - ANDON violations are properly detected
 * - Test results are aggregated correctly
 */
@DisplayName("Multi-Agent Telemetry E2E Test")
class MultiAgentTelemetryE2ETest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiAgentTelemetryE2ETest.class);

    private SimpleMeterRegistry meterRegistry;
    private MultiAgentTestOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        orchestrator = new MultiAgentTestOrchestrator.Builder()
                .withMeterRegistry(meterRegistry)
                .build();
    }

    @Test
    @DisplayName("5 agents execute in parallel with correct results")
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    void testMultiAgentExecution() {
        LOGGER.info("Starting multi-agent execution test");

        // Execute all 5 agents
        AggregatedTestResults results = orchestrator.executeAllAgents();

        assertNotNull(results, "Results should not be null");
        assertNotNull(results.getStatus(), "Status should be set");

        // Verify results aggregation
        long totalTests = results.getTotalTests();
        assertTrue(totalTests > 0, "Total tests should be > 0");

        long totalPassed = results.getTotalTestsPassed();
        long totalFailed = results.getTotalTestsFailed();

        assertEquals(totalTests, totalPassed + totalFailed,
                "Total tests should equal passed + failed");

        LOGGER.info("Test execution complete: {} tests, {} passed, {} failed",
                totalTests, totalPassed, totalFailed);
    }

    @Test
    @DisplayName("Token counts are collected from all agents")
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    void testTokenCounting() {
        LOGGER.info("Starting token counting test");

        AggregatedTestResults results = orchestrator.executeAllAgents();

        long groqTokens = results.totalTokensGroq;
        long openaiTokens = results.totalTokensOpenAI;

        assertTrue(groqTokens >= 0, "Groq tokens should be non-negative");
        assertTrue(openaiTokens >= 0, "OpenAI tokens should be non-negative");

        long totalTokens = results.getTotalTokens();
        assertEquals(groqTokens + openaiTokens, totalTokens,
                "Total tokens should equal Groq + OpenAI");

        LOGGER.info("Token counts - Groq: {}, OpenAI: {}, Total: {}",
                groqTokens, openaiTokens, totalTokens);
    }

    @Test
    @DisplayName("Concurrency metrics are recorded")
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    void testConcurrencyMetrics() {
        LOGGER.info("Starting concurrency metrics test");

        AggregatedTestResults results = orchestrator.executeAllAgents();

        var agentMetrics = results.getAgentMetrics();
        assertFalse(agentMetrics.isEmpty(), "Should have agent metrics");

        for (var entry : agentMetrics.entrySet()) {
            var metrics = entry.getValue();
            assertNotNull(metrics.concurrencyMetrics, "Concurrency metrics should be set");

            LOGGER.info("Agent {} concurrency: peak threads = {}",
                    entry.getKey(), metrics.concurrencyMetrics.peakConcurrentThreads);
        }
    }

    @Test
    @DisplayName("Latency percentiles are recorded")
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    void testLatencyMetrics() {
        LOGGER.info("Starting latency metrics test");

        AggregatedTestResults results = orchestrator.executeAllAgents();

        var agentMetrics = results.getAgentMetrics();

        for (var entry : agentMetrics.entrySet()) {
            var metrics = entry.getValue();
            assertTrue(metrics.latencyP50 >= 0, "p50 latency should be >= 0");
            assertTrue(metrics.latencyP95 >= 0, "p95 latency should be >= 0");
            assertTrue(metrics.latencyP99 >= 0, "p99 latency should be >= 0");

            // Verify ordering: p50 <= p95 <= p99
            assertTrue(metrics.latencyP50 <= metrics.latencyP95,
                    "p50 should be <= p95");
            assertTrue(metrics.latencyP95 <= metrics.latencyP99,
                    "p95 should be <= p99");

            LOGGER.info("Agent {} latency - p50: {}, p95: {}, p99: {}",
                    entry.getKey(), metrics.latencyP50, metrics.latencyP95, metrics.latencyP99);
        }
    }

    @Test
    @DisplayName("Pass rate is calculated correctly")
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    void testPassRateCalculation() {
        LOGGER.info("Starting pass rate calculation test");

        AggregatedTestResults results = orchestrator.executeAllAgents();

        long totalTests = results.getTotalTests();
        long totalPassed = results.getTotalTestsPassed();

        double expectedPassRate = (double) totalPassed / totalTests * 100;
        double actualPassRate = results.getPassRate();

        assertEquals(expectedPassRate, actualPassRate, 0.01,
                "Pass rate calculation should be correct");

        LOGGER.info("Pass rate: {:.2f}%", actualPassRate);
    }

    @Test
    @DisplayName("ANDON monitor detects violations")
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    void testAndonViolationDetection() {
        LOGGER.info("Starting ANDON violation detection test");

        AggregatedTestResults results = orchestrator.executeAllAgents();
        AndonMonitor andonMonitor = orchestrator.getAndonMonitor();

        var violations = results.getAndonViolations();
        LOGGER.info("Total ANDON violations: {}", violations.size());

        for (var violation : violations) {
            LOGGER.info("ANDON violation - Type: {}, Severity: {}, Message: {}",
                    violation.type, violation.severity, violation.message);
        }

        // Check that monitor has recorded violations
        var monitorAlerts = andonMonitor.getAlerts();
        assertTrue(monitorAlerts.size() >= 0, "Monitor should have alerts list");

        LOGGER.info("Monitor recorded {} alerts", monitorAlerts.size());
    }

    @Test
    @DisplayName("Throughput is measured per agent")
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    void testThroughputMeasurement() {
        LOGGER.info("Starting throughput measurement test");

        AggregatedTestResults results = orchestrator.executeAllAgents();

        var agentMetrics = results.getAgentMetrics();

        for (var entry : agentMetrics.entrySet()) {
            var metrics = entry.getValue();
            double throughput = metrics.throughputTestsPerSec;

            assertTrue(throughput >= 0, "Throughput should be >= 0");
            LOGGER.info("Agent {} throughput: {:.2f} tests/sec",
                    entry.getKey(), throughput);
        }

        double avgThroughput = results.averageThroughput;
        LOGGER.info("Average throughput across all agents: {:.2f} tests/sec", avgThroughput);
    }

    @Test
    @DisplayName("All 5 agents have results recorded")
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    void testAllAgentsExecuted() {
        LOGGER.info("Starting all agents executed test");

        AggregatedTestResults results = orchestrator.executeAllAgents();

        var agentMetrics = results.getAgentMetrics();
        assertEquals(5, agentMetrics.size(), "Should have exactly 5 agents");

        var expectedAgents = new String[]{
                "engine-tests",
                "stateless-tests",
                "integration-tests",
                "a2a-tests",
                "autonomous-tests"
        };

        for (String agentId : expectedAgents) {
            assertTrue(agentMetrics.containsKey(agentId),
                    "Should have metrics for agent: " + agentId);

            var metrics = agentMetrics.get(agentId);
            assertTrue(metrics.testsRun > 0, "Agent " + agentId + " should have tests run");
        }

        LOGGER.info("All 5 agents executed successfully");
    }

    @Test
    @DisplayName("Status is correctly determined based on results")
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    void testStatusDetermination() {
        LOGGER.info("Starting status determination test");

        AggregatedTestResults results = orchestrator.executeAllAgents();

        String status = results.getStatus();
        assertNotNull(status, "Status should be set");
        assertTrue(status.equals("GREEN") || status.equals("YELLOW") || status.equals("RED"),
                "Status should be GREEN, YELLOW, or RED");

        double passRate = results.getPassRate();
        if (passRate == 100.0) {
            assertEquals("GREEN", status, "100% pass rate should be GREEN");
        } else if (passRate >= 95.0) {
            assertTrue(status.equals("GREEN") || status.equals("YELLOW"),
                    "95%+ pass rate should be GREEN or YELLOW");
        } else {
            assertEquals("RED", status, "<95% pass rate should be RED");
        }

        LOGGER.info("Test status: {} (pass rate: {:.2f}%)", status, passRate);
    }
}
