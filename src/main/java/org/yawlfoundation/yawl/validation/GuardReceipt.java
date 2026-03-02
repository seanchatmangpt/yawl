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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Receipt summarizing validation results from all guard checkers.
 *
 * Provides a comprehensive view of all violations found during validation.
 */
public class GuardReceipt {

    private String phase;                  // Phase name (e.g., "guards", "shacl")
    private Instant timestamp;            // When validation was performed
    private int filesScanned;              // Number of files processed
    private List<GuardViolation> violations; // All violations found
    private String status;                 // "GREEN" or "RED"
    private String errorMessage;           // Error message if RED
    private GuardSummary summary;         // Summary statistics

    public GuardReceipt() {
        this.violations = new ArrayList<>();
        this.timestamp = Instant.now();
        this.summary = new GuardSummary();
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getFilesScanned() {
        return filesScanned;
    }

    public void setFilesScanned(int filesScanned) {
        this.filesScanned = filesScanned;
        summary.setTotalFiles(filesScanned);
    }

    public List<GuardViolation> getViolations() {
        return violations;
    }

    public void setViolations(List<GuardViolation> violations) {
        this.violations = violations;
        summary.updateFromViolations(violations);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public GuardSummary getSummary() {
        return summary;
    }

    /**
     * Adds a single violation to the receipt.
     */
    public void addViolation(GuardViolation violation) {
        this.violations.add(violation);
        summary.updateFromViolations(violations);
    }

    /**
     * Creates a summary string for display.
     */
    public String getSummaryText() {
        if (status.equals("GREEN")) {
            return "✓ GREEN: No violations found in " + filesScanned + " files";
        } else {
            return String.format("✗ RED: %d violations found in %d files",
                violations.size(), filesScanned);
        }
    }
}