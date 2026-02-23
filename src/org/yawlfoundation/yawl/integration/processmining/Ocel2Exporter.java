/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /**
     * Container for an OCEL2 (Object-Centric Event Log v2.0) event log.
     * Holds an ordered list of {@link Ocel2Event} instances for analysis.
     */
    public static final class Ocel2EventLog {

        private final List<Ocel2Event> events;

        /**
         * Construct an event log from an ordered list of events.
         *
         * @param events ordered list of events (must not be null)
         */
        public Ocel2EventLog(List<Ocel2Event> events) {
            this.events = Collections.unmodifiableList(
                    new ArrayList<>(Objects.requireNonNull(events, "events must not be null")));
        }

        /**
         * Returns all events in this log in the order they were added.
         *
         * @return unmodifiable ordered list of events
         */
        public List<Ocel2Event> getEvents() {
            return events;
        }
    }

    /**
     * A single event within an OCEL2 event log.
     * Captures the activity name, timestamp, and object bindings (omap) of the event.
     */
    public static final class Ocel2Event {

        private final String id;
        private final String activity;
        private final Instant time;
        private final Map<String, List<String>> objects;
        private final Map<String, Object> properties;

        /**
         * Construct an OCEL2 event.
         *
         * @param id        event identifier; must not be null
         * @param activity activity name (ocel:activity); must not be null
         * @param time     event timestamp (ocel:timestamp); must not be null
         * @param objects  map of object-type to list of object IDs (ocel:omap); must not be null
         * @param properties event properties; must not be null
         */
        public Ocel2Event(String id, String activity, Instant time, Map<String, List<String>> objects, Map<String, Object> properties) {
            this.id        = Objects.requireNonNull(id, "id must not be null");
            this.activity = Objects.requireNonNull(activity, "activity must not be null");
            this.time     = Objects.requireNonNull(time, "time must not be null");
            this.objects  = Collections.unmodifiableMap(
                    new LinkedHashMap<>(Objects.requireNonNull(objects, "objects must not be null")));
            this.properties = Collections.unmodifiableMap(
                    new LinkedHashMap<>(Objects.requireNonNull(properties, "properties must not be null")));
        }

        /** Returns the event identifier. */
        public String getId() { return id; }

        /** Returns the activity name for this event. */
        public String getActivity() { return activity; }

        /** Returns the event timestamp. */
        public Instant getTime() { return time; }

        /**
         * Returns the object map (omap): each entry maps an object type
         * (e.g. {@code "case"}, {@code "resource"}) to the list of
         * object IDs of that type associated with this event.
         *
         * @return unmodifiable map of object-type to object-ID lists
         */
        public Map<String, List<String>> getObjects() { return objects; }

        /**
         * Returns the event properties.
         *
         * @return unmodifiable map of event properties
         */
        public Map<String, Object> getProperties() { return properties; }
    }
}
