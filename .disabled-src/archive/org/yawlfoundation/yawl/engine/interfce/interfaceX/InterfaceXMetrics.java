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

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Metrics collection for Interface X operations using simple atomic counters.
 *
 * <p>Provides counters for tracking Interface X notification delivery,
 * retries, failures, and dead letter queue operations.</p>
 *
 * <p>Metric names (for external monitoring systems):
 * <ul>
 *   <li>{@code yawl_interface_x_notifications_total} - Total notifications attempted</li>
 *   <li>{@code yawl_interface_x_notifications_success} - Successful notifications</li>
 *   <li>{@code yawl_interface_x_retries_total} - Total retry attempts</li>
 *   <li>{@code yawl_interface_x_failures_total} - Final failures (exhausted retries)</li>
 *   <li>{@code yawl_interface_x_dead_letters_total} - Dead lettered notifications</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class InterfaceXMetrics {

    private static final Logger LOGGER = LogManager.getLogger(InterfaceXMetrics.class);

    private static volatile InterfaceXMetrics instance;
    // ReentrantLock for double-checked locking: consistent with virtual-thread
    // safety policy (synchronized on Object can pin carriers).
    private static final ReentrantLock INSTANCE_LOCK = new ReentrantLock();

    // Atomic counters for metrics
    private final AtomicLong notificationsTotal = new AtomicLong(0);
    private final AtomicLong notificationsSuccess = new AtomicLong(0);
    private final AtomicLong retriesTotal = new AtomicLong(0);
    private final AtomicLong failuresTotal = new AtomicLong(0);
    private final AtomicLong deadLettersTotal = new AtomicLong(0);
    private final AtomicLong totalDurationNanos = new AtomicLong(0);

    private InterfaceXMetrics() {
        LOGGER.info("InterfaceXMetrics initialized");
    }

    /**
     * Initializes the singleton metrics instance.
     *
     * @param meterRegistry ignored (for API compatibility)
     * @return the initialized instance
     */
    public static InterfaceXMetrics initialize(Object meterRegistry) {
        if (instance == null) {
            INSTANCE_LOCK.lock();
            try {
                if (instance == null) {
                    instance = new InterfaceXMetrics();
                }
            } finally {
                INSTANCE_LOCK.unlock();
            }
        }
        return instance;
    }

    /**
     * Gets the singleton metrics instance.
     *
     * @return the metrics instance
     * @throws IllegalStateException if not initialized
     */
    public static InterfaceXMetrics getInstance() {
        if (instance == null) {
            INSTANCE_LOCK.lock();
            try {
                if (instance == null) {
                    throw new IllegalStateException("InterfaceXMetrics not initialized. Call initialize() first.");
                }
            } finally {
                INSTANCE_LOCK.unlock();
            }
        }
        return instance;
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
     * Resets the singleton instance (for testing only).
     * This method is intentionally package-private for test access.
     */
    static void resetSingleton() {
        INSTANCE_LOCK.lock();
        try {
            instance = null;
        } finally {
            INSTANCE_LOCK.unlock();
        }
    }

    /**
     * Records a notification attempt.
     *
     * @param commandName the command name for tagging
     */
    public void recordNotificationAttempt(String commandName) {
        notificationsTotal.incrementAndGet();
        LOGGER.debug("Notification attempt recorded for command: {}", commandName);
    }

    /**
     * Records a successful notification delivery.
     *
     * @param commandName the command name for tagging
     */
    public void recordSuccess(String commandName) {
        notificationsSuccess.incrementAndGet();
        LOGGER.debug("Notification success recorded for command: {}", commandName);
    }

    /**
     * Records a retry attempt.
     *
     * @param commandName the command name for tagging
     * @param attemptNumber the current attempt number
     */
    public void recordRetry(String commandName, int attemptNumber) {
        retriesTotal.incrementAndGet();
        LOGGER.debug("Retry attempt {} recorded for command: {}", attemptNumber, commandName);
    }

    /**
     * Records a final failure (all retries exhausted).
     *
     * @param commandName the command name for tagging
     */
    public void recordFailure(String commandName) {
        failuresTotal.incrementAndGet();
        LOGGER.warn("Final failure recorded for command: {}", commandName);
    }

    /**
     * Records a dead-lettered notification.
     *
     * @param commandName the command name for tagging
     */
    public void recordDeadLetter(String commandName) {
        deadLettersTotal.incrementAndGet();
        LOGGER.warn("Dead letter recorded for command: {}", commandName);
    }

    /**
     * Records a notification duration.
     *
     * @param durationMs the duration in milliseconds
     */
    public void recordDuration(long durationMs) {
        totalDurationNanos.addAndGet(durationMs * 1_000_000);
    }

    /**
     * Gets the total number of notification attempts.
     *
     * @return the total count
     */
    public long getNotificationsTotalCount() {
        return notificationsTotal.get();
    }

    /**
     * Gets the total number of successful notifications.
     *
     * @return the success count
     */
    public long getNotificationsSuccessCount() {
        return notificationsSuccess.get();
    }

    /**
     * Gets the total number of retry attempts.
     *
     * @return the retry count
     */
    public long getRetriesTotalCount() {
        return retriesTotal.get();
    }

    /**
     * Gets the total number of final failures.
     *
     * @return the failure count
     */
    public long getFailuresTotalCount() {
        return failuresTotal.get();
    }

    /**
     * Gets the total number of dead letters.
     *
     * @return the dead letter count
     */
    public long getDeadLettersTotalCount() {
        return deadLettersTotal.get();
    }

    /**
     * Gets the total duration in milliseconds.
     *
     * @return the total duration
     */
    public long getTotalDurationMs() {
        return totalDurationNanos.get() / 1_000_000;
    }

    /**
     * Resets all counters (primarily for testing).
     */
    public void reset() {
        notificationsTotal.set(0);
        notificationsSuccess.set(0);
        retriesTotal.set(0);
        failuresTotal.set(0);
        deadLettersTotal.set(0);
        totalDurationNanos.set(0);
    }

    /**
     * Starts a timer for measuring operation duration.
     * Returns null since we use simple atomic counters instead of Micrometer.
     *
     * @return null (timer tracking is done via recordDuration)
     */
    public Object startTimer() {
        return null;
    }

    /**
     * Stops a timer and records the duration.
     * Since startTimer returns null, this method accepts null input.
     *
     * @param timer the timer object (ignored, can be null)
     */
    public void stopTimer(Object timer) {
        // Duration is tracked via recordDuration method
    }

    /**
     * Gets the meter registry.
     * Returns null since we use simple atomic counters instead of Micrometer.
     *
     * @return null (no Micrometer meter registry)
     */
    public Object getMeterRegistry() {
        return null;
    }

    /**
     * Gets a summary of all metrics.
     *
     * @return a string representation of metrics
     */
    @Override
    public String toString() {
        return String.format(
                "InterfaceXMetrics{notifications=%d, success=%d, retries=%d, failures=%d, deadLetters=%d}",
                notificationsTotal.get(), notificationsSuccess.get(),
                retriesTotal.get(), failuresTotal.get(), deadLettersTotal.get());
    }
}
