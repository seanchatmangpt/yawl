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

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceE.YLogGatewayClient;
import org.yawlfoundation.yawl.util.XNode;
import org.yawlfoundation.yawl.util.XNodeParser;

/**
 * Exports YAWL event logs to OCEL 2.0 (Object-Centric Event Log) format.
 *
 * OCEL 2.0 is the IEEE 2023 standard for object-centric process mining, developed by
 * van der Aalst's group. It represents processes as events linked to multiple objects
 * (cases, tasks, resources), enabling analysis of complex multi-instance workflows.
 *
 * YAWL's multi-case, multi-resource execution maps naturally to OCEL object types:
 * - case: One object per case ID (workflow instance)
 * - task: One object per (caseId, taskName) pair (task instance)
 * - resource: One object per unique resource/participant
 *
 * Events are linked to all involved objects via relationships.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class OcelExporter {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneId.of("UTC"));

    private final InterfaceB_EnvironmentBasedClient ibClient;
    private final YLogGatewayClient logClient;
    private final String sessionHandle;

    /**
     * Create an exporter connected to a live YAWL engine.
     *
     * @param engineUrl Base URL of YAWL engine (e.g., "http://localhost:8080/yawl")
     * @param username Username for authentication
     * @param password Password for authentication
     * @throws IOException If connection fails
     */
    public OcelExporter(String engineUrl, String username, String password)
            throws IOException {
        String base = engineUrl.endsWith("/") ? engineUrl.substring(0, engineUrl.length() - 1) : engineUrl;
        this.ibClient = new InterfaceB_EnvironmentBasedClient(base + "/ib");
        this.logClient = new YLogGatewayClient(base + "/logGateway");
        String session = ibClient.connect(username, password);
        if (session == null || session.contains("failure") || session.contains("error")) {
            throw new IOException("Failed to connect: " + session);
        }
        this.sessionHandle = session;
    }

    /**
     * Export OCEL 2.0 log for a specification (all completed cases).
     *
     * @param specId Specification ID
     * @return OCEL 2.0 JSON string
     * @throws IOException If export fails
     */
    public String exportSpecificationToOcel(YSpecificationID specId) throws IOException {
        String xes = logClient.getSpecificationXESLog(specId, true, sessionHandle);
        if (xes == null || xes.contains("<failure>")) {
            throw new IOException("Failed to export XES: " + xes);
        }
        return xesToOcel(xes);
    }

    /**
     * Export OCEL 2.0 log to a file.
     *
     * @param specId Specification ID
     * @param outputPath Output file path
     * @throws IOException If export fails
     */
    public void exportToFile(YSpecificationID specId, Path outputPath) throws IOException {
        String ocel = exportSpecificationToOcel(specId);
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            w.print(ocel);
        }
    }

    /**
     * Convert XES XML string to OCEL 2.0 JSON (static, no engine connection needed).
     *
     * Parses standard XES log format (with xes:trace containing xes:event elements)
     * and produces IEEE OCEL 2.0 JSON with objects (case, task, resource) and events.
     *
     * @param xesXml XES XML string
     * @return OCEL 2.0 JSON string
     */
    public static String xesToOcel(String xesXml) {
        XNode xesRoot = new XNodeParser().parse(xesXml);
        if (xesRoot == null || !xesRoot.getName().equals("log")) {
            return buildEmptyOcel();
        }

        // Collections for OCEL objects and events
        Map<String, OcelObject> objectMap = new LinkedHashMap<>();  // id -> object
        Set<String> objectTypeNames = new HashSet<>();
        Map<String, String> objectTypeAttributes = new HashMap<>();
        List<Map<String, Object>> events = new java.util.ArrayList<>();

        // Track event types and their attributes
        Set<String> eventTypeNames = new HashSet<>();
        eventTypeNames.add("workflow_event");
        Map<String, Set<String>> eventTypeAttributes = new HashMap<>();
        eventTypeAttributes.put("workflow_event", new HashSet<>());
        eventTypeAttributes.get("workflow_event").add("lifecycle");
        eventTypeAttributes.get("workflow_event").add("engineInstanceId");

        // Iterate through traces (one per case)
        List<XNode> traces = xesRoot.getChildren("trace");
        for (XNode trace : traces) {
            String caseId = extractAttribute(trace, "concept:name");
            if (caseId == null || caseId.isEmpty()) {
                caseId = "case-unknown-" + System.currentTimeMillis();
            }

            // Create case object
            String caseObjectId = "case-" + caseId;
            if (!objectMap.containsKey(caseObjectId)) {
                OcelObject caseObj = new OcelObject(caseObjectId, "case");
                caseObj.addAttribute(Instant.now(), "caseId", caseId);
                objectMap.put(caseObjectId, caseObj);
                objectTypeNames.add("case");
            }

            // Process events in trace
            List<XNode> traceEvents = trace.getChildren("event");
            for (XNode event : traceEvents) {
                String timestamp = extractAttribute(event, "time:timestamp");
                if (timestamp == null) {
                    timestamp = Instant.now().toString();
                }

                String taskName = extractAttribute(event, "concept:name");
                if (taskName == null) {
                    taskName = "unknown";
                }

                String instanceId = extractAttribute(event, "concept:instance");
                if (instanceId == null) {
                    instanceId = "unknown";
                }

                String lifecycle = extractAttribute(event, "lifecycle:transition");
                if (lifecycle == null) {
                    lifecycle = "unknown";
                }

                String resourceId = extractAttribute(event, "org:resource");

                // Create task object (per case + task)
                String taskObjectId = "task-" + caseId + "-" + taskName;
                if (!objectMap.containsKey(taskObjectId)) {
                    OcelObject taskObj = new OcelObject(taskObjectId, "task");
                    taskObj.addRelationship(caseObjectId, "belongsTo");
                    objectMap.put(taskObjectId, taskObj);
                    objectTypeNames.add("task");
                }

                // Create resource object if present
                String resourceObjectId = null;
                if (resourceId != null && !resourceId.isEmpty() && !resourceId.equals("unknown")) {
                    resourceObjectId = "resource-" + resourceId;
                    if (!objectMap.containsKey(resourceObjectId)) {
                        OcelObject resourceObj = new OcelObject(resourceObjectId, "resource");
                        objectMap.put(resourceObjectId, resourceObj);
                        objectTypeNames.add("resource");
                    }
                }

                // Create OCEL event
                Map<String, Object> ocelEvent = new LinkedHashMap<>();
                ocelEvent.put("id", "evt-" + caseId + "-" + System.nanoTime());
                ocelEvent.put("type", "workflow_event");
                ocelEvent.put("time", timestamp);

                // Event attributes
                List<Map<String, Object>> attributes = new java.util.ArrayList<>();
                attributes.add(createAttribute("lifecycle", "string", lifecycle));
                attributes.add(createAttribute("engineInstanceId", "string", instanceId));
                ocelEvent.put("attributes", attributes);

                // Event relationships (links to objects)
                List<Map<String, Object>> relationships = new java.util.ArrayList<>();
                relationships.add(createRelationship(caseObjectId, "case"));
                relationships.add(createRelationship(taskObjectId, "task"));
                if (resourceObjectId != null) {
                    relationships.add(createRelationship(resourceObjectId, "resource"));
                }
                ocelEvent.put("relationships", relationships);

                events.add(ocelEvent);
            }
        }

        // Build OCEL 2.0 structure
        Map<String, Object> ocel = new LinkedHashMap<>();

        // Object types with their attribute schemas
        List<Map<String, Object>> objectTypes = new java.util.ArrayList<>();
        for (String typeName : objectTypeNames) {
            Map<String, Object> typeEntry = new LinkedHashMap<>();
            typeEntry.put("name", typeName);
            List<Map<String, Object>> typeAttrs = new java.util.ArrayList<>();

            // Add standard attributes per type
            if ("case".equals(typeName)) {
                typeAttrs.add(createAttributeSchema("caseId", "string"));
            } else if ("task".equals(typeName)) {
                typeAttrs.add(createAttributeSchema("taskName", "string"));
            } else if ("resource".equals(typeName)) {
                typeAttrs.add(createAttributeSchema("resourceId", "string"));
            }

            typeEntry.put("attributes", typeAttrs);
            objectTypes.add(typeEntry);
        }
        ocel.put("objectTypes", objectTypes);

        // Event types with their attribute schemas
        List<Map<String, Object>> eventTypes = new java.util.ArrayList<>();
        for (String eventType : eventTypeNames) {
            Map<String, Object> typeEntry = new LinkedHashMap<>();
            typeEntry.put("name", eventType);
            List<Map<String, Object>> typeAttrs = new java.util.ArrayList<>();

            Set<String> attrs = eventTypeAttributes.getOrDefault(eventType, new HashSet<>());
            for (String attr : attrs) {
                typeAttrs.add(createAttributeSchema(attr, "string"));
            }

            typeEntry.put("attributes", typeAttrs);
            eventTypes.add(typeEntry);
        }
        ocel.put("eventTypes", eventTypes);

        // Objects
        List<Map<String, Object>> objectsList = new java.util.ArrayList<>();
        for (OcelObject obj : objectMap.values()) {
            objectsList.add(obj.toMap());
        }
        ocel.put("objects", objectsList);

        // Events
        ocel.put("events", events);

        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ocel);
        } catch (Exception e) {
            return buildEmptyOcel();
        }
    }

    /**
     * Close the connection to YAWL engine.
     */
    public void close() throws IOException {
        ibClient.disconnect(sessionHandle);
    }

    private static String buildEmptyOcel() {
        Map<String, Object> ocel = new LinkedHashMap<>();
        ocel.put("objectTypes", new java.util.ArrayList<>());
        ocel.put("eventTypes", new java.util.ArrayList<>());
        ocel.put("objects", new java.util.ArrayList<>());
        ocel.put("events", new java.util.ArrayList<>());

        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ocel);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String extractAttribute(XNode node, String attributeName) {
        if (node == null) return null;
        List<XNode> children = node.getChildren();
        for (XNode child : children) {
            if (attributeName.equals(child.getName())) {
                String value = child.getAttributeValue("value");
                if (value != null) return value;
            }
        }
        return null;
    }

    private static Map<String, Object> createAttribute(String name, String type, String value) {
        Map<String, Object> attr = new LinkedHashMap<>();
        attr.put("name", name);
        attr.put("type", type);
        attr.put("value", value);
        return attr;
    }

    private static Map<String, Object> createAttributeSchema(String name, String type) {
        Map<String, Object> attr = new LinkedHashMap<>();
        attr.put("name", name);
        attr.put("type", type);
        return attr;
    }

    private static Map<String, Object> createRelationship(String objectId, String qualifier) {
        Map<String, Object> rel = new LinkedHashMap<>();
        rel.put("objectId", objectId);
        rel.put("qualifier", qualifier);
        return rel;
    }

    /**
     * Internal representation of an OCEL object.
     */
    private static class OcelObject {
        private final String id;
        private final String type;
        private final List<Map<String, Object>> attributes;
        private final List<Map<String, Object>> relationships;

        OcelObject(String id, String type) {
            this.id = id;
            this.type = type;
            this.attributes = new java.util.ArrayList<>();
            this.relationships = new java.util.ArrayList<>();
        }

        void addAttribute(Instant time, String name, String value) {
            Map<String, Object> attr = new LinkedHashMap<>();
            attr.put("time", ISO_FORMATTER.format(time));
            attr.put("name", name);
            attr.put("value", value);
            attributes.add(attr);
        }

        void addRelationship(String objectId, String qualifier) {
            Map<String, Object> rel = new LinkedHashMap<>();
            rel.put("objectId", objectId);
            rel.put("qualifier", qualifier);
            relationships.add(rel);
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("type", type);
            map.put("attributes", attributes);
            map.put("relationships", relationships);
            return map;
        }
    }
}
