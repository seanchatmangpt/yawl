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

package org.yawlfoundation.yawl.engine.interfce.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Health indicator for Interface X (Exception Handling Interface).
 *
 * <h2>Overview</h2>
 * <p>Monitors the health of Interface X, which handles exception event notifications
 * from the YAWL engine to external exception services.</p>
 *
 * <h2>Health Criteria</h2>
 * <table border="1">
 *   <tr><th>Status</th><th>Criteria</th></tr>
 *   <tr><td>UP</td><td>Circuit breaker CLOSED, retry rate &lt; 10%, no dead letter queue buildup</td></tr>
 *   <tr><td>DEGRADED</td><td>Circuit breaker HALF_OPEN, retry rate 10-25%, or moderate DLQ size</td></tr>
 *   <tr><td>DOWN</td><td>Circuit breaker OPEN, retry rate &gt; 25%, or large DLQ buildup</td></tr>
 * </table>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
@Component
public class InterfaceXHealthIndicator implements HealthIndicator {

    private static final Logger _logger = LogManager.getLogger(InterfaceXHealthIndicator.class);

    public static final String CIRCUIT_BREAKER_NAME = "interfaceX-notifications";
    private static final double RETRY_RATE_WARNING_THRESHOLD = 10.0;
    private static final double RETRY_RATE_CRITICAL_THRESHOLD = 25.0;
    private static final int DLQ_WARNING_THRESHOLD = 100;
    private static final int DLQ_CRITICAL_THRESHOLD = 500;
    private static final double FAILURE_RATE_WARNING_THRESHOLD = 5.0;
    private static final double FAILURE_RATE_CRITICAL_THRESHOLD = 15.0;

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final InterfaceMetrics interfaceMetrics;
    private final ConcurrentLinkedQueue<DeadLetterEntry> deadLetterQueue = new ConcurrentLinkedQueue<>();
    private final AtomicLong totalNotifications = new AtomicLong(0);
    private final AtomicLong successfulNotifications = new AtomicLong(0);
    private final AtomicLong retriedNotifications = new AtomicLong(0);
    private final AtomicLong failedNotifications = new AtomicLong(0);

    public InterfaceXHealthIndicator(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.interfaceMetrics = InterfaceMetrics.getInstance();
        _logger.info("InterfaceXHealthIndicator initialized");
    }

    @Override
    public Health health() {
        try {
            HealthStatusDetails details = collectHealthDetails();
            return buildHealthResponse(details);
        } catch (Exception e) {
            _logger.error("Error checking Interface X health", e);
            return Health.down()
                    .withDetail("error", e.getClass().getName())
                    .withDetail("message", e.getMessage())
                    .build();
        }
    }

    private HealthStatusDetails collectHealthDetails() {
        HealthStatusDetails details = new HealthStatusDetails();
        collectCircuitBreakerStatus(details);
        collectNotificationMetrics(details);
        collectDeadLetterQueueStatus(details);
        determineOverallStatus(details);
        return details;
    }

    private void collectCircuitBreakerStatus(HealthStatusDetails details) {
        details.circuitBreakerAvailable = circuitBreakerRegistry.getAllCircuitBreakers()
                .stream()
                .anyMatch(cb -> cb.getName().contains("interfaceX") || cb.getName().contains("InterfaceX"));

        CircuitBreaker circuitBreaker = getCircuitBreaker();
        if (circuitBreaker != null) {
            details.circuitBreakerState = circuitBreaker.getState().toString();
            CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
            details.circuitBreakerFailureRate = metrics.getFailureRate();
            details.circuitBreakerSlowCallRate = metrics.getSlowCallRate();
            details.circuitBreakerBufferedCalls = metrics.getNumberOfBufferedCalls();
            details.circuitBreakerFailedCalls = metrics.getNumberOfFailedCalls();
            details.circuitBreakerSuccessfulCalls = metrics.getNumberOfSuccessfulCalls();
        } else {
            details.circuitBreakerState = "NOT_CONFIGURED";
            details.circuitBreakerFailureRate = 0.0f;
        }
    }

