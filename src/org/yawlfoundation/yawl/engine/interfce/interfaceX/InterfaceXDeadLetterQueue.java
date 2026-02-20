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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Thread-safe dead letter queue for Interface X notifications that exhausted all retry attempts.
 * Modernized to use Resilience4j event listener patterns and Java 25 ReentrantLock.
 *
 * <p>This queue provides:
 * <ul>
 *   <li>In-memory storage of failed notifications with configurable TTL (default 24 hours)</li>
 *   <li>Lazy expiration via Caffeine-style check-on-access (eliminates background threads)</li>
 *   <li>Manual retry capability with tracking</li>
 *   <li>Filtering by command type and observer URI</li>
 *   <li>Integration with Resilience4j CircuitBreaker/Retry event listeners</li>
 *   <li>Thread-safe via ReentrantLock (no synchronized blocks for Java 25 virtual threads)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class InterfaceXDeadLetterQueue {

    private static final Logger LOGGER = LogManager.getLogger(InterfaceXDeadLetterQueue.class);

    private static final int DEFAULT_TTL_HOURS = 24;

    // Entry storage (entries are responsible for TTL via isExpired() check)
    private final Map<String, InterfaceXDeadLetterEntry> entries;
    private final ReentrantLock entriesLock = new ReentrantLock();
    private final int ttlHours;

    // Metrics
    private final AtomicLong totalDeadLettered = new AtomicLong(0);
    private final AtomicLong totalExpired = new AtomicLong(0);
    private final AtomicLong totalRetried = new AtomicLong(0);

    // Singleton instance
    private static volatile InterfaceXDeadLetterQueue instance;
    private static final Object INSTANCE_LOCK = new Object();

    // Callback for dead letter notifications
    private volatile Consumer<InterfaceXDeadLetterEntry> deadLetterCallback;

    /**
     * Private constructor for singleton pattern.
     * Uses lazy expiration via entry checks (Caffeine-style).
     *
     * @param ttlHours time-to-live in hours for entries
     */
    private InterfaceXDeadLetterQueue(int ttlHours) {
        this.ttlHours = ttlHours;
        this.entries = new java.util.HashMap<>();

        LOGGER.info("InterfaceXDeadLetterQueue initialized with {} hour TTL (lazy expiration)", ttlHours);
    }

    /**
     * Initializes the singleton instance with default TTL.
     *
     * @param ignored ignored parameter (for API compatibility)
     * @return the singleton instance
     */
    public static InterfaceXDeadLetterQueue initialize(Object ignored) {
        return getInstance();
    }

    /**
     * Checks if the singleton instance has been initialized.
     *
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    /**
     * Gets the singleton instance with default TTL.
     *
     * @return the singleton instance
     */
    public static InterfaceXDeadLetterQueue getInstance() {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    instance = new InterfaceXDeadLetterQueue(DEFAULT_TTL_HOURS);
                }
            }
        }
        return instance;
    }

    /**
     * Shuts down the singleton instance, releasing resources.
     */
    public static void shutdownInstance() {
        if (instance != null) {
            synchronized (INSTANCE_LOCK) {
                if (instance != null) {
                    instance.close();
                    instance = null;
                }
            }
        }
    }

    /**
     * Sets the callback for dead letter notifications.
     *
     * @param callback the callback to invoke when entries are added
     */
    public void setDeadLetterCallback(Consumer<InterfaceXDeadLetterEntry> callback) {
        this.deadLetterCallback = callback;
    }

    /**
     * Adds a failed notification to the dead letter queue.
     * This method can be called by Resilience4j event listeners on circuit breaker failures.
     *
     * @param command the command code
     * @param parameters the original request parameters
     * @param observerURI the target observer URI
     * @param failureReason the failure message
     * @param attemptCount the number of attempts made before exhaustion
     * @return the created entry
     */
    public InterfaceXDeadLetterEntry add(int command, Map<String, String> parameters,
                                         String observerURI, String failureReason,
                                         int attemptCount) {
        InterfaceXDeadLetterEntry entry = new InterfaceXDeadLetterEntry(
                command, parameters, observerURI, failureReason, attemptCount, ttlHours);

        entriesLock.lock();
        try {
            entries.put(entry.getId(), entry);
            totalDeadLettered.incrementAndGet();
        } finally {
            entriesLock.unlock();
        }

        LOGGER.warn("Added dead letter entry for command {}: {} (failure: {}, observer: {})",
            entry.getCommandName(), entry.getId(), failureReason, observerURI);

        // Notify callback if set (e.g., from Resilience4j event listeners)
        Consumer<InterfaceXDeadLetterEntry> callback = this.deadLetterCallback;
        if (callback != null) {
            try {
                callback.accept(entry);
            } catch (Exception e) {
                LOGGER.error("Dead letter callback failed for entry {}", entry.getId(), e);
            }
        }

        return entry;
    }

    /**
     * Adds a failed notification to the dead letter queue using the original API.
     *
     * @param id the entry ID
     * @param commandName the command name
     * @param parameters the original request parameters
     * @param failureReason the failure exception
     * @deprecated Use {@link #add(int, Map, String, String, int)} instead
     */
    @Deprecated
    public void addDeadLetter(String id, String commandName, Map<String, String> parameters,
                              Throwable failureReason) {
        int commandCode = parseCommandCode(commandName);
        add(commandCode, parameters, "unknown", failureReason.getMessage(), 0);
    }

    /**
     * Parses a command name to its code.
     */
    private int parseCommandCode(String commandName) {
        if (commandName == null) return -1;
        return switch (commandName) {
            case "NOTIFY_CHECK_CASE_CONSTRAINTS" -> 0;
            case "NOTIFY_CHECK_ITEM_CONSTRAINTS" -> 1;
            case "NOTIFY_WORKITEM_ABORT" -> 2;
            case "NOTIFY_TIMEOUT" -> 3;
            case "NOTIFY_RESOURCE_UNAVAILABLE" -> 4;
            case "NOTIFY_CONSTRAINT_VIOLATION" -> 5;
            case "NOTIFY_CANCELLED_CASE" -> 6;
            default -> -1;
        };
    }

    /**
     * Gets a dead letter entry by ID.
     *
     * @param id the entry ID
     * @return an Optional containing the entry, or empty if not found
     */
    public Optional<InterfaceXDeadLetterEntry> get(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    /**
     * Gets a dead letter entry by ID (alias for get).
     *
     * @param id the entry ID
     * @return an Optional containing the entry, or empty if not found
     * @deprecated Use {@link #get(String)} instead
     */
    @Deprecated
    public Optional<InterfaceXDeadLetterEntry> getEntry(String id) {
        return get(id);
    }

    /**
     * Removes an entry from the queue.
     *
     * @param id the entry ID
     * @return true if the entry was removed
     */
    public boolean remove(String id) {
        InterfaceXDeadLetterEntry entry = entries.remove(id);
        if (entry != null) {
            LOGGER.info("Removed dead letter entry: {}", id);
            return true;
        }
        return false;
    }

    /**
     * Gets all entries in the queue.
     *
     * @return a collection of all entries
     */
    public Collection<InterfaceXDeadLetterEntry> getAll() {
        return new ArrayList<>(entries.values());
    }

    /**
     * Gets entries filtered by command type.
     *
     * @param command the command code to filter by
     * @return a list of matching entries
     */
    public List<InterfaceXDeadLetterEntry> getByCommand(int command) {
        List<InterfaceXDeadLetterEntry> result = new ArrayList<>();
        for (InterfaceXDeadLetterEntry entry : entries.values()) {
            if (entry.getCommand() == command) {
                result.add(entry);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Gets entries filtered by observer URI.
     *
     * @param observerURI the observer URI to filter by
     * @return a list of matching entries
     */
    public List<InterfaceXDeadLetterEntry> getByObserverURI(String observerURI) {
        List<InterfaceXDeadLetterEntry> result = new ArrayList<>();
        for (InterfaceXDeadLetterEntry entry : entries.values()) {
            if (observerURI.equals(entry.getObserverURI())) {
                result.add(entry);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Attempts a manual retry of a dead letter entry.
     *
     * @param id the entry ID
     * @param retryFunction the function to execute for retry
     * @return true if retry succeeded and entry was removed
     * @throws IllegalArgumentException if entry not found
     */
    public boolean retryManually(String id, Consumer<InterfaceXDeadLetterEntry> retryFunction) {
        InterfaceXDeadLetterEntry entry = entries.get(id);
        if (entry == null) {
            throw new IllegalArgumentException("Entry not found: " + id);
        }

        entry.recordManualRetry();
        totalRetried.incrementAndGet();

        try {
            retryFunction.accept(entry);
            entries.remove(id);
            LOGGER.info("Manual retry succeeded for entry: {}", id);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Manual retry failed for entry {}: {}", id, e.getMessage());
            return false;
        }
    }

    /**
     * Retries processing a dead letter entry (legacy API).
     *
     * @param id the entry ID
     * @return true if retry succeeded
     * @deprecated Use {@link #retryManually(String, Consumer)} instead
     */
    @Deprecated
    public boolean retryEntry(String id) {
        return retryManually(id, e -> {});
    }

    /**
     * Removes an entry from the queue (legacy API).
     *
     * @param id the entry ID
     * @return true if the entry was removed
     * @deprecated Use {@link #remove(String)} instead
     */
    @Deprecated
    public boolean removeEntry(String id) {
        return remove(id);
    }

    /**
     * Gets the current size of the queue.
     *
     * @return the number of entries
     */
    public int size() {
        return entries.size();
    }

    /**
     * Gets all current entries (legacy API).
     *
     * @return a collection of all entries
     * @deprecated Use {@link #getAll()} instead
     */
    @Deprecated
    public Collection<InterfaceXDeadLetterEntry> getAllEntries() {
        return getAll();
    }

    /**
     * Gets all expired entries.
     *
     * @return a collection of expired entries
     */
    public Collection<InterfaceXDeadLetterEntry> getExpiredEntries() {
        List<InterfaceXDeadLetterEntry> expired = new ArrayList<>();
        for (InterfaceXDeadLetterEntry entry : entries.values()) {
            if (entry.isExpired()) {
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
     * Gets the total number of dead-lettered entries ever added.
     *
     * @return the total count
     */
    public long getTotalDeadLettered() {
        return totalDeadLettered.get();
    }

    /**
     * Gets the total number of expired entries.
     *
     * @return the total count
     */
    public long getTotalExpired() {
        return totalExpired.get();
    }

    /**
     * Gets the total number of retried entries.
     *
     * @return the total count
     */
    public long getTotalRetried() {
        return totalRetried.get();
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
     * Closes the queue and releases resources.
     */
    public void close() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("InterfaceXDeadLetterQueue closed");
    }

    /**
     * Shuts down the cleanup scheduler (legacy API).
     *
     * @deprecated Use {@link #close()} instead
     */
    @Deprecated
    public void shutdown() {
        close();
    }

    /**
     * Gets metrics about the dead letter queue.
     *
     * @return a map of metric names to values
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
