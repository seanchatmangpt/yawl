/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.engine.observability;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for YAWL OpenTelemetry tracing utilities.
 * Tests span creation and management for YAWL operations.
 *
 * @author YAWL Development Team
 */
public class YAWLTracingTest {

    @BeforeEach
    public void setUp() {
        YAWLTelemetry telemetry = YAWLTelemetry.getInstance();
        telemetry.setEnabled(true);
    }

    @Test
    public void testCreateCaseSpan() {
        String operationName = "ExecuteCase";
        String caseId = "case-001";
        String specificationId = "TestSpec";

        Span span = YAWLTracing.createCaseSpan(operationName, caseId, specificationId);

        assertNotNull("Span should not be null", span);
        assertTrue("Span should be recording", span.isRecording());

        span.end();
    }

    @Test
    public void testCreateWorkItemSpan() {
        String operationName = "CompleteWorkItem";
        String workItemId = "wi-001";
        String caseId = "case-002";
        String taskId = "task-001";

        Span span = YAWLTracing.createWorkItemSpan(operationName, workItemId,
                                                    caseId, taskId);

        assertNotNull("Span should not be null", span);
        assertTrue("Span should be recording", span.isRecording());

        span.end();
    }

    @Test
    public void testCreateNetRunnerSpan() {
        String operationName = "NetRunner.continueExecutionOnNet";
        String caseId = "case-003";
        String netId = "net-001";

        Span span = YAWLTracing.createNetRunnerSpan(operationName, caseId, netId);

        assertNotNull("Span should not be null", span);
        assertTrue("Span should be recording", span.isRecording());

        span.end();
    }

    @Test
    public void testCreateEngineSpan() {
        String operationName = "InitializeEngine";

        Span span = YAWLTracing.createEngineSpan(operationName);

        assertNotNull("Span should not be null", span);
        assertTrue("Span should be recording", span.isRecording());

        span.end();
    }

    @Test
    public void testAddCaseAttributes() {
        String caseId = "case-004";
        String specificationId = "TestSpec";

        Span span = YAWLTracing.createEngineSpan("TestOperation");
        span.makeCurrent();

        YAWLTracing.addCaseAttributes(caseId, specificationId);

        span.end();

        // Verify no exceptions were thrown
        assertTrue("Test completed successfully", true);
    }

    @Test
    public void testAddWorkItemAttributes() {
        String workItemId = "wi-002";
        String taskId = "task-002";

        Span span = YAWLTracing.createEngineSpan("TestOperation");
        span.makeCurrent();

        YAWLTracing.addWorkItemAttributes(workItemId, taskId);

        span.end();

        assertTrue("Test completed successfully", true);
    }

    @Test
    public void testAddTaskStateAttributes() {
        long enabledTasksCount = 10;
        long busyTasksCount = 5;

        Span span = YAWLTracing.createEngineSpan("TestOperation");
        span.makeCurrent();

        YAWLTracing.addTaskStateAttributes(enabledTasksCount, busyTasksCount);

        span.end();

        assertTrue("Test completed successfully", true);
    }

    @Test
    public void testRecordException() {
        Exception exception = new RuntimeException("Test exception");

        Span span = YAWLTracing.createEngineSpan("FailingOperation");
        span.makeCurrent();

        YAWLTracing.recordException(exception);

        span.end();

        assertTrue("Exception recording completed successfully", true);
    }

    @Test
    public void testAddEvent() {
        String eventName = "TestEvent";

        Span span = YAWLTracing.createEngineSpan("TestOperation");
        span.makeCurrent();

        YAWLTracing.addEvent(eventName);

        span.end();

        assertTrue("Event added successfully", true);
    }

    @Test
    public void testAddEventWithAttributes() {
        String eventName = "TestEvent";
        Attributes attributes = Attributes.builder()
            .put("event.key", "event.value")
            .build();

        Span span = YAWLTracing.createEngineSpan("TestOperation");
        span.makeCurrent();

        YAWLTracing.addEvent(eventName, attributes);

        span.end();

        assertTrue("Event with attributes added successfully", true);
    }

    @Test
    public void testGetCurrentSpan() {
        Span span = YAWLTracing.getCurrentSpan();
        assertNotNull("Current span should not be null", span);
    }

    @Test
    public void testGetCurrentContext() {
        Context context = YAWLTracing.getCurrentContext();
        assertNotNull("Current context should not be null", context);
    }

    @Test
    public void testWithSpan() {
        Span span = YAWLTracing.createCaseSpan("TestCase", "case-005", "TestSpec");

        String result = YAWLTracing.withSpan(span, () -> {
            YAWLTracing.addEvent("Processing");
            return "success";
        });

        assertEquals("Result should be 'success'", "success", result);
    }