    private void collectNotificationMetrics(HealthStatusDetails details) {
        details.totalNotifications = interfaceMetrics.getInterfaceXTotalNotifications();
        details.totalRetries = interfaceMetrics.getInterfaceXTotalRetries();
        details.totalFailures = interfaceMetrics.getInterfaceXTotalFailures();
        details.retryRate = interfaceMetrics.getInterfaceXRetryRate();
        details.failureRate = interfaceMetrics.getInterfaceXFailureRate();
    }

    private void collectDeadLetterQueueStatus(HealthStatusDetails details) {
        details.deadLetterQueueSize = deadLetterQueue.size();
    }

    private void determineOverallStatus(HealthStatusDetails details) {
        boolean isDown = false;
        boolean isDegraded = false;
        StringBuilder reason = new StringBuilder();

        if ("OPEN".equals(details.circuitBreakerState) || "FORCED_OPEN".equals(details.circuitBreakerState)) {
            isDown = true;
            reason.append("Circuit breaker OPEN; ");
        } else if ("HALF_OPEN".equals(details.circuitBreakerState)) {
            isDegraded = true;
            reason.append("Circuit breaker HALF_OPEN; ");
        }

        if (details.retryRate >= RETRY_RATE_CRITICAL_THRESHOLD) {
            isDown = true;
            reason.append(String.format("Critical retry rate: %.2f%%; ", details.retryRate));
        } else if (details.retryRate >= RETRY_RATE_WARNING_THRESHOLD) {
            isDegraded = true;
            reason.append(String.format("High retry rate: %.2f%%; ", details.retryRate));
        }

        if (details.failureRate >= FAILURE_RATE_CRITICAL_THRESHOLD) {
            isDown = true;
            reason.append(String.format("Critical failure rate: %.2f%%; ", details.failureRate));
        } else if (details.failureRate >= FAILURE_RATE_WARNING_THRESHOLD) {
            isDegraded = true;
            reason.append(String.format("High failure rate: %.2f%%; ", details.failureRate));
        }

        if (details.deadLetterQueueSize >= DLQ_CRITICAL_THRESHOLD) {
            isDown = true;
            reason.append(String.format("Critical DLQ size: %d; ", details.deadLetterQueueSize));
        } else if (details.deadLetterQueueSize >= DLQ_WARNING_THRESHOLD) {
            isDegraded = true;
            reason.append(String.format("Large DLQ size: %d; ", details.deadLetterQueueSize));
        }

        if (isDown) {
            details.status = "DOWN";
        } else if (isDegraded) {
            details.status = "DEGRADED";
        } else {
            details.status = "UP";
        }
        details.reason = reason.toString().trim();
    }

    private Health buildHealthResponse(HealthStatusDetails details) {
        Health.Builder builder;
        if ("DOWN".equals(details.status)) {
            builder = Health.down();
        } else if ("DEGRADED".equals(details.status)) {
            builder = Health.status("DEGRADED");
        } else {
            builder = Health.up();
        }

        return builder
                .withDetail("status", details.status)
                .withDetail("reason", details.reason)
                .withDetail("circuitBreaker.state", details.circuitBreakerState)
                .withDetail("circuitBreaker.failureRate", details.circuitBreakerFailureRate)
                .withDetail("circuitBreaker.slowCallRate", details.circuitBreakerSlowCallRate)
                .withDetail("circuitBreaker.bufferedCalls", details.circuitBreakerBufferedCalls)
                .withDetail("circuitBreaker.failedCalls", details.circuitBreakerFailedCalls)
                .withDetail("circuitBreaker.successfulCalls", details.circuitBreakerSuccessfulCalls)
                .withDetail("notifications.total", details.totalNotifications)
                .withDetail("notifications.retries", details.totalRetries)
                .withDetail("notifications.failures", details.totalFailures)
                .withDetail("notifications.retryRate", String.format("%.2f%%", details.retryRate))
                .withDetail("notifications.failureRate", String.format("%.2f%%", details.failureRate))
                .withDetail("deadLetterQueue.size", details.deadLetterQueueSize)
                .build();
    }

