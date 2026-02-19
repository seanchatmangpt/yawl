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
 * Specification management operations (Interface A).
 *
 * <p>Provides methods for uploading, listing, and unloading YAWL specifications.</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SpecificationOperations {

    private final YawlClient client;

    SpecificationOperations(YawlClient client) {
        this.client = client;
    }

    /**
     * Uploads a YAWL specification to the engine.
     *
     * @param specXml the specification XML content
     * @param sessionHandle the session handle
     * @return async result containing the response XML
     */
    public YawlClient.AsyncResult<String> upload(String specXml, String sessionHandle) {
        CompletableFuture<String> future = client.doPostXml(
            "/ia/specifications",
            Map.of("sessionHandle", sessionHandle),
            specXml
        ).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            if (response.statusCode() == 401) {
                throw new YawlClient.YawlException("Invalid session handle");
            }
            throw new YawlClient.YawlException("Upload failed: " + response.body());
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Lists all loaded specifications.
     *
     * @param sessionHandle the session handle
     * @return async result containing the specification list XML
     */
    public YawlClient.AsyncResult<String> list(String sessionHandle) {
        CompletableFuture<String> future = client.doGet("/ia/specifications", Map.of(
            "sessionHandle", sessionHandle
        )).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Failed to list specifications");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Unloads a specification from the engine.
     *
     * @param specId the specification identifier
     * @param version the specification version
     * @param uri the specification URI
     * @param sessionHandle the session handle
     * @return async result containing the response XML
     */
    public YawlClient.AsyncResult<String> unload(String specId, String version, String uri, String sessionHandle) {
        CompletableFuture<String> future = client.doDelete(
            "/ia/specifications/" + specId,
            Map.of(
                "version", version != null ? version : "",
                "uri", uri != null ? uri : specId,
                "sessionHandle", sessionHandle
            )
        ).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            if (response.statusCode() == 404) {
                throw new YawlClient.YawlException("Specification not found: " + specId);
            }
            throw new YawlClient.YawlException("Unload failed: " + response.body());
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Gets specification data.
     *
     * @param specIdentifier the specification identifier
     * @param specVersion the specification version
     * @param specUri the specification URI
     * @param sessionHandle the session handle
     * @return async result containing the specification data XML
     */
    public YawlClient.AsyncResult<String> getData(String specIdentifier, String specVersion, String specUri, String sessionHandle) {
        Map<String, String> params = new java.util.HashMap<>();
        params.put("action", "getSpecificationData");
        params.put("specidentifier", specIdentifier != null ? specIdentifier : "");
        params.put("specversion", specVersion != null ? specVersion : "0.1");
        params.put("specuri", specUri);
        params.put("sessionHandle", sessionHandle);

        CompletableFuture<String> future = client.doGet("/ib", params)
            .thenApply(response -> {
                if (response.statusCode() == 200) {
                    return response.body();
                }
                throw new YawlClient.YawlException("Failed to get specification data");
            });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Gets the specification for a running case.
     *
     * @param caseId the case identifier
     * @param sessionHandle the session handle
     * @return async result containing the specification XML
     */
    public YawlClient.AsyncResult<String> getForCase(String caseId, String sessionHandle) {
        CompletableFuture<String> future = client.doGet("/ib", Map.of(
            "action", "getSpecificationForCase",
            "caseID", caseId,
            "sessionHandle", sessionHandle
        )).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Failed to get specification for case");
        });

        return new YawlClient.AsyncResult<>(future);
    }
}
