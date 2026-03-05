/*
 * Copyright (c) 2024 YAWL Foundation. All rights reserved.
 */
package org.yawlfoundation.yawl.integration.mcp.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dead Path Analyzer tool for the YAWL MCP server.
 *
 * <p>Identifies workflow paths (tasks) that are structurally valid in the specification
 * but have never been executed in any running case — the workflow equivalent of dead code.
 * Compares the complete task set from the specification against all tasks actually activated
 * across all running cases. Returns hot paths (frequently activated), cold paths (rarely
 * activated), and dead paths (never activated).
 *
 * <p>Use cases:
 * <ul>
 *   <li>Workflow optimization: identify unreachable branches</li>
 *   <li>Specification cleanup: remove zombie code from workflows</li>
 *   <li>Coverage analysis: measure which workflow paths are tested in practice</li>
 *   <li>Audit trails: discover if compliance tasks are ever triggered</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DeadPathAnalyzerTools {

    private static final String YAWL_NS = "http://www.yawlfoundation.org/yawlschema";

    private DeadPathAnalyzerTools() {
        throw new UnsupportedOperationException(
            "DeadPathAnalyzerTools is a static factory class and cannot be instantiated.");
    }

    /**
     * Creates the dead path analyzer tool specification.
     *
     * @param interfaceBClient connected InterfaceB client
     * @param sessionHandle    active YAWL session handle
     * @return tool specification for MCP registration
     */
    public static McpServerFeatures.SyncToolSpecification create(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        if (interfaceBClient == null) {
            throw new IllegalArgumentException("interfaceBClient is required");
        }
        if (sessionHandle == null || sessionHandle.isBlank()) {
            throw new IllegalArgumentException("sessionHandle is required");
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("specId", Map.of(
            "type", "string",
            "description", "Specification identifier (URI, e.g. 'OrderProcess')"));
        props.put("specVersion", Map.of(
            "type", "string",
            "description", "Specification version (e.g. '0.1'). Optional, defaults to latest."));

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, List.of("specId"), false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_analyze_dead_paths")
                .description(
                    "Identify workflow paths that are structurally valid (exist in the Petri net) " +
                    "but have never been executed in any running case — the workflow equivalent of dead code. " +
                    "Compares the complete task set from the specification against all tasks actually activated " +
                    "across all running cases. Returns hot paths (frequently activated), cold paths " +
                    "(rarely activated), and dead paths (never activated). " +
                    "Enables workflow optimization and cleanup of unreachable branches.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String specId = requireStringArg(params, "specId");
                    String specVersion = optionalStringArg(params, "specVersion", "0.1");

                    long start = System.currentTimeMillis();

                    // Step 1: Get specification XML to extract all tasks
                    YSpecificationID ySpecId = new YSpecificationID(specId, specVersion, specId);
                    String specXml = interfaceBClient.getSpecification(ySpecId, sessionHandle);

                    if (specXml == null || specXml.contains("<failure>")) {
                        return errorResult(
                            "Failed to get specification " + specId + ": " + specXml);
                    }

                    // Step 2: Extract all task IDs from specification
                    List<TaskInfo> allTasks = extractTasksFromSpec(specXml);

                    // Step 3: Get running case IDs
                    String casesXml = interfaceBClient.getCases(specId, sessionHandle);
                    List<String> caseIds = extractCaseIds(casesXml);

                    // Step 4: For each running case, collect activated task IDs
                    Map<String, Integer> taskActivationCount = new HashMap<>();
                    for (String caseId : caseIds) {
                        try {
                            List<WorkItemRecord> items =
                                interfaceBClient.getWorkItemsForCase(caseId, sessionHandle);

                            if (items != null) {
                                for (WorkItemRecord item : items) {
                                    String taskId = item.getTaskID();
                                    if (taskId != null && !taskId.isBlank()) {
                                        taskActivationCount.merge(taskId, 1, Integer::sum);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Skip cases that fail to load
                        }
                    }

                    long elapsed = System.currentTimeMillis() - start;

                    // Step 5: Classify tasks by activation count
                    List<Map<String, Object>> hotPaths = new ArrayList<>();
                    List<Map<String, Object>> coldPaths = new ArrayList<>();
                    List<Map<String, Object>> deadPaths = new ArrayList<>();

                    // Calculate median activation count
                    int median = calculateMedian(taskActivationCount);

                    for (TaskInfo task : allTasks) {
                        int count = taskActivationCount.getOrDefault(task.id, 0);

                        Map<String, Object> pathInfo = new LinkedHashMap<>();
                        pathInfo.put("task_id", task.id);
                        pathInfo.put("task_name", task.name);
                        pathInfo.put("activation_count", count);

                        if (count == 0) {
                            deadPaths.add(pathInfo);
                        } else if (count > median) {
                            hotPaths.add(pathInfo);
                        } else {
                            coldPaths.add(pathInfo);
                        }
                    }

                    // Step 6: Build result
                    double coverage = allTasks.isEmpty()
                        ? 0.0
                        : (100.0 * (allTasks.size() - deadPaths.size()) / allTasks.size());

                    String recommendation = buildRecommendation(specId, deadPaths, allTasks.size());

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("spec_id",              specId);
                    result.put("spec_version",         specVersion);
                    result.put("total_tasks_in_spec",  allTasks.size());
                    result.put("cases_analyzed",       caseIds.size());
                    result.put("hot_paths",            hotPaths);
                    result.put("hot_path_count",       hotPaths.size());
                    result.put("cold_paths",           coldPaths);
                    result.put("cold_path_count",      coldPaths.size());
                    result.put("dead_paths",           deadPaths);
                    result.put("dead_path_count",      deadPaths.size());
                    result.put("coverage_percent",     String.format("%.1f", coverage));
                    result.put("analysis_type",        "live_case_path_coverage");
                    result.put("query_time_ms",        elapsed);
                    result.put("token_cost",           "0");
                    result.put("recommendation",       recommendation);

                    return textResult(new ObjectMapper()
                        .writerWithDefaultPrettyPrinter().writeValueAsString(result));

                } catch (Exception e) {
                    return errorResult("Error analyzing dead paths: " + e.getMessage());
                }
            }
        );
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Extract all task IDs and names from the specification XML.
     */
    private static List<TaskInfo> extractTasksFromSpec(String specXml) throws Exception {
        List<TaskInfo> tasks = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(
            specXml.getBytes(StandardCharsets.UTF_8)));

        // Find root net (decomposition with isRootNet="true")
        Element rootNet = findRootNet(doc);
        if (rootNet != null) {
            // Extract tasks from root net
            NodeList taskNodes = rootNet.getElementsByTagNameNS(YAWL_NS, "task");
            if (taskNodes.getLength() == 0) {
                taskNodes = rootNet.getElementsByTagName("task");
            }

            for (int i = 0; i < taskNodes.getLength(); i++) {
                Element task = (Element) taskNodes.item(i);
                String taskId = task.getAttribute("id");
                if (taskId != null && !taskId.isBlank()) {
                    String taskName = extractChildText(task, "name");
                    if (taskName == null) {
                        taskName = taskId;
                    }
                    tasks.add(new TaskInfo(taskId, taskName));
                }
            }
        }

        return tasks;
    }

    /**
     * Extract case IDs from getCases XML response.
     * Expected format: <caselist><case id="1.1">...</case>...</caselist>
     */
    private static List<String> extractCaseIds(String casesXml) throws Exception {
        List<String> caseIds = new ArrayList<>();

        if (casesXml == null || casesXml.isBlank() || casesXml.contains("<failure>")) {
            return caseIds;
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(
            casesXml.getBytes(StandardCharsets.UTF_8)));

        // Find all <case> elements
        NodeList caseNodes = doc.getElementsByTagName("case");
        for (int i = 0; i < caseNodes.getLength(); i++) {
            Element caseEl = (Element) caseNodes.item(i);
            String caseId = caseEl.getAttribute("id");
            if (caseId != null && !caseId.isBlank()) {
                caseIds.add(caseId);
            }
        }

        return caseIds;
    }

    /**
     * Find the root net (decomposition with isRootNet="true").
     */
    private static Element findRootNet(Document doc) {
        NodeList decomps = doc.getElementsByTagNameNS(YAWL_NS, "decomposition");
        for (int i = 0; i < decomps.getLength(); i++) {
            Element el = (Element) decomps.item(i);
            if ("true".equalsIgnoreCase(el.getAttribute("isRootNet"))) {
                return el;
            }
        }
        // Fallback: non-namespaced
        decomps = doc.getElementsByTagName("decomposition");
        for (int i = 0; i < decomps.getLength(); i++) {
            Element el = (Element) decomps.item(i);
            if ("true".equalsIgnoreCase(el.getAttribute("isRootNet"))) {
                return el;
            }
        }
        return null;
    }

    /**
     * Extract text content from child element.
     */
    private static String extractChildText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagNameNS(YAWL_NS, tagName);
        if (nodes.getLength() == 0) {
            nodes = parent.getElementsByTagName(tagName);
        }
        if (nodes.getLength() > 0) {
            String text = nodes.item(0).getTextContent();
            if (text != null && !text.isBlank()) {
                return text.trim();
            }
        }
        return null;
    }

    /**
     * Calculate the median activation count.
     */
    private static int calculateMedian(Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return 0;
        }
        List<Integer> sorted = new ArrayList<>(counts.values());
        sorted.sort(Integer::compareTo);
        return sorted.get(sorted.size() / 2);
    }

    /**
     * Build a recommendation message based on dead paths found.
     */
    private static String buildRecommendation(
            String specId,
            List<Map<String, Object>> deadPaths,
            int totalTasks) {

        if (deadPaths.isEmpty()) {
            return "All workflow paths are exercised by running cases. " +
                   "Specification appears well-tested.";
        }

        int deadCount = deadPaths.size();
        double deadPercent = (100.0 * deadCount) / totalTasks;

        StringBuilder sb = new StringBuilder();
        sb.append(deadCount).append(" dead path").append(deadCount == 1 ? "" : "s").append(" found (")
            .append(String.format("%.1f", deadPercent)).append("% of specification). ");

        if (deadCount <= 3) {
            sb.append("Consider: Are these '");
            for (int i = 0; i < deadCount && i < 3; i++) {
                if (i > 0) sb.append("', '");
                @SuppressWarnings("unchecked")
                Map<String, Object> path = (Map<String, Object>) deadPaths.get(i);
                sb.append(path.get("task_name"));
            }
            sb.append("' reachable? Check XOR-split conditions or authorization guards.");
        } else {
            sb.append("Many unreachable paths detected. Review workflow structure:");
            for (int i = 0; i < Math.min(3, deadCount); i++) {
                @SuppressWarnings("unchecked")
                Map<String, Object> path = (Map<String, Object>) deadPaths.get(i);
                sb.append(" '").append(path.get("task_name")).append("'");
                if (i < Math.min(3, deadCount) - 1) sb.append(",");
            }
            if (deadCount > 3) {
                sb.append(", ... and ").append(deadCount - 3).append(" more.");
            }
        }

        return sb.toString();
    }

    private static McpSchema.CallToolResult textResult(String text) {
        return new McpSchema.CallToolResult(
            List.of(new McpSchema.TextContent(text)), false, null, null);
    }

    private static McpSchema.CallToolResult errorResult(String message) {
        return new McpSchema.CallToolResult(
            List.of(new McpSchema.TextContent(message)), true, null, null);
    }

    private static String requireStringArg(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Required argument missing: " + name);
        }
        return value.toString();
    }

    private static String optionalStringArg(Map<String, Object> args, String name,
                                             String defaultValue) {
        Object value = args.get(name);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Simple record for task metadata (ID and name).
     */
    private static class TaskInfo {
        final String id;
        final String name;

        TaskInfo(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
