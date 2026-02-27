package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for YAWL observability infrastructure.
 * Validates OpenTelemetry tracing, Prometheus metrics, and structured logging.
 */
@Tag("unit")
@Execution(ExecutionMode.SAME_THREAD)  // Uses GlobalOpenTelemetry and YawlMetrics singletons
public class ObservabilityTest {

    private static OpenTelemetrySdk openTelemetrySdk;
    private static Tracer tracer;
    private static PrometheusMeterRegistry meterRegistry;

    @BeforeAll
    public static void setupAll() {
        // Create a minimal OpenTelemetry SDK for testing (no exporters)
        Resource resource = Resource.getDefault();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .build();

        openTelemetrySdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();

        tracer = openTelemetrySdk.getTracer("test-yawl-monitoring", "6.0.0");

        // Initialize metrics once for all tests
        meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        YawlMetrics.initialize(meterRegistry);
    }

    @AfterAll
    public static void tearDownAll() {
        if (openTelemetrySdk != null) {
            CompletableResultCode shutdown = openTelemetrySdk.shutdown();
            shutdown.join(5, TimeUnit.SECONDS);
        }
    }

    // ==================== OpenTelemetry Tests ====================

    @Test
    public void testOpenTelemetrySdk() {
        OpenTelemetry otel = GlobalOpenTelemetry.get();
        assertNotNull(otel, "OpenTelemetry should be initialized");
    }

    @Test
    public void testGetTracer() {
        assertNotNull(tracer, "Tracer should not be null");
    }

    @Test
    public void testWorkflowSpanBuilder() {
        Span span = WorkflowSpanBuilder.create(tracer, "test.operation")
            .withCaseId("case-123")
            .withSpecificationId("spec-456")
            .withActivityName("testActivity")
            .setAttribute("test_key", "test_value")
            .setAttribute("test_number", 42L)
            .setAttribute("test_boolean", true)
            .start();

        assertNotNull(span, "Span should be created");

        span.end();
    }

    @Test
    public void testSpanHierarchy() {
        Span parentSpan = tracer.spanBuilder("parent.operation")
            .setAttribute("level", "parent")
            .startSpan();

        Span childSpan = tracer.spanBuilder("child.operation")
            .setParent(io.opentelemetry.context.Context.current().with(parentSpan))
            .setAttribute("level", "child")
            .startSpan();

        assertNotNull(parentSpan);
        assertNotNull(childSpan);

        childSpan.end();
        parentSpan.end();
    }

    // ==================== Metrics Tests ====================

    @Test
    public void testYawlMetricsInitialization() {
        YawlMetrics metrics = YawlMetrics.getInstance();
        assertNotNull(metrics, "YawlMetrics should be initialized");
        assertNotNull(metrics.getMeterRegistry(), "MeterRegistry should be available");
    }

    @Test
    public void testCaseMetrics() {
        YawlMetrics metrics = YawlMetrics.getInstance();

        // Reset to known state
        metrics.setActiveCaseCount(0);

        // Test counter increments
        metrics.incrementCaseCreated();
        metrics.incrementCaseCreated();
        metrics.incrementCaseCompleted();
        metrics.incrementCaseFailed();

        // Verify active case count (2 created - 1 completed - 1 failed = 0)
        assertEquals(0, metrics.getActiveCaseCount(), "Should have 0 active cases after equal create/complete/fail");

        metrics.setActiveCaseCount(10);
        assertEquals(10, metrics.getActiveCaseCount(), "Active case count should be 10");
    }

    @Test
    public void testTaskMetrics() {
        YawlMetrics metrics = YawlMetrics.getInstance();

        metrics.incrementTaskExecuted();
        metrics.incrementTaskExecuted();
        metrics.incrementTaskFailed();

        // Metrics recorded successfully if no exceptions
    }

    @Test
    public void testQueueMetrics() {
        YawlMetrics metrics = YawlMetrics.getInstance();

        metrics.setQueueDepth(100);
        assertEquals(100, metrics.getQueueDepth(), "Queue depth should be 100");

        metrics.setQueueDepth(-10);
        assertEquals(0, metrics.getQueueDepth(), "Negative queue depth should be clamped to 0");
    }

    @Test
    public void testThreadPoolMetrics() {
        YawlMetrics metrics = YawlMetrics.getInstance();

        metrics.setActiveThreads(8);
        assertEquals(8, metrics.getActiveThreads(), "Active threads should be 8");

        metrics.setActiveThreads(-5);
        assertEquals(0, metrics.getActiveThreads(), "Negative thread count should be clamped to 0");
    }

