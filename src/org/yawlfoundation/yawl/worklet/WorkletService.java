/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.worklet;

import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RDR-driven worklet orchestrator — the first formal rule-based AI routing layer
 * in a workflow engine.
 *
 * <p>Listens for {@link YEventType#ITEM_ENABLED} events. On each event:
 * <ol>
 *   <li>Loads the {@link RdrSet} for the triggering specification.</li>
 *   <li>Evaluates the RDR tree for the work item's task against a context
 *       derived from the work item.</li>
 *   <li>Routes the work item:
 *       <ul>
 *         <li>{@link WorkletSelection.SubCaseSelection} → records a sub-case worklet for launch</li>
 *         <li>{@link WorkletSelection.A2AAgentSelection} → dispatches to an A2A agent on a
 *             virtual thread (fire-and-forget HTTP POST)</li>
 *         <li>{@link WorkletSelection.NoSelection} → no action; normal YAWL execution continues</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>A2A conclusions in the RDR rule set use the prefix {@code "a2a:"} followed by an HTTP URL
 * and skill name: {@code "a2a:http://agent:8090/risk_assessment"}.
 *
 * <p>Register with a YAWL engine via:
 * <pre>{@code
 * engine.addWorkItemEventListener(new WorkletService(repository));
 * }</pre>
 */
public class WorkletService implements YWorkItemEventListener {

    static final String A2A_PREFIX = "a2a:";
    private static final Duration A2A_TIMEOUT = Duration.ofSeconds(30);

    private final RdrSetRepository repository;
    private final ConcurrentHashMap<String, WorkletRecord> activeRecords;
    private final HttpClient httpClient;

    /**
     * Constructs a WorkletService backed by the given rule repository.
     *
     * @param repository the RDR rule set repository; must not be null
     * @throws IllegalArgumentException if repository is null
     */
    public WorkletService(RdrSetRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("RdrSetRepository must not be null");
        }
        this.repository = repository;
        this.activeRecords = new ConcurrentHashMap<>();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(A2A_TIMEOUT)
                .build();
    }

    /**
     * Handles a YAWL work item event. Only {@link YEventType#ITEM_ENABLED} events trigger
     * worklet evaluation; all other event types are silently ignored.
     *
     * @param event the work item event; must not be null
     */
    @Override
    public void handleWorkItemEvent(YWorkItemEvent event) {
        if (event == null || event.getEventType() != YEventType.ITEM_ENABLED) {
            return;
        }
        YWorkItem workItem = event.getWorkItem();
        if (workItem == null) {
            return;
        }

        String specId = workItem.getSpecificationID().getIdentifier();
        RdrSet rdrSet = repository.load(specId);

        WorkletSelection selection = evaluate(workItem, rdrSet);

        switch (selection) {
            case WorkletSelection.SubCaseSelection scs -> {
                WorkletRecord record = new WorkletRecord(
                        scs.workletName(),
                        workItem.getCaseID().toString(),
                        workItem.getTaskID());
                activeRecords.put(record.getCompositeKey(), record);
            }
            case WorkletSelection.A2AAgentSelection a2a -> {
                WorkletRecord record = new WorkletRecord(
                        a2a.agentEndpoint(),
                        a2a.skill(),
                        workItem.getCaseID().toString(),
                        workItem.getTaskID());
                activeRecords.put(record.getCompositeKey(), record);
                Thread.ofVirtual()
                        .name("worklet-a2a-" + workItem.getCaseID() + ":" + workItem.getTaskID())
                        .start(() -> dispatchA2A(record, a2a));
            }
            case WorkletSelection.NoSelection ignored -> {
                // No worklet selected; normal YAWL execution continues.
            }
        }
    }

    /**
     * Evaluates the RDR rule set for the given work item and returns the
     * appropriate {@link WorkletSelection}.
     *
     * <p>Package-private for testing.
     *
     * @param workItem the work item to evaluate
     * @param rdrSet   the rule set for the work item's specification
     * @return a non-null {@link WorkletSelection}
     */
    WorkletSelection evaluate(YWorkItem workItem, RdrSet rdrSet) {
        String taskId = workItem.getTaskID();
        Map<String, String> context = buildContext(workItem);

        RdrTree tree = rdrSet.getTree(taskId);
        if (tree == null || tree.isEmpty()) {
            return new WorkletSelection.NoSelection();
        }

        RdrNode selectedNode = tree.selectNode(context);
        if (selectedNode == null) {
            return new WorkletSelection.NoSelection();
        }

        String conclusion = selectedNode.getConclusion();
        int nodeId = selectedNode.getId();

        if (conclusion.startsWith(A2A_PREFIX)) {
            String rest = conclusion.substring(A2A_PREFIX.length());
            // Format: "http://host:port/skill" — skill is the last path segment
            int lastSlash = rest.lastIndexOf('/');
            if (lastSlash <= 0 || lastSlash == rest.length() - 1) {
                throw new WorkletServiceException(
                        "A2A conclusion has invalid format (expected 'a2a:http://host/skill'): "
                        + conclusion);
            }
            String endpoint = rest.substring(0, lastSlash);
            String skill = rest.substring(lastSlash + 1);
            return new WorkletSelection.A2AAgentSelection(endpoint, skill, nodeId);
        }

        return new WorkletSelection.SubCaseSelection(conclusion, nodeId);
    }

    /**
     * Builds a data context map from the work item's available attributes.
     *
     * <p>Context keys: {@code taskId}, {@code caseId}, {@code specId}.
     * Additional data values from the work item's data document are not yet extracted
     * (require JDOM data access); this initial context supports task/case-based rules.
     *
     * <p>Package-private for testing.
     *
     * @param workItem the work item
     * @return a populated context map; never null
     */
    Map<String, String> buildContext(YWorkItem workItem) {
        Map<String, String> context = new HashMap<>();
        context.put("taskId", workItem.getTaskID());
        context.put("caseId", workItem.getCaseID().toString());
        context.put("specId", workItem.getSpecificationID().getIdentifier());
        return context;
    }

    /**
     * Returns an unmodifiable snapshot of all active worklet records.
     */
    public Map<String, WorkletRecord> getActiveRecords() {
        return Map.copyOf(activeRecords);
    }

    /**
     * Removes the active record for the given composite key.
     *
     * @param compositeKey the key returned by {@link WorkletRecord#getCompositeKey()}
     * @return the removed record, or null if no record existed
     */
    public WorkletRecord removeRecord(String compositeKey) {
        return activeRecords.remove(compositeKey);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void dispatchA2A(WorkletRecord record, WorkletSelection.A2AAgentSelection a2a) {
        String url = a2a.agentEndpoint() + "/" + a2a.skill();
        String body = buildA2ARequestBody(record);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(A2A_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // A2A dispatch failure is non-fatal: log and allow normal YAWL execution to continue
            activeRecords.remove(record.getCompositeKey());
        }
    }

    private String buildA2ARequestBody(WorkletRecord record) {
        return """
                {"caseId":"%s","taskId":"%s","workletName":"%s"}"""
                .formatted(
                    escapeJson(record.getHostCaseId()),
                    escapeJson(record.getHostTaskId()),
                    escapeJson(record.getWorkletName()));
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
