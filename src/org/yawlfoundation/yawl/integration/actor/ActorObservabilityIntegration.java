package org.yawlfoundation.yawl.integration.actor;

import org.yawlfoundation.yawl.integration.observability.OpenTelemetryConfig;
import org.yawlfoundation.yawl.integration.observability.ObservabilityService;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Observability Integration for Actor Validation
 *
 * Provides metrics, tracing, and logging integration for actor validation
 * with OpenTelemetry support for comprehensive observability.
 *
 * @since 6.0.0
 */
public class ActorObservabilityIntegration {

    private final ObservabilityService observabilityService;
    private final OpenTelemetry openTelemetry;
    private final Meter meter;
    private final Tracer tracer;

    // Metrics counters
    private final LongCounter validationCounter;
    private final LongCounter violationCounter;
    private final LongCounter memoryLeakCounter;
    private final LongCounter deadlockCounter;
    private final LongCounter slowProcessingCounter;

    // Metrics gauges
    private final ObservableDoubleGauge activeActorsGauge;
    private final ObservableDoubleGauge memoryUsageGauge;
    private final ObservableDoubleGauge averageProcessingTimeGauge;

    // Metrics up-down counters
    private final DoubleUpDownCounter healthScoreGauge;

    private final AtomicLong totalValidations = new AtomicLong(0);
    private final AtomicLong totalViolations = new AtomicLong(0);

    public ActorObservabilityIntegration(ObservabilityService observabilityService) {
        this.observabilityService = observabilityService;
        this.openTelemetry = OpenTelemetryConfig.getOpenTelemetry();
        this.meter = openTelemetry.getMeter("yawl.actor.validation");
        this.tracer = openTelemetry.getTracer("yawl.actor.validation");

        // Initialize metrics
        this.validationCounter = meter
            .counterBuilder("yawl.actor.validation.count")
            .setDescription("Total number of actor validations")
            .build();

        this.violationCounter = meter
            .counterBuilder("yawl.actor.validation.violations")
            .setDescription("Total number of validation violations")
            .build();

        this.memoryLeakCounter = meter
            .counterBuilder("yawl.actor.validation.memory.leaks")
            .setDescription("Number of memory leak violations")
            .build();

        this.deadlockCounter = meter
            .counterBuilder("yawl.actor.validation.deadlocks")
            .setDescription("Number of deadlock violations")
            .build();

        this.slowProcessingCounter = meter
            .counterBuilder("yawl.actor.validation.slow.processing")
            .setDescription("Number of slow processing violations")
            .build();

        this.activeActorsGauge = meter
            .gaugeBuilder("yawl.actor.active.count")
            .setDescription("Number of active actors")
            .build();

        this.memoryUsageGauge = meter
            .gaugeBuilder("yawl.actor.memory.usage")
            .setDescription("Memory usage of actors (MB)")
            .build();

        this.averageProcessingTimeGauge = meter
            .gaugeBuilder("yawl.actor.processing.time.average")
            .setDescription("Average processing time (ms)")
            .build();

        this.healthScoreGauge = meter
            .upDownCounterBuilder("yawl.actor.health.score")
            .setDescription("Actor health score (0-100)")
            .build();

        // Initialize observability service
        initializeObservability();
    }

    /**
     * Initialize observability service
     */
    private void initializeObservability() {
        // Register custom metrics
        observabilityService.registerMetric("actor_validation_count", () -> totalValidations.get());
        observabilityService.registerMetric("actor_violation_count", () -> totalViolations.get());
        observabilityService.registerMetric("actor_memory_leak_count", () -> memoryLeakCounter.getCount());
        observabilityService.registerMetric("actor_deadlock_count", () -> deadlockCounter.getCount());
        observabilityService.registerMetric("actor_slow_processing_count", () -> slowProcessingCounter.getCount());

        // Register event types
        observabilityService.registerEventType("actor.validation.started");
        observabilityService.registerEventType("actor.validation.completed");
        observabilityService.registerEventType("actor.violation.detected");
        observabilityService.registerEventType("actor.memory.leak.detected");
        observabilityService.registerEventType("actor.deadlock.detected");
        observabilityService.registerEventType("actor.performance.warning");
        observabilityService.registerEventType("actor.error");
    }

    /**
     * Start validation span
     */
    public Span startValidationSpan(String caseId) {
        return tracer.spanBuilder("actor.validation")
            .setAttribute("case.id", caseId)
            .setAttribute("start.time", Instant.now().toString())
            .startSpan();
    }

    /**
     * Record validation result
     */
    public void recordValidationResult(Span span, String caseId, Duration processingTime,
                                     boolean memoryLeakDetected, boolean deadlockDetected,
                                     int violationCount) {
        try (Scope scope = span.makeCurrent()) {
            // Update counters
            totalValidations.incrementAndGet();
            validationCounter.add(1);

            if (violationCount > 0) {
                totalViolations.addAndGet(violationCount);
                violationCounter.add(violationCount);
            }

            if (memoryLeakDetected) {
                memoryLeakCounter.add(1);
            }

            if (deadlockDetected) {
                deadlockCounter.add(1);
            }

            // Update gauges
            if (processingTime.toMillis() > 5000) {
                slowProcessingCounter.add(1);
            }

            // Update health score
            updateHealthScore(caseId, memoryLeakDetected, deadlockDetected, violationCount);

            // Set span attributes
            span.setAttribute("case.id", caseId)
               .setAttribute("processing.ms", processingTime.toMillis())
               .setAttribute("memory.leak", memoryLeakDetected)
               .setAttribute("deadlock", deadlockDetected)
               .setAttribute("violations", violationCount)
               .setAttribute("status", violationCount > 0 ? "failure" : "success");

            // End span
            span.end();
        } catch (Exception e) {
            observabilityService.emitEvent("actor.error", Map.of(
                "caseId", caseId,
                "error", e.getMessage(),
                "timestamp", Instant.now()
            ));
        }
    }

