package org.yawlfoundation.yawl.integration.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.prometheus.PrometheusHttpServer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * OpenTelemetry Configuration for YAWL.
 * Provides distributed tracing, metrics, and logging integration.
 *
 * Features:
 * - OTLP exporter for traces to collector
 * - Prometheus metrics endpoint
 * - W3C Trace Context propagation
 * - Configurable sampling (10% default)
 * - Real implementation with no mocks
 *
 * @author YAWL Integration Team
 * @version 5.2
 */
public class OpenTelemetryConfig {

    private static final String SERVICE_NAME = "yawl-engine";
    private static final String SERVICE_VERSION = "5.2";
    private static final double DEFAULT_SAMPLING_RATIO = 0.1; // 10% sampling
    private static final int PROMETHEUS_PORT = 9464;

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final SdkTracerProvider tracerProvider;
    private final SdkMeterProvider meterProvider;

    /**
     * Creates OpenTelemetry configuration with default settings.
     * Reads OTEL_EXPORTER_OTLP_ENDPOINT from environment or uses localhost:4317.
     */
    public OpenTelemetryConfig() {
        this(System.getenv().getOrDefault("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4317"),
             DEFAULT_SAMPLING_RATIO);
    }

    /**
     * Creates OpenTelemetry configuration with custom settings.
     *
     * @param otlpEndpoint OTLP collector endpoint
     * @param samplingRatio Trace sampling ratio (0.0 to 1.0)
     */
    public OpenTelemetryConfig(String otlpEndpoint, double samplingRatio) {
        if (otlpEndpoint == null || otlpEndpoint.isBlank()) {
            throw new IllegalArgumentException("OTLP endpoint cannot be null or empty");
        }
        if (samplingRatio < 0.0 || samplingRatio > 1.0) {
            throw new IllegalArgumentException("Sampling ratio must be between 0.0 and 1.0");
        }

        Resource resource = createResource();
        this.tracerProvider = createTracerProvider(resource, otlpEndpoint, samplingRatio);
        this.meterProvider = createMeterProvider(resource, otlpEndpoint);
        this.openTelemetry = createOpenTelemetry();
        this.tracer = openTelemetry.getTracer(SERVICE_NAME, SERVICE_VERSION);

        System.out.println("OpenTelemetry initialized:");
        System.out.println("  Service: " + SERVICE_NAME + " v" + SERVICE_VERSION);
        System.out.println("  OTLP Endpoint: " + otlpEndpoint);
        System.out.println("  Sampling Ratio: " + (samplingRatio * 100) + "%");
        System.out.println("  Prometheus Port: " + PROMETHEUS_PORT);
    }

    /**
     * Creates resource with service metadata.
     */
    private Resource createResource() {
        String hostname = System.getenv().getOrDefault("HOSTNAME", "unknown");
        String environment = System.getenv().getOrDefault("ENVIRONMENT", "development");

        return Resource.getDefault().merge(
            Resource.create(
                Attributes.builder()
                    .put(AttributeKey.stringKey("service.name"), SERVICE_NAME)
                    .put(AttributeKey.stringKey("service.version"), SERVICE_VERSION)
                    .put(AttributeKey.stringKey("service.namespace"), "yawl")
                    .put(AttributeKey.stringKey("service.instance.id"), hostname)
                    .put(AttributeKey.stringKey("deployment.environment"), environment)
                    .build()
            )
        );
    }

    /**
     * Creates tracer provider with OTLP exporter and console logging.
     */
    private SdkTracerProvider createTracerProvider(Resource resource, String otlpEndpoint, double samplingRatio) {
        OtlpGrpcSpanExporter otlpExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(otlpEndpoint)
                .setTimeout(Duration.ofSeconds(10))
                .build();

        LoggingSpanExporter loggingExporter = LoggingSpanExporter.create();

        return SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(otlpExporter)
                        .setScheduleDelay(Duration.ofSeconds(5))
                        .setMaxQueueSize(2048)
                        .setMaxExportBatchSize(512)
                        .build())
                .addSpanProcessor(SimpleSpanProcessor.create(loggingExporter))
                .setSampler(Sampler.traceIdRatioBased(samplingRatio))
                .build();
    }

    /**
     * Creates meter provider with Prometheus exporter and OTLP exporter.
     */
    private SdkMeterProvider createMeterProvider(Resource resource, String otlpEndpoint) {
        PrometheusHttpServer prometheusExporter = PrometheusHttpServer.builder()
                .setPort(PROMETHEUS_PORT)
                .build();

        OtlpGrpcMetricExporter otlpMetricExporter = OtlpGrpcMetricExporter.builder()
                .setEndpoint(otlpEndpoint)
                .setTimeout(Duration.ofSeconds(10))
                .build();

        PeriodicMetricReader otlpReader = PeriodicMetricReader.builder(otlpMetricExporter)
                .setInterval(Duration.ofSeconds(60))
                .build();

        return SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(prometheusExporter)
                .registerMetricReader(otlpReader)
                .build();
    }

    /**
     * Creates OpenTelemetry SDK instance.
     */
    private OpenTelemetry createOpenTelemetry() {
        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();
    }

    /**
     * Gets the OpenTelemetry instance.
     *
     * @return OpenTelemetry instance
     */
    public OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }

    /**
     * Gets the tracer for creating spans.
     *
     * @return Tracer instance
     */
    public Tracer getTracer() {
        return tracer;
    }

    /**
     * Gets the tracer provider.
     *
     * @return SdkTracerProvider instance
     */
    public SdkTracerProvider getTracerProvider() {
        return tracerProvider;
    }

    /**
     * Gets the meter provider.
     *
     * @return SdkMeterProvider instance
     */
    public SdkMeterProvider getMeterProvider() {
        return meterProvider;
    }

    /**
     * Shuts down OpenTelemetry and flushes remaining telemetry.
     */
    public void shutdown() {
        System.out.println("Shutting down OpenTelemetry...");

        try {
            tracerProvider.shutdown().join(10, TimeUnit.SECONDS);
            meterProvider.shutdown().join(10, TimeUnit.SECONDS);
            System.out.println("OpenTelemetry shutdown complete");
        } catch (Exception e) {
            System.err.println("Error during OpenTelemetry shutdown: " + e.getMessage());
        }
    }

    /**
     * Forces immediate flush of pending telemetry.
     */
    public void forceFlush() {
        try {
            tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
            meterProvider.forceFlush().join(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Error during OpenTelemetry flush: " + e.getMessage());
        }
    }
}
