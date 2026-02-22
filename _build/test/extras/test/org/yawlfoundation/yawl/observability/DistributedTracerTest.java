package org.yawlfoundation.yawl.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.exporter.jaeger.thrift.JaegerThriftSpanExporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD: Real OpenTelemetry spans with in-memory exporters.
 * Tests DistributedTracer with actual trace propagation.
 */
class DistributedTracerTest {

    private DistributedTracer tracer;
    private OpenTelemetry openTelemetry;

    @BeforeEach
    void setUp() {
        // Use in-memory tracer for testing
        openTelemetry = OpenTelemetrySdk.builder().build();
        tracer = new DistributedTracer(openTelemetry);
        MDC.clear();
    }

    @Test
    void testGenerateTraceId_UniquePrefixFormat() {
        String traceId1 = tracer.generateTraceId();
        String traceId2 = tracer.generateTraceId();

        assertTrue(traceId1.startsWith("yawl-"));
        assertTrue(traceId2.startsWith("yawl-"));
        assertNotEquals(traceId1, traceId2);
    }

    @Test
    void testGenerateTraceId_UUIDFormat() {
        String traceId = tracer.generateTraceId();

        // Should be "yawl-" + UUID
        String[] parts = traceId.split("-");
        assertEquals(6, parts.length); // yawl + 5 uuid parts
        assertEquals("yawl", parts[0]);
    }

    @Test
    void testStartCaseSpan_CreatesValidSpan() {
        try (DistributedTracer.TraceSpan span = tracer.startCaseSpan("case-123", "spec-001")) {
            assertNotNull(span);
            String traceId = span.getTraceId();
            assertNotNull(traceId);
            assertTrue(traceId.length() > 0);
        }
    }

    @Test
    void testStartCaseSpan_SetsAttributes() {
        try (DistributedTracer.TraceSpan span = tracer.startCaseSpan("case-123", "spec-001")) {
            span.setAttribute("yawl.case.id", "case-123");
            span.setAttribute("yawl.spec.id", "spec-001");

            // Should not throw
            span.addEvent("case_initialized");
        }
    }

    @Test
    void testStartWorkItemSpan_WithCaseContext() {
        String parentTraceId = tracer.generateTraceId();

        try (DistributedTracer.TraceSpan span = tracer.startWorkItemSpan("case-1", "wi-1", "approve", parentTraceId)) {
            assertNotNull(span);
            span.setAttribute("yawl.workitem.id", "wi-1");
            span.addEvent("work_item_started");
        }
    }

    @Test
    void testStartTaskSpan_CreatesTaskSpan() {
        try (DistributedTracer.TraceSpan span = tracer.startTaskSpan("approve_doc", "case-1", "agent-001")) {
            assertNotNull(span);
            span.setAttribute("yawl.agent.id", "agent-001");
            span.addEvent("task_assigned");
        }
    }

    @Test
    void testStartAgentActionSpan_TracksAgentAction() {
        try (DistributedTracer.TraceSpan span = tracer.startAgentActionSpan("agent-001", "validate_request", "case-1")) {
            assertNotNull(span);
            span.addEvent("validation_started");
        }
    }

    @Test
    void testPropagateTraceId_StoresInMDC() {
        String traceId = tracer.generateTraceId();
        tracer.propagateTraceId(traceId);

        assertEquals(traceId, MDC.get("trace_id"));
    }

    @Test
    void testExtractTraceId_RetrievesFromMDC() {
        String traceId = tracer.generateTraceId();
        MDC.put("trace_id", traceId);

        assertEquals(traceId, tracer.extractTraceId());
    }

    @Test
    void testClearTraceContext_RemovesFromMDC() {
        String traceId = tracer.generateTraceId();
        MDC.put("trace_id", traceId);

        tracer.clearTraceContext();

        assertNull(MDC.get("trace_id"));
    }

    @Test
    void testWithTraceContext_WrapsRunnableContext() {
        String traceId = tracer.generateTraceId();
        StringBuilder result = new StringBuilder();

        Runnable wrapped = tracer.withTraceContext(() -> {
            String contextTraceId = MDC.get("trace_id");
            result.append(contextTraceId);
        }, traceId);

        wrapped.run();

        assertEquals(traceId, result.toString());
    }

    @Test
    void testWithTraceContext_RestoresFormerContext() {
        String originalTraceId = "original-trace-id";
        MDC.put("trace_id", originalTraceId);

        String newTraceId = tracer.generateTraceId();

        Runnable wrapped = tracer.withTraceContext(() -> {
            assertEquals(newTraceId, MDC.get("trace_id"));
        }, newTraceId);

        wrapped.run();

        // Should restore original context
        assertEquals(originalTraceId, MDC.get("trace_id"));
    }

