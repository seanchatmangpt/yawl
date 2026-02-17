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

import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;
import org.yawlfoundation.yawl.util.SafeNumberParser;

/**
 * Agent registration information for the agent registry.
 *
 * This represents a registered agent with its connection details,
 * capability, and health tracking.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class AgentInfo {

    private final String id;
    private final String name;
    private final AgentCapability capability;
    private final String host;
    private final int port;
    private volatile long lastHeartbeat;

    /**
     * Create agent registration information.
     *
     * @param id unique agent identifier
     * @param name human-readable agent name
     * @param capability agent's domain capability
     * @param host agent's host address
     * @param port agent's listening port
     */
    public AgentInfo(String id, String name, AgentCapability capability,
                     String host, int port) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id is required");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name is required");
        }
        if (capability == null) {
            throw new IllegalArgumentException("capability is required");
        }
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("host is required");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }

        this.id = id.trim();
        this.name = name.trim();
        this.capability = capability;
        this.host = host.trim();
        this.port = port;
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public AgentCapability getCapability() {
        return capability;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    /**
     * Update the last heartbeat timestamp to current time.
     */
    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }

    /**
     * Check if agent is considered alive based on heartbeat timeout.
     *
     * @param timeoutMillis maximum time since last heartbeat
     * @return true if agent has sent heartbeat within timeout
     */
    public boolean isAlive(long timeoutMillis) {
        return (System.currentTimeMillis() - lastHeartbeat) <= timeoutMillis;
    }

    /**
     * Convert to JSON representation.
     *
     * @return JSON string
     */
    public String toJson() {
        return String.format(
            "{\"id\":\"%s\",\"name\":\"%s\",\"capability\":{\"domainName\":\"%s\"," +
            "\"description\":\"%s\"},\"host\":\"%s\",\"port\":%d,\"lastHeartbeat\":%d}",
            escapeJson(id),
            escapeJson(name),
            escapeJson(capability.domainName()),
            escapeJson(capability.description()),
            escapeJson(host),
            port,
            lastHeartbeat
        );
    }

    /**
     * Parse from JSON representation.
     *
     * @param json JSON string
     * @return AgentInfo instance
     */
    public static AgentInfo fromJson(String json) {
        String id = extractJsonField(json, "id");
        String name = extractJsonField(json, "name");
        String domainName = extractNestedJsonField(json, "capability", "domainName");
        String description = extractNestedJsonField(json, "capability", "description");
        String host = extractJsonField(json, "host");
        int port = SafeNumberParser.parseIntOrThrow(extractJsonField(json, "port"), "agent port in registry JSON");

        AgentCapability capability = new AgentCapability(domainName, description);
        AgentInfo info = new AgentInfo(id, name, capability, host, port);

        String heartbeatStr = extractJsonField(json, "lastHeartbeat");
        if (heartbeatStr != null && !heartbeatStr.isEmpty()) {
            info.lastHeartbeat = SafeNumberParser.parseLongOrThrow(heartbeatStr, "agent lastHeartbeat in registry JSON");
        }

        return info;
    }

    private static String extractJsonField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) {
            return null;
        }
        start += pattern.length();

        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }

        if (start >= json.length()) {
            return null;
        }

        if (json.charAt(start) == '"') {
            start++;
            int end = json.indexOf('"', start);
            if (end == -1) {
                return null;
            }
            return json.substring(start, end);
        } else {
            int end = start;
            while (end < json.length() &&
                   json.charAt(end) != ',' &&
                   json.charAt(end) != '}' &&
                   json.charAt(end) != ']') {
                end++;
            }
            return json.substring(start, end).trim();
        }
    }

    private static String extractNestedJsonField(String json, String parentField,
                                                  String childField) {
        String parentPattern = "\"" + parentField + "\":";
        int parentStart = json.indexOf(parentPattern);
        if (parentStart == -1) {
            return null;
        }
        parentStart += parentPattern.length();

        int objectStart = json.indexOf('{', parentStart);
        if (objectStart == -1) {
            return null;
        }

        int objectEnd = json.indexOf('}', objectStart);
        if (objectEnd == -1) {
            return null;
        }

        String objectJson = json.substring(objectStart, objectEnd + 1);
        return extractJsonField(objectJson, childField);
    }

    private static String escapeJson(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot escape null JSON value");
        }
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    @Override
    public String toString() {
        return String.format("AgentInfo[id=%s, name=%s, capability=%s, host=%s:%d]",
                           id, name, capability, host, port);
    }
}
