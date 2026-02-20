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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.a2a.handoff;


import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yawlfoundation.yawl.integration.a2a.validation.SchemaValidator;

/**
 * Immutable record representing a handoff message for agent-to-agent communication.
 *
 * <p>Handoff messages are formatted as A2A text messages with the prefix
 * "YAWL_HANDOFF:" followed by the encoded session data. The message includes
 * basic handoff information plus an optional JSON payload for additional context.
 *
 * <p>Message format:
 * <pre>
 * {
 *   "parts": [{
 *     "type": "text",
 *     "text": "YAWL_HANDOFF:WI-42:encrypted_token"
 *   }, {
 *     "type": "data",
 *     "data": {
 *       "reasoning": "This document requires specialized expertise",
 *       "priority": "high",
 *       "estimated_duration": "PT30M"
 *     }
 *   }]
 * }
 * </pre>
 *
 * @param workItemId the YAWL work item ID being handed off
 * @param fromAgent the source agent ID initiating the handoff
 * @param toAgent the target agent ID receiving the handoff
 * @param token the handoff token with session information
 * @param payload optional additional data from the source agent
 * @param timestamp when the handoff was initiated
 *
 * @since YAWL 5.2
 * @see HandoffProtocol
 * @see HandoffToken
 * @see HandoffSession
 */
