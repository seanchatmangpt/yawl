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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.pi.PIException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Engine for inferring OCEL2 schema from raw event log samples using Z.AI.
 *
 * <p>Attempts AI-based schema inference first, then falls back to heuristics
 * if Z.AI is unavailable or inference fails. Identifies case ID, activity,
 * timestamp, and auxiliary columns/fields.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public final class SchemaInferenceEngine {

    private static final Logger LOG = LogManager.getLogger(SchemaInferenceEngine.class);
    private final ZaiService zaiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Create schema inference engine with optional Z.AI service.
     *
     * @param zaiService Z.AI service (may be null if API key unavailable)
     */
    public SchemaInferenceEngine(ZaiService zaiService) {
        this.zaiService = zaiService;
    }

    /**
     * Infer OCEL2 schema from raw data sample.
     *
     * <p>Attempts AI inference via Z.AI if available, falls back to heuristics.</p>
     *
     * @param rawSample raw data sample
     * @param formatHint hint about data format (csv, json, xml)
     * @return inferred OCEL2 schema
     * @throws PIException if inference fails
     */
    public OcedSchema infer(String rawSample, String formatHint) throws PIException {
        if (zaiService != null) {
            try {
                return inferWithAi(rawSample, formatHint);
            } catch (Exception e) {
                LOG.warn("AI schema inference failed, falling back to heuristics", e);
                return inferWithHeuristics(rawSample, formatHint);
            }
        }
        return inferWithHeuristics(rawSample, formatHint);
    }

    private OcedSchema inferWithAi(String rawSample, String formatHint) throws PIException {
        String prompt = String.format(
            """
            You are an OCEL2 schema expert. Analyze this %s data sample and respond ONLY \
            with valid JSON identifying: caseIdColumn, activityColumn, timestampColumn, \
            objectTypeColumns (array), attributeColumns (array). Do not include any other text.

            Data sample:
            %s""",
            formatHint, rawSample
        );

        try {
            String response = zaiService.chat(prompt);
            JsonNode parsed = objectMapper.readTree(response);
            return buildSchemaFromJson(parsed, formatHint, true);
        } catch (Exception e) {
            throw new PIException(
                "AI schema inference failed: " + e.getMessage(),
                "dataprep",
                e
            );
        }
    }

    private OcedSchema inferWithHeuristics(String rawSample, String formatHint)
            throws PIException {
        String[] lines = rawSample.split("\n", 10);
        if (lines.length == 0) {
            throw new PIException("Empty sample data", "dataprep");
        }

        String header = lines[0];
        String[] columns = header.split(",|\\s+|\\|");

        String caseIdCol = findColumn(columns, "case", "id", "caseId");
        String activityCol = findColumn(columns, "activity", "task", "event", "action");
        String timestampCol = findColumn(columns, "time", "date", "ts", "timestamp");

        if (caseIdCol == null || activityCol == null || timestampCol == null) {
            throw new PIException(
                String.format(
                    "Cannot infer schema: found case=%s, activity=%s, timestamp=%s",
                    caseIdCol, activityCol, timestampCol
                ),
                "dataprep"
            );
        }

        List<String> objectTypes = new ArrayList<>();
        objectTypes.add("case");
        List<String> attributes = new ArrayList<>();

        return new OcedSchema(
            "schema-" + UUID.randomUUID().toString().substring(0, 8),
            caseIdCol,
            activityCol,
            timestampCol,
            objectTypes,
            attributes,
            formatHint,
            false,
            Instant.now()
        );
    }

    private OcedSchema buildSchemaFromJson(JsonNode node, String format, boolean aiInferred)
            throws PIException {
        try {
            String caseIdColumn = node.get("caseIdColumn").asText();
            String activityColumn = node.get("activityColumn").asText();
            String timestampColumn = node.get("timestampColumn").asText();

            List<String> objectTypeColumns = new ArrayList<>();
            JsonNode objectTypes = node.get("objectTypeColumns");
            if (objectTypes != null && objectTypes.isArray()) {
                objectTypes.forEach(ot -> objectTypeColumns.add(ot.asText()));
            }

            List<String> attributeColumns = new ArrayList<>();
            JsonNode attributes = node.get("attributeColumns");
            if (attributes != null && attributes.isArray()) {
                attributes.forEach(attr -> attributeColumns.add(attr.asText()));
            }

            return new OcedSchema(
                "schema-" + UUID.randomUUID().toString().substring(0, 8),
                caseIdColumn,
                activityColumn,
                timestampColumn,
                objectTypeColumns,
                attributeColumns,
                format,
                aiInferred,
                Instant.now()
            );
        } catch (Exception e) {
            throw new PIException(
                "Failed to parse AI response as JSON: " + e.getMessage(),
                "dataprep",
                e
            );
        }
    }

    private String findColumn(String[] columns, String... candidates) {
        for (String candidate : candidates) {
            for (String column : columns) {
                String normalized = column.toLowerCase().trim();
                if (normalized.contains(candidate.toLowerCase()) ||
                    normalized.equals(candidate.toLowerCase())) {
                    return column.trim();
                }
            }
        }
        return null;
    }
}
