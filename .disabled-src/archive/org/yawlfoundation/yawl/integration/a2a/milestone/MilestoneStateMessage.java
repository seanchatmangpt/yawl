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

package org.yawlfoundation.yawl.integration.a2a.milestone;

import java.time.Instant;
import java.util.Objects;

/**
 * A2A protocol message for milestone state changes in YAWL workflows.
 *
 * This record represents a milestone event that occurs when:
 * - A milestone condition transitions to REACHED state
 * - A milestone condition expires (transitions to EXPIRED state)
 * - A task is enabled or disabled by a milestone state change
 *
 * Messages follow the A2A schema for timestamp synchronization and
 * enable real-time case monitoring for autonomous agents.
 *
 * <p>JSON Schema:
 * <pre>
 * {
 *   "caseId": "case-uuid-12345",
 *   "milestoneId": "milestone-condition-id",
 *   "milestoneName": "Approval Received",
 *   "state": "REACHED",
 *   "previousState": "NOT_REACHED",
 *   "timestamp": "2026-02-28T15:30:00.000Z",
 *   "taskId": "task-uuid-99",
 *   "taskName": "Proceed with Order",
 *   "enabledByMilestone": true,
 *   "specificationId": "OrderProcess:v2.0",
 *   "metadata": {
 *     "reachTime": 125000,
 *     "expiryTimeout": 3600000,
 *     "milestoneExpression": "case.approval_status == 'approved'"
 *   }
 * }
 * </pre>
 *
 * @param caseId case identifier
 * @param milestoneId unique identifier of the milestone condition
 * @param milestoneName human-readable name of the milestone
 * @param state current milestone state (REACHED, NOT_REACHED, EXPIRED)
 * @param previousState previous milestone state
 * @param timestamp UTC timestamp when state changed
 * @param taskId ID of task affected by milestone (null if milestone-only event)
 * @param taskName name of task affected by milestone
 * @param enabledByMilestone true if this event enabled/disabled a task
 * @param specificationId YAWL specification identifier
 * @param reachTimeMs milliseconds from case start to milestone reach
 * @param expiryTimeoutMs milestone expiry timeout in milliseconds
 * @param milestoneExpression XQuery expression that defines the milestone
 * @since YAWL 6.0.0
 */
