package org.yawlfoundation.yawl.observability;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

/**
 * Fluent builder for creating workflow-related spans.
 *
 * Provides workflow-specific semantic conventions for span hierarchy:
 * - engine: YAWL engine operations (case creation, execution)
 * - task: Task/activity execution
 * - activity: Individual workflow activity
 * - transition: Case transitions between states
 *
 * Example usage:
 * Span span = WorkflowSpanBuilder.create(tracer, "case.execute")
 *     .setSpanKind(SpanKind.INTERNAL)
 *     .setAttribute("case.id", "case-123")
 *     .setAttribute("specification.id", "spec-456")
 *     .start();
 */
public class WorkflowSpanBuilder {

    private final Tracer tracer;
    private final String spanName;
    private final AttributesBuilder attributesBuilder;
    private SpanKind spanKind = SpanKind.INTERNAL;
    private Context parentContext = Context.current();

    private WorkflowSpanBuilder(Tracer tracer, String spanName) {
        this.tracer = tracer;
        this.spanName = spanName;
        this.attributesBuilder = Attributes.builder();
    }

    /**
     * Creates a new span builder with the given tracer and span name.
     */
    public static WorkflowSpanBuilder create(Tracer tracer, String spanName) {
        return new WorkflowSpanBuilder(tracer, spanName);
    }

    /**
     * Sets the span kind (INTERNAL, SERVER, CLIENT, etc).
     */
    public WorkflowSpanBuilder setSpanKind(SpanKind kind) {
        this.spanKind = kind;
        return this;
    }

    /**
     * Adds a string attribute to the span.
     */
    public WorkflowSpanBuilder setAttribute(String key, String value) {
        if (value != null) {
            attributesBuilder.put(key, value);
        }
        return this;
    }

    /**
     * Adds a numeric attribute to the span.
     */
    public WorkflowSpanBuilder setAttribute(String key, long value) {
        attributesBuilder.put(key, value);
        return this;
    }

    /**
     * Adds a numeric attribute to the span.
     */
    public WorkflowSpanBuilder setAttribute(String key, double value) {
        attributesBuilder.put(key, value);
        return this;
    }

    /**
     * Adds a boolean attribute to the span.
     */
    public WorkflowSpanBuilder setAttribute(String key, boolean value) {
        attributesBuilder.put(key, value);
        return this;
    }

    /**
     * Sets the parent context for this span.
     */
    public WorkflowSpanBuilder setParentContext(Context context) {
        this.parentContext = context;
        return this;
    }

    /**
     * Adds case-related attributes.
     */
    public WorkflowSpanBuilder withCaseId(String caseId) {
        return setAttribute("case.id", caseId);
    }

    /**
     * Adds specification-related attributes.
     */
    public WorkflowSpanBuilder withSpecificationId(String specId) {
        return setAttribute("specification.id", specId);
    }

    /**
     * Adds task-related attributes.
     */
    public WorkflowSpanBuilder withTaskId(String taskId) {
        return setAttribute("task.id", taskId);
    }

    /**
     * Adds activity name.
     */
    public WorkflowSpanBuilder withActivityName(String activityName) {
        return setAttribute("activity.name", activityName);
    }

    /**
     * Adds engine metrics.
     */
    public WorkflowSpanBuilder withEngineMetrics(long queueDepth, long activeThreads) {
        return setAttribute("engine.queue_depth", queueDepth)
                .setAttribute("engine.active_threads", activeThreads);
    }

    /**
     * Starts the span with configured attributes.
     */
    public Span start() {
        return tracer.spanBuilder(spanName)
                .setSpanKind(spanKind)
                .setAllAttributes(attributesBuilder.build())
                .setParent(parentContext)
                .startSpan();
    }
}
