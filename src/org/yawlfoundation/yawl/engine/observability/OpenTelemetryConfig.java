/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.prometheus.PrometheusHttpServer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ResourceAttributes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Spring Boot configuration for OpenTelemetry integration.
 * This provides programmatic configuration for teams that want deeper control
 * over observability settings beyond what the Java agent provides.
 *
 * Note: The Java agent provides zero-code instrumentation. This configuration
 * is OPTIONAL and provides additional customization capabilities.
 *
 * @author YAWL Development Team
 */
@Configuration
@ConditionalOnProperty(name = "yawl.observability.enabled", havingValue = "true", matchIfMissing = false)
public class OpenTelemetryConfig {


    private static final Logger logger = LogManager.getLogger(OpenTelemetryConfig.class);
    private static final Logger _logger = LogManager.getLogger(OpenTelemetryConfig.class);

    @Value("${yawl.observability.service.name:yawl-engine}")
    private String serviceName;

    @Value("${yawl.observability.service.version:5.2}")
    private String serviceVersion;

    @Value("${yawl.observability.service.namespace:yawl-workflows}")
    private String serviceNamespace;

    @Value("${yawl.observability.deployment.environment:production}")
    private String deploymentEnvironment;

    @Value("${yawl.observability.otlp.endpoint:http://localhost:4317}")
    private String otlpEndpoint;

    @Value("${yawl.observability.traces.sampler.ratio:0.1}")
    private double traceSamplerRatio;

    @Value("${yawl.observability.metrics.export.interval:60}")
    private int metricsExportInterval;

    @Value("${yawl.observability.prometheus.port:9464}")
    private int prometheusPort;

    @Value("${yawl.observability.exporter.type:otlp}")
    private String exporterType;

