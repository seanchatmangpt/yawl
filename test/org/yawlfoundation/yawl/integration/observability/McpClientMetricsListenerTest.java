package org.yawlfoundation.yawl.integration.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.yawlfoundation.yawl.observability.CustomMetricsRegistry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for McpClientMetricsListener integration.
 */
@DisplayName("MCP Client Metrics Listener Tests")
public class McpClientMetricsListenerTest {

    private MeterRegistry meterRegistry;
    private CustomMetricsRegistry customMetrics;
    private McpClientMetricsListener listener;

    @BeforeEach
    public void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        CustomMetricsRegistry.initialize(meterRegistry);
        customMetrics = CustomMetricsRegistry.getInstance();
        listener = new McpClientMetricsListener("test-mcp-client");
    }

    @Test
    @DisplayName("Listener should record call latency with start/end pattern")
    public void testCallLatencyWithStartEnd() {
        long startTime = listener.startCall();
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        listener.recordCallSuccess(startTime);

        Timer timer = meterRegistry.find("yawl.mcp.client.call.latency").timer();
        assertNotNull(timer, "Call latency timer should be registered");
        assertEquals(1, timer.count(), "Should record one call");
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) >= 50,
                "Total time should be at least 50ms");
    }

    @Test
    @DisplayName("Listener should record call success")
    public void testCallSuccessWithLatency() {
        listener.recordCallSuccessWithLatency(250);

        Counter successCounter = meterRegistry.find("yawl.mcp.client.calls.success").counter();
        assertNotNull(successCounter, "Success counter should be registered");
        assertEquals(1.0, successCounter.count(), "Should count 1 success");
    }

    @Test
    @DisplayName("Listener should record call failure")
    public void testCallFailureWithLatency() {
        listener.recordCallFailureWithLatency(500);

        Counter failureCounter = meterRegistry.find("yawl.mcp.client.calls.failure").counter();
        assertNotNull(failureCounter, "Failure counter should be registered");
        assertEquals(1.0, failureCounter.count(), "Should count 1 failure");
    }

    @Test
    @DisplayName("Listener should track active connections")
    public void testActiveConnections() {
        listener.setActiveConnections(3);
        assertEquals(3, customMetrics.getMcpClientActiveConnections());

        listener.incrementActiveConnections();
        assertEquals(4, customMetrics.getMcpClientActiveConnections());

        listener.decrementActiveConnections();
        assertEquals(3, customMetrics.getMcpClientActiveConnections());
    }

    @Test
    @DisplayName("Listener should not decrement below zero")
    public void testConnectionsNeverNegative() {
        listener.setActiveConnections(1);
        listener.decrementActiveConnections();
        listener.decrementActiveConnections(); // This should not go below 0

        assertEquals(0, customMetrics.getMcpClientActiveConnections(),
                "Connections should never be negative");
    }

    @Test
    @DisplayName("Listener should track mixed success and failure")
    public void testMixedSuccessFailure() {
        listener.recordCallSuccessWithLatency(100);
        listener.recordCallFailureWithLatency(200);
        listener.recordCallSuccessWithLatency(150);
        listener.recordCallFailureWithLatency(250);

        Counter successCounter = meterRegistry.find("yawl.mcp.client.calls.success").counter();
        Counter failureCounter = meterRegistry.find("yawl.mcp.client.calls.failure").counter();

        assertEquals(2.0, successCounter.count(), "Should count 2 successes");
        assertEquals(2.0, failureCounter.count(), "Should count 2 failures");
    }

    @Test
    @DisplayName("Listener should provide client name")
    public void testClientName() {
        assertEquals("test-mcp-client", listener.getClientName());
    }

    @Test
    @DisplayName("Multiple listeners should coexist independently")
    public void testMultipleListeners() {
        McpClientMetricsListener listener2 = new McpClientMetricsListener("mcp-agents");
        McpClientMetricsListener listener3 = new McpClientMetricsListener("mcp-tools");

        listener.recordCallSuccessWithLatency(100);
        listener2.recordCallFailureWithLatency(200);
        listener3.recordCallSuccessWithLatency(150);

        Counter successCounter = meterRegistry.find("yawl.mcp.client.calls.success").counter();
        Counter failureCounter = meterRegistry.find("yawl.mcp.client.calls.failure").counter();

        assertEquals(2.0, successCounter.count(), "Should count 2 total successes");
        assertEquals(1.0, failureCounter.count(), "Should count 1 total failure");
    }
}
