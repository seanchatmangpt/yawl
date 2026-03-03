/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.validation;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Summary statistics for validation results.
 *
 * Provides detailed counts of violations by pattern type.
 */
public class GuardSummary {

    private int totalFiles;         // Total files scanned
    private int totalViolations;    // Total violations
    private int criticalViolations; // FAIL severity violations
    private int warningViolations;  // WARN severity violations

    // Count by pattern type
    private Map<String, Integer> violationCounts = new HashMap<>();

    public int getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }

    public int getTotalViolations() {
        return totalViolations;
    }

    public int getCriticalViolations() {
        return criticalViolations;
    }

    public int getWarningViolations() {
        return warningViolations;
    }

    public Map<String, Integer> getViolationCounts() {
        return violationCounts;
    }

    /**
     * Updates summary statistics based on violations list.
     */
    public void updateFromViolations(List<GuardViolation> violations) {
        this.totalViolations = violations.size();
        this.criticalViolations = 0;
        this.warningViolations = 0;
        this.violationCounts.clear();

        for (GuardViolation violation : violations) {
            // Count by severity
            if ("FAIL".equals(violation.getSeverity())) {
                criticalViolations++;
            } else if ("WARN".equals(violation.getSeverity())) {
                warningViolations++;
            }

            // Count by pattern
            violationCounts.merge(violation.getPattern(), 1, Integer::sum);
        }
    }

    /**
     * Creates a formatted summary report.
     */
    public String formatSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Validation Summary:\n");
        sb.append(String.format("  Files Scanned: %d\n", totalFiles));
        sb.append(String.format("  Total Violations: %d\n", totalViolations));
        sb.append(String.format("  Critical (FAIL): %d\n", criticalViolations));
        sb.append(String.format("  Warnings (WARN): %d\n", warningViolations));

        if (!violationCounts.isEmpty()) {
            sb.append("  Violations by Pattern:\n");
            violationCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    sb.append(String.format("    %s: %d\n", entry.getKey(), entry.getValue()));
                });
        }

        return sb.toString();
    }
}