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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TtyCommandQueue with Java 25 and Resilience4j patterns.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class TtyCommandQueueTest {

    private TtyCommandQueue queue;

    @BeforeEach
    void setUp() {
        queue = new TtyCommandQueue(10);
    }

    @Test
    @DisplayName("Should create queue with default size")
    void shouldCreateQueueWithDefaultSize() {
        TtyCommandQueue defaultQueue = new TtyCommandQueue();
        assertEquals(TtyCommandQueue.DEFAULT_MAX_SIZE, defaultQueue.getMaxSize());
    }

    @Test
    @DisplayName("Should create queue with custom size")
    void shouldCreateQueueWithCustomSize() {
        TtyCommandQueue customQueue = new TtyCommandQueue(50);
        assertEquals(50, customQueue.getMaxSize());
    }

    @Test
    @DisplayName("Should enqueue and dequeue commands in priority order")
    void shouldEnqueueDequeuePriorityOrder() {
        queue.enqueue("low priority", TtyCommandQueue.TtyCommandPriority.LOW);
        queue.enqueue("high priority", TtyCommandQueue.TtyCommandPriority.HIGH);
        queue.enqueue("medium priority", TtyCommandQueue.TtyCommandPriority.MEDIUM);

        Optional<TtyCommandQueue.TtyCommand> cmd1 = queue.dequeue();
        assertTrue(cmd1.isPresent());
        assertEquals(TtyCommandQueue.TtyCommandPriority.HIGH, cmd1.get().priority());

        Optional<TtyCommandQueue.TtyCommand> cmd2 = queue.dequeue();
        assertTrue(cmd2.isPresent());
        assertEquals(TtyCommandQueue.TtyCommandPriority.MEDIUM, cmd2.get().priority());

        Optional<TtyCommandQueue.TtyCommand> cmd3 = queue.dequeue();
        assertTrue(cmd3.isPresent());
        assertEquals(TtyCommandQueue.TtyCommandPriority.LOW, cmd3.get().priority());
    }

    @Test
    @DisplayName("Should reject on full queue with proper exception")
    void shouldRejectOnFullQueue() {
        for (int i = 0; i < 10; i++) {
            queue.enqueue("command " + i, TtyCommandQueue.TtyCommandPriority.MEDIUM);
        }

        assertThrows(IllegalStateException.class, () ->
                queue.enqueue("overflow", TtyCommandQueue.TtyCommandPriority.HIGH));
    }

    @Test
    @DisplayName("Should cancel command by ID")
    void shouldCancelCommandById() {
        TtyCommandQueue.TtyCommand cmd = queue.enqueue("test", TtyCommandQueue.TtyCommandPriority.MEDIUM);
        assertEquals(1, queue.size());

        boolean cancelled = queue.cancel(cmd.id());
        assertTrue(cancelled);
        assertEquals(0, queue.size());
    }

    @Test
    @DisplayName("Should cancel commands by pattern")
    void shouldCancelCommandsByPattern() {
        queue.enqueue("delete_user_1", TtyCommandQueue.TtyCommandPriority.MEDIUM);
        queue.enqueue("delete_user_2", TtyCommandQueue.TtyCommandPriority.MEDIUM);
        queue.enqueue("create_user", TtyCommandQueue.TtyCommandPriority.MEDIUM);

        int cancelled = queue.cancelByPattern("delete_user");
        assertEquals(2, cancelled);
        assertEquals(1, queue.size());
    }

    @Test
    @DisplayName("Should cancel commands by priority")
    void shouldCancelCommandsByPriority() {
        queue.enqueue("low 1", TtyCommandQueue.TtyCommandPriority.LOW);
        queue.enqueue("low 2", TtyCommandQueue.TtyCommandPriority.LOW);
        queue.enqueue("high", TtyCommandQueue.TtyCommandPriority.HIGH);

        int cancelled = queue.cancelByPriority(TtyCommandQueue.TtyCommandPriority.LOW);
        assertEquals(2, cancelled);
        assertEquals(1, queue.size());
    }

    @Test
    @DisplayName("Should provide metrics for Micrometer/Prometheus")
    void shouldProvideMetricsForMonitoring() {
        queue.enqueue("cmd1", TtyCommandQueue.TtyCommandPriority.HIGH);
        queue.enqueue("cmd2", TtyCommandQueue.TtyCommandPriority.MEDIUM);

        Map<String, Number> metrics = queue.getMetrics();

        assertNotNull(metrics);
        assertEquals(2, metrics.get("queue.size"));
        assertEquals(10, metrics.get("queue.max_size"));
        assertEquals(20.0, metrics.get("queue.utilization_percent"));
        assertEquals(2, metrics.get("commands.enqueued.total"));
        assertEquals(0, metrics.get("commands.dequeued.total"));
        assertEquals(0, metrics.get("commands.completed.total"));
        assertEquals(0, metrics.get("commands.failed.total"));
    }

    @Test
    @DisplayName("Should track command completion status")
    void shouldTrackCommandCompletionStatus() {
        TtyCommandQueue.TtyCommand cmd = queue.enqueue("test", TtyCommandQueue.TtyCommandPriority.MEDIUM);
        queue.dequeue();

        queue.markCompleted(cmd.id(), "success result");

        Map<String, Number> metrics = queue.getMetrics();
        assertEquals(1, metrics.get("commands.completed.total"));
        assertEquals(100.0, metrics.get("commands.success_rate_percent"));
    }

    @Test
    @DisplayName("Should track command failures")
    void shouldTrackCommandFailures() {
        TtyCommandQueue.TtyCommand cmd1 = queue.enqueue("test1", TtyCommandQueue.TtyCommandPriority.MEDIUM);
        TtyCommandQueue.TtyCommand cmd2 = queue.enqueue("test2", TtyCommandQueue.TtyCommandPriority.MEDIUM);

        queue.dequeue();
        queue.dequeue();

        queue.markCompleted(cmd1.id(), "success");
        queue.markFailed(cmd2.id(), "error message");

        Map<String, Number> metrics = queue.getMetrics();
        assertEquals(1, metrics.get("commands.completed.total"));
        assertEquals(1, metrics.get("commands.failed.total"));
        assertEquals(50.0, metrics.get("commands.success_rate_percent"));
    }

    @Test
    @DisplayName("Should track command statistics")
    void shouldTrackCommandStatistics() {
        TtyCommandQueue.QueueStatistics initialStats = queue.getStatistics();

        queue.enqueue("high", TtyCommandQueue.TtyCommandPriority.HIGH);
        queue.enqueue("medium1", TtyCommandQueue.TtyCommandPriority.MEDIUM);
        queue.enqueue("medium2", TtyCommandQueue.TtyCommandPriority.MEDIUM);
        queue.enqueue("low", TtyCommandQueue.TtyCommandPriority.LOW);

        TtyCommandQueue.QueueStatistics stats = queue.getStatistics();

        assertEquals(4, stats.currentSize());
        assertEquals(4, stats.totalEnqueued());
        assertEquals(1, stats.highPriorityCount());
        assertEquals(2, stats.mediumPriorityCount());
        assertEquals(1, stats.lowPriorityCount());
    }

    @Test
    @DisplayName("Should maintain command history with ReentrantLock")
    void shouldMaintainCommandHistoryThreadSafe() throws InterruptedException {
        TtyCommandQueue.TtyCommand cmd = queue.enqueue("test", TtyCommandQueue.TtyCommandPriority.MEDIUM);

        Thread dequeueThread = new Thread(() -> {
            Optional<TtyCommandQueue.TtyCommand> dequeued = queue.dequeue();
            if (dequeued.isPresent()) {
                queue.markCompleted(dequeued.get().id(), "done");
            }
        });

        Thread historyThread = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                queue.getHistory(5);
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        dequeueThread.start();
        historyThread.start();

        dequeueThread.join(2000);
        historyThread.join(2000);

        Optional<TtyCommandQueue.CommandStatus> status = queue.findInHistory(cmd.id());
        assertTrue(status.isPresent());
    }

    @Test
    @DisplayName("Should clear queue and reset counters")
    void shouldClearQueueResetCounters() {
        queue.enqueue("cmd1", TtyCommandQueue.TtyCommandPriority.MEDIUM);
        queue.enqueue("cmd2", TtyCommandQueue.TtyCommandPriority.MEDIUM);

        assertEquals(2, queue.size());
        assertEquals(2, queue.getStatistics().totalEnqueued());

        int cleared = queue.clear();
        assertEquals(2, cleared);
        assertEquals(0, queue.size());
        assertEquals(2, queue.getStatistics().totalCancelled());
    }

    @Test
    @DisplayName("Should return false for cancel non-existent command")
    void shouldReturnFalseCancelNonExistent() {
        boolean cancelled = queue.cancel("non-existent-id");
        assertFalse(cancelled);
    }

    @Test
    @DisplayName("Should peek without removing command")
    void shouldPeekWithoutRemoving() {
        TtyCommandQueue.TtyCommand cmd = queue.enqueue("test", TtyCommandQueue.TtyCommandPriority.HIGH);

        Optional<TtyCommandQueue.TtyCommand> peeked = queue.peek();
        assertTrue(peeked.isPresent());
        assertEquals(cmd.id(), peeked.get().id());

        // Command should still be in queue
        assertEquals(1, queue.size());
    }

    @Test
    @DisplayName("Should check queue full status")
    void shouldCheckQueueFullStatus() {
        assertFalse(queue.isFull());

        for (int i = 0; i < 10; i++) {
            queue.enqueue("cmd " + i, TtyCommandQueue.TtyCommandPriority.MEDIUM);
        }

        assertTrue(queue.isFull());
    }

    @Test
    @DisplayName("Should get all queued commands")
    void shouldGetAllQueuedCommands() {
        queue.enqueue("cmd1", TtyCommandQueue.TtyCommandPriority.HIGH);
        queue.enqueue("cmd2", TtyCommandQueue.TtyCommandPriority.MEDIUM);

        java.util.List<TtyCommandQueue.TtyCommand> all = queue.getAllQueued();
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("Should count commands by priority")
    void shouldCountCommandsByPriority() {
        queue.enqueue("h1", TtyCommandQueue.TtyCommandPriority.HIGH);
        queue.enqueue("h2", TtyCommandQueue.TtyCommandPriority.HIGH);
        queue.enqueue("m", TtyCommandQueue.TtyCommandPriority.MEDIUM);

        assertEquals(2, queue.countByPriority(TtyCommandQueue.TtyCommandPriority.HIGH));
        assertEquals(1, queue.countByPriority(TtyCommandQueue.TtyCommandPriority.MEDIUM));
        assertEquals(0, queue.countByPriority(TtyCommandQueue.TtyCommandPriority.LOW));
    }
}
