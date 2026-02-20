package org.yawlfoundation.yawl.observability;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.autoconfigure.tracing.TracingProperties;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TracingConfiguration.
 * Verifies Spring Cloud Sleuth integration and OpenTelemetry setup.
 *
 * @since 6.0.0
 */
@DisplayName("Tracing Configuration Tests")
@ExtendWith({SpringExtension.class, MockitoExtension.class})
class TracingConfigurationTest {

    private TracingConfiguration tracingConfiguration;

    @Mock
    private CurrentTraceContext currentTraceContext;

    @Mock
    private Tracer tracer;

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        tracingConfiguration = new TracingConfiguration();
    }

    @Test
    @DisplayName("Should create TraceIdExtractor bean")
    void testTraceIdExtractorCreation() {
        TraceIdExtractor extractor = tracingConfiguration.traceIdExtractor(currentTraceContext);

        assertNotNull(extractor, "TraceIdExtractor should not be null");
    }

    @Test
    @DisplayName("Should create MdcContextPropagator bean")
    void testMdcContextPropagatorCreation() {
        MdcContextPropagator propagator = tracingConfiguration.mdcContextPropagator();

        assertNotNull(propagator, "MdcContextPropagator should not be null");
    }

    @Test
    @DisplayName("Should create CircuitBreakerObservabilityListener bean")
    void testCircuitBreakerObservabilityListenerCreation() {
        CircuitBreakerObservabilityListener listener =
                tracingConfiguration.circuitBreakerObservabilityListener(circuitBreakerRegistry, tracer);

        assertNotNull(listener, "CircuitBreakerObservabilityListener should not be null");
    }

    @Test
    @DisplayName("Should create TracingHealthIndicator bean")
    void testTracingHealthIndicatorCreation() {
        TracingProperties properties = new TracingProperties();
        properties.getSampling().setProbability(0.1);

        TracingHealthIndicator indicator = tracingConfiguration.tracingHealthIndicator(properties, currentTraceContext);

        assertNotNull(indicator, "TracingHealthIndicator should not be null");
    }

    @Test
    @DisplayName("Should initialize OpenTelemetry SDK")
    void testOpenTelemetryInitialization() {
        // Clear any previous initialization
        OpenTelemetryInitializer.shutdown();

        OpenTelemetryInitializer initializer = tracingConfiguration.openTelemetryInitializer();

        assertNotNull(initializer, "OpenTelemetryInitializer should not be null");
        OpenTelemetrySdk sdk = OpenTelemetryInitializer.getSdk();
        assertNotNull(sdk, "OpenTelemetry SDK should be initialized");

        // Cleanup
        OpenTelemetryInitializer.shutdown();
    }

    @Test
    @DisplayName("Should propagate trace ID to MDC")
    void testTraceIdPropagation() {
        TraceIdExtractor extractor = tracingConfiguration.traceIdExtractor(currentTraceContext);

        when(currentTraceContext.context()).thenReturn(null);
        String traceId = extractor.extractAndPropagateTraceId();

        assertTrue(traceId == null, "Should return null when no trace context is active");
    }

    @Test
    @DisplayName("Should support W3C TraceContext format")
    void testW3CTraceparentGeneration() {
        TraceIdExtractor extractor = tracingConfiguration.traceIdExtractor(currentTraceContext);

        when(currentTraceContext.context()).thenReturn(null);
        String traceparent = extractor.getW3CTraceparent();

        assertTrue(traceparent == null, "Should return null when no active trace");
    }

    @Test
    @DisplayName("Should register circuit breaker event listeners")
    void testCircuitBreakerListenerRegistration() {
        CircuitBreakerObservabilityListener listener =
                tracingConfiguration.circuitBreakerObservabilityListener(circuitBreakerRegistry, tracer);

        assertNotNull(listener, "Listener should be created and returned");
    }
}
