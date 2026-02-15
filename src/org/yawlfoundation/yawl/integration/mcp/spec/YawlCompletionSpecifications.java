package org.yawlfoundation.yawl.integration.mcp.spec;

import io.modelcontextprotocol.spec.CompleteCompletion;
import io.modelcontextprotocol.spec.CompleteResult;
import io.modelcontextprotocol.spec.PromptReference;
import io.modelcontextprotocol.spec.ResourceReference;
import io.modelcontextprotocol.spec.SyncCompletionSpecification;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * YAWL Completion Specifications for MCP.
 *
 * Provides autocompletion support for prompt arguments and resource URIs.
 * This helps AI models discover available specification names, case IDs, etc.
 *
 * Completions:
 * - Specification name completion for workflow start prompts
 * - Case ID completion for resource URIs
 * - Work item ID completion for task execution
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlCompletionSpecifications {

    private static final Logger LOGGER = Logger.getLogger(YawlCompletionSpecifications.class.getName());

    private final InterfaceB_EnvironmentBasedClient client;
    private final String sessionHandle;

    /**
     * Creates a new completion specifications provider.
     *
     * @param client the YAWL InterfaceB client
     * @param sessionHandle the YAWL session handle
     */
    public YawlCompletionSpecifications(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle) {
        this.client = client;
        this.sessionHandle = sessionHandle;
    }

    /**
     * Gets all completion specifications.
     *
     * @return list of all completion specifications
     */
    public List<SyncCompletionSpecification> getAllCompletions() {
        List<SyncCompletionSpecification> completions = new ArrayList<>();

        completions.add(createSpecNameCompletion());
        completions.add(createCaseIdCompletion());
        completions.add(createWorkItemIdCompletion());

        return completions;
    }

    /**
     * Creates specification name completion for prompts.
     */
    private SyncCompletionSpecification createSpecNameCompletion() {
        return new SyncCompletionSpecification(
                new PromptReference("yawl_start_workflow"),
                (exchange, request) -> {
                    try {
                        String prefix = extractPrefix(request.argument());
                        var specs = client.getSpecificationList(sessionHandle);

                        List<String> matches = new ArrayList<>();
                        for (var spec : specs) {
                            String name = spec.getID();
                            if (prefix == null || prefix.isEmpty() ||
                                    name.toLowerCase().startsWith(prefix.toLowerCase())) {
                                matches.add(name);
                            }
                            if (matches.size() >= 10) break;
                        }

                        return new CompleteResult(
                                new CompleteCompletion(matches, matches.size(), false)
                        );
                    } catch (IOException e) {
                        LOGGER.warning("Failed to get specification names: " + e.getMessage());
                        return new CompleteResult(new CompleteCompletion(List.of(), 0, false));
                    }
                }
        );
    }

    /**
     * Creates case ID completion for resource URIs.
     */
    private SyncCompletionSpecification createCaseIdCompletion() {
        return new SyncCompletionSpecification(
                new ResourceReference("yawl://cases/"),
                (exchange, request) -> {
                    try {
                        String prefix = extractPrefix(request.argument());
                        String casesXml = client.getAllRunningCases(sessionHandle);

                        // Parse case IDs from XML response
                        List<String> caseIds = parseCaseIdsFromXml(casesXml);

                        List<String> matches = new ArrayList<>();
                        for (String caseId : caseIds) {
                            if (prefix == null || prefix.isEmpty() ||
                                    caseId.toLowerCase().startsWith(prefix.toLowerCase())) {
                                matches.add(caseId);
                            }
                            if (matches.size() >= 10) break;
                        }

                        return new CompleteResult(
                                new CompleteCompletion(matches, matches.size(), false)
                        );
                    } catch (IOException e) {
                        LOGGER.warning("Failed to get case IDs: " + e.getMessage());
                        return new CompleteResult(new CompleteCompletion(List.of(), 0, false));
                    }
                }
        );
    }

    /**
     * Creates work item ID completion for resource URIs.
     */
    private SyncCompletionSpecification createWorkItemIdCompletion() {
        return new SyncCompletionSpecification(
                new ResourceReference("yawl://workitems/"),
                (exchange, request) -> {
                    try {
                        String prefix = extractPrefix(request.argument());
                        var items = client.getCompleteListOfLiveWorkItems(sessionHandle);

                        List<String> matches = new ArrayList<>();
                        for (var item : items) {
                            String itemId = item.getID();
                            if (prefix == null || prefix.isEmpty() ||
                                    itemId.toLowerCase().startsWith(prefix.toLowerCase())) {
                                matches.add(itemId);
                            }
                            if (matches.size() >= 10) break;
                        }

                        return new CompleteResult(
                                new CompleteCompletion(matches, matches.size(), false)
                        );
                    } catch (IOException e) {
                        LOGGER.warning("Failed to get work item IDs: " + e.getMessage());
                        return new CompleteResult(new CompleteCompletion(List.of(), 0, false));
                    }
                }
        );
    }

    /**
     * Extracts the prefix string from a completion argument.
     *
     * @param arg the completion argument
     * @return the prefix string or null
     */
    private String extractPrefix(Object arg) {
        if (arg == null) {
            return null;
        }
        if (arg instanceof Map) {
            Object value = ((Map<?, ?>) arg).get("value");
            return value != null ? value.toString() : null;
        }
        return arg.toString();
    }

    /**
     * Parses case IDs from the XML response.
     *
     * @param xml the XML response from getAllRunningCases
     * @return list of case IDs
     */
    private List<String> parseCaseIdsFromXml(String xml) {
        List<String> caseIds = new ArrayList<>();
        if (xml == null || xml.isEmpty()) {
            return caseIds;
        }

        // Simple XML parsing for case IDs
        int start = 0;
        while ((start = xml.indexOf("<id>", start)) != -1) {
            int end = xml.indexOf("</id>", start);
            if (end != -1) {
                String id = xml.substring(start + 4, end);
                caseIds.add(id);
                start = end + 5;
            } else {
                break;
            }
        }

        return caseIds;
    }
}
