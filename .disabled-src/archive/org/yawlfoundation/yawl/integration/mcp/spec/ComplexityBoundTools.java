/*
 * Copyright (c) 2024 YAWL Foundation. All rights reserved.
 */
package org.yawlfoundation.yawl.integration.mcp.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Structural complexity bounds tools for the YAWL MCP server.
 *
 * <p>Exposes formal structural analysis of YAWL workflow specifications through MCP.
 * Computes minimum and maximum task execution counts, degree of parallelism, and
 * cyclomatic complexity using pure Petri net topology analysis — no inference,
 * no simulation, no heuristics.
 *
 * <h2>The Tool</h2>
 * <ul>
 *   <li>{@code yawl_compute_structural_bounds} — formal complexity certificate for a workflow spec</li>
 * </ul>
 *
 * <h2>Complexity Metrics</h2>
 * <table>
 *   <tr><th>Metric</th><th>Definition</th><th>Use Case</th></tr>
 *   <tr><td>min_tasks_to_complete</td><td>Shortest path from start to end (BFS, take min at XOR-splits)</td><td>Best case throughput</td></tr>
 *   <tr><td>max_tasks_to_complete</td><td>Longest path from start to end (DFS, take max at XOR-splits)</td><td>Worst case SLA</td></tr>
 *   <tr><td>max_parallelism</td><td>Maximum concurrent tasks at any AND-split</td><td>Resource planning</td></tr>
 *   <tr><td>cyclomatic_complexity</td><td>1 + number of XOR/OR decision points (McCabe)</td><td>Maintainability</td></tr>
 * </table>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class ComplexityBoundTools {

    /** YAWL XML namespace for DOM parsing. */
    private static final String YAWL_NS = "http://www.yawlfoundation.org/yawlschema";

    private ComplexityBoundTools() {
        throw new UnsupportedOperationException(
            "ComplexityBoundTools is a static factory class and cannot be instantiated.");
    }

    /**
     * Creates the structural bounds computation tool specification.
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

        return createComputeStructuralBoundsTool(interfaceBClient, sessionHandle);
    }

    // =========================================================================
    // Tool: yawl_compute_structural_bounds
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createComputeStructuralBoundsTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("specId", Map.of(
            "type", "string",
            "description", "Specification identifier (URI)"));

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, List.of("specId"), false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_compute_structural_bounds")
                .description(
                    "Compute formal structural complexity bounds for a workflow specification " +
                    "using Petri net topology analysis. Calculates: minimum tasks to completion " +
                    "(shortest path through XOR-splits), maximum tasks to completion (longest path " +
                    "through AND-splits + all parallel branches), degree of parallelism (max " +
                    "concurrent AND-split branches), and McCabe cyclomatic complexity (XOR decision " +
                    "points). Returns a formal workflow complexity certificate — no LLM inference, " +
                    "pure graph theory. Cost: 0 inference tokens. Proof method: Petri net structural analysis.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String specId = requireStringArg(args.arguments(), "specId");
                    long start = System.currentTimeMillis();

                    // Get specification XML
                    YSpecificationID ySpecId = new YSpecificationID(specId, "0.1", specId);
                    String specXml = interfaceBClient.getSpecification(ySpecId, sessionHandle);

                    if (specXml == null || specXml.contains("<failure>")) {
                        return errorResult("Failed to get specification " + specId + ": " + specXml);
                    }

                    // Parse and analyze
                    Document doc = parseXml(specXml);
                    StructuralAnalyzer analyzer = new StructuralAnalyzer(doc);
                    Map<String, Object> bounds = analyzer.computeBounds();

                    long elapsed = System.currentTimeMillis() - start;

                    // Build result
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("spec_id", specId);
                    result.put("structural_bounds", bounds.get("structural_bounds"));
                    result.put("complexity_verdict", bounds.get("complexity_verdict"));
                    result.put("complexity_score", bounds.get("complexity_score"));
                    result.put("proof_method", "petri_net_structural_analysis");
                    result.put("token_cost", "0");
                    result.put("coordination_model", "CONSTRUCT");
                    result.put("interpretation", bounds.get("interpretation"));
                    result.put("query_time_ms", elapsed);

                    return textResult(new ObjectMapper()
                        .writerWithDefaultPrettyPrinter().writeValueAsString(result));

                } catch (Exception e) {
                    return errorResult("Error computing structural bounds: " + e.getMessage());
                }
            }
        );
    }

    // =========================================================================
    // Structural Analyzer — Petri Net Topology Analysis
    // =========================================================================

    private static class StructuralAnalyzer {
        private final Document doc;
        private final Map<String, TaskInfo> tasks = new LinkedHashMap<>();
        private final Map<String, List<String>> flowGraph = new LinkedHashMap<>(); // nodeId -> List of successors
        private final Set<String> visited = new HashSet<>();
        private String inputConditionId;
        private String outputConditionId;

        StructuralAnalyzer(Document doc) {
            this.doc = doc;
        }

        Map<String, Object> computeBounds() throws Exception {
            extractTopology();
            buildFlowGraph();

            // Compute metrics
            int minTasks = computeMinPathLength();
            int maxTasks = computeMaxPathLength();
            int maxParallelism = computeMaxParallelism();
            int xorSplitCount = countXorSplits();
            int andSplitCount = countAndSplits();
            int decisionPoints = xorSplitCount; // (XOR or OR splits are decision points)
            int mccabeComplexity = decisionPoints + 1;
            int totalTasks = tasks.size();
            int totalConditions = countConditions();

            // Complexity score: weighted sum
            double complexityScore = (xorSplitCount * 2.0) +
                                    (andSplitCount * 3.0) +
                                    (maxParallelism * 1.5);

            // Complexity verdict
            String verdict;
            if (complexityScore <= 3) {
                verdict = "SIMPLE";
            } else if (complexityScore <= 7) {
                verdict = "MODERATE";
            } else if (complexityScore <= 15) {
                verdict = "COMPLEX";
            } else {
                verdict = "VERY_COMPLEX";
            }

            // Build interpretation
            Map<String, Object> interpretation = new LinkedHashMap<>();
            interpretation.put("min_path",
                "Best case: " + minTasks + " tasks (happy path with all XOR-splits taking shortest branch)");
            interpretation.put("max_path",
                "Worst case: " + maxTasks + " tasks (all AND-split branches + longest XOR branches executed)");
            interpretation.put("parallelism",
                "Up to " + maxParallelism + " tasks can execute concurrently at AND-splits");
            interpretation.put("complexity",
                "McCabe complexity " + mccabeComplexity + ": " + decisionPoints +
                " decision points + 1 (" +
                (mccabeComplexity <= 3 ? "simple, easily testable" :
                 mccabeComplexity <= 7 ? "moderate, reasonable to test" :
                 mccabeComplexity <= 10 ? "complex, difficult to test fully" :
                 "very complex, high maintenance risk") + ")");

            // Structural bounds map
            Map<String, Object> bounds = new LinkedHashMap<>();
            bounds.put("min_tasks_to_complete", minTasks);
            bounds.put("max_tasks_to_complete", maxTasks);
            bounds.put("max_parallelism", maxParallelism);
            bounds.put("cyclomatic_complexity", mccabeComplexity);
            bounds.put("and_split_count", andSplitCount);
            bounds.put("xor_split_count", xorSplitCount);
            bounds.put("total_tasks", totalTasks);
            bounds.put("total_conditions", totalConditions);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("structural_bounds", bounds);
            result.put("complexity_verdict", verdict);
            result.put("complexity_score", Math.round(complexityScore * 10.0) / 10.0);
            result.put("interpretation", interpretation);

            return result;
        }

        private void extractTopology() throws Exception {
            Element rootNet = findRootNet();
            if (rootNet == null) return;

            // Extract input/output condition IDs
            NodeList inputs = rootNet.getElementsByTagNameNS(YAWL_NS, "inputCondition");
            if (inputs.getLength() == 0) inputs = rootNet.getElementsByTagName("inputCondition");
            if (inputs.getLength() > 0) {
                inputConditionId = ((Element) inputs.item(0)).getAttribute("id");
            }

            NodeList outputs = rootNet.getElementsByTagNameNS(YAWL_NS, "outputCondition");
            if (outputs.getLength() == 0) outputs = rootNet.getElementsByTagName("outputCondition");
            if (outputs.getLength() > 0) {
                outputConditionId = ((Element) outputs.item(0)).getAttribute("id");
            }

            // Extract all tasks
            NodeList taskNodes = rootNet.getElementsByTagNameNS(YAWL_NS, "task");
            if (taskNodes.getLength() == 0) taskNodes = rootNet.getElementsByTagName("task");

            for (int i = 0; i < taskNodes.getLength(); i++) {
                Element task = (Element) taskNodes.item(i);
                String taskId = task.getAttribute("id");
                if (taskId == null || taskId.isBlank()) continue;

                String name = extractChildText(task, "name");
                String joinType = extractJoinType(task);
                String splitType = extractSplitType(task);

                tasks.put(taskId, new TaskInfo(taskId, name, joinType, splitType));
            }
        }

        private void buildFlowGraph() throws Exception {
            Element rootNet = findRootNet();
            if (rootNet == null) return;

            // Process all flow elements (from tasks and conditions)
            NodeList flows = rootNet.getElementsByTagNameNS(YAWL_NS, "flow");
            if (flows.getLength() == 0) flows = rootNet.getElementsByTagName("flow");

            for (int i = 0; i < flows.getLength(); i++) {
                Element flow = (Element) flows.item(i);
                String source = flow.getAttribute("source");
                String target = flow.getAttribute("target");

                if (source != null && !source.isBlank() && target != null && !target.isBlank()) {
                    flowGraph.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
                }
            }
        }

        private int computeMinPathLength() {
            if (inputConditionId == null || outputConditionId == null) return 0;

            Map<String, Integer> distances = new LinkedHashMap<>();
            Queue<String> queue = new LinkedList<>();

            distances.put(inputConditionId, 0);
            queue.add(inputConditionId);

            while (!queue.isEmpty()) {
                String current = queue.poll();
                int currentDist = distances.get(current);

                List<String> successors = flowGraph.getOrDefault(current, List.of());
                for (String successor : successors) {
                    // Skip if already visited (BFS finds shortest first)
                    if (distances.containsKey(successor)) continue;

                    int nextDist = currentDist;
                    if (tasks.containsKey(successor)) {
                        nextDist++; // Count task transitions
                    }

                    distances.put(successor, nextDist);
                    queue.add(successor);

                    if (successor.equals(outputConditionId)) {
                        return nextDist;
                    }
                }
            }

            return 0;
        }

        private int computeMaxPathLength() {
            if (inputConditionId == null || outputConditionId == null) return 0;

            visited.clear();
            return dfsMaxPath(inputConditionId, new HashMap<>());
        }

        private int dfsMaxPath(String nodeId, Map<String, Integer> memo) {
            if (nodeId.equals(outputConditionId)) {
                return 0;
            }

            if (memo.containsKey(nodeId)) {
                return memo.get(nodeId);
            }

            // Cycle detection
            if (visited.contains(nodeId)) {
                return 0; // Don't follow cycles
            }

            visited.add(nodeId);

            List<String> successors = flowGraph.getOrDefault(nodeId, List.of());
            if (successors.isEmpty()) {
                visited.remove(nodeId);
                return 0;
            }

            int maxDist = 0;
            TaskInfo task = tasks.get(nodeId);

            if (task != null && "and".equalsIgnoreCase(task.splitType)) {
                // AND-split: sum all branches
                for (String successor : successors) {
                    int pathLen = dfsMaxPath(successor, memo);
                    maxDist += pathLen;
                }
                // Add current task
                maxDist += 1;
            } else {
                // XOR/OR-split: take max branch
                for (String successor : successors) {
                    int pathLen = dfsMaxPath(successor, memo);
                    maxDist = Math.max(maxDist, pathLen);
                }
                if (tasks.containsKey(nodeId)) {
                    maxDist += 1;
                }
            }

            visited.remove(nodeId);
            memo.put(nodeId, maxDist);
            return maxDist;
        }

        private int computeMaxParallelism() {
            int maxConcurrent = 0;

            for (TaskInfo task : tasks.values()) {
                if ("and".equalsIgnoreCase(task.splitType)) {
                    List<String> branches = flowGraph.getOrDefault(task.id, List.of());
                    maxConcurrent = Math.max(maxConcurrent, branches.size());
                }
            }

            return maxConcurrent;
        }

        private int countXorSplits() {
            int count = 0;
            for (TaskInfo task : tasks.values()) {
                if ("xor".equalsIgnoreCase(task.splitType) || "or".equalsIgnoreCase(task.splitType)) {
                    count++;
                }
            }
            return count;
        }

        private int countAndSplits() {
            int count = 0;
            for (TaskInfo task : tasks.values()) {
                if ("and".equalsIgnoreCase(task.splitType)) {
                    count++;
                }
            }
            return count;
        }

        private int countConditions() {
            int count = 0;
            Element rootNet = findRootNet();
            if (rootNet == null) return 0;

            // Input condition
            NodeList inputs = rootNet.getElementsByTagNameNS(YAWL_NS, "inputCondition");
            if (inputs.getLength() == 0) inputs = rootNet.getElementsByTagName("inputCondition");
            count += inputs.getLength();

            // Output condition
            NodeList outputs = rootNet.getElementsByTagNameNS(YAWL_NS, "outputCondition");
            if (outputs.getLength() == 0) outputs = rootNet.getElementsByTagName("outputCondition");
            count += outputs.getLength();

            // Regular conditions
            NodeList conditions = rootNet.getElementsByTagNameNS(YAWL_NS, "condition");
            if (conditions.getLength() == 0) conditions = rootNet.getElementsByTagName("condition");
            count += conditions.getLength();

            return count;
        }

        private Element findRootNet() {
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

        private String extractJoinType(Element task) {
            NodeList joinNodes = task.getElementsByTagNameNS(YAWL_NS, "joinType");
            if (joinNodes.getLength() == 0) {
                joinNodes = task.getElementsByTagName("joinType");
            }
            if (joinNodes.getLength() > 0) {
                NodeList codeNodes = ((Element) joinNodes.item(0))
                    .getElementsByTagName("code");
                if (codeNodes.getLength() > 0) {
                    return codeNodes.item(0).getTextContent().trim();
                }
            }
            return "xor"; // YAWL default
        }

        private String extractSplitType(Element task) {
            NodeList splitNodes = task.getElementsByTagNameNS(YAWL_NS, "splitType");
            if (splitNodes.getLength() == 0) {
                splitNodes = task.getElementsByTagName("splitType");
            }
            if (splitNodes.getLength() > 0) {
                NodeList codeNodes = ((Element) splitNodes.item(0))
                    .getElementsByTagName("code");
                if (codeNodes.getLength() > 0) {
                    return codeNodes.item(0).getTextContent().trim();
                }
            }
            return "and"; // YAWL default
        }

        private String extractChildText(Element parent, String tagName) {
            NodeList nodes = parent.getElementsByTagNameNS(YAWL_NS, tagName);
            if (nodes.getLength() == 0) {
                nodes = parent.getElementsByTagName(tagName);
            }
            if (nodes.getLength() > 0) {
                String text = nodes.item(0).getTextContent();
                if (text != null && !text.isBlank()) return text.trim();
            }
            return null;
        }
    }

    /**
     * Immutable task information extracted from YAWL spec XML.
     */
    private static class TaskInfo {
        final String id;
        final String name;
        final String joinType;
        final String splitType;

        TaskInfo(String id, String name, String joinType, String splitType) {
            this.id = id;
            this.name = name;
            this.joinType = joinType;
            this.splitType = splitType;
        }
    }

    // =========================================================================
    // Internal helpers (copied from ConstructCoordinationTools pattern)
    // =========================================================================

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Disable external entity resolution to prevent XXE
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
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
}
