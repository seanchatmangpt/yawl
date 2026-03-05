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

package org.yawlfoundation.yawl.integration.verification;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Immutable record representing the complete result of a workflow
 * soundness verification run.
 *
 * <p>A verification report includes:
 * <ul>
 *   <li>All detected findings (deadlocks, unsoundness issues)</li>
 *   <li>Overall soundness status (true = no errors)</li>
 *   <li>Counts of errors, warnings, and findings</li>
 *   <li>Verification execution time</li>
 *   <li>A human-readable summary</li>
 * </ul>
 *
 * @param findings list of all detected issues (may be empty)
 * @param isSound true iff no ERROR-level findings exist
 * @param deadlockCount number of ERROR-level findings
 * @param warningCount number of WARNING-level findings
 * @param infoCount number of INFO-level findings
 * @param summary human-readable summary of results
 * @param verificationTime how long the verification took
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record VerificationReport(
    List<VerificationFinding> findings,
    boolean isSound,
    int deadlockCount,
    int warningCount,
    int infoCount,
    String summary,
    Duration verificationTime
) {
    /**
     * Constructs a VerificationReport with the given parameters.
     *
     * @param findings list of findings (non-null; may be empty)
     * @param isSound soundness status
     * @param deadlockCount count of ERROR findings (non-negative)
     * @param warningCount count of WARNING findings (non-negative)
     * @param infoCount count of INFO findings (non-negative)
     * @param summary human-readable summary (non-null, non-empty)
     * @param verificationTime elapsed time (non-null, non-negative)
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if summary is empty, counts are negative,
     *                                  or isSound is inconsistent with findings
     */
    public VerificationReport {
        Objects.requireNonNull(findings, "findings must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        Objects.requireNonNull(verificationTime, "verificationTime must not be null");

        if (summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be empty");
        }
        if (deadlockCount < 0 || warningCount < 0 || infoCount < 0) {
            throw new IllegalArgumentException("counts must be non-negative");
        }
        if (verificationTime.isNegative()) {
            throw new IllegalArgumentException("verificationTime must not be negative");
        }

        // Verify consistency: isSound should be true iff deadlockCount == 0
        boolean hasFatalErrors = !findings.isEmpty() && findings.stream()
            .anyMatch(f -> f.severity() == VerificationFinding.Severity.ERROR);
        if (isSound == hasFatalErrors) {
            throw new IllegalArgumentException(
                "isSound must be false if ERROR-level findings exist, true otherwise"
            );
        }
    }

    /**
     * Convenience method: returns total count of all findings.
     *
     * @return deadlockCount + warningCount + infoCount
     */
    public int totalFindingCount() {
        return deadlockCount + warningCount + infoCount;
    }

    /**
     * Returns a formatted string representation of this report.
     *
     * @return multi-line string showing soundness status, counts, and summary
     */
    @Override
    public String toString() {
        return """
            Verification Report
            ===================
            Status: %s
            Deadlocks (Errors): %d
            Warnings: %d
            Info: %d
            Total Findings: %d
            Verification Time: %s
            Summary: %s
            """
            .formatted(
                isSound ? "SOUND" : "UNSOUND",
                deadlockCount,
                warningCount,
                infoCount,
                totalFindingCount(),
                verificationTime,
                summary
            );
    }
}
