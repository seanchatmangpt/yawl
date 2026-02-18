/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.worklet;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link WorkletRecord} — worklet instance lifecycle management.
 *
 * <p>Chicago TDD: Real WorkletRecord objects, no mocks.
 * Tests all state transitions and constraint enforcement.
 *
 * @author YAWL Foundation
 * @version 6.0.0-Alpha
 */
@DisplayName("WorkletRecord — worklet instance lifecycle and state transitions")
class TestWorkletRecord {

    private WorkletRecord record;

    @BeforeEach
    void setUp() {
        record = new WorkletRecord("OrderApprovalWorklet", "123.456", "Approve_Order");
    }

    @Test
    @DisplayName("Constructor creates record with correct initial state")
    void testConstructor_validArguments_createsRecord() {
        assertEquals("OrderApprovalWorklet", record.getWorkletName(), "Worklet name should match");
        assertEquals("123.456", record.getHostCaseId(), "Host case ID should match");
        assertEquals("Approve_Order", record.getHostTaskId(), "Host task ID should match");
        assertNull(record.getWorkletCaseId(), "Worklet case ID should be null initially");
        assertEquals(WorkletRecord.Status.PENDING, record.getStatus(), "Initial status should be PENDING");
        assertTrue(record.isPending(), "Should be pending initially");
        assertFalse(record.isRunning(), "Should not be running initially");
        assertFalse(record.isComplete(), "Should not be complete initially");
        assertFalse(record.isCancelled(), "Should not be cancelled initially");
    }

    @Test
    @DisplayName("Constructor records selection timestamp at creation time")
    void testConstructor_recordsSelectionTimestamp() {
        Instant before = Instant.now();
        WorkletRecord newRecord = new WorkletRecord("TestWorklet", "case1", "task1");
        Instant after = Instant.now();

        assertNotNull(newRecord.getSelectionTime(), "Selection time should not be null");
        assertFalse(newRecord.getSelectionTime().isBefore(before),
                "Selection time should be at or after test start");
        assertFalse(newRecord.getSelectionTime().isAfter(after),
                "Selection time should be at or before test end");
    }

    @Test
    @DisplayName("Constructor trims whitespace from all string fields")
    void testConstructor_trimsWhitespace() {
        WorkletRecord r = new WorkletRecord("  MyWorklet  ", "  case1  ", "  task1  ");
        assertEquals("MyWorklet", r.getWorkletName(), "Worklet name should be trimmed");
        assertEquals("case1", r.getHostCaseId(), "Host case ID should be trimmed");
        assertEquals("task1", r.getHostTaskId(), "Host task ID should be trimmed");
    }

