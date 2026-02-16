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

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test suite for YAWL OpenTelemetry integration.
 * Tests the telemetry instrumentation for YAWL workflow operations.
 *
 * @author YAWL Development Team
 */
public class YAWLTelemetryTest {

    private YAWLTelemetry telemetry;

    @Before
    public void setUp() {
        telemetry = YAWLTelemetry.getInstance();
        telemetry.setEnabled(true);
    }

    @Test
    public void testGetInstance() {
        assertNotNull("YAWLTelemetry instance should not be null", telemetry);
        YAWLTelemetry instance2 = YAWLTelemetry.getInstance();
        assertSame("YAWLTelemetry should be singleton", telemetry, instance2);
    }

    @Test
    public void testGetTracer() {
        Tracer tracer = telemetry.getTracer();
        assertNotNull("Tracer should not be null", tracer);
    }

    @Test
    public void testGetMeter() {
        Meter meter = telemetry.getMeter();
        assertNotNull("Meter should not be null", meter);
    }

    @Test
    public void testRecordCaseStarted() {
        String caseId = "test-case-001";
        String specId = "TestSpecification";

        telemetry.recordCaseStarted(caseId, specId);

        // Metrics are recorded asynchronously, so we just verify no exceptions
        assertTrue("Telemetry should be enabled", telemetry.isEnabled());
    }

    @Test
    public void testRecordCaseCompleted() {
        String caseId = "test-case-002";
        String specId = "TestSpecification";

        // Start and complete a case
        telemetry.recordCaseStarted(caseId, specId);

        // Simulate some processing time
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        telemetry.recordCaseCompleted(caseId, specId);

        assertTrue("Telemetry should be enabled", telemetry.isEnabled());
    }

    @Test
    public void testRecordCaseCancelled() {
        String caseId = "test-case-003";
        String specId = "TestSpecification";

        telemetry.recordCaseStarted(caseId, specId);
        telemetry.recordCaseCancelled(caseId, specId);

        assertTrue("Telemetry should be enabled", telemetry.isEnabled());
    }

    @Test
    public void testRecordCaseFailed() {
        String caseId = "test-case-004";
        String specId = "TestSpecification";
        String errorMessage = "Test error message";

        telemetry.recordCaseStarted(caseId, specId);
        telemetry.recordCaseFailed(caseId, specId, errorMessage);

        assertTrue("Telemetry should be enabled", telemetry.isEnabled());
    }

    @Test
    public void testRecordWorkItemCreated() {
        String workItemId = "wi-001";
        String caseId = "test-case-005";
        String taskId = "task-001";

        telemetry.recordWorkItemCreated(workItemId, caseId, taskId);

        assertTrue("Telemetry should be enabled", telemetry.isEnabled());
    }

    @Test
    public void testRecordWorkItemStarted() {
        String workItemId = "wi-002";
        String caseId = "test-case-006";
        String taskId = "task-002";

        telemetry.recordWorkItemStarted(workItemId, caseId, taskId);

        assertTrue("Telemetry should be enabled", telemetry.isEnabled());
    }

    @Test
    public void testRecordWorkItemCompleted() {
        String workItemId = "wi-003";
        String caseId = "test-case-007";
        String taskId = "task-003";

        telemetry.recordWorkItemStarted(workItemId, caseId, taskId);

        // Simulate some processing time
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        telemetry.recordWorkItemCompleted(workItemId, caseId, taskId);

        assertTrue("Telemetry should be enabled", telemetry.isEnabled());
    }

    @Test
    public void testRecordWorkItemFailed() {
        String workItemId = "wi-004";
        String caseId = "test-case-008";
        String taskId = "task-004";
        String errorMessage = "Work item processing error";

        telemetry.recordWorkItemStarted(workItemId, caseId, taskId);
        telemetry.recordWorkItemFailed(workItemId, caseId, taskId, errorMessage);

        assertTrue("Telemetry should be enabled", telemetry.isEnabled());
    }

    @Test
    public void testRecordNetRunnerExecution() {
        long durationMs = 250;
        String caseId = "test-case-009";

        telemetry.recordNetRunnerExecution(durationMs, caseId);

        assertTrue("Telemetry should be enabled", telemetry.isEnabled());
    }

    @Test
    public void testRecordEngineOperation() {
        String operation = "TestOperation";
        double durationMs = 125.5;

        telemetry.recordEngineOperation(operation, durationMs);

        assertTrue("Telemetry should be enabled", telemetry.isEnabled());
    }

