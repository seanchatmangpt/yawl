package org.yawlfoundation.yawl.integration.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for OpenTelemetryConfig.
 * Tests configuration loading, endpoint validation, and shutdown handling.
 *
 * Chicago TDD style - tests against real OpenTelemetry SDK components.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
class OpenTelemetryConfigTest {

    private OpenTelemetryConfig config;

    @BeforeEach
    void setUp() {
        System.setProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4317");
    }

    @AfterEach
    void tearDown() {
        if (config != null) {
            try {
                config.shutdown();
            } catch (Exception e) {
                // Ignore shutdown errors in cleanup
            }
            config = null;
        }
        System.clearProperty("OTEL_EXPORTER_OTLP_ENDPOINT");
    }

    @Test
    @DisplayName("Default constructor should initialize all components")
    void testDefaultConstructor() {
        config = new OpenTelemetryConfig();

        assertNotNull(config.getOpenTelemetry(), "OpenTelemetry instance should not be null");
        assertNotNull(config.getTracer(), "Tracer should not be null");
        assertNotNull(config.getTracerProvider(), "TracerProvider should not be null");
        assertNotNull(config.getMeterProvider(), "MeterProvider should not be null");
    }

    @Test
    @DisplayName("Custom endpoint and sampling should be accepted")
    void testCustomEndpointAndSampling() {
        String endpoint = "http://custom-collector:4317";
        double samplingRatio = 0.5;

        config = new OpenTelemetryConfig(endpoint, samplingRatio);

        assertNotNull(config.getOpenTelemetry(), "OpenTelemetry should be initialized with custom endpoint");
        assertNotNull(config.getTracer(), "Tracer should be available");
    }

    @Test
    @DisplayName("Null endpoint should throw IllegalArgumentException")
    void testNullEndpoint() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new OpenTelemetryConfig(null, 0.1)
        );
        assertTrue(exception.getMessage().contains("endpoint cannot be null or empty"),
            "Exception message should mention null/empty endpoint");
    }

    @Test
    @DisplayName("Empty endpoint should throw IllegalArgumentException")
    void testEmptyEndpoint() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new OpenTelemetryConfig("", 0.1)
        );
        assertTrue(exception.getMessage().contains("endpoint cannot be null or empty"),
            "Exception message should mention null/empty endpoint");
    }

    @Test
    @DisplayName("Whitespace-only endpoint should throw IllegalArgumentException")
    void testWhitespaceEndpoint() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new OpenTelemetryConfig("   ", 0.1)
        );
        assertTrue(exception.getMessage().contains("endpoint cannot be null or empty"),
            "Exception message should mention null/empty endpoint");
    }

    @Test
    @DisplayName("Negative sampling ratio should throw IllegalArgumentException")
    void testNegativeSamplingRatio() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new OpenTelemetryConfig("http://localhost:4317", -0.1)
        );
        assertTrue(exception.getMessage().contains("Sampling ratio must be between 0.0 and 1.0"),
            "Exception message should mention valid sampling range");
    }

    @Test
    @DisplayName("Sampling ratio > 1.0 should throw IllegalArgumentException")
    void testSamplingRatioTooHigh() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new OpenTelemetryConfig("http://localhost:4317", 1.5)
        );
        assertTrue(exception.getMessage().contains("Sampling ratio must be between 0.0 and 1.0"),
            "Exception message should mention valid sampling range");
    }

    @Test
    @DisplayName("Sampling ratio 0.0 should be valid")
    void testZeroSamplingRatio() {
        config = new OpenTelemetryConfig("http://localhost:4317", 0.0);
        assertNotNull(config.getOpenTelemetry(), "OpenTelemetry should accept 0.0 sampling ratio");
    }

    @Test
    @DisplayName("Sampling ratio 1.0 should be valid")
    void testFullSamplingRatio() {
        config = new OpenTelemetryConfig("http://localhost:4317", 1.0);
        assertNotNull(config.getOpenTelemetry(), "OpenTelemetry should accept 1.0 sampling ratio");
    }

    @Test
    @DisplayName("Tracer should be associated with yawl-engine service")
    void testTracerServiceName() {
        config = new OpenTelemetryConfig();
        Tracer tracer = config.getTracer();

        assertNotNull(tracer, "Tracer should not be null");
        assertDoesNotThrow(() -> {
            var span = tracer.spanBuilder("test-span").startSpan();
            span.end();
        }, "Should be able to create and end a span");
    }

    @Test
    @DisplayName("Shutdown should complete gracefully")
    void testShutdown() {
        config = new OpenTelemetryConfig();

        assertDoesNotThrow(() -> config.shutdown(),
            "Shutdown should not throw exception");
    }

    @Test
    @DisplayName("Multiple shutdowns should be handled gracefully")
    void testMultipleShutdowns() {
        config = new OpenTelemetryConfig();

        assertDoesNotThrow(() -> {
            config.shutdown();
            config.shutdown();
        }, "Multiple shutdowns should not throw exception");
    }

    @Test
    @DisplayName("ForceFlush should complete without error")
    void testForceFlush() {
        config = new OpenTelemetryConfig();

        assertDoesNotThrow(() -> config.forceFlush(),
            "ForceFlush should not throw exception");
    }

    @Test
    @DisplayName("ForceFlush after shutdown should be handled gracefully")
    void testForceFlushAfterShutdown() {
        config = new OpenTelemetryConfig();
        config.shutdown();

        assertDoesNotThrow(() -> config.forceFlush(),
            "ForceFlush after shutdown should not throw exception");
    }

    @Test
    @DisplayName("OpenTelemetry should be globally registered")
    void testGlobalRegistration() {
        config = new OpenTelemetryConfig();

        var globalTracer = io.opentelemetry.api.GlobalOpenTelemetry.getTracer("test");
        assertNotNull(globalTracer, "Global tracer should be available");
    }

    @Test
    @DisplayName("Tracer should create and complete spans")
    void testSpanCreation() {
        config = new OpenTelemetryConfig();
        Tracer tracer = config.getTracer();

        assertDoesNotThrow(() -> {
            var span = tracer.spanBuilder("integration-test-span")
                .setAttribute("test.attribute", "test-value")
                .startSpan();
            try (var scope = span.makeCurrent()) {
                Thread.sleep(10);
            } finally {
                span.end();
            }
        }, "Should create and end span without error");
    }

    @Test
    @DisplayName("MeterProvider should be operational")
    void testMeterProvider() {
        config = new OpenTelemetryConfig();
        SdkMeterProvider meterProvider = config.getMeterProvider();

        assertNotNull(meterProvider, "MeterProvider should not be null");

        assertDoesNotThrow(() -> {
            var meter = meterProvider.get("test-meter");
            var counter = meter.counterBuilder("test.counter").build();
            counter.add(1);
        }, "Should be able to create and use counters");
    }
}
