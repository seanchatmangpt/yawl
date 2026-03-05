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

package org.yawlfoundation.yawl.integration.pool;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Metrics collector for YAWL connection pool.
 *
 * <p>Tracks comprehensive statistics about pool usage including:</p>
 * <ul>
 *   <li>Active, idle, and total connections</li>
 *   <li>Borrow and return counts</li>
 *   <li>Creation and destruction counts</li>
 *   <li>Validation success/failure rates</li>
 *   <li>Wait times and timeout counts</li>
 *   <li>Error counts by type</li>
 * </ul>
 *
 * <p>Thread-safe for concurrent access. All counters use atomic operations.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public class YawlConnectionPoolMetrics {

    private final Instant createdAt;

    // Connection counts
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong idleConnections = new AtomicLong(0);
    private final LongAdder totalCreated = new LongAdder();
    private final LongAdder totalDestroyed = new LongAdder();
    private final LongAdder totalEvicted = new LongAdder();

    // Borrow/Return counts
    private final LongAdder totalBorrowed = new LongAdder();
    private final LongAdder totalReturned = new LongAdder();
    private final LongAdder borrowTimeouts = new LongAdder();

    // Validation counts
    private final LongAdder validationsPassed = new LongAdder();
    private final LongAdder validationsFailed = new LongAdder();

    // Timing
    private final LongAdder totalBorrowWaitTimeMs = new LongAdder();
    private final LongAdder totalConnectionCreateTimeMs = new LongAdder();
    private final AtomicLong maxBorrowWaitTimeMs = new AtomicLong(0);
    private final AtomicLong maxConnectionCreateTimeMs = new AtomicLong(0);

    // Errors
    private final LongAdder connectionErrors = new LongAdder();
    private final LongAdder validationErrors = new LongAdder();
    private final Map<String, LongAdder> errorsByType = new ConcurrentHashMap<>();

    // Health check
    private final LongAdder healthChecksPassed = new LongAdder();
    private final LongAdder healthChecksFailed = new LongAdder();
    private final AtomicLong lastHealthCheckTime = new AtomicLong(0);
    private final AtomicLong lastSuccessfulHealthCheck = new AtomicLong(0);

    /**
     * Create a new metrics collector.
     */
    public YawlConnectionPoolMetrics() {
        this.createdAt = Instant.now();
    }

    // ========== Connection Lifecycle ==========

    /**
     * Record a connection being created.
     *
     * @param createTimeMs time taken to create the connection
     */
    public void recordConnectionCreated(long createTimeMs) {
        totalCreated.increment();
        totalConnectionCreateTimeMs.add(createTimeMs);
        updateMax(maxConnectionCreateTimeMs, createTimeMs);
    }

    /**
     * Record a connection being destroyed.
     */
    public void recordConnectionDestroyed() {
        totalDestroyed.increment();
    }

    /**
     * Record a connection being evicted.
     */
    public void recordConnectionEvicted() {
        totalEvicted.increment();
    }

    /**
     * Update active connection count.
     *
     * @param count current active count
     */
    public void setActiveConnections(long count) {
        activeConnections.set(count);
    }

    /**
     * Update idle connection count.
     *
     * @param count current idle count
     */
    public void setIdleConnections(long count) {
        idleConnections.set(count);
    }

    // ========== Borrow/Return ==========

    /**
     * Record a connection being borrowed.
     *
     * @param waitTimeMs time waited for the connection
     */
    public void recordBorrowed(long waitTimeMs) {
        totalBorrowed.increment();
        totalBorrowWaitTimeMs.add(waitTimeMs);
        updateMax(maxBorrowWaitTimeMs, waitTimeMs);
    }

    /**
     * Record a connection being returned.
     */
    public void recordReturned() {
        totalReturned.increment();
    }

    /**
     * Record a borrow timeout.
     */
    public void recordBorrowTimeout() {
        borrowTimeouts.increment();
    }

    // ========== Validation ==========

    /**
     * Record a successful validation.
     */
    public void recordValidationPassed() {
        validationsPassed.increment();
    }

    /**
     * Record a failed validation.
     */
    public void recordValidationFailed() {
        validationsFailed.increment();
    }

    /**
     * Record a validation error.
     *
     * @param errorType type of error
     */
    public void recordValidationError(String errorType) {
        validationErrors.increment();
        errorsByType.computeIfAbsent(errorType, k -> new LongAdder()).increment();
    }

    // ========== Errors ==========

    /**
     * Record a connection error.
     *
     * @param errorType type of error
     */
    public void recordConnectionError(String errorType) {
        connectionErrors.increment();
        errorsByType.computeIfAbsent(errorType, k -> new LongAdder()).increment();
    }

    // ========== Health Checks ==========

    /**
     * Record a health check result.
     *
     * @param passed true if health check passed
     */
    public void recordHealthCheck(boolean passed) {
        long now = System.currentTimeMillis();
        lastHealthCheckTime.set(now);
        if (passed) {
            healthChecksPassed.increment();
            lastSuccessfulHealthCheck.set(now);
        } else {
            healthChecksFailed.increment();
        }
    }

    // ========== Getters ==========

    /**
     * Get current active connection count.
     *
     * @return active count
     */
    public long getActiveConnections() {
        return activeConnections.get();
    }

    /**
     * Get current idle connection count.
     *
     * @return idle count
     */
    public long getIdleConnections() {
        return idleConnections.get();
    }

    /**
     * Get total connections (active + idle).
     *
     * @return total count
     */
    public long getTotalConnections() {
        return activeConnections.get() + idleConnections.get();
    }

    /**
     * Get total connections created since pool start.
     *
     * @return created count
     */
    public long getTotalCreated() {
        return totalCreated.sum();
    }

    /**
     * Get total connections destroyed since pool start.
     *
     * @return destroyed count
     */
    public long getTotalDestroyed() {
        return totalDestroyed.sum();
    }

    /**
     * Get total connections evicted since pool start.
     *
     * @return evicted count
     */
    public long getTotalEvicted() {
        return totalEvicted.sum();
    }

    /**
     * Get total connections borrowed since pool start.
     *
     * @return borrowed count
     */
    public long getTotalBorrowed() {
        return totalBorrowed.sum();
    }

    /**
     * Get total connections returned since pool start.
     *
     * @return returned count
     */
    public long getTotalReturned() {
        return totalReturned.sum();
    }

    /**
     * Get total borrow timeouts since pool start.
     *
     * @return timeout count
     */
    public long getBorrowTimeouts() {
        return borrowTimeouts.sum();
    }

    /**
     * Get total validations passed since pool start.
     *
     * @return passed count
     */
    public long getValidationsPassed() {
        return validationsPassed.sum();
    }

    /**
     * Get total validations failed since pool start.
     *
     * @return failed count
     */
    public long getValidationsFailed() {
        return validationsFailed.sum();
    }

    /**
     * Get validation success rate (0.0 to 1.0).
     *
     * @return success rate
     */
    public double getValidationSuccessRate() {
        long passed = validationsPassed.sum();
        long failed = validationsFailed.sum();
        long total = passed + failed;
        return total > 0 ? (double) passed / total : 1.0;
    }

    /**
     * Get average borrow wait time in milliseconds.
     *
     * @return average wait time
     */
    public double getAverageBorrowWaitTimeMs() {
        long borrows = totalBorrowed.sum();
        return borrows > 0 ? (double) totalBorrowWaitTimeMs.sum() / borrows : 0.0;
    }

    /**
     * Get maximum borrow wait time in milliseconds.
     *
     * @return max wait time
     */
    public long getMaxBorrowWaitTimeMs() {
        return maxBorrowWaitTimeMs.get();
    }

    /**
     * Get average connection creation time in milliseconds.
     *
     * @return average creation time
     */
    public double getAverageConnectionCreateTimeMs() {
        long created = totalCreated.sum();
        return created > 0 ? (double) totalConnectionCreateTimeMs.sum() / created : 0.0;
    }

    /**
     * Get maximum connection creation time in milliseconds.
     *
     * @return max creation time
     */
    public long getMaxConnectionCreateTimeMs() {
        return maxConnectionCreateTimeMs.get();
    }

    /**
     * Get total connection errors.
     *
     * @return error count
     */
    public long getConnectionErrors() {
        return connectionErrors.sum();
    }

    /**
     * Get total validation errors.
     *
     * @return error count
     */
    public long getValidationErrors() {
        return validationErrors.sum();
    }

    /**
     * Get error counts by type.
     *
     * @return map of error type to count
     */
    public Map<String, Long> getErrorsByType() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        errorsByType.forEach((k, v) -> result.put(k, v.sum()));
        return result;
    }

    /**
     * Get total health checks passed.
     *
     * @return passed count
     */
    public long getHealthChecksPassed() {
        return healthChecksPassed.sum();
    }

    /**
     * Get total health checks failed.
     *
     * @return failed count
     */
    public long getHealthChecksFailed() {
        return healthChecksFailed.sum();
    }

    /**
     * Get timestamp of last health check.
     *
     * @return epoch millis or 0 if never
     */
    public long getLastHealthCheckTime() {
        return lastHealthCheckTime.get();
    }

    /**
     * Get timestamp of last successful health check.
     *
     * @return epoch millis or 0 if never
     */
    public long getLastSuccessfulHealthCheck() {
        return lastSuccessfulHealthCheck.get();
    }

    /**
     * Get pool creation timestamp.
     *
     * @return creation instant
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Get pool uptime in milliseconds.
     *
     * @return uptime
     */
    public long getUptimeMs() {
        return System.currentTimeMillis() - createdAt.toEpochMilli();
    }

    // ========== Utility ==========

    private void updateMax(AtomicLong max, long newValue) {
        long current;
        do {
            current = max.get();
            if (newValue <= current) {
                return;
            }
        } while (!max.compareAndSet(current, newValue));
    }

    /**
     * Reset all metrics to initial values.
     */
    public void reset() {
        activeConnections.set(0);
        idleConnections.set(0);
        totalCreated.reset();
        totalDestroyed.reset();
        totalEvicted.reset();
        totalBorrowed.reset();
        totalReturned.reset();
        borrowTimeouts.reset();
        validationsPassed.reset();
        validationsFailed.reset();
        totalBorrowWaitTimeMs.reset();
        totalConnectionCreateTimeMs.reset();
        maxBorrowWaitTimeMs.set(0);
        maxConnectionCreateTimeMs.set(0);
        connectionErrors.reset();
        validationErrors.reset();
        healthChecksPassed.reset();
        healthChecksFailed.reset();
        lastHealthCheckTime.set(0);
        lastSuccessfulHealthCheck.set(0);
        errorsByType.clear();
    }

    /**
     * Get a snapshot of all metrics as a map.
     *
     * @return map of metric name to value
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new ConcurrentHashMap<>();
        map.put("activeConnections", getActiveConnections());
        map.put("idleConnections", getIdleConnections());
        map.put("totalConnections", getTotalConnections());
        map.put("totalCreated", getTotalCreated());
        map.put("totalDestroyed", getTotalDestroyed());
        map.put("totalEvicted", getTotalEvicted());
        map.put("totalBorrowed", getTotalBorrowed());
        map.put("totalReturned", getTotalReturned());
        map.put("borrowTimeouts", getBorrowTimeouts());
        map.put("validationsPassed", getValidationsPassed());
        map.put("validationsFailed", getValidationsFailed());
        map.put("validationSuccessRate", getValidationSuccessRate());
        map.put("averageBorrowWaitTimeMs", getAverageBorrowWaitTimeMs());
        map.put("maxBorrowWaitTimeMs", getMaxBorrowWaitTimeMs());
        map.put("averageConnectionCreateTimeMs", getAverageConnectionCreateTimeMs());
        map.put("maxConnectionCreateTimeMs", getMaxConnectionCreateTimeMs());
        map.put("connectionErrors", getConnectionErrors());
        map.put("validationErrors", getValidationErrors());
        map.put("healthChecksPassed", getHealthChecksPassed());
        map.put("healthChecksFailed", getHealthChecksFailed());
        map.put("uptimeMs", getUptimeMs());
        map.put("errorsByType", getErrorsByType());
        return map;
    }

    @Override
    public String toString() {
        return "YawlConnectionPoolMetrics{" +
                "active=" + getActiveConnections() +
                ", idle=" + getIdleConnections() +
                ", borrowed=" + getTotalBorrowed() +
                ", returned=" + getTotalReturned() +
                ", created=" + getTotalCreated() +
                ", destroyed=" + getTotalDestroyed() +
                ", timeouts=" + getBorrowTimeouts() +
                ", errors=" + getConnectionErrors() +
                '}';
    }
}
