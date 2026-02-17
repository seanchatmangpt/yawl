package org.yawlfoundation.yawl.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.jaeger.thrift.JaegerThriftSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Initializes OpenTelemetry SDK with support for multiple trace exporters.
 *
 * Supports configuration through system properties:
 * - otel.exporter.otlp.endpoint: OTLP/gRPC endpoint (e.g., http://localhost:4317)
 * - otel.exporter.jaeger.endpoint: Jaeger Thrift endpoint (e.g., http://localhost:14268/api/traces)
 * - otel.service.name: Service name for resource (default: yawl-engine)
 * - otel.service.version: Service version (default: 6.0.0)
 * - otel.resource.attributes: Comma-separated key=value resource attributes
 */
public class OpenTelemetryInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenTelemetryInitializer.class);
    private static volatile OpenTelemetrySdk openTelemetrySdk;
    private static volatile boolean initialized = false;

    private OpenTelemetryInitializer() {
    }

    /**
     * Initializes the global OpenTelemetry SDK singleton.
     * This method is idempotent and thread-safe.
     */
    public static synchronized void initialize() {
        if (initialized) {
            LOGGER.debug("OpenTelemetry already initialized");
            return;
        }

        try {
            openTelemetrySdk = buildOpenTelemetrySdk();
            GlobalOpenTelemetry.set(openTelemetrySdk);
            initialized = true;
            LOGGER.info("OpenTelemetry SDK initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize OpenTelemetry SDK", e);
            throw new ObservabilityException("OpenTelemetry initialization failed", e);
        }
    }

    /**
     * Builds the OpenTelemetry SDK with configured span exporters.
     */
    private static OpenTelemetrySdk buildOpenTelemetrySdk() {
        Resource resource = buildResource();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .build();

        configureExporters(tracerProvider);

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
    }

    /**
     * Builds resource attributes describing the YAWL service.
     */
    private static Resource buildResource() {
        String serviceName = System.getProperty("otel.service.name", "yawl-engine");
        String serviceVersion = System.getProperty("otel.service.version", "6.0.0");

        AttributesBuilder attributesBuilder = Attributes.builder()
                .put(ResourceAttributes.SERVICE_NAME, serviceName)
                .put(ResourceAttributes.SERVICE_VERSION, serviceVersion)
                .put(ResourceAttributes.SERVICE_NAMESPACE, "org.yawlfoundation")
                .put("process.runtime.name", System.getProperty("java.runtime.name"))
                .put("process.runtime.version", System.getProperty("java.version"));

        String resourceAttributesStr = System.getProperty("otel.resource.attributes");
        if (resourceAttributesStr != null && !resourceAttributesStr.isEmpty()) {
            for (String attr : resourceAttributesStr.split(",")) {
                String[] kv = attr.split("=");
                if (kv.length == 2) {
                    attributesBuilder.put(kv[0].trim(), kv[1].trim());
                }
            }
        }

        return Resource.create(attributesBuilder.build());
    }

    /**
     * Configures span exporters based on system properties.
     * Supports OTLP/gRPC and Jaeger Thrift exporters.
     */
    private static void configureExporters(SdkTracerProvider tracerProvider) {
        String otlpEndpoint = System.getProperty("otel.exporter.otlp.endpoint");
        String jaegerEndpoint = System.getProperty("otel.exporter.jaeger.endpoint");

        if (otlpEndpoint != null && !otlpEndpoint.isEmpty()) {
            try {
                OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                        .setEndpoint(otlpEndpoint)
                        .build();
                tracerProvider.addSpanProcessor(SimpleSpanProcessor.create(exporter));
                LOGGER.info("Configured OTLP/gRPC span exporter: {}", otlpEndpoint);
            } catch (Exception e) {
                LOGGER.warn("Failed to configure OTLP exporter", e);
            }
        }

        if (jaegerEndpoint != null && !jaegerEndpoint.isEmpty()) {
            try {
                JaegerThriftSpanExporter exporter = JaegerThriftSpanExporter.builder()
                        .setEndpoint(jaegerEndpoint)
                        .build();
                tracerProvider.addSpanProcessor(SimpleSpanProcessor.create(exporter));
                LOGGER.info("Configured Jaeger Thrift span exporter: {}", jaegerEndpoint);
            } catch (Exception e) {
                LOGGER.warn("Failed to configure Jaeger exporter", e);
            }
        }

        if ((otlpEndpoint == null || otlpEndpoint.isEmpty()) &&
                (jaegerEndpoint == null || jaegerEndpoint.isEmpty())) {
            LOGGER.warn("No span exporters configured. Set otel.exporter.otlp.endpoint or otel.exporter.jaeger.endpoint");
        }
    }

    /**
     * Gets the global tracer for YAWL components.
     */
    public static Tracer getTracer(String instrumentationName) {
        if (!initialized) {
            initialize();
        }
        return Objects.requireNonNull(openTelemetrySdk).getTracer(instrumentationName, "6.0.0");
    }

    /**
     * Gets the initialized OpenTelemetry SDK.
     * Returns null if not yet initialized.
     */
    public static OpenTelemetrySdk getSdk() {
        return openTelemetrySdk;
    }

    /**
     * Shuts down the OpenTelemetry SDK gracefully.
     */
    public static synchronized void shutdown() {
        if (openTelemetrySdk != null) {
            try {
                openTelemetrySdk.close();
                LOGGER.info("OpenTelemetry SDK shut down");
            } catch (Exception e) {
                LOGGER.error("Error shutting down OpenTelemetry SDK", e);
            } finally {
                openTelemetrySdk = null;
                initialized = false;
            }
        }
    }
}
