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
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cancellation Auditor tool for the YAWL MCP server.
 *
 * <p>Audits YAWL workflow cancellation regions for correctness issues.
 * Analyzes task cancellation semantics (removesTokens, removesTokensFromFlow)
 * and detects structural problems: mutual cancellation, self-cancellation,
 * large blast radius, and orphan cancellations.
 *
 * <p>Use cases:
 * <ul>
 *   <li>Cancellation safety: identify mutual cancellation deadlock risks</li>
 *   <li>Blast radius analysis: flag tasks that cancel too many elements</li>
 *   <li>Reachability verification: detect unreachable cancellation targets</li>
 *   <li>Live impact assessment: measure cancellation impact on running cases</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class CancellationAuditorTools {

    private static final String YAWL_NS = "http://www.yawlfoundation.org/yawlschema";

    private CancellationAuditorTools() {
        throw new UnsupportedOperationException(
            "CancellationAuditorTools is a static factory class and cannot be instantiated.");
    }

    /**
     * Creates the cancellation auditor tool specification.
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
        props.put("caseId", Map.of(
            "type", "string",
            "description", "Optional case ID to analyze live blast radius for running items"));
        props.put("blastRadiusThreshold", Map.of(
            "type", "number",
            "description", "Flag regions with more cancelled elements than this threshold (default 3)"));

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, List.of("specId"), false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_audit_cancellation_regions")
                .description(
                    "Audit YAWL workflow cancellation regions for correctness issues. " +
                    "Analyzes task cancellation semantics (removesTokens, removesTokensFromFlow) " +
                    "and detects structural problems: mutual cancellation (race condition risk), " +
                    "self-cancellation (known deadlock risk), large blast radius (tasks cancelling " +
                    "many elements), and orphan cancellations (cancelled elements not reachable " +
                    "from cancelling task). Optionally analyzes live blast radius for running cases " +
                    "to show which cancellations would impact active work items.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String specId = requireStringArg(params, "specId");
                    String specVersion = optionalStringArg(params, "specVersion", "0.1");
                    String caseId = optionalStringArg(params, "caseId", null);
                    int blastRadiusThreshold = ((Number) params.getOrDefault("blastRadiusThreshold", 3))
                        .intValue();

                    long startMs = System.currentTimeMillis();

                    // Step 1: Load spec and find root net
                    YSpecificationID ySpecId = new YSpecificationID(specId, specVersion, specId);
                    String specXml = interfaceBClient.getSpecification(ySpecId, sessionHandle);

                    if (specXml == null || specXml.contains("<failure>")) {
                        return errorResult("Failed to get specification " + specId + ": " + specXml);
                    }

                    Document doc = parseXml(specXml);
                    Element rootNet = findRootNet(doc);
                    if (rootNet == null) {
                        return errorResult("No root net found: " + specId);
                    }

                    // Step 2: Extract cancellation sets from XML
                    Map<String, Set<String>> cancellationMap = extractCancellationSets(rootNet);

                    // Step 3: Build flow graph for reachability analysis
                    Map<String, List<String>> outEdges = new HashMap<>();
                    extractFlowEdges(rootNet, outEdges);

                    // Step 4: Structural issue detection
                    List<Map<String, Object>> issues = detectStructuralIssues(
                        cancellationMap, outEdges, blastRadiusThreshold);

                    // Step 5: Live blast radius (if caseId given)
                    Map<String, List<String>> liveBlastRadius = new LinkedHashMap<>();
                    if (caseId != null && !caseId.isBlank()) {
                        liveBlastRadius = analyzeLiveBlastRadius(
                            interfaceBClient, sessionHandle, caseId, cancellationMap);
                    }

                    long elapsedMs = System.currentTimeMillis() - startMs;

                    // Step 6: Build JSON result
                    Map<String, List<String>> cancellationMapJson = new LinkedHashMap<>();
                    cancellationMap.forEach((k, v) -> cancellationMapJson.put(k, new ArrayList<>(v)));

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("spec_id", specId);
                    result.put("spec_version", specVersion);
                    if (caseId != null && !caseId.isBlank()) {
                        result.put("case_id", caseId);
                    }
                    result.put("cancellation_map", cancellationMapJson);
                    result.put("issues", issues);
                    if (!liveBlastRadius.isEmpty()) {
                        result.put("live_blast_radius", liveBlastRadius);
                    }
                    result.put("tasks_with_cancel_regions", cancellationMap.size());
                    int totalEntries = cancellationMap.values().stream()
                        .mapToInt(Set::size).sum();
                    result.put("total_cancel_region_entries", totalEntries);
                    result.put("issue_count", issues.size());
                    result.put("analysis_time_ms", elapsedMs);

                    if (cancellationMap.isEmpty()) {
                        result.put("interpretation",
                            "No cancellation regions found in this specification.");
                    } else {
                        long errorCount = issues.stream()
                            .filter(i -> "ERROR".equals(i.get("severity")))
                            .count();
                        long warnCount = issues.stream()
                            .filter(i -> "WARNING".equals(i.get("severity")))
                            .count();
                        result.put("interpretation",
                            cancellationMap.size() + " cancellation region(s) found. " +
                            errorCount + " error(s), " + warnCount + " warning(s).");
                    }

                    return textResult(new ObjectMapper()
                        .writerWithDefaultPrettyPrinter().writeValueAsString(result));

                } catch (Exception e) {
                    return errorResult("Error auditing cancellation regions: " + e.getMessage());
                }
            }
        );
    }

    // =========================================================================
    // Cancellation Extraction & Analysis
    // =========================================================================

    /**
     * Extract cancellation sets from root net tasks.
     *
     * @param rootNet the root net element
     * @return map of taskId → set of cancelled element IDs
     */
    private static Map<String, Set<String>> extractCancellationSets(Element rootNet)
            throws Exception {

        Map<String, Set<String>> cancellationMap = new LinkedHashMap<>();

        NodeList tasks = rootNet.getElementsByTagNameNS(YAWL_NS, "task");
        if (tasks.getLength() == 0) {
            tasks = rootNet.getElementsByTagName("task");
        }

        for (int i = 0; i < tasks.getLength(); i++) {
            Element task = (Element) tasks.item(i);
            String taskId = task.getAttribute("id");
            if (taskId == null || taskId.isBlank()) {
                continue;
            }

            Set<String> cancelSet = new LinkedHashSet<>();

            // Direct element cancellations: <removesTokens id="elementId"/>
            NodeList removesTokens = task.getElementsByTagNameNS(YAWL_NS, "removesTokens");
            if (removesTokens.getLength() == 0) {
                removesTokens = task.getElementsByTagName("removesTokens");
            }
            for (int r = 0; r < removesTokens.getLength(); r++) {
                String cancelledId = ((Element) removesTokens.item(r)).getAttribute("id");
                if (cancelledId != null && !cancelledId.isBlank()) {
                    cancelSet.add(cancelledId);
                }
            }

            // Implicit condition cancellations: <removesTokensFromFlow>
            NodeList removesFlow = task.getElementsByTagNameNS(YAWL_NS, "removesTokensFromFlow");
            if (removesFlow.getLength() == 0) {
                removesFlow = task.getElementsByTagName("removesTokensFromFlow");
            }
            for (int r = 0; r < removesFlow.getLength(); r++) {
                Element rfElem = (Element) removesFlow.item(r);
                String srcId = extractChildAttributeId(rfElem, "flowSource");
                String dstId = extractChildAttributeId(rfElem, "flowDestination");
                if (srcId != null && dstId != null) {
                    cancelSet.add("flow:" + srcId + "→" + dstId);
                }
            }

            if (!cancelSet.isEmpty()) {
                cancellationMap.put(taskId, cancelSet);
            }
        }

        return cancellationMap;
    }

    /**
     * Extract child element attribute "id".
     */
    private static String extractChildAttributeId(Element parent, String childTag) {
        NodeList children = parent.getElementsByTagNameNS(YAWL_NS, childTag);
        if (children.getLength() == 0) {
            children = parent.getElementsByTagName(childTag);
        }
        if (children.getLength() == 0) {
            return null;
        }
        return ((Element) children.item(0)).getAttribute("id");
    }

    /**
     * Extract flow edges from root net (tasks and conditions).
     *
     * @param rootNet the root net element
     * @param outEdges output map: node ID → list of reachable node IDs
     */
    private static void extractFlowEdges(Element rootNet, Map<String, List<String>> outEdges)
            throws Exception {

        // Process tasks
        NodeList allTasks = rootNet.getElementsByTagNameNS(YAWL_NS, "task");
        if (allTasks.getLength() == 0) {
            allTasks = rootNet.getElementsByTagName("task");
        }
        for (int i = 0; i < allTasks.getLength(); i++) {
            Element task = (Element) allTasks.item(i);
            String taskId = task.getAttribute("id");
            if (taskId == null || taskId.isBlank()) {
                continue;
            }
            extractFlowFromElement(task, taskId, outEdges);
        }

        // Process conditions
        for (String condTag : new String[]{"inputCondition", "outputCondition", "condition"}) {
            NodeList conds = rootNet.getElementsByTagNameNS(YAWL_NS, condTag);
            if (conds.getLength() == 0) {
                conds = rootNet.getElementsByTagName(condTag);
            }
            for (int i = 0; i < conds.getLength(); i++) {
                Element cond = (Element) conds.item(i);
                String condId = cond.getAttribute("id");
                if (condId == null || condId.isBlank()) {
                    continue;
                }
                extractFlowFromElement(cond, condId, outEdges);
            }
        }
    }

    /**
     * Extract flowsInto edges from a single element.
     */
    private static void extractFlowFromElement(Element elem, String elemId,
                                               Map<String, List<String>> outEdges) {
        NodeList flows = elem.getElementsByTagNameNS(YAWL_NS, "flowsInto");
        if (flows.getLength() == 0) {
            flows = elem.getElementsByTagName("flowsInto");
        }

        List<String> successors = outEdges.computeIfAbsent(elemId, k -> new ArrayList<>());

        for (int f = 0; f < flows.getLength(); f++) {
            Element flow = (Element) flows.item(f);
            NodeList refs = flow.getElementsByTagNameNS(YAWL_NS, "nextElementRef");
            if (refs.getLength() == 0) {
                refs = flow.getElementsByTagName("nextElementRef");
            }
            for (int r = 0; r < refs.getLength(); r++) {
                String targetId = ((Element) refs.item(r)).getAttribute("id");
                if (targetId != null && !targetId.isBlank() && !successors.contains(targetId)) {
                    successors.add(targetId);
                }
            }
        }
    }

    /**
     * BFS reachability from a single start node.
     */
    private static Set<String> bfsReachable(String startId, Map<String, List<String>> outEdges) {
        Set<String> reachable = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(startId);
        reachable.add(startId);

        while (!queue.isEmpty()) {
            String curr = queue.poll();
            List<String> nexts = outEdges.getOrDefault(curr, List.of());
            for (String next : nexts) {
                if (!reachable.contains(next)) {
                    reachable.add(next);
                    queue.add(next);
                }
            }
        }

        return reachable;
    }

    /**
     * Detect structural issues in cancellation sets.
     */
    private static List<Map<String, Object>> detectStructuralIssues(
            Map<String, Set<String>> cancellationMap,
            Map<String, List<String>> outEdges,
            int blastRadiusThreshold) {

        List<Map<String, Object>> issues = new ArrayList<>();
        List<String> allTaskIds = new ArrayList<>(cancellationMap.keySet());

        // Issue 1: Mutual cancellation (A cancels B, B cancels A)
        for (int a = 0; a < allTaskIds.size(); a++) {
            for (int b = a + 1; b < allTaskIds.size(); b++) {
                String taskA = allTaskIds.get(a);
                String taskB = allTaskIds.get(b);
                boolean aCancelsB = cancellationMap.getOrDefault(taskA, Set.of())
                    .contains(taskB);
                boolean bCancelsA = cancellationMap.getOrDefault(taskB, Set.of())
                    .contains(taskA);

                if (aCancelsB && bCancelsA) {
                    Map<String, Object> issue = new LinkedHashMap<>();
                    issue.put("type", "MUTUAL_CANCELLATION");
                    issue.put("severity", "ERROR");
                    issue.put("task_ids", List.of(taskA, taskB));
                    issue.put("description",
                        "Tasks '" + taskA + "' and '" + taskB +
                        "' cancel each other — race condition risk");
                    issues.add(issue);
                }
            }
        }

        // Issue 2: Self-cancellation
        for (Map.Entry<String, Set<String>> entry : cancellationMap.entrySet()) {
            if (entry.getValue().contains(entry.getKey())) {
                Map<String, Object> issue = new LinkedHashMap<>();
                issue.put("type", "SELF_CANCELLATION");
                issue.put("severity", "ERROR");
                issue.put("task_id", entry.getKey());
                issue.put("description",
                    "Task '" + entry.getKey() +
                    "' cancels itself (known deadlock risk)");
                issues.add(issue);
            }
        }

        // Issue 3: Large blast radius
        for (Map.Entry<String, Set<String>> entry : cancellationMap.entrySet()) {
            if (entry.getValue().size() > blastRadiusThreshold) {
                Map<String, Object> issue = new LinkedHashMap<>();
                issue.put("type", "LARGE_BLAST_RADIUS");
                issue.put("severity", "WARNING");
                issue.put("task_id", entry.getKey());
                issue.put("cancelled_count", entry.getValue().size());
                issue.put("description",
                    "Task '" + entry.getKey() + "' cancels " +
                    entry.getValue().size() + " elements (threshold: " +
                    blastRadiusThreshold + ")");
                issues.add(issue);
            }
        }

        // Issue 4: Orphan cancellation
        for (Map.Entry<String, Set<String>> entry : cancellationMap.entrySet()) {
            String cancellingTask = entry.getKey();
            Set<String> reachable = bfsReachable(cancellingTask, outEdges);

            for (String cancelledId : entry.getValue()) {
                // Skip synthetic flow: IDs (implicit conditions)
                if (cancelledId.startsWith("flow:")) {
                    continue;
                }
                if (!reachable.contains(cancelledId)) {
                    Map<String, Object> issue = new LinkedHashMap<>();
                    issue.put("type", "ORPHAN_CANCELLATION");
                    issue.put("severity", "WARNING");
                    issue.put("cancelling_task", cancellingTask);
                    issue.put("cancelled_element", cancelledId);
                    issue.put("description",
                        "Task '" + cancellingTask + "' cancels '" + cancelledId +
                        "' which is not reachable from it in the flow graph");
                    issues.add(issue);
                }
            }
        }

        return issues;
    }

    /**
     * Analyze live blast radius for a specific case.
     *
     * @param interfaceBClient the InterfaceB client
     * @param sessionHandle the session handle
     * @param caseId the case ID
     * @param cancellationMap the cancellation map
     * @return map of cancelling task → list of live victims
     */
    private static Map<String, List<String>> analyzeLiveBlastRadius(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle,
            String caseId,
            Map<String, Set<String>> cancellationMap) {

        Map<String, List<String>> liveBlastRadius = new LinkedHashMap<>();

        try {
            List<WorkItemRecord> liveItems = interfaceBClient.getWorkItemsForCase(caseId, sessionHandle);
            if (liveItems == null) {
                return liveBlastRadius;
            }

            Set<String> liveTaskIds = liveItems.stream()
                .filter(i -> {
                    String status = i.getStatus();
                    return WorkItemRecord.statusEnabled.equals(status) ||
                           WorkItemRecord.statusExecuting.equals(status) ||
                           WorkItemRecord.statusFired.equals(status);
                })
                .map(WorkItemRecord::getTaskID)
                .collect(Collectors.toSet());

            for (Map.Entry<String, Set<String>> entry : cancellationMap.entrySet()) {
                String cancellingTask = entry.getKey();
                if (!liveTaskIds.contains(cancellingTask)) {
                    continue;
                }

                List<String> liveVictims = entry.getValue().stream()
                    .filter(liveTaskIds::contains)
                    .collect(Collectors.toList());

                if (!liveVictims.isEmpty()) {
                    liveBlastRadius.put(cancellingTask, liveVictims);
                }
            }
        } catch (Exception e) {
            // If live analysis fails, continue with structural analysis
        }

        return liveBlastRadius;
    }

    // =========================================================================
    // XML Parsing & Helpers
    // =========================================================================

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

    // =========================================================================
    // MCP Result & Argument Helpers
    // =========================================================================

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
}
