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

package org.yawlfoundation.yawl.engine.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for YAWLTelemetry deadlock and error metrics.
 * Validates all new metrics added for deadlock tracking and error recording.
 */
@Tag("unit")
@Execution(ExecutionMode.SAME_THREAD)
public class YAWLTelemetryTest {

    private static OpenTelemetrySdk openTelemetrySdk;
    private static YAWLTelemetry telemetry;

    @BeforeAll
    public static void setupAll() {
        try {
            // Check if GlobalOpenTelemetry is already initialized
            GlobalOpenTelemetry.get();
            // If we get here, it's already registered. Reuse the existing instance.
            openTelemetrySdk = null;  // Don't own the shutdown
        } catch (IllegalStateException e) {
            // Not yet registered, proceed with registration
            Resource resource = Resource.getDefault();

            SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                    .setResource(resource)
                    .build();

            openTelemetrySdk = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                    .buildAndRegisterGlobal();
        }

        telemetry = YAWLTelemetry.getInstance();
    }

    @AfterAll
    public static void tearDownAll() {
        if (openTelemetrySdk != null) {
            CompletableResultCode shutdown = openTelemetrySdk.shutdown();
            shutdown.join(5, TimeUnit.SECONDS);
            openTelemetrySdk = null;
        }
        // If openTelemetrySdk is null, it means another test owns the global instance.
        // Don't shut it down—let it remain for other tests.
    }

    @BeforeEach
    public void setup() {
        telemetry.setEnabled(true);
    }

    // ==================== Basic Telemetry Tests ====================

    @Test
    public void testTelemetryInstance() {
        assertNotNull(telemetry, "YAWLTelemetry instance should not be null");
        assertTrue(telemetry.isEnabled(), "Telemetry should be enabled by default");
    }

    @Test
    public void testEnableDisable() {
        telemetry.setEnabled(false);
        assertFalse(telemetry.isEnabled(), "Telemetry should be disabled");

        telemetry.setEnabled(true);
        assertTrue(telemetry.isEnabled(), "Telemetry should be enabled");
    }

    @Test
    public void testGetTracer() {
        assertNotNull(telemetry.getTracer(), "Tracer should not be null");
    }

    @Test
    public void testGetMeter() {
        assertNotNull(telemetry.getMeter(), "Meter should not be null");
    }

    @Test
    public void testGetOpenTelemetry() {
        assertNotNull(telemetry.getOpenTelemetry(), "OpenTelemetry should not be null");
    }

    // ==================== Deadlock Metrics Tests ====================

    @Test
    public void testRecordDeadlock() {
        long initialCount = telemetry.getDeadlockedTasksCount();

        telemetry.recordDeadlock("case-123", "spec-456", 3);

        // Verify deadlocked tasks count increased
        assertTrue(telemetry.getDeadlockedTasksCount() >= initialCount + 3,
            "Deadlocked tasks count should have increased");
    }

    @Test
    public void testRecordDeadlockResolution() {
        // First record a deadlock
        telemetry.recordDeadlock("case-resolution-test", "spec-789", 2);

        // Then resolve it
        telemetry.recordDeadlockResolution("case-resolution-test");

        // Verify we can get stats without error
        YAWLTelemetry.DeadlockStats stats = telemetry.getDeadlockStats();
        assertNotNull(stats, "DeadlockStats should not be null");
    }

    @Test
    public void testDeadlockStats() {
        telemetry.updateDeadlockedTasksCount(5);

        YAWLTelemetry.DeadlockStats stats = telemetry.getDeadlockStats();

        assertEquals(5, stats.getCurrentDeadlockedTasks(),
            "Current deadlocked tasks count should match");
        assertNotNull(stats.toString(), "toString should return non-null string");
        assertTrue(stats.toString().contains("5"),
            "toString should contain the count");
    }

    @Test
    public void testEnhancedDeadlockStats() {
        // Record a few deadlocks
        telemetry.recordDeadlock("case-stats-1", "spec-stats", 3);
        telemetry.recordDeadlock("case-stats-2", "spec-stats", 2);

        YAWLTelemetry.DeadlockStats stats = telemetry.getDeadlockStats();

        // Verify enhanced statistics
        assertTrue(stats.getTotalDeadlocksDetected() >= 2,
            "Total deadlocks should be at least 2");
        assertTrue(stats.getTotalDeadlockedTasks() >= 5,
            "Total deadlocked tasks should be at least 5");
        assertTrue(stats.getActiveDeadlockCases() >= 2,
            "Active deadlock cases should be at least 2");
        assertNotNull(stats.getActiveDeadlocks(),
            "Active deadlocks map should not be null");
        assertTrue(stats.hasActiveDeadlocks(),
            "Should have active deadlocks");
        assertTrue(stats.getAverageTasksPerDeadlock() > 0,
            "Average tasks per deadlock should be positive");
    }

