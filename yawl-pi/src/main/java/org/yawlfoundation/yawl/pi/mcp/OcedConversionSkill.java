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

package org.yawlfoundation.yawl.pi.mcp;

import org.yawlfoundation.yawl.integration.a2a.skills.A2ASkill;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillRequest;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillResult;
import org.yawlfoundation.yawl.pi.PIException;
import org.yawlfoundation.yawl.pi.bridge.OcedBridge;
import org.yawlfoundation.yawl.pi.bridge.OcedBridgeFactory;
import org.yawlfoundation.yawl.pi.bridge.OcedSchema;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A2A skill for converting raw enterprise event data to OCEL 2.0 — no LLM required.
 *
 * <p>Implements van der Aalst's Process Intelligence connection 5 (data preparation):
 * converts CSV, JSON, or XML event logs into OCEL 2.0 (Object-Centric Event Log,
 * IEEE 2023 standard) format via heuristic schema inference.</p>
 *
 * <h2>Parameters</h2>
 * <ul>
 *   <li>{@code eventData} — required; raw CSV, JSON, or XML/XES event data (max 10MB)</li>
 *   <li>{@code format} — optional; 'csv', 'json', or 'xml' (auto-detected if omitted)</li>
 * </ul>
 *
 * <h2>Result data keys</h2>
 * <ul>
 *   <li>{@code ocel2Json} — OCEL 2.0 JSON string</li>
 *   <li>{@code detectedFormat} — 'CSV', 'JSON', or 'XML'</li>
 *   <li>{@code caseIdColumn} — inferred case ID column name</li>
 *   <li>{@code activityColumn} — inferred activity column name</li>
 *   <li>{@code timestampColumn} — inferred timestamp column name</li>
 *   <li>{@code aiInferred} — whether AI-assisted schema inference was used</li>
 *   <li>{@code elapsed_ms} — wall-clock conversion time</li>
 * </ul>
 *
 * @see OcedBridgeFactory
 * @see YawlOcedBridgeToolSpecifications
 * @since YAWL 6.0
 */
public class OcedConversionSkill implements A2ASkill {

    private static final int MAX_DATA_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

    private void validateDataSize(String data, String paramName) {
        if (data != null && data.getBytes(StandardCharsets.UTF_8).length > MAX_DATA_SIZE_BYTES) {
            throw new IllegalArgumentException(
                paramName + " exceeds maximum size of " + (MAX_DATA_SIZE_BYTES / 1024 / 1024) + "MB"
            );
        }
    }

    @Override
    public String getId() {
        return "oced_to_ocel";
    }

    @Override
    public String getName() {
        return "OCED to OCEL 2.0 Conversion";
    }

    @Override
    public String getDescription() {
        return "Convert raw enterprise event log data (CSV, JSON, or XML/XES) to OCEL 2.0 "
            + "(Object-Centric Event Log, IEEE 2023 standard) for process mining. "
            + "Auto-detects format and infers schema via keyword heuristics — no LLM required. "
            + "Output is interoperable with PM4Py, ocpa, PM4JS, and Celonis Process Sphere™.";
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("workflow:data");
    }

    @Override
    public List<String> getTags() {
        return List.of("ocel", "ocel2", "ocpm", "data-prep", "no-llm", "format-conversion",
            "process-mining", "csv", "json", "xml");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        if (request == null) {
            return SkillResult.error("Request cannot be null");
        }

        String eventData = request.getParameter("eventData");
        if (eventData == null || eventData.isBlank()) {
            return SkillResult.error(
                "Parameter 'eventData' is required. "
                + "Provide raw CSV rows, a JSON array of event objects, or XES/XML event log data.");
        }

        validateDataSize(eventData, "eventData");

        String format = request.getParameter("format");
        long start = System.currentTimeMillis();

        try {
            OcedBridge bridge;
            if (format == null || format.isBlank() || format.equalsIgnoreCase("auto")) {
                bridge = OcedBridgeFactory.autoDetect(eventData);
            } else {
                bridge = OcedBridgeFactory.forFormat(format);
            }

            OcedSchema schema = bridge.inferSchema(eventData);
            String ocel2Json = bridge.convert(eventData, schema);
            long elapsed = System.currentTimeMillis() - start;

            Map<String, Object> data = new HashMap<>();
            data.put("ocel2Json", ocel2Json);
            data.put("detectedFormat", bridge.formatName().toUpperCase());
            data.put("caseIdColumn", schema.caseIdColumn());
            data.put("activityColumn", schema.activityColumn());
            data.put("timestampColumn", schema.timestampColumn());
            data.put("objectTypeColumns", schema.objectTypeColumns());
            data.put("attributeColumns", schema.attributeColumns());
            data.put("aiInferred", schema.aiInferred());
            data.put("elapsed_ms", elapsed);

            return SkillResult.success(data, elapsed);

        } catch (PIException e) {
            return SkillResult.error("OCEL conversion failed: " + e.getMessage());
        } catch (Exception e) {
            return SkillResult.error("Unexpected error during OCEL conversion: " + e.getMessage());
        }
    }
}
