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

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Versioned, immutable envelope for YAWL workflow lifecycle events.
 *
 * <p>All fields are part of the stable v1.0 event schema. Schema evolution
 * follows a compatible extension model: new optional fields may be added,
 * existing fields are never removed or renamed within a schema version.
 *
 * <h2>JSON representation (Avro-compatible field ordering)</h2>
 * <pre>
 * {
 *   "eventId":       "550e8400-e29b-41d4-a716-446655440000",
 *   "eventType":     "CASE_STARTED",
 *   "schemaVersion": "1.0",
 *   "specId":        "OrderFulfillment:1.0",
 *   "caseId":        "42",
 *   "workItemId":    null,
 *   "timestamp":     "2026-02-17T10:00:00.000Z",
 *   "payload": {
 *     "caseParams": "...",
 *     "launchedBy": "agent-order-service"
 *   }
 * }
 * </pre>
 *
 * <h2>Avro Schema Definition</h2>
 * <pre>
 * {
 *   "namespace": "org.yawlfoundation.yawl.events",
 *   "type": "record",
 *   "name": "WorkflowEvent",
 *   "fields": [
 *     {"name": "eventId",       "type": "string"},
 *     {"name": "eventType",     "type": {"type": "enum", "name": "EventType",
 *       "symbols": ["CASE_STARTED","CASE_COMPLETED","CASE_CANCELLED","CASE_SUSPENDED",
 *                  "CASE_RESUMED","WORKITEM_ENABLED","WORKITEM_STARTED",
 *                  "WORKITEM_COMPLETED","WORKITEM_CANCELLED","WORKITEM_FAILED",
 *                  "WORKITEM_SUSPENDED","SPEC_LOADED","SPEC_UNLOADED"]}},
 *     {"name": "schemaVersion", "type": "string", "default": "1.0"},
 *     {"name": "specId",        "type": "string"},
 *     {"name": "caseId",        "type": "string"},
 *     {"name": "workItemId",    "type": ["null", "string"], "default": null},
 *     {"name": "timestamp",     "type": {"type": "long", "logicalType": "timestamp-micros"}},
 *     {"name": "payload",       "type": {"type": "map", "values": "string"}}
 *   ]
 * }
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class WorkflowEvent {

    /**
     * All YAWL workflow event types.
     *
     * <p>Case-level events carry a null {@code workItemId}.
     * Work-item-level events carry a non-null {@code workItemId}.
     * Specification-level events carry null values for both {@code caseId} and {@code workItemId}.
     */
    public enum EventType {
        // Case lifecycle
        CASE_STARTED,
        CASE_COMPLETED,
        CASE_CANCELLED,
        CASE_SUSPENDED,
        CASE_RESUMED,

        // Work item lifecycle
        WORKITEM_ENABLED,
        WORKITEM_STARTED,
        WORKITEM_COMPLETED,
        WORKITEM_CANCELLED,
        WORKITEM_FAILED,
        WORKITEM_SUSPENDED,

        // Specification lifecycle
        SPEC_LOADED,
        SPEC_UNLOADED
    }

    /** Current schema version. Increment on breaking changes only. */
    public static final String SCHEMA_VERSION = "1.0";

    private final String            eventId;
    private final EventType         eventType;
    private final String            schemaVersion;
    private final String            specId;
    private final String            caseId;
    private final String            workItemId;
    private final Instant           timestamp;
    private final Map<String, String> payload;

    /**
     * Construct a workflow event with a generated UUID and current timestamp.
     *
     * @param eventType  type of event (must not be null)
     * @param specId     specification identifier (must not be blank)
     * @param caseId     case identifier (null for SPEC_LOADED/SPEC_UNLOADED)
     * @param workItemId work item identifier (null for case-level events)
     * @param payload    event-specific payload data (may be empty, not null)
     */
    public WorkflowEvent(EventType eventType, String specId, String caseId,
                         String workItemId, Map<String, String> payload) {
        this(UUID.randomUUID().toString(), eventType, SCHEMA_VERSION, specId, caseId,
             workItemId, Instant.now(), payload);
    }

    /**
     * Construct a workflow event with all fields specified.
     * Used for deserialization and replay scenarios.
     *
     * @param eventId       unique event identifier
     * @param eventType     type of event
     * @param schemaVersion schema version string
     * @param specId        specification identifier
     * @param caseId        case identifier
     * @param workItemId    work item identifier (may be null)
     * @param timestamp     event timestamp
     * @param payload       event payload data
     */
    public WorkflowEvent(String eventId, EventType eventType, String schemaVersion,
                         String specId, String caseId, String workItemId,
                         Instant timestamp, Map<String, String> payload) {
        this.eventId       = Objects.requireNonNull(eventId, "eventId");
        this.eventType     = Objects.requireNonNull(eventType, "eventType");
        this.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        this.specId        = Objects.requireNonNull(specId, "specId");
        this.caseId        = caseId;   // null for spec-level events
        this.workItemId    = workItemId; // null for case-level events
        this.timestamp     = Objects.requireNonNull(timestamp, "timestamp");
        this.payload       = Collections.unmodifiableMap(
                                 payload != null ? payload : Map.of());
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Unique event identifier (UUID v4). */
    public String getEventId()       { return eventId; }

    /** Event type. */
    public EventType getEventType()  { return eventType; }

    /** Schema version string (currently "1.0"). */
    public String getSchemaVersion() { return schemaVersion; }

    /** Specification ID in the format {@code specURI:version}. */
    public String getSpecId()        { return specId; }

    /** Case identifier. Null for SPEC_LOADED / SPEC_UNLOADED events. */
    public String getCaseId()        { return caseId; }

    /** Work item identifier. Null for case-level and spec-level events. */
    public String getWorkItemId()    { return workItemId; }

    /** UTC timestamp when the event was created. */
    public Instant getTimestamp()    { return timestamp; }

    /** Event-specific payload as a string-to-string map. */
    public Map<String, String> getPayload() { return payload; }

    /**
     * Returns true if this event requires exactly-once delivery guarantee.
     * State-completing events (CASE_COMPLETED, WORKITEM_COMPLETED) require
     * exactly-once semantics to prevent duplicate processing by consumers.
     *
     * @return true for exactly-once events
     */
    public boolean requiresExactlyOnceDelivery() {
        return eventType == EventType.CASE_COMPLETED
            || eventType == EventType.WORKITEM_COMPLETED;
    }

    /**
     * Returns the Kafka topic routing key for this event.
     * Format: {@code yawl.{eventType.toLowerCase()}}
     *
     * @return Kafka topic name
     */
    public String kafkaTopic() {
        return "yawl." + eventType.name().toLowerCase().replace('_', '-');
    }

    /**
     * Returns the RabbitMQ routing key for this event.
     * Format: {@code workflow.{eventType.toLowerCase()}}
     *
     * @return RabbitMQ routing key
     */
    public String rabbitRoutingKey() {
        return "workflow." + eventType.name().toLowerCase().replace('_', '.');
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkflowEvent)) return false;
        WorkflowEvent that = (WorkflowEvent) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "WorkflowEvent{eventId='" + eventId
             + "', type=" + eventType
             + ", specId='" + specId
             + "', caseId='" + caseId
             + "', workItemId='" + workItemId
             + "', ts=" + timestamp + '}';
    }
}