    @Test
    public void testDurationMetrics() {
        YawlMetrics metrics = YawlMetrics.getInstance();

        // Record durations without exceptions
        metrics.recordCaseDuration(1000);
        metrics.recordTaskDuration(500);
        metrics.recordEngineLatencyMs(100);

        // Verify timer started and recorded successfully
        io.micrometer.core.instrument.Timer.Sample sample = io.micrometer.core.instrument.Timer.start(
            metrics.getMeterRegistry()
        );
        sample.stop(metrics.getMeterRegistry().timer("test.timer"));
    }

    @Test
    public void testPrometheusMetricsExport() {
        YawlMetrics metrics = YawlMetrics.getInstance();
        metrics.incrementCaseCreated();
        metrics.incrementTaskExecuted();

        String prometheusOutput = meterRegistry.scrape();
        // Debug: print output to see actual metric names
        // Prometheus converts dots to underscores and adds _total suffix for counters
        assertTrue(prometheusOutput.contains("yawl"),
            "Prometheus output should contain yawl metrics. Actual output: " + prometheusOutput);
    }

    // ==================== Structured Logging Tests ====================

    @Test
    public void testStructuredLoggerCreation() {
        StructuredLogger logger1 = StructuredLogger.getLogger(ObservabilityTest.class);
        StructuredLogger logger2 = StructuredLogger.getLogger("test.logger");

        assertNotNull(logger1, "Logger from class should be created");
        assertNotNull(logger2, "Logger from string should be created");
    }

    @Test
    public void testStructuredLoggerWithFields() {
        StructuredLogger log = StructuredLogger.getLogger("test.structured");
        Map<String, Object> fields = new HashMap<>();
        fields.put("case_id", "case-123");
        fields.put("activity", "test");
        fields.put("duration_ms", 500);

        // Should not throw exceptions
        log.info("Test message", fields);
        log.debug("Debug message", fields);
        log.warn("Warning message", fields);
    }

    @Test
    public void testStructuredLoggerWithException() {
        StructuredLogger log = StructuredLogger.getLogger("test.exception");
        Exception testException = new IllegalStateException("Test error");
        Map<String, Object> fields = new HashMap<>();
        fields.put("error_context", "test");

        // Should not throw exceptions
        log.error("Error occurred", fields, testException);
    }

    @Test
    public void testMDCContextManagement() {
        String correlationId = "correlation-123";
        String traceId = "trace-456";

        StructuredLogger.setCorrelationId(correlationId);
        StructuredLogger.setTraceId(traceId);

        // Verify context was set (cannot directly access MDC without SLF4J internals)
        // Logging should include these values
        StructuredLogger log = StructuredLogger.getLogger("test.mdc");
        log.info("Message with context", new HashMap<>());

        StructuredLogger.clearContext();
    }

    // ==================== Health Check Tests ====================

    @Test
    public void testHealthCheckResult() {
        Map<String, Object> details = new HashMap<>();
        details.put("database", "UP");
        details.put("queue", "UP");

        HealthCheckEndpoint.HealthCheckResult result = new HealthCheckEndpoint.HealthCheckResult(
            HealthCheckEndpoint.HealthStatus.UP,
            1000,
            details
        );

        assertEquals(HealthCheckEndpoint.HealthStatus.UP, result.getStatus());
        assertEquals(1000, result.getUptime());
        assertTrue(result.getDetails().containsKey("database"));
    }

    @Test
    public void testHealthCheckEndpoint() {
        HealthCheckEndpoint.HealthCheckDelegate delegate = new HealthCheckEndpoint.HealthCheckDelegate() {
            @Override
            public boolean isDatabaseHealthy() {
                return true;
            }

            @Override
            public boolean isQueueHealthy() {
                return true;
            }

            @Override
            public long getActiveWorkerThreads() {
                return 8;
            }

            @Override
            public long getMaxWorkerThreads() {
                return 16;
            }

            @Override
            public long getQueueDepth() {
                return 10;
            }

            @Override
            public long getQueueCapacity() {
                return 1000;
            }

            @Override
            public boolean isInitializationComplete() {
                return true;
            }

            @Override
            public long getWarmupDurationMs() {
                return 5000;
            }

            @Override
            public boolean isSchemaValid() {
                return true;
            }

            @Override
            public boolean isCaseStorageReady() {
                return true;
            }
        };

        HealthCheckEndpoint endpoint = new HealthCheckEndpoint(delegate);

        HealthCheckEndpoint.HealthCheckResult livenessResult = endpoint.liveness();
        assertEquals(HealthCheckEndpoint.HealthStatus.UP, livenessResult.getStatus());

        HealthCheckEndpoint.HealthCheckResult readinessResult = endpoint.readiness();
        assertEquals(HealthCheckEndpoint.HealthStatus.UP, readinessResult.getStatus());

        HealthCheckEndpoint.HealthCheckResult startupResult = endpoint.startup();
        assertEquals(HealthCheckEndpoint.HealthStatus.UP, startupResult.getStatus());
    }

