/*
 * Copyright (c) 2024 YAWL Foundation. All rights reserved.
 */
package org.yawlfoundation.yawl.integration.mcp.resource;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
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
import java.util.logging.Logger;

/**
 * MCP resource template for live Petri-net visualization using Mermaid flowcharts.
 *
 * <p>Creates a resource template that renders the current token marking of a running YAWL case
 * as an interactive Mermaid diagram. The diagram shows:
 * <ul>
 *   <li>All tasks (rectangles) and conditions (circles) from the workflow net</li>
 *   <li>Flow edges between all connected elements</li>
 *   <li>Active tasks (with live work items) highlighted in orange</li>
 *   <li>Input condition highlighted in green</li>
 *   <li>Output condition highlighted in blue</li>
 * </ul>
 *
 * <p>Resource URI: {@code yawl://cases/{caseId}/mermaid}
 *
 * <p>The diagram is rendered as Markdown containing a Mermaid flowchart block.
 * This allows direct embedding in Markdown documents and rendering by Markdown viewers
 * that support Mermaid (e.g., GitHub, GitLab, Confluence).
 *
 * <p>The visualization is grounded in formal Petri net semantics: every node and edge
 * corresponds to elements in the workflow specification, and active nodes reflect
 * the current token marking from live work items.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class MermaidStateResource {

    private static final Logger LOGGER = Logger.getLogger(MermaidStateResource.class.getName());

    /** YAWL XML namespace for DOM parsing. */
    private static final String YAWL_NS = "http://www.yawlfoundation.org/yawlschema";

    private MermaidStateResource() {
        throw new UnsupportedOperationException(
            "MermaidStateResource is a static factory class and cannot be instantiated");
    }

    /**
     * Creates the MCP resource template for live Petri-net Mermaid visualization.
     *
     * <p>URI pattern: {@code yawl://cases/{caseId}/mermaid}
     *
     * <p>When invoked, this template:
     * <ol>
     *   <li>Extracts the caseId from the URI</li>
     *   <li>Fetches the workflow specification for that case</li>
     *   <li>Fetches live work items for that case</li>
     *   <li>Parses the specification to extract tasks, conditions, and flow edges</li>
     *   <li>Determines which tasks are currently active (have live work items)</li>
     *   <li>Generates a Mermaid flowchart diagram</li>
     *   <li>Returns the diagram as Markdown with syntax highlighting</li>
     * </ol>
     *
     * @param interfaceBClient the YAWL InterfaceB client connected to the engine
     * @param sessionHandle the authenticated YAWL session handle
     * @return sync resource template specification for MCP registration
     * @throws IllegalArgumentException if client or sessionHandle is null/empty
     */
    public static McpServerFeatures.SyncResourceTemplateSpecification create(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        if (interfaceBClient == null) {
            throw new IllegalArgumentException(
                "InterfaceB_EnvironmentBasedClient is required to create Mermaid state resource");
        }
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            throw new IllegalArgumentException(
                "A valid YAWL session handle is required to create Mermaid state resource");
        }

        McpSchema.ResourceTemplate template = new McpSchema.ResourceTemplate(
            "yawl://cases/{caseId}/mermaid",
            "case_petrinet_mermaid",
            null,
            "Real-time Mermaid flowchart of the Petri net token marking for a running case. " +
            "Active tasks are highlighted orange. Input/output conditions highlighted " +
            "green/blue. Zero inference tokens — rendered from formal workflow semantics.",
            "text/markdown",
            null,  // metadata
            null   // comment
        );

        return new McpServerFeatures.SyncResourceTemplateSpecification(template, (exchange, request) -> {
            try {
                String caseId = extractCaseIdFromUri(request.uri(), "yawl://cases/");
                if (caseId == null || caseId.isEmpty()) {
                    throw new IllegalArgumentException(
                        "Case ID is required in the URI (e.g. yawl://cases/42/mermaid)");
                }

                // Remove any trailing path segments (e.g. /mermaid)
                if (caseId.contains("/")) {
                    caseId = caseId.substring(0, caseId.indexOf('/'));
                }

                // Fetch specification for this case
                String specXml = interfaceBClient.getSpecificationForCase(caseId, sessionHandle);
                if (specXml == null || specXml.isEmpty()) {
                    String errorMarkdown = buildErrorMarkdown(caseId, "Specification not found");
                    return new McpSchema.ReadResourceResult(List.of(
                        new McpSchema.TextResourceContents(
                            request.uri(), "text/markdown", errorMarkdown)
                    ));
                }

                // Fetch live work items for this case
                List<WorkItemRecord> workItems = interfaceBClient.getWorkItemsForCase(caseId, sessionHandle);
                Set<String> activeTasks = extractActiveTaskIds(workItems);

                // Parse specification to extract net structure
                NetStructure netStructure = parseNetStructure(specXml);

                // Generate Mermaid diagram
                String mermaidDiagram = renderMermaidDiagram(netStructure, activeTasks, caseId);

                // Wrap in Markdown code block
                String markdown = "```mermaid\n" + mermaidDiagram + "\n```";

                return new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(
                        request.uri(), "text/markdown", markdown)
                ));

            } catch (Exception e) {
                LOGGER.warning("Failed to generate Mermaid diagram for case: " + e.getMessage());
                String errorMarkdown = buildErrorMarkdown(
                    extractCaseIdFromUri(request.uri(), "yawl://cases/"),
                    e.getMessage());
                return new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(
                        request.uri(), "text/markdown", errorMarkdown)
                ));
            }
        });
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    /**
     * Extracts active task IDs from a list of work item records.
     *
     * @param workItems list of work item records (may be null)
     * @return set of task IDs that have live work items
     */
    private static Set<String> extractActiveTaskIds(List<WorkItemRecord> workItems) {
        Set<String> activeTasks = new HashSet<>();
        if (workItems != null) {
            for (WorkItemRecord wir : workItems) {
                String taskId = wir.getTaskID();
                if (taskId != null && !taskId.isEmpty()) {
                    activeTasks.add(taskId);
                }
            }
        }
        return activeTasks;
    }

    /**
     * Parses the YAWL specification XML to extract net structure (tasks, conditions, flows).
     *
     * @param specXml YAWL specification XML
     * @return NetStructure with tasks, conditions, and flow edges
     * @throws RuntimeException if XML parsing fails
     */
    private static NetStructure parseNetStructure(String specXml) {
        try {
            Document doc = parseXml(specXml);
            return extractNetStructure(doc);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse YAWL specification: " + e.getMessage(), e);
        }
    }

    /**
     * Parses XML string into a DOM Document.
     *
     * @param xml XML string
     * @return parsed Document
     * @throws Exception if parsing fails
     */
    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Disable external entity resolution to prevent XXE
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Extracts the root net structure from a parsed YAWL specification document.
     *
     * @param doc parsed YAWL specification Document
     * @return NetStructure with all tasks, conditions, and flows
     */
    private static NetStructure extractNetStructure(Document doc) {
        NetStructure structure = new NetStructure();

        // Find root net
        Element rootNet = findRootNet(doc);
        if (rootNet == null) {
            return structure;
        }

        // Extract all tasks
        extractTasks(rootNet, structure);

        // Extract all conditions (input, output, and regular)
        extractConditions(rootNet, structure);

        // Extract all flow edges
        extractFlows(rootNet, structure);

        return structure;
    }

    /**
     * Finds the root net element in the specification.
     *
     * @param doc parsed YAWL specification Document
     * @return root net Element, or null if not found
     */
    private static Element findRootNet(Document doc) {
        // Try YAWL-namespaced decomposition elements first
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
     * Extracts all tasks from the root net.
     *
     * @param rootNet root net Element
     * @param structure NetStructure to populate
     */
    private static void extractTasks(Element rootNet, NetStructure structure) {
        NodeList taskNodes = rootNet.getElementsByTagNameNS(YAWL_NS, "task");
        if (taskNodes.getLength() == 0) {
            taskNodes = rootNet.getElementsByTagName("task");
        }

        for (int i = 0; i < taskNodes.getLength(); i++) {
            Element taskEl = (Element) taskNodes.item(i);
            String taskId = taskEl.getAttribute("id");
            if (taskId == null || taskId.isEmpty()) {
                continue;
            }

            String taskName = extractChildText(taskEl, "name");
            if (taskName == null || taskName.isEmpty()) {
                taskName = taskId;
            }

            structure.addTask(taskId, taskName);
        }
    }

    /**
     * Extracts all conditions (input, output, and regular) from the root net.
     *
     * @param rootNet root net Element
     * @param structure NetStructure to populate
     */
    private static void extractConditions(Element rootNet, NetStructure structure) {
        // Input condition
        NodeList inputCondNodes = rootNet.getElementsByTagNameNS(YAWL_NS, "inputCondition");
        if (inputCondNodes.getLength() == 0) {
            inputCondNodes = rootNet.getElementsByTagName("inputCondition");
        }
        if (inputCondNodes.getLength() > 0) {
            Element condEl = (Element) inputCondNodes.item(0);
            String condId = condEl.getAttribute("id");
            if (condId == null || condId.isEmpty()) {
                condId = "InputCondition";
            }
            structure.addInputCondition(condId);
        }

        // Output condition
        NodeList outputCondNodes = rootNet.getElementsByTagNameNS(YAWL_NS, "outputCondition");
        if (outputCondNodes.getLength() == 0) {
            outputCondNodes = rootNet.getElementsByTagName("outputCondition");
        }
        if (outputCondNodes.getLength() > 0) {
            Element condEl = (Element) outputCondNodes.item(0);
            String condId = condEl.getAttribute("id");
            if (condId == null || condId.isEmpty()) {
                condId = "OutputCondition";
            }
            structure.addOutputCondition(condId);
        }

        // Regular conditions
        NodeList condNodes = rootNet.getElementsByTagNameNS(YAWL_NS, "condition");
        if (condNodes.getLength() == 0) {
            condNodes = rootNet.getElementsByTagName("condition");
        }
        for (int i = 0; i < condNodes.getLength(); i++) {
            Element condEl = (Element) condNodes.item(i);
            String condId = condEl.getAttribute("id");
            if (condId == null || condId.isEmpty()) {
                continue;
            }
            structure.addCondition(condId);
        }
    }

    /**
     * Extracts all flow edges (flowsInto) from the root net.
     *
     * @param rootNet root net Element
     * @param structure NetStructure to populate
     */
    private static void extractFlows(Element rootNet, NetStructure structure) {
        // Look for elements with flowsInto children (tasks and conditions)
        NodeList allElements = rootNet.getElementsByTagNameNS(YAWL_NS, "flowsInto");
        if (allElements.getLength() == 0) {
            allElements = rootNet.getElementsByTagName("flowsInto");
        }

        for (int i = 0; i < allElements.getLength(); i++) {
            Element flowsIntoEl = (Element) allElements.item(i);

            // The parent of flowsInto is the source element
            Element sourceEl = (Element) flowsIntoEl.getParentNode();
            String sourceId = sourceEl.getAttribute("id");
            if (sourceId == null || sourceId.isEmpty()) {
                continue;
            }

            // Look for nextElementRef inside flowsInto
            NodeList nextRefNodes = flowsIntoEl.getElementsByTagNameNS(YAWL_NS, "nextElementRef");
            if (nextRefNodes.getLength() == 0) {
                nextRefNodes = flowsIntoEl.getElementsByTagName("nextElementRef");
            }

            for (int j = 0; j < nextRefNodes.getLength(); j++) {
                Element nextRefEl = (Element) nextRefNodes.item(j);
                String targetId = nextRefEl.getAttribute("id");
                if (targetId != null && !targetId.isEmpty()) {
                    structure.addFlow(sourceId, targetId);
                }
            }
        }
    }

    /**
     * Extracts text content of a child element by tag name.
     *
     * @param parent parent Element
     * @param tagName tag name to search for
     * @return text content of the element, or null if not found
     */
    private static String extractChildText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagNameNS(YAWL_NS, tagName);
        if (nodes.getLength() == 0) {
            nodes = parent.getElementsByTagName(tagName);
        }
        if (nodes.getLength() > 0) {
            String text = nodes.item(0).getTextContent();
            if (text != null && !text.isEmpty()) {
                return text.trim();
            }
        }
        return null;
    }

    /**
     * Generates a Mermaid flowchart diagram representing the Petri net with current token marking.
     *
     * @param structure net structure with tasks, conditions, and flows
     * @param activeTasks set of task IDs that currently have tokens (live work items)
     * @param caseId the case ID (for context in the diagram)
     * @return Mermaid flowchart diagram string
     */
    private static String renderMermaidDiagram(NetStructure structure, Set<String> activeTasks, String caseId) {
        StringBuilder sb = new StringBuilder();

        // Mermaid flowchart header
        sb.append("flowchart TD\n");

        // Define all task nodes (rectangles)
        for (Map.Entry<String, String> task : structure.tasks.entrySet()) {
            String taskId = task.getKey();
            String taskName = task.getValue();
            sb.append("    ").append(sanitizeId(taskId))
                .append("[").append(sanitizeLabel(taskName)).append("]\n");
        }

        // Define all condition nodes (circles)
        for (String condId : structure.conditions) {
            sb.append("    ").append(sanitizeId(condId))
                .append("((").append(sanitizeLabel(condId)).append("))\n");
        }

        // Define input condition (special styling)
        if (structure.inputConditionId != null) {
            sb.append("    ").append(sanitizeId(structure.inputConditionId))
                .append("((Input))\n");
        }

        // Define output condition (special styling)
        if (structure.outputConditionId != null) {
            sb.append("    ").append(sanitizeId(structure.outputConditionId))
                .append("((Output))\n");
        }

        // Add all flow edges
        for (NetStructure.Flow flow : structure.flows) {
            sb.append("    ").append(sanitizeId(flow.sourceId))
                .append(" --> ").append(sanitizeId(flow.targetId)).append("\n");
        }

        // Add styling for active tasks
        for (String activeTaskId : activeTasks) {
            if (structure.tasks.containsKey(activeTaskId)) {
                sb.append("    style ").append(sanitizeId(activeTaskId))
                    .append(" fill:#f90,stroke:#333,stroke-width:2px\n");
            }
        }

        // Add styling for input and output conditions
        if (structure.inputConditionId != null) {
            sb.append("    style ").append(sanitizeId(structure.inputConditionId))
                .append(" fill:#0f0,stroke:#333,stroke-width:2px\n");
        }
        if (structure.outputConditionId != null) {
            sb.append("    style ").append(sanitizeId(structure.outputConditionId))
                .append(" fill:#00f,stroke:#333,stroke-width:2px\n");
        }

        return sb.toString();
    }

    /**
     * Sanitizes an ID to be safe for use in Mermaid diagrams.
     * Removes special characters and ensures the ID is valid.
     *
     * @param id the ID to sanitize
     * @return sanitized ID suitable for Mermaid
     */
    private static String sanitizeId(String id) {
        if (id == null || id.isEmpty()) {
            return "UNNAMED";
        }
        // Replace non-alphanumeric characters with underscores
        return id.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /**
     * Sanitizes a label for display in Mermaid diagrams.
     * Escapes special characters and truncates if too long.
     *
     * @param label the label to sanitize
     * @return sanitized label suitable for Mermaid
     */
    private static String sanitizeLabel(String label) {
        if (label == null || label.isEmpty()) {
            return "Unnamed";
        }
        // Escape special characters
        String escaped = label
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ");
        // Truncate to reasonable length
        if (escaped.length() > 50) {
            escaped = escaped.substring(0, 47) + "...";
        }
        return escaped;
    }

    /**
     * Builds an error Mermaid diagram to display when specification parsing fails.
     *
     * @param caseId the case ID
     * @param errorMessage the error message
     * @return Mermaid diagram string showing the error
     */
    private static String buildErrorMarkdown(String caseId, String errorMessage) {
        if (caseId == null) {
            caseId = "UNKNOWN";
        }
        String sanitizedError = errorMessage == null ? "Unknown error" : errorMessage
            .replace("\"", "'")
            .replace("\n", " ");
        String diagram = "```mermaid\nflowchart TD\n" +
            "    ERR[\"Case " + sanitizeId(caseId) + " not found or parse error: " + sanitizeLabel(sanitizedError) + "\"]\n" +
            "    style ERR fill:#f00,stroke:#333,stroke-width:2px\n" +
            "```";
        return diagram;
    }

    /**
     * Extracts a case ID from a case URI, stripping any trailing path segments.
     *
     * @param uri the full URI (e.g. "yawl://cases/42" or "yawl://cases/42/mermaid")
     * @param prefix the URI prefix to strip (e.g. "yawl://cases/")
     * @return the extracted case ID, or null if not parseable
     */
    private static String extractCaseIdFromUri(String uri, String prefix) {
        if (uri == null || !uri.startsWith(prefix)) {
            return null;
        }
        String remainder = uri.substring(prefix.length());
        // Strip trailing path segments if present
        int slashIdx = remainder.indexOf('/');
        if (slashIdx > 0) {
            return remainder.substring(0, slashIdx);
        }
        return remainder;
    }

    // =========================================================================
    // Net Structure Model
    // =========================================================================

    /**
     * Internal model representing the structure of a YAWL workflow net.
     * Contains tasks, conditions, and flow edges.
     */
    private static class NetStructure {
        Map<String, String> tasks = new LinkedHashMap<>();  // taskId -> taskName
        Set<String> conditions = new HashSet<>();
        String inputConditionId;
        String outputConditionId;
        List<Flow> flows = new ArrayList<>();

        void addTask(String id, String name) {
            tasks.put(id, name);
        }

        void addCondition(String id) {
            conditions.add(id);
        }

        void addInputCondition(String id) {
            this.inputConditionId = id;
            conditions.add(id);
        }

        void addOutputCondition(String id) {
            this.outputConditionId = id;
            conditions.add(id);
        }

        void addFlow(String sourceId, String targetId) {
            flows.add(new Flow(sourceId, targetId));
        }

        static class Flow {
            String sourceId;
            String targetId;

            Flow(String sourceId, String targetId) {
                this.sourceId = sourceId;
                this.targetId = targetId;
            }
        }
    }
}
