/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.validation.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Receipt document for guard validation results.
 * Tracks phase metadata, timestamps, violations, and final status.
 * Serializes to JSON for audit trail and debugging.
 */
public class GuardReceipt {
    private String phase = "guards";
    private Instant timestamp;
    private int filesScanned = 0;
    private final List<GuardViolation> violations = new ArrayList<>();
    private String status = "PENDING";
    private String errorMessage;
    private GuardSummary summary;

    /**
     * Create a new guard receipt with current timestamp.
     */
    public GuardReceipt() {
        this.timestamp = Instant.now();
        this.summary = new GuardSummary();
    }

    /**
     * Add a violation to this receipt and update summary.
     *
     * @param violation the GuardViolation to add
     */
    public void addViolation(GuardViolation violation) {
        Objects.requireNonNull(violation, "violation must not be null");
        violations.add(violation);
        summary.increment(violation.getPattern());
    }

    /**
     * Set the final status based on violation count.
     */
    public void finalizeStatus() {
        if (violations.isEmpty()) {
            this.status = "GREEN";
            this.errorMessage = "No guard violations detected.";
        } else {
            this.status = "RED";
            this.errorMessage = violations.size() + " guard violation(s) found. " +
                                "Fix violations or throw UnsupportedOperationException.";
        }
    }

    /**
     * Get the exit code for this receipt.
     * Returns 0 if GREEN (success), 2 if RED (violations found).
     *
     * @return 0 for success, 2 for violations
     */
    public int getExitCode() {
        return "GREEN".equals(status) ? 0 : 2;
    }

    /**
     * Serialize this receipt to JSON string.
     */
    public String toJson() {
        Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
        return gson.toJson(this);
    }

    /**
     * Deserialize a GuardReceipt from JSON string.
     */
    public static GuardReceipt fromJson(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, GuardReceipt.class);
    }

    // Getters and setters

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public int getFilesScanned() {
        return filesScanned;
    }

    public void setFilesScanned(int filesScanned) {
        this.filesScanned = filesScanned;
    }

    public List<GuardViolation> getViolations() {
        return Collections.unmodifiableList(violations);
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

    public void setSummary(GuardSummary summary) {
        this.summary = summary;
    }

    @Override
    public String toString() {
        return "GuardReceipt{" +
                "phase='" + phase + '\'' +
                ", timestamp=" + timestamp +
                ", filesScanned=" + filesScanned +
                ", violationCount=" + violations.size() +
                ", status='" + status + '\'' +
                '}';
    }
}
