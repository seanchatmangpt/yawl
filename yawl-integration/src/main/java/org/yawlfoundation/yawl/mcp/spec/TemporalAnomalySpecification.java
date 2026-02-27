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

package org.yawlfoundation.yawl.integration.mcp.spec;

import java.util.*;
import java.util.regex.Pattern;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.anomaly.TemporalAnomalySentinel;
import org.yawlfoundation.yawl.util.JDOMUtil;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool specification for the Temporal Anomaly Sentinel.
 *
 * Creates a single MCP tool: yawl_temporal_anomaly_sentinel
 *
 * Analyzes all running cases and work items to detect temporal anomalies
 * that suggest SLA violations or resource bottlenecks. Returns a formatted
 * report with risk scores and actionable recommendations.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class TemporalAnomalySpecification {

    private TemporalAnomalySpecification() {
        throw new UnsupportedOperationException(
            "TemporalAnomalySpecification is a static factory class and cannot be instantiated.");
    }

    /**
     * Creates the Temporal Anomaly Sentinel MCP tool specification.
     *
     * @param interfaceBClient the YAWL InterfaceB client
     * @param sessionHandle the active YAWL session handle
     * @return MCP tool specification for temporal anomaly detection
     */
    public static McpServerFeatures.SyncToolSpecification createTemporalAnomalySentinelTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        if (interfaceBClient == null) {
            throw new IllegalArgumentException(
                "interfaceBClient is required");
        }
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            throw new IllegalArgumentException(
                "sessionHandle is required");
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("defaultTimeoutMinutes", Map.of(
            "type", "number",
            "description", "Default expected duration (minutes) for unknown tasks. " +
                "Used when insufficient historical data exists. Default: 60"));

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, List.of(), false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_temporal_anomaly_sentinel")
                .description("Analyze all running cases and work items to detect temporal anomalies. " +
                    "Identifies tasks dramatically over their expected time, stalled cases, and SLA violations " +
                    "before they happen. Uses cross-case comparison to establish baselines.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();

                    Number timeoutNum = (Number) params.get("defaultTimeoutMinutes");
                    double defaultTimeoutMinutes = timeoutNum != null ?
                        timeoutNum.doubleValue() : 60.0;

                    // Validate parameter
                    if (defaultTimeoutMinutes <= 0) {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Error: defaultTimeoutMinutes must be positive. Received: " +
                                defaultTimeoutMinutes)),
                            true, null, null);
                    }

                    // Get all running cases
                    String casesXml = interfaceBClient.getAllRunningCases(sessionHandle);
                    int totalCases = countRunningCases(casesXml);

                    // Get all work items for each running case
                    List<WorkItemRecord> allWorkItems = new ArrayList<>();
                    List<String> caseIds = extractCaseIds(casesXml);

                    for (String caseId : caseIds) {
                        try {
                            List<WorkItemRecord> caseItems =
                                interfaceBClient.getWorkItemsForCase(caseId, sessionHandle);
                            if (caseItems != null) {
                                allWorkItems.addAll(caseItems);
                            }
                        } catch (Exception e) {
                            // Continue with other cases if one fails
                        }
                    }

                    // Detect anomalies
                    List<TemporalAnomalySentinel.AnomalyRecord> anomalies =
                        TemporalAnomalySentinel.detect(allWorkItems, defaultTimeoutMinutes);

                    // Generate report
                    String report = TemporalAnomalySentinel.generateReport(anomalies, totalCases);

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(report)),
                        false, null, null);

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Error analyzing temporal anomalies: " + e.getMessage())),
                        true, null, null);
                }
            }
        );
    }

    // ===== XML Parsing Helpers =====

    /**
     * Counts the number of running cases from getAllRunningCases XML.
     *
     * @param casesXml XML response from getAllRunningCases
     * @return number of running cases
     */
    private static int countRunningCases(String casesXml) {
        if (casesXml == null || casesXml.isEmpty()) {
            return 0;
        }

        try {
            Document doc = JDOMUtil.stringToDocument(casesXml);
            if (doc == null) {
                return 0;
            }

            Element root = doc.getRootElement();
            if (root == null) {
                return 0;
            }

            // Count <case> or <caseID> elements
            List<Element> caseElements = root.getChildren("case");
            if (caseElements.isEmpty()) {
                caseElements = root.getChildren("caseID");
            }

            return caseElements.size();
        } catch (Exception e) {
            // Fallback: count by string matching
            Pattern pattern = Pattern.compile("<case>|<caseID>");
            return (int) pattern.matcher(casesXml).results().count();
        }
    }

    /**
     * Extracts case IDs from getAllRunningCases XML.
     *
     * @param casesXml XML response from getAllRunningCases
     * @return list of case IDs
     */
    private static List<String> extractCaseIds(String casesXml) {
        List<String> caseIds = new ArrayList<>();

        if (casesXml == null || casesXml.isEmpty()) {
            return caseIds;
        }

        try {
            Document doc = JDOMUtil.stringToDocument(casesXml);
            if (doc == null) {
                return caseIds;
            }

            Element root = doc.getRootElement();
            if (root == null) {
                return caseIds;
            }

            // Try to extract from <case> elements
            List<Element> caseElements = root.getChildren("case");
            for (Element caseElem : caseElements) {
                Element idElem = caseElem.getChild("caseID");
                if (idElem != null && idElem.getText() != null) {
                    caseIds.add(idElem.getText());
                } else {
                    // Try direct text if no child element
                    String text = caseElem.getText();
                    if (text != null && !text.isEmpty()) {
                        caseIds.add(text);
                    }
                }
            }

            // If no cases found, try direct caseID elements
            if (caseIds.isEmpty()) {
                List<Element> idElements = root.getChildren("caseID");
                for (Element idElem : idElements) {
                    String text = idElem.getText();
                    if (text != null && !text.isEmpty()) {
                        caseIds.add(text);
                    }
                }
            }

            return caseIds;
        } catch (Exception e) {
            // Fallback: regex extraction
            Pattern pattern = Pattern.compile("<caseID>([^<]+)</caseID>");
            java.util.regex.Matcher matcher = pattern.matcher(casesXml);
            while (matcher.find()) {
                caseIds.add(matcher.group(1));
            }
            return caseIds;
        }
    }
}
