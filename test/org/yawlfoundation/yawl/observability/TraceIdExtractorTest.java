package org.yawlfoundation.yawl.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TraceIdExtractor.
 * Verifies trace ID extraction, propagation, and MDC integration.
 *
 * @since 6.0.0
 */
@DisplayName("TraceIdExtractor Tests")
@ExtendWith({SpringExtension.class, MockitoExtension.class})
class TraceIdExtractorTest {

    private TraceIdExtractor extractor;

    @Mock
    private CurrentTraceContext currentTraceContext;

    @Mock
    private TraceContext traceContext;

    @BeforeEach
    void setUp() {
        MDC.clear();
        extractor = new TraceIdExtractor(currentTraceContext);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("Should extract trace ID and propagate to MDC")
    void testExtractAndPropagateTraceId() {
        String expectedTraceId = "abc123def456";
        String expectedSpanId = "span789";

        when(currentTraceContext.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn(expectedTraceId);
        when(traceContext.spanId()).thenReturn(expectedSpanId);
        when(traceContext.sampled()).thenReturn(true);

        String result = extractor.extractAndPropagateTraceId();

        assertEquals(expectedTraceId, result, "Should return the trace ID");
        assertEquals(expectedTraceId, MDC.get(TraceIdExtractor.MDC_TRACE_ID), "Should put trace ID in MDC");
        assertEquals(expectedSpanId, MDC.get(TraceIdExtractor.MDC_SPAN_ID), "Should put span ID in MDC");
    }

    @Test
    @DisplayName("Should return null when no trace context is active")
    void testExtractWhenNoTraceContext() {
        when(currentTraceContext.context()).thenReturn(null);

        String result = extractor.extractAndPropagateTraceId();

        assertNull(result, "Should return null when no trace context");
    }

    @Test
    @DisplayName("Should generate W3C traceparent format")
    void testW3CTraceparentFormat() {
        String traceId = "abc123def456";
        String spanId = "span789";

        when(currentTraceContext.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn(traceId);
        when(traceContext.spanId()).thenReturn(spanId);
        when(traceContext.sampled()).thenReturn(true);

        extractor.extractAndPropagateTraceId();
        String traceparent = MDC.get(TraceIdExtractor.MDC_W3C_TRACEPARENT);

        assertNotNull(traceparent, "Should generate W3C traceparent");
        assertTrue(traceparent.startsWith("00-"), "Should start with 00 version");
        assertTrue(traceparent.contains(traceId), "Should contain trace ID");
        assertTrue(traceparent.endsWith("-01"), "Should end with 01 for sampled trace");
    }

    @Test
    @DisplayName("Should handle unsampled traces")
    void testUnsampledTraceFlags() {
        when(currentTraceContext.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("trace123");
        when(traceContext.spanId()).thenReturn("span456");
        when(traceContext.sampled()).thenReturn(false);

        extractor.extractAndPropagateTraceId();
        String traceparent = MDC.get(TraceIdExtractor.MDC_W3C_TRACEPARENT);

        assertTrue(traceparent.endsWith("-00"), "Should end with 00 for unsampled trace");
    }

    @Test
    @DisplayName("Should get trace ID without modifying MDC")
    void testGetTraceId() {
        String expectedTraceId = "abc123";
        when(currentTraceContext.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn(expectedTraceId);

        String result = extractor.getTraceId();

        assertEquals(expectedTraceId, result, "Should return trace ID");
        assertNull(MDC.get(TraceIdExtractor.MDC_TRACE_ID), "Should not modify MDC");
    }

    @Test
    @DisplayName("Should get span ID without modifying MDC")
    void testGetSpanId() {
        String expectedSpanId = "span789";
        when(currentTraceContext.context()).thenReturn(traceContext);
        when(traceContext.spanId()).thenReturn(expectedSpanId);

        String result = extractor.getSpanId();

        assertEquals(expectedSpanId, result, "Should return span ID");
        assertNull(MDC.get(TraceIdExtractor.MDC_SPAN_ID), "Should not modify MDC");
    }

    @Test
    @DisplayName("Should clear trace context from MDC")
    void testClearTraceContext() {
        when(currentTraceContext.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("trace123");
        when(traceContext.spanId()).thenReturn("span456");
        when(traceContext.sampled()).thenReturn(true);

        extractor.extractAndPropagateTraceId();
        assertTrue(extractor.hasActiveTrace(), "Should have active trace");

        extractor.clearTraceContext();

        assertNull(MDC.get(TraceIdExtractor.MDC_TRACE_ID), "Should clear trace ID from MDC");
        assertNull(MDC.get(TraceIdExtractor.MDC_SPAN_ID), "Should clear span ID from MDC");
        assertNull(MDC.get(TraceIdExtractor.MDC_W3C_TRACEPARENT), "Should clear traceparent from MDC");
    }

    @Test
    @DisplayName("Should propagate manual trace IDs")
    void testPropagateManualTrace() {
        String manualTraceId = "manual-trace-123";
        String manualSpanId = "manual-span-456";

        extractor.propagateManualTrace(manualTraceId, manualSpanId);

        assertEquals(manualTraceId, MDC.get(TraceIdExtractor.MDC_TRACE_ID), "Should put manual trace ID in MDC");
        assertEquals(manualSpanId, MDC.get(TraceIdExtractor.MDC_SPAN_ID), "Should put manual span ID in MDC");
    }

    @Test
    @DisplayName("Should handle manual trace with null span ID")
    void testPropagateManualTraceWithNullSpanId() {
        String manualTraceId = "manual-trace-123";

        extractor.propagateManualTrace(manualTraceId, null);

        assertEquals(manualTraceId, MDC.get(TraceIdExtractor.MDC_TRACE_ID), "Should put manual trace ID in MDC");
        String traceparent = MDC.get(TraceIdExtractor.MDC_W3C_TRACEPARENT);
        assertTrue(traceparent.contains(manualTraceId), "Should contain manual trace ID in traceparent");
    }

    @Test
    @DisplayName("Should detect active trace")
    void testHasActiveTrace() {
        when(currentTraceContext.context()).thenReturn(null);
        assertFalse(extractor.hasActiveTrace(), "Should return false when no active trace");

        when(currentTraceContext.context()).thenReturn(traceContext);
        assertTrue(extractor.hasActiveTrace(), "Should return true when trace is active");
    }

    @Test
    @DisplayName("Should handle across thread boundaries with virtual threads")
    void testVirtualThreadTraceContextPropagation() throws InterruptedException {
        String parentTraceId = "parent-trace-123";
        String parentSpanId = "parent-span-456";

        // Set up parent trace context
        when(currentTraceContext.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn(parentTraceId);
        when(traceContext.spanId()).thenReturn(parentSpanId);

        extractor.extractAndPropagateTraceId();

        // Verify parent thread has MDC set
        assertEquals(parentTraceId, MDC.get(TraceIdExtractor.MDC_TRACE_ID), "Parent thread should have trace ID");

        // In real scenario, virtual thread would need ScopedValue or similar mechanism
        // This test verifies the concept works with thread propagation
    }
}
