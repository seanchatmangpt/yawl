package org.yawlfoundation.yawl.observability;

import org.springframework.boot.actuate.autoconfigure.tracing.TracingProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.cloud.sleuth.CurrentTraceContext;

import java.util.Objects;

/**
 * Health indicator for distributed tracing system.
 *
 * Reports on the health and configuration status of Spring Cloud Sleuth and OpenTelemetry,
 * providing visibility into trace collection and propagation infrastructure.
 *
 * Health checks include:
 * - Tracing enabled/disabled status
 * - Sampling probability configuration
 * - Trace context availability
 * - OpenTelemetry SDK initialization status
 *
 * @since 6.0.0
 */
public class TracingHealthIndicator implements HealthIndicator {

    private final TracingProperties tracingProperties;
    private final CurrentTraceContext currentTraceContext;

    public TracingHealthIndicator(TracingProperties tracingProperties, CurrentTraceContext currentTraceContext) {
        this.tracingProperties = Objects.requireNonNull(tracingProperties);
        this.currentTraceContext = Objects.requireNonNull(currentTraceContext);
    }

    @Override
    public Health health() {
        try {
            Health.Builder builder = new Health.Builder();

            // Check if tracing is enabled
            boolean tracingEnabled = isTracingEnabled();
            builder.status(tracingEnabled ? "UP" : "DEGRADED");

            // Add sampling probability
            double samplingProbability = tracingProperties.getSampling().getProbability();
            builder.withDetail("sampling_probability", samplingProbability);

            // Check if trace context is available
            boolean hasActiveTrace = currentTraceContext.context() != null;
            builder.withDetail("active_trace_context", hasActiveTrace);

            // Check OpenTelemetry SDK status
            boolean otelInitialized = OpenTelemetryInitializer.getSdk() != null;
            builder.withDetail("opentelemetry_sdk_initialized", otelInitialized);

            // Add configuration details
            builder.withDetail("tracing_enabled", tracingEnabled);
            builder.withDetail("span_in_logs", tracingProperties.isSpanInLogs());

            return builder.build();
        } catch (Exception e) {
            return Health.down()
                    .withException(e)
                    .withDetail("error", "Failed to determine tracing health")
                    .build();
        }
    }

    /**
     * Determines if tracing is enabled by checking configuration properties.
     */
    private boolean isTracingEnabled() {
        // Tracing is enabled if sampling probability > 0
        return tracingProperties.getSampling().getProbability() > 0.0;
    }
}