    private CircuitBreaker getCircuitBreaker() {
        for (CircuitBreaker cb : circuitBreakerRegistry.getAllCircuitBreakers()) {
            if (cb.getName().contains("interfaceX") || cb.getName().contains("InterfaceX")) {
                return cb;
            }
        }
        return circuitBreakerRegistry.find(CIRCUIT_BREAKER_NAME).orElse(null);
    }

    public void addToDeadLetterQueue(String eventType, String payload, String errorMessage) {
        DeadLetterEntry entry = new DeadLetterEntry();
        entry.eventType = eventType;
        entry.payload = payload;
        entry.errorMessage = errorMessage;
        entry.timestamp = System.currentTimeMillis();
        entry.retryCount = 0;
        deadLetterQueue.add(entry);
        _logger.warn("Added to dead letter queue: eventType={} error={}", eventType, errorMessage);
    }

    public int getDeadLetterQueueSize() {
        return deadLetterQueue.size();
    }

    public Map<String, DeadLetterEntry> getDeadLetterEntriesForRetry(int maxEntries) {
        Map<String, DeadLetterEntry> entries = new HashMap<>();
        int count = 0;
        for (DeadLetterEntry entry : deadLetterQueue) {
            if (count >= maxEntries) {
                break;
            }
            String id = entry.eventType + "-" + entry.timestamp;
            entries.put(id, entry);
            count++;
        }
        return entries;
    }

    public void removeFromDeadLetterQueue(String eventType, long timestamp) {
        deadLetterQueue.removeIf(entry ->
                entry.eventType.equals(eventType) && entry.timestamp == timestamp);
    }

    public void clearDeadLetterQueue() {
        int size = deadLetterQueue.size();
        deadLetterQueue.clear();
        _logger.info("Cleared dead letter queue: {} entries removed", size);
    }

    public void recordNotificationAttempt(boolean success) {
        totalNotifications.incrementAndGet();
        if (success) {
            successfulNotifications.incrementAndGet();
        }
    }

    public void recordNotificationRetry() {
        retriedNotifications.incrementAndGet();
    }

    public void recordNotificationFailure() {
        failedNotifications.incrementAndGet();
    }

    public long getTotalNotifications() {
        return totalNotifications.get();
    }

    public long getSuccessfulNotifications() {
        return successfulNotifications.get();
    }

    public long getRetriedNotifications() {
        return retriedNotifications.get();
    }

    public long getFailedNotifications() {
        return failedNotifications.get();
    }

    public void reset() {
        totalNotifications.set(0);
        successfulNotifications.set(0);
        retriedNotifications.set(0);
        failedNotifications.set(0);
        deadLetterQueue.clear();
    }

    private static class HealthStatusDetails {
        String status = "UP";
        String reason = "";
        boolean circuitBreakerAvailable = false;
        String circuitBreakerState = "UNKNOWN";
        float circuitBreakerFailureRate = 0.0f;
        float circuitBreakerSlowCallRate = 0.0f;
        int circuitBreakerBufferedCalls = 0;
        int circuitBreakerFailedCalls = 0;
        int circuitBreakerSuccessfulCalls = 0;
        long totalNotifications = 0;
        long totalRetries = 0;
        long totalFailures = 0;
        double retryRate = 0.0;
        double failureRate = 0.0;
        int deadLetterQueueSize = 0;
    }

    public static class DeadLetterEntry {
        private String eventType;
        private String payload;
        private String errorMessage;
        private long timestamp;
        private int retryCount;

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public void setRetryCount(int retryCount) {
            this.retryCount = retryCount;
        }

        public void incrementRetryCount() {
            this.retryCount++;
        }
    }
}
