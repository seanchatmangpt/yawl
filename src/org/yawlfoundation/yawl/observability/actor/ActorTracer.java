package org.yawlfoundation.yawl.observability.actor;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.api.trace.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Duration;
import java.time.Instant;

/**
 * Distributed tracing for YAWL actor message flows.
 *
 * Provides comprehensive tracing capabilities for actor-based systems including:
 * - Message flow tracing across actor boundaries
 * - End-to-end request tracking
 * - Performance analysis and bottleneck detection
 * - Error propagation and correlation
 * - Span context propagation
 *
 * Integrates with OpenTelemetry and existing YAWL observability.
 */
public class ActorTracer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorTracer.class);
    private static ActorTracer instance;

    private final Tracer tracer;
    private final TextMapPropagator textMapPropagator;
    private final ConcurrentMap<String, ActorSpanContext> activeSpans;
    private final AtomicLong spanIdCounter;

    // Message tracing configuration
    private final boolean traceMessages;
    private final boolean traceActorInteractions;
    private final Duration maxSpanDuration;
    private final int maxSpanDepth;

    // Context propagation utilities
    private final MapTextMapGetter mapGetter = new MapTextMapGetter();
    private final MapTextMapSetter mapSetter = new MapTextMapSetter();

    /**
     * Creates a new ActorTracer instance.
     */
    public ActorTracer(Tracer tracer) {
        this.tracer = tracer;
        this.textMapPropagator = io.opentelemetry.api.GlobalOpenTelemetry.get()
                .getPropagators()
                .getTextMapPropagator();
        this.activeSpans = new ConcurrentHashMap<>();
        this.spanIdCounter = new AtomicLong(0);
        this.traceMessages = true;
        this.traceActorInteractions = true;
        this.maxSpanDuration = Duration.ofSeconds(30);
        this.maxSpanDepth = 10;

        LOGGER.info("ActorTracer initialized");
    }

    /**
     * Initializes the singleton instance.
     */
    public static synchronized void initialize(Tracer tracer) {
        if (instance == null) {
            instance = new ActorTracer(tracer);
        }
    }

    /**
     * Gets the singleton instance.
     */
    public static ActorTracer getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ActorTracer not initialized. Call initialize() first.");
        }
        return instance;
    }

    // Message Flow Tracing Methods

    /**
     * Starts a new message flow span.
     */
    public ActorSpan startMessageFlow(String flowId, String sourceActor, String targetActor,
                                    String messageType, Map<String, String> baggage) {
        String spanId = "flow-" + spanIdCounter.incrementAndGet();

        SpanBuilder spanBuilder = tracer.spanBuilder("yawl.actor.message.flow")
                .setAttribute("flow.id", flowId)
                .setAttribute("source.actor", sourceActor)
                .setAttribute("target.actor", targetActor)
                .setAttribute("message.type", messageType)
                .setAttribute("span.depth", 1)
                .setStartTimestamp(Instant.now());

        // Add baggage if provided
        if (baggage != null && !baggage.isEmpty()) {
            baggage.forEach((key, value) ->
                spanBuilder.setAttribute("baggage." + key, value));
        }

        Span span = spanBuilder.startSpan();
        Context context = span.makeCurrent();

        ActorSpanContext context = new ActorSpanContext(
                spanId, flowId, sourceActor, targetActor, messageType, span, context, 1);

        activeSpans.put(spanId, context);

        // Record start time
        span.setAttribute("start.time", System.currentTimeMillis());

        LOGGER.debug("Started message flow: {} from {} to {}", flowId, sourceActor, targetActor);

        return new ActorSpan(spanId, span);
    }

    /**
     * Continues an existing message flow.
     */
    public ActorSpan continueMessageFlow(String parentSpanId, String sourceActor, String targetActor,
                                        String messageType, Map<String, String> baggage) {
        ActorSpanContext parentContext = activeSpans.get(parentSpanId);
        if (parentContext == null) {
            LOGGER.warn("Parent span not found: {}", parentSpanId);
            return startMessageFlow(parentSpanId, sourceActor, targetActor, messageType, baggage);
        }

        if (parentContext.getDepth() >= maxSpanDepth) {
            LOGGER.warn("Max span depth reached, creating new branch");
            String newFlowId = parentContext.getFlowId() + "-" + spanIdCounter.incrementAndGet();
            return startMessageFlow(newFlowId, sourceActor, targetActor, messageType, baggage);
        }

        String spanId = "flow-" + spanIdCounter.incrementAndGet();
        int depth = parentContext.getDepth() + 1;

        SpanBuilder spanBuilder = tracer.spanBuilder("yawl.actor.message.flow")
                .setParent(parentContext.getContext())
                .setAttribute("flow.id", parentContext.getFlowId())
                .setAttribute("source.actor", sourceActor)
                .setAttribute("target.actor", targetActor)
                .setAttribute("message.type", messageType)
                .setAttribute("span.depth", depth)
                .setAttribute("parent.span.id", parentSpanId)
                .setStartTimestamp(Instant.now());

        // Add baggage if provided
        if (baggage != null && !baggage.isEmpty()) {
            baggage.forEach((key, value) ->
                spanBuilder.setAttribute("baggage." + key, value));
        }

        Span span = spanBuilder.startSpan();
        Context context = span.makeCurrent();

        ActorSpanContext context = new ActorSpanContext(
                spanId, parentContext.getFlowId(), sourceActor, targetActor,
                messageType, span, context, depth);

        activeSpans.put(spanId, context);

        // Link to parent
        parentContext.addChildSpan(spanId);

        LOGGER.debug("Continued message flow: {} from {} to {} (depth={})",
                parentContext.getFlowId(), sourceActor, targetActor, depth);

        return new ActorSpan(spanId, span);
    }

    /**
     * Records message processing.
     */
    public void recordMessageProcessing(String spanId, String actorId, String messageType,
                                      long processingTimeNanos, long messageSize) {
        ActorSpanContext context = activeSpans.get(spanId);
        if (context == null) {
            LOGGER.warn("Span not found: {}", spanId);
            return;
        }

        Span span = context.getSpan();

        // Record processing time
        span.setAttribute(SemanticAttributes.PROCESSING_DURATION, processingTimeNanos);

        // Record message attributes
        span.setAttribute("processed.by", actorId);
        span.setAttribute("processed.message.type", messageType);
        span.setAttribute("message.size.bytes", messageSize);

        // Add event for processing completion
        span.addEvent("message.processed",
                Map.of(
                    "processing.time.nanos", String.valueOf(processingTimeNanos),
                    "message.size.bytes", String.valueOf(messageSize),
                    "timestamp", String.valueOf(System.currentTimeMillis())
                ));

        LOGGER.debug("Recorded message processing: {} by {}", messageType, actorId);
    }

    /**
     * Records message delivery.
     */
    public void recordMessageDelivery(String spanId, String actorId, String messageType,
                                    long deliveryTimeNanos, boolean success) {
        ActorSpanContext context = activeSpans.get(spanId);
        if (context == null) {
            LOGGER.warn("Span not found: {}", spanId);
            return;
        }

        Span span = context.getSpan();

        // Record delivery attributes
        span.setAttribute("delivered.to", actorId);
        span.setAttribute("delivery.duration.nanos", deliveryTimeNanos);
        span.setAttribute("delivery.success", success);

        // Add delivery event
        span.addEvent("message.delivered",
                Map.of(
                    "delivery.time.nanos", String.valueOf(deliveryTimeNanos),
                    "success", String.valueOf(success),
                    "timestamp", String.valueOf(System.currentTimeMillis())
                ));

        if (!success) {
            span.recordException(new Exception("Message delivery failed"));
        }

        LOGGER.debug("Recorded message delivery: {} to {} (success={})", messageType, actorId, success);
    }

    /**
     * Records message handling error.
     */
    public void recordMessageError(String spanId, String actorId, String messageType,
                                  String errorType, String errorMessage, Throwable error) {
        ActorSpanContext context = activeSpans.get(spanId);
        if (context == null) {
            LOGGER.warn("Span not found: {}", spanId);
            return;
        }

        Span span = context.getSpan();

        // Set error status
        span.setStatus(StatusCode.ERROR, errorMessage);

        // Record error attributes
        span.setAttribute("error.actor", actorId);
        span.setAttribute("error.message.type", messageType);
        span.setAttribute("error.type", errorType);
        span.setAttribute("error.message", errorMessage);

        // Add error event
        span.addEvent("message.error",
                Map.of(
                    "error.type", errorType,
                    "error.message", errorMessage,
                    "timestamp", String.valueOf(System.currentTimeMillis())
                ));

        // Record exception if provided
        if (error != null) {
            span.recordException(error);
        }

        LOGGER.error("Message error in {} - {}: {}", actorId, errorType, errorMessage);
    }

    /**
     * Finishes a message flow span.
     */
    public void finishMessageFlow(String spanId, Duration totalDuration) {
        ActorSpanContext context = activeSpans.remove(spanId);
        if (context == null) {
            LOGGER.warn("Span not found for finish: {}", spanId);
            return;
        }

        Span span = context.getSpan();
        long endTime = System.currentTimeMillis();

        // Record total duration
        span.setAttribute("total.duration.millis", totalDuration.toMillis());
        span.setAttribute("end.time", endTime);

        // Check for long-running spans
        if (totalDuration.compareTo(maxSpanDuration) > 0) {
            span.setAttribute("span.warning", "long_running");
            LOGGER.warn("Long running span detected: {} took {}ms",
                    spanId, totalDuration.toMillis());
        }

        // Finish the span
        span.end();

        LOGGER.debug("Finished message flow: {} (duration={}ms)",
                context.getFlowId(), totalDuration.toMillis());
    }

    // Actor Interaction Tracing

    /**
     * Traces actor-to-actor interaction.
     */
    public void traceActorInteraction(String sourceActor, String targetActor, String interactionType,
                                    Map<String, String> attributes) {
        if (!traceActorInteractions) {
            return;
        }

        Span span = tracer.spanBuilder("yawl.actor.interaction")
                .setAttribute("source.actor", sourceActor)
                .setAttribute("target.actor", targetActor)
                .setAttribute("interaction.type", interactionType)
                .setAttribute("start.time", System.currentTimeMillis())
                .startSpan();

        // Add custom attributes
        if (attributes != null) {
            attributes.forEach((key, value) ->
                span.setAttribute("interaction." + key, value));
        }

        // Finish immediately for simple interactions
        span.end();

        LOGGER.debug("Traced actor interaction: {} -> {} ({})",
                sourceActor, targetActor, interactionType);
    }

    /**
     * Traces actor state change.
     */
    public void traceStateChange(String actorId, String oldState, String newState,
                               String reason, Map<String, String> attributes) {
        Span span = tracer.spanBuilder("yawl.actor.state.change")
                .setAttribute("actor.id", actorId)
                .setAttribute("old.state", oldState)
                .setAttribute("new.state", newState)
                .setAttribute("change.reason", reason)
                .setAttribute("timestamp", System.currentTimeMillis())
                .startSpan();

        // Add custom attributes
        if (attributes != null) {
            attributes.forEach((key, value) ->
                span.setAttribute("state." + key, value));
        }

        span.end();

        LOGGER.debug("State change: {} from {} to {} (reason: {})",
                actorId, oldState, newState, reason);
    }

    // Context Propagation Methods

    /**
     * Extracts context from headers.
     */
    public Context extractContext(Map<String, String> headers) {
        return textMapPropagator.extract(Context.current(), headers, mapGetter);
    }

    /**
     * Injects context into headers.
     */
    public void injectContext(Context context, Map<String, String> headers) {
        textMapPropagator.inject(context, headers, mapSetter);
    }

    /**
     * Gets current context.
     */
    public Context getCurrentContext() {
        return Context.current();
    }

    // Span Management Methods

    /**
     * Gets active span by ID.
     */
    public ActorSpanContext getActiveSpan(String spanId) {
        return activeSpans.get(spanId);
    }

    /**
     * Gets all active spans.
     */
    public Map<String, ActorSpanContext> getActiveSpans() {
        return new ConcurrentHashMap<>(activeSpans);
    }

    /**
     * Abandons a span (useful for abandoned operations).
     */
    public void abandonSpan(String spanId) {
        ActorSpanContext context = activeSpans.remove(spanId);
        if (context != null) {
            context.getSpan().setStatus(StatusCode.ERROR, "Abandoned");
            context.getSpan().end();
            LOGGER.warn("Abandoned span: {}", spanId);
        }
    }

    // Cleanup and Monitoring

    /**
     * Cleanup old spans (call periodically).
     */
    public void cleanupOldSpans() {
        long now = System.currentTimeMillis();
        activeSpans.entrySet().removeIf(entry -> {
            ActorSpanContext context = entry.getValue();
            long age = now - context.getSpan().getStartEpochNanos() / 1_000_000;
            if (age > maxSpanDuration.toMillis()) {
                context.getSpan().end();
                return true;
            }
            return false;
        });
    }

    /**
     * Gets span statistics.
     */
    public SpanStatistics getSpanStatistics() {
        int activeCount = activeSpans.size();
        long totalDuration = activeSpans.values().stream()
                .mapToLong(context -> System.currentTimeMillis() -
                        context.getSpan().getStartEpochNanos() / 1_000_000)
                .sum();

        return new SpanStatistics(activeCount, totalDuration);
    }

    // Supporting Classes

    /**
     * Actor span context wrapper.
     */
    public static final class ActorSpanContext {
        private final String spanId;
        private final String flowId;
        private final String sourceActor;
        private final String targetActor;
        private final String messageType;
        private final Span span;
        private final Context context;
        private final int depth;
        private final java.util.List<String> childSpans;

        public ActorSpanContext(String spanId, String flowId, String sourceActor,
                              String targetActor, String messageType,
                              Span span, Context context, int depth) {
            this.spanId = spanId;
            this.flowId = flowId;
            this.sourceActor = sourceActor;
            this.targetActor = targetActor;
            this.messageType = messageType;
            this.span = span;
            this.context = context;
            this.depth = depth;
            this.childSpans = new java.util.ArrayList<>();
        }

        // Getters
        public String getSpanId() { return spanId; }
        public String getFlowId() { return flowId; }
        public String getSourceActor() { return sourceActor; }
        public String getTargetActor() { return targetActor; }
        public String getMessageType() { return messageType; }
        public Span getSpan() { return span; }
        public Context getContext() { return context; }
        public int getDepth() { return depth; }
        public java.util.List<String> getChildSpans() { return childSpans; }

        // Child span management
        public void addChildSpan(String childSpanId) {
            childSpans.add(childSpanId);
        }
    }

    /**
     * Actor span wrapper.
     */
    public static final class ActorSpan {
        private final String spanId;
        private final Span span;

        public ActorSpan(String spanId, Span span) {
            this.spanId = spanId;
            this.span = span;
        }

        // Getters
        public String getSpanId() { return spanId; }
        public Span getSpan() { return span; }

        // Convenience methods
        public void setAttribute(String key, String value) {
            span.setAttribute(key, value);
        }

        public void addEvent(String name, Map<String, String> attributes) {
            span.addEvent(name, attributes);
        }

        public void recordException(Throwable error) {
            span.recordException(error);
        }

        public void setStatus(StatusCode code, String description) {
            span.setStatus(code, description);
        }
    }

    /**
     * Span statistics.
     */
    public static final class SpanStatistics {
        private final int activeSpanCount;
        private final long totalDurationMillis;

        public SpanStatistics(int activeSpanCount, long totalDurationMillis) {
            this.activeSpanCount = activeSpanCount;
            this.totalDurationMillis = totalDurationMillis;
        }

        // Getters
        public int getActiveSpanCount() { return activeSpanCount; }
        public long getTotalDurationMillis() { return totalDurationMillis; }
    }

    // TextMap implementations for propagation
    private static class MapTextMapGetter implements TextMapGetter<Map<String, String>> {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier.get(key);
        }
    }

    private static class MapTextMapSetter implements TextMapSetter<Map<String, String>> {
        @Override
        public void set(Map<String, String> carrier, String key, String value) {
            carrier.put(key, value);
        }
    }
}