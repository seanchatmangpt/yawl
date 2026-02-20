package org.yawlfoundation.yawl.observability;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.EventConsumer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.tracing.TracingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.sleuth.BaggageManager;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.SleuthTracingAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.ServletHttpServerAttributesGetter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Spring Cloud Sleuth + OpenTelemetry distributed tracing configuration for YAWL v6.0.0.
 *
 * This configuration enables Spring Cloud Sleuth for automatic trace ID propagation across
 * thread boundaries and integrates with OpenTelemetry for advanced span creation and export.
 *
 * Key capabilities:
 * - Automatic trace ID generation and propagation via Sleuth
 * - MDC (Mapped Diagnostic Context) integration with W3C TraceContext format
 * - Virtual thread support (Java 21+) with proper trace context inheritance
 * - Integration with resilience4j circuit breaker events
 * - Configurable trace ID extraction and propagation via custom propagators
 * - Compatible with OpenTelemetry OTLP exporters for centralized trace collection
 *
 * Configuration properties (in application.yml):
 * - spring.sleuth.sampler.probability: Trace sampling ratio (default 0.1 = 10%)
 * - spring.sleuth.propagation-keys: Custom MDC keys to propagate
 * - otel.exporter.otlp.endpoint: OTLP collector endpoint (e.g., http://localhost:4317)
 *
 * Example usage in application code:
 * {@code
 * @Autowired
 * private Tracer tracer;
 *
 * public void processWorkitem(String workitemId) {
 *     try (var scope = tracer.createScope("process.workitem", workitemId)) {
 *         // Trace ID automatically included in logs via MDC
 *         logger.info("Processing workitem");
 *         // Nested spans automatically create parent-child relationships
 *     }
 * }
 * }
 *
 * @since 6.0.0
 */
@Configuration
@ConditionalOnClass({
    SleuthTracingAutoConfiguration.class,
    io.opentelemetry.api.OpenTelemetry.class
})
@Import(SleuthTracingAutoConfiguration.class)
public class TracingConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(TracingConfiguration.class);
    private static final String YAWL_SERVICE_NAME = "yawl-engine";

    /**
     * Creates a bean for MDC-based trace ID extraction and propagation.
     * This bean ensures trace IDs flow through logs even when Sleuth's automatic
     * instrumentation is not active.
     */
    @Bean
    public TraceIdExtractor traceIdExtractor(CurrentTraceContext currentTraceContext) {
        return new TraceIdExtractor(currentTraceContext);
    }

    /**
     * Creates a TextMapPropagator for OpenTelemetry that propagates trace context
     * across thread boundaries, especially important for virtual threads.
     */
    @Bean
    public MdcContextPropagator mdcContextPropagator() {
        return new MdcContextPropagator();
    }

    /**
     * Configures resilience4j circuit breaker observability by registering event listeners.
     * These listeners emit trace events when circuit breaker state changes occur, enabling
     * correlation between resilience events and distributed traces.
     */
    @Bean
    public CircuitBreakerObservabilityListener circuitBreakerObservabilityListener(
            CircuitBreakerRegistry circuitBreakerRegistry,
            Tracer tracer) {

        CircuitBreakerObservabilityListener listener = new CircuitBreakerObservabilityListener(tracer);

        circuitBreakerRegistry.getEventPublisher()
                .onEntryAdded(event -> {
                    CircuitBreaker circuitBreaker = event.getAddedEntry();
                    circuitBreaker.getEventPublisher()
                            .onStateTransition(listener::onCircuitBreakerStateTransition)
                            .onSuccess(listener::onCircuitBreakerSuccess)
                            .onError(listener::onCircuitBreakerError)
                            .onIgnoredError(listener::onCircuitBreakerIgnoredError)
                            .onSlowSuccess(listener::onCircuitBreakerSlowSuccess)
                            .onSlowError(listener::onCircuitBreakerSlowError)
                            .onCallNotPermitted(listener::onCircuitBreakerCallNotPermitted);
                    LOGGER.debug("Registered observability for circuit breaker: {}", circuitBreaker.getName());
                });

        return listener;
    }

    /**
     * Verifies that tracing is enabled and properly configured.
     * Logs configuration status at startup for operational visibility.
     */
    @Bean
    public TracingHealthIndicator tracingHealthIndicator(
            TracingProperties tracingProperties,
            CurrentTraceContext currentTraceContext) {

        TracingHealthIndicator indicator = new TracingHealthIndicator(tracingProperties, currentTraceContext);
        LOGGER.info("Tracing health indicator registered. Sampling probability: {}",
                tracingProperties.getSampling().getProbability());
        return indicator;
    }

    /**
     * Initializes OpenTelemetry if not already initialized.
     * This ensures the global OpenTelemetry SDK is available for manual span creation.
     */
    @Bean
    public OpenTelemetryInitializer openTelemetryInitializer() {
        OpenTelemetryInitializer.initialize();
        LOGGER.info("OpenTelemetry SDK initialized");
        return new OpenTelemetryInitializer();
    }
}
