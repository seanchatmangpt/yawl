package org.yawlfoundation.yawl.benchmark.framework;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standardized result collection for benchmark executions.
 * Provides performance metrics tracking with metadata and context.
 *
 * @since 6.0.0-GA
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BenchmarkResult {

    @JsonProperty("benchmark_id")
    private String benchmarkId;

    @JsonProperty("execution_timestamp")
    private Instant executionTimestamp;

    @JsonProperty("status")
    private ExecutionStatus status;

    @JsonProperty("duration_ms")
    private long durationMs;

    @JsonProperty("throughput_per_sec")
    private double throughputPerSec;

    @JsonProperty("metrics")
    private Map<String, MetricValue> metrics = new ConcurrentHashMap<>();

    @JsonProperty("errors")
    private List<BenchmarkError> errors = Collections.synchronizedList(new ArrayList<>());

    @JsonProperty("context")
    private BenchmarkContext context;

    @JsonProperty("memory_usage_before_mb")
    private double memoryUsageBeforeMb;

    @JsonProperty("memory_usage_after_mb")
    private double memoryUsageAfterMb;

    @JsonProperty("peak_memory_usage_mb")
    private double peakMemoryUsageMb;

    @JsonProperty("cpu_usage_percent")
    private double cpuUsagePercent;

    @JsonProperty("benchmark_metadata")
    private Map<String, String> metadata = new ConcurrentHashMap<>();

    // Metrics collection
    private final AtomicInteger operationCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    /**
     * Execution status enumeration.
     */
    public enum ExecutionStatus {
        SUCCESS, FAILED, TIMEOUT, CANCELLED, PARTIAL_SUCCESS
    }

    /**
     * Metric value with statistics.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MetricValue {
        @JsonProperty("value")
        private double value;

        @JsonProperty("unit")
        private String unit;

        @JsonProperty("min")
        private Double min;

        @JsonProperty("max")
        private Double max;

        @JsonProperty("mean")
        private Double mean;

        @JsonProperty("stddev")
        private Double stddev;

        public MetricValue(double value, String unit) {
            this.value = value;
            this.unit = unit;
        }

        // Getters and setters
        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public Double getMin() { return min; }
        public void setMin(Double min) { this.min = min; }
        public Double getMax() { return max; }
        public void setMax(Double max) { this.max = max; }
        public Double getMean() { return mean; }
        public void setMean(Double mean) { this.mean = mean; }
        public Double getStddev() { return stddev; }
        public void setStddev(Double stddev) { this.stddev = stddev; }
    }

    /**
     * Benchmark error representation.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BenchmarkError {
        @JsonProperty("timestamp")
        private Instant timestamp;

        @JsonProperty("error_type")
        private String errorType;

        @JsonProperty("error_message")
        private String errorMessage;

        @JsonProperty("error_stack_trace")
        private String errorStackTrace;

        @JsonProperty("error_category")
        private ErrorCategory category;

        public enum ErrorCategory {
            SYSTEM_ERROR, BUSINESS_ERROR, CONFIGURATION_ERROR, TIMEOUT_ERROR, RESOURCE_ERROR
        }

        public BenchmarkError(String errorType, String errorMessage, ErrorCategory category) {
            this.timestamp = Instant.now();
            this.errorType = errorType;
            this.errorMessage = errorMessage;
            this.category = category;
        }

        // Getters and setters
        public Instant getTimestamp() { return timestamp; }
        public String getErrorType() { return errorType; }
        public String getErrorMessage() { return errorMessage; }
        public String getErrorStackTrace() { return errorStackTrace; }
        public void setErrorStackTrace(String errorStackTrace) { this.errorStackTrace = errorStackTrace; }
        public ErrorCategory getCategory() { return category; }
    }

    /**
     * Benchmark context with environment information.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BenchmarkContext {
        @JsonProperty("jvm_version")
        private String jvmVersion;

        @JsonProperty("os_info")
        private String osInfo;

        @JsonProperty("cpu_cores")
        private int cpuCores;

        @JsonProperty("memory_total_mb")
        private long memoryTotalMb;

        @JsonProperty("benchmark_config")
        private Map<String, Object> configuration;

        public BenchmarkContext(String jvmVersion, String osInfo, int cpuCores, long memoryTotalMb) {
            this.jvmVersion = jvmVersion;
            this.osInfo = osInfo;
            this.cpuCores = cpuCores;
            this.memoryTotalMb = memoryTotalMb;
        }

        // Getters and setters
        public String getJvmVersion() { return jvmVersion; }
        public String getOsInfo() { return osInfo; }
        public int getCpuCores() { return cpuCores; }
        public long getMemoryTotalMb() { return memoryTotalMb; }
        public Map<String, Object> getConfiguration() { return configuration; }
        public void setConfiguration(Map<String, Object> configuration) { this.configuration = configuration; }
    }

    // Constructor
    public BenchmarkResult(String benchmarkId) {
        this.benchmarkId = benchmarkId;
        this.executionTimestamp = Instant.now();
        this.status = ExecutionStatus.SUCCESS;
    }

    // Metric collection methods
    public void addMetric(String name, double value, String unit) {
        MetricValue metric = new MetricValue(value, unit);
        metrics.put(name, metric);

        // Update basic statistics
        if (metrics.containsKey(name)) {
            MetricValue existing = metrics.get(name);
            if (existing.getMin() == null || value < existing.getMin()) {
                existing.setMin(value);
            }
            if (existing.getMax() == null || value > existing.getMax()) {
                existing.setMax(value);
            }
        }
    }

    public void incrementOperationCount() {
        operationCount.incrementAndGet();
    }

    public void incrementSuccessCount() {
        successCount.incrementAndGet();
    }

    public void incrementFailureCount() {
        failureCount.incrementAndGet();
    }

    public void addError(String errorType, String errorMessage, BenchmarkError.ErrorCategory category) {
        BenchmarkError error = new BenchmarkError(errorType, errorMessage, category);
        errors.add(error);

        if (status != ExecutionStatus.FAILED && errors.size() > 0) {
            status = ExecutionStatus.PARTIAL_SUCCESS;
        }
    }

    public void setFailureWithReason(String errorType, String errorMessage) {
        this.status = ExecutionStatus.FAILED;
        addError(errorType, errorMessage, BenchmarkError.ErrorCategory.SYSTEM_ERROR);
    }

    public void recordMemoryUsageBefore() {
        Runtime runtime = Runtime.getRuntime();
        memoryUsageBeforeMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0);
    }

    public void recordMemoryUsageAfter() {
        Runtime runtime = Runtime.getRuntime();
        memoryUsageAfterMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0);
        peakMemoryUsageMb = memoryUsageAfterMb;
    }

    public void updatePeakMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        double currentUsage = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0);
        peakMemoryUsageMb = Math.max(peakMemoryUsageMb, currentUsage);
    }

    // Calculate throughput
    public void calculateThroughput(long durationMs) {
        if (durationMs > 0) {
            this.durationMs = durationMs;
            this.throughputPerSec = (operationCount.get() * 1000.0) / durationMs;
        }
    }

    // Success rate calculation
    public double getSuccessRate() {
        int total = operationCount.get();
        return total > 0 ? (successCount.get() * 100.0) / total : 0.0;
    }

    // Getters and setters
    public String getBenchmarkId() { return benchmarkId; }
    public Instant getExecutionTimestamp() { return executionTimestamp; }
    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) { this.status = status; }
    public long getDurationMs() { return durationMs; }
    public double getThroughputPerSec() { return throughputPerSec; }
    public Map<String, MetricValue> getMetrics() { return metrics; }
    public List<BenchmarkError> getErrors() { return errors; }
    public BenchmarkContext getContext() { return context; }
    public void setContext(BenchmarkContext context) { this.context = context; }
    public double getMemoryUsageBeforeMb() { return memoryUsageBeforeMb; }
    public double getMemoryUsageAfterMb() { return memoryUsageAfterMb; }
    public double getPeakMemoryUsageMb() { return peakMemoryUsageMb; }
    public double getCpuUsagePercent() { return cpuUsagePercent; }
    public void setCpuUsagePercent(double cpuUsagePercent) { this.cpuUsagePercent = cpuUsagePercent; }
    public Map<String, String> getMetadata() { return metadata; }

    @JsonProperty("operation_count")
    public int getOperationCount() { return operationCount.get(); }

    @JsonProperty("success_count")
    public int getSuccessCount() { return successCount.get(); }

    @JsonProperty("failure_count")
    public int getFailureCount() { return failureCount.get(); }

    @JsonProperty("success_rate_percent")
    public double getSuccessRatePercent() { return getSuccessRate(); }
}