package org.yawlfoundation.yawl.integration.selfplay;

import java.util.Objects;

/**
 * Alert indicating a convergence issue that requires attention.
 *
 * <p>Provides structured information about convergence problems with:
 * <ul>
 *   <li>Type of issue (divergence, plateau, slow progress, oscillation)</li>
 *   <li>Severity level for prioritization</li>
 *   <li>Human-readable message describing the issue</li>
 * </ul>
 */
public final class ConvergenceAlert {

    private final AlertType type;
    private final String message;
    private final AlertSeverity severity;

    /**
     * Creates a new convergence alert.
     *
     * @param type the type of convergence issue
     * @param message detailed description of the issue
     * @param severity the severity level for prioritization
     */
    public ConvergenceAlert(AlertType type, String message, AlertSeverity severity) {
        this.type = Objects.requireNonNull(type, "Alert type is required");
        this.message = Objects.requireNonNull(message, "Alert message is required");
        this.severity = Objects.requireNonNull(severity, "Alert severity is required");
    }

    /**
     * Returns the type of convergence issue.
     */
    public AlertType getType() {
        return type;
    }

    /**
     * Returns the alert type (deprecated, use getType() instead).
     */
    public AlertType type() {
        return type;
    }

    /**
     * Returns a detailed description of the issue.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the human-readable message (deprecated, use getMessage() instead).
     */
    public String message() {
        return message;
    }

    /**
     * Returns the severity level for prioritization.
     */
    public AlertSeverity getSeverity() {
        return severity;
    }

    /**
     * Returns the severity level (deprecated, use getSeverity() instead).
     */
    public AlertSeverity severity() {
        return severity;
    }

    /**
     * Returns whether this alert requires immediate attention.
     */
    public boolean requiresImmediateAttention() {
        return severity == AlertSeverity.HIGH;
    }

    /**
     * Returns whether this alert suggests a configuration issue.
     */
    public boolean isConfigurationIssue() {
        return type == AlertType.DIVERGENCE || type == AlertType.OSCILLATION;
    }

    /**
     * Returns whether this alert suggests a performance issue.
     */
    public boolean isPerformanceIssue() {
        return type == AlertType.SLOW_PROGRESS || type == AlertType.PLATEAU;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConvergenceAlert that = (ConvergenceAlert) o;
        return type == that.type &&
               severity == that.severity &&
               message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, message, severity);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", severity, type, message);
    }

    /**
     * Types of convergence issues.
     */
    public enum AlertType {
        DIVERGENCE("Fitness scores are diverging"),
        PLATEAU("Convergence plateau detected"),
        SLOW_PROGRESS("Progress is very slow"),
        OSCILLATION("Fitness scores are oscillating");

        private final String description;

        AlertType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Severity levels for alerts.
     */
    public enum AlertSeverity {
        LOW("Low"),
        MEDIUM("Medium"),
        HIGH("High");

        private final String displayName;

        AlertSeverity(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}