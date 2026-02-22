/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.benchmark;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Generator of realistic test data for YAWL integration benchmarks.
 *
 * <p>Creates synthetic workflow specifications, work items, and AI prompts
 * that simulate real-world usage patterns.
 *
 * <p>Data Generation Categories:
 * <ul>
 *   <li>Workflow Specifications - XML-based YAWL workflow definitions</li>
 *   <li>Work Items - Task records with case data</li>
 *   <li>A2A Requests - Agent-to-agent message payloads</li>
 *   <li>MCP Tool Calls - Model Context Protocol tool invocations</li>
 *   <li>Z.ai Prompts - AI generation prompts</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class TestDataGenerator {

    // Workflow specification templates
    private static final String[] WORKFLOW_TYPES = {
        "Order Processing", "Invoice Approval", "Employee Onboarding",
        "Customer Support", "Expense Reimbursement", "Contract Review",
        "Travel Request", "Hiring Process", "Compliance Check", "Service Request"
    };

    private static final String[] TASK_NAMES = {
        "Review", "Approve", "Submit", "Verify", "Process", "Route",
        "Assign", "Complete", "Reject", "Escalate", "Notify", "Archive"
    };

    private static final String[] DEPARTMENTS = {
        "Finance", "HR", "IT", "Operations", "Legal", "Marketing",
        "Sales", "Customer Service", "R&D", "Quality Assurance"
    };

    private static final String[] CUSTOMERS = {
        "Acme Corporation", "Global Industries", "Tech Solutions Inc",
        "Manufacturing Co", "Service Providers LLC", "Data Systems Ltd",
        "Logistics Plus", "Innovation Group", "Professional Services Inc"
    };

    private static final String[] STATUSES = {
        "offered", "allocated", "started", "suspended", "completed", "failed"
    };

    // AI prompt templates
    private static final String[] ZAI_PROMPTS = {
        "Analyze this workflow for potential bottlenecks and optimization opportunities.",
        "Generate a detailed workflow analysis with recommendations for improvement.",
        "Review the following workflow specification for best practices compliance.",
        "Identify any risks or security concerns in this workflow design.",
        "Create test cases to verify the correctness of this workflow implementation.",
        "Generate documentation for this workflow including input/output specifications.",
        "Analyze the performance implications of this workflow design.",
        "Suggest automation opportunities for manual tasks in this workflow."
    };

    // MCP tool names
    private static final String[] MCP_TOOLS = {
        "launch_case", "cancel_case", "get_case_status", "get_specification",
        "list_specifications", "get_work_items", "complete_work_item",
        "checkout_work_item", "checkin_work_item", "get_running_cases"
    };

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Generates a random workflow specification XML
     */
    public static String generateWorkflowSpecification() {
        String workflowId = "workflow-" + UUID.randomUUID().toString().substring(0, 8);
        String workflowName = WORKFLOW_TYPES[ThreadLocalRandom.current().nextInt(WORKFLOW_TYPES.length)];
        int taskCount = ThreadLocalRandom.current().nextInt(3, 8);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<specification xmlns=\"http://www.yawlfoundation.org/yawlschema\">");
        xml.append("<").append(workflowId).append(">");
        xml.append("<name>").append(workflowName).append("</name>");
        xml.append("<description>A sample ").append(workflowName).append(" workflow</description>");

        // Generate input parameters
        xml.append("<input>");
        xml.append("<parameters>");
        for (int i = 0; i < ThreadLocalRandom.current().nextInt(2, 5); i++) {
            xml.append("<parameter>");
            xml.append("<name>param").append(i + 1).append("</name>");
            xml.append("<type>string</type>");
            xml.append("<required>true</required>");
            xml.append("</parameter>");
        }
        xml.append("</parameters>");
        xml.append("</input>");

        // Generate output parameters
        xml.append("<output>");
        xml.append("<parameters>");
        xml.append("<parameter>");
        xml.append("<name>result</name>");
        xml.append("<type>string</type>");
        xml.append("</parameter>");
        xml.append("<parameter>");
        xml.append("<name>status</name>");
        xml.append("<type>string</type>");
        xml.append("</parameter>");
        xml.append("</parameters>");
        xml.append("</output>");

        // Generate tasks
        for (int i = 0; i < taskCount; i++) {
            xml.append("<task>");
            xml.append("<id>task").append(i + 1).append("</id>");
            xml.append("<name>").append(TASK_NAMES[ThreadLocalRandom.current().nextInt(TASK_NAMES.length)])
               .append("</name>");
            xml.append("<input>");
            xml.append("<parameters>");
            if (i > 0) {
                xml.append("<parameter>");
                xml.append("<name>outputFromTask").append(i).append("</name>");
                xml.append("<type>string</type>");
                xml.append("</parameter>");
            }
            xml.append("</parameters>");
            xml.append("</input>");
            xml.append("<output>");
            xml.append("<parameters>");
            xml.append("<parameter>");
            xml.append("<name>output").append(i + 1).append("</name>");
            xml.append("<type>string</type>");
            xml.append("</parameter>");
            xml.append("</parameters>");
            xml.append("</output>");
            xml.append("<completionCondition>true</completionCondition>");
            xml.append("</task>");
        }

        xml.append("<controlflows>");
        // Simple sequence flow
        for (int i = 0; i < taskCount - 1; i++) {
            xml.append("<flow>");
            xml.append("<source>task").append(i + 1).append("</source>");
            xml.append("<target>task").append(i + 2).append("</target>");
            xml.append("<type>sequence</type>");
            xml.append("</flow>");
        }
        xml.append("</controlflows>");

        xml.append("</").append(workflowId).append(">");
        xml.append("</specification>");

        return xml.toString();
    }

    /**
     * Generates a random work item record
     */
    public static Map<String, Object> generateWorkItem() {
        String workItemId = "wi-" + UUID.randomUUID().toString().substring(0, 12);
        String taskId = "task-" + ThreadLocalRandom.current().nextInt(1, 10);
        String caseId = "case-" + ThreadLocalRandom.current().nextInt(1000, 9999);
        String department = DEPARTMENTS[ThreadLocalRandom.current().nextInt(DEPARTMENTS.length)];

        Map<String, Object> workItem = new LinkedHashMap<>();
        workItem.put("id", workItemId);
        workItem.put("caseId", caseId);
        workItem.put("taskId", taskId);
        workItem.put("name", TASK_NAMES[ThreadLocalRandom.current().nextInt(TASK_NAMES.length)] + " Task");
        workItem.put("status", STATUSES[ThreadLocalRandom.current().nextInt(STATUSES.length)]);
        workItem.put("department", department);
        workItem.put("priority", ThreadLocalRandom.current().nextInt(1, 6));
        workItem.put("created", generateTimestamp());
        workItem.put("due", generateTimestamp(24 * 60 * 60 * 1000)); // 24 hours from now

        // Add workflow-specific data
        switch (taskId) {
            case "task-1":
                workItem.put("customer", CUSTOMERS[ThreadLocalRandom.current().nextInt(CUSTOMERS.length)]);
                workItem.put("amount", ThreadLocalRandom.current().nextInt(100, 10000));
                break;
            case "task-2":
                workItem.put("approver", "manager-" + ThreadLocalRandom.current().nextInt(1, 20));
                workItem.put("budget", ThreadLocalRandom.current().nextInt(1000, 100000));
                break;
            case "task-3":
                workItem.put("employee", "emp-" + ThreadLocalRandom.current().nextInt(1000, 9999));
                workItem.put("location", "office-" + ThreadLocalRandom.current().nextInt(1, 50));
                break;
            default:
                workItem.put("assignee", "user-" + ThreadLocalRandom.current().nextInt(1, 100));
                break;
        }

        return workItem;
    }

    /**
     * Generates random test data for A2A requests
     */
    public static Map<String, Object> generateA2ARequest(String operationType) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("operation", operationType);
        request.put("timestamp", System.currentTimeMillis());
        request.put("requestId", UUID.randomUUID().toString());

        switch (operationType) {
            case "launch_workflow":
                request.put("specId", "OrderProcessing");
                request.put("data", Map.of(
                    "orderId", ThreadLocalRandom.current().nextInt(10000, 99999),
                    "customer", CUSTOMERS[ThreadLocalRandom.current().nextInt(CUSTOMERS.length)],
                    "amount", ThreadLocalRandom.current().nextInt(100, 10000),
                    "priority", ThreadLocalRandom.current().nextInt(1, 6)
                ));
                break;

            case "manage_workitems":
                request.put("caseId", "case-" + ThreadLocalRandom.current().nextInt(1000, 9999));
                request.put("action", ThreadLocalRandom.current().nextBoolean() ? "claim" : "complete");
                List<Map<String, Object>> workItems = new ArrayList<>();
                for (int i = 0; i < ThreadLocalRandom.current().nextInt(1, 5); i++) {
                    workItems.add(generateWorkItem());
                }
                request.put("workItems", workItems);
                break;

            case "query_workflows":
                request.put("filter", ThreadLocalRandom.current().nextBoolean() ? "running" : "all");
                request.put("department", DEPARTMENTS[ThreadLocalRandom.current().nextInt(DEPARTMENTS.length)]);
                break;

            case "cancel_workflow":
                request.put("caseId", "case-" + ThreadLocalRandom.current().nextInt(1000, 9999));
                request.put("reason", "Cancellation requested by user");
                break;

            default:
                request.put("action", "query");
                break;
        }

        return request;
    }

    /**
     * Generates MCP tool call request payload
     */
    public static Map<String, Object> generateMcpToolCall(String toolName) {
        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("tool", toolName != null ? toolName : MCP_TOOLS[ThreadLocalRandom.current().nextInt(MCP_TOOLS.length)]);
        toolCall.put("timestamp", System.currentTimeMillis());

        Map<String, Object> args = new LinkedHashMap<>();

        switch ((String) toolCall.get("tool")) {
            case "launch_case":
                args.put("specId", "OrderProcessing");
                args.put("caseData", generateCaseData());
                break;
            case "cancel_case":
                args.put("caseId", "case-" + ThreadLocalRandom.current().nextInt(1000, 9999));
                break;
            case "get_case_status":
                args.put("caseId", "case-" + ThreadLocalRandom.current().nextInt(1000, 9999));
                break;
            case "get_work_items":
                args.put("caseId", "case-" + ThreadLocalRandom.current().nextInt(1000, 9999));
                break;
            case "complete_work_item":
                args.put("workItemId", "wi-" + UUID.randomUUID().toString().substring(0, 12));
                args.put("data", generateCaseData());
                break;
            default:
                args.put("query", "all");
                break;
        }

        toolCall.put("arguments", args);
        return toolCall;
    }

    /**
     * Generates case data for workflow operations
     */
    public static Map<String, Object> generateCaseData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", "ORD-" + ThreadLocalRandom.current().nextInt(10000, 99999));
        data.put("customerName", CUSTOMERS[ThreadLocalRandom.current().nextInt(CUSTOMERS.length)]);
        data.put("amount", ThreadLocalRandom.current().nextInt(100, 10000));
        data.put("currency", "USD");
        data.put("priority", ThreadLocalRandom.current().nextInt(1, 6));
        data.put("department", DEPARTMENTS[ThreadLocalRandom.current().nextInt(DEPARTMENTS.length)]);
        data.put("timestamp", System.currentTimeMillis());
        data.put("metadata", Map.of(
            "source", "benchmark",
            "version", "1.0"
        ));
        return data;
    }

    /**
     * Generates Z.ai prompt test data
     */
    public static String generateZaiPrompt(String promptType) {
        StringBuilder prompt = new StringBuilder();

        switch (promptType) {
            case "workflow_analysis":
                prompt.append("Analyze the following YAWL workflow for efficiency and compliance:\n\n");
                prompt.append(generateWorkflowSpecification());
                prompt.append("\n\nProvide specific recommendations for improvement.");
                break;

            case "decision_generation":
                prompt.append("Generate a decision for the following workflow:\n\n");
                prompt.append("Case ID: case-").append(ThreadLocalRandom.current().nextInt(1000, 9999)).append("\n");
                prompt.append("Task: ").append(TASK_NAMES[ThreadLocalRandom.current().nextInt(TASK_NAMES.length)]).append("\n");
                prompt.append("Data: ").append(generateWorkItem());
                prompt.append("\n\nConsider urgency, budget, and department policies.");
                break;

            case "data_transformation":
                prompt.append("Transform the following workflow data into JSON format:\n\n");
                prompt.append("Order ID: ").append(ThreadLocalRandom.current().nextInt(10000, 99999)).append("\n");
                prompt.append("Customer: ").append(CUSTOMERS[ThreadLocalRandom.current().nextInt(CUSTOMERS.length)]).append("\n");
                prompt.append("Amount: $").append(ThreadLocalRandom.current().nextInt(100, 10000)).append("\n");
                prompt.append("Status: ").append(ThreadLocalRandom.current().nextBoolean() ? "Pending" : "Approved");
                break;

            case "chat":
                prompt.append(ZAI_PROMPTS[ThreadLocalRandom.current().nextInt(ZAI_PROMPTS.length)]);
                break;

            default:
                prompt.append(ZAI_PROMPTS[ThreadLocalRandom.current().nextInt(ZAI_PROMPTS.length)]);
                break;
        }

        return prompt.toString();
    }

    /**
     * Generates a large JSON payload for serialization benchmarks
     */
    public static String generateLargeJsonPayload(int itemCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", System.currentTimeMillis());
        payload.put("totalCount", itemCount);

        List<Map<String, Object>> items = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; i++) {
            items.add(generateWorkItem());
        }
        payload.put("items", items);

        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize payload", e);
        }
    }

    /**
     * Generates timestamp
     */
    private static String generateTimestamp() {
        return generateTimestamp(0);
    }

    private static String generateTimestamp(long offsetMillis) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MILLISECOND, (int) offsetMillis);
        return cal.getTime().toString();
    }

    // =========================================================================
    // Batch Generators
    // =========================================================================

    /**
     * Batch generator for multiple test items
     */
    public static List<Map<String, Object>> generateWorkItems(int count) {
        List<Map<String, Object>> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(generateWorkItem());
        }
        return items;
    }

    /**
     * Batch generator for A2A requests
     */
    public static List<Map<String, Object>> generateA2ARequests(String operationType, int count) {
        List<Map<String, Object>> requests = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            requests.add(generateA2ARequest(operationType));
        }
        return requests;
    }

    /**
     * Batch generator for MCP tool calls
     */
    public static List<Map<String, Object>> generateMcpToolCalls(int count) {
        List<Map<String, Object>> calls = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            calls.add(generateMcpToolCall(null));
        }
        return calls;
    }

    /**
     * Batch generator for Z.ai prompts
     */
    public static List<String> generateZaiPrompts(int count) {
        List<String> prompts = new ArrayList<>(count);
        String[] promptTypes = {"workflow_analysis", "decision_generation", "data_transformation", "chat"};

        for (int i = 0; i < count; i++) {
            String promptType = promptTypes[i % promptTypes.length];
            prompts.add(generateZaiPrompt(promptType));
        }
        return prompts;
    }

    // =========================================================================
    // Main Method for Testing
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("=== YAWL Test Data Generator ===\n");

        // Generate sample data
        System.out.println("Generating workflow specification...");
        System.out.println(generateWorkflowSpecification());

        System.out.println("\nGenerating work items...");
        List<Map<String, Object>> workItems = generateWorkItems(3);
        workItems.forEach(System.out::println);

        System.out.println("\nGenerating A2A requests...");
        List<Map<String, Object>> a2aRequests = generateA2ARequests("launch_workflow", 2);
        a2aRequests.forEach(System.out::println);

        System.out.println("\nGenerating MCP tool calls...");
        List<Map<String, Object>> mcpCalls = generateMcpToolCalls(2);
        mcpCalls.forEach(System.out::println);

        System.out.println("\nGenerating Z.ai prompts...");
        List<String> zaiPrompts = generateZaiPrompts(3);
        zaiPrompts.forEach(System.out::println);

        System.out.println("\nGenerating large JSON payload (100 items)...");
        String largePayload = generateLargeJsonPayload(100);
        System.out.println("Payload size: " + largePayload.length() + " characters");
    }
}