    /**
     * Create the OpenTelemetry resource with YAWL service information.
     *
     * @return the configured Resource
     */
    @Bean
    public Resource otelResource() {
        String hostname = System.getenv("HOSTNAME");
        if (hostname == null || hostname.isEmpty()) {
            hostname = "unknown";
        }

        Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.builder()
                .put(ResourceAttributes.SERVICE_NAME, serviceName)
                .put(ResourceAttributes.SERVICE_VERSION, serviceVersion)
                .put(ResourceAttributes.SERVICE_NAMESPACE, serviceNamespace)
                .put(ResourceAttributes.DEPLOYMENT_ENVIRONMENT, deploymentEnvironment)
                .put(ResourceAttributes.SERVICE_INSTANCE_ID, hostname)
                .put(ResourceAttributes.TELEMETRY_SDK_NAME, "opentelemetry")
                .put(ResourceAttributes.TELEMETRY_SDK_LANGUAGE, "java")
                .put(ResourceAttributes.TELEMETRY_SDK_VERSION, "1.36.0")
                .build()));

        _logger.info("OpenTelemetry Resource created for service: {}", serviceName);
        return resource;
    }

    /**
     * Create the span exporter based on configuration.
     *
     * @return the configured SpanExporter
     */
    @Bean
    public SpanExporter spanExporter() {
        SpanExporter exporter = switch (exporterType.toLowerCase()) {
            case "otlp" -> {
                SpanExporter exp = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(otlpEndpoint)
                    .setTimeout(30, TimeUnit.SECONDS)
                    .build();
                _logger.info("OTLP SpanExporter configured with endpoint: {}", otlpEndpoint);
                yield exp;
            }
            case "logging" -> {
                _logger.info("Logging SpanExporter configured (for development)");
                yield LoggingSpanExporter.create();
            }
            default -> {
                _logger.warn("Unknown exporter type: {}. Using logging exporter.", exporterType);
                yield LoggingSpanExporter.create();
            }
        };

        return exporter;
    }

    /**
     * Create the tracer provider.
     *
     * @param resource the OpenTelemetry resource
     * @param spanExporter the span exporter
     * @return the configured SdkTracerProvider
     */
    @Bean
    public SdkTracerProvider sdkTracerProvider(Resource resource, SpanExporter spanExporter) {
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                .setScheduleDelay(Duration.ofSeconds(5))
                .setMaxQueueSize(8192)
                .setMaxExportBatchSize(512)
                .setExporterTimeout(Duration.ofSeconds(30))
                .build())
            .setSampler(Sampler.parentBased(
                Sampler.traceIdRatioBased(traceSamplerRatio)))
            .build();

        _logger.info("SdkTracerProvider configured with sampling ratio: {}", traceSamplerRatio);
        return tracerProvider;
    }

    /**
     * Create the metric reader for Prometheus.
     *
     * @return the Prometheus MetricReader
     */
    @Bean
    public MetricReader prometheusMetricReader() {
        try {
            MetricReader reader = PrometheusHttpServer.builder()
                .setPort(prometheusPort)
                .build();
            _logger.info("Prometheus MetricReader configured on port: {}", prometheusPort);
            return reader;
        } catch (Exception e) {
            _logger.error("Failed to create Prometheus MetricReader: {}", e.getMessage());
            throw new RuntimeException("Failed to configure Prometheus metrics", e);
        }
    }

    /**
     * Create the metric reader for OTLP.
     *
     * @return the OTLP MetricReader
     */
    @Bean
    public MetricReader otlpMetricReader() {
        if ("otlp".equalsIgnoreCase(exporterType)) {
            OtlpGrpcMetricExporter exporter = OtlpGrpcMetricExporter.builder()
                .setEndpoint(otlpEndpoint)
                .setTimeout(30, TimeUnit.SECONDS)
                .build();

            MetricReader reader = PeriodicMetricReader.builder(exporter)
                .setInterval(Duration.ofSeconds(metricsExportInterval))
                .build();

            _logger.info("OTLP MetricReader configured with interval: {}s", metricsExportInterval);
            return reader;
        } else if ("logging".equalsIgnoreCase(exporterType)) {
            LoggingMetricExporter exporter = LoggingMetricExporter.create();
            MetricReader reader = PeriodicMetricReader.builder(exporter)
                .setInterval(Duration.ofSeconds(metricsExportInterval))
                .build();

            _logger.info("Logging MetricReader configured (for development)");
            return reader;
        }

        return null;
    }

    /**
     * Create the meter provider.
     *
     * @param resource the OpenTelemetry resource
     * @param prometheusMetricReader the Prometheus metric reader
     * @param otlpMetricReader the OTLP metric reader (optional)
     * @return the configured SdkMeterProvider
     */
    @Bean
    public SdkMeterProvider sdkMeterProvider(Resource resource,
                                              MetricReader prometheusMetricReader,
                                              MetricReader otlpMetricReader) {
        var builder = SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(prometheusMetricReader);

        if (otlpMetricReader != null) {
            builder.registerMetricReader(otlpMetricReader);
        }

        SdkMeterProvider meterProvider = builder.build();
        _logger.info("SdkMeterProvider configured with multiple readers");
        return meterProvider;
    }

    /**
     * Create the OpenTelemetry SDK instance.
     *
     * @param tracerProvider the tracer provider
     * @param meterProvider the meter provider
     * @return the configured OpenTelemetry instance
     */
    @Bean
    public OpenTelemetry openTelemetry(SdkTracerProvider tracerProvider,
                                        SdkMeterProvider meterProvider) {
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .buildAndRegisterGlobal();

        _logger.info("OpenTelemetry SDK initialized and registered globally");
        return openTelemetry;
    }

    /**
     * Initialize YAWL telemetry singleton.
     *
     * @param openTelemetry the OpenTelemetry instance
     * @return the YAWLTelemetry instance
     */
    @Bean
    public YAWLTelemetry yawlTelemetry(OpenTelemetry openTelemetry) {
        YAWLTelemetry telemetry = YAWLTelemetry.getInstance();
        telemetry.setEnabled(true);
        _logger.info("YAWLTelemetry initialized and enabled");
        return telemetry;
    }
}
