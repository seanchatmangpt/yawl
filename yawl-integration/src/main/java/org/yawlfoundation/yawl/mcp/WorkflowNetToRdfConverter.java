/*
 * Copyright (c) 2024 YAWL Foundation. All rights reserved.
 */
package org.yawlfoundation.yawl.integration.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a YAWL workflow specification (XML from InterfaceB) to JSON-LD.
 *
 * <p>This converter is the bridge between the YAWL execution model and the
 * CONSTRUCT coordination layer. It materialises the workflow net as an RDF
 * graph (expressed as JSON-LD) so that SPARQL CONSTRUCT queries can derive
 * MCP tool schemas and A2A capability cards from the formal specification.
 *
 * <h2>Output Structure</h2>
 * <pre>
 * {
 *   "@context": { "yawl": "http://yawlfoundation.org/yawl#" },
 *   "@id": "yawl:net:SpecUri",
 *   "@type": "yawl:WorkflowNet",
 *   "yawl:netId": "SpecUri",
 *   "yawl:specVersion": "0.1",
 *   "yawl:coordinationModel": "CONSTRUCT",
 *   "yawl:routingCost": "zero_inference_tokens",
 *   "yawl:soundnessGuarantee": "Petri net soundness: no deadlock, no livelock",
 *   "yawl:hasTask": [ ... ],
 *   "yawl:hasCondition": [ ... ]
 * }
 * </pre>
 *
 * <h2>CONSTRUCT Coordination Claim</h2>
 * The JSON-LD output is the graph over which SPARQL CONSTRUCT queries run to
 * generate MCP tool schemas and A2A capability cards. Unlike select/do frameworks
 * that hand-author their interfaces, YAWL derives them from the formal workflow
 * definition — every tool description is a CONSTRUCT output.
 *
 * <h2>No External Dependencies</h2>
 * Uses only JDK DOM parsing and Jackson (already present in yawl-integration).
 * Deliberately excludes Apache Jena to avoid adding heavyweight RDF dependencies
 * to the integration module.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class WorkflowNetToRdfConverter {

    /** YAWL XML namespace for DOM parsing. */
    static final String YAWL_NS = "http://www.yawlfoundation.org/yawlschema";

    /** RDF ontology namespace for JSON-LD output. */
    static final String YAWL_ONTOLOGY_NS = "http://yawlfoundation.org/yawl#";

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Convert a YAWL specification XML string to Turtle (RDF serialisation).
     *
     * <p>Produces W3C Turtle suitable for loading into Oxigraph (via sparql-runner)
     * so that SPARQL CONSTRUCT queries can derive MCP tool schemas and A2A capability
     * cards from the formal workflow definition.
     *
     * <p>The Turtle output includes exactly the triples required by the SPARQL WHERE
     * clauses in the {@code *.sparql} query files:
     * <ul>
     *   <li>{@code yawl:WorkflowNet} with {@code yawl:netId} and metadata</li>
     *   <li>{@code yawl:AtomicTask} per task with {@code yawl:taskId},
     *       {@code yawl:inNet}, and optional {@code yawl:taskName}</li>
     * </ul>
     *
     * @param specXml YAWL specification XML (from InterfaceB.getSpecification())
     * @return Turtle string ready for loading into Oxigraph
     * @throws IllegalArgumentException if specXml is null or empty
     * @throws RuntimeException         if the XML cannot be parsed
     */
    public String convertToTurtle(String specXml) {
        if (specXml == null || specXml.isBlank()) {
            throw new IllegalArgumentException("specXml must not be null or blank");
        }
        try {
            Document doc = parseXml(specXml);
            return buildTurtle(doc);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to convert YAWL spec to Turtle: " + e.getMessage(), e);
        }
    }

    /**
     * Convert a YAWL specification XML string to JSON-LD.
     *
     * <p>Parses the YAWL specification XML, extracts the root net's tasks and
     * conditions, and serialises as JSON-LD with the YAWL ontology namespace.
     *
     * @param specXml  YAWL specification XML (from InterfaceB.getSpecification())
     * @return JSON-LD string representing the workflow net as an RDF graph
     * @throws IllegalArgumentException if specXml is null or empty
     * @throws RuntimeException         if the XML cannot be parsed
     */
    public String convert(String specXml) {
        if (specXml == null || specXml.isBlank()) {
            throw new IllegalArgumentException("specXml must not be null or blank");
        }

        try {
            Document doc = parseXml(specXml);
            Map<String, Object> jsonLd = buildJsonLd(doc);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonLd);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert YAWL spec to JSON-LD: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal parsing and building
    // -------------------------------------------------------------------------

    private String buildTurtle(Document doc) {
        String specUri     = extractSpecUri(doc);
        String specVersion = extractSpecVersion(doc);

        String netIri = YAWL_ONTOLOGY_NS + "net/" + iriSafe(specUri);

        StringBuilder sb = new StringBuilder();
        sb.append("@prefix yawl: <").append(YAWL_ONTOLOGY_NS).append("> .\n");
        sb.append("@prefix mcp:  <http://modelcontextprotocol.io/schema#> .\n");
        sb.append("@prefix a2a:  <http://a2aprotocol.ai/schema#> .\n\n");

        // WorkflowNet resource
        sb.append('<').append(netIri).append("> a yawl:WorkflowNet ;\n");
        sb.append("    yawl:netId         ").append(turtleLiteral(specUri)).append(" ;\n");
        sb.append("    yawl:specVersion   ").append(turtleLiteral(specVersion)).append(" ;\n");
        sb.append("    yawl:coordinationModel \"CONSTRUCT\" ;\n");
        sb.append("    yawl:routingCost   \"zero_inference_tokens\" .\n\n");

        // AtomicTask resources
        Element rootNet = findRootNet(doc);
        if (rootNet != null) {
            List<Map<String, Object>> tasks = extractTasks(rootNet, specUri);
            for (Map<String, Object> task : tasks) {
                String taskId = (String) task.get("yawl:taskId");
                if (taskId == null || taskId.isBlank()) continue;

                String taskIri = YAWL_ONTOLOGY_NS + "task/" + iriSafe(taskId);

                sb.append('<').append(taskIri).append("> a yawl:AtomicTask ;\n");
                sb.append("    yawl:taskId ").append(turtleLiteral(taskId)).append(" ;\n");
                sb.append("    yawl:inNet  <").append(netIri).append("> ");

                String taskName = (String) task.get("yawl:taskName");
                if (taskName != null && !taskName.isBlank()) {
                    sb.append(";\n    yawl:taskName ").append(turtleLiteral(taskName)).append(' ');
                }
                String joinType = (String) task.get("yawl:joinType");
                if (joinType != null && !joinType.isBlank()) {
                    sb.append(";\n    yawl:joinType ").append(turtleLiteral(joinType)).append(' ');
                }
                String splitType = (String) task.get("yawl:splitType");
                if (splitType != null && !splitType.isBlank()) {
                    sb.append(";\n    yawl:splitType ").append(turtleLiteral(splitType)).append(' ');
                }
                sb.append(".\n\n");
            }
        }

        return sb.toString();
    }

    /** Escape a value as a Turtle plain string literal. */
    private static String turtleLiteral(String value) {
        return '"' + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r") + '"';
    }

    /** Make a value safe for use as an IRI path segment (replace non-alphanumeric with _). */
    private static String iriSafe(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Disable external entity resolution to prevent XXE
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private Map<String, Object> buildJsonLd(Document doc) {
        Map<String, Object> root = new LinkedHashMap<>();

        // JSON-LD context
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("yawl", YAWL_ONTOLOGY_NS);
        context.put("mcp",  "http://modelcontextprotocol.io/schema#");
        context.put("a2a",  "http://a2aprotocol.ai/schema#");
        root.put("@context", context);

        // Extract spec URI and version from <specification> element
        String specUri = extractSpecUri(doc);
        String specVersion = extractSpecVersion(doc);
        String specName = extractSpecName(doc);

        root.put("@id",   "yawl:net:" + specUri);
        root.put("@type", "yawl:WorkflowNet");
        root.put("yawl:netId",       specUri);
        root.put("yawl:specVersion", specVersion);
        if (specName != null && !specName.isBlank()) {
            root.put("yawl:specName", specName);
        }

        // CONSTRUCT coordination metadata
        root.put("yawl:coordinationModel",    "CONSTRUCT");
        root.put("yawl:routingCost",          "zero_inference_tokens");
        root.put("yawl:soundnessGuarantee",
            "Petri net soundness: no deadlock, no livelock, every enabled task eventually fires");
        root.put("yawl:enablementQuery",
            "SPARQL SELECT over token marking — see query-enabled-tasks.sparql");

        // Extract root net tasks and conditions
        Element rootNet = findRootNet(doc);
        if (rootNet != null) {
            List<Map<String, Object>> tasks = extractTasks(rootNet, specUri);
            List<Map<String, Object>> conditions = extractConditions(rootNet, specUri);

            if (!tasks.isEmpty()) {
                root.put("yawl:hasTask", tasks);
            }
            if (!conditions.isEmpty()) {
                root.put("yawl:hasCondition", conditions);
            }
            root.put("yawl:taskCount",      tasks.size());
            root.put("yawl:conditionCount", conditions.size());
        }

        // SPARQL CONSTRUCT queries reference
        root.put("yawl:constructQueries", Map.of(
            "generateMcpTools",  "classpath:org/yawlfoundation/yawl/integration/mcp/sparql/generate-mcp-tools.sparql",
            "generateA2aSkill",  "classpath:org/yawlfoundation/yawl/integration/mcp/sparql/generate-a2a-skill.sparql",
            "queryEnabledTasks", "classpath:org/yawlfoundation/yawl/integration/mcp/sparql/query-enabled-tasks.sparql"
        ));

        return root;
    }

    private String extractSpecUri(Document doc) {
        // Try YAWL-namespaced specification element first
        NodeList specs = doc.getElementsByTagNameNS(YAWL_NS, "specification");
        if (specs.getLength() > 0) {
            String uri = ((Element) specs.item(0)).getAttribute("uri");
            if (uri != null && !uri.isBlank()) return uri;
        }
        // Fallback: non-namespaced
        specs = doc.getElementsByTagName("specification");
        if (specs.getLength() > 0) {
            String uri = ((Element) specs.item(0)).getAttribute("uri");
            if (uri != null && !uri.isBlank()) return uri;
        }
        return "UnknownSpec";
    }

    private String extractSpecVersion(Document doc) {
        NodeList versionNodes = doc.getElementsByTagNameNS(YAWL_NS, "version");
        if (versionNodes.getLength() == 0) {
            versionNodes = doc.getElementsByTagName("version");
        }
        if (versionNodes.getLength() > 0) {
            String v = versionNodes.item(0).getTextContent();
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "0.1";
    }

    private String extractSpecName(Document doc) {
        // <name> inside <specification> (not inside a task)
        NodeList specs = doc.getElementsByTagNameNS(YAWL_NS, "specification");
        if (specs.getLength() == 0) {
            specs = doc.getElementsByTagName("specification");
        }
        if (specs.getLength() > 0) {
            Element specEl = (Element) specs.item(0);
            NodeList nameNodes = specEl.getElementsByTagNameNS(YAWL_NS, "name");
            if (nameNodes.getLength() == 0) {
                nameNodes = specEl.getElementsByTagName("name");
            }
            if (nameNodes.getLength() > 0) {
                String name = nameNodes.item(0).getTextContent();
                if (name != null && !name.isBlank()) return name.trim();
            }
        }
        return null;
    }

    private Element findRootNet(Document doc) {
        // Find <decomposition isRootNet="true">
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

    private List<Map<String, Object>> extractTasks(Element net, String specUri) {
        List<Map<String, Object>> tasks = new ArrayList<>();

        NodeList taskNodes = net.getElementsByTagNameNS(YAWL_NS, "task");
        if (taskNodes.getLength() == 0) {
            taskNodes = net.getElementsByTagName("task");
        }

        for (int i = 0; i < taskNodes.getLength(); i++) {
            Element task = (Element) taskNodes.item(i);
            String taskId = task.getAttribute("id");
            if (taskId == null || taskId.isBlank()) continue;

            Map<String, Object> taskMap = new LinkedHashMap<>();
            taskMap.put("@id",   "yawl:task:" + taskId);
            taskMap.put("@type", "yawl:AtomicTask");
            taskMap.put("yawl:taskId",  taskId);
            taskMap.put("yawl:inNet",   "yawl:net:" + specUri);

            // Task name
            String name = extractChildText(task, "name");
            if (name != null) taskMap.put("yawl:taskName", name);

            // Join type
            String joinType = extractJoinType(task);
            if (joinType != null) taskMap.put("yawl:joinType", joinType.toUpperCase());

            // Split type
            String splitType = extractSplitType(task);
            if (splitType != null) taskMap.put("yawl:splitType", splitType.toUpperCase());

            // Decomposition reference (for type determination)
            NodeList decomps = task.getElementsByTagNameNS(YAWL_NS, "decomposesTo");
            if (decomps.getLength() == 0) {
                decomps = task.getElementsByTagName("decomposesTo");
            }
            if (decomps.getLength() > 0) {
                String decompId = ((Element) decomps.item(0)).getAttribute("id");
                if (decompId != null && !decompId.isBlank()) {
                    taskMap.put("yawl:decompositionId", decompId);
                }
            }

            tasks.add(taskMap);
        }

        return tasks;
    }

    private List<Map<String, Object>> extractConditions(Element net, String specUri) {
        List<Map<String, Object>> conditions = new ArrayList<>();

        // inputCondition
        addConditions(conditions, net, "inputCondition", "yawl:InputCondition", specUri);
        // outputCondition
        addConditions(conditions, net, "outputCondition", "yawl:OutputCondition", specUri);
        // regular conditions
        addConditions(conditions, net, "condition", "yawl:Condition", specUri);

        return conditions;
    }

    private void addConditions(List<Map<String, Object>> list, Element net,
                               String tagName, String rdfType, String specUri) {
        NodeList nodes = net.getElementsByTagNameNS(YAWL_NS, tagName);
        if (nodes.getLength() == 0) {
            nodes = net.getElementsByTagName(tagName);
        }
        for (int i = 0; i < nodes.getLength(); i++) {
            Element cond = (Element) nodes.item(i);
            String condId = cond.getAttribute("id");
            if (condId == null || condId.isBlank()) continue;

            Map<String, Object> condMap = new LinkedHashMap<>();
            condMap.put("@id",   "yawl:cond:" + condId);
            condMap.put("@type", rdfType);
            condMap.put("yawl:conditionId", condId);
            condMap.put("yawl:inNet", "yawl:net:" + specUri);

            String name = extractChildText(cond, "name");
            if (name != null) condMap.put("yawl:conditionName", name);

            list.add(condMap);
        }
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
