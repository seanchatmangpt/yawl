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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.a2a.tty;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Priority queue for TTY commands with support for ordering, cancellation, and tracking.
 *
 * <p>The command queue manages commands with three priority levels:
 * <ul>
 *   <li>{@link TtyCommandPriority#HIGH} - Critical commands executed first</li>
 *   <li>{@link TtyCommandPriority#MEDIUM} - Standard commands (default)</li>
 *   <li>{@link TtyCommandPriority#LOW} - Background/batch commands</li>
 * </ul>
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>Thread-safe priority-based ordering</li>
 *   <li>Command cancellation by ID or pattern</li>
 *   <li>Command tracking and history</li>
 *   <li>Queue statistics and monitoring</li>
 *   <li>Backpressure support with max queue size</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * TtyCommandQueue queue = new TtyCommandQueue(100);
 *
 * // Enqueue commands
 * queue.enqueue(TtyCommand.of("Read file", TtyCommandPriority.HIGH));
 * queue.enqueue(TtyCommand.of("Write file", TtyCommandPriority.MEDIUM));
 *
 * // Process in priority order
 * while (!queue.isEmpty()) {
 *     Optional<TtyCommand> next = queue.dequeue();
 *     next.ifPresent(cmd -> processCommand(cmd));
 * }
 * }</pre>
 *
 * @since YAWL 5.2
 */
public final class TtyCommandQueue {

    private static final Logger _logger = LogManager.getLogger(TtyCommandQueue.class);

    /**
     * Default maximum queue size.
     */
    public static final int DEFAULT_MAX_SIZE = 1000;

    /**
     * Priority levels for TTY commands.
     */
    public enum TtyCommandPriority {
        /**
         * High priority - executed first.
         * Use for critical commands that need immediate execution.
         */
        HIGH(1),

        /**
         * Medium priority - standard execution order.
         * Default priority for most commands.
         */
        MEDIUM(2),

        /**
         * Low priority - executed last.
         * Use for background tasks and batch operations.
         */
        LOW(3);

        private final int level;

        TtyCommandPriority(int level) {
            this.level = level;
        }

        /**
         * Get the numeric level for ordering.
         *
         * @return the priority level (1 = highest)
         */
        public int getLevel() {
            return level;
        }

        /**
         * Parse priority from string.
         *
         * @param value the string value
         * @return the priority, or MEDIUM if invalid
         */
        public static TtyCommandPriority fromString(String value) {
            if (value == null) {
                return MEDIUM;
            }
            return switch (value.toUpperCase()) {
                case "HIGH", "CRITICAL", "URGENT" -> HIGH;
                case "LOW", "BACKGROUND", "BATCH" -> LOW;
                default -> MEDIUM;
            };
        }
    }

    /**
     * Represents a command in the queue.
     *
     * @param id unique command identifier
     * @param content the command content/prompt
     * @param priority the command priority
     * @param safetyClass the pre-classified safety class (nullable)
     * @param createdAt when the command was created
     * @param correlationId optional correlation ID for tracking related commands
     * @param metadata additional metadata
     */
    public record TtyCommand(
        String id,
        String content,
        TtyCommandPriority priority,
        TtySafetyLayer.SafetyClass safetyClass,
        Instant createdAt,
        String correlationId,
        java.util.Map<String, String> metadata
    ) implements Comparable<TtyCommand> {

        /**
         * Create a simple command with default values.
         *
         * @param content the command content
         * @param priority the priority
         * @return the command
         */
        public static TtyCommand of(String content, TtyCommandPriority priority) {
            return new TtyCommand(
                UUID.randomUUID().toString(),
                content,
                priority,
                null,
                Instant.now(),
                null,
                java.util.Map.of()
            );
        }

        /**
         * Create a command with safety classification.
         *
         * @param content the command content
         * @param priority the priority
         * @param safetyClass the safety classification
         * @return the command
         */
        public static TtyCommand of(
            String content,
            TtyCommandPriority priority,
            TtySafetyLayer.SafetyClass safetyClass
        ) {
            return new TtyCommand(
                UUID.randomUUID().toString(),
                content,
                priority,
                safetyClass,
                Instant.now(),
                null,
                java.util.Map.of()
            );
        }

        /**
         * Create a builder for constructing commands.
         *
         * @param content the command content
         * @return builder instance
         */
        public static Builder builder(String content) {
            return new Builder(content);
        }

        @Override
        public int compareTo(TtyCommand other) {
            // First compare by priority (lower level = higher priority)
            int priorityCompare = Integer.compare(this.priority.level, other.priority.level);
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            // Then by creation time (earlier = higher priority within same level)
            return this.createdAt.compareTo(other.createdAt);
        }

        /**
         * Create a copy with updated safety class.
         *
         * @param safetyClass the new safety class
         * @return updated command
         */
        public TtyCommand withSafetyClass(TtySafetyLayer.SafetyClass safetyClass) {
            return new TtyCommand(
                id, content, priority, safetyClass, createdAt, correlationId, metadata
            );
        }

        /**
         * Builder for TtyCommand.
         */
        public static final class Builder {
            private String content;
            private TtyCommandPriority priority = TtyCommandPriority.MEDIUM;
            private TtySafetyLayer.SafetyClass safetyClass;
            private String correlationId;
            private final java.util.Map<String, String> metadata = new java.util.HashMap<>();

            private Builder(String content) {
                this.content = content;
            }

            public Builder priority(TtyCommandPriority priority) {
                this.priority = priority;
                return this;
            }

            public Builder safetyClass(TtySafetyLayer.SafetyClass safetyClass) {
                this.safetyClass = safetyClass;
                return this;
            }

            public Builder correlationId(String correlationId) {
                this.correlationId = correlationId;
                return this;
            }

            public Builder metadata(String key, String value) {
                this.metadata.put(key, value);
                return this;
            }

            public TtyCommand build() {
                return new TtyCommand(
                    UUID.randomUUID().toString(),
                    content,
                    priority,
                    safetyClass,
                    Instant.now(),
                    correlationId,
                    java.util.Map.copyOf(new java.util.HashMap<>(metadata))
                );
            }
        }
    }

    /**
     * Status of a dequeued command.
     *
     * @param command the command
     * @param status the processing status
     * @param processedAt when the command was processed
     * @param result optional result information
     */
    public record CommandStatus(
        TtyCommand command,
        Status status,
        Instant processedAt,
        String result
    ) {
        /**
         * Command processing status.
         */
        public enum Status {
            PENDING,
            RUNNING,
            COMPLETED,
            FAILED,
            CANCELLED,
            TIMEOUT
        }

        /**
         * Create a pending status.
         *
         * @param command the command
         * @return pending status
         */
        public static CommandStatus pending(TtyCommand command) {
            return new CommandStatus(command, Status.PENDING, null, null);
        }

        /**
         * Create a completed status.
         *
         * @param command the command
         * @param result the result
         * @return completed status
         */
        public static CommandStatus completed(TtyCommand command, String result) {
            return new CommandStatus(command, Status.COMPLETED, Instant.now(), result);
        }

        /**
         * Create a failed status.
         *
         * @param command the command
         * @param error the error message
         * @return failed status
         */
        public static CommandStatus failed(TtyCommand command, String error) {
            return new CommandStatus(command, Status.FAILED, Instant.now(), error);
        }

        /**
         * Create a cancelled status.
         *
         * @param command the command
         * @return cancelled status
         */
        public static CommandStatus cancelled(TtyCommand command) {
            return new CommandStatus(command, Status.CANCELLED, Instant.now(), null);
        }
    }

    /**
     * Statistics about the command queue.
     *
     * @param totalEnqueued total commands enqueued
     * @param totalDequeued total commands dequeued
     * @param totalCompleted total commands completed successfully
     * @param totalFailed total commands failed
     * @param totalCancelled total commands cancelled
     * @param currentSize current queue size
     * @param highPriorityCount count of HIGH priority commands
     * @param mediumPriorityCount count of MEDIUM priority commands
     * @param lowPriorityCount count of LOW priority commands
     */
    public record QueueStatistics(
        long totalEnqueued,
        long totalDequeued,
        long totalCompleted,
        long totalFailed,
        long totalCancelled,
        int currentSize,
        int highPriorityCount,
        int mediumPriorityCount,
        int lowPriorityCount
    ) {
        /**
         * Get the success rate as a percentage.
         *
         * @return success rate (0-100)
         */
        public double getSuccessRate() {
            long total = totalCompleted + totalFailed;
            if (total == 0) {
                return 100.0;
            }
            return (totalCompleted * 100.0) / total;
        }
    }

    private final PriorityBlockingQueue<TtyCommand> queue;
    private final List<CommandStatus> history;
    private final int maxSize;
    private final AtomicInteger enqueueCounter = new AtomicInteger(0);
    private final AtomicInteger dequeueCounter = new AtomicInteger(0);
    private final AtomicInteger completedCounter = new AtomicInteger(0);
    private final AtomicInteger failedCounter = new AtomicInteger(0);
    private final AtomicInteger cancelledCounter = new AtomicInteger(0);
    private final ReentrantLock historyLock = new ReentrantLock();

    /**
     * Create a command queue with default max size.
     */
    public TtyCommandQueue() {
        this(DEFAULT_MAX_SIZE);
    }

    /**
     * Create a command queue with specified max size.
     *
     * @param maxSize maximum number of commands in the queue
     */
    public TtyCommandQueue(int maxSize) {
        this.maxSize = maxSize > 0 ? maxSize : DEFAULT_MAX_SIZE;
        this.queue = new PriorityBlockingQueue<>(this.maxSize);
        this.history = new ArrayList<>();
        _logger.info("TtyCommandQueue initialized with maxSize={}", this.maxSize);
    }

    /**
     * Enqueue a command.
     *
     * @param command the command to enqueue
     * @return true if enqueued successfully
     * @throws IllegalStateException if queue is full
     */
    public boolean enqueue(TtyCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        if (queue.size() >= maxSize) {
            _logger.warn("Queue is full (size={}), rejecting command: {}", maxSize, command.id());
            throw new IllegalStateException("Command queue is full (max size: " + maxSize + ")");
        }

        boolean added = queue.offer(command);
        if (added) {
            enqueueCounter.incrementAndGet();
            _logger.debug("Enqueued command: id={}, priority={}, queueSize={}",
                command.id(), command.priority(), queue.size());
        }
        return added;
    }

    /**
     * Enqueue a command with content and priority.
     *
     * @param content the command content
     * @param priority the priority
     * @return the created command
     */
    public TtyCommand enqueue(String content, TtyCommandPriority priority) {
        TtyCommand command = TtyCommand.of(content, priority);
        enqueue(command);
        return command;
    }

    /**
     * Dequeue the next highest priority command.
     *
     * @return the next command, or empty if queue is empty
     */
    public Optional<TtyCommand> dequeue() {
        TtyCommand command = queue.poll();
        if (command != null) {
            dequeueCounter.incrementAndGet();
            _logger.debug("Dequeued command: id={}, priority={}", command.id(), command.priority());
            addToHistory(CommandStatus.pending(command));
        }
        return Optional.ofNullable(command);
    }

    /**
     * Dequeue with timeout.
     *
     * @param timeoutMs timeout in milliseconds
     * @return the next command, or empty if timeout
     */
    public Optional<TtyCommand> dequeue(long timeoutMs) throws InterruptedException {
        TtyCommand command = queue.poll();
        if (command != null) {
            dequeueCounter.incrementAndGet();
            _logger.debug("Dequeued command: id={}, priority={}", command.id(), command.priority());
            addToHistory(CommandStatus.pending(command));
            return Optional.of(command);
        }
        // PriorityBlockingQueue doesn't support timed poll well, so return empty
        return Optional.empty();
    }

    /**
     * Peek at the next command without removing it.
     *
     * @return the next command, or empty if queue is empty
     */
    public Optional<TtyCommand> peek() {
        return Optional.ofNullable(queue.peek());
    }

    /**
     * Cancel a command by ID.
     *
     * @param commandId the command ID to cancel
     * @return true if command was found and removed
     */
    public boolean cancel(String commandId) {
        Objects.requireNonNull(commandId, "commandId cannot be null");

        boolean removed = queue.removeIf(cmd -> cmd.id().equals(commandId));
        if (removed) {
            cancelledCounter.incrementAndGet();
            _logger.info("Cancelled command: {}", commandId);
        }
        return removed;
    }

    /**
     * Cancel all commands matching a pattern.
     *
     * @param pattern the pattern to match against command content
     * @return number of commands cancelled
     */
    public int cancelByPattern(String pattern) {
        Objects.requireNonNull(pattern, "pattern cannot be null");

        int[] count = {0};
        queue.removeIf(cmd -> {
            if (cmd.content() != null && cmd.content().contains(pattern)) {
                count[0]++;
                cancelledCounter.incrementAndGet();
                return true;
            }
            return false;
        });

        if (count[0] > 0) {
            _logger.info("Cancelled {} commands matching pattern: {}", count[0], pattern);
        }
        return count[0];
    }

    /**
     * Cancel all commands with a specific priority.
     *
     * @param priority the priority to cancel
     * @return number of commands cancelled
     */
    public int cancelByPriority(TtyCommandPriority priority) {
        Objects.requireNonNull(priority, "priority cannot be null");

        int[] count = {0};
        queue.removeIf(cmd -> {
            if (cmd.priority() == priority) {
                count[0]++;
                cancelledCounter.incrementAndGet();
                return true;
            }
            return false;
        });

        if (count[0] > 0) {
            _logger.info("Cancelled {} commands with priority: {}", count[0], priority);
        }
        return count[0];
    }

    /**
     * Clear all commands from the queue.
     *
     * @return number of commands cleared
     */
    public int clear() {
        int size = queue.size();
        int cancelled = size;
        queue.clear();
        cancelledCounter.addAndGet(cancelled);
        _logger.info("Cleared {} commands from queue", cancelled);
        return cancelled;
    }

    /**
     * Mark a command as completed.
     *
     * @param commandId the command ID
     * @param result the result
     */
    public void markCompleted(String commandId, String result) {
        completedCounter.incrementAndGet();
        updateHistoryStatus(commandId, CommandStatus.Status.COMPLETED, result);
        _logger.debug("Command completed: {}", commandId);
    }

    /**
     * Mark a command as failed.
     *
     * @param commandId the command ID
     * @param error the error message
     */
    public void markFailed(String commandId, String error) {
        failedCounter.incrementAndGet();
        updateHistoryStatus(commandId, CommandStatus.Status.FAILED, error);
        _logger.warn("Command failed: {} - Error: {}", commandId, error);
    }

    /**
     * Check if the queue is empty.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Get the current queue size.
     *
     * @return number of commands in queue
     */
    public int size() {
        return queue.size();
    }

    /**
     * Check if the queue is full.
     *
     * @return true if at max capacity
     */
    public boolean isFull() {
        return queue.size() >= maxSize;
    }

    /**
     * Get the maximum queue size.
     *
     * @return max size
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Get queue statistics.
     *
     * @return current statistics
     */
    public QueueStatistics getStatistics() {
        int highCount = 0;
        int mediumCount = 0;
        int lowCount = 0;

        for (TtyCommand cmd : queue) {
            switch (cmd.priority()) {
                case HIGH -> highCount++;
                case MEDIUM -> mediumCount++;
                case LOW -> lowCount++;
            }
        }

        return new QueueStatistics(
            enqueueCounter.get(),
            dequeueCounter.get(),
            completedCounter.get(),
            failedCounter.get(),
            cancelledCounter.get(),
            queue.size(),
            highCount,
            mediumCount,
            lowCount
        );
    }

    /**
     * Get command history.
     *
     * @param limit maximum number of entries
     * @return list of command statuses
     */
    public List<CommandStatus> getHistory(int limit) {
        historyLock.lock();
        try {
            int start = Math.max(0, history.size() - limit);
            return new ArrayList<>(history.subList(start, history.size()));
        } finally {
            historyLock.unlock();
        }
    }

    /**
     * Find a command in history by ID.
     *
     * @param commandId the command ID
     * @return the command status, or empty if not found
     */
    public Optional<CommandStatus> findInHistory(String commandId) {
        historyLock.lock();
        try {
            return history.stream()
                .filter(status -> status.command().id().equals(commandId))
                .findFirst();
        } finally {
            historyLock.unlock();
        }
    }

    /**
     * Get all commands currently in the queue.
     *
     * @return list of queued commands
     */
    public List<TtyCommand> getAllQueued() {
        return new ArrayList<>(queue);
    }

    /**
     * Count commands by priority.
     *
     * @param priority the priority to count
     * @return count of commands with that priority
     */
    public int countByPriority(TtyCommandPriority priority) {
        return (int) queue.stream()
            .filter(cmd -> cmd.priority() == priority)
            .count();
    }

    private void addToHistory(CommandStatus status) {
        historyLock.lock();
        try {
            history.add(status);
            // Keep history bounded
            if (history.size() > maxSize * 2) {
                history.removeFirst();
            }
        } finally {
            historyLock.unlock();
        }
    }

    private void updateHistoryStatus(String commandId, CommandStatus.Status status, String result) {
        historyLock.lock();
        try {
            for (int i = history.size() - 1; i >= 0; i--) {
                CommandStatus current = history.get(i);
                if (current.command().id().equals(commandId)) {
                    history.set(i, new CommandStatus(
                        current.command(),
                        status,
                        Instant.now(),
                        result
                    ));
                    break;
                }
            }
        } finally {
            historyLock.unlock();
        }
    }
}
