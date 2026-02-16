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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTTP client for PM4Py A2A agent. Sends JSON messages and extracts text response.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class Pm4PyClient {

    private final String agentUrl;

    public Pm4PyClient(String agentUrl) {
        if (agentUrl == null || agentUrl.isEmpty()) {
            throw new IllegalArgumentException("agentUrl is required");
        }
        this.agentUrl = agentUrl.endsWith("/") ? agentUrl : agentUrl + "/";
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
        StringBuilder payload = new StringBuilder();
        payload.append("{\"skill\":\"").append(escapeJson(skill)).append("\"");
        payload.append(",\"xes_input\":\"").append(escapeJson(xesInput)).append("\"");
        if (extraArgs != null) {
            for (Map.Entry<String, String> e : extraArgs.entrySet()) {
                payload.append(",\"").append(escapeJson(e.getKey())).append("\":\"");
                payload.append(escapeJson(e.getValue())).append("\"");
            }
        }
        payload.append("}");

        String messageBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"message/send\",\"params\":"
            + "{\"message\":{\"role\":\"user\",\"parts\":[{\"kind\":\"text\",\"text\":"
            + escapeJson(payload.toString()) + "}]}}}";

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(agentUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(messageBody.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            String body = new BufferedReader(new InputStreamReader(
                code >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8))
                .lines().collect(Collectors.joining("\n"));

            if (code >= 400) {
                return "{\"error\":\"PM4Py agent HTTP " + code + ": " + body + "\"}";
            }
            return body;
        } catch (Exception e) {
            return "{\"error\":\"PM4Py agent call failed: " + e.getMessage() + "\"}";
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
