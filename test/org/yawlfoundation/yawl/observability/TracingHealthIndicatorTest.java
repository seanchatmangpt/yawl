package org.yawlfoundation.yawl.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.autoconfigure.tracing.TracingProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TracingHealthIndicator.
 * Verifies tracing health check reporting.
 *
 * @since 6.0.0
 */
@DisplayName("TracingHealthIndicator Tests")
@ExtendWith({SpringExtension.class, MockitoExtension.class})
class TracingHealthIndicatorTest {

    private TracingHealthIndicator indicator;

    @Mock
    private TracingProperties tracingProperties;

    @Mock
    private CurrentTraceContext currentTraceContext;

    @BeforeEach
    void setUp() {
        indicator = new TracingHealthIndicator(tracingProperties, currentTraceContext);
    }

    @Test
    @DisplayName("Should report UP when tracing is enabled")
    void testHealthUpWhenTracingEnabled() {
        when(tracingProperties.getSampling().getProbability()).thenReturn(0.1);
        when(tracingProperties.isSpanInLogs()).thenReturn(true);
        when(currentTraceContext.context()).thenReturn(null);

        Health health = indicator.health();

        assertNotNull(health, "Health should not be null");
        assertEquals("UP", health.getStatus().getCode(), "Status should be UP");
    }

    @Test
    @DisplayName("Should report DEGRADED when tracing is disabled")
    void testHealthDegradedWhenTracingDisabled() {
        when(tracingProperties.getSampling().getProbability()).thenReturn(0.0);
        when(tracingProperties.isSpanInLogs()).thenReturn(false);

        Health health = indicator.health();

        assertEquals("DEGRADED", health.getStatus().getCode(), "Status should be DEGRADED when sampling is 0");
    }

    @Test
    @DisplayName("Should include sampling probability in details")
    void testSamplingProbabilityDetail() {
        when(tracingProperties.getSampling().getProbability()).thenReturn(0.5);
        when(tracingProperties.isSpanInLogs()).thenReturn(true);

        Health health = indicator.health();

        assertTrue(health.getDetails().containsKey("sampling_probability"), "Should include sampling_probability");
        assertEquals(0.5, health.getDetails().get("sampling_probability"), "Should report correct sampling probability");
    }

    @Test
    @DisplayName("Should include span in logs configuration")
    void testSpanInLogsDetail() {
        when(tracingProperties.getSampling().getProbability()).thenReturn(0.1);
        when(tracingProperties.isSpanInLogs()).thenReturn(true);

        Health health = indicator.health();

        assertTrue(health.getDetails().containsKey("span_in_logs"), "Should include span_in_logs");
        assertEquals(true, health.getDetails().get("span_in_logs"), "Should report correct span_in_logs setting");
    }

    @Test
    @DisplayName("Should indicate active trace context availability")
    void testActiveTraceContextDetail() {
        when(tracingProperties.getSampling().getProbability()).thenReturn(0.1);
        when(tracingProperties.isSpanInLogs()).thenReturn(true);
        when(currentTraceContext.context()).thenReturn(null);

        Health health = indicator.health();

        assertTrue(health.getDetails().containsKey("active_trace_context"), "Should include active_trace_context");
        assertEquals(false, health.getDetails().get("active_trace_context"), "Should report no active trace context");
    }

    @Test
    @DisplayName("Should report OpenTelemetry SDK status")
    void testOpenTelemetrySDKStatus() {
        when(tracingProperties.getSampling().getProbability()).thenReturn(0.1);
        when(tracingProperties.isSpanInLogs()).thenReturn(true);

        Health health = indicator.health();

        assertTrue(health.getDetails().containsKey("opentelemetry_sdk_initialized"),
                "Should include opentelemetry_sdk_initialized");
    }

    @Test
    @DisplayName("Should handle exceptions gracefully")
    void testHealthCheckExceptionHandling() {
        // Simulate exception by using invalid configuration
        when(tracingProperties.getSampling()).thenThrow(new RuntimeException("Configuration error"));

        Health health = indicator.health();

        assertEquals("DOWN", health.getStatus().getCode(), "Status should be DOWN on exception");
        assertTrue(health.getDetails().containsKey("error"), "Should include error message");
    }

    @Test
    @DisplayName("Should provide comprehensive details")
    void testComprehensiveHealthDetails() {
        when(tracingProperties.getSampling().getProbability()).thenReturn(0.25);
        when(tracingProperties.isSpanInLogs()).thenReturn(true);

        Health health = indicator.health();

        assertNotNull(health.getDetails(), "Details should not be null");
        assertTrue(health.getDetails().size() > 3, "Should include multiple detail entries");
        assertTrue(health.getDetails().containsKey("tracing_enabled"), "Should include tracing_enabled");
        assertTrue(health.getDetails().containsKey("sampling_probability"), "Should include sampling_probability");
        assertTrue(health.getDetails().containsKey("span_in_logs"), "Should include span_in_logs");
    }

    @Test
    @DisplayName("Should handle very low sampling probability")
    void testVeryLowSamplingProbability() {
        when(tracingProperties.getSampling().getProbability()).thenReturn(0.001);
        when(tracingProperties.isSpanInLogs()).thenReturn(false);

        Health health = indicator.health();

        assertEquals("UP", health.getStatus().getCode(), "Status should still be UP");
        assertEquals(0.001, health.getDetails().get("sampling_probability"), "Should report correct low probability");
    }

    @Test
    @DisplayName("Should handle 100% sampling probability")
    void testFullSamplingProbability() {
        when(tracingProperties.getSampling().getProbability()).thenReturn(1.0);
        when(tracingProperties.isSpanInLogs()).thenReturn(true);

        Health health = indicator.health();

        assertEquals("UP", health.getStatus().getCode(), "Status should be UP");
        assertEquals(1.0, health.getDetails().get("sampling_probability"), "Should report full sampling");
    }
}
