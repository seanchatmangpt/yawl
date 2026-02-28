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

package org.yawlfoundation.yawl.integration.processmining.ocpm;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Input data structure for Object-Centric Process Mining (OCPM).
 *
 * <p>Captures OCEL 2.0 log data: events (with activities, timestamps, object relationships)
 * and objects (with their type and attributes). Objects are the units of analysis in OCPM,
 * not cases.</p>
 *
 * <p>Frequency threshold filters low-frequency directly-follows edges during discovery.</p>
 */
public record OcpmInput(
    List<OcpmEvent> events,
    List<OcpmObject> objects,
    double frequencyThreshold
) {
    public OcpmInput {
        events = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(events, "events is required")));
        objects = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(objects, "objects is required")));
        if (frequencyThreshold < 0.0 || frequencyThreshold > 1.0) {
            throw new IllegalArgumentException("frequencyThreshold must be in [0.0, 1.0]");
        }
    }

    /**
     * An event in an OCEL 2.0 log.
     *
     * @param eventId unique event identifier
     * @param activity activity name (e.g., "apply", "review", "approve")
     * @param timestamp event occurrence time
     * @param relatedObjects map of objectType → objectId (e.g., "case" → "case-123")
     */
    public record OcpmEvent(
        String eventId,
        String activity,
        Instant timestamp,
        Map<String, String> relatedObjects
    ) {
        public OcpmEvent {
            Objects.requireNonNull(eventId, "eventId is required");
            Objects.requireNonNull(activity, "activity is required");
            Objects.requireNonNull(timestamp, "timestamp is required");
            relatedObjects = Collections.unmodifiableMap(new HashMap<>(
                Objects.requireNonNull(relatedObjects, "relatedObjects is required")
            ));
        }
    }

    /**
     * An object in an OCEL 2.0 log.
     *
     * @param objectId unique object identifier
     * @param objectType object type (e.g., "case", "item", "resource")
     * @param attributes object attributes
     */
    public record OcpmObject(
        String objectId,
        String objectType,
        Map<String, String> attributes
    ) {
        public OcpmObject {
            Objects.requireNonNull(objectId, "objectId is required");
            Objects.requireNonNull(objectType, "objectType is required");
            attributes = Collections.unmodifiableMap(new HashMap<>(
                Objects.requireNonNull(attributes, "attributes is required")
            ));
        }
    }

    /**
     * Parse OCEL 2.0 JSON format into OcpmInput.
     *
     * <p>Expected OCEL 2.0 structure:
     * <pre>
     * {
     *   "ocel:version": "2.0",
     *   "ocel:events": {
     *     "ev-id": {
     *       "ocel:activity": "activity_name",
     *       "ocel:timestamp": "2026-02-28T10:30:00Z",
     *       "ocel:omap": {
     *         "objectType": ["objId1", "objId2"],
     *         ...
     *       }
     *     },
     *     ...
     *   },
     *   "ocel:objects": {
     *     "obj-id": {
     *       "ocel:type": "objectType",
     *       "ocel:ovmap": { "attr": "value", ... }
     *     },
     *     ...
     *   }
     * }
     * </pre>
     *
     * @param ocel2Json OCEL 2.0 JSON string
     * @return OcpmInput instance with default frequencyThreshold=0.0 (all edges)
     * @throws IllegalArgumentException if JSON is malformed or missing required fields
     */
    public static OcpmInput fromOcel2Json(String ocel2Json) {
        return fromOcel2Json(ocel2Json, 0.0);
    }

    /**
     * Parse OCEL 2.0 JSON with custom frequency threshold.
     *
     * @param ocel2Json OCEL 2.0 JSON string
     * @param frequencyThreshold edge frequency threshold [0.0, 1.0]
     * @return OcpmInput instance
     * @throws IllegalArgumentException if JSON is malformed or missing required fields
     */
    public static OcpmInput fromOcel2Json(String ocel2Json, double frequencyThreshold) {
        Objects.requireNonNull(ocel2Json, "ocel2Json is required");

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(ocel2Json);

            // Parse events
            List<OcpmEvent> events = new ArrayList<>();
            JsonNode eventsNode = root.get("ocel:events");
            if (eventsNode != null && eventsNode.isObject()) {
                eventsNode.fields().forEachRemaining(entry -> {
                    String eventId = entry.getKey();
                    JsonNode eventData = entry.getValue();

                    String activity = eventData.get("ocel:activity").asText();
                    String timestamp = eventData.get("ocel:timestamp").asText();
                    Instant instant = Instant.parse(timestamp);

                    // Parse object map (ocel:omap)
                    Map<String, String> relatedObjects = new HashMap<>();
                    JsonNode omap = eventData.get("ocel:omap");
                    if (omap != null && omap.isObject()) {
                        omap.fields().forEachRemaining(omapEntry -> {
                            String objectType = omapEntry.getKey();
                            JsonNode objectIds = omapEntry.getValue();
                            if (objectIds.isArray() && objectIds.size() > 0) {
                                // For simplicity, take first object of each type per event
                                String objectId = objectIds.get(0).asText();
                                relatedObjects.put(objectType, objectId);
                            }
                        });
                    }

                    events.add(new OcpmEvent(eventId, activity, instant, relatedObjects));
                });
            }

            // Parse objects
            List<OcpmObject> objects = new ArrayList<>();
            JsonNode objectsNode = root.get("ocel:objects");
            if (objectsNode != null && objectsNode.isObject()) {
                objectsNode.fields().forEachRemaining(entry -> {
                    String objectId = entry.getKey();
                    JsonNode objectData = entry.getValue();

                    String objectType = objectData.get("ocel:type").asText();

                    // Parse object attributes (ocel:ovmap)
                    Map<String, String> attributes = new HashMap<>();
                    JsonNode ovmap = objectData.get("ocel:ovmap");
                    if (ovmap != null && ovmap.isObject()) {
                        ovmap.fields().forEachRemaining(attrEntry ->
                            attributes.put(attrEntry.getKey(), attrEntry.getValue().asText())
                        );
                    }

                    objects.add(new OcpmObject(objectId, objectType, attributes));
                });
            }

            // Sort events by timestamp
            events.sort((e1, e2) -> e1.timestamp().compareTo(e2.timestamp()));

            return new OcpmInput(events, objects, frequencyThreshold);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse OCEL 2.0 JSON: " + e.getMessage(), e);
        }
    }
}
