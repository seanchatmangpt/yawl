package org.yawlfoundation.yawl.ggen.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the receipt from guard validation phase (H phase).
 *
 * <p>This class contains the results of running guard checks against
 * generated Java code. It includes metadata about the validation run,
 * a list of violations found, and summary statistics.</p>
 *
 * <p><b>Json Serialization:</b></p>
 * <p>Class is annotated for JSON serialization using Jackson.
 * Non-null values are included in the output.</p>
 *
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * GuardReceipt receipt = new GuardReceipt(
 *     "guards",
 *     Instant.now(),
 *     42,
 *     violations,
 *     "RED",
 *     "3 guard violations found"
 * );
 * }</pre>
 *
 * @see org.yawlfoundation.yawl.ggen.validation.HyperStandardsValidator
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class GuardReceipt {

    private final String phase;
    private final Instant timestamp;
    private final int filesScanned;
    private final List<GuardViolation> violations;
    private final String status;
    private final String errorMessage;

    @JsonProperty("summary")
    private final GuardSummary summary;

    /**
     * Creates a new guard receipt.
     *
     * @param phase the validation phase name (e.g., "guards")
     * @param timestamp when the validation was performed
     * @param filesScanned number of files that were scanned
     * @param violations list of violations found (empty if none)
     * @param status overall status ("GREEN" or "RED")
     * @param errorMessage error message if status is "RED", null otherwise
     * @throws NullPointerException if phase, timestamp, violations, or status is null
     * @throws IllegalArgumentException if filesScanned is negative
     */
    public GuardReceipt(String phase, Instant timestamp,
                       int filesScanned, List<GuardViolation> violations,
                       String status, String errorMessage) {
        this.phase = Objects.requireNonNull(phase, "Phase cannot be null");
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp cannot be null");
        this.violations = Objects.requireNonNull(violations, "Violations cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");

        if (filesScanned < 0) {
            throw new IllegalArgumentException("Files scanned cannot be negative: " + filesScanned);
        }
        this.filesScanned = filesScanned;

        this.errorMessage = "RED".equals(status) ?
            Objects.requireNonNull(errorMessage, "Error message cannot be null for RED status") :
            null;

        this.summary = new GuardSummary(violations);
    }

    /**
     * Creates a new guard receipt for a successful validation (GREEN).
     *
     * @param phase the validation phase name
     * @param timestamp when the validation was performed
     * @param filesScanned number of files that were scanned
     * @param violations list of violations found (should be empty)
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if filesScanned is negative
     */
    public GuardReceipt(String phase, Instant timestamp,
                       int filesScanned, List<GuardViolation> violations) {
        this(phase, timestamp, filesScanned, violations, "GREEN", null);
    }

    /**
     * Returns the validation phase name.
     *
     * @return the phase name (e.g., "guards")
     */
    public String getPhase() {
        return phase;
    }

    /**
     * Returns when the validation was performed.
     *
     * @return the timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the number of files that were scanned.
     *
     * @return number of files scanned
     */
    public int getFilesScanned() {
        return filesScanned;
    }

    /**
     * Returns the list of violations found.
     *
     * @return list of violations (empty if none)
     */
    public List<GuardViolation> getViolations() {
        return violations;
    }

    /**
     * Returns the overall validation status.
     *
     * @return "GREEN" if no violations, "RED" if violations found
     */
    public String getStatus() {
        return status;
    }

    /**
     * Returns the error message if validation failed.
     *
     * @return error message, or null if validation succeeded
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Returns the violation summary.
     *
     * @return summary of violations by pattern type
     */
    public GuardSummary getSummary() {
        return summary;
    }

    /**
     * Determines if validation was successful (GREEN status).
     *
     * @return true if no violations found, false otherwise
     */
    public boolean isGreen() {
        return "GREEN".equals(status);
    }

    /**
     * Determines if validation failed (RED status).
     *
     * @return true if violations found, false otherwise
     */
    public boolean isRed() {
        return "RED".equals(status);
    }

    /**
     * Returns the total number of violations.
     *
     * @return total violation count
     */
    public int getTotalViolations() {
        return violations.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GuardReceipt that = (GuardReceipt) o;
        return filesScanned == that.filesScanned &&
               Objects.equals(phase, that.phase) &&
               Objects.equals(timestamp, that.timestamp) &&
               Objects.equals(violations, that.violations) &&
               Objects.equals(status, that.status) &&
               Objects.equals(errorMessage, that.errorMessage) &&
               Objects.equals(summary, that.summary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(phase, timestamp, filesScanned, violations, status, errorMessage, summary);
    }

    @Override
    public String toString() {
        return String.format("GuardReceipt{phase='%s', status='%s', filesScanned=%d, totalViolations=%d}",
                phase, status, filesScanned, getTotalViolations());
    }
}