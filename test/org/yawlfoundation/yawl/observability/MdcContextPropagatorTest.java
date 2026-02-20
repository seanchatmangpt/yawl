package org.yawlfoundation.yawl.observability;

import io.opentelemetry.context.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for MdcContextPropagator.
 * Verifies OpenTelemetry TextMapPropagator integration with MDC.
 *
 * @since 6.0.0
 */
@DisplayName("MdcContextPropagator Tests")
@ExtendWith({SpringExtension.class, MockitoExtension.class})
class MdcContextPropagatorTest {

    private MdcContextPropagator propagator;
    private Map<String, String> carrier;

    @BeforeEach
    void setUp() {
        MDC.clear();
        propagator = new MdcContextPropagator();
        carrier = new HashMap<>();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("Should return correct propagator fields")
    void testFields() {
        assertNotNull(propagator.fields(), "Fields should not be null");
        assertTrue(propagator.fields().contains(MdcContextPropagator.TRACE_ID_KEY), "Should contain trace_id");
        assertTrue(propagator.fields().contains(MdcContextPropagator.SPAN_ID_KEY), "Should contain span_id");
        assertTrue(propagator.fields().contains(MdcContextPropagator.TRACE_FLAGS_KEY), "Should contain trace_flags");
        assertTrue(propagator.fields().contains(MdcContextPropagator.TRACEPARENT_KEY), "Should contain traceparent");
    }

    @Test
    @DisplayName("Should inject trace context from MDC to carrier")
    void testInject() {
        String traceId = "abc123def456";
        String spanId = "span789";

        MDC.put(MdcContextPropagator.TRACE_ID_KEY, traceId);
        MDC.put(MdcContextPropagator.SPAN_ID_KEY, spanId);

        propagator.inject(Context.current(), carrier, carrier::put);

        assertEquals(traceId, carrier.get(MdcContextPropagator.TRACE_ID_KEY), "Should inject trace ID");
        assertEquals(spanId, carrier.get(MdcContextPropagator.SPAN_ID_KEY), "Should inject span ID");
    }

    @Test
    @DisplayName("Should extract W3C traceparent from carrier")
    void testExtractW3CTraceparent() {
        String traceparent = "00-abc123def456-span789-01";
        carrier.put(MdcContextPropagator.TRACEPARENT_KEY, traceparent);

        propagator.extract(Context.current(), carrier, carrier::get);

        assertEquals("abc123def456", MDC.get(MdcContextPropagator.TRACE_ID_KEY), "Should extract trace ID from traceparent");
        assertEquals("span789", MDC.get(MdcContextPropagator.SPAN_ID_KEY), "Should extract span ID from traceparent");
        assertEquals("01", MDC.get(MdcContextPropagator.TRACE_FLAGS_KEY), "Should extract trace flags");
    }

    @Test
    @DisplayName("Should extract individual MDC keys from carrier")
    void testExtractIndividualKeys() {
        String traceId = "abc123";
        String spanId = "span456";
        String flags = "01";

        carrier.put(MdcContextPropagator.TRACE_ID_KEY, traceId);
        carrier.put(MdcContextPropagator.SPAN_ID_KEY, spanId);
        carrier.put(MdcContextPropagator.TRACE_FLAGS_KEY, flags);

        propagator.extract(Context.current(), carrier, carrier::get);

        assertEquals(traceId, MDC.get(MdcContextPropagator.TRACE_ID_KEY), "Should extract trace ID");
        assertEquals(spanId, MDC.get(MdcContextPropagator.SPAN_ID_KEY), "Should extract span ID");
        assertEquals(flags, MDC.get(MdcContextPropagator.TRACE_FLAGS_KEY), "Should extract flags");
    }

    @Test
    @DisplayName("Should handle empty carrier gracefully")
    void testExtractFromEmptyCarrier() {
        propagator.extract(Context.current(), carrier, carrier::get);

        // Should not throw and MDC should remain empty
        assertEquals(0, MDC.getCopyOfContextMap().size(), "MDC should be empty");
    }

    @Test
    @DisplayName("Should handle null context gracefully")
    void testInjectWithNullContext() {
        propagator.inject(null, carrier, carrier::put);

        // Should not throw and carrier should remain empty
        assertTrue(carrier.isEmpty(), "Carrier should remain empty");
    }

    @Test
    @DisplayName("Should construct W3C traceparent when extracting individual keys")
    void testConstructTraceparentFromIndividualKeys() {
        String traceId = "abc123";
        String spanId = "span456";

        carrier.put(MdcContextPropagator.TRACE_ID_KEY, traceId);
        carrier.put(MdcContextPropagator.SPAN_ID_KEY, spanId);

        propagator.extract(Context.current(), carrier, carrier::get);

        String traceparent = MDC.get(MdcContextPropagator.TRACEPARENT_KEY);
        assertNotNull(traceparent, "Should construct traceparent");
        assertTrue(traceparent.startsWith("00-"), "Should start with 00");
        assertTrue(traceparent.contains(traceId), "Should contain trace ID");
        assertTrue(traceparent.contains(spanId), "Should contain span ID");
    }

    @Test
    @DisplayName("Should handle malformed W3C traceparent")
    void testHandleMalformedTraceparent() {
        carrier.put(MdcContextPropagator.TRACEPARENT_KEY, "invalid-format");

        propagator.extract(Context.current(), carrier, carrier::get);

        // Should fall back to extracting individual keys (none present in this case)
        assertEquals(0, MDC.getCopyOfContextMap().size(), "Should handle malformed traceparent gracefully");
    }

    @Test
    @DisplayName("Should fallback to individual keys when traceparent is incomplete")
    void testFallbackToIndividualKeys() {
        String traceId = "abc123";
        String spanId = "span456";

        // Set traceparent with insufficient parts
        carrier.put(MdcContextPropagator.TRACEPARENT_KEY, "00-abc");
        // Provide individual keys as fallback
        carrier.put(MdcContextPropagator.TRACE_ID_KEY, traceId);
        carrier.put(MdcContextPropagator.SPAN_ID_KEY, spanId);

        propagator.extract(Context.current(), carrier, carrier::get);

        // Should fall back to individual keys
        assertEquals(traceId, MDC.get(MdcContextPropagator.TRACE_ID_KEY), "Should extract trace ID from individual key");
        assertEquals(spanId, MDC.get(MdcContextPropagator.SPAN_ID_KEY), "Should extract span ID from individual key");
    }

    @Test
    @DisplayName("Should have meaningful toString representation")
    void testToString() {
        String name = propagator.toString();
        assertNotNull(name, "toString should not return null");
        assertTrue(name.contains("MdcContextPropagator") || name.contains("mdc"), "Should identify propagator type");
    }

    @Test
    @DisplayName("Should default to flags 00 when not provided")
    void testDefaultTraceFlags() {
        String traceId = "abc123";
        carrier.put(MdcContextPropagator.TRACE_ID_KEY, traceId);

        propagator.extract(Context.current(), carrier, carrier::get);

        assertEquals("00", MDC.get(MdcContextPropagator.TRACE_FLAGS_KEY), "Should default to 00 flags");
    }
}