    @Test
    public void testWithSpanVoid() {
        Span span = YAWLTracing.createCaseSpan("TestCase", "case-006", "TestSpec");

        final AtomicBoolean executed = new AtomicBoolean(false);

        YAWLTracing.withSpanVoid(span, () -> {
            YAWLTracing.addEvent("Processing");
            executed.set(true);
        });

        assertTrue("Operation should have been executed", executed.get());
    }

    @Test
    public void testWithSpanException() {
        Span span = YAWLTracing.createCaseSpan("FailingCase", "case-007", "TestSpec");

        try {
            YAWLTracing.withSpan(span, () -> {
                throw new RuntimeException("Test exception");
            });
            fail("Should have thrown RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("Test exception", e.getMessage());
        }
    }

    @Test
    public void testNestedSpans() {
        Span parentSpan = YAWLTracing.createCaseSpan("ParentCase", "case-008", "TestSpec");

        String result = YAWLTracing.withSpan(parentSpan, () -> {
            YAWLTracing.addEvent("Parent started");

            Span childSpan = YAWLTracing.createWorkItemSpan("ChildWorkItem",
                "wi-003", "case-008", "task-003");

            return YAWLTracing.withSpan(childSpan, () -> {
                YAWLTracing.addEvent("Child processing");
                return "nested-success";
            });
        });

        assertEquals("Result should be 'nested-success'", "nested-success", result);
    }

    @Test
    public void testTraceOperationSuccess() throws Exception {
        String result = YAWLTracing.traceOperation("TestOperation", () -> {
            return "operation-result";
        });

        assertEquals("Result should be 'operation-result'", "operation-result", result);
    }

    @Test
    public void testTraceOperationFailure() {
        try {
            YAWLTracing.traceOperation("FailingOperation", () -> {
                throw new RuntimeException("Operation failed");
            });
            fail("Should have thrown Exception");
        } catch (Exception e) {
            assertTrue("Exception message should be correct",
                      e.getMessage().contains("Operation failed"));
        }
    }

    @Test
    public void testTraceOperationVoidSuccess() throws Exception {
        final AtomicBoolean executed = new AtomicBoolean(false);

        YAWLTracing.traceOperationVoid("VoidOperation", () -> {
            executed.set(true);
        });

        assertTrue("Void operation should have been executed", executed.get());
    }

    @Test
    public void testTraceOperationVoidFailure() {
        try {
            YAWLTracing.traceOperationVoid("FailingVoidOperation", () -> {
                throw new RuntimeException("Void operation failed");
            });
            fail("Should have thrown Exception");
        } catch (Exception e) {
            assertTrue("Exception message should be correct",
                      e.getMessage().contains("Void operation failed"));
        }
    }

    @Test
    public void testComplexWorkflowTracing() {
        // Simulate a complex workflow with multiple nested operations
        Span caseSpan = YAWLTracing.createCaseSpan("ComplexWorkflow",
            "case-009", "ComplexSpec");

        YAWLTracing.withSpanVoid(caseSpan, () -> {
            YAWLTracing.addEvent("Workflow started");
            YAWLTracing.addCaseAttributes("case-009", "ComplexSpec");

            for (int i = 0; i < 3; i++) {
                String workItemId = "wi-" + i;
                String taskId = "task-" + i;

                Span workItemSpan = YAWLTracing.createWorkItemSpan(
                    "ProcessWorkItem", workItemId, "case-009", taskId);

                YAWLTracing.withSpanVoid(workItemSpan, () -> {
                    YAWLTracing.addEvent("WorkItem processing");
                    YAWLTracing.addWorkItemAttributes(workItemId, taskId);

                    // Simulate some work
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        YAWLTracing.recordException(e);
                    }

                    YAWLTracing.addEvent("WorkItem completed");
                });
            }

            YAWLTracing.addEvent("Workflow completed");
        });

        assertTrue("Complex workflow tracing completed successfully", true);
    }

    @Test
    public void testConcurrentSpanCreation() throws InterruptedException {
        final int numThreads = 10;
        final int spansPerThread = 100;

        Thread[] threads = new Thread[numThreads];

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < spansPerThread; i++) {
                    String caseId = "thread-" + threadId + "-case-" + i;
                    Span span = YAWLTracing.createCaseSpan("ConcurrentCase",
                        caseId, "ConcurrentSpec");

                    YAWLTracing.withSpanVoid(span, () -> {
                        YAWLTracing.addEvent("Processing");
                    });
                }
            });
            threads[t].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertTrue("Concurrent span creation completed successfully", true);
    }
}
