package org.yawlfoundation.yawl.observability;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * OpenTelemetry TextMapPropagator that propagates trace context through MDC (Mapped Diagnostic Context).
 *
 * This propagator ensures trace context is preserved across thread boundaries, including virtual threads.
 * It integrates with SLF4J MDC to maintain correlation IDs throughout the execution lifecycle.
 *
 * Thread-safe and optimized for high-throughput, distributed tracing scenarios.
 *
 * Propagated keys:
 * - trace_id: OpenTelemetry trace ID
 * - span_id: OpenTelemetry span ID
 * - trace_flags: OpenTelemetry trace flags (sampled/not sampled)
 * - traceparent: W3C TraceContext format (00-trace-id-span-id-flags)
 *
 * @since 6.0.0
 */
public class MdcContextPropagator implements TextMapPropagator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MdcContextPropagator.class);

    public static final String TRACE_ID_KEY = "trace_id";
    public static final String SPAN_ID_KEY = "span_id";
    public static final String TRACE_FLAGS_KEY = "trace_flags";
    public static final String TRACEPARENT_KEY = "traceparent";

    private static final Set<String> FIELDS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(TRACE_ID_KEY, SPAN_ID_KEY, TRACE_FLAGS_KEY, TRACEPARENT_KEY))
    );

    @Override
    public Collection<String> fields() {
        return FIELDS;
    }

    /**
     * Injects trace context from OpenTelemetry Context into the MDC.
     *
     * @param context the OpenTelemetry Context containing trace information
     * @param carrier the carrier to inject into (MDC in this case)
     */
    @Override
    public <C> void inject(Context context, C carrier, Setter<? super C> setter) {
        if (context == null) {
            return;
        }

        // Extract trace context from OpenTelemetry span
        String traceId = MDC.get(TRACE_ID_KEY);
        String spanId = MDC.get(SPAN_ID_KEY);
        String traceFlags = MDC.get(TRACE_FLAGS_KEY);

        if (traceId != null) {
            setter.set(carrier, TRACE_ID_KEY, traceId);
        }
        if (spanId != null) {
            setter.set(carrier, SPAN_ID_KEY, spanId);
        }
        if (traceFlags != null) {
            setter.set(carrier, TRACE_FLAGS_KEY, traceFlags);
        }

        // Also propagate W3C traceparent format
        String traceparent = MDC.get(TRACEPARENT_KEY);
        if (traceparent != null) {
            setter.set(carrier, TRACEPARENT_KEY, traceparent);
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Injected MDC context into carrier: traceId={}, spanId={}", traceId, spanId);
        }
    }

    /**
     * Extracts trace context from a carrier and puts it in MDC.
     *
     * This method is called when receiving distributed trace context from external sources
     * (e.g., HTTP headers, message queue headers). It extracts the context and stores it in MDC
     * so all subsequent logging includes the correlation ID.
     *
     * @param context the OpenTelemetry Context to extract into
     * @param carrier the carrier to extract from (typically headers or message metadata)
     * @return the Context with extracted trace context
     */
    @Override
    public <C> Context extract(Context context, C carrier, Getter<? super C> getter) {
        if (context == null) {
            return Context.current();
        }

        // Extract W3C traceparent first (preferred format)
        String traceparent = getter.get(carrier, TRACEPARENT_KEY);
        if (traceparent != null && traceparent.startsWith("00-")) {
            // Parse W3C traceparent: 00-trace-id-span-id-flags
            String[] parts = traceparent.split("-");
            if (parts.length >= 4) {
                String traceId = parts[1];
                String spanId = parts[2];
                String flags = parts[3];

                MDC.put(TRACE_ID_KEY, traceId);
                MDC.put(SPAN_ID_KEY, spanId);
                MDC.put(TRACE_FLAGS_KEY, flags);
                MDC.put(TRACEPARENT_KEY, traceparent);

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Extracted W3C traceparent: traceId={}, spanId={}", traceId, spanId);
                }
                return context;
            }
        }

        // Fallback: extract individual keys
        String traceId = getter.get(carrier, TRACE_ID_KEY);
        String spanId = getter.get(carrier, SPAN_ID_KEY);
        String traceFlags = getter.get(carrier, TRACE_FLAGS_KEY);

        if (traceId != null) {
            MDC.put(TRACE_ID_KEY, traceId);
            if (spanId != null) {
                MDC.put(SPAN_ID_KEY, spanId);
            }
            if (traceFlags != null) {
                MDC.put(TRACE_FLAGS_KEY, traceFlags);
            } else {
                MDC.put(TRACE_FLAGS_KEY, "00");
            }

            // Construct W3C traceparent format
            String parentSpanId = spanId != null ? spanId : "0000000000000000";
            String parentFlags = traceFlags != null ? traceFlags : "00";
            String reconstructedTraceparent = String.format("00-%s-%s-%s", traceId, parentSpanId, parentFlags);
            MDC.put(TRACEPARENT_KEY, reconstructedTraceparent);

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Extracted individual MDC keys: traceId={}, spanId={}, flags={}",
                        traceId, spanId, traceFlags);
            }
        }

        return context;
    }

    /**
     * Gets the propagator name for identification in logs.
     *
     * @return the name "mdc-context-propagator"
     */
    @Override
    public String toString() {
        return "MdcContextPropagator";
    }
}