    /**
     * Emit validation started event
     */
    public void emitValidationStarted(String caseId) {
        observabilityService.emitEvent("actor.validation.started", Map.of(
            "caseId", caseId,
            "timestamp", Instant.now(),
            "metrics", Map.of(
                "total_validations", totalValidations.get(),
                "total_violations", totalViolations.get()
            )
        ));
    }

    /**
     * Emit validation completed event
     */
    public void emitValidationCompleted(String caseId, Duration processingTime,
                                      int violations, boolean success) {
        observabilityService.emitEvent("actor.validation.completed", Map.of(
            "caseId", caseId,
            "timestamp", Instant.now(),
            "processing.ms", processingTime.toMillis(),
            "violations", violations,
            "success", success,
            "metrics", Map.of(
                "total_validations", totalValidations.get(),
                "total_violations", totalViolations.get()
            )
        ));
    }

    /**
     * Emit violation event
     */
    public void emitViolationDetected(String caseId, String violationType, String message) {
        Map<String, Object> event = new HashMap<>();
        event.put("caseId", caseId);
        event.put("violationType", violationType);
        event.put("message", message);
        event.put("timestamp", Instant.now());

        switch (violationType) {
            case "H_ACTOR_LEAK":
                memoryLeakCounter.add(1);
                observabilityService.emitEvent("actor.memory.leak.detected", event);
                break;
            case "H_ACTOR_DEADLOCK":
                deadlockCounter.add(1);
                observabilityService.emitEvent("actor.deadlock.detected", event);
                break;
            default:
                observabilityService.emitEvent("actor.violation.detected", event);
        }

        violationCounter.add(1);
        totalViolations.incrementAndGet();
    }

    /**
     * Emit performance warning
     */
    public void emitPerformanceWarning(String caseId, String metric, double value, double threshold) {
        observabilityService.emitEvent("actor.performance.warning", Map.of(
            "caseId", caseId,
            "metric", metric,
            "value", value,
            "threshold", threshold,
            "timestamp", Instant.now()
        ));

        slowProcessingCounter.add(1);
    }

    /**
     * Update actor metrics
     */
    public void updateActorMetrics(int activeActors, double memoryUsageMB,
                                  double averageProcessingTime) {
        // Update gauges
        activeActorsGauge.set(activeActors);
        memoryUsageGauge.set(memoryUsageMB);
        averageProcessingTimeGauge.set(averageProcessingTime);

        // Emit metrics event
        observabilityService.emitEvent("actor.metrics.updated", Map.of(
            "active_actors", activeActors,
            "memory_mb", memoryUsageMB,
            "average_processing_ms", averageProcessingTime,
            "timestamp", Instant.now()
        ));
    }

    /**
     * Update health score
     */
    private void updateHealthScore(String caseId, boolean hasMemoryLeak,
                                 boolean hasDeadlock, int violationCount) {
        // Calculate health score (0-100)
        int score = 100;

        if (hasDeadlock) {
            score -= 50;
        }
        if (hasMemoryLeak) {
            score -= 30;
        }
        if (violationCount > 5) {
            score -= Math.min(violationCount, 20);
        }

        score = Math.max(0, score);

        // Update gauge
        healthScoreGauge.set(score);
    }

    /**
     * Create performance span
     */
    public Span createPerformanceSpan(String caseId, String operation) {
        return tracer.spanBuilder("actor.performance")
            .setAttribute("case.id", caseId)
            .setAttribute("operation", operation)
            .startSpan();
    }

    /**
     * Record error
     */
    public void recordError(String caseId, String errorType, String errorMessage, Exception e) {
        observabilityService.emitEvent("actor.error", Map.of(
            "caseId", caseId,
            "errorType", errorType,
            "errorMessage", errorMessage,
            "exception", e.getMessage(),
            "timestamp", Instant.now()
        ));

        // Create error span
        Span errorSpan = tracer.spanBuilder("actor.error")
            .setAttribute("case.id", caseId)
            .setAttribute("error.type", errorType)
            .setAttribute("error.message", errorMessage)
            .startSpan();

        if (e != null) {
            errorSpan.recordException(e);
        }

        errorSpan.end();
    }

    /**
     * Get metrics summary
     */
    public Map<String, Object> getMetricsSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("total_validations", totalValidations.get());
        summary.put("total_violations", totalViolations.get());
        summary.put("memory_leak_count", memoryLeakCounter.getCount());
        summary.put("deadlock_count", deadlockCounter.getCount());
        summary.put("slow_processing_count", slowProcessingCounter.getCount());
        summary.put("average_processing_time", averageProcessingTimeGauge.measure().getValue());
        summary.put("active_actors", activeActorsGauge.measure().getValue());
        summary.put("memory_usage_mb", memoryUsageGauge.measure().getValue());
        summary.put("health_score", healthScoreGauge.measure().getValue());
        summary.put("timestamp", Instant.now());
        return summary;
    }

    /**
     * Shutdown observability integration
     */
    public void shutdown() {
        // Flush any remaining metrics
        meter.close();

        // Emit shutdown event
        observabilityService.emitEvent("actor.validation.shutdown", Map.of(
            "timestamp", Instant.now(),
            "total_validations", totalValidations.get(),
            "total_violations", totalViolations.get()
        ));
    }
}