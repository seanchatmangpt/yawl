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

package org.yawlfoundation.yawl.integration.selfplay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for validating OCel files in test scenarios.
 * Provides methods to validate OCel structure, check event counts, and verify object types.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public class OcelValidator {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Validate that an OCel file is well-formed and meets basic requirements.
     *
     * @param path Path to the OCel file to validate
     * @return true if valid, false otherwise
     * @throws IOException if file cannot be read
     */
    public static boolean isValid(String path) throws IOException {
        try {
            JsonNode root = mapper.readTree(Path.of(path));

            // Check required top-level fields
            if (!root.has("objectTypes") || !root.has("eventTypes") ||
                !root.has("objects") || !root.has("events")) {
                return false;
            }

            // Validate objectTypes array
            JsonNode objectTypes = root.get("objectTypes");
            if (!objectTypes.isArray() || objectTypes.size() == 0) {
                return false;
            }

            // Validate eventTypes array
            JsonNode eventTypes = root.get("eventTypes");
            if (!eventTypes.isArray() || eventTypes.size() == 0) {
                return false;
            }

            // Validate objects array
            JsonNode objects = root.get("objects");
            if (!objects.isArray() || objects.size() == 0) {
                return false;
            }

            // Validate events array
            JsonNode events = root.get("events");
            if (!events.isArray() || events.size() == 0) {
                return false;
            }

            // Validate each object has an id and type
            for (JsonNode obj : objects) {
                if (!obj.has("id") || !obj.has("type")) {
                    return false;
                }
            }

            // Validate each event has an id, type, and time
            for (JsonNode event : events) {
                if (!event.has("id") || !event.has("type") || !event.has("time")) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the number of events in an OCel file.
     *
     * @param path Path to the OCel file
     *return Number of events
     * @throws IOException if file cannot be read
     */
    public static int getEventCount(String path) throws IOException {
        JsonNode root = mapper.readTree(Path.of(path));
        JsonNode events = root.get("events");
        return events.size();
    }

    /**
     * Get all object types in an OCel file.
     *
     * @param path Path to the OCel file
     * @return Set of object type names
     * @throws IOException if file cannot be read
     */
    public static Set<String> getObjectTypes(String path) throws IOException {
        JsonNode root = mapper.readTree(Path.of(path));
        JsonNode objects = root.get("objects");
        Set<String> types = new HashSet<>();

        for (JsonNode obj : objects) {
            types.add(obj.get("type").asText());
        }

        return types;
    }

    /**
     * Get the number of objects of a specific type.
     *
     * @param path Path to the OCel file
     * @param type Object type to count
     * @return Number of objects of the specified type
     * @throws IOException if file cannot be read
     */
    public static int getObjectCountByType(String path, String type) throws IOException {
        JsonNode root = mapper.readTree(Path.of(path));
        JsonNode objects = root.get("objects");
        int count = 0;

        for (JsonNode obj : objects) {
            if (type.equals(obj.get("type").asText())) {
                count++;
            }
        }

        return count;
    }

    /**
     * Validate that an OCel file has all required object types for a PI.
     *
     * @param path Path to the OCel file
     * @return true if has all required PI object types, false otherwise
     * @throws IOException if file cannot be read
     */
    public static boolean hasAllPiObjectTypes(String path) throws IOException {
        Set<String> types = getObjectTypes(path);
        return types.contains("Feature") &&
               types.contains("Team") &&
               types.contains("PI") &&
               types.contains("ART");
    }

    /**
     * Get the total number of objects in an OCel file.
     *
     * @param path Path to the OCel file
     * @return Total number of objects
     * @throws IOException if file cannot be read
     */
    public static int getObjectCount(String path) throws IOException {
        JsonNode root = mapper.readTree(Path.of(path));
        JsonNode objects = root.get("objects");
        return objects.size();
    }

    /**
     * Get the time range of events in an OCel file.
     *
     * @param path Path to the OCel file
     * @return Array with [minTime, maxTime] or null if no events
     * @throws IOException if file cannot be read
     */
    public static String[] getTimeRange(String path) throws IOException {
        JsonNode root = mapper.readTree(Path.of(path));
        JsonNode events = root.get("events");

        if (events.size() == 0) {
            return null;
        }

        String minTime = null;
        String maxTime = null;

        for (JsonNode event : events) {
            String time = event.get("time").asText();
            if (minTime == null || time.compareTo(minTime) < 0) {
                minTime = time;
            }
            if (maxTime == null || time.compareTo(maxTime) > 0) {
                maxTime = time;
            }
        }

        return new String[]{minTime, maxTime};
    }

    /**
     * Validate that all events have relationships to objects.
     *
     * @param path Path to the OCel file
     * @return true if all events have relationships, false otherwise
     * @throws IOException if file cannot be read
     */
    public static boolean allEventsHaveRelationships(String path) throws IOException {
        JsonNode root = mapper.readTree(Path.of(path));
        JsonNode events = root.get("events");

        for (JsonNode event : events) {
            JsonNode relationships = event.get("relationships");
            if (relationships == null || relationships.size() == 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Create a valid OCel JSON structure for testing.
     *
     * @param objectTypes Array of object type names
     * @param eventTypes Array of event type names
     * @param objects Array of objects with id and type
     * @param events Array of events with id, type, time, and relationships
     * @return Valid OCel JSON string
     */
    public static String createOcelJson(String[] objectTypes, String[] eventTypes,
                                      Object[] objects, Object[] events) {
        ObjectNode root = mapper.createObjectNode();

        // Add object types
        ArrayNode objTypesArray = root.putArray("objectTypes");
        for (String type : objectTypes) {
            ObjectNode objType = objTypesArray.addObject();
            objType.put("name", type);
        }

        // Add event types
        ArrayNode eventTypesArray = root.putArray("eventTypes");
        for (String type : eventTypes) {
            ObjectNode eventType = eventTypesArray.addObject();
            eventType.put("name", type);
        }

        // Add objects
        ArrayNode objectsArray = root.putArray("objects");
        for (Object obj : objects) {
            if (obj instanceof Map) {
                Map<?, ?> objMap = (Map<?, ?>) obj;
                ObjectNode objNode = objectsArray.addObject();
                objNode.put("id", (String) objMap.get("id"));
                objNode.put("type", (String) objMap.get("type"));
            }
        }

        // Add events
        ArrayNode eventsArray = root.putArray("events");
        for (Object event : events) {
            if (event instanceof Map) {
                Map<?, ?> eventMap = (Map<?, ?>) event;
                ObjectNode eventNode = eventsArray.addObject();
                eventNode.put("id", (String) eventMap.get("id"));
                eventNode.put("type", (String) eventMap.get("type"));
                eventNode.put("time", (String) eventMap.get("time"));

                // Add relationships if present
                if (eventMap.containsKey("relationships")) {
                    ArrayNode relArray = eventNode.putArray("relationships");
                    for (Map<?, ?> rel : (List<Map<?, ?>>) eventMap.get("relationships")) {
                        ObjectNode relNode = relArray.addObject();
                        relNode.put("objectId", (String) rel.get("objectId"));
                        relNode.put("qualifier", (String) rel.get("qualifier"));
                    }
                }
            }
        }

        return root.toString();
    }
}