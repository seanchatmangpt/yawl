package org.yawlfoundation.yawl.benchmark.framework;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * Centralized configuration management for benchmarks.
 * Provides environment-specific settings and benchmark parameters.
 *
 * @since 6.0.0-GA
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BenchmarkConfiguration {

    @JsonProperty("benchmark_name")
    private String benchmarkName;

    @JsonProperty("benchmark_description")
    private String benchmarkDescription;

    @JsonProperty("environment")
    private EnvironmentType environment;

    @JsonProperty("parallel_threads")
    private int parallelThreads;

    @JsonProperty("warmup_iterations")
    private int warmupIterations;

    @JsonProperty("measurement_iterations")
    private int measurementIterations;

    @JsonProperty("timeout_duration")
    private Duration timeoutDuration;

    @JsonProperty("memory_threshold_mb")
    private long memoryThresholdMb;

    @JsonProperty("cpu_threshold_percent")
    private double cpuThresholdPercent;

    @JsonProperty("throughput_target_per_sec")
    private double throughputTargetPerSec;

    @JsonProperty("error_rate_threshold_percent")
    private double errorRateThresholdPercent;

    @JsonProperty("retries_on_failure")
    private int retriesOnFailure;

    @JsonProperty("circuit_breaker_enabled")
    private boolean circuitBreakerEnabled;

    @JsonProperty("circuit_breaker_failure_rate_threshold")
    private double circuitBreakerFailureRateThreshold;

    @JsonProperty("circuit_breaker_recovery_timeout")
    private Duration circuitBreakerRecoveryTimeout;

    @JsonProperty("telemetry_enabled")
    private boolean telemetryEnabled;

    @JsonProperty("output_format")
    private OutputFormat outputFormat;

    @JsonProperty("custom_parameters")
    private Map<String, Object> customParameters;

    @JsonProperty("dependency_configurations")
    private Map<String, DependencyConfiguration> dependencyConfigurations;

    /**
     * Environment types for benchmark execution.
     */
    public enum EnvironmentType {
        LOCAL, REMOTE, CONTAINERIZED, KUBERNETES
    }

    /**
     * Output format options for benchmark results.
     */
    public enum OutputFormat {
        JSON, CSV, MARKDOWN, HTML
    }

    /**
     * Dependency configuration wrapper.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DependencyConfiguration {
        @JsonProperty("enabled")
        private boolean enabled;

        @JsonProperty("timeout")
        private Duration timeout;

        @JsonProperty("connection_pool_size")
        private int connectionPoolSize;

        @JsonProperty("retry_attempts")
        private int retryAttempts;

        @JsonProperty("circuit_breaker")
        private boolean circuitBreaker;

        public DependencyConfiguration(boolean enabled, Duration timeout, int connectionPoolSize, int retryAttempts, boolean circuitBreaker) {
            this.enabled = enabled;
            this.timeout = timeout;
            this.connectionPoolSize = connectionPoolSize;
            this.retryAttempts = retryAttempts;
            this.circuitBreaker = circuitBreaker;
        }

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public int getConnectionPoolSize() { return connectionPoolSize; }
        public void setConnectionPoolSize(int connectionPoolSize) { this.connectionPoolSize = connectionPoolSize; }
        public int getRetryAttempts() { return retryAttempts; }
        public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }
        public boolean isCircuitBreaker() { return circuitBreaker; }
        public void setCircuitBreaker(boolean circuitBreaker) { this.circuitBreaker = circuitBreaker; }
    }

    // Builder pattern for configuration
    public static class Builder {
        private String benchmarkName;
        private String benchmarkDescription;
        private EnvironmentType environment = EnvironmentType.LOCAL;
        private int parallelThreads = Runtime.getRuntime().availableProcessors();
        private int warmupIterations = 3;
        private int measurementIterations = 10;
        private Duration timeoutDuration = Duration.ofMinutes(30);
        private long memoryThresholdMb = 1024;
        private double cpuThresholdPercent = 80.0;
        private double throughputTargetPerSec = 1000.0;
        private double errorRateThresholdPercent = 5.0;
        private int retriesOnFailure = 3;
        private boolean circuitBreakerEnabled = true;
        private double circuitBreakerFailureRateThreshold = 50.0;
        private Duration circuitBreakerRecoveryTimeout = Duration.ofMinutes(5);
        private boolean telemetryEnabled = true;
        private OutputFormat outputFormat = OutputFormat.JSON;
        private final Map<String, Object> customParameters = new HashMap<>();
        private final Map<String, DependencyConfiguration> dependencyConfigurations = new HashMap<>();

        public Builder(String benchmarkName, String benchmarkDescription) {
            this.benchmarkName = benchmarkName;
            this.benchmarkDescription = benchmarkDescription;
        }

        public Builder environment(EnvironmentType environment) {
            this.environment = environment;
            return this;
        }

        public Builder parallelThreads(int threads) {
            this.parallelThreads = threads;
            return this;
        }

        public Builder warmupIterations(int iterations) {
            this.warmupIterations = iterations;
            return this;
        }

        public Builder measurementIterations(int iterations) {
            this.measurementIterations = iterations;
            return this;
        }

        public Builder timeoutDuration(Duration timeout) {
            this.timeoutDuration = timeout;
            return this;
        }

        public Builder memoryThresholdMb(long threshold) {
            this.memoryThresholdMb = threshold;
            return this;
        }

        public Builder cpuThresholdPercent(double threshold) {
            this.cpuThresholdPercent = threshold;
            return this;
        }

        public Builder throughputTargetPerSec(double target) {
            this.throughputTargetPerSec = target;
            return this;
        }

        public Builder errorRateThresholdPercent(double threshold) {
            this.errorRateThresholdPercent = threshold;
            return this;
        }

        public Builder retriesOnFailure(int retries) {
            this.retriesOnFailure = retries;
            return this;
        }

        public Builder circuitBreakerEnabled(boolean enabled) {
            this.circuitBreakerEnabled = enabled;
            return this;
        }

        public Builder circuitBreakerFailureRateThreshold(double threshold) {
            this.circuitBreakerFailureRateThreshold = threshold;
            return this;
        }

        public Builder circuitBreakerRecoveryTimeout(Duration timeout) {
            this.circuitBreakerRecoveryTimeout = timeout;
            return this;
        }

        public Builder telemetryEnabled(boolean enabled) {
            this.telemetryEnabled = enabled;
            return this;
        }

        public Builder outputFormat(OutputFormat format) {
            this.outputFormat = format;
            return this;
        }

        public Builder addCustomParameter(String key, Object value) {
            this.customParameters.put(key, value);
            return this;
        }

        public Builder addDependencyConfiguration(String name, boolean enabled, Duration timeout, int poolSize, int retries, boolean circuitBreaker) {
            this.dependencyConfigurations.put(name, new DependencyConfiguration(enabled, timeout, poolSize, retries, circuitBreaker));
            return this;
        }

        public BenchmarkConfiguration build() {
            BenchmarkConfiguration config = new BenchmarkConfiguration();
            config.benchmarkName = this.benchmarkName;
            config.benchmarkDescription = this.benchmarkDescription;
            config.environment = this.environment;
            config.parallelThreads = this.parallelThreads;
            config.warmupIterations = this.warmupIterations;
            config.measurementIterations = this.measurementIterations;
            config.timeoutDuration = this.timeoutDuration;
            config.memoryThresholdMb = this.memoryThresholdMb;
            config.cpuThresholdPercent = this.cpuThresholdPercent;
            config.throughputTargetPerSec = this.throughputTargetPerSec;
            config.errorRateThresholdPercent = this.errorRateThresholdPercent;
            config.retriesOnFailure = this.retriesOnFailure;
            config.circuitBreakerEnabled = this.circuitBreakerEnabled;
            config.circuitBreakerFailureRateThreshold = this.circuitBreakerFailureRateThreshold;
            config.circuitBreakerRecoveryTimeout = this.circuitBreakerRecoveryTimeout;
            config.telemetryEnabled = this.telemetryEnabled;
            config.outputFormat = this.outputFormat;
            config.customParameters = new HashMap<>(this.customParameters);
            config.dependencyConfigurations = new HashMap<>(this.dependencyConfigurations);
            return config;
        }
    }

    // Static factory methods
    public static Builder builder(String benchmarkName, String benchmarkDescription) {
        return new Builder(benchmarkName, benchmarkDescription);
    }

    // Getters and setters
    public String getBenchmarkName() { return benchmarkName; }
    public String getBenchmarkDescription() { return benchmarkDescription; }
    public EnvironmentType getEnvironment() { return environment; }
    public int getParallelThreads() { return parallelThreads; }
    public int getWarmupIterations() { return warmupIterations; }
    public int getMeasurementIterations() { return measurementIterations; }
    public Duration getTimeoutDuration() { return timeoutDuration; }
    public long getMemoryThresholdMb() { return memoryThresholdMb; }
    public double getCpuThresholdPercent() { return cpuThresholdPercent; }
    public double getThroughputTargetPerSec() { return throughputTargetPerSec; }
    public double getErrorRateThresholdPercent() { return errorRateThresholdPercent; }
    public int getRetriesOnFailure() { return retriesOnFailure; }
    public boolean isCircuitBreakerEnabled() { return circuitBreakerEnabled; }
    public double getCircuitBreakerFailureRateThreshold() { return circuitBreakerFailureRateThreshold; }
    public Duration getCircuitBreakerRecoveryTimeout() { return circuitBreakerRecoveryTimeout; }
    public boolean isTelemetryEnabled() { return telemetryEnabled; }
    public OutputFormat getOutputFormat() { return outputFormat; }
    public Map<String, Object> getCustomParameters() { return customParameters; }
    public Map<String, DependencyConfiguration> getDependencyConfigurations() { return dependencyConfigurations; }

    // Helper methods for configuration validation
    public boolean validate() {
        if (warmupIterations < 1 || measurementIterations < 1) {
            throw new IllegalArgumentException("Warmup and measurement iterations must be at least 1");
        }
        if (parallelThreads <= 0) {
            throw new IllegalArgumentException("Parallel threads must be positive");
        }
        if (memoryThresholdMb <= 0 || cpuThresholdPercent <= 0 || cpuThresholdPercent > 100) {
            throw new IllegalArgumentException("Invalid threshold values");
        }
        if (timeoutDuration == null || timeoutDuration.isNegative()) {
            throw new IllegalArgumentException("Timeout duration must be positive");
        }
        return true;
    }

    // Method to create ExecutorService based on configuration
    public ExecutorService createExecutorService() {
        return ForkJoinPool.commonPool(); // Uses virtual threads in Java 25
    }

    // Method to calculate optimal thread count based on workload
    public int calculateOptimalThreadCount() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        return Math.min(parallelThreads, availableProcessors * 2); // Cap at 2x cores
    }

    // Configuration overrides based on environment
    public void applyEnvironmentOverrides() {
        switch (environment) {
            case CONTAINERIZED:
                memoryThresholdMb = Math.min(memoryThresholdMb, 512); // Reduced memory in containers
                parallelThreads = Math.min(parallelThreads, Runtime.getRuntime().availableProcessors());
                break;
            case KUBERNETES:
                memoryThresholdMb = Math.min(memoryThresholdMb, 256);
                parallelThreads = Math.min(parallelThreads, Runtime.getRuntime().availableProcessors());
                break;
            default:
                break;
        }
    }
}