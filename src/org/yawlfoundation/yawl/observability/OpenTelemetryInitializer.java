package org.yawlfoundation.yawl.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;

/**
 * Initializes OpenTelemetry SDK with support for OTLP trace exporters.
 *
 * Supports configuration through system properties:
 * - otel.exporter.otlp.endpoint: OTLP/gRPC endpoint (e.g., http://localhost:4317)
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
        SdkTracerProvider tracerProvider = createTracerProvider(resource);

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

        Attributes.Builder attributesBuilder = Attributes.builder()
                .put(AttributeKey.stringKey("service.name"), serviceName)
                .put(AttributeKey.stringKey("service.version"), serviceVersion)
                .put(AttributeKey.stringKey("service.namespace"), "org.yawlfoundation")
                .put(AttributeKey.stringKey("process.runtime.name"), System.getProperty("java.runtime.name"))
                .put(AttributeKey.stringKey("process.runtime.version"), System.getProperty("java.version"));

        String resourceAttributesStr = System.getProperty("otel.resource.attributes");
        if (resourceAttributesStr != null && !resourceAttributesStr.isEmpty()) {
            for (String attr : resourceAttributesStr.split(",")) {
                String[] kv = attr.split("=");
                if (kv.length == 2) {
                    attributesBuilder.put(kv[0].trim(), kv[1].trim());
                }
            }
        }

        return Resource.getDefault().merge(Resource.create(attributesBuilder.build()));
    }

    /**
     * Creates tracer provider with OTLP exporter if configured.
     */
    private static SdkTracerProvider createTracerProvider(Resource resource) {
        String otlpEndpoint = System.getProperty("otel.exporter.otlp.endpoint");

        SdkTracerProvider.Builder builder = SdkTracerProvider.builder()
                .setResource(resource);

        if (otlpEndpoint != null && !otlpEndpoint.isEmpty()) {
            try {
                OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                        .setEndpoint(otlpEndpoint)
                        .setTimeout(Duration.ofSeconds(10))
                        .build();
                builder.addSpanProcessor(SimpleSpanProcessor.create(exporter));
                LOGGER.info("Configured OTLP/gRPC span exporter: {}", otlpEndpoint);
            } catch (Exception e) {
                LOGGER.warn("Failed to configure OTLP exporter", e);
            }
        } else {
            LOGGER.warn("No span exporters configured. Set otel.exporter.otlp.endpoint");
        }

        return builder.build();
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
