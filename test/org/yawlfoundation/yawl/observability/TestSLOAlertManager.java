/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.observability;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test implementation of SLOAlertManager for Chicago TDD testing.
 * Captures alert interactions for verification without mocking.
 */
public class TestSLOAlertManager {

    private final List<String> alertCalls = new CopyOnWriteArrayList<>();
    private final AtomicLong alertCount = new AtomicLong(0);

    /**
     * Test method to simulate alert quota threshold notification.
     * This replaces the non-existent alertQuotaThreshold method.
     */
    public void alertQuotaThreshold(String tenantId, Long currentCount, Long hardLimit, Long softLimit) {
        String call = String.format("alertQuotaThreshold(tenant=%s, current=%d, hard=%d, soft=%d)",
            tenantId, currentCount, hardLimit, softLimit);
        alertCalls.add(call);
        alertCount.incrementAndGet();
    }

    /**
     * Get the number of alert calls made.
     */
    public long getAlertCallCount() {
        return alertCount.get();
    }

    /**
     * Get all alert calls made.
     */
    public List<String> getAlertCalls() {
        return new CopyOnWriteArrayList<>(alertCalls);
    }

    /**
     * Check if an alert was made for the specified tenant.
     */
    public boolean hasAlertForTenant(String tenantId) {
        return alertCalls.stream()
            .anyMatch(call -> call.contains("tenant=" + tenantId));
    }

    /**
     * Get the count of alerts for a specific tenant.
     */
    public long getAlertCountForTenant(String tenantId) {
        return alertCalls.stream()
            .filter(call -> call.contains("tenant=" + tenantId))
            .count();
    }

    /**
     * Clear all tracked alerts (for test cleanup).
     */
    public void clear() {
        alertCalls.clear();
        alertCount.set(0);
    }

    /**
     * Get the most recent alert call.
     */
    public String getMostRecentAlert() {
        return alertCalls.isEmpty() ? null : alertCalls.get(alertCalls.size() - 1);
    }
}