public record MilestoneStateMessage(
    String caseId,
    String milestoneId,
    String milestoneName,
    String state,
    String previousState,
    Instant timestamp,
    String taskId,
    String taskName,
    boolean enabledByMilestone,
    String specificationId,
    long reachTimeMs,
    long expiryTimeoutMs,
    String milestoneExpression
) {

    /** Milestone state enum */
    public enum State {
        REACHED("REACHED"),
        NOT_REACHED("NOT_REACHED"),
        EXPIRED("EXPIRED");

        private final String value;

        State(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static State fromString(String value) {
            for (State s : State.values()) {
                if (s.value.equals(value)) {
                    return s;
                }
            }
            throw new IllegalArgumentException("Unknown milestone state: " + value);
        }
    }

    /**
     * Creates a new milestone state message with validation.
     *
     * @param caseId case identifier (required)
     * @param milestoneId milestone identifier (required)
     * @param milestoneName milestone name (required)
     * @param state milestone state (required)
     * @param previousState previous state (required)
     * @param timestamp UTC timestamp (required)
     * @param taskId affected task ID (may be null)
     * @param taskName affected task name (may be null)
     * @param enabledByMilestone whether task enabled by milestone
     * @param specificationId specification ID (required)
     * @param reachTimeMs time to reach in milliseconds
     * @param expiryTimeoutMs expiry timeout in milliseconds
     * @param milestoneExpression XQuery expression (may be null)
     */
    public MilestoneStateMessage {
        Objects.requireNonNull(caseId, "caseId cannot be null");
        Objects.requireNonNull(milestoneId, "milestoneId cannot be null");
        Objects.requireNonNull(milestoneName, "milestoneName cannot be null");
        Objects.requireNonNull(state, "state cannot be null");
        Objects.requireNonNull(previousState, "previousState cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(specificationId, "specificationId cannot be null");

        if (caseId.isBlank()) {
            throw new IllegalArgumentException("caseId cannot be blank");
        }
        if (milestoneId.isBlank()) {
            throw new IllegalArgumentException("milestoneId cannot be blank");
        }
        if (specificationId.isBlank()) {
            throw new IllegalArgumentException("specificationId cannot be blank");
        }

        // Validate state values
        try {
            State.fromString(state);
            State.fromString(previousState);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid milestone state: " + e.getMessage());
        }

        if (reachTimeMs < 0) {
            throw new IllegalArgumentException("reachTimeMs cannot be negative");
        }
        if (expiryTimeoutMs < 0) {
            throw new IllegalArgumentException("expiryTimeoutMs cannot be negative");
        }
    }

    /**
     * Converts to A2A message format (JSON-compatible map).
     *
     * @return map representation of this milestone message
     */
    public java.util.Map<String, Object> toA2AMessage() {
        var message = new java.util.LinkedHashMap<String, Object>();
        message.put("caseId", caseId);
        message.put("milestoneId", milestoneId);
        message.put("milestoneName", milestoneName);
        message.put("state", state);
        message.put("previousState", previousState);
        message.put("timestamp", timestamp.toString());
        message.put("specificationId", specificationId);
        message.put("enabledByMilestone", enabledByMilestone);

        if (taskId != null && !taskId.isBlank()) {
            message.put("taskId", taskId);
        }
        if (taskName != null && !taskName.isBlank()) {
            message.put("taskName", taskName);
        }

        var metadata = new java.util.LinkedHashMap<String, Object>();
        metadata.put("reachTimeMs", reachTimeMs);
        metadata.put("expiryTimeoutMs", expiryTimeoutMs);
        if (milestoneExpression != null && !milestoneExpression.isBlank()) {
            metadata.put("milestoneExpression", milestoneExpression);
        }
        message.put("metadata", metadata);

        return message;
    }

    /**
     * Converts this message to JSON string representation.
     *
     * @return JSON string
     */
    public String toJson() {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            return mapper.writeValueAsString(toA2AMessage());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize MilestoneStateMessage to JSON", e);
        }
    }

    /**
     * Creates a milestone message from JSON string.
     *
     * @param json the JSON string
     * @return parsed milestone message
     * @throws IllegalArgumentException if JSON is invalid
     */
    public static MilestoneStateMessage fromJson(String json) {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            var data = mapper.readValue(json,
                new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
            return fromMap(data);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse MilestoneStateMessage from JSON", e);
        }
    }

    /**
     * Creates a milestone message from a map (parsed JSON).
     *
     * @param data the map data
     * @return milestone message
     * @throws IllegalArgumentException if required fields are missing
     */
    public static MilestoneStateMessage fromMap(java.util.Map<String, Object> data) {
        String caseId = getRequiredString(data, "caseId");
        String milestoneId = getRequiredString(data, "milestoneId");
        String milestoneName = getRequiredString(data, "milestoneName");
        String state = getRequiredString(data, "state");
        String previousState = getRequiredString(data, "previousState");
        String specificationId = getRequiredString(data, "specificationId");

        Instant timestamp = Instant.parse(getRequiredString(data, "timestamp"));
        String taskId = getOptionalString(data, "taskId");
        String taskName = getOptionalString(data, "taskName");
        boolean enabledByMilestone = getOptionalBoolean(data, "enabledByMilestone", false);

        long reachTimeMs = 0;
        long expiryTimeoutMs = 0;
        String milestoneExpression = null;

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> metadata =
            (java.util.Map<String, Object>) data.get("metadata");
        if (metadata != null) {
            Object rt = metadata.get("reachTimeMs");
            if (rt instanceof Number) {
                reachTimeMs = ((Number) rt).longValue();
            }
            Object et = metadata.get("expiryTimeoutMs");
            if (et instanceof Number) {
                expiryTimeoutMs = ((Number) et).longValue();
            }
            Object me = metadata.get("milestoneExpression");
            if (me instanceof String) {
                milestoneExpression = (String) me;
            }
        }

        return new MilestoneStateMessage(
            caseId, milestoneId, milestoneName, state, previousState,
            timestamp, taskId, taskName, enabledByMilestone,
            specificationId, reachTimeMs, expiryTimeoutMs, milestoneExpression);
    }

    private static String getRequiredString(java.util.Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Required field missing: " + key);
        }
        return value.toString();
    }

    private static String getOptionalString(java.util.Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    private static boolean getOptionalBoolean(java.util.Map<String, Object> data, String key,
                                              boolean defaultValue) {
        Object value = data.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    /**
     * Creates a builder for constructing milestone messages fluently.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for MilestoneStateMessage.
     */
    public static class Builder {
        private String caseId;
        private String milestoneId;
        private String milestoneName;
        private String state;
        private String previousState;
        private Instant timestamp;
        private String taskId;
        private String taskName;
        private boolean enabledByMilestone;
        private String specificationId;
        private long reachTimeMs;
        private long expiryTimeoutMs;
        private String milestoneExpression;

        public Builder caseId(String caseId) {
            this.caseId = caseId;
            return this;
        }

        public Builder milestoneId(String milestoneId) {
            this.milestoneId = milestoneId;
            return this;
        }

        public Builder milestoneName(String milestoneName) {
            this.milestoneName = milestoneName;
            return this;
        }

        public Builder state(String state) {
            this.state = state;
            return this;
        }

        public Builder previousState(String previousState) {
            this.previousState = previousState;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder taskName(String taskName) {
            this.taskName = taskName;
            return this;
        }

        public Builder enabledByMilestone(boolean enabledByMilestone) {
            this.enabledByMilestone = enabledByMilestone;
            return this;
        }

        public Builder specificationId(String specificationId) {
            this.specificationId = specificationId;
            return this;
        }

        public Builder reachTimeMs(long reachTimeMs) {
            this.reachTimeMs = reachTimeMs;
            return this;
        }

        public Builder expiryTimeoutMs(long expiryTimeoutMs) {
            this.expiryTimeoutMs = expiryTimeoutMs;
            return this;
        }

        public Builder milestoneExpression(String milestoneExpression) {
            this.milestoneExpression = milestoneExpression;
            return this;
        }

        public MilestoneStateMessage build() {
            return new MilestoneStateMessage(
                caseId, milestoneId, milestoneName, state, previousState,
                timestamp, taskId, taskName, enabledByMilestone,
                specificationId, reachTimeMs, expiryTimeoutMs, milestoneExpression);
        }
    }
}
