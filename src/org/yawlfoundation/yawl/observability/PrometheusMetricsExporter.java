package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.metrics.core.registration.CollectorRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spring Boot auto-configuration for Prometheus metrics export.
 *
 * Integrates Micrometer + Prometheus client with custom labels for operational context.
 * Exposes metrics endpoint at `/actuator/prometheus`.
 *
 * Environment variables for configuration:
 * - YAWL_ENVIRONMENT (e.g., development, staging, production)
 * - YAWL_REGION (e.g., us-east-1, eu-west-1)
 * - YAWL_SERVICE_VERSION (application version)
 *
 * This configuration is automatically activated when:
 * 1. Micrometer Prometheus registry is on the classpath
 * 2. Spring Boot Actuator is configured
 */
@Configuration
@ConditionalOnClass({PrometheusMeterRegistry.class})
public class PrometheusMetricsExporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrometheusMetricsExporter.class);

    /**
     * Customizes the MeterRegistry with common labels for operational context.
     */
    @Bean
    public MeterRegistryCustomizer<PrometheusMeterRegistry> metricsCommonTags(Environment environment) {
        return registry -> {
            String environment_name = environment.getProperty("yawl.metrics.environment", "development");
            String region = environment.getProperty("yawl.metrics.region", "default");
            String service_version = environment.getProperty("app.version", "unknown");

            registry.config()
                    .commonTags(
                        "environment", environment_name,
                        "region", region,
                        "service_version", service_version,
                        "service_name", "yawl-engine"
                    );

            LOGGER.info("Prometheus metrics configured with common tags - environment: {}, region: {}, version: {}",
                    environment_name, region, service_version);
        };
    }

    /**
     * Initializes the CustomMetricsRegistry singleton with the provided MeterRegistry.
     * Called early to ensure metrics are available for all services.
     */
    @Bean
    public CustomMetricsRegistryInitializer customMetricsRegistryInitializer(MeterRegistry meterRegistry) {
        return new CustomMetricsRegistryInitializer(meterRegistry);
    }

    /**
     * Initializer bean that sets up the CustomMetricsRegistry singleton.
     */
    public static class CustomMetricsRegistryInitializer {

        public CustomMetricsRegistryInitializer(MeterRegistry meterRegistry) {
            CustomMetricsRegistry.initialize(meterRegistry);
            LOGGER.info("CustomMetricsRegistry initialized from Spring Boot configuration");
        }
    }

    /**
     * Provides prometheus metrics collector registry bean for advanced metric operations.
     * Allows direct Prometheus client API access for custom metric registration beyond Micrometer.
     */
    @Bean
    public CollectorRegistry prometheusCollectorRegistry() {
        return CollectorRegistry.defaultRegistry;
    }
}
