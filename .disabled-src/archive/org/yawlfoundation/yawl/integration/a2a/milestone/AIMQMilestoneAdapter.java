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
import java.util.*;

import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;

/**
 * Adapter for converting YAWL workflow milestone events to A2A (agent-to-agent)
 * protocol messages and vice versa.
 *
 * This adapter bridges the gap between YAWL's internal WorkflowEvent system
 * and the A2A message protocol, enabling seamless interoperability with
 * autonomous agents and multi-agent systems.
 *
 * <p>Milestone events are handled specially:
 * - MILESTONE_REACHED events create REACHED state messages
 * - MILESTONE_EXPIRED events create EXPIRED state messages
 * - Task-related milestone events include taskId and taskName
 *
 * <p>Protocol features:
 * - Timestamp synchronization (UTC ISO-8601)
 * - Idempotent serialization/deserialization
 * - Graceful handling of missing optional fields
 * - Full A2A schema validation support
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class AIMQMilestoneAdapter {

    private static final String SCHEMA_VERSION = "1.0";
    private static final String MESSAGE_TYPE_MILESTONE = "MILESTONE_STATE";

    /**
     * Converts a WorkflowEvent to a MilestoneStateMessage.
     *
     * This method handles conversion of YAWL workflow milestone events
     * into A2A protocol messages. It extracts relevant fields from the
     * event payload and creates a properly formatted milestone message.
     *
     * @param event the workflow event (should be milestone-related)
     * @return a milestone state message
     * @throws IllegalArgumentException if event is null or missing required fields
     */
    public static MilestoneStateMessage fromWorkflowEvent(WorkflowEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("WorkflowEvent cannot be null");
        }

        Map<String, String> payload = event.getPayload();
        if (payload == null) {
            payload = new HashMap<>();
        }

        String state = extractMilestoneState(event.getEventType(), payload);
        String previousState = payload.getOrDefault("previousState", "NOT_REACHED");
        String milestoneId = payload.getOrDefault("milestoneId", event.getWorkItemId());
        String milestoneName = payload.getOrDefault("milestoneName", "Unknown Milestone");
        String taskId = payload.getOrDefault("taskId", null);
        String taskName = payload.getOrDefault("taskName", null);
        boolean enabledByMilestone = Boolean.parseBoolean(
            payload.getOrDefault("enabledByMilestone", "false"));

        long reachTimeMs = parseLongField(payload, "reachTimeMs", 0L);
        long expiryTimeoutMs = parseLongField(payload, "expiryTimeoutMs", 0L);
        String milestoneExpression = payload.getOrDefault("milestoneExpression", null);

        return new MilestoneStateMessage(
            event.getCaseId(),
            milestoneId,
            milestoneName,
            state,
            previousState,
            event.getTimestamp(),
            taskId,
            taskName,
            enabledByMilestone,
            event.getSpecId(),
            reachTimeMs,
            expiryTimeoutMs,
            milestoneExpression
        );
    }

    /**
     * Converts a MilestoneStateMessage to a WorkflowEvent.
     *
     * This method creates a YAWL WorkflowEvent from an A2A milestone message,
     * enabling integration with YAWL's event sourcing and monitoring systems.
     *
     * @param message the milestone state message
     * @return a workflow event representing the milestone state change
     * @throws IllegalArgumentException if message is null
     */
    public static WorkflowEvent toWorkflowEvent(MilestoneStateMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("MilestoneStateMessage cannot be null");
        }

        String eventTypeStr = mapMilestoneStateToEventType(message.state());
        WorkflowEvent.EventType eventType = WorkflowEvent.EventType.valueOf(eventTypeStr);

        Map<String, String> payload = new HashMap<>();
        payload.put("milestoneId", message.milestoneId());
        payload.put("milestoneName", message.milestoneName());
        payload.put("state", message.state());
        payload.put("previousState", message.previousState());
        payload.put("enabledByMilestone", String.valueOf(message.enabledByMilestone()));
        payload.put("reachTimeMs", String.valueOf(message.reachTimeMs()));
        payload.put("expiryTimeoutMs", String.valueOf(message.expiryTimeoutMs()));

        if (message.taskId() != null) {
            payload.put("taskId", message.taskId());
        }
        if (message.taskName() != null) {
            payload.put("taskName", message.taskName());
        }
        if (message.milestoneExpression() != null) {
            payload.put("milestoneExpression", message.milestoneExpression());
        }

        return new WorkflowEvent(
            UUID.randomUUID().toString(),
            eventType,
            SCHEMA_VERSION,
            message.specificationId(),
            message.caseId(),
            message.taskId(),
            message.timestamp(),
            payload
        );
    }

    /**
     * Publishes a milestone message as an A2A event via MCP.
     *
     * This is a convenience method for integrating milestone events
     * with MCP event publishers and WebSocket streaming.
     *
     * @param message the milestone message
     * @param publisherFunc function to call with the message
     * @throws Exception if publishing fails
     */
    public static void publishViaA2A(
            MilestoneStateMessage message,
            MilestonePublisher publisherFunc) throws Exception {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        if (publisherFunc == null) {
            throw new IllegalArgumentException("Publisher function cannot be null");
        }

        publisherFunc.publish(message);
    }

    /**
     * Functional interface for milestone publishers.
     */
    public interface MilestonePublisher {
        void publish(MilestoneStateMessage message) throws Exception;
    }

    /**
     * Extracts milestone state from event type and payload.
     */
    private static String extractMilestoneState(
            WorkflowEvent.EventType eventType,
            Map<String, String> payload) {
        // Check if explicit state is in payload
        if (payload.containsKey("state")) {
            return payload.get("state");
        }

        // Otherwise, infer from event type
        return switch(eventType) {
            case WORKITEM_ENABLED -> "REACHED";
            case WORKITEM_SUSPENDED -> "EXPIRED";
            case CASE_SUSPENDED -> "EXPIRED";
            default -> payload.getOrDefault("state", "NOT_REACHED");
        };
    }

    /**
     * Maps milestone state to YAWL event type.
     */
    private static String mapMilestoneStateToEventType(String state) {
        return switch(state) {
            case "REACHED" -> "WORKITEM_ENABLED";
            case "EXPIRED" -> "WORKITEM_SUSPENDED";
            case "NOT_REACHED" -> "CASE_SUSPENDED";
            default -> "WORKITEM_ENABLED";
        };
    }

    /**
     * Parses a long field from payload.
     */
    private static long parseLongField(Map<String, String> payload, String key, long defaultValue) {
        String value = payload.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Validates a milestone message against A2A schema.
     *
     * @param message the message to validate
     * @return validation result
     */
    public static ValidationResult validate(MilestoneStateMessage message) {
        List<String> errors = new ArrayList<>();

        // Check required fields
        if (message.caseId() == null || message.caseId().isBlank()) {
            errors.add("caseId is required");
        }
        if (message.milestoneId() == null || message.milestoneId().isBlank()) {
            errors.add("milestoneId is required");
        }
        if (message.milestoneName() == null || message.milestoneName().isBlank()) {
            errors.add("milestoneName is required");
        }

        // Check state validity
        try {
            MilestoneStateMessage.State.fromString(message.state());
        } catch (IllegalArgumentException e) {
            errors.add("Invalid state: " + message.state());
        }

        try {
            MilestoneStateMessage.State.fromString(message.previousState());
        } catch (IllegalArgumentException e) {
            errors.add("Invalid previousState: " + message.previousState());
        }

        // Check timestamp is not in future (allow 5 second clock skew)
        Instant now = Instant.now();
        if (message.timestamp().isAfter(now.plusSeconds(5))) {
            errors.add("Timestamp is in the future");
        }

        // Check timing fields are non-negative
        if (message.reachTimeMs() < 0) {
            errors.add("reachTimeMs cannot be negative");
        }
        if (message.expiryTimeoutMs() < 0) {
            errors.add("expiryTimeoutMs cannot be negative");
        }

        boolean isValid = errors.isEmpty();
        return new ValidationResult(isValid, errors);
    }

    /**
     * Validation result record.
     */
    public record ValidationResult(
        boolean isValid,
        List<String> errors
    ) {
        public String getSummary() {
            if (isValid) {
                return "Valid";
            }
            return String.join("; ", errors);
        }
    }

    /**
     * Creates a milestone message from a case event and milestone metadata.
     *
     * Convenience factory method for creating messages programmatically.
     *
     * @param caseId the case ID
     * @param specId the specification ID
     * @param milestoneId the milestone ID
     * @param milestoneName the milestone name
     * @param newState the new state
     * @return a new milestone message
     */
    public static MilestoneStateMessage createMessage(
            String caseId,
            String specId,
            String milestoneId,
            String milestoneName,
            String newState) {
        return MilestoneStateMessage.builder()
            .caseId(caseId)
            .specificationId(specId)
            .milestoneId(milestoneId)
            .milestoneName(milestoneName)
            .state(newState)
            .previousState("NOT_REACHED")
            .timestamp(Instant.now())
            .reachTimeMs(0)
            .expiryTimeoutMs(0)
            .build();
    }

    /**
     * Retries publishing a milestone message with exponential backoff.
     *
     * Implements the A2A retry protocol: max 3 retries with
     * exponential backoff (1s, 2s, 4s).
     *
     * @param message the message to publish
     * @param publisher the publisher function
     * @throws Exception if all retries fail
     */
    public static void publishWithRetry(
            MilestoneStateMessage message,
            MilestonePublisher publisher) throws Exception {
        Exception lastException = null;
        int[] delays = {1000, 2000, 4000}; // 1s, 2s, 4s

        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                publisher.publish(message);
                return; // Success
            } catch (Exception e) {
                lastException = e;
                if (attempt < 2) { // Not the last attempt
                    Thread.sleep(delays[attempt]);
                }
            }
        }

        throw new RuntimeException(
            "Failed to publish milestone message after 3 attempts", lastException);
    }
}
