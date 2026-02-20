package org.yawlfoundation.yawl.integration.mcp.resource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.yawlfoundation.yawl.engine.interfce.SpecificationData;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * YAWL Resource Provider for MCP SDK v1 (0.18.0+).
 *
 * Static factory class that creates MCP resource and resource template specifications
 * backed by real YAWL engine calls via InterfaceB. Resources expose read-only access
 * to workflow specifications, running cases, and work items.
 *
 * Implements MCP 2025-11-25 specification with exchange-based handlers.
 *
 * Static Resources (fixed URIs):
 * - yawl://specifications - List all loaded workflow specifications
 * - yawl://cases - List all running workflow cases
 * - yawl://workitems - List all live work items
 *
 * Resource Templates (parameterized URIs):
 * - yawl://cases/{caseId} - Case state and work items for a specific case
 * - yawl://cases/{caseId}/data - Case variable data for a specific case
 * - yawl://workitems/{workItemId} - Individual work item details
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class YawlResourceProvider {

    private static final Logger LOGGER = Logger.getLogger(YawlResourceProvider.class.getName());

    private YawlResourceProvider() {
        throw new UnsupportedOperationException(
            "YawlResourceProvider is a static factory class and cannot be instantiated");
    }

    /**
     * Creates all static resource specifications backed by real YAWL engine calls.
     *
     * @param client the YAWL InterfaceB client connected to the engine
     * @param sessionHandleSupplier supplier of the authenticated YAWL session handle
     * @return list of sync resource specifications for MCP registration
     */
    public static List<McpServerFeatures.SyncResourceSpecification> createAllResources(
            InterfaceB_EnvironmentBasedClient client, Supplier<String> sessionHandleSupplier) {
        if (client == null) {
            throw new IllegalArgumentException(
                "InterfaceB_EnvironmentBasedClient is required to create YAWL MCP resources");
        }
        if (sessionHandleSupplier == null) {
            throw new IllegalArgumentException(
                "sessionHandleSupplier is required - provide a Supplier that returns the active session handle");
        }

        List<McpServerFeatures.SyncResourceSpecification> resources = new ArrayList<>();
        resources.add(createSpecificationsResource(client, sessionHandleSupplier.get()));
        resources.add(createCasesResource(client, sessionHandleSupplier.get()));
        resources.add(createWorkItemsResource(client, sessionHandleSupplier.get()));
        return resources;
    }

    /**
     * Creates all resource template specifications backed by real YAWL engine calls.
     *
     * @param client the YAWL InterfaceB client connected to the engine
     * @param sessionHandle the authenticated YAWL session handle
     * @return list of sync resource template specifications for MCP registration
     */
    public static List<McpServerFeatures.SyncResourceTemplateSpecification> createAllResourceTemplates(
            InterfaceB_EnvironmentBasedClient client, Supplier<String> sessionHandleSupplier) {
        if (client == null) {
            throw new IllegalArgumentException(
                "InterfaceB_EnvironmentBasedClient is required to create YAWL MCP resource templates");
        }
        if (sessionHandleSupplier == null) {
            throw new IllegalArgumentException(
                "sessionHandleSupplier is required - provide a Supplier that returns the active session handle");
        }

        List<McpServerFeatures.SyncResourceTemplateSpecification> templates = new ArrayList<>();
        templates.add(createCaseDetailsTemplate(client, sessionHandleSupplier.get()));
        templates.add(createCaseDataTemplate(client, sessionHandleSupplier.get()));
        templates.add(createWorkItemDetailsTemplate(client, sessionHandleSupplier.get()));
        return templates;
    }

    // =========================================================================
    // Static Resource Specifications
    // =========================================================================

    /**
     * Creates the loaded specifications resource.
     * Calls client.getSpecificationList() and formats each spec as JSON.
     */
    private static McpServerFeatures.SyncResourceSpecification createSpecificationsResource(
            InterfaceB_EnvironmentBasedClient client, Supplier<String> sessionHandleSupplier) {

        McpSchema.Resource resource = new McpSchema.Resource(
            "yawl://specifications",
            "Loaded Specifications",
            "All workflow specifications currently loaded in the YAWL engine",
            "application/json",
            null
        );

        return new McpServerFeatures.SyncResourceSpecification(resource, (exchange, request) -> {
            try {
                List<SpecificationData> specs = client.getSpecificationList(sessionHandle);

                List<Map<String, Object>> specList = new ArrayList<>();
                if (specs != null) {
                    for (SpecificationData spec : specs) {
                        Map<String, Object> specMap = new LinkedHashMap<>();
                        specMap.put("identifier", spec.getID().getIdentifier());
                        specMap.put("version", spec.getID().getVersionAsString());
                        specMap.put("uri", spec.getID().getUri());
                        specMap.put("name", spec.getName());
                        specMap.put("status", spec.getStatus());
                        specMap.put("documentation", spec.getDocumentation());
                        specMap.put("rootNetId", spec.getRootNetID());
                        specList.add(specMap);
                    }
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("specifications", specList);
                result.put("count", specList.size());

                String json = toJson(result);
                return new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(
                        request.uri(), "application/json", json)
                ));
            } catch (IOException e) {
                throw new RuntimeException(
                    "Failed to read specifications from YAWL engine: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Creates the running cases resource.
     * Calls client.getAllRunningCases() and returns the XML response as JSON-wrapped text.
     */
    private static McpServerFeatures.SyncResourceSpecification createCasesResource(
            InterfaceB_EnvironmentBasedClient client, Supplier<String> sessionHandleSupplier) {

        McpSchema.Resource resource = new McpSchema.Resource(
            "yawl://cases",
            "Running Cases",
            "All currently running workflow cases in the YAWL engine",
            "application/json",
            null
        );

        return new McpServerFeatures.SyncResourceSpecification(resource, (exchange, request) -> {
            try {
                String casesXml = client.getAllRunningCases(sessionHandle);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("runningCasesXml", casesXml);
                result.put("source", "yawl-engine");

                String json = toJson(result);
                return new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(
                        request.uri(), "application/json", json)
                ));
            } catch (IOException e) {
                throw new RuntimeException(
                    "Failed to read running cases from YAWL engine: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Creates the live work items resource.
     * Calls client.getCompleteListOfLiveWorkItems() and formats each work item as JSON.
     */
    private static McpServerFeatures.SyncResourceSpecification createWorkItemsResource(
            InterfaceB_EnvironmentBasedClient client, Supplier<String> sessionHandleSupplier) {

        McpSchema.Resource resource = new McpSchema.Resource(
            "yawl://workitems",
            "Live Work Items",
            "All live work items across all running cases in the YAWL engine",
            "application/json",
            null
        );

        return new McpServerFeatures.SyncResourceSpecification(resource, (exchange, request) -> {
            try {
                List<WorkItemRecord> items = client.getCompleteListOfLiveWorkItems(sessionHandle);

                List<Map<String, Object>> itemList = new ArrayList<>();
                if (items != null) {
                    for (WorkItemRecord wir : items) {
                        Map<String, Object> itemMap = new LinkedHashMap<>();
                        itemMap.put("id", wir.getID());
                        itemMap.put("caseId", wir.getCaseID());
                        itemMap.put("taskId", wir.getTaskID());
                        itemMap.put("status", wir.getStatus());
                        itemMap.put("specIdentifier", wir.getSpecIdentifier());
                        itemMap.put("specUri", wir.getSpecURI());
                        itemMap.put("specVersion", wir.getSpecVersion());
                        if (wir.getEnablementTimeMs() != null) {
                            itemMap.put("enablementTimeMs", wir.getEnablementTimeMs());
                        }
                        if (wir.getStartTimeMs() != null) {
                            itemMap.put("startTimeMs", wir.getStartTimeMs());
                        }
                        itemList.add(itemMap);
                    }
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("workItems", itemList);
                result.put("count", itemList.size());

                String json = toJson(result);
                return new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(
                        request.uri(), "application/json", json)
                ));
            } catch (IOException e) {
                throw new RuntimeException(
                    "Failed to read live work items from YAWL engine: " + e.getMessage(), e);
            }
        });
    }

    // =========================================================================
    // Resource Template Specifications
    // =========================================================================

    /**
     * Creates the case details resource template.
     * Extracts caseId from the URI and calls client.getCaseState() and
     * client.getWorkItemsForCase() to return combined case information.
     */
    private static McpServerFeatures.SyncResourceTemplateSpecification createCaseDetailsTemplate(
            InterfaceB_EnvironmentBasedClient client, Supplier<String> sessionHandleSupplier) {

        McpSchema.ResourceTemplate template = new McpSchema.ResourceTemplate(
            "yawl://cases/{caseId}",
            "Case Details",
            null,
            "Case state and work items for a specific running case",
            "application/json",
            null
        );

        return new McpServerFeatures.SyncResourceTemplateSpecification(template, (exchange, request) -> {
            try {
                String caseId = extractCaseIdFromUri(request.uri(), "yawl://cases/");
                if (caseId == null || caseId.isEmpty()) {
                    throw new IllegalArgumentException(
                        "Case ID is required in the URI (e.g. yawl://cases/42)");
                }

                // Remove any trailing path segments (e.g. /data)
                if (caseId.contains("/")) {
                    caseId = caseId.substring(0, caseId.indexOf('/'));
                }

                String caseState = client.getCaseState(caseId, sessionHandleSupplier.get());

                List<WorkItemRecord> workItems = client.getWorkItemsForCase(caseId, sessionHandleSupplier.get());
                List<Map<String, Object>> itemList = new ArrayList<>();
                if (workItems != null) {
                    for (WorkItemRecord wir : workItems) {
                        Map<String, Object> itemMap = new LinkedHashMap<>();
                        itemMap.put("id", wir.getID());
                        itemMap.put("taskId", wir.getTaskID());
                        itemMap.put("status", wir.getStatus());
                        if (wir.getEnablementTimeMs() != null) {
                            itemMap.put("enablementTimeMs", wir.getEnablementTimeMs());
                        }
                        if (wir.getStartTimeMs() != null) {
                            itemMap.put("startTimeMs", wir.getStartTimeMs());
                        }
                        itemList.add(itemMap);
                    }
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("caseId", caseId);
                result.put("state", caseState);
                result.put("workItems", itemList);
                result.put("workItemCount", itemList.size());

                String json = toJson(result);
                return new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(
                        request.uri(), "application/json", json)
                ));
            } catch (IOException e) {
                throw new RuntimeException(
                    "Failed to read case details from YAWL engine: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Creates the case data resource template.
     * Extracts caseId from the URI and calls client.getCaseData() to return
     * the case's variable data.
     */
    private static McpServerFeatures.SyncResourceTemplateSpecification createCaseDataTemplate(
            InterfaceB_EnvironmentBasedClient client, Supplier<String> sessionHandleSupplier) {

        McpSchema.ResourceTemplate template = new McpSchema.ResourceTemplate(
            "yawl://cases/{caseId}/data",
            "Case Data",
            null,
            "Case variable data for a specific running case",
            "application/json",
            null
        );

        return new McpServerFeatures.SyncResourceTemplateSpecification(template, (exchange, request) -> {
            try {
                String uri = request.uri();
                String caseId = extractCaseIdFromDataUri(uri);
                if (caseId == null || caseId.isEmpty()) {
                    throw new IllegalArgumentException(
                        "Case ID is required in the URI (e.g. yawl://cases/42/data)");
                }

                String caseData = client.getCaseData(caseId, sessionHandleSupplier.get());

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("caseId", caseId);
                result.put("data", caseData);

                String json = toJson(result);
                return new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(
                        request.uri(), "application/json", json)
                ));
            } catch (IOException e) {
                throw new RuntimeException(
                    "Failed to read case data from YAWL engine: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Creates the work item details resource template.
     * Extracts workItemId from the URI and calls client.getWorkItem() to return
     * the full XML representation of the work item.
     */
    private static McpServerFeatures.SyncResourceTemplateSpecification createWorkItemDetailsTemplate(
            InterfaceB_EnvironmentBasedClient client, Supplier<String> sessionHandleSupplier) {

        McpSchema.ResourceTemplate template = new McpSchema.ResourceTemplate(
            "yawl://workitems/{workItemId}",
            "Work Item Details",
            null,
            "Detailed information about a specific work item",
            "application/json",
            null
        );

        return new McpServerFeatures.SyncResourceTemplateSpecification(template, (exchange, request) -> {
            try {
                String workItemId = extractIdFromUri(request.uri(), "yawl://workitems/");
                if (workItemId == null || workItemId.isEmpty()) {
                    throw new IllegalArgumentException(
                        "Work item ID is required in the URI (e.g. yawl://workitems/42:TaskA)");
                }

                String workItemXml = client.getWorkItem(workItemId, sessionHandleSupplier.get());

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("workItemId", workItemId);
                result.put("workItem", workItemXml);

                String json = toJson(result);
                return new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(
                        request.uri(), "application/json", json)
                ));
            } catch (IOException e) {
                throw new RuntimeException(
                    "Failed to read work item details from YAWL engine: " + e.getMessage(), e);
            }
        });
    }

    // =========================================================================
    // URI Parsing Helpers
    // =========================================================================

    /**
     * Extracts an ID segment from a URI by removing a known prefix.
     *
     * @param uri the full URI (e.g. "yawl://workitems/42:TaskA")
     * @param prefix the URI prefix to strip (e.g. "yawl://workitems/")
     * @return the extracted ID, or null if the URI does not start with the prefix
     */
    private static String extractIdFromUri(String uri, String prefix) {
        if (uri == null || !uri.startsWith(prefix)) {
            return null;
        }
        return uri.substring(prefix.length());
    }

    /**
     * Extracts a caseId from a case URI, stripping any trailing path segments.
     *
     * @param uri the full URI (e.g. "yawl://cases/42" or "yawl://cases/42/data")
     * @param prefix the URI prefix to strip (e.g. "yawl://cases/")
     * @return the extracted case ID
     */
    private static String extractCaseIdFromUri(String uri, String prefix) {
        String remainder = extractIdFromUri(uri, prefix);
        if (remainder == null) {
            return null;
        }
        // Strip trailing path segments if present
        int slashIdx = remainder.indexOf('/');
        if (slashIdx > 0) {
            return remainder.substring(0, slashIdx);
        }
        return remainder;
    }

    /**
     * Extracts a caseId from a case data URI (yawl://cases/{caseId}/data).
     *
     * @param uri the full URI (e.g. "yawl://cases/42/data")
     * @return the extracted case ID, or null if not parseable
     */
    private static String extractCaseIdFromDataUri(String uri) {
        String prefix = "yawl://cases/";
        if (uri == null || !uri.startsWith(prefix)) {
            return null;
        }
        String remainder = uri.substring(prefix.length());
        // Expected format: {caseId}/data
        int slashIdx = remainder.indexOf('/');
        if (slashIdx > 0) {
            return remainder.substring(0, slashIdx);
        }
        // Fall back to using the entire remainder if no /data suffix
        return remainder;
    }

    // =========================================================================
    // JSON Serialization
    // =========================================================================

    /**
     * Converts a map to a JSON string using manual serialization.
     * This avoids requiring an ObjectMapper dependency in this static factory class.
     *
     * @param map the data to serialize
     * @return JSON string representation
     */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private static String toJson(Map<String, Object> map) {
        try {
            return JSON_MAPPER.writeValueAsString(map);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize map to JSON", e);
        }
    }
}
