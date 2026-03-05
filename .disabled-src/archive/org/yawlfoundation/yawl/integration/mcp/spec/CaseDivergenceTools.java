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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Case Divergence Analyzer tool for the YAWL MCP server.
 *
 * <p>Analyzes how running YAWL workflow cases have diverged from each other through
 * cohort analysis. Groups cases by their current active task positions and identifies
 * majority cohorts, outlier cases, and the most divergent XOR/OR split points in the
 * specification. Provides a quantitative divergence index (0.0 = all cases aligned,
 * 1.0 = all cases in unique positions) and human-readable path descriptions for each cohort.
 *
 * <p>Use cases:
 * <ul>
 *   <li>Case monitoring: understand which workflow branches are being taken in production</li>
 *   <li>Anomaly detection: identify outlier cases diverging from majority behavior</li>
 *   <li>XOR-split analysis: discover which decision points cause the most divergence</li>
 *   <li>SLA monitoring: track if cases are progressing through expected task sequences</li>
 *   <li>Workflow optimization: identify bottleneck decision points or rarely-taken paths</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class CaseDivergenceTools {

    private static final String YAWL_NS = "http://www.yawlfoundation.org/yawlschema";

    private CaseDivergenceTools() {
        throw new UnsupportedOperationException(
            "CaseDivergenceTools is a static factory class and cannot be instantiated.");
    }

    /**
     * Creates the case divergence analyzer tool specification.
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
            "description", "Specification version (e.g. '0.1'). Optional, defaults to 0.1."));

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, List.of("specId"), false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_analyze_case_divergence")
                .description(
                    "Analyze how running YAWL workflow cases have diverged from each other through cohort analysis. " +
                    "Groups cases by their current active task positions and identifies majority cohorts, outlier cases, " +
                    "and the most divergent XOR/OR split points in the specification. Provides a quantitative divergence index " +
                    "(0.0 = all cases aligned, 1.0 = all cases in unique positions) and human-readable path descriptions for each cohort. " +
                    "Use cases: case monitoring, anomaly detection, XOR-split analysis, SLA monitoring, workflow optimization.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String specId = requireStringArg(params, "specId");
                    String specVersion = optionalStringArg(params, "specVersion", "0.1");

                    long startMs = System.currentTimeMillis();

                    // Step 1: Get running cases
                    String casesXml = interfaceBClient.getCases(specId, sessionHandle);
                    List<String> caseIds = extractCaseIds(casesXml);

                    if (caseIds.isEmpty()) {
                        Map<String, Object> emptyResult = new LinkedHashMap<>();
                        emptyResult.put("spec_id", specId);
                        emptyResult.put("spec_version", specVersion);
                        emptyResult.put("total_cases", 0);
                        emptyResult.put("unique_positions", 0);
                        emptyResult.put("divergence_index", 0.0);
                        emptyResult.put("message", "No running cases found for specification: " + specId);
                        emptyResult.put("cohorts", List.of());
                        emptyResult.put("outlier_cases", List.of());
                        emptyResult.put("analysis_time_ms", System.currentTimeMillis() - startMs);
                        return textResult(new ObjectMapper()
                            .writerWithDefaultPrettyPrinter().writeValueAsString(emptyResult));
                    }

                    // Step 2: Collect active task positions per case
                    Map<String, TreeSet<String>> casePositions = new LinkedHashMap<>();
                    Map<String, String> taskIdToName = extractTaskNames(
                        specId, specVersion, interfaceBClient, sessionHandle);

                    for (String caseId : caseIds) {
                        try {
                            List<WorkItemRecord> items =
                                interfaceBClient.getWorkItemsForCase(caseId, sessionHandle);

                            if (items != null) {
                                TreeSet<String> activeTasks = items.stream()
                                    .filter(i -> WorkItemRecord.statusEnabled.equals(i.getStatus())
                                              || WorkItemRecord.statusFired.equals(i.getStatus())
                                              || WorkItemRecord.statusExecuting.equals(i.getStatus()))
                                    .map(WorkItemRecord::getTaskID)
                                    .collect(Collectors.toCollection(TreeSet::new));
                                casePositions.put(caseId, activeTasks);
                            }
                        } catch (Exception e) {
                            System.err.println("[CaseDivergenceTools] Skipping case " + caseId
                                + ": " + e.getMessage());
                        }
                    }

                    // Step 3: Group cases into cohorts by position
                    Map<String, List<String>> positionToCases = new LinkedHashMap<>();
                    for (Map.Entry<String, TreeSet<String>> entry : casePositions.entrySet()) {
                        String caseId = entry.getKey();
                        String positionKey = entry.getValue().toString();
                        positionToCases.computeIfAbsent(positionKey, k -> new ArrayList<>())
                            .add(caseId);
                    }

                    int totalCases = casePositions.size();
                    int uniquePositions = positionToCases.size();
                    double divergenceIndex = totalCases <= 1 ? 0.0 :
                        (double)(uniquePositions - 1) / (double)(totalCases - 1);

                    String divergenceInterpretation;
                    if (divergenceIndex < 0.25) {
                        divergenceInterpretation =
                            "LOW — most cases are in similar positions";
                    } else if (divergenceIndex < 0.60) {
                        divergenceInterpretation =
                            "MODERATE — cases are diverging across multiple paths";
                    } else {
                        divergenceInterpretation =
                            "HIGH — cases are highly dispersed; investigate XOR split decisions";
                    }

                    // Step 4: Identify majority cohort and outliers
                    String majorityPositionKey = positionToCases.entrySet().stream()
                        .max(Comparator.comparingInt(e -> e.getValue().size()))
                        .map(Map.Entry::getKey)
                        .orElse("");

                    int majorityCohortSize = positionToCases.getOrDefault(
                        majorityPositionKey, List.of()).size();

                    List<String> outlierCases = positionToCases.entrySet().stream()
                        .filter(e -> !e.getKey().equals(majorityPositionKey))
                        .flatMap(e -> e.getValue().stream())
                        .sorted()
                        .collect(Collectors.toList());

                    // Step 5: Build cohort descriptions
                    List<Map<String, Object>> cohorts = new ArrayList<>();
                    List<Map.Entry<String, List<String>>> sortedCohorts =
                        new ArrayList<>(positionToCases.entrySet());
                    sortedCohorts.sort((a, b) -> b.getValue().size() - a.getValue().size());

                    for (Map.Entry<String, List<String>> entry : sortedCohorts) {
                        String posKey = entry.getKey();
                        List<String> cases = entry.getValue();

                        TreeSet<String> taskIds = casePositions.getOrDefault(
                            cases.get(0), new TreeSet<>());

                        String pathDescription;
                        if (taskIds.isEmpty()) {
                            pathDescription =
                                "No active tasks (case may be suspended or at a data-driven hold)";
                        } else {
                            String taskList = taskIds.stream()
                                .map(id -> taskIdToName.getOrDefault(id, id))
                                .collect(Collectors.joining(", "));
                            pathDescription = "Active at: [" + taskList + "]";
                        }

                        Map<String, Object> cohort = new LinkedHashMap<>();
                        cohort.put("position_key", posKey);
                        cohort.put("case_count", cases.size());
                        cohort.put("case_ids", cases);
                        cohort.put("path_description", pathDescription);
                        cohort.put("is_majority", posKey.equals(majorityPositionKey));
                        cohorts.add(cohort);
                    }

                    // Step 6: Find most divergent XOR/OR split
                    String mostDivergentSplit = null;
                    try {
                        YSpecificationID ySpecId =
                            new YSpecificationID(specId, specVersion, specId);
                        String specXml = interfaceBClient.getSpecification(ySpecId, sessionHandle);
                        Document doc = parseXml(specXml);
                        Element rootNet = findRootNet(doc);

                        if (rootNet != null) {
                            Map<String, List<String>> outEdges = new HashMap<>();
                            NodeList allTasks = rootNet.getElementsByTagNameNS(YAWL_NS, "task");
                            if (allTasks.getLength() == 0) {
                                allTasks = rootNet.getElementsByTagName("task");
                            }

                            for (int i = 0; i < allTasks.getLength(); i++) {
                                Element task = (Element) allTasks.item(i);
                                String taskId = task.getAttribute("id");
                                String splitCode = "and";

                                NodeList splitEl = task.getElementsByTagNameNS(
                                    YAWL_NS, "splitType");
                                if (splitEl.getLength() == 0) {
                                    splitEl = task.getElementsByTagName("splitType");
                                }
                                if (splitEl.getLength() > 0) {
                                    NodeList codeNodes = ((Element) splitEl.item(0))
                                        .getElementsByTagName("code");
                                    if (codeNodes.getLength() > 0) {
                                        splitCode = codeNodes.item(0)
                                            .getTextContent().trim();
                                    }
                                }

                                if (!"xor".equalsIgnoreCase(splitCode)
                                    && !"or".equalsIgnoreCase(splitCode)) {
                                    continue;
                                }

                                List<String> outputs = new ArrayList<>();
                                NodeList flows = task.getElementsByTagNameNS(
                                    YAWL_NS, "flowsInto");
                                if (flows.getLength() == 0) {
                                    flows = task.getElementsByTagName("flowsInto");
                                }
                                for (int f = 0; f < flows.getLength(); f++) {
                                    Element flow = (Element) flows.item(f);
                                    NodeList refs = flow.getElementsByTagName("nextElementRef");
                                    for (int r = 0; r < refs.getLength(); r++) {
                                        String refId = ((Element) refs.item(r))
                                            .getAttribute("id");
                                        if (refId != null && !refId.isBlank()) {
                                            outputs.add(refId);
                                        }
                                    }
                                }

                                if (!outputs.isEmpty()) {
                                    outEdges.put(taskId, outputs);
                                }
                            }

                            int maxScore = 0;
                            for (Map.Entry<String, List<String>> entry2 : outEdges.entrySet()) {
                                String splitTaskId = entry2.getKey();
                                Set<String> outputTasks = new HashSet<>(entry2.getValue());
                                int cohortsSpanned = 0;

                                for (String posKey : positionToCases.keySet()) {
                                    boolean anyActive = outputTasks.stream()
                                        .anyMatch(posKey::contains);
                                    if (anyActive) {
                                        cohortsSpanned++;
                                    }
                                }

                                if (cohortsSpanned > maxScore) {
                                    maxScore = cohortsSpanned;
                                    mostDivergentSplit = taskIdToName.getOrDefault(
                                        splitTaskId, splitTaskId);
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[CaseDivergenceTools] Could not analyze splits: "
                            + e.getMessage());
                    }

                    // Step 7: Build final JSON result
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("spec_id", specId);
                    result.put("spec_version", specVersion);
                    result.put("total_cases", totalCases);
                    result.put("unique_positions", uniquePositions);
                    result.put("divergence_index",
                        Math.round(divergenceIndex * 1000.0) / 1000.0);
                    result.put("divergence_interpretation", divergenceInterpretation);
                    result.put("cohorts", cohorts);
                    result.put("outlier_cases", outlierCases);
                    if (mostDivergentSplit != null) {
                        result.put("most_divergent_split", mostDivergentSplit);
                    }
                    result.put("majority_cohort_size", majorityCohortSize);
                    result.put("analysis_time_ms", System.currentTimeMillis() - startMs);

                    return textResult(new ObjectMapper()
                        .writerWithDefaultPrettyPrinter().writeValueAsString(result));

                } catch (Exception e) {
                    return errorResult("Error analyzing case divergence: " + e.getMessage());
                }
            }
        );
    }

    // =========================================================================
    // Internal helpers (exact copies from reference files)
    // =========================================================================

    /**
     * Extract task names from specification XML for human-readable descriptions.
     */
    private static Map<String, String> extractTaskNames(String specId, String specVersion,
            InterfaceB_EnvironmentBasedClient client, String sessionHandle) {
        Map<String, String> names = new HashMap<>();
        try {
            YSpecificationID ySpecId = new YSpecificationID(specId, specVersion, specId);
            String xml = client.getSpecification(ySpecId, sessionHandle);
            Document doc = parseXml(xml);
            Element rootNet = findRootNet(doc);
            if (rootNet == null) {
                return names;
            }
            NodeList tasks = rootNet.getElementsByTagNameNS(YAWL_NS, "task");
            if (tasks.getLength() == 0) {
                tasks = rootNet.getElementsByTagName("task");
            }
            for (int i = 0; i < tasks.getLength(); i++) {
                Element t = (Element) tasks.item(i);
                String id = t.getAttribute("id");
                String name = extractChildText(t, "name");
                if (name != null && !name.isBlank()) {
                    names.put(id, name);
                }
            }
        } catch (Exception e) {
            System.err.println("[CaseDivergenceTools] Could not load task names: "
                + e.getMessage());
        }
        return names;
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
     * Parse XML string into a DOM Document.
     */
    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
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
     * Create a successful text result for MCP.
     */
    private static McpSchema.CallToolResult textResult(String text) {
        return new McpSchema.CallToolResult(
            List.of(new McpSchema.TextContent(text)), false, null, null);
    }

    /**
     * Create an error result for MCP.
     */
    private static McpSchema.CallToolResult errorResult(String message) {
        return new McpSchema.CallToolResult(
            List.of(new McpSchema.TextContent(message)), true, null, null);
    }

    /**
     * Extract required string argument from parameters.
     */
    private static String requireStringArg(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Required argument missing: " + name);
        }
        return value.toString();
    }

    /**
     * Extract optional string argument with default fallback.
     */
    private static String optionalStringArg(Map<String, Object> args, String name,
                                             String defaultValue) {
        Object value = args.get(name);
        return value != null ? value.toString() : defaultValue;
    }
}
