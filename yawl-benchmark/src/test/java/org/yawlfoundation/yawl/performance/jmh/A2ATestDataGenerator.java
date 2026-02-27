/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.performance.jmh;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Test data generator for A2A communication benchmarks.
 *
 * Generates realistic test data patterns for different types of A2A messages:
 * - Simple messages (ping/pong)
 * - Workflow launch messages
 * - Work item management messages
 * - Query messages
 * - Large messages for serialization testing
 *
 * @author YAWL Performance Team
 * @version 6.0.0
 */
public class A2ATestDataGenerator {

    // Sample workflow specifications for testing
    private static final String[] WORKFLOW_SPECS = {
        "SimpleCase:1.0",
        "OrderProcessing:2.1",
        "InvoiceApproval:1.5",
        "EmployeeOnboarding:3.0",
        "CustomerSupport:1.2"
    };

    // Sample workflow statuses
    private static final String[] WORKFLOW_STATUSES = {
        "running", "completed", "cancelled", "failed", "paused"
    };

    // Sample work item statuses
    private static final String[] WORKITEM_STATUSES = {
        "enabled", "allocated", "started", "completed", "failed", "abandoned"
    };

    // Sample agent names
    private static final String[] AGENT_NAMES = {
        "agent-1", "agent-2", "agent-3", "agent-4", "agent-5",
        "workflow-agent", "specialized-agent", "general-agent"
    };

    // Sample error messages
    private static final String[] ERROR_MESSAGES = {
        "Invalid specification ID",
        "Insufficient permissions",
        "Work item not found",
        "Case already completed",
        "Engine connection failed",
        "Authentication required",
        "Rate limit exceeded"
    };