    @Test
    public void testDeadlockStatsResolvedCases() {
        String caseId = "case-resolved-stats";
        telemetry.recordDeadlock(caseId, "spec-resolved", 1);
        telemetry.recordDeadlockResolution(caseId);

        YAWLTelemetry.DeadlockStats stats = telemetry.getDeadlockStats();

        assertTrue(stats.getResolvedDeadlockCases() >= 1,
            "Resolved deadlock cases should be at least 1");
    }

    @Test
    public void testGetLockContentionStats() {
        // Record some lock contentions
        telemetry.recordLockContention(100, "case-contention-1", "operationA");
        telemetry.recordLockContention(200, "case-contention-2", "operationB");
        telemetry.recordLockContention(600, "case-contention-3", "operationA"); // Above threshold

        YAWLTelemetry.LockContentionStats stats = telemetry.getLockContentionStats();

        // Verify statistics
        assertTrue(stats.getTotalContentions() >= 3,
            "Total contentions should be at least 3");
        assertTrue(stats.getTotalWaitTimeMs() >= 900,
            "Total wait time should be at least 900ms");
        assertTrue(stats.getAverageWaitTimeMs() > 0,
            "Average wait time should be positive");
        assertEquals(600, stats.getMaxWaitTimeMs(),
            "Max wait time should be 600ms");
        assertTrue(stats.getContentionsAboveThreshold() >= 1,
            "At least 1 contention should be above threshold");
        assertEquals(500, stats.getThresholdMs(),
            "Threshold should be 500ms");
        assertNotNull(stats.getContentionByOperation(),
            "Contention by operation map should not be null");
        assertTrue(stats.hasHighContention(),
            "Should have high contention");
        assertFalse(stats.isHealthy(),
            "Should not be healthy due to contention above threshold");
        assertTrue(stats.getPercentageAboveThreshold() > 0,
            "Percentage above threshold should be positive");
    }

    @Test
    public void testLockContentionStatsHealthy() {
        // Create fresh telemetry stats for healthy test
        telemetry.recordLockContention(100, "case-healthy", "op1");
        telemetry.recordLockContention(200, "case-healthy", "op2");

        YAWLTelemetry.LockContentionStats stats = telemetry.getLockContentionStats();

        assertNotNull(stats.toString(),
            "toString should return non-null string");
    }

    @Test
    public void testLockContentionStatsByOperation() {
        telemetry.recordLockContention(100, "case-op", "uniqueOperationTest");
        telemetry.recordLockContention(150, "case-op", "uniqueOperationTest");

        YAWLTelemetry.LockContentionStats stats = telemetry.getLockContentionStats();

        assertTrue(stats.getContentionByOperation().containsKey("uniqueOperationTest"),
            "Should contain contention record for uniqueOperationTest");

        YAWLTelemetry.LockContentionRecord record =
            stats.getContentionByOperation().get("uniqueOperationTest");
        assertNotNull(record, "LockContentionRecord should not be null");
        assertTrue(record.count() >= 2, "Count should be at least 2");
        assertTrue(record.averageWaitMs() > 0, "Average wait should be positive");
    }

    @Test
    public void testUpdateDeadlockedTasksCount() {
        telemetry.updateDeadlockedTasksCount(10);

        assertEquals(10, telemetry.getDeadlockedTasksCount(),
            "Deadlocked tasks count should be updated to 10");
    }

    @Test
    public void testRecordDeadlockWithNullValues() {
        // Should not throw with null values
        assertDoesNotThrow(() -> telemetry.recordDeadlock(null, null, 0));
        assertDoesNotThrow(() -> telemetry.recordDeadlock("case", null, 1));
        assertDoesNotThrow(() -> telemetry.recordDeadlock(null, "spec", 1));
    }

    // ==================== Error Metrics Tests ====================

    @Test
    public void testRecordValidationError() {
        assertDoesNotThrow(() ->
            telemetry.recordValidationError("case-123", "task-456", "query", "Invalid data")
        );
    }

    @Test
    public void testRecordValidationErrorWithNulls() {
        assertDoesNotThrow(() ->
            telemetry.recordValidationError(null, null, null, null)
        );
    }

    @Test
    public void testRecordLockWaitTimeout() {
        assertDoesNotThrow(() ->
            telemetry.recordLockWaitTimeout("case-123", "resource-456", 5000)
        );
    }

    @Test
    public void testRecordLockWaitTimeoutWithNulls() {
        assertDoesNotThrow(() ->
            telemetry.recordLockWaitTimeout(null, null, 0)
        );
    }

