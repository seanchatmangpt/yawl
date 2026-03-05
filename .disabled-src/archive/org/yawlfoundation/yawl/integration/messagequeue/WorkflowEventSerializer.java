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

package org.yawlfoundation.yawl.integration.messagequeue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * JSON serializer/deserializer for {@link WorkflowEvent} objects.
 *
 * <p>Produces compact, schema-versioned JSON using Jackson 2.x. The produced
 * format is compatible with the Avro schema definition in the {@link WorkflowEvent}
 * Javadoc and can be ingested by Confluent Schema Registry when using the
 * JSON Schema type (not Avro binary, to avoid schema registry dependency).
 *
 * <p>Thread-safe: a single instance may be shared across all publisher threads.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class WorkflowEventSerializer {

    private final ObjectMapper mapper;

    /**
     * Construct with default Jackson configuration (ISO-8601 timestamps, no nulls).
     */
    public WorkflowEventSerializer() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * Serialize a {@link WorkflowEvent} to UTF-8 JSON bytes.
     *
     * @param event the event to serialize (must not be null)
     * @return UTF-8 encoded JSON bytes
     * @throws IllegalArgumentException if event is null
     * @throws SerializationException   if JSON serialization fails
     */
    public byte[] serialize(WorkflowEvent event) throws SerializationException {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        try {
            Map<String, Object> envelope = buildEnvelope(event);
            return mapper.writeValueAsBytes(envelope);
        } catch (IOException e) {
            throw new SerializationException("Failed to serialize WorkflowEvent " + event.getEventId(), e);
        }
    }

    /**
     * Serialize a {@link WorkflowEvent} to a UTF-8 JSON string.
     *
     * @param event the event to serialize (must not be null)
     * @return JSON string
     * @throws SerializationException if JSON serialization fails
     */
    public String serializeToString(WorkflowEvent event) throws SerializationException {
        return new String(serialize(event), StandardCharsets.UTF_8);
    }

    /**
     * Deserialize UTF-8 JSON bytes into a {@link WorkflowEvent}.
     *
     * @param bytes JSON bytes (must not be null or empty)
     * @return deserialized event
     * @throws SerializationException if deserialization fails or schema is incompatible
     */
    @SuppressWarnings("unchecked")
    public WorkflowEvent deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes must not be null or empty");
        }
        try {
            Map<String, Object> envelope = mapper.readValue(bytes, Map.class);
            return fromEnvelope(envelope);
        } catch (IOException e) {
            throw new SerializationException("Failed to deserialize WorkflowEvent", e);
        }
    }

    // -------------------------------------------------------------------------
    // Envelope building / parsing
    // -------------------------------------------------------------------------

    private Map<String, Object> buildEnvelope(WorkflowEvent event) {
        return Map.of(
            "eventId",       event.getEventId(),
            "eventType",     event.getEventType().name(),
            "schemaVersion", event.getSchemaVersion(),
            "specId",        event.getSpecId(),
            "caseId",        event.getCaseId() != null ? event.getCaseId() : "",
            "workItemId",    event.getWorkItemId() != null ? event.getWorkItemId() : "",
            "timestamp",     event.getTimestamp().toString(),
            "payload",       event.getPayload()
        );
    }

    @SuppressWarnings("unchecked")
    private WorkflowEvent fromEnvelope(Map<String, Object> envelope) throws SerializationException {
        try {
            String eventId       = (String) envelope.get("eventId");
            String eventTypeStr  = (String) envelope.get("eventType");
            String schemaVersion = (String) envelope.getOrDefault("schemaVersion", WorkflowEvent.SCHEMA_VERSION);
            String specId        = (String) envelope.get("specId");
            String caseId        = nullIfBlank((String) envelope.get("caseId"));
            String workItemId    = nullIfBlank((String) envelope.get("workItemId"));
            String timestampStr  = (String) envelope.get("timestamp");

            if (eventId == null || eventTypeStr == null || specId == null || timestampStr == null) {
                throw new SerializationException(
                        "Event envelope missing required fields: eventId, eventType, specId, or timestamp");
            }

            WorkflowEvent.EventType eventType = WorkflowEvent.EventType.valueOf(eventTypeStr);
            java.time.Instant timestamp       = java.time.Instant.parse(timestampStr);
            Map<String, String> payload       = (Map<String, String>) envelope.getOrDefault("payload", Map.of());

            return new WorkflowEvent(eventId, eventType, schemaVersion, specId,
                                     caseId, workItemId, timestamp, payload);
        } catch (SerializationException e) {
            throw e;
        } catch (Exception e) {
            throw new SerializationException("Failed to parse event envelope: " + e.getMessage(), e);
        }
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /**
     * Thrown when event serialization or deserialization fails.
     */
    public static final class SerializationException extends Exception {

        /**
         * Construct with message.
         *
         * @param message description of the failure
         */
        public SerializationException(String message) {
            super(message);
        }

        /**
         * Construct with message and cause.
         *
         * @param message description of the failure
         * @param cause   underlying exception
         */
        public SerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
