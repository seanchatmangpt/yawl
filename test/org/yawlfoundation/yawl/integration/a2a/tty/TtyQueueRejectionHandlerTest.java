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

package org.yawlfoundation.yawl.integration.a2a.tty;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for TtyQueueRejectionHandler with Resilience4j Bulkhead.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class TtyQueueRejectionHandlerTest {

    private TtyCommandQueue queue;
    private TtyQueueRejectionHandler handler;
    private BulkheadRegistry registry;

    @BeforeEach
    void setUp() {
        queue = new TtyCommandQueue(10);
        handler = new TtyQueueRejectionHandler(queue);
        registry = BulkheadRegistry.ofDefaults();
    }

    @Test
    @DisplayName("Should create handler with valid queue")
    void shouldCreateHandlerWithValidQueue() {
        assertNotNull(handler);
        assertEquals(queue, handler.queue);
    }

    @Test
    @DisplayName("Should reject null queue in constructor")
    void shouldRejectNullQueue() {
        assertThrows(IllegalArgumentException.class, () ->
                new TtyQueueRejectionHandler(null));
    }

    @Test
    @DisplayName("Should record rejection events")
    void shouldRecordRejectionEvents() {
        assertEquals(0, handler.getRejectionCount());

        handler.recordRejection();
        assertEquals(1, handler.getRejectionCount());

        handler.recordRejection();
        handler.recordRejection();
        assertEquals(3, handler.getRejectionCount());
    }

    @Test
    @DisplayName("Should reset rejection counter")
    void shouldResetRejectionCounter() {
        handler.recordRejection();
        handler.recordRejection();
        assertEquals(2, handler.getRejectionCount());

        handler.resetRejectionCount();
        assertEquals(0, handler.getRejectionCount());
    }

    @Test
    @DisplayName("Should provide queue metrics")
    void shouldProvideQueueMetrics() {
        queue.enqueue("test command", TtyCommandQueue.TtyCommandPriority.MEDIUM);

        Map<String, Number> metrics = handler.getMetrics();

        assertNotNull(metrics);
        assertTrue(metrics.containsKey("queue.size"));
        assertTrue(metrics.containsKey("queue.max_size"));
        assertTrue(metrics.containsKey("queue.utilization_percent"));
        assertTrue(metrics.containsKey("commands.enqueued.total"));
        assertTrue(metrics.containsKey("queue.rejections.total"));

        assertEquals(1, metrics.get("queue.size"));
        assertEquals(10, metrics.get("queue.max_size"));
    }

    @Test
    @DisplayName("Should create valid monitoring task")
    void shouldCreateValidMonitoringTask() {
        Runnable monitoringTask = handler.createMonitoringTask();
        assertNotNull(monitoringTask);
    }

    @Test
    @DisplayName("Should handle bulkhead entry addition")
    void shouldHandleBulkheadEntryAddition() {
        Bulkhead bulkhead = registry.bulkhead("test-bulkhead");

        assertDoesNotThrow(() ->
                handler.onEntryAdded(
                        new io.github.resilience4j.core.registry.EntryAddedEvent<>(bulkhead)));
    }

    @Test
    @DisplayName("Should handle bulkhead entry removal")
    void shouldHandleBulkheadEntryRemoval() {
        Bulkhead bulkhead = registry.bulkhead("test-bulkhead");

        assertDoesNotThrow(() ->
                handler.onEntryRemoved(
                        new io.github.resilience4j.core.registry.EntryRemovedEvent<>(bulkhead)));
    }

    @Test
    @DisplayName("Should handle bulkhead entry replacement")
    void shouldHandleBulkheadEntryReplacement() {
        Bulkhead oldBulkhead = registry.bulkhead("old-bulkhead");
        Bulkhead newBulkhead = registry.bulkhead("new-bulkhead");

        assertDoesNotThrow(() ->
                handler.onEntryReplaced(
                        new io.github.resilience4j.core.registry.EntryReplacedEvent<>(
                                oldBulkhead, newBulkhead)));
    }

    @Test
    @DisplayName("Should track high queue utilization")
    void shouldTrackHighQueueUtilization() {
        // Fill the queue to near capacity
        for (int i = 0; i < 8; i++) {
            queue.enqueue("command " + i, TtyCommandQueue.TtyCommandPriority.MEDIUM);
        }

        Map<String, Number> metrics = handler.getMetrics();
        double utilization = metrics.get("queue.utilization_percent").doubleValue();

        assertTrue(utilization > 70.0, "Queue utilization should be > 70%");
    }

    @Test
    @DisplayName("Should integrate metrics with queue statistics")
    void shouldIntegrateMetricsWithQueueStatistics() {
        // Enqueue and process some commands
        TtyCommandQueue.TtyCommand cmd = queue.enqueue("test", TtyCommandQueue.TtyCommandPriority.HIGH);
        queue.dequeue();
        queue.markCompleted(cmd.id(), "success");

        Map<String, Number> metrics = handler.getMetrics();

        assertEquals(1L, metrics.get("commands.enqueued.total"));
        assertEquals(1L, metrics.get("commands.dequeued.total"));
        assertEquals(1L, metrics.get("commands.completed.total"));
        assertEquals(100.0, metrics.get("commands.success_rate_percent"));
    }
}