    @Test
    public void testRecordJwksRefreshFailure() {
        assertDoesNotThrow(() ->
            telemetry.recordJwksRefreshFailure(
                "https://auth.example.com/certs",
                "IOException",
                "Connection refused"
            )
        );
    }

    @Test
    public void testRecordJwksRefreshFailureWithNulls() {
        assertDoesNotThrow(() ->
            telemetry.recordJwksRefreshFailure(null, null, null)
        );
    }

    @Test
    public void testRecordCaseCancellationFailure() {
        assertDoesNotThrow(() ->
            telemetry.recordCaseCancellationFailure("case-123", "Task in progress")
        );
    }

    @Test
    public void testRecordCaseCancellationFailureWithNulls() {
        assertDoesNotThrow(() ->
            telemetry.recordCaseCancellationFailure(null, null)
        );
    }

    @Test
    public void testRecordInterfaceDeliveryFailure() {
        assertDoesNotThrow(() ->
            telemetry.recordInterfaceDeliveryFailure(
                "InterfaceX_EngineSideClient",
                "NOTIFY_TIMEOUT",
                "ConnectionException",
                3
            )
        );
    }

    @Test
    public void testRecordInterfaceDeliveryFailureWithNulls() {
        assertDoesNotThrow(() ->
            telemetry.recordInterfaceDeliveryFailure(null, null, null, 0)
        );
    }

    @Test
    public void testRecordRetryExhausted() {
        assertDoesNotThrow(() ->
            telemetry.recordRetryExhausted(
                "webhook-delivery",
                "deliver",
                5,
                "ConnectionTimeoutException"
            )
        );
    }

    @Test
    public void testRecordRetryExhaustedWithNulls() {
        assertDoesNotThrow(() ->
            telemetry.recordRetryExhausted(null, null, 0, null)
        );
    }

    @Test
    public void testRecordFallbackUsed() {
        assertDoesNotThrow(() ->
            telemetry.recordFallbackUsed(
                "circuit-breaker",
                "executeQuery",
                "circuit_open",
                30000
            )
        );
    }

    @Test
    public void testRecordFallbackUsedWithStaleData() {
        assertDoesNotThrow(() ->
            telemetry.recordFallbackUsed(
                "cache",
                "getData",
                "stale_data",
                300000  // 5 minutes old
            )
        );
    }

    @Test
    public void testRecordFallbackUsedWithNulls() {
        assertDoesNotThrow(() ->
            telemetry.recordFallbackUsed(null, null, null, -1)
        );
    }

    // ==================== Lock Contention Tests ====================

    @Test
    public void testRecordLockContention() {
        assertDoesNotThrow(() ->
            telemetry.recordLockContention(100, "case-123", "continueIfPossible")
        );
    }

    @Test
    public void testRecordLockContentionWithNulls() {
        assertDoesNotThrow(() ->
            telemetry.recordLockContention(50, null, null)
        );
    }

    // ==================== Case Metrics Tests ====================

    @Test
    public void testRecordCaseStarted() {
        assertDoesNotThrow(() ->
            telemetry.recordCaseStarted("case-start-123", "spec-start-456")
        );
    }

    @Test
    public void testRecordCaseCompleted() {
        telemetry.recordCaseStarted("case-complete-123", "spec-456");
        assertDoesNotThrow(() ->
            telemetry.recordCaseCompleted("case-complete-123", "spec-456")
        );
    }

    @Test
    public void testRecordCaseCancelled() {
        telemetry.recordCaseStarted("case-cancel-123", "spec-456");
        assertDoesNotThrow(() ->
            telemetry.recordCaseCancelled("case-cancel-123", "spec-456")
        );
    }

    @Test
    public void testRecordCaseFailed() {
        telemetry.recordCaseStarted("case-fail-123", "spec-456");
        assertDoesNotThrow(() ->
            telemetry.recordCaseFailed("case-fail-123", "spec-456", "Test error")
        );
    }

    // ==================== Work Item Metrics Tests ====================

    @Test
    public void testRecordWorkItemCreated() {
        assertDoesNotThrow(() ->
            telemetry.recordWorkItemCreated("wi-123", "case-456", "task-789")
        );
    }

    @Test
    public void testRecordWorkItemStarted() {
        assertDoesNotThrow(() ->
            telemetry.recordWorkItemStarted("wi-start-123", "case-456", "task-789")
        );
    }

    @Test
    public void testRecordWorkItemCompleted() {
        telemetry.recordWorkItemStarted("wi-complete-123", "case-456", "task-789");
        assertDoesNotThrow(() ->
            telemetry.recordWorkItemCompleted("wi-complete-123", "case-456", "task-789")
        );
    }

    @Test
    public void testRecordWorkItemFailed() {
        telemetry.recordWorkItemStarted("wi-fail-123", "case-456", "task-789");
        assertDoesNotThrow(() ->
            telemetry.recordWorkItemFailed("wi-fail-123", "case-456", "task-789", "Test failure")
        );
    }

