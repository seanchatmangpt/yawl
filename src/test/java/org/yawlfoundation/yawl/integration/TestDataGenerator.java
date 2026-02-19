/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import static org.yawlfoundation.yawl.integration.TestConstants.*;

/**
 * Test data generation utilities for integration tests.
 *
 * <p>Provides fluent API for generating test data with Java 25 features
 * including records, sealed classes, and pattern matching support.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class TestDataGenerator {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final RandomGenerator random = RandomGenerator.getDefault();
    private static final SecureRandom secureRandom = new SecureRandom();

    private TestDataGenerator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ============ ID Generation ============

    /**
     * Generates a unique work item ID.
     *
     * @return unique work item ID in format WI-XXXXXXXX
     */
    public static String generateWorkItemId() {
        return "WI-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Generates multiple unique work item IDs.
     *
     * @param count number of IDs to generate
     * @return list of unique work item IDs
     */
    public static List<String> generateWorkItemIds(int count) {
        return IntStream.range(0, count)
            .mapToObj(_ -> generateWorkItemId())
            .toList();
    }

    /**
     * Generates a unique case ID.
     *
     * @return unique case ID
     */
    public static String generateCaseId() {
        return "case-" + UUID.randomUUID().toString().substring(0, 12);
    }

    /**
     * Generates a unique agent ID.
     *
     * @param prefix optional prefix for the agent ID
     * @return unique agent ID
     */
    public static String generateAgentId(String prefix) {
        String base = prefix != null ? prefix : "agent";
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Generates a unique agent ID with default prefix.
     *
     * @return unique agent ID
     */
    public static String generateAgentId() {
        return generateAgentId("agent");
    }

    /**
     * Generates a unique session handle.
     *
     * @return unique session handle (32 alphanumeric characters)
     */
    public static String generateSessionHandle() {
        StringBuilder sb = new StringBuilder(32);
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < 32; i++) {
            sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Generates a unique correlation ID for tracing.
     *
     * @return unique correlation ID
     */
    public static String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    // ============ A2A Message Generation ============

    /**
     * Creates a valid A2A handoff message.
     *
     * @param workItemId work item being handed off
     * @param fromAgent source agent
     * @param toAgent target agent
     * @return JSON string of the handoff message
     */
    public static String createHandoffMessage(String workItemId, String fromAgent, String toAgent) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode parts = root.putArray("parts");

        ObjectNode textPart = parts.addObject();
        textPart.put("type", "text");
        textPart.put("text", String.format("%s%s:%s:%s", A2A_HANDOFF_PREFIX, workItemId, fromAgent));

        ObjectNode dataPart = parts.addObject();
        dataPart.put("type", "data");
        ObjectNode data = dataPart.putObject("data");
        data.put("toAgent", toAgent);
        data.put("reason", "Integration test handoff");
        data.put("priority", PRIORITY_NORMAL);
        data.put("timestamp", Instant.now().toString());

        return root.toString();
    }

    /**
     * Creates a valid A2A handoff message with custom data.
     *
     * @param workItemId work item being handed off
     * @param fromAgent source agent
     * @param toAgent target agent
     * @param additionalData additional data to include
     * @return JSON string of the handoff message
     */
    public static String createHandoffMessage(String workItemId, String fromAgent, String toAgent,
                                               Map<String, Object> additionalData) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode parts = root.putArray("parts");

        ObjectNode textPart = parts.addObject();
        textPart.put("type", "text");
        textPart.put("text", String.format("%s%s:%s:%s", A2A_HANDOFF_PREFIX, workItemId, fromAgent));

        ObjectNode dataPart = parts.addObject();
        dataPart.put("type", "data");
        ObjectNode data = dataPart.putObject("data");
        data.put("toAgent", toAgent);
        data.put("reason", additionalData.getOrDefault("reason", "Integration test handoff").toString());
        data.put("priority", additionalData.getOrDefault("priority", PRIORITY_NORMAL).toString());
        data.put("timestamp", Instant.now().toString());

        additionalData.forEach((key, value) -> {
            if (!"reason".equals(key) && !"priority".equals(key)) {
                data.putPOJO(key, value);
            }
        });

        return root.toString();
    }

    /**
     * Creates a valid agent card JSON.
     *
     * @param agentName name of the agent
     * @param capabilities list of capabilities
     * @return JSON string of the agent card
     */
    public static String createAgentCard(String agentName, List<String> capabilities) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("name", agentName);
        root.put("version", "1.0.0");

        ArrayNode protocols = root.putArray("protocols");
        ObjectNode a2aProtocol = protocols.addObject();
        a2aProtocol.put("name", "a2a");
        a2aProtocol.put("version", A2A_PROTOCOL_VERSION);
        a2aProtocol.put("url", DEFAULT_MCP_SERVER_URI);

        ArrayNode skills = root.putArray("skills");
        capabilities.forEach(cap -> {
            ObjectNode skill = skills.addObject();
            skill.put("name", cap);
            skill.put("description", "Capability: " + cap);
        });

        return root.toString();
    }

    // ============ MCP Message Generation ============

    /**
     * Creates a valid MCP tool call request.
     *
     * @param callId unique call identifier
     * @param method method name
     * @param params parameters as map
     * @return JSON string of the tool call
     */
    public static String createMcpToolCall(String callId, String method, Map<String, Object> params) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("jsonrpc", JSON_RPC_VERSION);
        root.put("id", callId);
        root.put("method", method);

        ObjectNode paramsNode = root.putObject("params");
        params.forEach(paramsNode::putPOJO);

        return root.toString();
    }

    /**
     * Creates a valid MCP tool result response.
     *
     * @param callId matching call identifier
     * @param result result data
     * @return JSON string of the tool result
     */
    public static String createMcpToolResult(String callId, Map<String, Object> result) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("jsonrpc", JSON_RPC_VERSION);
        root.put("id", callId);

        ObjectNode resultNode = root.putObject("result");
        result.forEach(resultNode::putPOJO);

        return root.toString();
    }

    /**
     * Creates an MCP error response.
     *
     * @param callId matching call identifier
     * @param errorCode error code
     * @param errorMessage error message
     * @return JSON string of the error response
     */
    public static String createMcpError(String callId, int errorCode, String errorMessage) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("jsonrpc", JSON_RPC_VERSION);
        root.put("id", callId);

        ObjectNode errorNode = root.putObject("error");
        errorNode.put("code", errorCode);
        errorNode.put("message", errorMessage);

        return root.toString();
    }

    // ============ Workflow Data Generation ============

    /**
     * Creates test workflow data.
     *
     * @param caseId case identifier
     * @param specId specification identifier
     * @return workflow data map
     */
    public static Map<String, Object> createWorkflowData(String caseId, String specId) {
        Map<String, Object> data = new HashMap<>();
        data.put("caseId", caseId);
        data.put("specId", specId);
        data.put("timestamp", Instant.now().toString());
        data.put("status", "running");
        data.put("priority", PRIORITY_NORMAL);
        return data;
    }

    /**
     * Creates test work item data.
     *
     * @param workItemId work item identifier
     * @param caseId parent case identifier
     * @param taskId task identifier
     * @return work item data map
     */
    public static Map<String, Object> createWorkItemData(String workItemId, String caseId, String taskId) {
        Map<String, Object> data = new HashMap<>();
        data.put("workItemId", workItemId);
        data.put("caseId", caseId);
        data.put("taskId", taskId);
        data.put("status", "enabled");
        data.put("createdAt", Instant.now().toString());
        data.put("priority", PRIORITY_NORMAL);
        return data;
    }

    /**
     * Creates a batch of test work items.
     *
     * @param count number of work items to create
     * @param caseId parent case identifier
     * @return list of work item data maps
     */
    public static List<Map<String, Object>> createWorkItemBatch(int count, String caseId) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(createWorkItemData(
                generateWorkItemId(),
                caseId,
                DEFAULT_TASK_ID + "-" + i
            ));
        }
        return items;
    }

    // ============ Random Data Generation ============

    /**
     * Generates random alphanumeric string.
     *
     * @param length desired length
     * @return random alphanumeric string
     */
    public static String randomAlphanumeric(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Generates random integer in range.
     *
     * @param min minimum value (inclusive)
     * @param max maximum value (inclusive)
     * @return random integer in range
     */
    public static int randomInt(int min, int max) {
        return random.nextInt(min, max + 1);
    }

    /**
     * Generates random priority value.
     *
     * @return random priority string
     */
    public static String randomPriority() {
        String[] priorities = {PRIORITY_LOW, PRIORITY_NORMAL, PRIORITY_HIGH, PRIORITY_CRITICAL};
        return priorities[random.nextInt(priorities.length)];
    }

    /**
     * Generates random future timestamp.
     *
     * @param minMinutes minimum minutes from now
     * @param maxMinutes maximum minutes from now
     * @return future timestamp
     */
    public static Instant randomFutureTimestamp(int minMinutes, int maxMinutes) {
        return Instant.now().plus(randomInt(minMinutes, maxMinutes), ChronoUnit.MINUTES);
    }

    /**
     * Generates random past timestamp.
     *
     * @param minMinutes minimum minutes ago
     * @param maxMinutes maximum minutes ago
     * @return past timestamp
     */
    public static Instant randomPastTimestamp(int minMinutes, int maxMinutes) {
        return Instant.now().minus(randomInt(minMinutes, maxMinutes), ChronoUnit.MINUTES);
    }

    // ============ Large Payload Generation ============

    /**
     * Creates a large payload for stress testing.
     *
     * @param size approximate size in characters
     * @return large payload string
     */
    public static String createLargePayload(int size) {
        StringBuilder sb = new StringBuilder(size);
        while (sb.length() < size) {
            sb.append("Large payload data block ")
              .append(UUID.randomUUID())
              .append(" ");
        }
        return sb.substring(0, size);
    }

    /**
     * Creates nested JSON structure for deep validation testing.
     *
     * @param depth nesting depth
     * @return deeply nested JSON string
     */
    public static String createNestedJson(int depth) {
        return createNestedJsonHelper(depth);
    }

    private static String createNestedJsonHelper(int remainingDepth) {
        if (remainingDepth <= 0) {
            return "{\"value\":\"leaf\"}";
        }
        return String.format("{\"level\":%d,\"nested\":%s}",
            remainingDepth, createNestedJsonHelper(remainingDepth - 1));
    }

    // ============ Scenario Records ============

    /**
     * Test scenario for handoff operations.
     *
     * @param workItemId work item identifier
     * @param fromAgent source agent
     * @param toAgent target agent
     * @param sessionHandle engine session handle
     * @param reason handoff reason
     * @param priority priority level
     * @param ttl time-to-live duration
     */
    public record HandoffScenario(
        String workItemId,
        String fromAgent,
        String toAgent,
        String sessionHandle,
        String reason,
        String priority,
        java.time.Duration ttl
    ) {
        /**
         * Creates a default handoff scenario.
         *
         * @return default scenario
         */
        public static HandoffScenario defaultScenario() {
            return new HandoffScenario(
                generateWorkItemId(),
                DEFAULT_SOURCE_AGENT,
                DEFAULT_TARGET_AGENT,
                generateSessionHandle(),
                "Default test scenario",
                PRIORITY_NORMAL,
                DEFAULT_HANDOFF_TTL
            );
        }

        /**
         * Creates a scenario with custom agents.
         *
         * @param from source agent
         * @param to target agent
         * @return scenario with custom agents
         */
        public static HandoffScenario withAgents(String from, String to) {
            return new HandoffScenario(
                generateWorkItemId(),
                from,
                to,
                generateSessionHandle(),
                "Custom agent scenario",
                PRIORITY_NORMAL,
                DEFAULT_HANDOFF_TTL
            );
        }
    }

    /**
     * Test scenario for MCP tool calls.
     *
     * @param callId call identifier
     * @param method method name
     * @param params parameters map
     * @param expectSuccess whether success is expected
     * @param timeout timeout duration
     */
    public record McpToolScenario(
        String callId,
        String method,
        Map<String, Object> params,
        boolean expectSuccess,
        java.time.Duration timeout
    ) {
        /**
         * Creates a launch workflow scenario.
         *
         * @param specId specification identifier
         * @return launch workflow scenario
         */
        public static McpToolScenario launchWorkflow(String specId) {
            Map<String, Object> params = new HashMap<>();
            params.put("specificationId", specId);
            params.put("data", Map.of("caseName", "Test Case"));

            return new McpToolScenario(
                "call-" + UUID.randomUUID().toString().substring(0, 8),
                "launch_workflow",
                params,
                true,
                DEFAULT_MCP_TIMEOUT
            );
        }

        /**
         * Creates a cancel workflow scenario.
         *
         * @param caseId case identifier
         * @return cancel workflow scenario
         */
        public static McpToolScenario cancelWorkflow(String caseId) {
            Map<String, Object> params = new HashMap<>();
            params.put("caseId", caseId);

            return new McpToolScenario(
                "call-" + UUID.randomUUID().toString().substring(0, 8),
                "cancel_workflow",
                params,
                true,
                DEFAULT_MCP_TIMEOUT
            );
        }
    }
}