public record HandoffMessage(
    String workItemId,
    String fromAgent,
    String toAgent,
    HandoffToken token,
    Map<String, Object> payload,
    Instant timestamp
) {

    /**
     * Creates a new handoff message with minimal payload.
     *
     * @param workItemId the YAWL work item ID being handed off
     * @param fromAgent the source agent ID initiating the handoff
     * @param toAgent the target agent ID receiving the handoff
     * @param token the handoff token with session information
     * @param timestamp when the handoff was initiated
     */
    public HandoffMessage(
        String workItemId,
        String fromAgent,
        String toAgent,
        HandoffToken token,
        Instant timestamp
    ) {
        this(workItemId, fromAgent, toAgent, token, java.util.Collections.emptyMap(), timestamp);
    }

    /**
     * Creates a new handoff message with all fields.
     *
     * @param workItemId the YAWL work item ID being handed off
     * @param fromAgent the source agent ID initiating the handoff
     * @param toAgent the target agent ID receiving the handoff
     * @param token the handoff token with session information
     * @param payload optional additional data from the source agent
     * @param timestamp when the handoff was initiated
     */
    public HandoffMessage {
        Objects.requireNonNull(workItemId, "workItemId cannot be null");
        Objects.requireNonNull(fromAgent, "fromAgent cannot be null");
        Objects.requireNonNull(toAgent, "toAgent cannot be null");
        Objects.requireNonNull(token, "token cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");

        if (workItemId.isBlank()) {
            throw new IllegalArgumentException("workItemId cannot be blank");
        }
        if (fromAgent.isBlank()) {
            throw new IllegalArgumentException("fromAgent cannot be blank");
        }
        if (toAgent.isBlank()) {
            throw new IllegalArgumentException("toAgent cannot be blank");
        }
        if (!fromAgent.equals(token.fromAgent())) {
            throw new IllegalArgumentException("fromAgent must match token.fromAgent()");
        }
        if (!toAgent.equals(token.toAgent())) {
            throw new IllegalArgumentException("toAgent must match token.toAgent()");
        }
        if (!workItemId.equals(token.workItemId())) {
            throw new IllegalArgumentException("workItemId must match token.workItemId()");
        }
    }

    /**
     * Creates a handoff message from a decoded A2A message.
     *
     * @param parts the message parts from A2A
     * @return a parsed handoff message
     * @throws HandoffException if the message format is invalid
     */
    public static HandoffMessage fromA2AMessage(List<Map<String, Object>> parts) throws HandoffException {
        try {
            // Extract the text part containing the handoff information
            String handoffText = parts.stream()
                .filter(part -> "text".equals(part.get("type")))
                .map(part -> (String) part.get("text"))
                .findFirst()
                .orElseThrow(() -> new HandoffException("Handoff message missing text part"));

            // Parse the handoff prefix
            if (!handoffText.startsWith("YAWL_HANDOFF:")) {
                throw new HandoffException("Handoff message missing YAWL_HANDOFF prefix");
            }

            String[] partsArray = handoffText.substring("YAWL_HANDOFF:".length()).split(":", 3);
            if (partsArray.length < 3) {
                throw new HandoffException("Invalid handoff message format");
            }

            String workItemId = partsArray[0];
            String token = partsArray[1];
            String fromAgent = partsArray[2];

            // Extract the payload if present
            Map<String, Object> payload = parts.stream()
                .filter(part -> "data".equals(part.get("type")))
                .map(part -> (Map<String, Object>) part.get("data"))
                .findFirst()
                .orElse(java.util.Collections.emptyMap());

            // Create the token (this would normally be decrypted here)
            HandoffToken handoffToken = new HandoffToken(
                workItemId,
                fromAgent,
                payload.getOrDefault("toAgent", "").toString(),
                payload.getOrDefault("engineSession", "").toString(),
                Instant.now().plusSeconds(60) // Default expiry
            );

            return new HandoffMessage(
                workItemId,
                fromAgent,
                handoffToken.toAgent(),
                handoffToken,
                payload,
                Instant.now()
            );
        } catch (Exception e) {
            throw new HandoffException("Failed to parse handoff message", e);
        }
    }

    /**
     * Converts this handoff message to A2A message format.
     *
     * @return the message parts ready for A2A transmission
     */
    public List<Map<String, Object>> toA2AMessage() {
        List<Map<String, Object>> parts = new java.util.ArrayList<>();

        // Add the text part with handoff information
        Map<String, Object> textPart = new java.util.HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", "YAWL_HANDOFF:" + workItemId + ":" + fromAgent);
        parts.add(textPart);

        // Add the payload if present
        if (!payload.isEmpty()) {
            Map<String, Object> dataPart = new java.util.HashMap<>();
            dataPart.put("type", "data");
            dataPart.put("data", Map.copyOf(payload));
            parts.add(dataPart);
        }

        return parts;
    }

    /**
     * Validates this handoff message against the A2A schema.
     *
     * @return true if validation passes
     * @throws HandoffException if validation fails
     */
    public boolean validate() throws HandoffException {
        SchemaValidator validator = new SchemaValidator();
        String json = toJson();
        SchemaValidator.ValidationResult result = validator.validateMessage(json);
        if (!result.isValid()) {
            throw new HandoffException("Validation failed: " + result.getSummary());
        }
        return true;
    }

    /**
     * Validates this handoff message against the A2A schema with business rules.
     *
     * @return true if validation with business rules passes
     * @throws HandoffException if validation fails
     */
    public boolean validateWithBusinessRules() throws HandoffException {
        // First do schema validation
        validate();
        // Then business rules
        if (!token.isValid()) {
            throw new HandoffException("Handoff token has expired");
        }
        if (fromAgent.equals(toAgent)) {
            throw new HandoffException("fromAgent and toAgent cannot be the same");
        }
        return true;
    }

    /**
     * Converts this handoff message to JSON string.
     *
     * @return JSON representation of this message
     */
    public String toJson() {
        Map<String, Object> message = Map.of("parts", toA2AMessage());
        return new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
            .set("parts", new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(toA2AMessage()))
            .toString();
    }

    /**
     * Creates a handoff message from JSON string with validation.
     *
     * @param json the JSON string representing a handoff message
     * @return validated handoff message
     * @throws SchemaValidationError if validation fails
     * @throws HandoffException if parsing fails
     */
    public static HandoffMessage fromJsonWithValidation(String json) throws HandoffException {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> root = mapper.readValue(json, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = (List<Map<String, Object>>) root.get("parts");
            HandoffMessage message = fromA2AMessage(parts);
            message.validate();
            return message;
        } catch (IOException e) {
            throw new HandoffException("Failed to parse handoff message from JSON", e);
        }
    }

    /**
     * Returns a copy of this message with updated payload.
     *
     * @param additionalPayload the payload data to add
     * @return a new handoff message with merged payload
     */
    public HandoffMessage withPayload(Map<String, Object> additionalPayload) {
        Map<String, Object> merged = new java.util.HashMap<>(this.payload);
        merged.putAll(additionalPayload);
        return new HandoffMessage(
            workItemId,
            fromAgent,
            toAgent,
            token,
            Map.copyOf(merged),
            timestamp
        );
    }

    /**
     * Returns true if this handoff message has expired.
     *
     * @return true if the message is expired, false otherwise
     */
    public boolean isExpired() {
        return token.expiresAt().isBefore(Instant.now());
    }

    /**
     * Returns the time remaining until this handoff expires.
     *
     * @return the duration until expiration, or zero if already expired
     */
    public java.time.Duration timeToExpiry() {
        return token.timeToExpiry();
    }
}