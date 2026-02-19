/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Unit tests for {@link ErrorSpanHelper}.
 *
 * <p>Validates error span creation with proper YAWL-specific attributes,
 * error status, and exception recording following TPS principles (making errors visible).</p>
 */
@Tag("unit")
@Execution(ExecutionMode.SAME_THREAD)  // Uses GlobalOpenTelemetry singleton
class TestErrorSpanHelper {

    private static OpenTelemetrySdk openTelemetrySdk;
    private static Tracer tracer;
    private static InMemorySpanExporter spanExporter;

    @BeforeAll
    static void setupAll() {
        // Create an in-memory span exporter for testing
        spanExporter = new InMemorySpanExporter();

        Resource resource = Resource.getDefault();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();

        openTelemetrySdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();

        tracer = openTelemetrySdk.getTracer("test-yawl-error-spans", "6.0.0");
    }

    @AfterAll
    static void tearDownAll() {
        if (openTelemetrySdk != null) {
            CompletableResultCode shutdown = openTelemetrySdk.shutdown();
            shutdown.join(5, TimeUnit.SECONDS);
        }
    }

    // ==================== Utility Class Tests ====================

    @Test
    void instantiation_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> {
            var ctor = ErrorSpanHelper.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ctor.newInstance();
        }, "ErrorSpanHelper must not be instantiatable");
    }

    // ==================== recordErrorSpan Tests ====================

    @Test
    void recordErrorSpan_withValidInputs_createsSpanWithErrorStatus() {
        spanExporter.reset();

        Exception testException = new RuntimeException("Test error message");
        ErrorSpanHelper.recordErrorSpan("test.operation", "case-123", testException);

        // Verify span was created
        assertEquals(1, spanExporter.getSpanCount(), "Should have created one span");
    }

    @Test
    void recordErrorSpan_withNullOperation_throwsIllegalArgumentException() {
        Exception testException = new RuntimeException("Test error");
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordErrorSpan(null, "case-123", testException);
        }, "Null operation should throw IllegalArgumentException");
    }

    @Test
    void recordErrorSpan_withNullCaseId_throwsIllegalArgumentException() {
        Exception testException = new RuntimeException("Test error");
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordErrorSpan("test.operation", null, testException);
        }, "Null caseId should throw IllegalArgumentException");
    }

    @Test
    void recordErrorSpan_withNullException_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordErrorSpan("test.operation", "case-123", null);
        }, "Null exception should throw IllegalArgumentException");
    }

    @Test
    void recordErrorSpan_withExceptionWithoutMessage_usesClassName() {
        spanExporter.reset();

        Exception testException = new RuntimeException();
        ErrorSpanHelper.recordErrorSpan("test.operation", "case-456", testException);

        // Should complete without error - uses class name as message
        assertEquals(1, spanExporter.getSpanCount(), "Should have created one span");
    }

    // ==================== recordTaskErrorSpan Tests ====================

    @Test
    void recordTaskErrorSpan_withValidInputs_createsSpanWithTaskAttributes() {
        spanExporter.reset();

        Exception testException = new IllegalStateException("Task failed");
        ErrorSpanHelper.recordTaskErrorSpan("task-789", "case-123", "execute", testException);

        assertEquals(1, spanExporter.getSpanCount(), "Should have created one span");
    }

    @Test
    void recordTaskErrorSpan_withNullTaskId_throwsIllegalArgumentException() {
        Exception testException = new RuntimeException("Test");
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordTaskErrorSpan(null, "case-123", "execute", testException);
        }, "Null taskId should throw IllegalArgumentException");
    }

    @Test
    void recordTaskErrorSpan_withNullOperation_throwsIllegalArgumentException() {
        Exception testException = new RuntimeException("Test");
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordTaskErrorSpan("task-789", "case-123", null, testException);
        }, "Null operation should throw IllegalArgumentException");
    }

    // ==================== recordCaseErrorSpan Tests ====================

    @Test
    void recordCaseErrorSpan_withValidInputs_createsSpanWithCaseAttributes() {
        spanExporter.reset();

        Exception testException = new RuntimeException("Case initialization failed");
        ErrorSpanHelper.recordCaseErrorSpan("case-999", "initialize", testException);

        assertEquals(1, spanExporter.getSpanCount(), "Should have created one span");
    }

    @Test
    void recordCaseErrorSpan_withNullCaseId_throwsIllegalArgumentException() {
        Exception testException = new RuntimeException("Test");
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordCaseErrorSpan(null, "cancel", testException);
        }, "Null caseId should throw IllegalArgumentException");
    }

    @Test
    void recordCaseErrorSpan_withNullOperation_throwsIllegalArgumentException() {
        Exception testException = new RuntimeException("Test");
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordCaseErrorSpan("case-999", null, testException);
        }, "Null operation should throw IllegalArgumentException");
    }

    // ==================== recordSpecificationErrorSpan Tests ====================

    @Test
    void recordSpecificationErrorSpan_withValidInputs_createsSpanWithSpecAttributes() {
        spanExporter.reset();

        Exception testException = new RuntimeException("Specification parsing failed");
        ErrorSpanHelper.recordSpecificationErrorSpan("spec-111", "case-222", "parse", testException);

        assertEquals(1, spanExporter.getSpanCount(), "Should have created one span");
    }

    @Test
    void recordSpecificationErrorSpan_withNullCaseId_stillCreatesSpan() {
        spanExporter.reset();

        Exception testException = new RuntimeException("Spec validation failed");
        // caseId can be null if error occurs before case creation
        ErrorSpanHelper.recordSpecificationErrorSpan("spec-111", null, "validate", testException);

        assertEquals(1, spanExporter.getSpanCount(), "Should have created one span even with null caseId");
    }

    @Test
    void recordSpecificationErrorSpan_withEmptyCaseId_stillCreatesSpan() {
        spanExporter.reset();

        Exception testException = new RuntimeException("Spec load failed");
        // Empty caseId should be treated same as null
        ErrorSpanHelper.recordSpecificationErrorSpan("spec-111", "", "load", testException);

        assertEquals(1, spanExporter.getSpanCount(), "Should have created one span even with empty caseId");
    }

    @Test
    void recordSpecificationErrorSpan_withNullSpecId_throwsIllegalArgumentException() {
        Exception testException = new RuntimeException("Test");
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordSpecificationErrorSpan(null, "case-222", "validate", testException);
        }, "Null specId should throw IllegalArgumentException");
    }

    // ==================== recordErrorOnCurrentSpan Tests ====================

    @Test
    void recordErrorOnCurrentSpan_withCurrentSpan_recordsError() {
        spanExporter.reset();

        Span parentSpan = tracer.spanBuilder("parent.operation").startSpan();
        parentSpan.makeCurrent();

        Exception testException = new RuntimeException("Nested error");
        ErrorSpanHelper.recordErrorOnCurrentSpan("nested.operation", testException);

        parentSpan.end();

        assertEquals(1, spanExporter.getSpanCount(), "Should have the parent span");
    }

    @Test
    void recordErrorOnCurrentSpan_withoutCurrentSpan_doesNotThrow() {
        // When there's no current span, should not throw
        Exception testException = new RuntimeException("Test error");
        assertDoesNotThrow(() -> {
            ErrorSpanHelper.recordErrorOnCurrentSpan("no.span", testException);
        }, "Should not throw when no current span exists");
    }

    // ==================== createErrorSpan Tests ====================

    @Test
    void createErrorSpan_withValidInputs_returnsStartedSpan() {
        spanExporter.reset();

        Exception testException = new RuntimeException("Manual span error");
        Span span = ErrorSpanHelper.createErrorSpan("manual.error.span", "case-manual", testException);

        assertNotNull(span, "Should return a non-null span");
        assertTrue(span.isRecording(), "Span should be recording");

        span.end(); // Caller must end the span
    }

    @Test
    void createErrorSpan_withNullSpanName_throwsIllegalArgumentException() {
        Exception testException = new RuntimeException("Test");
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.createErrorSpan(null, "case-123", testException);
        }, "Null spanName should throw IllegalArgumentException");
    }

    // ==================== recordErrorSpanWithAttributes Tests ====================

    @Test
    void recordErrorSpanWithAttributes_withValidInputs_includesCustomAttributes() {
        spanExporter.reset();

        Exception testException = new RuntimeException("Error with extra context");
        Attributes extraAttributes = Attributes.builder()
                .put("custom.key", "custom.value")
                .put("custom.number", 42L)
                .build();

        ErrorSpanHelper.recordErrorSpanWithAttributes("custom.operation", "case-custom",
                testException, extraAttributes);

        assertEquals(1, spanExporter.getSpanCount(), "Should have created one span");
    }

    @Test
    void recordErrorSpanWithAttributes_withNullAttributes_throwsIllegalArgumentException() {
        Exception testException = new RuntimeException("Test");
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordErrorSpanWithAttributes("test.op", "case-123",
                    testException, null);
        }, "Null additionalAttributes should throw IllegalArgumentException");
    }

    // ==================== executeWithErrorSpan (Runnable) Tests ====================

    @Test
    void executeWithErrorSpan_runnable_successfulOperation_doesNotCreateErrorSpan() throws Exception {
        spanExporter.reset();

        // Successful operation should not create error span
        ErrorSpanHelper.executeWithErrorSpan("successful.op", "case-success", () -> {
            // No exception - operation succeeds
        });

        // No error span should be created for successful operations
        assertEquals(0, spanExporter.getSpanCount(), "Should not create span for successful operation");
    }

    @Test
    void executeWithErrorSpan_runnable_failedOperation_createsErrorSpanAndRethrows() {
        spanExporter.reset();

        Exception testException = new RuntimeException("Operation failed");

        Exception thrown = assertThrows(Exception.class, () -> {
            ErrorSpanHelper.executeWithErrorSpan("failed.op", "case-fail", () -> {
                throw testException;
            });
        }, "Should rethrow the exception");

        assertSame(testException, thrown, "Should rethrow the same exception");
        assertEquals(1, spanExporter.getSpanCount(), "Should create error span for failed operation");
    }

    @Test
    void executeWithErrorSpan_runnable_withNullRunnable_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.executeWithErrorSpan("test.op", "case-123", (ErrorSpanHelper.ThrowingRunnable) null);
        }, "Null runnable should throw IllegalArgumentException");
    }

    // ==================== executeWithErrorSpan (Supplier) Tests ====================

    @Test
    void executeWithErrorSpan_supplier_successfulOperation_returnsResult() throws Exception {
        spanExporter.reset();

        String result = ErrorSpanHelper.executeWithErrorSpan("supplier.op", "case-supply",
                () -> "success-result");

        assertEquals("success-result", result, "Should return the supplier result");
        assertEquals(0, spanExporter.getSpanCount(), "Should not create span for successful operation");
    }

    @Test
    void executeWithErrorSpan_supplier_failedOperation_createsErrorSpanAndRethrows() {
        spanExporter.reset();

        Exception testException = new RuntimeException("Supplier failed");

        Exception thrown = assertThrows(Exception.class, () -> {
            ErrorSpanHelper.executeWithErrorSpan("supplier.fail", "case-fail", () -> {
                throw testException;
            });
        }, "Should rethrow the exception");

        assertSame(testException, thrown, "Should rethrow the same exception");
        assertEquals(1, spanExporter.getSpanCount(), "Should create error span for failed operation");
    }

    @Test
    void executeWithErrorSpan_supplier_withNullSupplier_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.executeWithErrorSpan("test.op", "case-123", (ErrorSpanHelper.ThrowingSupplier<String>) null);
        }, "Null supplier should throw IllegalArgumentException");
    }

    // ==================== Edge Cases ====================

    @Test
    void recordErrorSpan_withNestedException_recordsCause() {
        spanExporter.reset();

        Exception cause = new NullPointerException("Root cause");
        Exception testException = new RuntimeException("Wrapper exception", cause);

        ErrorSpanHelper.recordErrorSpan("nested.exception", "case-nested", testException);

        assertEquals(1, spanExporter.getSpanCount(), "Should create span for nested exception");
    }

    @Test
    void multipleErrorSpans_inSequence_allCreatedSuccessfully() {
        spanExporter.reset();

        for (int i = 0; i < 5; i++) {
            Exception ex = new RuntimeException("Error " + i);
            ErrorSpanHelper.recordErrorSpan("batch.operation." + i, "case-batch-" + i, ex);
        }

        assertEquals(5, spanExporter.getSpanCount(), "Should create all 5 error spans");
    }

    // ==================== In-Memory Span Exporter ====================

    /**
     * Simple in-memory span exporter for testing.
     */
    private static class InMemorySpanExporter implements SpanExporter {

        private int spanCount = 0;

        @Override
        public CompletableResultCode export(java.util.Collection<SpanData> spans) {
            spanCount += spans.size();
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

        public int getSpanCount() {
            return spanCount;
        }

        public void reset() {
            spanCount = 0;
        }
    }
}