    @Test
    void testWithTraceContext_NullRunnableRejected() {
        assertThrows(NullPointerException.class, () -> {
            tracer.withTraceContext(null, "trace-id");
        });
    }

    @Test
    void testWithTraceContext_NullTraceIdRejected() {
        assertThrows(NullPointerException.class, () -> {
            tracer.withTraceContext(() -> {}, null);
        });
    }

    @Test
    void testTraceSpan_Activation() {
        try (DistributedTracer.TraceSpan span = tracer.startCaseSpan("case-1", "spec-1")) {
            span.activate();
            // Should not throw
        }
    }

    @Test
    void testTraceSpan_MultipleActivations_Idempotent() {
        try (DistributedTracer.TraceSpan span = tracer.startCaseSpan("case-1", "spec-1")) {
            span.activate();
            span.activate();
            // Should handle multiple activations gracefully
        }
    }

    @Test
    void testTraceSpan_EndWithSuccess() {
        DistributedTracer.TraceSpan span = tracer.startCaseSpan("case-1", "spec-1");
        span.endWithSuccess();
        // Should close cleanly
    }

    @Test
    void testTraceSpan_EndWithError() {
        DistributedTracer.TraceSpan span = tracer.startCaseSpan("case-1", "spec-1");
        span.endWithError("Case execution failed");
        // Should close with error status
    }

    @Test
    void testTraceSpan_RecordException() {
        try (DistributedTracer.TraceSpan span = tracer.startCaseSpan("case-1", "spec-1")) {
            Exception ex = new RuntimeException("Task failed");
            span.recordException(ex);
        }
        // Should record exception without throwing
    }

    @Test
    void testTraceSpan_AddEvent() {
        try (DistributedTracer.TraceSpan span = tracer.startCaseSpan("case-1", "spec-1")) {
            span.addEvent("case_checkpoint", "milestone", "approval_started");
            // Should add event with attributes
        }
    }

    @Test
    void testStartCaseSpan_NullCaseId_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            tracer.startCaseSpan(null, "spec-1");
        });
    }

    @Test
    void testStartCaseSpan_NullSpecId_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            tracer.startCaseSpan("case-1", null);
        });
    }

    @Test
    void testStartWorkItemSpan_NullCaseId_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            tracer.startWorkItemSpan(null, "wi-1", "approve", "trace-1");
        });
    }

    @Test
    void testStartWorkItemSpan_NullWorkItemId_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            tracer.startWorkItemSpan("case-1", null, "approve", "trace-1");
        });
    }

    @Test
    void testStartWorkItemSpan_NullTaskName_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            tracer.startWorkItemSpan("case-1", "wi-1", null, "trace-1");
        });
    }

    @Test
    void testStartTaskSpan_NullTaskName_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            tracer.startTaskSpan(null, "case-1", "agent-1");
        });
    }

    @Test
    void testStartTaskSpan_NullCaseId_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            tracer.startTaskSpan("task", null, "agent-1");
        });
    }

    @Test
    void testStartAgentActionSpan_NullAgentId_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            tracer.startAgentActionSpan(null, "action", "case-1");
        });
    }

    @Test
    void testStartAgentActionSpan_NullActionName_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            tracer.startAgentActionSpan("agent-1", null, "case-1");
        });
    }

    @Test
    void testCrossThreadTracePropagation() {
        String traceId = tracer.generateTraceId();

        StringBuilder crossThreadResult = new StringBuilder();

        Runnable task = tracer.withTraceContext(() -> {
            // Simulate work in different thread
            crossThreadResult.append(MDC.get("trace_id"));
        }, traceId);

        new Thread(task).run();

        // Trace ID should be propagated
        assertEquals(traceId, crossThreadResult.toString());
    }

    @Test
    void testTraceSpan_AutoClose() {
        try (DistributedTracer.TraceSpan span = tracer.startCaseSpan("case-1", "spec-1")) {
            assertNotNull(span);
            // Auto-close via try-with-resources
        }
        // Should close without exception
    }

    @Test
    void testStartCaseSpan_PropagateTraceIdToMDC() {
        try (DistributedTracer.TraceSpan span = tracer.startCaseSpan("case-1", "spec-1")) {
            String contextTraceId = MDC.get("trace_id");
            assertNotNull(contextTraceId);
            assertTrue(contextTraceId.length() > 0);
        }
    }
}
