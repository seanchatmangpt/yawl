package org.yawlfoundation.yawl.integration.mcp.spec;

import org.yawlfoundation.yawl.integration.mcp.stub.McpServerFeatures;
import org.yawlfoundation.yawl.integration.mcp.stub.McpSyncServerExchange;
import org.yawlfoundation.yawl.integration.mcp.stub.McpSchema;
import org.yawlfoundation.yawl.engine.interfce.SpecificationData;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * YAWL Completion Specifications for MCP SDK 0.17.2.
 *
 * Static factory class that creates MCP completion specifications for autocompletion
 * of prompt arguments and resource URIs. Each completion handler queries the real
 * YAWL engine to provide dynamic suggestions based on current engine state.
 *
 * Completions:
 * - workflow_analysis prompt: Suggests specification identifiers from loaded specs
 * - task_completion_guide prompt: Suggests work item IDs from live work items
 * - yawl://cases/{caseId} resource: Suggests case IDs from running cases
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlCompletionSpecifications {

    private static final Logger LOGGER = Logger.getLogger(YawlCompletionSpecifications.class.getName());

    private static final int MAX_COMPLETION_RESULTS = 50;

    private YawlCompletionSpecifications() {
        // Static factory class - prevent instantiation
    }

    /**
     * Creates all completion specifications backed by real YAWL engine calls.
     *
     * @param client the YAWL InterfaceB client connected to the engine
     * @param sessionHandle the authenticated YAWL session handle
     * @return list of sync completion specifications for MCP registration
     */
    public static List<McpServerFeatures.SyncCompletionSpecification> createAll(
            InterfaceB_EnvironmentBasedClient client, String sessionHandle) {
        if (client == null) {
            throw new IllegalArgumentException(
                "InterfaceB_EnvironmentBasedClient is required to create YAWL MCP completions");
        }
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            throw new IllegalArgumentException(
                "A valid YAWL session handle is required to create YAWL MCP completions");
        }

        List<McpServerFeatures.SyncCompletionSpecification> completions = new ArrayList<>();
        completions.add(createWorkflowAnalysisCompletion(client, sessionHandle));
        completions.add(createTaskCompletionGuideCompletion(client, sessionHandle));
        completions.add(createCaseResourceCompletion(client, sessionHandle));
        return completions;
    }

    // =========================================================================
    // Completion: workflow_analysis prompt - spec identifiers
    // =========================================================================

    /**
     * Creates a completion for the workflow_analysis prompt.
     *
     * When a user is filling in the specIdentifier argument for the workflow_analysis
     * prompt, this handler queries the YAWL engine for all loaded specification
     * identifiers and returns those matching the user's partial input.
     */
    private static McpServerFeatures.SyncCompletionSpecification createWorkflowAnalysisCompletion(
            InterfaceB_EnvironmentBasedClient client, String sessionHandle) {

        return new McpServerFeatures.SyncCompletionSpecification(
            new McpSchema.PromptReference("workflow_analysis"),
            (exchange, request) -> {
                try {
                    String partial = request.argument().value();
                    List<SpecificationData> specs = client.getSpecificationList(sessionHandle);

                    List<String> matches = new ArrayList<>();
                    if (specs != null) {
                        for (SpecificationData spec : specs) {
                            String identifier = spec.getID().getIdentifier();
                            if (partial == null || identifier.toLowerCase().startsWith(
                                    partial.toLowerCase())) {
                                matches.add(identifier);
                            }
                            if (matches.size() >= MAX_COMPLETION_RESULTS) {
                                break;
                            }
                        }
                    }

                    boolean hasMore = specs != null && matches.size() < specs.size()
                        && matches.size() >= MAX_COMPLETION_RESULTS;

                    return new McpSchema.CompleteResult(
                        new McpSchema.CompleteResult.CompleteCompletion(
                            matches, matches.size(), hasMore));
                } catch (IOException e) {
                    LOGGER.warning("Failed to fetch specifications for completion: " + e.getMessage());
                    return new McpSchema.CompleteResult(
                        new McpSchema.CompleteResult.CompleteCompletion(
                            List.of(), 0, false));
                }
            }
        );
    }

    // =========================================================================
    // Completion: task_completion_guide prompt - work item IDs
    // =========================================================================

    /**
     * Creates a completion for the task_completion_guide prompt.
     *
     * When a user is filling in the workItemId argument for the task_completion_guide
     * prompt, this handler queries the YAWL engine for all live work item IDs and
     * returns those matching the user's partial input.
     */
    private static McpServerFeatures.SyncCompletionSpecification createTaskCompletionGuideCompletion(
            InterfaceB_EnvironmentBasedClient client, String sessionHandle) {

        return new McpServerFeatures.SyncCompletionSpecification(
            new McpSchema.PromptReference("task_completion_guide"),
            (exchange, request) -> {
                try {
                    String partial = request.argument().value();
                    List<WorkItemRecord> items = client.getCompleteListOfLiveWorkItems(sessionHandle);

                    List<String> matches = new ArrayList<>();
                    if (items != null) {
                        for (WorkItemRecord wir : items) {
                            String itemId = wir.getID();
                            if (partial == null || itemId.toLowerCase().startsWith(
                                    partial.toLowerCase())) {
                                matches.add(itemId);
                            }
                            if (matches.size() >= MAX_COMPLETION_RESULTS) {
                                break;
                            }
                        }
                    }

                    boolean hasMore = items != null && matches.size() < items.size()
                        && matches.size() >= MAX_COMPLETION_RESULTS;

                    return new McpSchema.CompleteResult(
                        new McpSchema.CompleteResult.CompleteCompletion(
                            matches, matches.size(), hasMore));
                } catch (IOException e) {
                    LOGGER.warning("Failed to fetch work items for completion: " + e.getMessage());
                    return new McpSchema.CompleteResult(
                        new McpSchema.CompleteResult.CompleteCompletion(
                            List.of(), 0, false));
                }
            }
        );
    }

    // =========================================================================
    // Completion: yawl://cases/{caseId} resource - case IDs
    // =========================================================================

    /**
     * Creates a completion for the yawl://cases/{caseId} resource template.
     *
     * When a user is navigating to a case resource, this handler queries the YAWL
     * engine for all running case IDs and returns those matching the user's partial
     * input. Case IDs are parsed from the engine's XML response.
     */
    private static McpServerFeatures.SyncCompletionSpecification createCaseResourceCompletion(
            InterfaceB_EnvironmentBasedClient client, String sessionHandle) {

        return new McpServerFeatures.SyncCompletionSpecification(
            new McpSchema.ResourceReference("yawl://cases/{caseId}"),
            (exchange, request) -> {
                try {
                    String partial = request.argument().value();
                    String casesXml = client.getAllRunningCases(sessionHandle);

                    List<String> caseIds = parseCaseIdsFromXml(casesXml);

                    List<String> matches = new ArrayList<>();
                    for (String caseId : caseIds) {
                        if (partial == null || caseId.startsWith(partial)) {
                            matches.add(caseId);
                        }
                        if (matches.size() >= MAX_COMPLETION_RESULTS) {
                            break;
                        }
                    }

                    boolean hasMore = matches.size() < caseIds.size()
                        && matches.size() >= MAX_COMPLETION_RESULTS;

                    return new McpSchema.CompleteResult(
                        new McpSchema.CompleteResult.CompleteCompletion(
                            matches, matches.size(), hasMore));
                } catch (IOException e) {
                    LOGGER.warning("Failed to fetch running cases for completion: " + e.getMessage());
                    return new McpSchema.CompleteResult(
                        new McpSchema.CompleteResult.CompleteCompletion(
                            List.of(), 0, false));
                }
            }
        );
    }

    // =========================================================================
    // XML Parsing Helper
    // =========================================================================

    /**
     * Parses case IDs from the YAWL engine's XML response for running cases.
     *
     * The engine returns case IDs wrapped in {@code <caseID>} elements within the
     * running cases XML. This method extracts each ID using simple XML tag parsing
     * to avoid requiring a full XML parser dependency.
     *
     * @param xml the XML response from client.getAllRunningCases()
     * @return list of case ID strings parsed from the XML
     */
    private static List<String> parseCaseIdsFromXml(String xml) {
        List<String> caseIds = new ArrayList<>();
        if (xml == null) {
            return caseIds;
        }

        // Parse <caseID>...</caseID> elements from the engine response
        String openTag = "<caseID>";
        String closeTag = "</caseID>";
        int searchStart = 0;
        while (true) {
            int tagStart = xml.indexOf(openTag, searchStart);
            if (tagStart == -1) {
                break;
            }
            int valueStart = tagStart + openTag.length();
            int tagEnd = xml.indexOf(closeTag, valueStart);
            if (tagEnd == -1) {
                break;
            }
            String caseId = xml.substring(valueStart, tagEnd).strip();
            if (!caseId.isEmpty()) {
                caseIds.add(caseId);
            }
            searchStart = tagEnd + closeTag.length();
        }

        // Also try <id>...</id> elements as a fallback format
        if (caseIds.isEmpty()) {
            String altOpen = "<id>";
            String altClose = "</id>";
            searchStart = 0;
            while (true) {
                int tagStart = xml.indexOf(altOpen, searchStart);
                if (tagStart == -1) {
                    break;
                }
                int valueStart = tagStart + altOpen.length();
                int tagEnd = xml.indexOf(altClose, valueStart);
                if (tagEnd == -1) {
                    break;
                }
                String caseId = xml.substring(valueStart, tagEnd).strip();
                if (!caseId.isEmpty()) {
                    caseIds.add(caseId);
                }
                searchStart = tagEnd + altClose.length();
            }
        }

        return caseIds;
    }
}
