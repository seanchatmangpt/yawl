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

package org.yawlfoundation.yawl.integration.adaptation;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable event record representing an external process event that may trigger
 * workflow adaptation.
 *
 * <p>ProcessEvent captures the essential data of an event: its type, source, timestamp,
 * payload (context-specific data), and severity. Events are processed by
 * {@link EventDrivenAdaptationEngine} to determine if any adaptation rules match
 * and what action should be taken.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * ProcessEvent event = new ProcessEvent(
 *     "evt-fraud-001",
 *     "FRAUD_ALERT",
 *     "fraud-detection-service",
 *     Instant.now(),
 *     Map.of("risk_score", 0.95, "transaction_id", "tx-123"),
 *     EventSeverity.CRITICAL
 * );
 * }</pre>
 *
 * @param eventId      unique identifier for this event
 * @param eventType    semantic type of event (e.g., "FRAUD_ALERT", "RATE_CHANGE")
 * @param sourceSystem source system or service that emitted the event
 * @param timestamp    when the event occurred
 * @param payload      context-specific event data (key-value pairs)
 * @param severity     severity level indicating urgency of adaptation
 *
 * @author YAWL Foundation
 * @since 6.0
 */
public record ProcessEvent(
    String eventId,
    String eventType,
    String sourceSystem,
    Instant timestamp,
    Map<String, Object> payload,
    EventSeverity severity
) {
    /**
     * Compact constructor with validation.
     *
     * @throws NullPointerException if eventId, eventType, sourceSystem, timestamp,
     *                              payload, or severity is null
     * @throws IllegalArgumentException if eventId or eventType is empty
     */
    public ProcessEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(sourceSystem, "sourceSystem must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(severity, "severity must not be null");

        if (eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
    }

    /**
     * Checks if the payload contains a specific key.
     *
     * @param key the key to check
     * @return true if the payload contains the key, false otherwise
     */
    public boolean hasPayloadKey(String key) {
        return payload.containsKey(Objects.requireNonNull(key, "key must not be null"));
    }

    /**
     * Retrieves a value from the payload.
     *
     * @param key the key to retrieve
     * @return the value, or null if not present
     * @throws NullPointerException if key is null
     */
    public Object payloadValue(String key) {
        return payload.get(Objects.requireNonNull(key, "key must not be null"));
    }

    /**
     * Retrieves a numeric value from the payload, parsing it if necessary.
     *
     * <p>If the value is already a {@link Number}, it is returned as a double.
     * If the value is a {@link String}, it is parsed as a double.
     * If the value is null or not present, this method throws an exception.
     *
     * @param key the key to retrieve
     * @return the numeric value as a double
     * @throws NullPointerException if key is null
     * @throws IllegalArgumentException if the value is not present, null, or not numeric
     */
    public double numericPayload(String key) {
        Objects.requireNonNull(key, "key must not be null");
        Object value = payload.get(key);

        if (value == null) {
            throw new IllegalArgumentException("Payload key not found: " + key);
        }

        if (value instanceof Number num) {
            return num.doubleValue();
        }

        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "Payload key '" + key + "' value is not numeric: " + str, e);
            }
        }

        throw new IllegalArgumentException(
            "Payload key '" + key + "' value is not numeric: " + value.getClass().getName());
    }
}