    /**
     * Generates a simple ping/pong message payload.
     */
    public static Map<String, Object> generatePingMessage(String testCaseId) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "ping");
        message.put("testCaseId", testCaseId);
        message.put("timestamp", Instant.now().toEpochMilli());
        message.put("message", "ping");
        message.put("metadata", Map.of(
            "version", "1.0",
            "priority", "normal"
        ));
        return message;
    }

    /**
     * Generates a workflow launch message payload.
     */
    public static Map<String, Object> generateWorkflowLaunchMessage(String testCaseId) {
        String specId = WORKFLOW_SPECS[ThreadLocalRandom.current().nextInt(WORKFLOW_SPECS.length)];
        
        Map<String, Object> caseData = new HashMap<>();
        caseData.put("requester", generateRandomAgent());
        caseData.put("priority", ThreadLocalRandom.current().nextBoolean() ? "high" : "normal");
        caseData.put("metadata", Map.of(
            "department", getRandomDepartment(),
            "project", getRandomProject(),
            "createdBy", "benchmark-test"
        ));
        
        Map<String, Object> message = new HashMap<>();
        message.put("type", "workflow_launch");
        message.put("testCaseId", testCaseId);
        message.put("specificationId", specId);
        message.put("caseData", caseData);
        message.put("timestamp", Instant.now().toEpochMilli());
        message.put("expectedDuration", estimateWorkflowDuration(specId));
        
        return message;
    }

    /**
     * Generates a work item management message payload.
     */
    public static Map<String, Object> generateWorkItemMessage(String testCaseId) {
        String action = Arrays.asList("list", "checkout", "complete", "cancel")
            .get(ThreadLocalRandom.current().nextInt(4));
        
        Map<String, Object> message = new HashMap<>();
        message.put("type", "workitem_" + action);
        message.put("testCaseId", testCaseId);
        message.put("action", action);
        message.put("workItemId", generateWorkItemId());
        message.put("caseId", testCaseId);
        
        if (action.equals("complete")) {
            message.put("result", Map.of(
                "status", ThreadLocalRandom.current().nextBoolean() ? "approved" : "rejected",
                "comments", getRandomDecisionComments()
            ));
        }
        
        message.put("timestamp", Instant.now().toEpochMilli());
        
        return message;
    }

    /**
     * Generates a query message payload.
     */
    public static Map<String, Object> generateQueryMessage(String testCaseId) {
        String queryType = Arrays.asList(
            "workflows", "workitems", "agents", "statistics", "performance"
        ).get(ThreadLocalRandom.current().nextInt(5));
        
        Map<String, Object> message = new HashMap<>();
        message.put("type", "query_" + queryType);
        message.put("testCaseId", testCaseId);
        message.put("queryType", queryType);
        message.put("parameters", generateQueryParameters(queryType));
        message.put("timestamp", Instant.now().toEpochMilli());
        
        return message;
    }

    /**
     * Generates a large message for serialization testing.
     */
    public static Map<String, Object> generateLargeMessage(String testCaseId) {
        Map<String, Object> largeMessage = new HashMap<>();
        
        // Base message
        largeMessage.put("type", "large_message");
        largeMessage.put("testCaseId", testCaseId);
        largeMessage.put("timestamp", Instant.now().toEpochMilli());
        largeMessage.put("size", "large");
        
        // Large nested data structures
        Map<String, Object> largeData = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            largeData.put("field_" + i, generateFieldValue(i));
        }
        largeMessage.put("largeData", largeData);
        
        // Large array of objects
        List<Object> largeArray = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            largeArray.add(Map.of(
                "id", i,
                "name", "item_" + i,
                "value", ThreadLocalRandom.current().nextDouble() * 1000,
                "metadata", generateMetadataObject(i)
            ));
        }
        largeMessage.put("largeArray", largeArray);
        
        // Nested structures
        List<Map<String, Object>> nestedStructures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Map<String, Object> nested = new HashMap<>();
            nested.put("level", i);
            nested.put("data", generateRandomData(i));
            nestedStructures.add(nested);
        }
        largeMessage.put("nestedStructures", nestedStructures);
        
        return largeMessage;
    }

    /**
     * Generates an error message payload.
     */
    public static Map<String, Object> generateErrorMessage(String testCaseId) {
        String errorMessage = ERROR_MESSAGES[ThreadLocalRandom.current().nextInt(ERROR_MESSAGES.length)];
        String severity = Arrays.asList("info", "warn", "error", "critical")
            .get(ThreadLocalRandom.current().nextInt(4));
        
        Map<String, Object> message = new HashMap<>();
        message.put("type", "error");
        message.put("testCaseId", testCaseId);
        message.put("severity", severity);
        message.put("message", errorMessage);
        message.put("timestamp", Instant.now().toEpochMilli());
        message.put("stackTrace", generateStackTrace());
        
        return message;
    }

    /**
     * Generates concurrent test data for stress testing.
     */
    public static List<Map<String, Object>> generateConcurrentTestData(int count) {
        List<Map<String, Object>> testData = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            String testCaseId = "concurrent-test-" + i;
            
            // Distribute different message types
            int messageType = i % 5;
            switch (messageType) {
                case 0:
                    testData.add(generatePingMessage(testCaseId));
                    break;
                case 1:
                    testData.add(generateWorkflowLaunchMessage(testCaseId));
                    break;
                case 2:
                    testData.add(generateWorkItemMessage(testCaseId));
                    break;
                case 3:
                    testData.add(generateQueryMessage(testCaseId));
                    break;
                case 4:
                    testData.add(generateLargeMessage(testCaseId));
                    break;
            }
        }
        
        return testData;
    }

    /**
     * Generates network partition test data.
     */
    public static List<Map<String, Object>> generatePartitionTestData() {
        List<Map<String, Object>> testData = new ArrayList<>();
        
        // Mix of normal and partitioned requests
        for (int i = 0; i < 100; i++) {
            String testCaseId = "partition-test-" + i;
            
            if (i % 10 == 0) {
                // Partition request - simulate unreachable server
                Map<String, Object> message = generatePingMessage(testCaseId);
                message.put("destination", "unreachable-server.invalid");
                message.put("timeout", 100);
                testData.add(message);
            } else {
                // Normal request
                testData.add(generatePingMessage(testCaseId));
            }
        }
        
        return testData;
    }

    // Helper methods

    private static String generateWorkItemId() {
        return "WI-" + ThreadLocalRandom.current().nextInt(10000, 99999);
    }

    private static String generateRandomAgent() {
        return AGENT_NAMES[ThreadLocalRandom.current().nextInt(AGENT_NAMES.length)];
    }

    private static String getRandomDepartment() {
        String[] departments = {"engineering", "marketing", "sales", "hr", "finance"};
        return departments[ThreadLocalRandom.current().nextInt(departments.length)];
    }

    private static String getRandomProject() {
        String[] projects = {"alpha", "beta", "gamma", "delta", "epsilon"};
        return projects[ThreadLocalRandom.current().nextInt(projects.length)];
    }

    private static long estimateWorkflowDuration(String specId) {
        // Simple estimation based on spec name
        if (specId.contains("Simple")) return 5000L;
        if (specId.contains("Order")) return 15000L;
        if (specId.contains("Invoice")) return 10000L;
        if (specId.contains("Employee")) return 25000L;
        if (specId.contains("Customer")) return 20000L;
        return 10000L; // default
    }

    private static Map<String, Object> generateQueryParameters(String queryType) {
        Map<String, Object> params = new HashMap<>();
        
        switch (queryType) {
            case "workflows":
                params.put("status", WORKFLOW_STATUSES[ThreadLocalRandom.current().nextInt(WORKFLOW_STATUSES.length)]);
                params.put("limit", ThreadLocalRandom.current().nextInt(10, 100));
                break;
            case "workitems":
                params.put("status", WORKITEM_STATUSES[ThreadLocalRandom.current().nextInt(WORKITEM_STATUSES.length)]);
                params.put("allocatedTo", ThreadLocalRandom.current().nextBoolean() ? generateRandomAgent() : null);
                break;
            case "agents":
                params.put("status", ThreadLocalRandom.current().nextBoolean() ? "online" : "offline");
                params.put("skills", Arrays.asList("workflow", "query", "management"));
                break;
            case "statistics":
                params.put("timeRange", "last_24_hours");
                params.put("metrics", Arrays.asList("throughput", "latency", "errors"));
                break;
            case "performance":
                params.put("timeRange", "last_hour");
                params.put("includeDetails", true);
                break;
        }
        
        return params;
    }

    private static String getRandomDecisionComments() {
        String[] comments = {
            "Approved based on guidelines",
            "Rejected due to insufficient information",
            "Requires additional review",
            "Approved with conditions",
            "Rejected - policy violation"
        };
        return comments[ThreadLocalRandom.current().nextInt(comments.length)];
    }

    private static Object generateFieldValue(int index) {
        int fieldType = index % 4;
        switch (fieldType) {
            case 0:
                return "string_value_" + index;
            case 1:
                return ThreadLocalRandom.current().nextDouble() * 1000;
            case 2:
                return ThreadLocalRandom.current().nextBoolean();
            case 3:
                return Arrays.asList("item1", "item2", "item3");
            default:
                return null;
        }
    }

    private static Map<String, Object> generateMetadataObject(int index) {
        return Map.of(
            "created", Instant.now().toEpochMilli(),
            "modified", Instant.now().toEpochMilli(),
            "version", 1.0,
            "tags", Arrays.asList("tag1", "tag2"),
            "metadata_id", "meta-" + index
        );
    }

    private static Object generateRandomData(int level) {
        switch (level % 3) {
            case 0:
                return Map.of("level", level, "type", "object");
            case 1:
                return Arrays.asList("item1", "item2", "item3");
            case 2:
                return "string_data_level_" + level;
            default:
                return null;
        }
    }

    private static String generateStackTrace() {
        return Arrays.asList(
            "at org.yawlfoundation.yawl.integration.a2a.YawlA2AServer.processWorkflowRequest(YawlA2AServer.java:579)",
            "at org.yawlfoundation.yawl.integration.a2a.YawlA2AServer$YawlAgentExecutor.execute(YawlA2AServer.java:547)",
            "at io.a2a.server.AgentExecutor.execute(AgentExecutor.java:45)",
            "at io.a2a.server.DefaultRequestHandler.handle(DefaultRequestHandler.java:234)"
        ).join("\n");
    }
}