    @Test
    @DisplayName("Constructor rejects null worklet name")
    void testConstructor_nullWorkletName_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkletRecord(null, "case1", "task1"),
                "Null worklet name should be rejected");
    }

    @Test
    @DisplayName("Constructor rejects blank worklet name")
    void testConstructor_blankWorkletName_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkletRecord("   ", "case1", "task1"),
                "Blank worklet name should be rejected");
    }

    @Test
    @DisplayName("Constructor rejects null host case ID")
    void testConstructor_nullHostCaseId_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkletRecord("Worklet", null, "task1"),
                "Null host case ID should be rejected");
    }

    @Test
    @DisplayName("Constructor rejects null host task ID")
    void testConstructor_nullHostTaskId_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkletRecord("Worklet", "case1", null),
                "Null host task ID should be rejected");
    }

    @Test
    @DisplayName("launch transitions status from PENDING to RUNNING")
    void testLaunch_fromPending_transitionsToRunning() {
        record.launch("worklet-case-789");

        assertEquals(WorkletRecord.Status.RUNNING, record.getStatus(),
                "Status should be RUNNING after launch");
        assertTrue(record.isRunning(), "isRunning() should return true");
        assertFalse(record.isPending(), "isPending() should return false");
        assertEquals("worklet-case-789", record.getWorkletCaseId(),
                "Worklet case ID should be set after launch");
    }

    @Test
    @DisplayName("launch sets the worklet case ID")
    void testLaunch_setsWorkletCaseId() {
        record.launch("case-99.1");
        assertEquals("case-99.1", record.getWorkletCaseId(), "Worklet case ID should be set");
    }

    @Test
    @DisplayName("launch trims whitespace from worklet case ID")
    void testLaunch_trimsWorkletCaseId() {
        record.launch("  case-99.1  ");
        assertEquals("case-99.1", record.getWorkletCaseId(),
                "Worklet case ID should be trimmed");
    }

    @Test
    @DisplayName("launch rejects null worklet case ID")
    void testLaunch_nullCaseId_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> record.launch(null),
                "Null worklet case ID should be rejected");
    }

    @Test
    @DisplayName("launch rejects blank worklet case ID")
    void testLaunch_blankCaseId_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> record.launch(""),
                "Blank worklet case ID should be rejected");
    }

    @Test
    @DisplayName("launch rejects call when already RUNNING")
    void testLaunch_fromRunning_throwsException() {
        record.launch("case-1");

        assertThrows(IllegalStateException.class,
                () -> record.launch("case-2"),
                "Launching again from RUNNING state should throw exception");
    }

    @Test
    @DisplayName("launch rejects call when already COMPLETE")
    void testLaunch_fromComplete_throwsException() {
        record.launch("case-1");
        record.complete();

        assertThrows(IllegalStateException.class,
                () -> record.launch("case-2"),
                "Launching from COMPLETE state should throw exception");
    }

    @Test
    @DisplayName("complete transitions status from RUNNING to COMPLETE")
    void testComplete_fromRunning_transitionsToComplete() {
        record.launch("case-1");
        record.complete();

        assertEquals(WorkletRecord.Status.COMPLETE, record.getStatus(),
                "Status should be COMPLETE after completion");
        assertTrue(record.isComplete(), "isComplete() should return true");
        assertFalse(record.isRunning(), "isRunning() should return false after completion");
    }

    @Test
    @DisplayName("complete rejects call when in PENDING state")
    void testComplete_fromPending_throwsException() {
        assertThrows(IllegalStateException.class,
                () -> record.complete(),
                "Completing from PENDING state should throw exception");
    }

    @Test
    @DisplayName("complete rejects call when already COMPLETE")
    void testComplete_fromComplete_throwsException() {
        record.launch("case-1");
        record.complete();

        assertThrows(IllegalStateException.class,
                () -> record.complete(),
                "Completing from COMPLETE state should throw exception");
    }

    @Test
    @DisplayName("cancel transitions status from PENDING to CANCELLED")
    void testCancel_fromPending_transitionsToCancelled() {
        record.cancel();

        assertEquals(WorkletRecord.Status.CANCELLED, record.getStatus(),
                "Status should be CANCELLED after cancellation from PENDING");
        assertTrue(record.isCancelled(), "isCancelled() should return true");
        assertFalse(record.isPending(), "isPending() should return false");
    }

    @Test
    @DisplayName("cancel transitions status from RUNNING to CANCELLED")
    void testCancel_fromRunning_transitionsToCancelled() {
        record.launch("case-1");
        record.cancel();

        assertEquals(WorkletRecord.Status.CANCELLED, record.getStatus(),
                "Status should be CANCELLED after cancellation from RUNNING");
        assertTrue(record.isCancelled(), "isCancelled() should return true");
        assertFalse(record.isRunning(), "isRunning() should return false");
    }

    @Test
    @DisplayName("cancel rejects call when already COMPLETE")
    void testCancel_fromComplete_throwsException() {
        record.launch("case-1");
        record.complete();

        assertThrows(IllegalStateException.class,
                () -> record.cancel(),
                "Cancelling from COMPLETE state should throw exception");
    }

    @Test
    @DisplayName("cancel rejects call when already CANCELLED")
    void testCancel_fromCancelled_throwsException() {
        record.cancel();

        assertThrows(IllegalStateException.class,
                () -> record.cancel(),
                "Cancelling from CANCELLED state should throw exception");
    }

    @Test
    @DisplayName("getCompositeKey returns hostCaseId:hostTaskId format")
    void testGetCompositeKey_returnsCorrectFormat() {
        String compositeKey = record.getCompositeKey();
        assertEquals("123.456:Approve_Order", compositeKey,
                "Composite key should be 'hostCaseId:hostTaskId'");
    }

    @Test
    @DisplayName("equals and hashCode based on worklet name, host case ID, and host task ID")
    void testEqualsAndHashCode_basedOnKeyFields() {
        WorkletRecord r1 = new WorkletRecord("MyWorklet", "case1", "task1");
        WorkletRecord r2 = new WorkletRecord("MyWorklet", "case1", "task1");
        WorkletRecord r3 = new WorkletRecord("OtherWorklet", "case1", "task1");

        assertEquals(r1, r2, "Records with same key fields should be equal");
        assertEquals(r1.hashCode(), r2.hashCode(), "Equal records should have equal hash codes");
        assertNotEquals(r1, r3, "Records with different worklet names should not be equal");
    }

    @Test
    @DisplayName("equals is false for different host case IDs")
    void testEquals_differentHostCaseId_returnsFalse() {
        WorkletRecord r1 = new WorkletRecord("Worklet", "case1", "task1");
        WorkletRecord r2 = new WorkletRecord("Worklet", "case2", "task1");

        assertNotEquals(r1, r2, "Different host case IDs should not be equal");
    }

    @Test
    @DisplayName("equals is false for different host task IDs")
    void testEquals_differentHostTaskId_returnsFalse() {
        WorkletRecord r1 = new WorkletRecord("Worklet", "case1", "task1");
        WorkletRecord r2 = new WorkletRecord("Worklet", "case1", "task2");

        assertNotEquals(r1, r2, "Different host task IDs should not be equal");
    }

    @Test
    @DisplayName("toString includes worklet name, host case, host task, and status")
    void testToString_includesKeyFields() {
        String str = record.toString();

        assertTrue(str.contains("OrderApprovalWorklet"), "toString should contain worklet name");
        assertTrue(str.contains("123.456"), "toString should contain host case ID");
        assertTrue(str.contains("Approve_Order"), "toString should contain host task ID");
        assertTrue(str.contains("PENDING"), "toString should contain status");
    }

    @Test
    @DisplayName("Full lifecycle: PENDING → RUNNING → COMPLETE")
    void testFullLifecycle_pendingToRunningToComplete() {
        assertEquals(WorkletRecord.Status.PENDING, record.getStatus());

        record.launch("worklet-99");
        assertEquals(WorkletRecord.Status.RUNNING, record.getStatus());
        assertEquals("worklet-99", record.getWorkletCaseId());

        record.complete();
        assertEquals(WorkletRecord.Status.COMPLETE, record.getStatus());

        assertTrue(record.isComplete());
        assertFalse(record.isRunning());
        assertFalse(record.isPending());
        assertFalse(record.isCancelled());
    }

    @Test
    @DisplayName("Full lifecycle: PENDING → RUNNING → CANCELLED")
    void testFullLifecycle_pendingToRunningToCancelled() {
        record.launch("worklet-99");
        record.cancel();

        assertEquals(WorkletRecord.Status.CANCELLED, record.getStatus());
        assertTrue(record.isCancelled());
        assertFalse(record.isRunning());
    }
}
