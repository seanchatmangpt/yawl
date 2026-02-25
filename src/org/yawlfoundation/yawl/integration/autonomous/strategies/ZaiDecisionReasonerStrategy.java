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

package org.yawlfoundation.yawl.integration.autonomous.strategies;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Decision reasoner using Z.AI for intelligent output generation.
 *
 * <p>Submits a work item to Z.AI with a configurable prompt template and
 * converts the JSON response into XML output data for work item completion.
 *
 * <p>Prompt substitutions:
 * <ul>
 *   <li>{@code {taskName}} → work item's task name</li>
 *   <li>{@code {taskDescription}} → work item's task name (no separate description field)</li>
 *   <li>{@code {inputData}} → work item's input data as string</li>
 * </ul>
 *
 * <p>Expected JSON response format:
 * <pre>
 * {
 *   "field1": "value1",
 *   "field2": "value2",
 *   ...
 * }
 * </pre>
 *
 * <p>JSON fields are converted to XML:
 * <pre>
 * {@code <outputData>
 *   <field1>value1</field1>
 *   <field2>value2</field2>
 * </outputData>}
 * </pre>
 *
 * If JSON parsing fails, the raw response is wrapped in:
 * <pre>
 * {@code <outputData><raw>response</raw></outputData>}
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since YAWL 6.0
 */
public final class ZaiDecisionReasonerStrategy implements DecisionReasoner {

    private static final Logger logger = LogManager.getLogger(ZaiDecisionReasonerStrategy.class);

    private final ZaiService zaiService;
    private final String promptTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Create a Z.AI-based decision reasoner.
     *
     * @param zaiService the Z.AI service for making API calls
     * @param promptTemplate the prompt template with {taskName}, {taskDescription}, {inputData} placeholders
     */
    public ZaiDecisionReasonerStrategy(ZaiService zaiService, String promptTemplate) {
        if (zaiService == null) {
            throw new IllegalArgumentException("ZaiService is required");
        }
        if (promptTemplate == null || promptTemplate.isBlank()) {
            throw new IllegalArgumentException("promptTemplate is required");
        }
        this.zaiService = zaiService;
        this.promptTemplate = promptTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Produce output for a work item using Z.AI reasoning.
     *
     * @param workItem the work item to process
     * @return XML-formatted output data
     */
    @Override
    public String produceOutput(WorkItemRecord workItem) {
        if (workItem == null) {
            logger.error("Work item is null");
            return wrapInRawXml("ERROR: Work item is null");
        }

        try {
            // Substitute placeholders in prompt
            String taskName = workItem.getTaskName();
            String inputData = workItem.getDataListString();

            String prompt = promptTemplate
                .replace("{taskName}", taskName != null ? taskName : "")
                .replace("{taskDescription}", taskName != null ? taskName : "")
                .replace("{inputData}", inputData != null ? inputData : "");

            // Call Z.AI with the prompt
            String response = zaiService.chat(prompt, "GLM-4.7-Flash");

            // Attempt to parse as JSON and convert to XML
            return jsonToXmlOutput(response, workItem.getID());

        } catch (Exception e) {
            logger.error("Z.AI decision reasoning failed for work item {}: {}",
                workItem.getID(), e.getMessage(), e);
            return wrapInRawXml("ERROR: " + e.getMessage());
        }
    }

    /**
     * Convert JSON response to XML output format.
     *
     * @param jsonResponse the JSON response from Z.AI
     * @param workItemId the work item ID (for logging)
     * @return XML output data, or raw-wrapped response if parsing fails
     */
    private String jsonToXmlOutput(String jsonResponse, String workItemId) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);

            StringBuilder xmlBuilder = new StringBuilder();
            xmlBuilder.append("<outputData>");

            // Convert each JSON field to XML element
            jsonNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode valueNode = entry.getValue();

                // Escape XML special characters in key and value
                String safeKey = escapeXml(key);
                String safeValue = valueNode.isTextual()
                    ? escapeXml(valueNode.asText())
                    : escapeXml(valueNode.toString());

                xmlBuilder.append("<").append(safeKey).append(">")
                    .append(safeValue)
                    .append("</").append(safeKey).append(">");
            });

            xmlBuilder.append("</outputData>");

            String xml = xmlBuilder.toString();
            logger.debug("Generated XML output for work item {}: {}",
                workItemId, xml);

            return xml;

        } catch (Exception e) {
            logger.warn("Failed to parse JSON response for work item {}, wrapping as raw: {}",
                workItemId, e.getMessage());
            return wrapInRawXml(jsonResponse);
        }
    }

    /**
     * Wrap raw response in XML output element.
     *
     * @param rawContent the raw content to wrap
     * @return XML-wrapped content
     */
    private String wrapInRawXml(String rawContent) {
        String escaped = escapeXml(rawContent);
        return "<outputData><raw>" + escaped + "</raw></outputData>";
    }

    /**
     * Escape XML special characters.
     *
     * @param text the text to escape
     * @return XML-safe text
     */
    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
