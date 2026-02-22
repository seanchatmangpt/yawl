/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.processmining;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Exports YAWL workflow events to OCEL2 (Object-Centric Event Log v2.0) JSON format.
 * OCEL2 captures relationships between events, objects, and object types for
 * object-centric process mining analysis.
 *
 * OCEL2 Schema:
 * {
 *   "ocel:version": "2.0",
 *   "ocel:ordering": "timestamp",
 *   "ocel:attribute-names": ["org:resource", ...],
 *   "ocel:object-types": ["Case", "WorkItem", ...],
 *   "ocel:events": { "ev-id": {...}, ... },
 *   "ocel:objects": { "obj-id": {...}, ... }
 * }
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public final class Ocel2Exporter {

    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * Create exporter with default Jackson ObjectMapper.
     */
    public Ocel2Exporter() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Export workflow events to OCEL2 v2.0 JSON format.
     *
     * Converts a list of WorkflowEventRecords into valid OCEL2 JSON structure.
     * Each event is mapped to an ocel:event entry with activity, timestamp, object map,
     * and value attributes. Referenced objects are collected into ocel:objects section.
     *
     * @param events list of WorkflowEventRecord objects to export
     * @return OCEL2 v2.0 compliant JSON string
     * @throws IOException if JSON serialization fails
     */
    public String exportWorkflowEvents(List<WorkflowEventRecord> events) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();

        // OCEL2 metadata
        root.put("ocel:version", "2.0");
        root.put("ocel:ordering", "timestamp");

        // Collect unique attribute names and object types from events
        Set<String> attributeNames = new HashSet<>();
        Set<String> objectTypes = new HashSet<>();
        attributeNames.add("org:resource");
        attributeNames.add("case:id");
        objectTypes.add("Case");
        objectTypes.add("WorkItem");

        // Add attribute names array
        ArrayNode attrArray = objectMapper.createArrayNode();
        for (String attr : attributeNames) {
            attrArray.add(attr);
        }
        root.set("ocel:attribute-names", attrArray);

        // Add object types array
        ArrayNode typeArray = objectMapper.createArrayNode();
        for (String type : objectTypes) {
            typeArray.add(type);
        }
        root.set("ocel:object-types", typeArray);

        // Build events section
        ObjectNode eventsNode = objectMapper.createObjectNode();
        ObjectNode objectsNode = objectMapper.createObjectNode();

        for (WorkflowEventRecord event : events) {
            // Create event entry
            ObjectNode eventEntry = objectMapper.createObjectNode();
            eventEntry.put("ocel:activity", event.activity());
            eventEntry.put("ocel:timestamp", ISO_FORMATTER.format(event.timestamp()));

            // Create object map (ocel:omap)
            ObjectNode omapNode = objectMapper.createObjectNode();
            if (event.caseId() != null && !event.caseId().isEmpty()) {
                ArrayNode caseArray = objectMapper.createArrayNode();
                caseArray.add(event.caseId());
                omapNode.set("Case", caseArray);

                // Ensure Case object exists in objects section
                if (!objectsNode.has(event.caseId())) {
                    ObjectNode caseObject = objectMapper.createObjectNode();
                    caseObject.put("ocel:type", "Case");
                    ObjectNode caseVmap = objectMapper.createObjectNode();
                    caseVmap.put("case:id", event.caseId());
                    caseObject.set("ocel:ovmap", caseVmap);
                    objectsNode.set(event.caseId(), caseObject);
                }
            }

            if (event.workItemId() != null && !event.workItemId().isEmpty()) {
                ArrayNode wiArray = objectMapper.createArrayNode();
                wiArray.add(event.workItemId());
                omapNode.set("WorkItem", wiArray);

                // Ensure WorkItem object exists in objects section
                if (!objectsNode.has(event.workItemId())) {
                    ObjectNode wiObject = objectMapper.createObjectNode();
                    wiObject.put("ocel:type", "WorkItem");
                    wiObject.set("ocel:ovmap", objectMapper.createObjectNode());
                    objectsNode.set(event.workItemId(), wiObject);
                }
            }
            eventEntry.set("ocel:omap", omapNode);

            // Create value map (ocel:vmap) with attributes
            ObjectNode vmapNode = objectMapper.createObjectNode();
            if (event.resource() != null && !event.resource().isEmpty()) {
                vmapNode.put("org:resource", event.resource());
            }
            if (event.caseId() != null && !event.caseId().isEmpty()) {
                vmapNode.put("case:id", event.caseId());
            }
            eventEntry.set("ocel:vmap", vmapNode);

            eventsNode.set(event.eventId(), eventEntry);
        }

        root.set("ocel:events", eventsNode);
        root.set("ocel:objects", objectsNode);

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    /**
     * Create a minimal WorkflowEventRecord from XES event data.
     * Generates a UUID for the event ID.
     *
     * @param caseId case identifier
     * @param activity activity name
     * @param timestamp event timestamp
     * @return WorkflowEventRecord with generated eventId, null workItemId and resource
     */
    public static WorkflowEventRecord fromXesEvent(String caseId, String activity, Instant timestamp) {
        String eventId = UUID.randomUUID().toString();
        return new WorkflowEventRecord(
                eventId,
                caseId,
                null,  // workItemId
                activity,
                null,  // resource
                timestamp,
                "ActivityEvent"
        );
    }

    /**
     * Immutable record representing a single workflow event for OCEL2 export.
     * Captures event identity, associated objects, activity, resource, and timing.
     *
     * @param eventId unique event identifier
     * @param caseId case reference (for ocel:omap)
     * @param workItemId work item reference (for ocel:omap)
     * @param activity activity name (ocel:activity)
     * @param resource resource/user attribute (org:resource)
     * @param timestamp event occurrence time (ocel:timestamp)
     * @param eventType type of event (e.g., "TaskCompleted", "ActivityEvent")
     */
    public record WorkflowEventRecord(
            String eventId,
            String caseId,
            String workItemId,
            String activity,
            String resource,
            Instant timestamp,
            String eventType
    ) {
    }
}
