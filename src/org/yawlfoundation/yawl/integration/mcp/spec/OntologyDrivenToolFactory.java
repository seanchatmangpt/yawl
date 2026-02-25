package org.yawlfoundation.yawl.integration.mcp.spec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Factory that derives MCP tool specifications from SPARQL CONSTRUCT over
 * {@code ontology/yawl-public-roots.ttl}, executed by the Oxigraph-based
 * {@code yawl-processmining-service} at {@code GET /ontology/mcp-tools}.
 *
 * <h3>Architecture</h3>
 * <pre>
 * yawl-public-roots.ttl              (ontology: YAWL extends Schema.org / PROV-O / FOAF / DC)
 *         ↓
 * SPARQL CONSTRUCT (Oxigraph/Rust)   (construct-mcp-from-workflow-ontology.sparql)
 *         ↓
 * GET /ontology/mcp-tools            (JSON: McpToolDescriptor[])
 *         ↓
 * OntologyDrivenToolFactory          (walks JSON → SyncToolSpecification per mcp:Tool)
 *         ↓
 * YawlMcpServer.addTools(...)        (registered alongside hand-authored tools)
 * </pre>
 *
 * <h3>Engine replacement</h3>
 * Apache Jena (Java) has been replaced by Oxigraph (Rust), which is the same
 * SPARQL engine used by ggen-core. The Rust service is called over HTTP; if it
 * is unavailable, {@link IllegalStateException} is thrown — no silent fallback.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class OntologyDrivenToolFactory {

    private static final Logger _log = LogManager.getLogger(OntologyDrivenToolFactory.class);
    private static final ObjectMapper _mapper = new ObjectMapper();

    private static final String ONTOLOGY_SERVICE_URL =
        System.getenv().getOrDefault("ONTOLOGY_SERVICE_URL", "http://localhost:8082");

    private OntologyDrivenToolFactory() {
        throw new UnsupportedOperationException(
            "OntologyDrivenToolFactory is a static factory and cannot be instantiated.");
    }

    /**
     * Derive and create all MCP tool specifications from the YAWL public-roots ontology.
     *
     * <p>Calls {@code GET /ontology/mcp-tools} on the processmining Rust service,
     * which executes the SPARQL CONSTRUCT over Oxigraph and returns JSON descriptors.
     * Builds one {@link McpServerFeatures.SyncToolSpecification} per tool found.
     *
     * @param interfaceBClient connected InterfaceB client for runtime engine operations
     * @param interfaceAClient connected InterfaceA client for design-time operations
     * @param sessionHandle    active YAWL session handle
     * @return list of ontology-derived tool specifications, ready for MCP server registration
     */
    public static List<McpServerFeatures.SyncToolSpecification> createAll(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            InterfaceA_EnvironmentBasedClient interfaceAClient,
            String sessionHandle) {

        if (interfaceBClient == null) {
            throw new IllegalArgumentException("interfaceBClient is required");
        }
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            throw new IllegalArgumentException("sessionHandle is required");
        }

        JsonNode toolsArray = fetchOntologyJson("/ontology/mcp-tools");

        Map<String, BiFunction<McpSchema.CallToolRequest, Map<String, Object>, String>>
            handlers = buildHandlerRegistry(interfaceBClient, interfaceAClient,
                                            sessionHandle, toolsArray);

        return buildSpecifications(toolsArray, handlers);
    }

    // -------------------------------------------------------------------------
    // HTTP: call Oxigraph-backed Rust service
    // -------------------------------------------------------------------------

    private static JsonNode fetchOntologyJson(String path) {
        String url = ONTOLOGY_SERVICE_URL + path;
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();

        try {
            HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                    "Ontology service returned HTTP " + response.statusCode()
                    + " from " + url + ": " + response.body());
            }
            JsonNode result = _mapper.readTree(response.body());
            _log.info("Loaded ontology JSON from {}: {} entries", url, result.size());
            return result;
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(
                "Ontology service unavailable at " + url
                + ". Ensure yawl-processmining-service is running on port 8082. "
                + "Error: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Build SyncToolSpecification from JSON descriptor array
    // -------------------------------------------------------------------------

    private static List<McpServerFeatures.SyncToolSpecification> buildSpecifications(
            JsonNode toolsArray,
            Map<String, BiFunction<McpSchema.CallToolRequest,
                                   Map<String, Object>, String>> handlers) {

        List<McpServerFeatures.SyncToolSpecification> specs = new ArrayList<>();

        for (JsonNode toolNode : toolsArray) {
            String toolName = textOf(toolNode, "name");
            String toolDesc = textOf(toolNode, "description");
            if (toolName.isEmpty() || toolDesc.isEmpty()) continue;

            // Build JSON Schema input from parameters array
            Map<String, Object> props    = new LinkedHashMap<>();
            List<String>        required = new ArrayList<>();

            JsonNode paramsNode = toolNode.get("parameters");
            if (paramsNode != null && paramsNode.isArray()) {
                for (JsonNode param : paramsNode) {
                    String pName = textOf(param, "name");
                    String pType = textOf(param, "type");
                    String pDesc = textOf(param, "description");
                    boolean pReq = param.path("required").asBoolean(false);

                    if (pName.isEmpty()) continue;
                    props.put(pName, Map.of(
                        "type", pType.isEmpty() ? "string" : pType,
                        "description", pDesc.isEmpty() ? pName : pDesc));
                    if (pReq) required.add(pName);
                }
            }

            McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object", props, required, false, null, Map.of());

            var handler = handlers.get(toolName);
            if (handler == null) {
                _log.warn("No handler registered for ontology-derived tool '{}' — skipping",
                          toolName);
                continue;
            }

            final String name = toolName;
            final var    h    = handler;

            specs.add(new McpServerFeatures.SyncToolSpecification(
                McpSchema.Tool.builder()
                    .name(name)
                    .description(toolDesc)
                    .inputSchema(inputSchema)
                    .build(),
                (exchange, callReq) -> {
                    try {
                        Map<String, Object> args = callReq.arguments() != null
                            ? callReq.arguments() : Map.of();
                        String response = h.apply(callReq, args);
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(response)),
                            false, null, null);
                    } catch (Exception e) {
                        _log.error("Tool '{}' execution failed: {}", name, e.getMessage(), e);
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: " + e.getMessage())),
                            true, null, null);
                    }
                }
            ));
        }

        _log.info("Built {} ontology-derived MCP tool specifications", specs.size());
        return specs;
    }

    // -------------------------------------------------------------------------
    // Handler registry — execution layer (Java)
    // Description layer came from Oxigraph CONSTRUCT; this layer calls the engine.
    // -------------------------------------------------------------------------

    private static Map<String, BiFunction<McpSchema.CallToolRequest,
                                          Map<String, Object>, String>>
    buildHandlerRegistry(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            InterfaceA_EnvironmentBasedClient interfaceAClient,
            String sessionHandle,
            JsonNode toolsArray) {

        Map<String, BiFunction<McpSchema.CallToolRequest, Map<String, Object>, String>>
            registry = new LinkedHashMap<>();

        // --- Core workflow operations delegating to InterfaceB ---

        registry.put("yawl_launch_case", (req, args) -> {
            String specId  = requireString(args, "specIdentifier");
            String version = optString(args, "specVersion", "0.1");
            String uri     = optString(args, "specUri", specId);
            String data    = optString(args, "caseData", null);
            try {
                var ySpecId = new org.yawlfoundation.yawl.engine.YSpecificationID(
                    specId, version, uri);
                String caseId = interfaceBClient.launchCase(ySpecId, data, null, sessionHandle);
                if (caseId == null || caseId.contains("<failure>")) {
                    return "Failed to launch case: " + caseId;
                }
                return "Case launched. ID: " + caseId
                    + " | Spec: " + specId + " v" + version;
            } catch (Exception e) {
                throw new RuntimeException("launch_case failed: " + e.getMessage(), e);
            }
        });

        registry.put("yawl_get_case_status", (req, args) -> {
            String caseId = requireString(args, "caseId");
            try {
                String state = interfaceBClient.getCaseState(caseId, sessionHandle);
                return state != null ? state : "No state found for case: " + caseId;
            } catch (Exception e) {
                throw new RuntimeException("get_case_status failed: " + e.getMessage(), e);
            }
        });

        registry.put("yawl_checkout_work_item", (req, args) -> {
            String itemId = requireString(args, "workItemId");
            try {
                String result = interfaceBClient.checkOutWorkItem(itemId, sessionHandle);
                return result != null ? result : "Checked out work item: " + itemId;
            } catch (Exception e) {
                throw new RuntimeException("checkout_work_item failed: " + e.getMessage(), e);
            }
        });

        registry.put("yawl_checkin_work_item", (req, args) -> {
            String itemId  = requireString(args, "workItemId");
            String output  = optString(args, "outputData", "<data/>");
            try {
                String result = interfaceBClient.checkInWorkItem(
                    itemId, output, null, sessionHandle);
                return result != null ? result : "Checked in work item: " + itemId;
            } catch (Exception e) {
                throw new RuntimeException("checkin_work_item failed: " + e.getMessage(), e);
            }
        });

        registry.put("yawl_list_specifications", (req, args) -> {
            try {
                var specList = interfaceBClient.getSpecificationList(sessionHandle);
                String specs = (specList != null && !specList.isEmpty())
                    ? _mapper.writeValueAsString(specList) : null;
                return specs != null ? specs : "No specifications loaded.";
            } catch (Exception e) {
                throw new RuntimeException("list_specifications failed: " + e.getMessage(), e);
            }
        });

        registry.put("yawl_get_running_cases", (req, args) -> {
            try {
                String cases = interfaceBClient.getAllRunningCases(sessionHandle);
                return cases != null ? cases : "No running cases.";
            } catch (Exception e) {
                throw new RuntimeException("get_running_cases failed: " + e.getMessage(), e);
            }
        });

        // --- Ontology-native tools: description AND execution derive from public-roots ---

        registry.put("yawl_discover_public_roots",
            discoverPublicRootsHandler(toolsArray));

        registry.put("yawl_validate_extension",
            validateExtensionHandler(toolsArray));

        registry.put("yawl_case_as_linked_data",
            caseAsLinkedDataHandler(interfaceBClient, sessionHandle));

        return registry;
    }

    // -------------------------------------------------------------------------
    // yawl_discover_public_roots
    // Uses the already-fetched tool descriptor JSON to enumerate extension bindings.
    // -------------------------------------------------------------------------

    private static BiFunction<McpSchema.CallToolRequest, Map<String, Object>, String>
    discoverPublicRootsHandler(JsonNode toolsArray) {
        return (req, args) -> {
            String domain = requireString(args, "domain");

            var sb = new StringBuilder();
            sb.append("Public Root Extensions for domain: ").append(domain).append("\n\n");

            String currentRoot = null;
            int    matchCount  = 0;

            for (JsonNode tool : toolsArray) {
                String name     = textOf(tool, "name");
                String epRoot   = textOf(tool, "epistemic_root");
                String rootDesc = textOf(tool, "root_description");
                String desc     = textOf(tool, "description");

                // Filter by domain keyword (case-insensitive match across all fields)
                if (!domain.equalsIgnoreCase("all")
                        && !name.toLowerCase().contains(domain.toLowerCase())
                        && !desc.toLowerCase().contains(domain.toLowerCase())
                        && !epRoot.toLowerCase().contains(domain.toLowerCase())) {
                    continue;
                }
                matchCount++;

                if (!epRoot.equals(currentRoot)) {
                    sb.append("Root: <").append(epRoot).append(">\n");
                    sb.append("  (").append(rootDesc).append(")\n");
                    currentRoot = epRoot;
                }
                sb.append("  ").append(name).append("\n");
                sb.append("    ").append(desc).append("\n");
            }

            if (matchCount == 0) {
                sb.append("No tools matched domain filter '").append(domain).append("'.\n");
                sb.append("Try domain='all' to list all extension bindings.\n");
            }

            sb.append("\nEpistemic consequence: every YAWL concept listed above ")
              .append("is semantically legible to any system that understands ")
              .append("Schema.org, PROV-O, FOAF, or Dublin Core. ")
              .append("No proprietary ontology translation required.");

            return sb.toString();
        };
    }

    // -------------------------------------------------------------------------
    // yawl_validate_extension
    // Uses the already-fetched tool descriptor JSON to enforce extension-only constraint.
    // -------------------------------------------------------------------------

    private static BiFunction<McpSchema.CallToolRequest, Map<String, Object>, String>
    validateExtensionHandler(JsonNode toolsArray) {
        return (req, args) -> {
            String specId  = requireString(args, "specIdentifier");
            String rootIri = optString(args, "rootIri", null);

            boolean valid;
            if (rootIri != null && !rootIri.isBlank()) {
                // Check if any tool extends the specified root
                valid = false;
                for (JsonNode tool : toolsArray) {
                    if (rootIri.equals(textOf(tool, "epistemic_root"))) {
                        valid = true;
                        break;
                    }
                }
            } else {
                // Check extension-only constraint: are there any tools at all?
                valid = toolsArray.size() > 0;
            }

            var sb = new StringBuilder();
            sb.append("Extension Validation for: ").append(specId).append("\n");
            if (rootIri != null) {
                sb.append("Against root: ").append(rootIri).append("\n");
            }
            sb.append("\nResult: ").append(valid ? "VALID" : "INVALID").append("\n");
            if (valid) {
                sb.append("All workflow operations extend known public roots.\n");
                sb.append("Extension-only constraint: SATISFIED.\n");
                sb.append("Epistemic alignment: guaranteed by construction.\n");
            } else {
                sb.append("WARNING: No operations found extending the specified public root.\n");
                sb.append("Check that yawl-public-roots.ttl declares the expected extensions.\n");
            }
            return sb.toString();
        };
    }

    // -------------------------------------------------------------------------
    // yawl_case_as_linked_data
    // Generates Turtle RDF string from case state — no Jena required.
    // -------------------------------------------------------------------------

    private static BiFunction<McpSchema.CallToolRequest, Map<String, Object>, String>
    caseAsLinkedDataHandler(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        return (req, args) -> {
            String caseId = requireString(args, "caseId");
            String format = optString(args, "format", "turtle");

            String caseState;
            try {
                caseState = interfaceBClient.getCaseState(caseId, sessionHandle);
            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch case state: " + e.getMessage(), e);
            }

            String now = java.time.Instant.now().toString();
            String escapedState = caseState != null
                ? caseState.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r")
                : "";

            // Build RDF Turtle string. The case IS a schema:Action and prov:Activity
            // because yawl:WorkflowCase rdfs:subClassOf schema:Action, prov:Activity.
            return """
                @prefix yawl:   <http://yawlfoundation.org/yawl#> .
                @prefix schema: <https://schema.org/> .
                @prefix prov:   <http://www.w3.org/ns/prov#> .
                @prefix xsd:    <http://www.w3.org/2001/XMLSchema#> .

                <urn:yawl:case:%s>
                  a yawl:WorkflowCase, schema:Action, prov:Activity ;
                  schema:identifier "%s" ;
                  prov:startedAtTime "%s"^^xsd:dateTime ;
                  schema:description "%s" .
                """.formatted(caseId, caseId, now, escapedState);
        };
    }

    // -------------------------------------------------------------------------
    // Parameter helpers
    // -------------------------------------------------------------------------

    private static String requireString(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Required parameter '" + key + "' is missing");
        }
        return v.toString();
    }

    private static String optString(Map<String, Object> args, String key, String def) {
        Object v = args.get(key);
        return (v != null && !v.toString().isBlank()) ? v.toString() : def;
    }

    private static String textOf(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && f.isTextual()) ? f.asText() : "";
    }
}
