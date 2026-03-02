package org.yawlfoundation.yawl.test.telemetry;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated results from all 5 test agents.
 *
 * Provides:
 * - Combined test counts and pass rate
 * - Aggregated token counts (Groq + OpenAI)
 * - Throughput and latency statistics
 * - ANDON violations across all agents
 */
public class AggregatedTestResults {

    @JsonProperty("test_run_id")
    private String testRunId;

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("start_time")
    private Instant startTime;

    @JsonProperty("end_time")
    private Instant endTime;

    @JsonProperty("total_duration_ms")
    private long totalDurationMs;

    @JsonProperty("status")
    private String status = "GREEN";

    // Aggregated test counts
    @JsonProperty("total_tests")
    private long totalTests;

    @JsonProperty("total_tests_passed")
    private long totalTestsPassed;

    @JsonProperty("total_tests_failed")
    private long totalTestsFailed;

    @JsonProperty("pass_rate_percent")
    private double passRate;

    // Token counts
    @JsonProperty("total_tokens_groq")
    private long totalTokensGroq;

    @JsonProperty("total_tokens_openai")
    private long totalTokensOpenAI;

    @JsonProperty("total_tokens_all")
    private long totalTokensAll;

    // Performance metrics
    @JsonProperty("average_throughput_tests_per_sec")
    private double averageThroughput;

    // Agent results
    @JsonProperty("agents")
    private final Map<String, TelemetryCollector.AgentMetrics> agentMetrics = new HashMap<>();

    // Exceptions per agent
    @JsonProperty("agent_exceptions")
    private final Map<String, String> agentExceptions = new HashMap<>();

    // ANDON violations
    @JsonProperty("andon_violations")
    private final List<AndonViolationRecord> andonViolations = new ArrayList<>();

    public AggregatedTestResults() {
        this.timestamp = Instant.now();
        this.startTime = Instant.now();
    }

    // Setters
    public void setTestRunId(String testRunId) {
        this.testRunId = testRunId;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public void setTotalDurationMs(long durationMs) {
        this.totalDurationMs = durationMs;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setTotalTests(long totalTests) {
        this.totalTests = totalTests;
    }

    public void setTotalTestsPassed(long totalTestsPassed) {
        this.totalTestsPassed = totalTestsPassed;
    }

    public void setTotalTestsFailed(long totalTestsFailed) {
        this.totalTestsFailed = totalTestsFailed;
    }

    public void setPassRate(double passRate) {
        this.passRate = passRate;
        // Update status based on pass rate
        if (passRate == 100.0 && andonViolations.isEmpty()) {
            this.status = "GREEN";
        } else if (passRate >= 95.0) {
            this.status = "YELLOW";
        } else {
            this.status = "RED";
        }
    }

    public void setTotalTokensGroq(long tokens) {
        this.totalTokensGroq = tokens;
        updateTotalTokens();
    }

    public void setTotalTokensOpenAI(long tokens) {
        this.totalTokensOpenAI = tokens;
        updateTotalTokens();
    }

    public void setAverageThroughput(double throughput) {
        this.averageThroughput = throughput;
    }

    // Agent management
    public void addAgentMetrics(String agentId, TelemetryCollector.AgentMetrics metrics) {
        agentMetrics.put(agentId, metrics);
    }

    public void addAgentResults(AgentTestResults agentResults) {
        // Convert agent results to metrics format for aggregation
        var metrics = new TelemetryCollector.AgentMetrics(agentResults.getAgentId());
        metrics.testsRun = agentResults.getTestsRun();
        metrics.testsPassed = agentResults.getTestsPassed();
        metrics.testsFailed = agentResults.getTestsFailed();

        var tokenCounts = agentResults.getTokenCounts();
        if (tokenCounts.containsKey("groq")) {
            metrics.tokensGroq = tokenCounts.get("groq");
        }
        if (tokenCounts.containsKey("openai-gpt-oss-20b")) {
            metrics.tokensOpenAI = tokenCounts.get("openai-gpt-oss-20b");
        }

        metrics.throughputTestsPerSec = agentResults.getThroughputTestsPerSec();
        metrics.concurrencyMetrics = agentResults.getConcurrencyMetrics();

        agentMetrics.put(agentResults.getAgentId(), metrics);

        // Add violations
        for (var violation : agentResults.getViolations()) {
            andonViolations.add(new AndonViolationRecord(
                    agentResults.getAgentId(),
                    violation.type,
                    violation.severity,
                    violation.message
            ));
        }
    }

    public void addAgentException(String agentId, Exception e) {
        agentExceptions.put(agentId, e.getMessage());
        this.status = "RED";
    }

    public void addAndonViolation(AndonViolationRecord violation) {
        andonViolations.add(violation);
        if ("CRITICAL".equals(violation.severity)) {
            this.status = "RED";
        }
    }

    // Getters
    public long getTotalTests() {
        return totalTests;
    }

    public long getTotalTestsPassed() {
        return totalTestsPassed;
    }

    public long getTotalTestsFailed() {
        return totalTestsFailed;
    }

    public double getPassRate() {
        return passRate;
    }

    public long getTotalTokens() {
        return totalTokensAll;
    }

    public String getStatus() {
        return status;
    }

    public Map<String, TelemetryCollector.AgentMetrics> getAgentMetrics() {
        return agentMetrics;
    }

    public List<AndonViolationRecord> getAndonViolations() {
        return andonViolations;
    }

    public String getSummary() {
        return String.format(
                "Total tests: %d, Passed: %d (%2.1f%%), Failed: %d, " +
                        "Status: %s, Tokens: Groq=%d OpenAI=%d",
                totalTests, totalTestsPassed, passRate, totalTestsFailed,
                status, totalTokensGroq, totalTokensOpenAI
        );
    }

    private void updateTotalTokens() {
        this.totalTokensAll = totalTokensGroq + totalTokensOpenAI;
    }

    /**
     * Record for ANDON violations.
     */
    public static class AndonViolationRecord {
        @JsonProperty("agent_id")
        public String agentId;

        @JsonProperty("type")
        public String type;

        @JsonProperty("severity")
        public String severity;

        @JsonProperty("message")
        public String message;

        @JsonProperty("timestamp")
        public Instant timestamp;

        public AndonViolationRecord(String agentId, String type, String severity, String message) {
            this.agentId = agentId;
            this.type = type;
            this.severity = severity;
            this.message = message;
            this.timestamp = Instant.now();
        }
    }
}
