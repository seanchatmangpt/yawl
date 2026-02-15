package org.yawlfoundation.yawl.integration.mcp.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.ReadResourceResult;
import io.modelcontextprotocol.spec.Resource;
import io.modelcontextprotocol.spec.ResourceContents;
import io.modelcontextprotocol.spec.SyncResourceSpecification;
import io.modelcontextprotocol.spec.TextResourceContents;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * YAWL Resource Specifications for MCP.
 *
 * Exposes YAWL data as MCP resources accessible via URI patterns.
 * Resources provide read-only access to workflow specifications,
 * running cases, and work items.
 *
 * URI Patterns:
 * - yawl://specifications - List all loaded specifications
 * - yawl://specifications/{id} - Get specific specification
 * - yawl://cases - List running cases
 * - yawl://cases/{id} - Get case details
 * - yawl://workitems - List live work items
 * - yawl://workitems/{id} - Get work item details
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlResourceProvider {

    private static final Logger LOGGER = Logger.getLogger(YawlResourceProvider.class.getName());

    private final InterfaceB_EnvironmentBasedClient client;
    private final String sessionHandle;
    private final ObjectMapper mapper;

    /**
     * Creates a new YAWL resource provider.
     *
     * @param client the YAWL InterfaceB client
     * @param sessionHandle the YAWL session handle
     * @param mapper the ObjectMapper for JSON processing
     */
    public YawlResourceProvider(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle,
            ObjectMapper mapper) {
        this.client = client;
        this.sessionHandle = sessionHandle;
        this.mapper = mapper;
    }

    /**
     * Gets all available resources.
     *
     * @return list of all resource specifications
     */
    public List<SyncResourceSpecification> getAllResources() {
        List<SyncResourceSpecification> resources = new ArrayList<>();

        resources.add(createSpecificationsResource());
        resources.add(createSpecificationByIdResource());
        resources.add(createCasesResource());
        resources.add(createCaseByIdResource());
        resources.add(createWorkItemsResource());
        resources.add(createWorkItemByIdResource());

        return resources;
    }

    /**
     * Creates the specifications list resource.
     */
    private SyncResourceSpecification createSpecificationsResource() {
        Resource resource = new Resource(
                "yawl://specifications",
                "YAWL Specifications",
                "All loaded workflow specifications",
                "application/json",
                null
        );

        return new SyncResourceSpecification(resource, (exchange, request) -> {
            try {
                var specs = client.getSpecificationList(sessionHandle);

                List<Map<String, Object>> specList = new ArrayList<>();
                for (var spec : specs) {
                    Map<String, Object> specMap = new HashMap<>();
                    specMap.put("id", spec.getID());
                    specMap.put("uri", spec.getUri());
                    specMap.put("version", spec.getVersion());
                    specMap.put("name", spec.getName());
                    specList.add(specMap);
                }

                String json = mapper.writeValueAsString(Map.of(
                        "specifications", specList,
                        "count", specList.size()
                ));

                List<ResourceContents> contents = List.of(
                        new TextResourceContents("yawl://specifications", json, "application/json")
                );

                return new ReadResourceResult(contents);
            } catch (IOException e) {
                LOGGER.warning("Failed to get specifications: " + e.getMessage());
                throw new RuntimeException("Failed to get specifications", e);
            }
        });
    }

    /**
     * Creates the specification by ID resource.
     */
    private SyncResourceSpecification createSpecificationByIdResource() {
        Resource resource = new Resource(
                "yawl://specifications/{id}",
                "YAWL Specification",
                "Get a specific workflow specification by ID",
                "application/json",
                null
        );

        return new SyncResourceSpecification(resource, (exchange, request) -> {
            try {
                String uri = request.uri();
                String specId = extractIdFromUri(uri, "yawl://specifications/");

                if (specId == null || specId.isEmpty()) {
                    throw new IllegalArgumentException("Specification ID required in URI");
                }

                YSpecificationID ySpecId = new YSpecificationID(specId);
                String specXml = client.getSpecification(ySpecId, sessionHandle);

                Map<String, Object> result = new HashMap<>();
                result.put("id", specId);
                result.put("specification", specXml);

                String json = mapper.writeValueAsString(result);

                List<ResourceContents> contents = List.of(
                        new TextResourceContents(uri, json, "application/json")
                );

                return new ReadResourceResult(contents);
            } catch (IOException e) {
                LOGGER.warning("Failed to get specification: " + e.getMessage());
                throw new RuntimeException("Failed to get specification", e);
            }
        });
    }

    /**
     * Creates the cases list resource.
     */
    private SyncResourceSpecification createCasesResource() {
        Resource resource = new Resource(
                "yawl://cases",
                "YAWL Running Cases",
                "All currently running workflow cases",
                "application/json",
                null
        );

        return new SyncResourceSpecification(resource, (exchange, request) -> {
            try {
                String casesXml = client.getAllRunningCases(sessionHandle);

                Map<String, Object> result = new HashMap<>();
                result.put("cases", casesXml);
                result.put("source", "yawl-engine");

                String json = mapper.writeValueAsString(result);

                List<ResourceContents> contents = List.of(
                        new TextResourceContents("yawl://cases", json, "application/json")
                );

                return new ReadResourceResult(contents);
            } catch (IOException e) {
                LOGGER.warning("Failed to get running cases: " + e.getMessage());
                throw new RuntimeException("Failed to get running cases", e);
            }
        });
    }

    /**
     * Creates the case by ID resource.
     */
    private SyncResourceSpecification createCaseByIdResource() {
        Resource resource = new Resource(
                "yawl://cases/{id}",
                "YAWL Case",
                "Get details for a specific case",
                "application/json",
                null
        );

        return new SyncResourceSpecification(resource, (exchange, request) -> {
            try {
                String uri = request.uri();
                String caseId = extractIdFromUri(uri, "yawl://cases/");

                if (caseId == null || caseId.isEmpty()) {
                    throw new IllegalArgumentException("Case ID required in URI");
                }

                String state = client.getCaseState(caseId, sessionHandle);
                String data = client.getCaseData(caseId, sessionHandle);
                List<WorkItemRecord> items = client.getWorkItemsForCase(caseId, sessionHandle);

                List<Map<String, Object>> itemList = new ArrayList<>();
                for (WorkItemRecord item : items) {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("id", item.getID());
                    itemMap.put("taskId", item.getTaskID());
                    itemMap.put("status", item.getStatus());
                    itemList.add(itemMap);
                }

                Map<String, Object> result = new HashMap<>();
                result.put("caseId", caseId);
                result.put("state", state);
                result.put("data", data);
                result.put("workItems", itemList);
                result.put("workItemCount", items.size());

                String json = mapper.writeValueAsString(result);

                List<ResourceContents> contents = List.of(
                        new TextResourceContents(uri, json, "application/json")
                );

                return new ReadResourceResult(contents);
            } catch (IOException e) {
                LOGGER.warning("Failed to get case: " + e.getMessage());
                throw new RuntimeException("Failed to get case", e);
            }
        });
    }

    /**
     * Creates the work items list resource.
     */
    private SyncResourceSpecification createWorkItemsResource() {
        Resource resource = new Resource(
                "yawl://workitems",
                "YAWL Work Items",
                "All live work items in the engine",
                "application/json",
                null
        );

        return new SyncResourceSpecification(resource, (exchange, request) -> {
            try {
                List<WorkItemRecord> items = client.getCompleteListOfLiveWorkItems(sessionHandle);

                List<Map<String, Object>> itemList = new ArrayList<>();
                for (WorkItemRecord item : items) {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("id", item.getID());
                    itemMap.put("caseId", item.getCaseID());
                    itemMap.put("taskId", item.getTaskID());
                    itemMap.put("status", item.getStatus());
                    itemMap.put("specId", item.getSpecificationID());
                    itemList.add(itemMap);
                }

                String json = mapper.writeValueAsString(Map.of(
                        "workItems", itemList,
                        "count", items.size()
                ));

                List<ResourceContents> contents = List.of(
                        new TextResourceContents("yawl://workitems", json, "application/json")
                );

                return new ReadResourceResult(contents);
            } catch (IOException e) {
                LOGGER.warning("Failed to get work items: " + e.getMessage());
                throw new RuntimeException("Failed to get work items", e);
            }
        });
    }

    /**
     * Creates the work item by ID resource.
     */
    private SyncResourceSpecification createWorkItemByIdResource() {
        Resource resource = new Resource(
                "yawl://workitems/{id}",
                "YAWL Work Item",
                "Get details for a specific work item",
                "application/json",
                null
        );

        return new SyncResourceSpecification(resource, (exchange, request) -> {
            try {
                String uri = request.uri();
                String workItemId = extractIdFromUri(uri, "yawl://workitems/");

                if (workItemId == null || workItemId.isEmpty()) {
                    throw new IllegalArgumentException("Work item ID required in URI");
                }

                String itemXml = client.getWorkItem(workItemId, sessionHandle);

                Map<String, Object> result = new HashMap<>();
                result.put("id", workItemId);
                result.put("workItem", itemXml);

                String json = mapper.writeValueAsString(result);

                List<ResourceContents> contents = List.of(
                        new TextResourceContents(uri, json, "application/json")
                );

                return new ReadResourceResult(contents);
            } catch (IOException e) {
                LOGGER.warning("Failed to get work item: " + e.getMessage());
                throw new RuntimeException("Failed to get work item", e);
            }
        });
    }

    /**
     * Extracts an ID from a URI path.
     *
     * @param uri the full URI
     * @param prefix the URI prefix
     * @return the extracted ID or null
     */
    private String extractIdFromUri(String uri, String prefix) {
        if (uri == null || !uri.startsWith(prefix)) {
            return null;
        }
        return uri.substring(prefix.length());
    }
}
