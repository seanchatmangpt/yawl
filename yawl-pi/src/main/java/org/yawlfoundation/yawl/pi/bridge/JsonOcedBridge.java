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

package org.yawlfoundation.yawl.pi.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.yawlfoundation.yawl.pi.PIException;

import java.util.HashSet;
import java.util.Set;

/**
 * OCEL2 bridge for JSON event logs.
 *
 * <p>Parses JSON array of objects and converts to OCEL2 v2.0 JSON format.
 * Each object becomes an event; unique case IDs become objects.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public final class JsonOcedBridge implements OcedBridge {

    private final SchemaInferenceEngine schemaInferenceEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Create JSON OCEL2 bridge.
     *
     * @param schemaInferenceEngine engine for inferring field roles
     */
    public JsonOcedBridge(SchemaInferenceEngine schemaInferenceEngine) {
        if (schemaInferenceEngine == null) {
            throw new IllegalArgumentException("schemaInferenceEngine is required");
        }
        this.schemaInferenceEngine = schemaInferenceEngine;
    }

    @Override
    public OcedSchema inferSchema(String rawSample) throws PIException {
        return schemaInferenceEngine.infer(rawSample, "json");
    }

    @Override
    public String convert(String rawData, OcedSchema schema) throws PIException {
        try {
            JsonNode arrayNode = objectMapper.readTree(rawData);
            if (!arrayNode.isArray()) {
                throw new PIException(
                    "JSON data must be an array of objects",
                    "dataprep"
                );
            }

            ObjectNode root = objectMapper.createObjectNode();
            root.put("ocel:version", "2.0");
            root.put("ocel:ordering", "timestamp");

            ArrayNode attrArray = objectMapper.createArrayNode();
            attrArray.add("org:resource");
            root.set("ocel:attribute-names", attrArray);

            ArrayNode typeArray = objectMapper.createArrayNode();
            typeArray.add("case");
            root.set("ocel:object-types", typeArray);

            ObjectNode eventsNode = objectMapper.createObjectNode();
            ObjectNode objectsNode = objectMapper.createObjectNode();
            Set<String> seenCases = new HashSet<>();

            int eventCount = 0;
            for (JsonNode eventData : arrayNode) {
                if (!eventData.isObject()) {
                    continue;
                }

                JsonNode caseNode = eventData.get(schema.caseIdColumn());
                JsonNode activityNode = eventData.get(schema.activityColumn());
                JsonNode timestampNode = eventData.get(schema.timestampColumn());

                if (caseNode == null || activityNode == null || timestampNode == null) {
                    continue;
                }

                String caseId = caseNode.asText().trim();
                String activity = activityNode.asText().trim();
                String timestamp = timestampNode.asText().trim();

                if (caseId.isEmpty() || activity.isEmpty()) {
                    continue;
                }

                ObjectNode event = objectMapper.createObjectNode();
                event.put("ocel:activity", activity);
                event.put("ocel:timestamp", timestamp);

                ObjectNode omap = objectMapper.createObjectNode();
                ArrayNode caseArray = objectMapper.createArrayNode();
                caseArray.add(caseId);
                omap.set("case", caseArray);
                event.set("ocel:omap", omap);

                event.set("ocel:vmap", objectMapper.createObjectNode());

                eventsNode.set("evt-" + eventCount, event);
                eventCount++;

                if (!seenCases.contains(caseId)) {
                    ObjectNode caseObj = objectMapper.createObjectNode();
                    caseObj.put("ocel:type", "case");
                    ObjectNode caseVmap = objectMapper.createObjectNode();
                    caseVmap.put("case:id", caseId);
                    caseObj.set("ocel:ovmap", caseVmap);
                    objectsNode.set(caseId, caseObj);
                    seenCases.add(caseId);
                }
            }

            root.set("ocel:events", eventsNode);
            root.set("ocel:objects", objectsNode);

            return objectMapper.writeValueAsString(root);
        } catch (PIException e) {
            throw e;
        } catch (Exception e) {
            throw new PIException(
                "JSON conversion failed: " + e.getMessage(),
                "dataprep",
                e
            );
        }
    }

    @Override
    public String formatName() {
        return "json";
    }
}
