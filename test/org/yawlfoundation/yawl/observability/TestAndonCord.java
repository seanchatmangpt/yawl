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

package org.yawlfoundation.yawl.observability;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Test implementation of AndonCord for Chicago TDD testing.
 * Captures all alert interactions for verification without mocking.
 */
public class TestAndonCord {

    private final List<AndonCord.Alert> alertHistory = new CopyOnWriteArrayList<>();
    private final Map<AndonCord.Severity, AtomicLong> alertCounts = new ConcurrentHashMap<>();
    private final Map<String, AndonCord.Alert> activeAlerts = new ConcurrentHashMap<>();

    public TestAndonCord() {
        // Initialize counters for all severities
        for (AndonCord.Severity severity : AndonCord.Severity.values()) {
            alertCounts.put(severity, new AtomicLong(0));
        }
    }

    /**
     * Override pull to capture alerts for testing.
     */
    public AndonCord.Alert pull(AndonCord.Severity severity, String alertName, Map<String, Object> context) {
        AndonCord.Alert alert = AndonCord.getInstance().pull(severity, alertName, context);
        if (alert != null) {
            alertHistory.add(alert);
            activeAlerts.put(alert.getId(), alert);
            alertCounts.get(severity).incrementAndGet();
        }
        return alert;
    }

    /**
     * Override acknowledge to track acknowledgments.
     */
    public AndonCord.Alert acknowledge(String alertId) {
        AndonCord.Alert alert = activeAlerts.get(alertId);
        if (alert != null) {
            AndonCord acknowledged = AndonCord.getInstance().acknowledge(alertId);
            if (acknowledged != null) {
                activeAlerts.remove(alertId);
                return acknowledged;
            }
        }
        return null;
    }

    /**
     * Override resolve to track resolutions.
     */
    public AndonCord.Alert resolve(String alertId) {
        AndonCord.Alert alert = activeAlerts.get(alertId);
        if (alert != null) {
            AndonCord resolved = AndonCord.getInstance().resolve(alertId);
            if (resolved != null) {
                activeAlerts.remove(alertId);
                return resolved;
            }
        }
        return null;
    }

    /**
     * Get all active alerts for verification.
     */
    public List<AndonCord.Alert> getActiveAlerts() {
        return List.copyOf(activeAlerts.values());
    }

    /**
     * Get alert history for verification.
     */
    public List<AndonCord.Alert> getAlertHistory() {
        return new CopyOnWriteArrayList<>(alertHistory);
    }

    /**
     * Get alert count by severity.
     */
    public long getAlertCount(AndonCord.Severity severity) {
        return alertCounts.getOrDefault(severity, new AtomicLong(0)).get();
    }

    /**
     * Get total alert count.
     */
    public long getTotalAlerts() {
        return alertHistory.size();
    }

    /**
     * Get alerts filtered by severity.
     */
    public List<AndonCord.Alert> getAlertsBySeverity(AndonCord.Severity severity) {
        return alertHistory.stream()
            .filter(alert -> alert.getSeverity() == severity)
            .collect(Collectors.toList());
    }

    /**
     * Get alerts for a specific case ID.
     */
    public List<AndonCord.Alert> getAlertsByCaseId(String caseId) {
        return alertHistory.stream()
            .filter(alert -> alert.getCaseId() != null && alert.getCaseId().equals(caseId))
            .collect(Collectors.toList());
    }

    /**
     * Check if an alert exists with specific criteria.
     */
    public boolean hasAlert(AndonCord.Severity severity, String alertName) {
        return alertHistory.stream()
            .anyMatch(alert -> alert.getSeverity() == severity &&
                             alert.getName().equals(alertName));
    }

    /**
     * Get the most recent alert.
     */
    public AndonCord.Alert getMostRecentAlert() {
        return alertHistory.isEmpty() ? null : alertHistory.get(alertHistory.size() - 1);
    }

    /**
     * Clear all tracked alerts (for test cleanup).
     */
    public void clear() {
        alertHistory.clear();
        activeAlerts.clear();
        alertCounts.forEach((severity, counter) -> counter.set(0));
    }

    /**
     * Get alert statistics for reporting.
     */
    public AlertStatistics getStatistics() {
        AlertStatistics stats = new AlertStatistics();
        alertHistory.forEach(alert -> {
            switch (alert.getState()) {
                case FIRING -> stats.active++;
                case ACKNOWLEDGED -> stats.acknowledged++;
                case RESOLVED -> stats.resolved++;
            }
        });
        stats.total = alertHistory.size();
        stats.bySeverity = new ConcurrentHashMap<>();
        alertCounts.forEach((severity, count) ->
            stats.bySeverity.put(severity.getLabel(), count.get()));
        return stats;
    }

    /**
     * Helper class for alert statistics.
     */
    public static class AlertStatistics {
        public int active;
        public int acknowledged;
        public int resolved;
        public int total;
        public Map<String, Long> bySeverity = new ConcurrentHashMap<>();

        @Override
        public String toString() {
            return String.format("AlertStatistics{total=%d, active=%d, acknowledged=%d, resolved=%d, bySeverity=%s}",
                total, active, acknowledged, resolved, bySeverity);
        }
    }
}