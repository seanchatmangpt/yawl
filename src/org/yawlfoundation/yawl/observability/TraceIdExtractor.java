package org.yawlfoundation.yawl.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.TraceContext;

import java.util.Objects;

/**
 * Extracts and propagates trace IDs in W3C TraceContext format to MDC (Mapped Diagnostic Context).
 *
 * This component ensures that trace IDs are always available in logs, following W3C standards:
 * - traceparent header format: 00-trace-id-span-id-flags
 * - MDC keys: trace_id, span_id, trace_flags
 *
 * Thread-safe and designed for high-throughput environments with virtual threads.
 *
 * @since 6.0.0
 */
public class TraceIdExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TraceIdExtractor.class);

    public static final String MDC_TRACE_ID = "trace_id";
    public static final String MDC_SPAN_ID = "span_id";
    public static final String MDC_TRACE_FLAGS = "trace_flags";
    public static final String MDC_W3C_TRACEPARENT = "traceparent";

    private final CurrentTraceContext currentTraceContext;

    public TraceIdExtractor(CurrentTraceContext currentTraceContext) {
        this.currentTraceContext = Objects.requireNonNull(currentTraceContext);
    }

    /**
     * Extracts the current trace ID from Sleuth's TraceContext and puts it in MDC.
     * Returns the trace ID for use in log messages or error handling.
     *
     * @return the current trace ID, or null if no trace is active
     */
    public String extractAndPropagateTraceId() {
        TraceContext context = currentTraceContext.context();
        if (context == null) {
            return null;
        }

        String traceId = context.traceId();
        String spanId = context.spanId();

        // Put trace context in MDC for logging
        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_SPAN_ID, spanId);

        // Create W3C traceparent format: 00-trace-id-span-id-flags
        String traceparent = String.format("00-%s-%s-%s",
                traceId,
                spanId,
                context.sampled() ? "01" : "00");
        MDC.put(MDC_W3C_TRACEPARENT, traceparent);

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Extracted trace context: traceId={}, spanId={}, sampled={}",
                    traceId, spanId, context.sampled());
        }

        return traceId;
    }

    /**
     * Extracts just the trace ID without modifying MDC.
     *
     * @return the current trace ID, or null if no trace is active
     */
    public String getTraceId() {
        TraceContext context = currentTraceContext.context();
        return context != null ? context.traceId() : null;
    }

    /**
     * Extracts just the span ID without modifying MDC.
     *
     * @return the current span ID, or null if no trace is active
     */
    public String getSpanId() {
        TraceContext context = currentTraceContext.context();
        return context != null ? context.spanId() : null;
    }

    /**
     * Creates a W3C traceparent header value for the current trace context.
     *
     * @return W3C traceparent format string (00-trace-id-span-id-flags), or null if no active trace
     */
    public String getW3CTraceparent() {
        TraceContext context = currentTraceContext.context();
        if (context == null) {
            return null;
        }

        return String.format("00-%s-%s-%s",
                context.traceId(),
                context.spanId(),
                context.sampled() ? "01" : "00");
    }

    /**
     * Clears all trace context from MDC.
     * Should be called when a trace scope ends to prevent context leakage.
     */
    public void clearTraceContext() {
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_SPAN_ID);
        MDC.remove(MDC_TRACE_FLAGS);
        MDC.remove(MDC_W3C_TRACEPARENT);
    }

    /**
     * Propagates the given trace ID to MDC manually.
     * Useful for correlation when Sleuth's automatic instrumentation is not active.
     *
     * @param traceId the trace ID to propagate (non-null)
     * @param spanId  the span ID (may be null)
     */
    public void propagateManualTrace(String traceId, String spanId) {
        Objects.requireNonNull(traceId);
        MDC.put(MDC_TRACE_ID, traceId);
        if (spanId != null) {
            MDC.put(MDC_SPAN_ID, spanId);
        }
        MDC.put(MDC_W3C_TRACEPARENT, String.format("00-%s-%s-00", traceId, spanId != null ? spanId : "0000000000000000"));
    }

    /**
     * Checks if a trace is currently active.
     *
     * @return true if CurrentTraceContext has an active trace, false otherwise
     */
    public boolean hasActiveTrace() {
        return currentTraceContext.context() != null;
    }
}
