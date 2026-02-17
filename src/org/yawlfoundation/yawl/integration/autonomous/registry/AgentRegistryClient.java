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

package org.yawlfoundation.yawl.integration.autonomous.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Client library for interacting with the Agent Registry.
 *
 * Provides methods for agent registration, heartbeat, unregistration,
 * and capability-based agent discovery.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class AgentRegistryClient {

    private static final Logger logger = LogManager.getLogger(AgentRegistryClient.class);

    private final String registryHost;
    private final int registryPort;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    /**
     * Create client with default timeouts (5 seconds connect, 10 seconds read).
     *
     * @param registryHost registry server host
     * @param registryPort registry server port
     */
    public AgentRegistryClient(String registryHost, int registryPort) {
        this(registryHost, registryPort, 5000, 10000);
    }

    /**
     * Create client with custom timeouts.
     *
     * @param registryHost registry server host
     * @param registryPort registry server port
     * @param connectTimeoutMs connection timeout in milliseconds
     * @param readTimeoutMs read timeout in milliseconds
     */
    public AgentRegistryClient(String registryHost, int registryPort,
                               int connectTimeoutMs, int readTimeoutMs) {
        if (registryHost == null || registryHost.isBlank()) {
            throw new IllegalArgumentException("registryHost is required");
        }
        if (registryPort < 1 || registryPort > 65535) {
            throw new IllegalArgumentException("registryPort must be between 1 and 65535");
        }
        if (connectTimeoutMs < 0) {
            throw new IllegalArgumentException("connectTimeoutMs must be non-negative");
        }
        if (readTimeoutMs < 0) {
            throw new IllegalArgumentException("readTimeoutMs must be non-negative");
        }

        this.registryHost = registryHost.strip();
        this.registryPort = registryPort;
        this.connectTimeout = Duration.ofMillis(connectTimeoutMs);
        this.readTimeout = Duration.ofMillis(readTimeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.connectTimeout)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Register an agent with the registry.
     *
     * @param agentInfo agent information
     * @return true if registration succeeded
     * @throws IOException if network error occurs
     */
    public boolean register(AgentInfo agentInfo) throws IOException {
        if (agentInfo == null) {
            throw new IllegalArgumentException("agentInfo is required");
        }

        String url = "http://%s:%d/agents/register".formatted(registryHost, registryPort);
        String response = sendPost(url, agentInfo.toJson());

        logger.info("Registered agent {} with registry at {}:{}",
                   agentInfo.getId(), registryHost, registryPort);
        return response.contains("\"status\":\"registered\"");
    }

    /**
     * Send heartbeat for an agent.
     *
     * @param agentId agent identifier
     * @return true if heartbeat succeeded
     * @throws IOException if network error occurs
     */
    public boolean sendHeartbeat(String agentId) throws IOException {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId is required");
        }

        String url = "http://%s:%d/agents/%s/heartbeat".formatted(
                                  registryHost, registryPort, agentId.strip());
        String response = sendPost(url, "{}");

        logger.debug("Sent heartbeat for agent {}", agentId);
        return response.contains("\"status\":\"ok\"");
    }

    /**
     * Unregister an agent from the registry.
     *
     * @param agentId agent identifier
     * @return true if unregistration succeeded
     * @throws IOException if network error occurs
     */
    public boolean unregister(String agentId) throws IOException {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId is required");
        }

        String url = "http://%s:%d/agents/%s".formatted(
                                  registryHost, registryPort, agentId.strip());
        String response = sendDelete(url);

        logger.info("Unregistered agent {} from registry", agentId);
        return response.contains("\"status\":\"unregistered\"");
    }

    /**
     * Query agents by domain capability.
     *
     * @param domain domain name to search for
     * @return list of matching agents
     * @throws IOException if network error occurs
     */
    public List<AgentInfo> queryByCapability(String domain) throws IOException {
        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException("domain is required");
        }

        String url = "http://%s:%d/agents/by-capability?domain=%s".formatted(
                                  registryHost, registryPort, urlEncode(domain.strip()));
        String response = sendGet(url);

        return parseAgentList(response);
    }

    /**
     * List all registered agents.
     *
     * @return list of all agents
     * @throws IOException if network error occurs
     */
    public List<AgentInfo> listAll() throws IOException {
        String url = "http://%s:%d/agents".formatted(registryHost, registryPort);
        String response = sendGet(url);
        return parseAgentList(response);
    }

    private String sendPost(String urlString, String body) throws IOException {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .timeout(readTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP %d: %s".formatted(response.statusCode(), response.body()));
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    private String sendGet(String urlString) throws IOException {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .timeout(readTimeout)
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP %d: %s".formatted(response.statusCode(), response.body()));
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    private String sendDelete(String urlString) throws IOException {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .timeout(readTimeout)
                    .DELETE()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP %d: %s".formatted(response.statusCode(), response.body()));
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    private List<AgentInfo> parseAgentList(String json) {
        var agents = new ArrayList<AgentInfo>();

        if (json == null || json.isBlank()) {
            return agents;
        }

        String trimmed = json.strip();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            throw new IllegalArgumentException("Expected JSON array but got: " + json);
        }

        String content = trimmed.substring(1, trimmed.length() - 1).strip();
        if (content.isEmpty()) {
            return agents;
        }

        int depth = 0;
        int start = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    String agentJson = content.substring(start, i + 1).strip();
                    agents.add(AgentInfo.fromJson(agentJson));
                    start = i + 1;
                    while (start < content.length() &&
                           (content.charAt(start) == ',' ||
                            Character.isWhitespace(content.charAt(start)))) {
                        start++;
                    }
                    i = start - 1;
                }
            }
        }

        return agents;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
