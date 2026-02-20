package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.prometheus.metrics.core.metrics.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Advanced Prometheus metrics registry with custom histograms and percentile tracking.
 *
 * Registers optimized histograms for YAWL-specific latency patterns:
 * - Circuit breaker state transitions (10ms, 100ms, 1s, 10s, 60s)
 * - Rate limiter permit acquisition (1ms, 5ms, 10ms, 50ms, 100ms)
 * - MCP client calls (100ms, 500ms, 1s, 5s, 30s)
 *
 * Implements singleton pattern with lazy initialization via Micrometer.
 * Thread-safe and suitable for high-throughput production environments.
 */
public final class CustomMetricsRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomMetricsRegistry.class);
    private static CustomMetricsRegistry instance;
    private static final Object LOCK = new Object();

    private final MeterRegistry meterRegistry;

    // Circuit Breaker Metrics
    private final Timer circuitBreakerStateChangeTimer;
    private final Counter circuitBreakerStateTransitionCounter;
    private final AtomicInteger circuitBreakerState = new AtomicInteger(0); // 0=CLOSED, 1=OPEN, 2=HALF_OPEN

    // Rate Limiter Metrics
    private final Timer rateLimiterAcquisitionTimer;
    private final Counter rateLimiterPermitAllowedCounter;
    private final Counter rateLimiterPermitRejectedCounter;
    private final AtomicLong rateLimiterAvailablePermits = new AtomicLong(0);

    // MCP Client Metrics
    private final Timer mcpClientCallLatencyTimer;
    private final Counter mcpClientCallSuccessCounter;
    private final Counter mcpClientCallFailureCounter;
    private final AtomicLong mcpClientActiveConnections = new AtomicLong(0);

    // Queue and Workflow Metrics
    private final AtomicLong queueDepthGauge = new AtomicLong(0);
    private final AtomicLong activeWorkflowsGauge = new AtomicLong(0);
    private final AtomicLong resourceUtilizationPercentGauge = new AtomicLong(0);

    // Distribution Summaries for detailed percentile analysis
    private final DistributionSummary circuitBreakerStateDurationSummary;
    private final DistributionSummary rateLimiterAcquisitionTimeSummary;
    private final DistributionSummary mcpClientLatencySummary;

    /**
     * Private constructor - initializes all custom metrics with optimized histograms.
     */
    private CustomMetricsRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry cannot be null");

        // ======== Circuit Breaker Metrics ========
        // Histogram buckets: 10ms, 100ms, 1s, 10s, 60s (10, 100, 1000, 10000, 60000 ms)
        this.circuitBreakerStateChangeTimer = Timer.builder("yawl.circuit.breaker.state.duration")
                .description("Circuit breaker state change duration")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .serviceLevelObjectives(10, 100, 1000, 10000, 60000)
                .baseUnit("milliseconds")
                .register(meterRegistry);

        this.circuitBreakerStateTransitionCounter = Counter.builder("yawl.circuit.breaker.transitions")
                .description("Total circuit breaker state transitions")
                .register(meterRegistry);

        // ======== Rate Limiter Metrics ========
        // Histogram buckets: 1ms, 5ms, 10ms, 50ms, 100ms
        this.rateLimiterAcquisitionTimer = Timer.builder("yawl.rate.limiter.acquisition.time")
                .description("Rate limiter permit acquisition latency")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .serviceLevelObjectives(1, 5, 10, 50, 100)
                .baseUnit("milliseconds")
                .register(meterRegistry);

        this.rateLimiterPermitAllowedCounter = Counter.builder("yawl.rate.limiter.permits.allowed")
                .description("Rate limiter permits allowed (not rate limited)")
                .register(meterRegistry);

        this.rateLimiterPermitRejectedCounter = Counter.builder("yawl.rate.limiter.permits.rejected")
                .description("Rate limiter permits rejected (rate limited)")
                .register(meterRegistry);

        // ======== MCP Client Metrics ========
        // Histogram buckets: 100ms, 500ms, 1s, 5s, 30s (100, 500, 1000, 5000, 30000 ms)
        this.mcpClientCallLatencyTimer = Timer.builder("yawl.mcp.client.call.latency")
                .description("MCP client call latency with percentile tracking")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .serviceLevelObjectives(100, 500, 1000, 5000, 30000)
                .baseUnit("milliseconds")
                .register(meterRegistry);

        this.mcpClientCallSuccessCounter = Counter.builder("yawl.mcp.client.calls.success")
                .description("Successful MCP client calls")
                .register(meterRegistry);

        this.mcpClientCallFailureCounter = Counter.builder("yawl.mcp.client.calls.failure")
                .description("Failed MCP client calls")
                .register(meterRegistry);

        // ======== Gauges for Queue and Workflow Metrics ========
        Gauge.builder("yawl.queue.depth", queueDepthGauge::get)
                .description("Current queue depth")
                .baseUnit("tasks")
                .register(meterRegistry);

        Gauge.builder("yawl.workflows.active", activeWorkflowsGauge::get)
                .description("Number of active workflows")
                .baseUnit("workflows")
                .register(meterRegistry);

        Gauge.builder("yawl.resource.utilization.percent", resourceUtilizationPercentGauge::get)
                .description("Current resource utilization percentage")
                .baseUnit("percent")
                .register(meterRegistry);

        Gauge.builder("yawl.circuit.breaker.state", circuitBreakerState::get)
                .description("Circuit breaker current state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)")
                .register(meterRegistry);

        Gauge.builder("yawl.rate.limiter.permits.available", rateLimiterAvailablePermits::get)
                .description("Available rate limiter permits")
                .register(meterRegistry);

        Gauge.builder("yawl.mcp.client.connections.active", mcpClientActiveConnections::get)
                .description("Active MCP client connections")
                .register(meterRegistry);

        // ======== Distribution Summaries for detailed percentile analysis ========
        this.circuitBreakerStateDurationSummary = DistributionSummary.builder(
                "yawl.circuit.breaker.state.duration.summary")
                .description("Circuit breaker state duration summary (for percentile analysis)")
                .baseUnit("milliseconds")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);

        this.rateLimiterAcquisitionTimeSummary = DistributionSummary.builder(
                "yawl.rate.limiter.acquisition.time.summary")
                .description("Rate limiter acquisition time summary (for percentile analysis)")
                .baseUnit("milliseconds")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);

        this.mcpClientLatencySummary = DistributionSummary.builder(
                "yawl.mcp.client.latency.summary")
                .description("MCP client latency summary (for percentile analysis)")
                .baseUnit("milliseconds")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);

        LOGGER.info("CustomMetricsRegistry initialized with advanced Prometheus metrics");
    }

    /**
     * Initializes the singleton metrics registry.
     * Thread-safe and idempotent.
     */
    public static void initialize(MeterRegistry meterRegistry) {
        synchronized (LOCK) {
            if (instance == null) {
                instance = new CustomMetricsRegistry(meterRegistry);
            }
        }
    }

    /**
     * Gets the singleton metrics registry instance.
     * Throws IllegalStateException if not initialized.
     */
    public static CustomMetricsRegistry getInstance() {
        synchronized (LOCK) {
            if (instance == null) {
                throw new IllegalStateException(
                    "CustomMetricsRegistry not initialized. Call initialize(MeterRegistry) first.");
            }
            return instance;
        }
    }

    // ======== Circuit Breaker Methods ========

    /**
     * Records a circuit breaker state transition.
     */
    public void recordCircuitBreakerStateChange(long durationMs) {
        circuitBreakerStateChangeTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        circuitBreakerStateDurationSummary.record(durationMs);
        circuitBreakerStateTransitionCounter.increment();
    }

    /**
     * Sets the current circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN).
     */
    public void setCircuitBreakerState(int state) {
        if (state < 0 || state > 2) {
            throw new IllegalArgumentException("Circuit breaker state must be 0 (CLOSED), 1 (OPEN), or 2 (HALF_OPEN)");
        }
        circuitBreakerState.set(state);
    }

    /**
     * Gets the current circuit breaker state.
     */
    public int getCircuitBreakerState() {
        return circuitBreakerState.get();
    }

    // ======== Rate Limiter Methods ========

    /**
     * Records permit acquisition time in milliseconds.
     */
    public void recordRateLimiterAcquisition(long durationMs) {
        rateLimiterAcquisitionTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        rateLimiterAcquisitionTimeSummary.record(durationMs);
    }

    /**
     * Increments the count of allowed (non-rate-limited) permits.
     */
    public void incrementRateLimiterPermitAllowed() {
        rateLimiterPermitAllowedCounter.increment();
    }

    /**
     * Increments the count of rejected (rate-limited) permits.
     */
    public void incrementRateLimiterPermitRejected() {
        rateLimiterPermitRejectedCounter.increment();
    }

    /**
     * Sets the number of available rate limiter permits.
     */
    public void setRateLimiterAvailablePermits(long permits) {
        rateLimiterAvailablePermits.set(Math.max(0, permits));
    }

    /**
     * Gets the number of available rate limiter permits.
     */
    public long getRateLimiterAvailablePermits() {
        return rateLimiterAvailablePermits.get();
    }

    // ======== MCP Client Methods ========

    /**
     * Records MCP client call latency in milliseconds.
     */
    public void recordMcpClientCallLatency(long durationMs) {
        mcpClientCallLatencyTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        mcpClientLatencySummary.record(durationMs);
    }

    /**
     * Increments the count of successful MCP client calls.
     */
    public void incrementMcpClientCallSuccess() {
        mcpClientCallSuccessCounter.increment();
    }

    /**
     * Increments the count of failed MCP client calls.
     */
    public void incrementMcpClientCallFailure() {
        mcpClientCallFailureCounter.increment();
    }

    /**
     * Sets the number of active MCP client connections.
     */
    public void setMcpClientActiveConnections(long connections) {
        mcpClientActiveConnections.set(Math.max(0, connections));
    }

    /**
     * Gets the number of active MCP client connections.
     */
    public long getMcpClientActiveConnections() {
        return mcpClientActiveConnections.get();
    }

    // ======== Queue and Workflow Metrics ========

    /**
     * Sets the current queue depth.
     */
    public void setQueueDepth(long depth) {
        queueDepthGauge.set(Math.max(0, depth));
    }

    /**
     * Gets the current queue depth.
     */
    public long getQueueDepth() {
        return queueDepthGauge.get();
    }

    /**
     * Sets the number of active workflows.
     */
    public void setActiveWorkflows(long count) {
        activeWorkflowsGauge.set(Math.max(0, count));
    }

    /**
     * Gets the number of active workflows.
     */
    public long getActiveWorkflows() {
        return activeWorkflowsGauge.get();
    }

    /**
     * Sets the resource utilization percentage (0-100).
     */
    public void setResourceUtilizationPercent(long percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Resource utilization must be between 0 and 100 percent");
        }
        resourceUtilizationPercentGauge.set(percent);
    }

    /**
     * Gets the resource utilization percentage.
     */
    public long getResourceUtilizationPercent() {
        return resourceUtilizationPercentGauge.get();
    }

    /**
     * Gets the underlying Micrometer registry for custom metric registration.
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}
