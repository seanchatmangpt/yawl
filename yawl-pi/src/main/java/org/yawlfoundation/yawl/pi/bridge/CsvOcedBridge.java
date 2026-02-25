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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.yawlfoundation.yawl.pi.PIException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * OCEL2 bridge for CSV (comma-separated values) event logs.
 *
 * <p>Parses CSV with header row and converts to OCEL2 v2.0 JSON format.
 * Each row becomes an event; unique case IDs become objects.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public final class CsvOcedBridge implements OcedBridge {

    private final SchemaInferenceEngine schemaInferenceEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Create CSV OCEL2 bridge.
     *
     * @param schemaInferenceEngine engine for inferring column roles
     */
    public CsvOcedBridge(SchemaInferenceEngine schemaInferenceEngine) {
        if (schemaInferenceEngine == null) {
            throw new IllegalArgumentException("schemaInferenceEngine is required");
        }
        this.schemaInferenceEngine = schemaInferenceEngine;
    }

    @Override
    public OcedSchema inferSchema(String rawSample) throws PIException {
        return schemaInferenceEngine.infer(rawSample, "csv");
    }

    @Override
    public String convert(String rawData, OcedSchema schema) throws PIException {
        try {
            String[] lines = rawData.split("\n");
            if (lines.length < 2) {
                throw new PIException(
                    "CSV requires header row and at least one data row",
                    "dataprep"
                );
            }

            String[] headers = parseCsvLine(lines[0]);
            Map<String, Integer> columnIndex = buildColumnIndex(headers);
            validateSchemaColumns(schema, columnIndex);

            int caseIdIdx = columnIndex.get(schema.caseIdColumn());
            int activityIdx = columnIndex.get(schema.activityColumn());
            int timestampIdx = columnIndex.get(schema.timestampColumn());

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

            for (int i = 1; i < lines.length; i++) {
                String[] values = parseCsvLine(lines[i]);
                if (values.length <= Math.max(caseIdIdx, Math.max(activityIdx, timestampIdx))) {
                    continue;
                }

                String caseId = values[caseIdIdx].trim();
                String activity = values[activityIdx].trim();
                String timestamp = values[timestampIdx].trim();

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

                eventsNode.set("evt-" + i, event);

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
                "CSV conversion failed: " + e.getMessage(),
                "dataprep",
                e
            );
        }
    }

    @Override
    public String formatName() {
        return "csv";
    }

    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(ch);
            }
        }
        result.add(current.toString());

        return result.toArray(new String[0]);
    }

    private Map<String, Integer> buildColumnIndex(String[] headers) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            index.put(headers[i].trim(), i);
        }
        return index;
    }

    private void validateSchemaColumns(OcedSchema schema, Map<String, Integer> columnIndex)
            throws PIException {
        if (!columnIndex.containsKey(schema.caseIdColumn())) {
            throw new PIException(
                "Case ID column not found: " + schema.caseIdColumn(),
                "dataprep"
            );
        }
        if (!columnIndex.containsKey(schema.activityColumn())) {
            throw new PIException(
                "Activity column not found: " + schema.activityColumn(),
                "dataprep"
            );
        }
        if (!columnIndex.containsKey(schema.timestampColumn())) {
            throw new PIException(
                "Timestamp column not found: " + schema.timestampColumn(),
                "dataprep"
            );
        }
    }
}