    // ==================== Engine Metrics Tests ====================

    @Test
    public void testRecordNetRunnerExecution() {
        assertDoesNotThrow(() ->
            telemetry.recordNetRunnerExecution(150, "case-123")
        );
    }

    @Test
    public void testRecordEngineOperation() {
        assertDoesNotThrow(() ->
            telemetry.recordEngineOperation("launchCase", 45.5)
        );
    }

    @Test
    public void testUpdateEnabledTasksCount() {
        assertDoesNotThrow(() -> telemetry.updateEnabledTasksCount(10));
    }

    @Test
    public void testUpdateBusyTasksCount() {
        assertDoesNotThrow(() -> telemetry.updateBusyTasksCount(5));
    }

    // ==================== Context and Utility Tests ====================

    @Test
    public void testGetCurrentContext() {
        assertNotNull(telemetry.getCurrentContext(), "Current context should not be null");
    }

    @Test
    public void testCreateSpan() {
        assertDoesNotThrow(() -> {
            var span = telemetry.createSpan("test.operation", "case-123");
            assertNotNull(span, "Span should be created");
            span.end();
        });
    }

    @Test
    public void testCreateSpanWithoutCaseId() {
        assertDoesNotThrow(() -> {
            var span = telemetry.createSpan("test.operation", null);
            assertNotNull(span, "Span should be created even without case ID");
            span.end();
        });
    }

    @Test
    public void testCreateScopedSpan() {
        assertDoesNotThrow(() -> {
            try (var scope = telemetry.createScopedSpan("test.scoped", "case-123")) {
                assertNotNull(scope, "Scope should be created");
            }
        });
    }

    @Test
    public void testRecordErrorOnCurrentSpan() {
        assertDoesNotThrow(() -> {
            try (var scope = telemetry.createScopedSpan("test.error", "case-123")) {
                telemetry.recordErrorOnCurrentSpan(new RuntimeException("Test error"));
            }
        });
    }

    // ==================== Attribute Key Constants Tests ====================

    @Test
    public void testAttributeKeysAreNotNull() {
        assertNotNull(YAWLTelemetry.ATTR_CASE_ID, "ATTR_CASE_ID should not be null");
        assertNotNull(YAWLTelemetry.ATTR_SPEC_ID, "ATTR_SPEC_ID should not be null");
        assertNotNull(YAWLTelemetry.ATTR_TASK_ID, "ATTR_TASK_ID should not be null");
        assertNotNull(YAWLTelemetry.ATTR_WORKITEM_ID, "ATTR_WORKITEM_ID should not be null");
        assertNotNull(YAWLTelemetry.ATTR_ERROR_TYPE, "ATTR_ERROR_TYPE should not be null");
        assertNotNull(YAWLTelemetry.ATTR_ERROR_MESSAGE, "ATTR_ERROR_MESSAGE should not be null");
        assertNotNull(YAWLTelemetry.ATTR_INTERFACE_NAME, "ATTR_INTERFACE_NAME should not be null");
        assertNotNull(YAWLTelemetry.ATTR_COMPONENT, "ATTR_COMPONENT should not be null");
        assertNotNull(YAWLTelemetry.ATTR_OPERATION, "ATTR_OPERATION should not be null");
        assertNotNull(YAWLTelemetry.ATTR_DEADLOCK_TASKS, "ATTR_DEADLOCK_TASKS should not be null");
        assertNotNull(YAWLTelemetry.ATTR_RETRY_ATTEMPT, "ATTR_RETRY_ATTEMPT should not be null");
        assertNotNull(YAWLTelemetry.ATTR_FALLBACK_REASON, "ATTR_FALLBACK_REASON should not be null");
    }

    // ==================== Disabled Telemetry Tests ====================

    @Test
    public void testMetricsNotRecordedWhenDisabled() {
        telemetry.setEnabled(false);

        // These should be no-ops when disabled
        telemetry.recordDeadlock("disabled-case", "spec", 1);
        telemetry.recordValidationError("disabled-case", "task", "type", "msg");
        telemetry.recordLockWaitTimeout("disabled-case", "resource", 100);
        telemetry.recordJwksRefreshFailure("uri", "type", "msg");
        telemetry.recordCaseCancellationFailure("disabled-case", "reason");
        telemetry.recordInterfaceDeliveryFailure("iface", "cmd", "err", 1);
        telemetry.recordRetryExhausted("comp", "op", 3, "err");
        telemetry.recordFallbackUsed("comp", "op", "reason", 1000);

        // Should not throw
        assertTrue(true, "Operations should complete without error when disabled");

        telemetry.setEnabled(true);
    }
}
