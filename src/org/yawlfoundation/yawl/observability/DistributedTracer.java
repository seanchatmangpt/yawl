package org.yawlfoundation.yawl.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Objects;
import java.util.UUID;

/**
 * Distributed tracing for cross-agent workflow execution.
 *
 * Auto-propagates trace IDs across workflow boundaries:
 * - Generate unique trace IDs for case lifecycle
 * - Auto-propagate to dependent tasks and agents
 * - Correlate events across autonomous agents
 * - Visualize end-to-end workflow execution flow
 *
 * Thread-safe implementation with scope-based context propagation.
 */
public class DistributedTracer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedTracer.class);
    private static final String TRACE_ID_PREFIX = "yawl-";
    private static final String SPAN_NAME_FORMAT = "%s_%s";

    private final Tracer tracer;

    public DistributedTracer(OpenTelemetry openTelemetry) {
        Objects.requireNonNull(openTelemetry);
        this.tracer = openTelemetry.getTracer(DistributedTracer.class.getCanonicalName());
    }

    /**
     * Generates a new trace ID for case lifecycle.
     */
    public String generateTraceId() {
        return TRACE_ID_PREFIX + UUID.randomUUID().toString();
    }

    /**
     * Starts a trace span for a case execution.
     */
    public TraceSpan startCaseSpan(String caseId, String specId) {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(specId);

        String spanName = String.format(SPAN_NAME_FORMAT, "case", specId);
        Span span = tracer.spanBuilder(spanName)
                .setAttribute("yawl.case.id", caseId)
                .setAttribute("yawl.spec.id", specId)
                .setAttribute("yawl.event.type", "case_started")
                .startSpan();

        String traceId = span.getSpanContext().getTraceId();
        MDC.put("trace_id", traceId);

        return new TraceSpan(span, traceId);
    }

    /**
     * Starts a trace span for a work item execution within a case.
     */
    public TraceSpan startWorkItemSpan(String caseId, String workItemId, String taskName, String parentTraceId) {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(workItemId);
        Objects.requireNonNull(taskName);

        String spanName = String.format(SPAN_NAME_FORMAT, "workitem", taskName);
        Span.Builder spanBuilder = tracer.spanBuilder(spanName)
                .setAttribute("yawl.case.id", caseId)
                .setAttribute("yawl.workitem.id", workItemId)
                .setAttribute("yawl.task.name", taskName)
                .setAttribute("yawl.event.type", "workitem_created");

        if (parentTraceId != null) {
            spanBuilder.setAttribute("trace.parent_id", parentTraceId);
        }

        Span span = spanBuilder.startSpan();
        String traceId = span.getSpanContext().getTraceId();

        return new TraceSpan(span, traceId);
    }

    /**
     * Starts a trace span for a task execution.
     */
    public TraceSpan startTaskSpan(String taskName, String caseId, String agentId) {
        Objects.requireNonNull(taskName);
        Objects.requireNonNull(caseId);

        String spanName = String.format(SPAN_NAME_FORMAT, "task", taskName);
        Span span = tracer.spanBuilder(spanName)
                .setAttribute("yawl.task.name", taskName)
                .setAttribute("yawl.case.id", caseId)
                .setAttribute("yawl.event.type", "task_started");

        if (agentId != null) {
            span.setAttribute("yawl.agent.id", agentId);
        }

        Span startedSpan = span.startSpan();
        String traceId = startedSpan.getSpanContext().getTraceId();

        return new TraceSpan(startedSpan, traceId);
    }

    /**
     * Starts a trace span for an agent action.
     */
    public TraceSpan startAgentActionSpan(String agentId, String actionName, String caseId) {
        Objects.requireNonNull(agentId);
        Objects.requireNonNull(actionName);

        String spanName = String.format(SPAN_NAME_FORMAT, "agent_action", actionName);
        Span span = tracer.spanBuilder(spanName)
                .setAttribute("yawl.agent.id", agentId)
                .setAttribute("yawl.action.name", actionName)
                .setAttribute("yawl.event.type", "agent_action")
                .setAttribute("yawl.case.id", caseId)
                .startSpan();

        String traceId = span.getSpanContext().getTraceId();

        return new TraceSpan(span, traceId);
    }

    /**
     * Propagates trace ID to MDC for correlation.
     */
    public void propagateTraceId(String traceId) {
        if (traceId != null) {
            MDC.put("trace_id", traceId);
        }
    }

    /**
     * Extracts trace ID from MDC.
     */
    public String extractTraceId() {
        return MDC.get("trace_id");
    }

    /**
     * Clears trace context from MDC.
     */
    public void clearTraceContext() {
        MDC.remove("trace_id");
    }

    /**
     * Wraps a runnable with tracing context for parallel execution.
     */
    public Runnable withTraceContext(Runnable runnable, String traceId) {
        Objects.requireNonNull(runnable);
        Objects.requireNonNull(traceId);

        return () -> {
            String previousTraceId = MDC.get("trace_id");
            try {
                MDC.put("trace_id", traceId);
                runnable.run();
            } finally {
                if (previousTraceId != null) {
                    MDC.put("trace_id", previousTraceId);
                } else {
                    MDC.remove("trace_id");
                }
            }
        };
    }

    /**
     * Immutable trace span handle with automatic context management.
     */
    public static final class TraceSpan implements AutoCloseable {
        private final Span span;
        private final String traceId;
        private Scope scope;

        TraceSpan(Span span, String traceId) {
            this.span = Objects.requireNonNull(span);
            this.traceId = Objects.requireNonNull(traceId);
            this.scope = null;
        }

        /**
         * Activates this span in the current context.
         */
        public void activate() {
            if (scope == null) {
                scope = span.makeCurrent();
            }
        }

        /**
         * Adds an event to the span.
         */
        public void addEvent(String eventName, String... keyValues) {
            span.addEvent(eventName);
            for (int i = 0; i < keyValues.length; i += 2) {
                if (i + 1 < keyValues.length) {
                    span.setAttribute(keyValues[i], keyValues[i + 1]);
                }
            }
        }

        /**
         * Records an error to the span.
         */
        public void recordException(Throwable exception) {
            span.recordException(exception);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
        }

        /**
         * Sets a custom attribute on the span.
         */
        public void setAttribute(String key, String value) {
            span.setAttribute(key, value);
        }

        /**
         * Gets the trace ID.
         */
        public String getTraceId() {
            return traceId;
        }

        /**
         * Ends the span and releases scope.
         */
        @Override
        public void close() {
            try {
                if (scope != null) {
                    scope.close();
                    scope = null;
                }
            } finally {
                span.end();
            }
        }

        /**
         * Ends span with status and message.
         */
        public void endWithError(String message) {
            try {
                span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, message);
            } finally {
                close();
            }
        }

        /**
         * Ends span with success status.
         */
        public void endWithSuccess() {
            try {
                span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
            } finally {
                close();
            }
        }
    }
}
