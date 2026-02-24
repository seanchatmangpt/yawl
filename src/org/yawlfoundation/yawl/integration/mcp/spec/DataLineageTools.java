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
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Data Lineage Analyzer tool for the YAWL MCP server.
 *
 * <p>Traces XQuery data flow mappings in YAWL workflow specifications to understand
 * how data flows between workflow variables and tasks. Analyzes input/output parameters,
 * local variables, and task data mappings (starting, completion, enablement) to build
 * a complete picture of data dependencies and transformations.
 *
 * <p>Use cases:
 * <ul>
 *   <li>Data impact analysis: identify which variables affect a specific task</li>
 *   <li>Data provenance: trace where a variable's value originates</li>
 *   <li>Dead data detection: find variables that are declared but never used</li>
 *   <li>Data flow validation: detect orphaned producers and dangling consumers</li>
 *   <li>XQuery expression mapping: understand data transformation logic</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DataLineageTools {

    private static final String YAWL_NS = "http://www.yawlfoundation.org/yawlschema";

    private DataLineageTools() {
        throw new UnsupportedOperationException(
            "DataLineageTools is a static factory class and cannot be instantiated.");
    }

    /**
     * Creates the data lineage analyzer tool specification.
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
            "description", "Specification version (e.g. '0.1'). Optional, defaults to '0.1'."));
        props.put("variableName", Map.of(
            "type", "string",
            "description", "Optional: filter results to show only data flows involving this variable"));

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, List.of("specId"), false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_trace_data_lineage")
                .description(
                    "Trace XQuery data flow mappings in YAWL workflow specifications. " +
                    "Analyzes how data flows between workflow variables and tasks via input/output parameters, " +
                    "local variables, and task data mappings (starting, completion, enablement). " +
                    "Identifies orphaned variables (declared but not consumed), dangling consumers " +
                    "(variables consumed but never produced), and XQuery transformation expressions. " +
                    "Supports filtering by variable name to trace specific data lineages. " +
                    "Essential for data impact analysis, provenance tracking, and data flow validation.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String specId = requireStringArg(params, "specId");
                    String specVersion = optionalStringArg(params, "specVersion", "0.1");
                    String focusVar = optionalStringArg(params, "variableName", null);

                    long startMs = System.currentTimeMillis();

                    // Step 1: Get specification XML
                    YSpecificationID ySpecId = new YSpecificationID(specId, specVersion, specId);
                    String specXml = interfaceBClient.getSpecification(ySpecId, sessionHandle);

                    if (specXml == null || specXml.contains("<failure>")) {
                        return errorResult(
                            "Failed to get specification " + specId + ": " + specXml);
                    }

                    // Step 2: Parse XML and find root net
                    Document doc = parseXml(specXml);
                    Element rootNet = findRootNet(doc);
                    if (rootNet == null) {
                        return errorResult("No root net found in spec: " + specId);
                    }

                    // Step 3: Extract net-level variables
                    List<Map<String, String>> netVariables = new ArrayList<>();
                    Set<String> netVarNames = new HashSet<>();

                    for (String paramTag : new String[]{"inputParam", "outputParam", "localVariable"}) {
                        String scope = "inputParam".equals(paramTag) ? "net_input"
                                     : "outputParam".equals(paramTag) ? "net_output" : "local";
                        NodeList paramNodes = rootNet.getElementsByTagNameNS(YAWL_NS, paramTag);
                        if (paramNodes.getLength() == 0) {
                            paramNodes = rootNet.getElementsByTagName(paramTag);
                        }

                        for (int i = 0; i < paramNodes.getLength(); i++) {
                            Element p = (Element) paramNodes.item(i);
                            String name = extractChildText(p, "name");
                            String type = extractChildText(p, "type");
                            if (name != null && !name.isBlank()) {
                                Map<String, String> v = new LinkedHashMap<>();
                                v.put("name", name);
                                v.put("type", type != null ? type : "unknown");
                                v.put("scope", scope);
                                netVariables.add(v);
                                netVarNames.add(name);
                            }
                        }
                    }

                    // Step 4: Extract task data mappings
                    List<Map<String, Object>> nodes = new ArrayList<>();
                    List<Map<String, Object>> edges = new ArrayList<>();

                    // Add the "net" pseudo-node once
                    Map<String, Object> netNode = new LinkedHashMap<>();
                    netNode.put("id", "net");
                    netNode.put("type", "net");
                    netNode.put("name", "Net Variables");
                    nodes.add(netNode);

                    Set<String> addedTaskIds = new HashSet<>();
                    NodeList tasks = rootNet.getElementsByTagNameNS(YAWL_NS, "task");
                    if (tasks.getLength() == 0) {
                        tasks = rootNet.getElementsByTagName("task");
                    }

                    for (int t = 0; t < tasks.getLength(); t++) {
                        Element task = (Element) tasks.item(t);
                        String taskId = task.getAttribute("id");
                        String taskName = extractChildText(task, "name");
                        if (taskName == null || taskName.isBlank()) {
                            taskName = taskId;
                        }

                        if (!addedTaskIds.contains(taskId)) {
                            Map<String, Object> taskNode = new LinkedHashMap<>();
                            taskNode.put("id", taskId);
                            taskNode.put("type", "task");
                            taskNode.put("name", taskName);
                            nodes.add(taskNode);
                            addedTaskIds.add(taskId);
                        }

                        // Process each mapping type
                        // starting: net → task (task reads from net)
                        processMapping(task, taskId, "startingMappings", "starting",
                                     "net", taskId, edges);
                        // completion: task → net (task writes to net)
                        processMapping(task, taskId, "completedMappings", "completion",
                                     taskId, "net", edges);
                        // enablement: net → task at enablement
                        processMapping(task, taskId, "enablementMappings", "enablement",
                                     "net", taskId, edges);
                    }

                    // Step 5: Orphan and dangling detection
                    Set<String> consumedByTasks = edges.stream()
                        .filter(e -> "starting".equals(e.get("mapping_type")))
                        .map(e -> (String) e.get("variable"))
                        .collect(Collectors.toSet());

                    Set<String> producedByTasks = edges.stream()
                        .filter(e -> "completion".equals(e.get("mapping_type")))
                        .map(e -> (String) e.get("variable"))
                        .collect(Collectors.toSet());

                    Set<String> nonInputVarNames = netVariables.stream()
                        .filter(v -> !"net_input".equals(v.get("scope")))
                        .map(v -> v.get("name"))
                        .collect(Collectors.toSet());
                    List<String> orphanProducers = nonInputVarNames.stream()
                        .filter(name -> !consumedByTasks.contains(name))
                        .collect(Collectors.toList());

                    Set<String> netInputNames = netVariables.stream()
                        .filter(v -> "net_input".equals(v.get("scope")))
                        .map(v -> v.get("name"))
                        .collect(Collectors.toSet());
                    Set<String> allProduced = new HashSet<>(netInputNames);
                    allProduced.addAll(producedByTasks);
                    List<String> danglingConsumers = consumedByTasks.stream()
                        .filter(name -> !allProduced.contains(name))
                        .collect(Collectors.toList());

                    List<String> dataFlowIssues = new ArrayList<>();
                    for (String v : orphanProducers) {
                        dataFlowIssues.add(
                            "Variable '" + v + "' is declared but never consumed by any task");
                    }
                    for (String v : danglingConsumers) {
                        dataFlowIssues.add(
                            "Variable '" + v + "' is consumed by a task but never produced");
                    }

                    // Step 6: Filter by variableName if given
                    List<Map<String, Object>> filteredEdges = edges;
                    if (focusVar != null && !focusVar.isBlank()) {
                        filteredEdges = edges.stream()
                            .filter(e -> focusVar.equals(e.get("variable")))
                            .collect(Collectors.toList());
                        Set<String> activeNodeIds = new HashSet<>();
                        filteredEdges.forEach(e -> {
                            activeNodeIds.add((String) e.get("from_id"));
                            activeNodeIds.add((String) e.get("to_id"));
                        });
                        nodes = nodes.stream()
                            .filter(n -> activeNodeIds.contains(n.get("id")))
                            .collect(Collectors.toList());
                    }

                    // Step 7: Build result
                    long elapsed = System.currentTimeMillis() - startMs;
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("spec_id", specId);
                    result.put("spec_version", specVersion);
                    result.put("focus_variable", focusVar);
                    result.put("net_variables", netVariables);
                    result.put("nodes", nodes);
                    result.put("edges", filteredEdges);
                    result.put("orphan_producers", orphanProducers);
                    result.put("dangling_consumers", danglingConsumers);
                    result.put("data_flow_issues", dataFlowIssues);
                    result.put("total_mappings", edges.size());
                    result.put("analysis_time_ms", elapsed);

                    return textResult(new ObjectMapper()
                        .writerWithDefaultPrettyPrinter().writeValueAsString(result));

                } catch (Exception e) {
                    return errorResult("Error analyzing data lineage: " + e.getMessage());
                }
            }
        );
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Parse XML string into a Document with security features enabled.
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
     * Extract text content from child element (tries both namespaced and non-namespaced).
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
     * Process a mapping container (startingMappings, completedMappings, enablementMappings).
     * Extracts variable names and XQuery expressions from mapping elements.
     */
    private static void processMapping(Element task, String taskId,
            String mappingContainerTag, String mappingType,
            String fromId, String toId, List<Map<String, Object>> edges) {

        NodeList containers = task.getElementsByTagNameNS(YAWL_NS, mappingContainerTag);
        if (containers.getLength() == 0) {
            containers = task.getElementsByTagName(mappingContainerTag);
        }

        for (int c = 0; c < containers.getLength(); c++) {
            Element container = (Element) containers.item(c);
            NodeList mappings = container.getElementsByTagName("mapping");
            if (mappings.getLength() == 0) {
                mappings = container.getElementsByTagNameNS(YAWL_NS, "mapping");
            }

            for (int m = 0; m < mappings.getLength(); m++) {
                Element mapping = (Element) mappings.item(m);

                // Get expression/query attribute
                String expr = null;
                NodeList exprList = mapping.getElementsByTagName("expression");
                if (exprList.getLength() == 0) {
                    exprList = mapping.getElementsByTagNameNS(YAWL_NS, "expression");
                }
                if (exprList.getLength() > 0) {
                    expr = ((Element) exprList.item(0)).getAttribute("query");
                }

                // Get mapsTo text (the variable name)
                String mapsTo = extractChildText(mapping, "mapsTo");
                if (mapsTo == null || mapsTo.isBlank()) {
                    continue;
                }

                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("from_id", fromId);
                edge.put("to_id", toId);
                edge.put("variable", mapsTo);
                if (expr != null && !expr.isBlank()) {
                    edge.put("xquery_expr", expr);
                }
                edge.put("mapping_type", mappingType);
                edges.add(edge);
            }
        }
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
}
