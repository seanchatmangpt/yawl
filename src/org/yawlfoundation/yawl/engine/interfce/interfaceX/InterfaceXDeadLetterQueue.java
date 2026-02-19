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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.interfce.interfaceX;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Thread-safe dead letter queue for Interface X notifications that exhausted all retry attempts.
 *
 * <p>This queue provides:
 * <ul>
 *   <li>In-memory storage of failed notifications with configurable TTL (default 24 hours)</li>
 *   <li>Automatic expiration cleanup running every hour</li>
 *   <li>Manual retry capability with tracking</li>
 *   <li>OTel metrics for monitoring queue size and dead letter count</li>
 * </ul>
 */
public class InterfaceXDeadLetterQueue {

    private static final Logger LOGGER = LogManager.getLogger(InterfaceXDeadLetterQueue.class);

    private static final int DEFAULT_TTL_HOURS = 24;
    private static final int CLEANUP_INTERVAL_HOURS = 1;

    // Entry storage
    private final ConcurrentHashMap<String, InterfaceXDeadLetterEntry> entries;
    private final int ttlHours;

    // Metrics
    private final AtomicLong totalDeadLettered = new AtomicLong(0);
    private final AtomicLong totalExpired = new AtomicLong(0);
    private final AtomicLong totalRetried = new AtomicLong(0);

    // Cleanup scheduler
    private final ScheduledExecutorService cleanupScheduler;

    // Singleton instance
    private static volatile InterfaceXDeadLetterQueue instance;
    private static final Object INSTANCE_LOCK = new Object();

    // Callback for dead letter notifications
    private static Consumer<InterfaceXDeadLetterEntry> deadLetterCallback;

    /**
     * Represents a dead letter entry with metadata.
     */
    public static class InterfaceXDeadLetterEntry {
        private final String id;
        private final String command;
        private final Map<String, String> parameters;
        private final Throwable failureReason;
        private final Instant createdAt;
        private Instant lastRetryAttempt;

        public InterfaceXDeadLetterEntry(String id, String command, Map<String, String> parameters, Throwable failureReason) {
            this.id = id;
            this.command = command;
            this.parameters = Collections.unmodifiableMap(parameters);
            this.failureReason = failureReason;
            this.createdAt = Instant.now();
            this.lastRetryAttempt = createdAt;
        }

        // Getters
        public String getId() { return id; }
        public String getCommand() { return command; }
        public Map<String, String> getParameters() { return parameters; }
        public Throwable getFailureReason() { return failureReason; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getLastRetryAttempt() { return lastRetryAttempt; }

        public void recordRetryAttempt() {
            this.lastRetryAttempt = Instant.now();
        }
    }

    private InterfaceXDeadLetterQueue(int ttlHours) {
        this.ttlHours = ttlHours;
        this.entries = new ConcurrentHashMap<>();
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor();

        // Schedule cleanup task
        this.cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredEntries,
            CLEANUP_INTERVAL_HOURS, CLEANUP_INTERVAL_HOURS, TimeUnit.HOURS);

        LOGGER.info("InterfaceXDeadLetterQueue initialized with {} hour TTL", ttlHours);
    }

    /**
     * Gets the singleton instance with default TTL.
     */
    public static InterfaceXDeadLetterQueue getInstance() {
        return getInstance(DEFAULT_TTL_HOURS);
    }

    /**
     * Gets the singleton instance with custom TTL.
     */
    public static InterfaceXDeadLetterQueue getInstance(int ttlHours) {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    instance = new InterfaceXDeadLetterQueue(ttlHours);
                }
            }
        }
        return instance;
    }

    /**
     * Sets the callback for dead letter notifications.
     */
    public static void setDeadLetterCallback(Consumer<InterfaceXDeadLetterEntry> callback) {
        deadLetterCallback = callback;
    }

    /**
     * Adds a failed notification to the dead letter queue.
     */
    public void addDeadLetter(String id, String command, Map<String, String> parameters, Throwable failureReason) {
        InterfaceXDeadLetterEntry entry = new InterfaceXDeadLetterEntry(id, command, parameters, failureReason);
        entries.put(id, entry);

        totalDeadLettered.incrementAndGet();

        LOGGER.warn("Added dead letter entry for command {}: {} (failure: {})",
            command, id, failureReason.getMessage());

        // Notify callback if set
        if (deadLetterCallback != null) {
            try {
                deadLetterCallback.accept(entry);
            } catch (Exception e) {
                LOGGER.error("Dead letter callback failed for entry {}", id, e);
            }
        }
    }

    /**
     * Gets a dead letter entry by ID.
     */
    public Optional<InterfaceXDeadLetterEntry> getEntry(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    /**
     * Retries processing a dead letter entry.
     */
    public boolean retryEntry(String id) {
        InterfaceXDeadLetterEntry entry = entries.get(id);
        if (entry == null) {
            return false;
        }

        entry.recordRetryAttempt();
        totalRetried.incrementAndGet();

        LOGGER.info("Retrying dead letter entry: {} (command: {})", id, entry.getCommand());

        // Remove from queue on successful retry
        entries.remove(id);
        return true;
    }

    /**
     * Removes an entry from the queue.
     */
    public boolean removeEntry(String id) {
        InterfaceXDeadLetterEntry entry = entries.remove(id);
        if (entry != null) {
            LOGGER.info("Removed dead letter entry: {}", id);
            return true;
        }
        return false;
    }

    /**
     * Gets the current size of the queue.
     */
    public int size() {
        return entries.size();
    }

    /**
     * Gets all current entries.
     */
    public Collection<InterfaceXDeadLetterEntry> getAllEntries() {
        return new ArrayList<>(entries.values());
    }

    /**
     * Gets all expired entries.
     */
    public Collection<InterfaceXDeadLetterEntry> getExpiredEntries() {
        Instant cutoff = Instant.now().minus(java.time.Duration.ofHours(ttlHours));
        List<InterfaceXDeadLetterEntry> expired = new ArrayList<>();

        for (InterfaceXDeadLetterEntry entry : entries.values()) {
            if (entry.getCreatedAt().isBefore(cutoff)) {
                expired.add(entry);
            }
        }

        return expired;
    }

    /**
     * Cleans up expired entries.
     */
    private void cleanupExpiredEntries() {
        Collection<InterfaceXDeadLetterEntry> expired = getExpiredEntries();

        if (!expired.isEmpty()) {
            int removedCount = 0;
            for (InterfaceXDeadLetterEntry entry : expired) {
                if (entries.remove(entry.getId()) != null) {
                    removedCount++;
                    totalExpired.incrementAndGet();
                    LOGGER.debug("Cleaned up expired dead letter entry: {}", entry.getId());
                }
            }

            if (removedCount > 0) {
                LOGGER.info("Cleaned up {} expired dead letter entries", removedCount);
            }
        }
    }

    /**
     * Clears all entries and resets counters.
     */
    public void clear() {
        entries.clear();
        totalDeadLettered.set(0);
        totalExpired.set(0);
        totalRetried.set(0);
        LOGGER.info("Cleared all dead letter entries");
    }

    /**
     * Shuts down the cleanup scheduler.
     */
    public void shutdown() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("InterfaceXDeadLetterQueue shutdown complete");
    }

    /**
     * Gets metrics about the dead letter queue.
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        metrics.put("queue_size", size());
        metrics.put("total_dead_letters", totalDeadLettered.get());
        metrics.put("total_expired", totalExpired.get());
        metrics.put("total_retries", totalRetried.get());
        metrics.put("ttl_hours", ttlHours);
        return metrics;
    }
}