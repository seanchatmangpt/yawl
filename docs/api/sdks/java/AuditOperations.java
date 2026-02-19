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
 * Audit and compliance operations.
 *
 * <p>Provides access to the receipt chain for audit trails, compliance checking,
 * and incident response.</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class AuditOperations {

    private final YawlClient client;

    AuditOperations(YawlClient client) {
        this.client = client;
    }

    /**
     * Gets the full receipt chain for a case.
     *
     * @param caseId the case identifier
     * @return async result containing the receipt chain JSON
     */
    public YawlClient.AsyncResult<String> getReceiptChain(String caseId) {
        CompletableFuture<String> future = client.doGet(
            "/audit/case/" + caseId + "/receipts",
            null
        ).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Failed to get receipt chain");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Gets the computed case state from receipts.
     *
     * @param caseId the case identifier
     * @return async result containing the computed state JSON
     */
    public YawlClient.AsyncResult<String> getComputedState(String caseId) {
        CompletableFuture<String> future = client.doGet(
            "/audit/case/" + caseId + "/state",
            null
        ).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Failed to get computed state");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Gets statistics for a case.
     *
     * @param caseId the case identifier
     * @return async result containing the statistics JSON
     */
    public YawlClient.AsyncResult<String> getStats(String caseId) {
        CompletableFuture<String> future = client.doGet(
            "/audit/case/" + caseId + "/stats",
            null
        ).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Failed to get stats");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Gets a single receipt by ID.
     *
     * @param receiptId the receipt ID
     * @return async result containing the receipt JSON
     */
    public YawlClient.AsyncResult<String> getReceipt(long receiptId) {
        CompletableFuture<String> future = client.doGet(
            "/audit/receipt/" + receiptId,
            null
        ).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            if (response.statusCode() == 404) {
                throw new YawlClient.YawlException("Receipt not found: " + receiptId);
            }
            throw new YawlClient.YawlException("Failed to get receipt");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Verifies chain integrity.
     *
     * @param caseId the case identifier
     * @return async result containing the verification result JSON
     */
    public YawlClient.AsyncResult<String> verifyChain(String caseId) {
        CompletableFuture<String> future = client.doGet(
            "/audit/verify/" + caseId,
            null
        ).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Chain verification failed");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Gets receipts within a time range.
     *
     * @param caseId the case identifier
     * @param startMs start timestamp in milliseconds
     * @param endMs end timestamp in milliseconds
     * @return async result containing the receipts JSON
     */
    public YawlClient.AsyncResult<String> getByTimeRange(String caseId, long startMs, long endMs) {
        CompletableFuture<String> future = client.doGet(
            "/audit/case/" + caseId + "/time-range",
            Map.of("start", String.valueOf(startMs), "end", String.valueOf(endMs))
        ).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            if (response.statusCode() == 400) {
                throw new YawlClient.YawlException("Invalid time range");
            }
            throw new YawlClient.YawlException("Failed to get receipts by time range");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Gets admitted (committed) transitions.
     *
     * @param caseId the case identifier
     * @return async result containing the admitted transitions JSON
     */
    public YawlClient.AsyncResult<String> getAdmitted(String caseId) {
        CompletableFuture<String> future = client.doGet(
            "/audit/case/" + caseId + "/admitted",
            null
        ).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Failed to get admitted transitions");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Gets rejected (policy violation) transitions.
     *
     * @param caseId the case identifier
     * @return async result containing the rejected transitions JSON
     */
    public YawlClient.AsyncResult<String> getRejected(String caseId) {
        CompletableFuture<String> future = client.doGet(
            "/audit/case/" + caseId + "/rejected",
            null
        ).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Failed to get rejected transitions");
        });

        return new YawlClient.AsyncResult<>(future);
    }
}
