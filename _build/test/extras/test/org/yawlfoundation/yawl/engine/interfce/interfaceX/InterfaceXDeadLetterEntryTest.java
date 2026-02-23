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

package org.yawlfoundation.yawl.engine.interfce.interfaceX;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InterfaceXDeadLetterEntry.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class InterfaceXDeadLetterEntryTest {

    @Test
    @DisplayName("Should create entry with all fields")
    void shouldCreateEntryWithAllFields() {
        Map<String, String> params = Map.of(
                "action", "3",
                "caseID", "case-123",
                "workItem", "<workitem>data</workitem>");

        InterfaceXDeadLetterEntry entry = new InterfaceXDeadLetterEntry(
                3,
                params,
                "http://localhost:9999/service",
                "Connection refused",
                3,
                24);

        assertNotNull(entry.getId());
        assertEquals(3, entry.getCommand());
        assertEquals(params, entry.getParameters());
        assertEquals("http://localhost:9999/service", entry.getObserverURI());
        assertEquals("Connection refused", entry.getFailureReason());
        assertEquals(3, entry.getAttemptCount());
        assertNotNull(entry.getCreatedAt());
        assertNotNull(entry.getExpiresAt());
        assertNull(entry.getLastRetryAttempt());
        assertEquals(0, entry.getManualRetryCount());
    }

    @Test
    @DisplayName("Should create immutable copy of parameters")
    void shouldCreateImmutableParameters() {
        Map<String, String> originalParams = new java.util.HashMap<>();
        originalParams.put("action", "3");

        InterfaceXDeadLetterEntry entry = new InterfaceXDeadLetterEntry(
                3, originalParams, "http://test", "Error", 3, 24);

        originalParams.put("newKey", "newValue");

        assertFalse(entry.getParameters().containsKey("newKey"));
        assertThrows(UnsupportedOperationException.class, () ->
                entry.getParameters().put("another", "value"));
    }

    @Test
    @DisplayName("Should calculate expiration correctly")
    void shouldCalculateExpirationCorrectly() throws InterruptedException {
        InterfaceXDeadLetterEntry entry = new InterfaceXDeadLetterEntry(
                3, Map.of("action", "3"), "http://test", "Error", 3, 1);

        assertFalse(entry.isExpired());

        long expectedExpiry = entry.getCreatedAt().plusSeconds(3600).toEpochMilli();
        long actualExpiry = entry.getExpiresAt().toEpochMilli();

        assertTrue(Math.abs(expectedExpiry - actualExpiry) < 1000);
    }

    @Test
    @DisplayName("Should record manual retries")
    void shouldRecordManualRetries() throws InterruptedException {
        InterfaceXDeadLetterEntry entry = new InterfaceXDeadLetterEntry(
                3, Map.of("action", "3"), "http://test", "Error", 3, 24);

        assertEquals(0, entry.getManualRetryCount());
        assertNull(entry.getLastRetryAttempt());

        entry.recordManualRetry();

        assertEquals(1, entry.getManualRetryCount());
        assertNotNull(entry.getLastRetryAttempt());

        Thread.sleep(10);

        Instant firstRetry = entry.getLastRetryAttempt();

        entry.recordManualRetry();

        assertEquals(2, entry.getManualRetryCount());
        assertTrue(entry.getLastRetryAttempt().isAfter(firstRetry));
    }

    @Test
    @DisplayName("Should convert command codes to names")
    void shouldConvertCommandCodesToNames() {
        assertEquals("NOTIFY_CHECK_CASE_CONSTRAINTS",
                new InterfaceXDeadLetterEntry(0, Map.of(), "http://test", "Error", 1, 24)
                        .getCommandName());

        assertEquals("NOTIFY_CHECK_ITEM_CONSTRAINTS",
                new InterfaceXDeadLetterEntry(1, Map.of(), "http://test", "Error", 1, 24)
                        .getCommandName());

        assertEquals("NOTIFY_WORKITEM_ABORT",
                new InterfaceXDeadLetterEntry(2, Map.of(), "http://test", "Error", 1, 24)
                        .getCommandName());

        assertEquals("NOTIFY_TIMEOUT",
                new InterfaceXDeadLetterEntry(3, Map.of(), "http://test", "Error", 1, 24)
                        .getCommandName());

        assertEquals("NOTIFY_RESOURCE_UNAVAILABLE",
                new InterfaceXDeadLetterEntry(4, Map.of(), "http://test", "Error", 1, 24)
                        .getCommandName());

        assertEquals("NOTIFY_CONSTRAINT_VIOLATION",
                new InterfaceXDeadLetterEntry(5, Map.of(), "http://test", "Error", 1, 24)
                        .getCommandName());

        assertEquals("NOTIFY_CANCELLED_CASE",
                new InterfaceXDeadLetterEntry(6, Map.of(), "http://test", "Error", 1, 24)
                        .getCommandName());

        assertEquals("UNKNOWN_COMMAND_99",
                new InterfaceXDeadLetterEntry(99, Map.of(), "http://test", "Error", 1, 24)
                        .getCommandName());
    }

    @Test
    @DisplayName("Should generate informative toString")
    void shouldGenerateToString() {
        InterfaceXDeadLetterEntry entry = new InterfaceXDeadLetterEntry(
                3, Map.of("action", "3"), "http://localhost:9999/service", "Error", 5, 24);

        String str = entry.toString();

        assertTrue(str.contains(entry.getId()));
        assertTrue(str.contains("NOTIFY_TIMEOUT"));
        assertTrue(str.contains("http://localhost:9999/service"));
        assertTrue(str.contains("attempts=5"));
    }
}
