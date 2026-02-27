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

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InterfaceXDeadLetterQueue.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Execution(ExecutionMode.SAME_THREAD)
class InterfaceXDeadLetterQueueTest {

    @BeforeEach
    void setUp() {
        InterfaceXDeadLetterQueue.shutdownInstance();
    }

    @AfterEach
    void tearDown() {
        InterfaceXDeadLetterQueue.shutdownInstance();
    }

    @Test
    @DisplayName("Should initialize singleton instance")
    void shouldInitializeSingleton() {
        assertFalse(InterfaceXDeadLetterQueue.isInitialized());

        InterfaceXDeadLetterQueue queue = InterfaceXDeadLetterQueue.initialize(null);

        assertTrue(InterfaceXDeadLetterQueue.isInitialized());
        assertNotNull(queue);
        assertSame(queue, InterfaceXDeadLetterQueue.getInstance());
    }

    @Test
    @DisplayName("Should add entry to dead letter queue")
    void shouldAddEntry() {
        InterfaceXDeadLetterQueue queue = InterfaceXDeadLetterQueue.initialize(null);
        Map<String, String> params = Map.of("action", "3", "caseID", "test-case-123");

        InterfaceXDeadLetterEntry entry = queue.add(
                3,
                params,
                "http://localhost:9999/service",
                "Connection refused",
                3);

        assertNotNull(entry);
        assertNotNull(entry.getId());
        assertEquals(3, entry.getCommand());
        assertEquals("Connection refused", entry.getFailureReason());
        assertEquals(3, entry.getAttemptCount());
        assertEquals(1, queue.size());
    }

    @Test
    @DisplayName("Should get entry by ID")
    void shouldGetEntryById() {
        InterfaceXDeadLetterQueue queue = InterfaceXDeadLetterQueue.initialize(null);
        Map<String, String> params = Map.of("action", "3");

        InterfaceXDeadLetterEntry added = queue.add(3, params, "http://test", "Error", 3);

        assertTrue(queue.get(added.getId()).isPresent());
        assertEquals(added.getId(), queue.get(added.getId()).get().getId());
    }

    @Test
    @DisplayName("Should remove entry by ID")
    void shouldRemoveEntry() {
        InterfaceXDeadLetterQueue queue = InterfaceXDeadLetterQueue.initialize(null);
        Map<String, String> params = Map.of("action", "3");

        InterfaceXDeadLetterEntry added = queue.add(3, params, "http://test", "Error", 3);

        assertTrue(queue.remove(added.getId()));
        assertEquals(0, queue.size());
        assertFalse(queue.get(added.getId()).isPresent());
    }

    @Test
    @DisplayName("Should filter entries by command type")
    void shouldFilterByCommand() {
        InterfaceXDeadLetterQueue queue = InterfaceXDeadLetterQueue.initialize(null);

        queue.add(3, Map.of("action", "3"), "http://test", "Error 1", 3);
        queue.add(3, Map.of("action", "3"), "http://test", "Error 2", 3);
        queue.add(6, Map.of("action", "6"), "http://test", "Error 3", 3);

        assertEquals(2, queue.getByCommand(3).size());
        assertEquals(1, queue.getByCommand(6).size());
        assertEquals(0, queue.getByCommand(99).size());
    }

    @Test
    @DisplayName("Should filter entries by observer URI")
    void shouldFilterByObserverURI() {
        InterfaceXDeadLetterQueue queue = InterfaceXDeadLetterQueue.initialize(null);

        queue.add(3, Map.of("action", "3"), "http://service1", "Error", 3);
        queue.add(3, Map.of("action", "3"), "http://service2", "Error", 3);
        queue.add(3, Map.of("action", "3"), "http://service1", "Error", 3);

        assertEquals(2, queue.getByObserverURI("http://service1").size());
        assertEquals(1, queue.getByObserverURI("http://service2").size());
    }

    @Test
    @DisplayName("Should clear all entries")
    void shouldClearAll() {
        InterfaceXDeadLetterQueue queue = InterfaceXDeadLetterQueue.initialize(null);

        queue.add(3, Map.of("action", "3"), "http://test", "Error", 3);
        queue.add(4, Map.of("action", "4"), "http://test", "Error", 3);

        assertEquals(2, queue.size());

        queue.clear();

        assertEquals(0, queue.size());
    }

    @Test
    @DisplayName("Should track total dead lettered count")
    void shouldTrackTotalDeadLettered() {
        InterfaceXDeadLetterQueue queue = InterfaceXDeadLetterQueue.initialize(null);

        queue.add(3, Map.of("action", "3"), "http://test", "Error", 3);
        queue.add(4, Map.of("action", "4"), "http://test", "Error", 3);

        assertEquals(2, queue.getTotalDeadLettered());

        queue.remove(queue.getAll().iterator().next().getId());

        assertEquals(2, queue.getTotalDeadLettered());
    }

    @Test
    @DisplayName("Should record manual retry attempts")
    void shouldRecordManualRetry() {
        InterfaceXDeadLetterQueue queue = InterfaceXDeadLetterQueue.initialize(null);

        InterfaceXDeadLetterEntry entry = queue.add(3, Map.of("action", "3"), "http://test", "Error", 3);

        assertEquals(0, entry.getManualRetryCount());
        assertNull(entry.getLastRetryAttempt());

        boolean result = queue.retryManually(entry.getId(), e -> {
            throw new RuntimeException("Still failing");
        });

        assertFalse(result);
        assertEquals(1, entry.getManualRetryCount());
        assertNotNull(entry.getLastRetryAttempt());
        assertEquals(1, queue.getTotalRetried());
        assertEquals(1, queue.size());

        result = queue.retryManually(entry.getId(), e -> {});

        assertTrue(result);
        assertEquals(0, queue.size());
    }

    @Test
    @DisplayName("Should handle manual retry for non-existent entry")
    void shouldHandleNonExistentManualRetry() {
        InterfaceXDeadLetterQueue queue = InterfaceXDeadLetterQueue.initialize(null);

        assertThrows(IllegalArgumentException.class, () ->
                queue.retryManually("non-existent-id", e -> {}));
    }

    @Test
    @DisplayName("Should close cleanly")
    void shouldCloseCleanly() {
        InterfaceXDeadLetterQueue queue = InterfaceXDeadLetterQueue.initialize(null);

        assertDoesNotThrow(queue::close);
    }
}
