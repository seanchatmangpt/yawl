package org.yawlfoundation.yawl.mcp.a2a.service.metrics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;

/**
 * Spring configuration for Prometheus metrics in YAWL MCP-A2A application.
 *
 * <p>Configures Micrometer with Prometheus exposition format, including:</p>
 * <ul>
 *   <li>Metric naming conventions and filters</li>
 *   <li>Common tags for all metrics (application, version, instance)</li>
 *   <li>Metric name prefixing for YAWL-specific metrics</li>
 * </ul>
 *
 * <h2>Configuration Properties</h2>
 * <p>Metrics behavior can be customized via application properties:</p>
 * <pre>{@code
 * yawl:
 *   metrics:
 *     enabled: true
 *     environment: production
 *     instance-id: pod-123
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@Configuration
public class MetricsConfiguration {

    private static final String METRIC_PREFIX = "yawl_";

    @Value("${spring.application.name:yawl-mcp-a2a-app}")
    private String applicationName;

    @Value("${yawl.metrics.enabled:true}")
    private boolean metricsEnabled;

    @Value("${yawl.metrics.environment:unknown}")
    private String environment;

    @Value("${yawl.metrics.instance-id:}")
    private String instanceId;

    /**
     * Configure the Prometheus meter registry with custom settings.
     *
     * <p>Sets up:</p>
     * <ul>
     *   <li>Common tags for all metrics</li>
     *   <li>Meter filters for metric name prefixing</li>
     * </ul>
     *
     * @param registry the meter registry (typically PrometheusMeterRegistry)
     * @return configured MetricsService
     */
    @Bean
    @ConditionalOnMissingBean
    public MetricsService metricsService(MeterRegistry registry) {
        configureCommonTags(registry);
        configureMeterFilters(registry);

        return new MetricsService(registry);
    }

    /**
     * Configure common tags applied to all metrics.
     *
     * <p>Common tags enable filtering and aggregation across dimensions:</p>
     * <ul>
     *   <li>{@code application}: Application name for service identification</li>
     *   <li>{@code environment}: Deployment environment (dev, staging, prod)</li>
     *   <li>{@code instance}: Unique instance identifier for horizontal scaling</li>
     * </ul>
     *
     * @param registry the meter registry to configure
     */
    private void configureCommonTags(MeterRegistry registry) {
        registry.config().commonTags(
            "application", applicationName,
            "environment", environment
        );

        // Add instance ID if configured (for horizontal scaling scenarios)
        if (instanceId != null && !instanceId.isEmpty()) {
            registry.config().commonTags("instance", instanceId);
        }
    }

    /**
     * Configure meter filters to control metric creation.
     *
     * <p>Filters:</p>
     * <ul>
     *   <li>Transform metric names to follow YAWL naming conventions</li>
     * </ul>
     *
     * @param registry the meter registry to configure
     */
    private void configureMeterFilters(MeterRegistry registry) {
        registry.config().meterFilter(new MeterFilter() {

            /**
             * Transform metric names to follow YAWL naming conventions.
             *
             * <p>Adds the yawl_ prefix to MCP, A2A, workflow, and engine metrics
             * that don't already have it.</p>
             */
            @Override
            public Meter.Id map(Meter.Id id) {
                String name = id.getName();
                if (!name.startsWith(METRIC_PREFIX) && isYawlMetric(name)) {
                    return id.withName(METRIC_PREFIX + name);
                }
                return id;
            }

            /**
             * Determine if a metric is a YAWL-specific metric.
             */
            private boolean isYawlMetric(String name) {
                return name.startsWith("mcp_") ||
                       name.startsWith("a2a_") ||
                       name.startsWith("workflow_") ||
                       name.startsWith("engine_");
            }
        });
    }
}