    @Test
    public void testHealthCheckDegraded() {
        HealthCheckEndpoint.HealthCheckDelegate delegate = new HealthCheckEndpoint.HealthCheckDelegate() {
            @Override
            public boolean isDatabaseHealthy() {
                return true;
            }

            @Override
            public boolean isQueueHealthy() {
                return true;
            }

            @Override
            public long getActiveWorkerThreads() {
                return 8;
            }

            @Override
            public long getMaxWorkerThreads() {
                return 16;
            }

            @Override
            public long getQueueDepth() {
                return 950; // 95% of capacity
            }

            @Override
            public long getQueueCapacity() {
                return 1000;
            }

            @Override
            public boolean isInitializationComplete() {
                return true;
            }

            @Override
            public long getWarmupDurationMs() {
                return 5000;
            }

            @Override
            public boolean isSchemaValid() {
                return true;
            }

            @Override
            public boolean isCaseStorageReady() {
                return true;
            }
        };

        HealthCheckEndpoint endpoint = new HealthCheckEndpoint(delegate);
        HealthCheckEndpoint.HealthCheckResult result = endpoint.readiness();

        assertEquals(HealthCheckEndpoint.HealthStatus.DEGRADED, result.getStatus(),
            "Should be DEGRADED when queue near capacity");
    }

    @Test
    public void testHealthCheckHttpStatusCode() {
        HealthCheckEndpoint.HealthCheckDelegate delegate = createMockDelegate();
        HealthCheckEndpoint endpoint = new HealthCheckEndpoint(delegate);

        HealthCheckEndpoint.HealthCheckResult upResult = new HealthCheckEndpoint.HealthCheckResult(
            HealthCheckEndpoint.HealthStatus.UP, 1000, new HashMap<>()
        );
        assertEquals(200, endpoint.getHttpStatusCode(upResult));

        HealthCheckEndpoint.HealthCheckResult downResult = new HealthCheckEndpoint.HealthCheckResult(
            HealthCheckEndpoint.HealthStatus.DOWN, 1000, new HashMap<>()
        );
        assertEquals(503, endpoint.getHttpStatusCode(downResult));
    }

    @Test
    public void testHealthCheckJsonSerialization() {
        HealthCheckEndpoint.HealthCheckDelegate delegate = createMockDelegate();
        HealthCheckEndpoint endpoint = new HealthCheckEndpoint(delegate);

        HealthCheckEndpoint.HealthCheckResult result = new HealthCheckEndpoint.HealthCheckResult(
            HealthCheckEndpoint.HealthStatus.UP, 1000, new HashMap<>()
        );

        String json = endpoint.toJson(result);
        assertTrue(json.contains("\"status\":\"UP\"") || json.contains("\"status\": \"UP\""));
        assertTrue(json.contains("uptime"));
    }

    // ==================== Exception Handling Tests ====================

    @Test
    public void testObservabilityException() {
        ObservabilityException ex1 = new ObservabilityException("Test message");
        assertEquals("Test message", ex1.getMessage());

        Exception cause = new RuntimeException("Cause");
        ObservabilityException ex2 = new ObservabilityException("Test", cause);
        assertEquals("Test", ex2.getMessage());
        assertEquals(cause, ex2.getCause());

        ObservabilityException ex3 = new ObservabilityException(cause);
        assertEquals(cause, ex3.getCause());
    }

    // ==================== Helper Methods ====================

    private static HealthCheckEndpoint.HealthCheckDelegate createMockDelegate() {
        return new HealthCheckEndpoint.HealthCheckDelegate() {
            @Override
            public boolean isDatabaseHealthy() {
                return true;
            }

            @Override
            public boolean isQueueHealthy() {
                return true;
            }

            @Override
            public long getActiveWorkerThreads() {
                return 8;
            }

            @Override
            public long getMaxWorkerThreads() {
                return 16;
            }

            @Override
            public long getQueueDepth() {
                return 10;
            }

            @Override
            public long getQueueCapacity() {
                return 1000;
            }

            @Override
            public boolean isInitializationComplete() {
                return true;
            }

            @Override
            public long getWarmupDurationMs() {
                return 5000;
            }

            @Override
            public boolean isSchemaValid() {
                return true;
            }

            @Override
            public boolean isCaseStorageReady() {
                return true;
            }
        };
    }
}
