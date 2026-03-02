package org.yawlfoundation.yawl.test.telemetry;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test results from a single test agent execution.
 *
 * Captures:
 * - Test counts (run, passed, failed)
 * - Execution timing
 * - Token counts (from LLM tests)
 * - Concurrency metrics
 * - ANDON violations
 * - Latency/throughput data
 */
public class AgentTestResults {

    @JsonProperty("agent_id")
    private final String agentId;

    @JsonProperty("test_suite_name")
    private final String testSuiteName;

    @JsonProperty("start_time")
    private Instant startTime;

    @JsonProperty("end_time")
    private Instant endTime;

    @JsonProperty("duration_ms")
    private long durationMs;

    // Test counts
    @JsonProperty("tests_run")
    private final AtomicLong testsRun = new AtomicLong(0);

    @JsonProperty("tests_passed")
    private final AtomicLong testsPassed = new AtomicLong(0);

    @JsonProperty("tests_failed")
    private final AtomicLong testsFailed = new AtomicLong(0);

    // Telemetry metrics
    @JsonProperty("token_counts")
    private final Map<String, Long> tokenCounts = new HashMap<>();

    @JsonProperty("concurrency_metrics")
    private final ConcurrencyMetrics concurrencyMetrics = new ConcurrencyMetrics();

    @JsonProperty("latency_percentiles_ms")
    private final Map<String, Double> latencyPercentiles = new HashMap<>();

    @JsonProperty("throughput_tests_per_sec")
    private double throughputTestsPerSec;

    // ANDON violations
    @JsonProperty("violations")
    private final List<AndonViolation> violations = new ArrayList<>();

    @JsonProperty("exception_message")
    private String exceptionMessage;

    @JsonProperty("exception_type")
    private String exceptionType;

    public AgentTestResults(String agentId, String testSuiteName) {
        this.agentId = agentId;
        this.testSuiteName = testSuiteName;
        this.startTime = Instant.now();
    }

    // Test count operations
    public void recordTestRun() {
        testsRun.incrementAndGet();
    }

    public void recordTestPassed() {
        testsPassed.incrementAndGet();
    }

    public void recordTestFailed() {
        testsFailed.incrementAndGet();
    }

    public void recordTestsRun(long count) {
        testsRun.addAndGet(count);
    }

    public void recordTestsPassed(long count) {
        testsPassed.addAndGet(count);
    }

    public void recordTestsFailed(long count) {
        testsFailed.addAndGet(count);
    }

    // Telemetry operations
    public void recordTokenCount(String llmName, long tokenCount) {
        tokenCounts.put(llmName, tokenCounts.getOrDefault(llmName, 0L) + tokenCount);
    }

    public void recordConcurrentThreads(long count) {
        concurrencyMetrics.recordConcurrentThreads(count);
    }

    public void recordLatencyPercentile(String percentile, double latencyMs) {
        latencyPercentiles.put(percentile, latencyMs);
    }

    public void recordThroughput(double testsPerSec) {
        this.throughputTestsPerSec = testsPerSec;
    }

    // ANDON violation operations
    public void addViolation(AndonViolation violation) {
        violations.add(violation);
    }

    public void recordException(Exception e) {
        this.exceptionType = e.getClass().getSimpleName();
        this.exceptionMessage = e.getMessage();
    }

    // Getters
    public String getAgentId() {
        return agentId;
    }

    public String getTestSuiteName() {
        return testSuiteName;
    }

    public long getTestsRun() {
        return testsRun.get();
    }

    public long getTestsPassed() {
        return testsPassed.get();
    }

    public long getTestsFailed() {
        return testsFailed.get();
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public Map<String, Long> getTokenCounts() {
        return tokenCounts;
    }

    public ConcurrencyMetrics getConcurrencyMetrics() {
        return concurrencyMetrics;
    }

    public Map<String, Double> getLatencyPercentiles() {
        return latencyPercentiles;
    }

    public double getThroughputTestsPerSec() {
        return throughputTestsPerSec;
    }

    public List<AndonViolation> getViolations() {
        return violations;
    }

    public boolean hasViolations() {
        return !violations.isEmpty();
    }

    public int getViolationCount() {
        return violations.size();
    }

    public String getSummary() {
        return String.format(
                "Agent: %s, Passed: %d, Failed: %d, Duration: %dms, Violations: %d",
                agentId, testsPassed.get(), testsFailed.get(), durationMs, violations.size()
        );
    }

    /**
     * Nested class for concurrency metrics.
     */
    public static class ConcurrencyMetrics {
        @JsonProperty("peak_concurrent_threads")
        public long peakConcurrentThreads;

        @JsonProperty("average_concurrent_threads")
        public double averageConcurrentThreads;

        @JsonProperty("max_queue_depth")
        public long maxQueueDepth;

        public void recordConcurrentThreads(long count) {
            if (count > peakConcurrentThreads) {
                peakConcurrentThreads = count;
            }
        }
    }

    /**
     * Nested class for ANDON violations.
     */
    public static class AndonViolation {
        @JsonProperty("type")
        public String type;

        @JsonProperty("severity")
        public String severity;

        @JsonProperty("message")
        public String message;

        @JsonProperty("timestamp")
        public Instant timestamp;

        public AndonViolation(String type, String severity, String message) {
            this.type = type;
            this.severity = severity;
            this.message = message;
            this.timestamp = Instant.now();
        }
    }
}
