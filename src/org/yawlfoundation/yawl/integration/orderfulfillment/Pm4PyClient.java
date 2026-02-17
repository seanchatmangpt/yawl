/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.orderfulfillment;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HTTP client for PM4Py A2A agent. Sends JSON messages and extracts text response.
 * Uses modern java.net.http.HttpClient and Jackson for JSON processing.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class Pm4PyClient {

    private final String agentUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public Pm4PyClient(String agentUrl) {
        if (agentUrl == null || agentUrl.isEmpty()) {
            throw new IllegalArgumentException("agentUrl is required");
        }
        this.agentUrl = agentUrl.endsWith("/") ? agentUrl : agentUrl + "/";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Create client from PM4PY_AGENT_URL env (default http://localhost:9092).
     */
    public static Pm4PyClient fromEnvironment() {
        String url = System.getenv("PM4PY_AGENT_URL");
        if (url == null || url.isEmpty()) {
            url = "http://localhost:9092";
        }
        return new Pm4PyClient(url);
    }

    /**
     * Call PM4Py agent with a skill and xes_input. Returns JSON result or error.
     */
    public String call(String skill, String xesInput) {
        return call(skill, xesInput, null);
    }

    /**
     * Call PM4Py agent with skill, xes_input, and optional extra args.
     */
    public String call(String skill, String xesInput, Map<String, String> extraArgs) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("skill", skill);
            payload.put("xes_input", xesInput);
            if (extraArgs != null) {
                payload.putAll(extraArgs);
            }

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("kind", "text");
            textPart.put("text", objectMapper.writeValueAsString(payload));

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("parts", new Object[]{textPart});

            Map<String, Object> params = new HashMap<>();
            params.put("message", message);

            Map<String, Object> rpcRequest = new HashMap<>();
            rpcRequest.put("jsonrpc", "2.0");
            rpcRequest.put("id", 1);
            rpcRequest.put("method", "message/send");
            rpcRequest.put("params", params);

            String requestBody = objectMapper.writeValueAsString(rpcRequest);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(agentUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "PM4Py agent HTTP " + response.statusCode() + ": " + response.body());
                return objectMapper.writeValueAsString(error);
            }

            return response.body();
        } catch (IOException e) {
            try {
                Map<String, String> error = new HashMap<>();
                error.put("error", "PM4Py agent call failed: " + e.getMessage());
                return objectMapper.writeValueAsString(error);
            } catch (IOException jsonError) {
                return "{\"error\":\"PM4Py agent call failed: " + e.getMessage() + "\"}";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            try {
                Map<String, String> error = new HashMap<>();
                error.put("error", "PM4Py agent call interrupted");
                return objectMapper.writeValueAsString(error);
            } catch (IOException jsonError) {
                return "{\"error\":\"PM4Py agent call interrupted\"}";
            }
        }
    }
}
