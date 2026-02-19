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
 * Case management operations (Interface B).
 *
 * <p>Provides methods for launching, querying, and managing workflow cases.</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class CaseOperations {

    private final YawlClient client;

    CaseOperations(YawlClient client) {
        this.client = client;
    }

    /**
     * Creates a new launch case request builder.
     *
     * @param specId the specification identifier
     * @return a new LaunchBuilder
     */
    public LaunchBuilder launch(String specId) {
        return new LaunchBuilder(client, specId);
    }

    /**
     * Gets all running cases.
     *
     * @param sessionHandle the session handle
     * @return async result containing the case list XML
     */
    public YawlClient.AsyncResult<String> getAllRunning(String sessionHandle) {
        CompletableFuture<String> future = client.doGet("/ib", Map.of(
            "action", "getAllRunningCases",
            "sessionHandle", sessionHandle
        )).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Failed to get running cases");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Gets cases for a specific specification.
     *
     * @param specIdentifier the specification identifier
     * @param specVersion the specification version
     * @param specUri the specification URI
     * @param sessionHandle the session handle
     * @return async result containing the case list XML
     */
    public YawlClient.AsyncResult<String> getForSpecification(String specIdentifier, String specVersion, String specUri, String sessionHandle) {
        Map<String, String> params = new java.util.HashMap<>();
        params.put("action", "getCasesForSpecification");
        params.put("specidentifier", specIdentifier != null ? specIdentifier : "");
        params.put("specversion", specVersion != null ? specVersion : "0.1");
        params.put("specuri", specUri);
        params.put("sessionHandle", sessionHandle);

        CompletableFuture<String> future = client.doGet("/ib", params)
            .thenApply(response -> {
                if (response.statusCode() == 200) {
                    return response.body();
                }
                throw new YawlClient.YawlException("Failed to get cases for specification");
            });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Gets the state of a case.
     *
     * @param caseId the case identifier
     * @param sessionHandle the session handle
     * @return async result containing the case state XML
     */
    public YawlClient.AsyncResult<String> getState(String caseId, String sessionHandle) {
        CompletableFuture<String> future = client.doGet("/ib", Map.of(
            "action", "getCaseState",
            "caseID", caseId,
            "sessionHandle", sessionHandle
        )).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Failed to get case state");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Gets the data of a case.
     *
     * @param caseId the case identifier
     * @param sessionHandle the session handle
     * @return async result containing the case data XML
     */
    public YawlClient.AsyncResult<String> getData(String caseId, String sessionHandle) {
        CompletableFuture<String> future = client.doGet("/ib", Map.of(
            "action", "getCaseData",
            "caseID", caseId,
            "sessionHandle", sessionHandle
        )).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Failed to get case data");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Cancels a case.
     *
     * @param caseId the case identifier
     * @param sessionHandle the session handle
     * @return async result containing the response XML
     */
    public YawlClient.AsyncResult<String> cancel(String caseId, String sessionHandle) {
        CompletableFuture<String> future = client.doPost("/ib", Map.of(
            "action", "cancelCase",
            "caseID", caseId,
            "sessionHandle", sessionHandle
        )).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Failed to cancel case");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Exports case state for migration.
     *
     * @param caseId the case identifier
     * @param sessionHandle the session handle
     * @return async result containing the exported state XML
     */
    public YawlClient.AsyncResult<String> exportState(String caseId, String sessionHandle) {
        CompletableFuture<String> future = client.doGet("/ib", Map.of(
            "action", "exportCaseState",
            "caseID", caseId,
            "sessionHandle", sessionHandle
        )).thenApply(response -> {
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new YawlClient.YawlException("Failed to export case state");
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Builder for launching cases.
     */
    public static class LaunchBuilder {
        private final YawlClient client;
        private final String specId;
        private String specIdentifier;
        private String specVersion = "0.1";
        private String specUri;
        private String caseParams;
        private String caseId;
        private String completionObserverUri;
        private String logData;
        private long delayMs;
        private Long startTime;

        LaunchBuilder(YawlClient client, String specId) {
            this.client = client;
            this.specId = specId;
            this.specUri = specId;
        }

        /**
         * Sets the specification identifier.
         */
        public LaunchBuilder identifier(String identifier) {
            this.specIdentifier = identifier;
            return this;
        }

        /**
         * Sets the specification version.
         */
        public LaunchBuilder version(String version) {
            this.specVersion = version;
            return this;
        }

        /**
         * Sets the specification URI.
         */
        public LaunchBuilder uri(String uri) {
            this.specUri = uri;
            return this;
        }

        /**
         * Sets the case parameters.
         */
        public LaunchBuilder withParams(String caseParams) {
            this.caseParams = caseParams;
            return this;
        }

        /**
         * Sets a custom case ID.
         */
        public LaunchBuilder withCaseId(String caseId) {
            this.caseId = caseId;
            return this;
        }

        /**
         * Sets the completion observer URI.
         */
        public LaunchBuilder withCompletionObserver(String uri) {
            this.completionObserverUri = uri;
            return this;
        }

        /**
         * Sets the log data.
         */
        public LaunchBuilder withLogData(String logData) {
            this.logData = logData;
            return this;
        }

        /**
         * Sets a delay before launching.
         */
        public LaunchBuilder withDelayMs(long delayMs) {
            this.delayMs = delayMs;
            return this;
        }

        /**
         * Sets a start time for delayed launch.
         */
        public LaunchBuilder withStartTime(long startTime) {
            this.startTime = startTime;
            return this;
        }

        /**
         * Executes the launch case request.
         *
         * @param sessionHandle the session handle
         * @return async result containing the case ID
         */
        public YawlClient.AsyncResult<String> execute(String sessionHandle) {
            Map<String, String> params = new java.util.HashMap<>();
            params.put("action", "launchCase");
            params.put("sessionHandle", sessionHandle);

            if (specIdentifier != null) params.put("specidentifier", specIdentifier);
            params.put("specversion", specVersion);
            params.put("specuri", specUri);
            if (caseParams != null) params.put("caseParams", caseParams);
            if (caseId != null) params.put("caseid", caseId);
            if (completionObserverUri != null) params.put("completionObserverURI", completionObserverUri);
            if (logData != null) params.put("logData", logData);
            if (delayMs > 0) params.put("mSec", String.valueOf(delayMs));
            if (startTime != null) params.put("start", String.valueOf(startTime));

            CompletableFuture<String> future = client.doPost("/ib", params)
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        String body = response.body().trim();
                        if (body.contains("failure")) {
                            throw new YawlClient.YawlException("Launch case failed: " + body);
                        }
                        return body;
                    }
                    throw new YawlClient.YawlException("Launch case failed with status: " + response.statusCode());
                });

            return new YawlClient.AsyncResult<>(future);
        }
    }
}
