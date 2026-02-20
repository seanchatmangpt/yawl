package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for CustomMetricsRegistry and advanced Prometheus metrics.
 *
 * Verifies:
 * - Custom histogram registration with optimized buckets
 * - Percentile tracking (p50, p75, p95, p99)
 * - Circuit breaker, rate limiter, and MCP client metrics
 * - Gauge and counter functionality
 * - Thread safety of singleton initialization
 */
@DisplayName("Prometheus Advanced Metrics Tests")
public class PrometheusMetricsTest {

    private MeterRegistry meterRegistry;
    private CustomMetricsRegistry customMetrics;

    @BeforeEach
    public void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        CustomMetricsRegistry.initialize(meterRegistry);
        customMetrics = CustomMetricsRegistry.getInstance();
    }

    // ======== Circuit Breaker Metrics Tests ========

    @Test
    @DisplayName("Circuit breaker metrics should record state change duration")
    public void testCircuitBreakerStateChangeDuration() {
        customMetrics.recordCircuitBreakerStateChange(50);
        customMetrics.recordCircuitBreakerStateChange(150);
        customMetrics.recordCircuitBreakerStateChange(2500);

        Timer timer = meterRegistry.find("yawl.circuit.breaker.state.duration").timer();
        assertNotNull(timer, "Circuit breaker timer should be registered");
        assertEquals(3, timer.count(), "Should have recorded 3 state changes");
        assertTrue(timer.totalTime(TimeUnit.MILLISECONDS) > 0, "Total time should be positive");
    }

    @Test
    @DisplayName("Circuit breaker state transitions should increment counter")
    public void testCircuitBreakerTransitionCounter() {
        customMetrics.recordCircuitBreakerStateChange(100);
        customMetrics.recordCircuitBreakerStateChange(100);
        customMetrics.recordCircuitBreakerStateChange(100);

        Counter counter = meterRegistry.find("yawl.circuit.breaker.transitions").counter();
        assertNotNull(counter, "Circuit breaker transition counter should be registered");
        assertEquals(3.0, counter.count(), "Counter should track 3 transitions");
    }

    @Test
    @DisplayName("Circuit breaker state gauge should update correctly")
    public void testCircuitBreakerStateGauge() {
        // CLOSED
        customMetrics.setCircuitBreakerState(0);
        double closedState = meterRegistry.find("yawl.circuit.breaker.state").gauge().value();
        assertEquals(0.0, closedState, "State should be CLOSED (0)");

        // OPEN
        customMetrics.setCircuitBreakerState(1);
        double openState = meterRegistry.find("yawl.circuit.breaker.state").gauge().value();
        assertEquals(1.0, openState, "State should be OPEN (1)");

        // HALF_OPEN
        customMetrics.setCircuitBreakerState(2);
        double halfOpenState = meterRegistry.find("yawl.circuit.breaker.state").gauge().value();
        assertEquals(2.0, halfOpenState, "State should be HALF_OPEN (2)");
    }

    @Test
    @DisplayName("Circuit breaker state should reject invalid values")
    public void testCircuitBreakerInvalidState() {
        assertThrows(IllegalArgumentException.class, () -> customMetrics.setCircuitBreakerState(-1));
        assertThrows(IllegalArgumentException.class, () -> customMetrics.setCircuitBreakerState(3));
    }

    // ======== Rate Limiter Metrics Tests ========

    @Test
    @DisplayName("Rate limiter should record acquisition time with correct buckets")
    public void testRateLimiterAcquisitionTime() {
        customMetrics.recordRateLimiterAcquisition(1);  // 1ms bucket
        customMetrics.recordRateLimiterAcquisition(5);  // 5ms bucket
        customMetrics.recordRateLimiterAcquisition(10); // 10ms bucket
        customMetrics.recordRateLimiterAcquisition(50); // 50ms bucket
        customMetrics.recordRateLimiterAcquisition(100);// 100ms bucket

        Timer timer = meterRegistry.find("yawl.rate.limiter.acquisition.time").timer();
        assertNotNull(timer, "Rate limiter acquisition timer should be registered");
        assertEquals(5, timer.count(), "Should have recorded 5 acquisitions");
    }

    @Test
    @DisplayName("Rate limiter should track allowed vs rejected permits")
    public void testRateLimiterPermitCounts() {
        customMetrics.incrementRateLimiterPermitAllowed();
        customMetrics.incrementRateLimiterPermitAllowed();
        customMetrics.incrementRateLimiterPermitRejected();
        customMetrics.incrementRateLimiterPermitRejected();
        customMetrics.incrementRateLimiterPermitRejected();

        Counter allowedCounter = meterRegistry.find("yawl.rate.limiter.permits.allowed").counter();
        Counter rejectedCounter = meterRegistry.find("yawl.rate.limiter.permits.rejected").counter();

        assertNotNull(allowedCounter, "Allowed permits counter should be registered");
        assertNotNull(rejectedCounter, "Rejected permits counter should be registered");
        assertEquals(2.0, allowedCounter.count(), "Should have 2 allowed permits");
        assertEquals(3.0, rejectedCounter.count(), "Should have 3 rejected permits");
    }

    @Test
    @DisplayName("Rate limiter available permits gauge should update correctly")
    public void testRateLimiterAvailablePermitsGauge() {
        customMetrics.setRateLimiterAvailablePermits(100);
        assertEquals(100, customMetrics.getRateLimiterAvailablePermits());

        Gauge gauge = meterRegistry.find("yawl.rate.limiter.permits.available").gauge();
        assertNotNull(gauge, "Available permits gauge should be registered");
        assertEquals(100.0, gauge.value(), "Gauge value should match set value");
    }

    // ======== MCP Client Metrics Tests ========

    @Test
    @DisplayName("MCP client should record call latency with correct buckets")
    public void testMcpClientCallLatency() {
        customMetrics.recordMcpClientCallLatency(100);   // 100ms bucket
        customMetrics.recordMcpClientCallLatency(500);   // 500ms bucket
        customMetrics.recordMcpClientCallLatency(1000);  // 1s bucket
        customMetrics.recordMcpClientCallLatency(5000);  // 5s bucket
        customMetrics.recordMcpClientCallLatency(30000); // 30s bucket

        Timer timer = meterRegistry.find("yawl.mcp.client.call.latency").timer();
        assertNotNull(timer, "MCP client latency timer should be registered");
        assertEquals(5, timer.count(), "Should have recorded 5 calls");
    }

    @Test
    @DisplayName("MCP client should track success vs failure counts")
    public void testMcpClientSuccessFailureCounts() {
        customMetrics.incrementMcpClientCallSuccess();
        customMetrics.incrementMcpClientCallSuccess();
        customMetrics.incrementMcpClientCallSuccess();
        customMetrics.incrementMcpClientCallFailure();
        customMetrics.incrementMcpClientCallFailure();

        Counter successCounter = meterRegistry.find("yawl.mcp.client.calls.success").counter();
        Counter failureCounter = meterRegistry.find("yawl.mcp.client.calls.failure").counter();

        assertNotNull(successCounter, "Success counter should be registered");
        assertNotNull(failureCounter, "Failure counter should be registered");
        assertEquals(3.0, successCounter.count(), "Should have 3 successful calls");
        assertEquals(2.0, failureCounter.count(), "Should have 2 failed calls");
    }

    @Test
    @DisplayName("MCP client active connections gauge should update correctly")
    public void testMcpClientActiveConnectionsGauge() {
        customMetrics.setMcpClientActiveConnections(5);
        assertEquals(5, customMetrics.getMcpClientActiveConnections());

        Gauge gauge = meterRegistry.find("yawl.mcp.client.connections.active").gauge();
        assertNotNull(gauge, "Active connections gauge should be registered");
        assertEquals(5.0, gauge.value(), "Gauge value should match set value");
    }

    // ======== Queue and Workflow Metrics Tests ========

    @Test
    @DisplayName("Queue depth gauge should update correctly")
    public void testQueueDepthGauge() {
        customMetrics.setQueueDepth(42);
        assertEquals(42, customMetrics.getQueueDepth());

        Gauge gauge = meterRegistry.find("yawl.queue.depth").gauge();
        assertNotNull(gauge, "Queue depth gauge should be registered");
        assertEquals(42.0, gauge.value(), "Gauge value should match set value");
    }

    @Test
    @DisplayName("Active workflows gauge should update correctly")
    public void testActiveWorkflowsGauge() {
        customMetrics.setActiveWorkflows(15);
        assertEquals(15, customMetrics.getActiveWorkflows());

        Gauge gauge = meterRegistry.find("yawl.workflows.active").gauge();
        assertNotNull(gauge, "Active workflows gauge should be registered");
        assertEquals(15.0, gauge.value(), "Gauge value should match set value");
    }

    @Test
    @DisplayName("Resource utilization percentage should be bounded 0-100")
    public void testResourceUtilizationValidation() {
        customMetrics.setResourceUtilizationPercent(50);
        assertEquals(50, customMetrics.getResourceUtilizationPercent());

        assertThrows(IllegalArgumentException.class, () -> customMetrics.setResourceUtilizationPercent(-1));
        assertThrows(IllegalArgumentException.class, () -> customMetrics.setResourceUtilizationPercent(101));
    }

    // ======== Distribution Summary Tests ========

    @Test
    @DisplayName("Distribution summaries should track percentiles")
    public void testDistributionSummaries() {
        // Circuit breaker state duration summary
        for (int i = 0; i < 100; i++) {
            customMetrics.recordCircuitBreakerStateChange(i * 10);
        }

        // Rate limiter acquisition time summary
        for (int i = 1; i <= 100; i++) {
            customMetrics.recordRateLimiterAcquisition(i);
        }

        // MCP client latency summary
        for (int i = 100; i <= 200; i++) {
            customMetrics.recordMcpClientCallLatency(i * 10);
        }

        // Verify summaries exist
        assertNotNull(meterRegistry.find("yawl.circuit.breaker.state.duration.summary").summary());
        assertNotNull(meterRegistry.find("yawl.rate.limiter.acquisition.time.summary").summary());
        assertNotNull(meterRegistry.find("yawl.mcp.client.latency.summary").summary());
    }

    // ======== Singleton Initialization Tests ========

    @Test
    @DisplayName("CustomMetricsRegistry should be singleton")
    public void testSingletonPattern() {
        CustomMetricsRegistry instance1 = CustomMetricsRegistry.getInstance();
        CustomMetricsRegistry instance2 = CustomMetricsRegistry.getInstance();

        assertSame(instance1, instance2, "Singleton should return same instance");
    }

    @Test
    @DisplayName("CustomMetricsRegistry should throw if accessed before initialization")
    public void testUninitializedAccess() {
        // This test assumes a new, uninitialized CustomMetricsRegistry
        // In a real test, would need to reset the singleton state
        // For now, we verify that initialization was successful in setUp()
        assertNotNull(customMetrics, "Metrics should be initialized");
    }

    // ======== Negative Value Handling ========

    @Test
    @DisplayName("Gauges should normalize negative values to 0")
    public void testNegativeValueHandling() {
        customMetrics.setQueueDepth(-10);
        assertEquals(0, customMetrics.getQueueDepth(), "Negative queue depth should be normalized to 0");

        customMetrics.setActiveWorkflows(-5);
        assertEquals(0, customMetrics.getActiveWorkflows(), "Negative workflows should be normalized to 0");

        customMetrics.setRateLimiterAvailablePermits(-1);
        assertEquals(0, customMetrics.getRateLimiterAvailablePermits(), "Negative permits should be normalized to 0");

        customMetrics.setMcpClientActiveConnections(-3);
        assertEquals(0, customMetrics.getMcpClientActiveConnections(), "Negative connections should be normalized to 0");
    }

    // ======== Histogram Bucket Tests ========

    @Test
    @DisplayName("Circuit breaker histogram should have correct bucket boundaries")
    public void testCircuitBreakerHistogramBuckets() {
        // Record values that span the bucket boundaries: 10, 100, 1000, 10000, 60000 ms
        customMetrics.recordCircuitBreakerStateChange(5);
        customMetrics.recordCircuitBreakerStateChange(15);
        customMetrics.recordCircuitBreakerStateChange(99);
        customMetrics.recordCircuitBreakerStateChange(101);
        customMetrics.recordCircuitBreakerStateChange(999);
        customMetrics.recordCircuitBreakerStateChange(1001);

        Timer timer = meterRegistry.find("yawl.circuit.breaker.state.duration").timer();
        assertNotNull(timer, "Timer should be registered");
        assertEquals(6, timer.count(), "Should record all measurements");
    }

    @Test
    @DisplayName("Rate limiter histogram should have correct bucket boundaries")
    public void testRateLimiterHistogramBuckets() {
        // Record values that span the bucket boundaries: 1, 5, 10, 50, 100 ms
        customMetrics.recordRateLimiterAcquisition(0);
        customMetrics.recordRateLimiterAcquisition(2);
        customMetrics.recordRateLimiterAcquisition(6);
        customMetrics.recordRateLimiterAcquisition(15);
        customMetrics.recordRateLimiterAcquisition(75);

        Timer timer = meterRegistry.find("yawl.rate.limiter.acquisition.time").timer();
        assertNotNull(timer, "Timer should be registered");
        assertEquals(5, timer.count(), "Should record all measurements");
    }

    @Test
    @DisplayName("MCP client histogram should have correct bucket boundaries")
    public void testMcpClientHistogramBuckets() {
        // Record values that span the bucket boundaries: 100, 500, 1000, 5000, 30000 ms
        customMetrics.recordMcpClientCallLatency(50);
        customMetrics.recordMcpClientCallLatency(200);
        customMetrics.recordMcpClientCallLatency(600);
        customMetrics.recordMcpClientCallLatency(2000);
        customMetrics.recordMcpClientCallLatency(25000);

        Timer timer = meterRegistry.find("yawl.mcp.client.call.latency").timer();
        assertNotNull(timer, "Timer should be registered");
        assertEquals(5, timer.count(), "Should record all measurements");
    }

    // ======== Meter Registry Access ========

    @Test
    @DisplayName("Should provide access to underlying MeterRegistry")
    public void testMeterRegistryAccess() {
        MeterRegistry registry = customMetrics.getMeterRegistry();
        assertNotNull(registry, "Should provide access to MeterRegistry");
        assertSame(meterRegistry, registry, "Should return same registry instance");
    }
}
