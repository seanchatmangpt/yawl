package org.yawlfoundation.yawl.ggen.validation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Receipt documenting the result of guard validation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GuardReceipt {

    private String phase;
    private Instant timestamp;
    private int filesScanned;

    @JsonProperty("violations")
    private List<GuardViolation> violations;

    private String status;  // GREEN or RED
    private String errorMessage;
    private GuardSummary summary;

    public GuardReceipt() {
        this.phase = "guards";
        this.timestamp = Instant.now();
        this.violations = new ArrayList<>();
        this.status = "GREEN";
        this.summary = new GuardSummary();
    }

    /** Add a violation and update summary. */
    public void addViolation(GuardViolation violation) {
        violations.add(violation);
        summary.increment(violation.getPattern());
        this.status = "RED";
    }

    /** Add all violations from a list. */
    public void addViolations(List<GuardViolation> newViolations) {
        for (GuardViolation v : newViolations) {
            addViolation(v);
        }
    }

    public boolean isGreen() { return "GREEN".equals(status) && violations.isEmpty(); }
    public boolean isRed() { return "RED".equals(status) || !violations.isEmpty(); }

    /** Update status and summary based on current violations. */
    public void updateStatusAndSummary() {
        this.summary = new GuardSummary();
        for (GuardViolation v : violations) {
            summary.increment(v.getPattern());
        }
        this.status = violations.isEmpty() ? "GREEN" : "RED";
    }

    /** Finalize status and summary (alias for updateStatusAndSummary for API compatibility). */
    public void finalizeStatus() {
        updateStatusAndSummary();
    }

    /** Returns exit code based on status: 0 for GREEN, 2 for RED. */
    public int getExitCode() {
        return isGreen() ? 0 : 2;
    }

    /** Serialize to JSON string. */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"phase\": \"").append(phase).append("\",\n");
        sb.append("  \"timestamp\": \"").append(timestamp).append("\",\n");
        sb.append("  \"filesScanned\": ").append(filesScanned).append(",\n");
        sb.append("  \"status\": \"").append(status).append("\",\n");
        sb.append("  \"violations\": [\n");
        for (int i = 0; i < violations.size(); i++) {
            GuardViolation v = violations.get(i);
            sb.append("    {\"pattern\": \"").append(v.getPattern()).append("\", ");
            sb.append("\"file\": \"").append(v.getFile()).append("\", ");
            sb.append("\"line\": ").append(v.getLine()).append(", ");
            sb.append("\"content\": \"").append(escapeJson(v.getContent())).append("\", ");
            sb.append("\"fixGuidance\": \"").append(escapeJson(v.getFixGuidance())).append("\"}");
            if (i < violations.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");
        sb.append("  \"summary\": ").append(summary.toJson()).append(",\n");
        sb.append("  \"errorMessage\": ").append(errorMessage != null ? "\"" + escapeJson(errorMessage) + "\"" : "null").append("\n");
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        // Null-safe JSON string escaping - empty string is valid JSON representation of null/missing
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    // Getters
    public String getPhase() { return phase; }
    public Instant getTimestamp() { return timestamp; }
    public int getFilesScanned() { return filesScanned; }
    public List<GuardViolation> getViolations() { return Collections.unmodifiableList(violations); }
    public String getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public GuardSummary getSummary() { return summary; }

    // Setters
    public void setPhase(String phase) { this.phase = phase; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public void setFilesScanned(int filesScanned) { this.filesScanned = filesScanned; }
    public void setViolations(List<GuardViolation> violations) {
        this.violations = new ArrayList<>(violations);
        this.summary = new GuardSummary();
        for (GuardViolation v : violations) {
            summary.increment(v.getPattern());
        }
        this.status = violations.isEmpty() ? "GREEN" : "RED";
    }
    public void setStatus(String status) { this.status = status; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setSummary(GuardSummary summary) { this.summary = summary; }

    // Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final GuardReceipt receipt = new GuardReceipt();

        public Builder phase(String phase) { receipt.setPhase(phase); return this; }
        public Builder timestamp(Instant timestamp) { receipt.setTimestamp(timestamp); return this; }
        public Builder filesScanned(int filesScanned) { receipt.setFilesScanned(filesScanned); return this; }
        public Builder violation(GuardViolation violation) { receipt.addViolation(violation); return this; }
        public Builder violations(List<GuardViolation> violations) { receipt.addViolations(violations); return this; }
        public Builder errorMessage(String errorMessage) { receipt.setErrorMessage(errorMessage); return this; }
        public GuardReceipt build() { return receipt; }
    }
}
