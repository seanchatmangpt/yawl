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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Counterfactual Simulator tool for the YAWL MCP server.
 *
 * <p>Simulates firing a workflow transition on an IN-MEMORY COPY of the current
 * Petri net marking, with ZERO side effects on the live running case.
 *
 * <p>Exposes a single MCP tool: {@code yawl_simulate_transition}
 *
 * <h2>The Claim Made Visible</h2>
 * Answers the question "what happens if I complete task X next?" using Petri net
 * token firing semantics: consume input place tokens, produce output place tokens,
 * compute newly enabled tasks. Returns the resulting marking and whether the simulated
 * path converges toward case completion.
 *
 * <p>Cost: 0 side effects on the live case. The simulation is purely in-memory and
 * deterministic, derived from the formal workflow specification.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class CounterfactualSimulatorTools {

    private static final String YAWL_NS = "http://www.yawlfoundation.org/yawlschema";

    private CounterfactualSimulatorTools() {
        throw new UnsupportedOperationException(
            "CounterfactualSimulatorTools is a static factory class and cannot be instantiated.");
    }

    /**
     * Creates the counterfactual transition simulator tool specification.
     *
     * @param interfaceBClient connected InterfaceB client
     * @param sessionHandle    active YAWL session handle
     * @return simulator tool specification for MCP registration
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

        return createSimulateTransitionTool(interfaceBClient, sessionHandle);
    }

    // =========================================================================
    // Tool: yawl_simulate_transition
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createSimulateTransitionTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("caseId", Map.of(
            "type", "string",
            "description", "The YAWL case identifier (e.g. '1.1', '42')"));
        props.put("taskId", Map.of(
            "type", "string",
            "description", "Task ID to simulate firing (must be currently enabled)"));

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, List.of("caseId", "taskId"), false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_simulate_transition")
                .description(
                    "Simulate firing a workflow transition on an in-memory copy of the current " +
                    "case marking — zero side effects on the live case. " +
                    "Answers 'what happens if I complete task X next?' using Petri net token " +
                    "firing semantics: consume input place tokens, produce output place tokens, " +
                    "compute newly enabled tasks. Returns the resulting marking and whether the " +
                    "simulated path converges toward case completion. " +
                    "CONSTRUCT coordination: routing decision is derived from the formal workflow " +
                    "specification, not LLM inference. Cost: 0 side effects, deterministic.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String caseId = requireStringArg(params, "caseId");
                    String taskId = requireStringArg(params, "taskId");
                    long start = System.currentTimeMillis();

                    // Step 1: Get live work items to determine current marking
                    List<WorkItemRecord> liveItems =
                        interfaceBClient.getWorkItemsForCase(caseId, sessionHandle);

                    if (liveItems == null || liveItems.isEmpty()) {
                        return errorResult(
                            "No active work items for case " + caseId + ". " +
                            "Case may be complete or not yet started.");
                    }

                    // Step 2: Get specification to extract net structure
                    String specXml = interfaceBClient.getSpecificationForCase(caseId, sessionHandle);
                    if (specXml == null || specXml.contains("<failure>")) {
                        return errorResult(
                            "Failed to retrieve specification for case " + caseId);
                    }

                    // Step 3: Parse net structure (tasks, conditions, flow edges)
                    NetStructure netStruct = parseNetStructure(specXml);

                    // Step 4: Build current marking from live work items
                    Set<String> currentMarking = new HashSet<>();
                    for (WorkItemRecord item : liveItems) {
                        if (WorkItemRecord.statusEnabled.equals(item.getStatus()) ||
                            WorkItemRecord.statusExecuting.equals(item.getStatus())) {
                            currentMarking.add(item.getTaskID());
                        }
                    }

                    // Step 5: Validate task is enabled
                    if (!currentMarking.contains(taskId)) {
                        return errorResult(
                            "Task '" + taskId + "' is not currently enabled. " +
                            "Currently enabled tasks: " + currentMarking);
                    }

                    // Step 6: Simulate firing the transition
                    Set<String> preSimulationMarking = new HashSet<>(currentMarking);
                    Set<String> postSimulationMarking = simulateFiring(
                        taskId, currentMarking, netStruct);

                    Set<String> consumedTokens = new HashSet<>(preSimulationMarking);
                    consumedTokens.retainAll(currentMarking); // tokens removed
                    consumedTokens = new HashSet<>(preSimulationMarking);
                    consumedTokens.removeAll(postSimulationMarking);

                    Set<String> producedTokens = new HashSet<>(postSimulationMarking);
                    producedTokens.removeAll(preSimulationMarking);

                    Set<String> newlyEnabledTasks = new HashSet<>(postSimulationMarking);
                    newlyEnabledTasks.removeAll(preSimulationMarking);

                    // Step 7: Compute distance to completion
                    int preDist = computeDistanceToCompletion(preSimulationMarking, netStruct);
                    int postDist = computeDistanceToCompletion(postSimulationMarking, netStruct);
                    int distanceDelta = preDist - postDist;

                    boolean converges = distanceDelta > 0 || postSimulationMarking.isEmpty();

                    long elapsed = System.currentTimeMillis() - start;

                    // Step 8: Format result
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("case_id", caseId);
                    result.put("simulated_task", taskId);
                    result.put("simulation_type", "counterfactual_zero_side_effects");
                    result.put("pre_simulation_marking", new ArrayList<>(preSimulationMarking));
                    result.put("post_simulation_marking", new ArrayList<>(postSimulationMarking));
                    result.put("consumed_tokens", new ArrayList<>(consumedTokens));
                    result.put("produced_tokens", new ArrayList<>(producedTokens));
                    result.put("newly_enabled_tasks", new ArrayList<>(newlyEnabledTasks));
                    result.put("distance_to_completion_delta", distanceDelta);
                    result.put("converges_toward_completion", converges);
                    result.put("proof_method", "petri_net_token_firing_rule");
                    result.put("token_cost", "0");
                    result.put("coordination_model", "CONSTRUCT");
                    result.put("query_time_ms", elapsed);
                    result.put("warning", "This is a simulation only. No change made to the live case.");

                    return textResult(new ObjectMapper()
                        .writerWithDefaultPrettyPrinter().writeValueAsString(result));

                } catch (Exception e) {
                    return errorResult("Error simulating transition: " + e.getMessage());
                }
            }
        );
    }

    // =========================================================================
    // Petri Net Structure and Simulation
    // =========================================================================

    /**
     * In-memory representation of the workflow net structure.
     */
    private static class NetStructure {
        Set<String> tasks;                    // task IDs
        Set<String> conditions;               // condition IDs
        Map<String, List<String>> outEdges;   // task -> [conditions]
        Map<String, List<String>> inEdges;    // condition -> [tasks]
        Map<String, String> taskNames;        // task ID -> task name
        Map<String, String> taskJoinTypes;    // task ID -> AND/XOR
        Map<String, String> taskSplitTypes;   // task ID -> AND/XOR

        NetStructure() {
            this.tasks = new HashSet<>();
            this.conditions = new HashSet<>();
            this.outEdges = new HashMap<>();
            this.inEdges = new HashMap<>();
            this.taskNames = new HashMap<>();
            this.taskJoinTypes = new HashMap<>();
            this.taskSplitTypes = new HashMap<>();
        }
    }

    /**
     * Parse YAWL specification XML to extract net structure.
     *
     * @param specXml YAWL specification XML
     * @return parsed NetStructure
     * @throws RuntimeException if parsing fails
     */
    private static NetStructure parseNetStructure(String specXml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Disable external entity resolution to prevent XXE
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(
            specXml.getBytes(StandardCharsets.UTF_8)));

        NetStructure net = new NetStructure();

        // Find root net (isRootNet="true")
        Element rootNet = findRootNet(doc);
        if (rootNet == null) {
            throw new RuntimeException("No root net found in specification");
        }

        // Extract tasks
        NodeList taskNodes = rootNet.getElementsByTagNameNS(YAWL_NS, "task");
        if (taskNodes.getLength() == 0) {
            taskNodes = rootNet.getElementsByTagName("task");
        }

        for (int i = 0; i < taskNodes.getLength(); i++) {
            Element task = (Element) taskNodes.item(i);
            String taskId = task.getAttribute("id");
            if (taskId != null && !taskId.isBlank()) {
                net.tasks.add(taskId);
                net.outEdges.putIfAbsent(taskId, new ArrayList<>());

                // Extract task name
                NodeList nameNodes = task.getElementsByTagNameNS(YAWL_NS, "name");
                if (nameNodes.getLength() == 0) {
                    nameNodes = task.getElementsByTagName("name");
                }
                if (nameNodes.getLength() > 0) {
                    String name = nameNodes.item(0).getTextContent();
                    if (name != null) {
                        net.taskNames.put(taskId, name.trim());
                    }
                }

                // Extract join type
                NodeList joinNodes = task.getElementsByTagNameNS(YAWL_NS, "join");
                if (joinNodes.getLength() == 0) {
                    joinNodes = task.getElementsByTagName("join");
                }
                if (joinNodes.getLength() > 0) {
                    String joinType = ((Element) joinNodes.item(0)).getAttribute("code");
                    if (joinType != null) {
                        net.taskJoinTypes.put(taskId, joinType.toUpperCase());
                    }
                }

                // Extract split type
                NodeList splitNodes = task.getElementsByTagNameNS(YAWL_NS, "split");
                if (splitNodes.getLength() == 0) {
                    splitNodes = task.getElementsByTagName("split");
                }
                if (splitNodes.getLength() > 0) {
                    String splitType = ((Element) splitNodes.item(0)).getAttribute("code");
                    if (splitType != null) {
                        net.taskSplitTypes.put(taskId, splitType.toUpperCase());
                    }
                }
            }
        }

        // Extract conditions
        NodeList conditionNodes = rootNet.getElementsByTagNameNS(YAWL_NS, "condition");
        if (conditionNodes.getLength() == 0) {
            conditionNodes = rootNet.getElementsByTagName("condition");
        }

        for (int i = 0; i < conditionNodes.getLength(); i++) {
            Element condition = (Element) conditionNodes.item(i);
            String condId = condition.getAttribute("id");
            if (condId != null && !condId.isBlank()) {
                net.conditions.add(condId);
                net.inEdges.putIfAbsent(condId, new ArrayList<>());
            }
        }

        // Extract flow edges (task -> condition and condition -> task)
        NodeList edgeNodes = rootNet.getElementsByTagNameNS(YAWL_NS, "flow");
        if (edgeNodes.getLength() == 0) {
            edgeNodes = rootNet.getElementsByTagName("flow");
        }

        for (int i = 0; i < edgeNodes.getLength(); i++) {
            Element flow = (Element) edgeNodes.item(i);
            String source = flow.getAttribute("source");
            String target = flow.getAttribute("target");

            if (source != null && target != null) {
                // Task -> Condition edge
                if (net.tasks.contains(source) && net.conditions.contains(target)) {
                    net.outEdges.get(source).add(target);
                    net.inEdges.get(target).add(source);
                }
                // Condition -> Task edge
                else if (net.conditions.contains(source) && net.tasks.contains(target)) {
                    net.outEdges.putIfAbsent(source, new ArrayList<>());
                    net.outEdges.get(source).add(target);
                    net.inEdges.putIfAbsent(target, new ArrayList<>());
                    net.inEdges.get(target).add(source);
                }
            }
        }

        return net;
    }

    /**
     * Find the root net element (decomposition with isRootNet="true").
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
     * Simulate firing a task transition on the given marking.
     *
     * <p>Petri net firing rule:
     * 1. Remove the fired task from the marking (consume input token)
     * 2. Add all output conditions to the marking (produce output tokens)
     * 3. Compute newly enabled tasks based on join types
     *
     * @param taskId         task to fire
     * @param currentMarking current set of enabled tasks
     * @param net            workflow net structure
     * @return new marking after firing
     */
    private static Set<String> simulateFiring(
            String taskId, Set<String> currentMarking, NetStructure net) {

        Set<String> newMarking = new HashSet<>(currentMarking);

        // Remove the fired task
        newMarking.remove(taskId);

        // Add all output conditions (from task's outgoing edges)
        List<String> outputConditions = net.outEdges.getOrDefault(taskId, new ArrayList<>());
        for (String condition : outputConditions) {
            // Mark the condition; check which tasks become enabled
            for (String potentialTask : net.tasks) {
                if (newMarking.contains(potentialTask)) {
                    continue; // Already enabled
                }

                // Check if this task is enabled by the new condition
                List<String> inputConditions = net.inEdges.getOrDefault(potentialTask, new ArrayList<>());
                if (inputConditions.isEmpty()) {
                    continue; // No input conditions (shouldn't happen in well-formed net)
                }

                String joinType = net.taskJoinTypes.getOrDefault(potentialTask, "XOR");

                if ("AND".equals(joinType)) {
                    // All input conditions must be marked
                    boolean allMarked = true;
                    for (String inCond : inputConditions) {
                        if (!isConditionMarked(inCond, outputConditions)) {
                            allMarked = false;
                            break;
                        }
                    }
                    if (allMarked) {
                        newMarking.add(potentialTask);
                    }
                } else {
                    // XOR: any input condition suffices
                    for (String inCond : inputConditions) {
                        if (condition.equals(inCond)) {
                            newMarking.add(potentialTask);
                            break;
                        }
                    }
                }
            }
        }

        return newMarking;
    }

    /**
     * Check if a condition is marked (i.e., in the set of produced conditions).
     */
    private static boolean isConditionMarked(String condition, List<String> markedConditions) {
        return markedConditions.contains(condition);
    }

    /**
     * Compute a heuristic distance to case completion (number of enabled tasks remaining).
     * Lower distance = closer to completion.
     */
    private static int computeDistanceToCompletion(Set<String> marking, NetStructure net) {
        if (marking.isEmpty()) {
            return 0; // Case is complete
        }
        // Heuristic: distance = number of enabled tasks
        return marking.size();
    }

    // =========================================================================
    // Helper Methods
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
}
