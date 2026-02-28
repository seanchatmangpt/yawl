/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.integration.blueocean.instrumentation;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OpenTelemetryMetricsInstrumentation.
 */
public class OpenTelemetryMetricsInstrumentationTest {
    private OpenTelemetryMetricsInstrumentation metrics;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new OpenTelemetryMetricsInstrumentation(registry);
    }

    @Test
    void testNullMeterRegistryThrows() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
                new OpenTelemetryMetricsInstrumentation(null));
    }

    @Test
    void testRecordLineageQuery() {
        // When
        metrics.recordLineageQuery("customers");
        metrics.recordLineageQuery("customers");

        // Then
        assertEquals(2, metrics.getLineageQueryCount("customers"));
    }

    @Test
    void testNullTableIdThrows() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                metrics.recordLineageQuery(null));
    }

    @Test
    void testBlankTableIdThrows() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                metrics.recordLineageQuery(""));
    }

    @Test
    void testStartDataAccessTimer() {
        // When
        try (var timer = metrics.startDataAccessTimer("orders", "READ")) {
            // Simulate work
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then - no exception thrown, timer recorded
        assertDoesNotThrow(() -> metrics.startDataAccessTimer("orders", "WRITE"));
    }

    @Test
    void testDataAccessTimerNullTableThrows() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                metrics.startDataAccessTimer(null, "READ"));
    }

    @Test
    void testDataAccessTimerBlankTableThrows() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                metrics.startDataAccessTimer("", "READ"));
    }

    @Test
    void testDataAccessTimerNullOperationThrows() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                metrics.startDataAccessTimer("table", null));
    }

    @Test
    void testRecordGuardViolation() {
        // When
        metrics.recordGuardViolation("H_TODO");
        metrics.recordGuardViolation("H_TODO");
        metrics.recordGuardViolation("H_MOCK");

        // Then
        assertEquals(2, metrics.getGuardViolationCount("H_TODO"));
        assertEquals(1, metrics.getGuardViolationCount("H_MOCK"));
    }

    @Test
    void testGuardViolationNullPatternThrows() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                metrics.recordGuardViolation(null));
    }

    @Test
    void testGuardViolationBlankPatternThrows() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                metrics.recordGuardViolation(""));
    }

    @Test
    void testRecordSchemaChange() {
        // When
        metrics.recordSchemaChange("customers", "ADD_COLUMN");
        metrics.recordSchemaChange("customers", "ADD_COLUMN");
        metrics.recordSchemaChange("orders", "TYPE_CHANGE");

        // Then - metrics recorded without throwing
        assertDoesNotThrow(() ->
                metrics.recordSchemaChange("products", "REMOVE_COLUMN"));
    }

    @Test
    void testSchemaChangeNullTableThrows() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                metrics.recordSchemaChange(null, "ADD_COLUMN"));
    }

    @Test
    void testSchemaChangeBlankTableThrows() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                metrics.recordSchemaChange("", "ADD_COLUMN"));
    }

    @Test
    void testSchemaChangeNullTypeThrows() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                metrics.recordSchemaChange("table", null));
    }

    @Test
    void testRecordTaskExecution() {
        // When
        metrics.recordTaskExecution("task-001", 1.5);
        metrics.recordTaskExecution("task-002", 2.3);

        // Then - no exception thrown
        assertDoesNotThrow(() -> metrics.recordTaskExecution("task-003", 0.5));
    }

    @Test
    void testTaskExecutionNullTaskIdThrows() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                metrics.recordTaskExecution(null, 1.0));
    }

    @Test
    void testTaskExecutionBlankTaskIdThrows() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                metrics.recordTaskExecution("", 1.0));
    }

    @Test
    void testTaskExecutionNegativeDurationThrows() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                metrics.recordTaskExecution("task-001", -0.5));
    }

    @Test
    void testRecordContractViolation() {
        // When
        metrics.recordContractViolation("MISSING_INPUT");
        metrics.recordContractViolation("TYPE_MISMATCH");
        metrics.recordContractViolation("MISSING_INPUT");

        // Then
        assertEquals(2, metrics.getTotalContractViolations());
    }

    @Test
    void testContractViolationNullCodeThrows() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                metrics.recordContractViolation(null));
    }

    @Test
    void testContractViolationBlankCodeThrows() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                metrics.recordContractViolation(""));
    }

    @Test
    void testGetLineageQueryCountForUnknownTable() {
        // When
        int count = metrics.getLineageQueryCount("nonexistent");

        // Then
        assertEquals(0, count);
    }

    @Test
    void testGetGuardViolationCountForUnknownPattern() {
        // When
        int count = metrics.getGuardViolationCount("UNKNOWN_PATTERN");

        // Then
        assertEquals(0, count);
    }

    @Test
    void testGetTotalContractViolations() {
        // When
        metrics.recordContractViolation("ERROR_1");
        metrics.recordContractViolation("ERROR_2");

        // Then
        assertEquals(2, metrics.getTotalContractViolations());
    }

    @Test
    void testExportMetricsAsPrometheus() {
        // Given - need PrometheusMeterRegistry, not SimpleMeterRegistry
        // When/Then
        assertThrows(UnsupportedOperationException.class, () ->
                metrics.exportMetricsAsPrometheus());
    }

    @Test
    void testMultipleTimerContexts() {
        // When - create and close multiple timer contexts
        var timer1 = metrics.startDataAccessTimer("table1", "READ");
        var timer2 = metrics.startDataAccessTimer("table2", "WRITE");

        assertDoesNotThrow(() -> {
            timer1.close();
            timer2.close();
        });

        // Then - no exception thrown
    }

    @Test
    void testMetricsAreNonNegative() {
        // When
        metrics.recordLineageQuery("test");
        metrics.recordGuardViolation("H_TEST");
        metrics.recordContractViolation("TEST_CODE");

        // Then
        assertTrue(metrics.getLineageQueryCount("test") >= 1);
        assertTrue(metrics.getGuardViolationCount("H_TEST") >= 1);
        assertTrue(metrics.getTotalContractViolations() >= 1);
    }

    @Test
    void testConcurrentRecordingThreadSafety() throws InterruptedException {
        // When - record metrics from multiple threads
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                metrics.recordGuardViolation("H_PATTERN");
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                metrics.recordContractViolation("CODE_X");
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // Then
        assertEquals(100, metrics.getGuardViolationCount("H_PATTERN"));
        assertEquals(100, metrics.getTotalContractViolations());
    }
}