    @Test
    public void testUpdateEnabledTasksCount() {
        long count = 10;

        telemetry.updateEnabledTasksCount(count);

        assertTrue("Telemetry should be enabled", telemetry.isEnabled());
    }

    @Test
    public void testUpdateBusyTasksCount() {
        long count = 5;

        telemetry.updateBusyTasksCount(count);

        assertTrue("Telemetry should be enabled", telemetry.isEnabled());
    }

    @Test
    public void testEnableDisableTelemetry() {
        assertTrue("Telemetry should be enabled initially", telemetry.isEnabled());

        telemetry.setEnabled(false);
        assertFalse("Telemetry should be disabled", telemetry.isEnabled());

        // Verify that recording operations don't throw exceptions when disabled
        telemetry.recordCaseStarted("test-case", "test-spec");
        telemetry.recordWorkItemCreated("wi-001", "test-case", "task-001");

        telemetry.setEnabled(true);
        assertTrue("Telemetry should be enabled again", telemetry.isEnabled());
    }

    @Test
    public void testDisabledTelemetryNoOps() {
        telemetry.setEnabled(false);

        String caseId = "test-case-010";
        String specId = "TestSpecification";

        // All these operations should complete without errors
        telemetry.recordCaseStarted(caseId, specId);
        telemetry.recordCaseCompleted(caseId, specId);
        telemetry.recordCaseCancelled(caseId, specId);
        telemetry.recordCaseFailed(caseId, specId, "error");

        telemetry.recordWorkItemCreated("wi-001", caseId, "task-001");
        telemetry.recordWorkItemStarted("wi-001", caseId, "task-001");
        telemetry.recordWorkItemCompleted("wi-001", caseId, "task-001");
        telemetry.recordWorkItemFailed("wi-001", caseId, "task-001", "error");

        telemetry.recordNetRunnerExecution(100, caseId);
        telemetry.recordEngineOperation("test", 50.0);
        telemetry.updateEnabledTasksCount(10);
        telemetry.updateBusyTasksCount(5);

        assertFalse("Telemetry should still be disabled", telemetry.isEnabled());
    }

    @Test
    public void testConcurrentCaseRecording() throws InterruptedException {
        final int numThreads = 10;
        final int casesPerThread = 100;

        Thread[] threads = new Thread[numThreads];

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < casesPerThread; i++) {
                    String caseId = "thread-" + threadId + "-case-" + i;
                    String specId = "ConcurrentSpec";

                    telemetry.recordCaseStarted(caseId, specId);

                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    if (i % 2 == 0) {
                        telemetry.recordCaseCompleted(caseId, specId);
                    } else {
                        telemetry.recordCaseCancelled(caseId, specId);
                    }
                }
            });
            threads[t].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertTrue("Telemetry should still be enabled after concurrent operations",
                   telemetry.isEnabled());
    }

    @Test
    public void testWorkflowScenario() {
        // Simulate a complete workflow scenario
        String caseId = "order-fulfillment-001";
        String specId = "OrderFulfillment";

        // Start case
        telemetry.recordCaseStarted(caseId, specId);

        // Process multiple work items
        String[] taskIds = {"ReceiveOrder", "ValidateOrder", "ProcessPayment", "ShipOrder"};

        for (int i = 0; i < taskIds.length; i++) {
            String workItemId = caseId + "-wi-" + i;
            String taskId = taskIds[i];

            telemetry.recordWorkItemCreated(workItemId, caseId, taskId);
            telemetry.recordWorkItemStarted(workItemId, caseId, taskId);

            // Simulate processing
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            telemetry.recordWorkItemCompleted(workItemId, caseId, taskId);
        }

        // Complete case
        telemetry.recordCaseCompleted(caseId, specId);

        assertTrue("Telemetry should be enabled", telemetry.isEnabled());
    }

    @Test
    public void testGetCurrentContext() {
        assertNotNull("Current context should not be null",
                     telemetry.getCurrentContext());
    }

    @Test
    public void testNullErrorMessageHandling() {
        String caseId = "test-case-null-error";
        String specId = "TestSpec";

        telemetry.recordCaseStarted(caseId, specId);
        telemetry.recordCaseFailed(caseId, specId, null);

        String workItemId = "wi-null-error";
        telemetry.recordWorkItemStarted(workItemId, caseId, "task-001");
        telemetry.recordWorkItemFailed(workItemId, caseId, "task-001", null);

        assertTrue("Telemetry should handle null error messages",
                   telemetry.isEnabled());
    }
}
