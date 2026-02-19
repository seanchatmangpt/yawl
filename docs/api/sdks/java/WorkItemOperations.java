/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.client;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Work item operations (Interface B and Interface X).
 *
 * <p>Provides methods for managing work items including checkout, checkin,
 * suspension, and data updates.</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class WorkItemOperations {

    private final YawlClient client;

    WorkItemOperations(YawlClient client) {
        this.client = client;
    }

    /**
     * Gets all live work items.
     *
     * @param sessionHandle the session handle
     * @return async result containing the work item list XML
     */
    public YawlClient.AsyncResult<String> getAllLive(String sessionHandle) {
        CompletableFuture<String> future = client.doGet("/ib", Map.of(
            "action", "getLiveItems",
            "sessionHandle", sessionHandle
        )).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Failed to get live work items");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Gets work items for a specific case.
     *
     * @param caseId the case identifier
     * @param sessionHandle the session handle
     * @return async result containing the work item list XML
     */
    public YawlClient.AsyncResult<String> getForCase(String caseId, String sessionHandle) {
        CompletableFuture<String> future = client.doGet("/ib", Map.of(
            "action", "getWorkItemsWithIdentifier",
            "id", caseId,
            "idType", "case",
            "sessionHandle", sessionHandle
        )).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Failed to get work items for case");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Gets a specific work item.
     *
     * @param workItemId the work item identifier
     * @param sessionHandle the session handle
     * @return async result containing the work item XML
     */
    public YawlClient.AsyncResult<String> get(String workItemId, String sessionHandle) {
        CompletableFuture<String> future = client.doGet("/ib", Map.of(
            "action", "getWorkItem",
            "workItemID", workItemId,
            "sessionHandle", sessionHandle
        )).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Failed to get work item");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Checks out a work item.
     *
     * @param workItemId the work item identifier
     * @param sessionHandle the session handle
     * @return async result containing the checked out work item XML
     */
    public YawlClient.AsyncResult<String> checkout(String workItemId, String sessionHandle) {
        return checkout(workItemId, null, sessionHandle);
    }

    /**
     * Checks out a work item with a log predicate.
     *
     * @param workItemId the work item identifier
     * @param logPredicate optional log predicate
     * @param sessionHandle the session handle
     * @return async result containing the checked out work item XML
     */
    public YawlClient.AsyncResult<String> checkout(String workItemId, String logPredicate, String sessionHandle) {
        Map<String, String> params = new java.util.HashMap<>();
        params.put("action", "checkout");
        params.put("workItemID", workItemId);
        params.put("sessionHandle", sessionHandle);
        if (logPredicate != null) {
            params.put("logPredicate", logPredicate);
        }

        CompletableFuture<String> future = client.doPost("/ib", params)
            .thenApply(response -> {
                if (response.statusCode() == 200) {
                    String body = response.body();
                    if (body.contains("failure")) {
                        throw new YawlClient.YawlException("Checkout failed: " + body);
                    }
                    return body;
                }
                throw new YawlClient.YawlException("Checkout failed with status: " + response.statusCode());
            });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Checks in a work item.
     *
     * @param workItemId the work item identifier
     * @param data the work item data XML
     * @param sessionHandle the session handle
     * @return async result containing the response XML
     */
    public YawlClient.AsyncResult<String> checkin(String workItemId, String data, String sessionHandle) {
        return checkin(workItemId, data, null, sessionHandle);
    }

    /**
     * Checks in a work item with a log predicate.
     *
     * @param workItemId the work item identifier
     * @param data the work item data XML
     * @param logPredicate optional log predicate
     * @param sessionHandle the session handle
     * @return async result containing the response XML
     */
    public YawlClient.AsyncResult<String> checkin(String workItemId, String data, String logPredicate, String sessionHandle) {
        Map<String, String> params = new java.util.HashMap<>();
        params.put("action", "checkin");
        params.put("workItemID", workItemId);
        params.put("data", data != null ? data : "<data/>");
        params.put("logPredicate", logPredicate != null ? logPredicate : "");
        params.put("sessionHandle", sessionHandle);

        CompletableFuture<String> future = client.doPost("/ib", params)
            .thenApply(response -> {
                if (response.statusCode() == 200) {
                    String body = response.body();
                    if (body.contains("failure")) {
                        throw new YawlClient.YawlException("Checkin failed: " + body);
                    }
                    return body;
                }
                throw new YawlClient.YawlException("Checkin failed with status: " + response.statusCode());
            });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Suspends a work item.
     *
     * @param workItemId the work item identifier
     * @param sessionHandle the session handle
     * @return async result containing the response XML
     */
    public YawlClient.AsyncResult<String> suspend(String workItemId, String sessionHandle) {
        CompletableFuture<String> future = client.doPost("/ib", Map.of(
            "action", "suspend",
            "workItemID", workItemId,
            "sessionHandle", sessionHandle
        )).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Suspend failed");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Unsuspends a work item.
     *
     * @param workItemId the work item identifier
     * @param sessionHandle the session handle
     * @return async result containing the response XML
     */
    public YawlClient.AsyncResult<String> unsuspend(String workItemId, String sessionHandle) {
        CompletableFuture<String> future = client.doPost("/ib", Map.of(
            "action", "unsuspend",
            "workItemID", workItemId,
            "sessionHandle", sessionHandle
        )).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Unsuspend failed");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Rolls back a work item from executing to fired status.
     *
     * @param workItemId the work item identifier
     * @param sessionHandle the session handle
     * @return async result containing the response XML
     */
    public YawlClient.AsyncResult<String> rollback(String workItemId, String sessionHandle) {
        CompletableFuture<String> future = client.doPost("/ib", Map.of(
            "action", "rollback",
            "workItemID", workItemId,
            "sessionHandle", sessionHandle
        )).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Rollback failed");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Skips a work item.
     *
     * @param workItemId the work item identifier
     * @param sessionHandle the session handle
     * @return async result containing the response XML
     */
    public YawlClient.AsyncResult<String> skip(String workItemId, String sessionHandle) {
        CompletableFuture<String> future = client.doPost("/ib", Map.of(
            "action", "skip",
            "workItemID", workItemId,
            "sessionHandle", sessionHandle
        )).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Skip failed");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    // ==========================================
    // Interface X REST Operations
    // ==========================================

    /**
     * Cancels a work item with exception data (Interface X).
     *
     * @param workItemId the work item identifier
     * @param exceptionData optional exception data XML
     * @param markAsFail whether to mark as failure
     * @param sessionHandle the session handle
     * @return async result containing the response XML
     */
    public YawlClient.AsyncResult<String> cancel(String workItemId, String exceptionData, boolean markAsFail, String sessionHandle) {
        CompletableFuture<String> future = client.doPostXml(
            "/ix/workitems/" + workItemId + "/cancel",
            Map.of(
                "sessionHandle", sessionHandle,
                "fail", String.valueOf(markAsFail)
            ),
            exceptionData != null ? exceptionData : ""
        ).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Cancel work item failed");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Updates work item data without checking in (Interface X).
     *
     * @param workItemId the work item identifier
     * @param data the updated data XML
     * @param sessionHandle the session handle
     * @return async result containing the response XML
     */
    public YawlClient.AsyncResult<String> updateData(String workItemId, String data, String sessionHandle) {
        CompletableFuture<String> future = client.doPutXml(
            "/ix/workitems/" + workItemId + "/data",
            Map.of("sessionHandle", sessionHandle),
            data
        ).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Update data failed");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Registers an InterfaceX listener.
     *
     * @param listenerUri the listener URI
     * @param sessionHandle the session handle
     * @return async result containing the response
     */
    public YawlClient.AsyncResult<String> registerListener(String listenerUri, String sessionHandle) {
        CompletableFuture<String> future = client.doPostXml(
            "/ix/listeners",
            Map.of("sessionHandle", sessionHandle),
            listenerUri
        ).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Register listener failed");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Unregisters an InterfaceX listener.
     *
     * @param listenerUri the listener URI
     * @param sessionHandle the session handle
     * @return async result containing the response
     */
    public YawlClient.AsyncResult<String> unregisterListener(String listenerUri, String sessionHandle) {
        CompletableFuture<String> future = client.doDelete(
            "/ix/listeners",
            Map.of(
                "uri", listenerUri,
                "sessionHandle", sessionHandle
            )
        ).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Unregister listener failed");
        });

        return new YawlClient.AsyncResult<>(future);
    }
}
