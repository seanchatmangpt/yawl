package org.yawlfoundation.yawl.benchmark.framework;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprehensive benchmark report with aggregated metrics.
 * Provides detailed performance analysis and summary statistics.
 *
 * @since 6.0.0-GA
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BenchmarkReport {

    @JsonProperty("agent_name")
    private final String agentName;

    @JsonProperty("benchmark_area")
    private final String benchmarkArea;

    @JsonProperty("start_time")
    private final Instant startTime;

    @JsonProperty("end_time")
    private final Instant endTime;

    @JsonProperty("total_operations")
    private final long totalOperations;

    @JsonProperty("successful_operations")
    private final long successfulOperations;

    @JsonProperty("failed_operations")
    private final long failedOperations;

    @JsonProperty("errors")
    private final List<String> errors;

    @JsonProperty("performance_summary")
    private final Map<String, Object> performanceSummary;

    @JsonProperty("duration_ms")
    private final long durationMs;

    public BenchmarkReport(String agentName, String benchmarkArea,
                          Instant startTime, Instant endTime,
                          long totalOperations, long successfulOperations,
                          long failedOperations, List<String> errors,
                          Map<String, Object> performanceSummary) {
        this.agentName = agentName;
        this.benchmarkArea = benchmarkArea;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalOperations = totalOperations;
        this.successfulOperations = successfulOperations;
        this.failedOperations = failedOperations;
        this.errors = errors != null ? errors : List.of();
        this.performanceSummary = performanceSummary != null ? performanceSummary : new ConcurrentHashMap<>();
        this.durationMs = endTime != null && startTime != null ?
            endTime.toEpochMilli() - startTime.toEpochMilli() : 0;
    }

    // Default constructor for deserialization
    public BenchmarkReport() {
        this("", "", null, null, 0, 0, 0, List.of(), new ConcurrentHashMap<>());
    }

    // Getters
    public String getAgentName() { return agentName; }
    public String getBenchmarkArea() { return benchmarkArea; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public long getTotalOperations() { return totalOperations; }
    public long getSuccessfulOperations() { return successfulOperations; }
    public long getFailedOperations() { return failedOperations; }
    public List<String> getErrors() { return errors; }
    public Map<String, Object> getPerformanceSummary() { return performanceSummary; }
    public long getDurationMs() { return durationMs; }

    @JsonProperty("success_rate")
    public double getSuccessRate() {
        return totalOperations == 0 ? 0 : (double) successfulOperations / totalOperations;
    }

    @JsonProperty("failure_rate")
    public double getFailureRate() {
        return totalOperations == 0 ? 0 : (double) failedOperations / totalOperations;
    }
}