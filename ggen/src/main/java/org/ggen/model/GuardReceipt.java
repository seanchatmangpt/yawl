/*
 * Copyright 2026 YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ggen.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Receipt generated after hyper-standards guard validation.
 *
 * <p>This class contains the complete results of guard validation including:
 * <ul>
 *   <li>Validation metadata (phase, timestamp, file count)</li>
 *   <li>List of all violations found</li>
 *   <li>Overall validation status (GREEN/RED)</li>
 *   <li>Error message for failed validation</li>
 *   <li>Summary statistics by violation pattern</li>
 * </ul>
 *
 * <p>The receipt serves as both a return value and a persistent record of
 * the validation results that can be written to JSON format.
 *
 * @since 1.0
 */
public final class GuardReceipt {

    /**
     * The validation phase (always "guards" for hyper-standards validation).
     */
    private final String phase;

    /**
     * Timestamp when validation was performed.
     */
    private final Instant timestamp;

    /**
     * Number of files that were scanned.
     */
    private final int filesScanned;

    /**
     * List of all violations found during validation.
     */
    private final List<GuardViolation> violations;

    /**
     * Overall validation status: "GREEN" for success, "RED" for failure.
     */
    private final String status;

    /**
     * Error message describing why validation failed (null if GREEN).
     */
    private final String errorMessage;

    /**
     * Summary of violations by pattern type.
     */
    private final HyperStandardsValidator.GuardSummary summary;

    /**
     * Creates a successful guard validation receipt.
     *
     * @param phase the validation phase name
     * @param timestamp when validation was performed
     * @param filesScanned number of files scanned
     * @param violations list of violations (empty for success)
     */
    public GuardReceipt(String phase, Instant timestamp, int filesScanned, List<GuardViolation> violations) {
        this(phase, timestamp, filesScanned, violations, "GREEN", null, null);
    }

    /**
     * Creates a guard validation receipt with results.
     *
     * @param phase the validation phase name
     * @param timestamp when validation was performed
     * @param filesScanned number of files scanned
     * @param violations list of violations found
     * @param status validation status ("GREEN" or "RED")
     * @param errorMessage error message (null if GREEN)
     * @param summary violation summary (can be null)
     */
    public GuardReceipt(String phase, Instant timestamp, int filesScanned,
                       List<GuardViolation> violations, String status,
                       String errorMessage, HyperStandardsValidator.GuardSummary summary) {
        this.phase = Objects.requireNonNull(phase, "Phase cannot be null");
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp cannot be null");
        this.filesScanned = filesScanned;
        this.violations = violations != null ?
            Collections.unmodifiableList(new ArrayList<>(violations)) :
            Collections.emptyList();
        this.status = validateStatus(status);
        this.errorMessage = errorMessage;
        this.summary = summary;
    }

    /**
     * Validates that the status is either "GREEN" or "RED".
     */
    private String validateStatus(String status) {
        Objects.requireNonNull(status, "Status cannot be null");
        if (!"GREEN".equals(status) && !"RED".equals(status)) {
            throw new IllegalArgumentException("Status must be either 'GREEN' or 'RED', got: " + status);
        }
        return status;
    }

    // Getters

    public String getPhase() {
        return phase;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getFilesScanned() {
        return filesScanned;
    }

    public List<GuardViolation> getViolations() {
        return violations;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public HyperStandardsValidator.GuardSummary getSummary() {
        return summary;
    }

    /**
     * Returns whether the validation was successful (GREEN status).
     *
     * @return true if no violations found, false otherwise
     */
    public boolean isGreen() {
        return "GREEN".equals(status);
    }

    /**
     * Returns whether the validation failed (RED status).
     *
     * @return true if violations found, false otherwise
     */
    public boolean isRed() {
        return "RED".equals(status);
    }

    /**
     * Returns whether any violations were found during validation.
     *
     * @return true if violations exist, false if validation passed
     */
    public boolean hasViolations() {
        return !violations.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GuardReceipt that = (GuardReceipt) o;
        return filesScanned == that.filesScanned &&
               phase.equals(that.phase) &&
               timestamp.equals(that.timestamp) &&
               violations.equals(that.violations) &&
               status.equals(that.status) &&
               Objects.equals(errorMessage, that.errorMessage) &&
               Objects.equals(summary, that.summary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(phase, timestamp, filesScanned, violations, status, errorMessage, summary);
    }

    @Override
    public String toString() {
        return "GuardReceipt{" +
               "phase='" + phase + '\'' +
               ", timestamp=" + timestamp +
               ", filesScanned=" + filesScanned +
               ", violations=" + violations.size() +
               ", status='" + status + '\'' +
               (errorMessage != null ? ", errorMessage='" + errorMessage + '\'' : "") +
               (summary != null ? ", summary=" + summary : "") +
               '}';
    }
}