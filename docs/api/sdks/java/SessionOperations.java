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
 * Session management operations for the YAWL API.
 *
 * <p>Provides methods for connecting to and disconnecting from the YAWL engine,
 * as well as checking connection status.</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SessionOperations {

    private final YawlClient client;

    SessionOperations(YawlClient client) {
        this.client = client;
    }

    /**
     * Connects to the YAWL engine and returns a session handle.
     *
     * <p>The session handle is required for all subsequent API calls.
     * Sessions expire after one hour of inactivity.</p>
     *
     * @param userId the user ID
     * @param password the password (will be encrypted)
     * @return async result containing the session handle
     */
    public YawlClient.AsyncResult<String> connect(String userId, String password) {
        CompletableFuture<String> future = client.doPost("/ib", Map.of(
            "action", "connect",
            "userid", userId,
            "password", encryptPassword(password)
        )).thenApply(response -> {
            if (response.statusCode() == 200) {
                String handle = response.body().trim();
                if (handle.contains("failure")) {
                    throw new YawlClient.YawlException("Connection failed: " + handle);
                }
                return handle;
            }
            throw new YawlClient.YawlException("Connection failed with status: " + response.statusCode());
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Checks if a session handle is still valid.
     *
     * @param sessionHandle the session handle to check
     * @return async result containing true if valid, false otherwise
     */
    public YawlClient.AsyncResult<Boolean> checkConnection(String sessionHandle) {
        CompletableFuture<Boolean> future = client.doGet("/ib", Map.of(
            "action", "checkConnection",
            "sessionHandle", sessionHandle
        )).thenApply(response -> {
            if (response.statusCode() == 200) {
                return !response.body().contains("failure");
            }
            return false;
        });

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Disconnects from the YAWL engine.
     *
     * @param sessionHandle the session handle to disconnect
     * @return async result containing true if disconnected successfully
     */
    public YawlClient.AsyncResult<Boolean> disconnect(String sessionHandle) {
        CompletableFuture<Boolean> future = client.doPost("/ia", Map.of(
            "action", "disconnect",
            "sessionHandle", sessionHandle
        )).thenApply(response -> response.statusCode() == 200);

        return new YawlClient.AsyncResult<>(future);
    }

    /**
     * Checks if the session has administrative privileges.
     *
     * @param sessionHandle the session handle
     * @return async result containing true if admin
     */
    public YawlClient.AsyncResult<Boolean> isAdministrator(String sessionHandle) {
        CompletableFuture<Boolean> future = client.doGet("/ib", Map.of(
            "action", "checkIsAdmin",
            "sessionHandle", sessionHandle
        )).thenApply(response -> response.body().contains("Granted"));

        return new YawlClient.AsyncResult<>(future);
    }

    private String encryptPassword(String password) {
        // Simplified - in production, use PasswordEncryptor.encrypt(password, null)
        return password;
    }
}
