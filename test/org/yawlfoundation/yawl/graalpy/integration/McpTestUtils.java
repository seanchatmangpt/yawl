/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.graalpy.integration;

import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for MCP testing operations.
 *
 * Provides helper methods to simplify MCP protocol testing and ensure
 * consistent test patterns across all validation scenarios.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class McpTestUtils {

    private static final String DEFAULT_ENGINE_URL = "http://localhost:8080/yawl";
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin";

    private McpTestUtils() {
        throw new UnsupportedOperationException(
            "McpTestUtils is a utility class and cannot be instantiated.");
    }

    /**
     * Creates a standard InterfaceA client for testing.
     */
    public static InterfaceA_EnvironmentBasedClient createInterfaceAClient() throws Exception {
        return new InterfaceA_EnvironmentBasedClient(DEFAULT_ENGINE_URL + "/ia");
    }

    /**
     * Creates a standard InterfaceB client for testing.
     */
    public static InterfaceB_EnvironmentBasedClient createInterfaceBClient() throws Exception {
        return new InterfaceB_EnvironmentBasedClient(DEFAULT_ENGINE_URL + "/ib");
    }

    /**
     * Connects to YAWL engine and returns session handle.
     */
    public static String connectToEngine(InterfaceB_EnvironmentBasedClient client) throws Exception {
        String sessionHandle = client.connect(DEFAULT_USERNAME, DEFAULT_PASSWORD);
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            throw new RuntimeException("Failed to establish session with YAWL engine");
        }
        return sessionHandle;
    }

    /**
     * Disconnects from YAWL engine.
     */
    public static void disconnectFromEngine(InterfaceB_EnvironmentBasedClient client, String sessionHandle) {
        if (client != null && sessionHandle != null) {
            try {
                client.disconnect(sessionHandle);
            } catch (Exception e) {
                // Log but don't fail on disconnection errors
                System.err.println("Warning: Error during disconnection: " + e.getMessage());
            }
        }
    }

    /**
     * Creates a test specification ID.
     */
    public static YSpecificationID createTestSpecId(String specId, String version, String uri) {
        return new YSpecificationID(specId, version, uri);
    }

    /**
     * Creates a standard test case XML data.
     */
    public static String createTestCaseData() {
        return "<data></data>";
    }

    /**
     * Creates test case data with parameters.
     */
    public static String createTestCaseDataWithParams(Map<String, String> params) {
        StringBuilder xmlBuilder = new StringBuilder("<data>");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            xmlBuilder.append("<param name=\"").append(entry.getKey()).append("\">")
                     .append(entry.getValue()).append("</param>");
        }
        xmlBuilder.append("</data>");
        return xmlBuilder.toString();
    }

    /**
     * Extracts case ID from case launch response.
     */
    public static String extractCaseIdFromResponse(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }
        // Case ID typically appears in format: "Case launched successfully. Case ID: urn:yawl:case:xxx"
        if (response.contains("Case ID:")) {
            String[] parts = response.split("Case ID:");
            if (parts.length > 1) {
                return parts[1].trim().split("\\s+")[0]; // Extract first non-empty part
            }
        }
        return null;
    }

    /**
     * Checks if a work item is available for checkout.
     */
    public static boolean isWorkItemAvailable(List<WorkItemRecord> workItems) {
        return workItems != null && !workItems.isEmpty();
    }

    /**
     * Gets the first available work item.
     */
    public static WorkItemRecord getFirstWorkItem(List<WorkItemRecord> workItems) {
        if (isWorkItemAvailable(workItems)) {
            return workItems.get(0);
        }
        return null;
    }

    /**
     * Validates that a string is a valid YAWL case ID format.
     */
    public static boolean isValidYawlCaseId(String caseId) {
        if (caseId == null || caseId.trim().isEmpty()) {
            return false;
        }
        return caseId.startsWith("urn:yawl:case:");
    }

    /**
     * Validates that a string is a valid YAWL specification ID format.
     */
    public static boolean isValidYawlSpecId(String specId) {
        if (specId == null || specId.trim().isEmpty()) {
            return false;
        }
        // YAWL spec IDs typically don't have a strict format, should not be empty
        return !specId.trim().isEmpty();
    }

    /**
     * Validates that response contains expected content patterns.
     */
    public static boolean validateResponseContent(String response, String[] expectedPatterns) {
        if (response == null || response.isEmpty()) {
            return false;
        }
        String lowerResponse = response.toLowerCase();
        for (String pattern : expectedPatterns) {
            if (!lowerResponse.contains(pattern.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates that error response contains expected error patterns.
     */
    public static boolean validateErrorResponse(String response, String[] errorPatterns) {
        return validateResponseContent(response, errorPatterns);
    }

    /**
     * Creates a comprehensive test workflow XML specification.
     */
    public static String createComprehensiveWorkflowXml() {
        return """
            <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema">
                <specification id="ComprehensiveWorkflow" name="ComprehensiveWorkflow" version="1.0" uri="http://example.com/comprehensive.xml">
                    <process id="ComprehensiveWorkflow_process">
                        <inputCondition id="i-top"/>
                        <outputCondition id="o-top"/>
                        <task id="TaskA" name="Task A">
                            <flow id="flow1" condition="true" target="TaskB"/>
                        </task>
                        <task id="TaskB" name="Task B">
                            <flow id="flow2" condition="hasData" target="TaskC"/>
                            <flow id="flow3" condition="true" target="TaskD"/>
                        </task>
                        <task id="TaskC" name="Task C">
                            <flow id="flow4" condition="true" target="TaskE"/>
                        </task>
                        <task id="TaskD" name="Task D">
                            <flow id="flow5" condition="true" target="TaskE"/>
                        </task>
                        <task id="TaskE" name="Task E">
                            <flow id="flow6" condition="true" target="o-top"/>
                        </task>
                        <decomposition id="TaskA_decomposition" decompositionRef="ComprehensiveWorkflow_process"/>
                        <decomposition id="TaskB_decomposition" decompositionRef="ComprehensiveWorkflow_process"/>
                        <decomposition id="TaskC_decomposition" decompositionRef="ComprehensiveWorkflow_process"/>
                        <decomposition id="TaskD_decomposition" decompositionRef="ComprehensiveWorkflow_process"/>
                        <decomposition id="TaskE_decomposition" decompositionRef="ComprehensiveWorkflow_process"/>
                    </process>
                </specification>
            </specificationSet>
            """;
    }

    /**
     * Creates a simple workflow XML specification.
     */
    public static String createSimpleWorkflowXml() {
        return """
            <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema">
                <specification id="SimpleWorkflow" name="SimpleWorkflow" version="1.0" uri="http://example.com/simple.xml">
                    <process id="SimpleWorkflow_process">
                        <inputCondition id="i-top"/>
                        <outputCondition id="o-top"/>
                        <task id="TaskA" name="Task A">
                            <flow id="flow1" condition="true" target="o-top"/>
                        </task>
                        <decomposition id="TaskA_decomposition" decompositionRef="SimpleWorkflow_process"/>
                    </process>
                </specification>
            </specificationSet>
            """;
    }

    /**
     * Creates a workflow with conditional branching.
     */
    public static String createConditionalWorkflowXml() {
        return """
            <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema">
                <specification id="ConditionalWorkflow" name="ConditionalWorkflow" version="1.0" uri="http://example.com/conditional.xml">
                    <process id="ConditionalWorkflow_process">
                        <inputCondition id="i-top"/>
                        <outputCondition id="o-top"/>
                        <task id="DecisionTask" name="Decision Task">
                            <flow id="flow_yes" condition="yes" target="TaskYes"/>
                            <flow id="flow_no" condition="no" target="TaskNo"/>
                            <flow id="flow_default" condition="true" target="TaskDefault"/>
                        </task>
                        <task id="TaskYes" name="Yes Task">
                            <flow id="flow_yes_end" condition="true" target="o-top"/>
                        </task>
                        <task id="TaskNo" name="No Task">
                            <flow id="flow_no_end" condition="true" target="o-top"/>
                        </task>
                        <task id="TaskDefault" name="Default Task">
                            <flow id="flow_default_end" condition="true" target="o-top"/>
                        </task>
                        <decomposition id="DecisionTask_decomposition" decompositionRef="ConditionalWorkflow_process"/>
                        <decomposition id="TaskYes_decomposition" decompositionRef="ConditionalWorkflow_process"/>
                        <decomposition id="TaskNo_decomposition" decompositionRef="ConditionalWorkflow_process"/>
                        <decomposition id="TaskDefault_decomposition" decompositionRef="ConditionalWorkflow_process"/>
                    </process>
                </specification>
            </specificationSet>
            """;
    }

    /**
     * Creates a workflow with parallel processing.
     */
    public static String createParallelWorkflowXml() {
        return """
            <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema">
                <specification id="ParallelWorkflow" name="ParallelWorkflow" version="1.0" uri="http://example.com/parallel.xml">
                    <process id="ParallelWorkflow_process">
                        <inputCondition id="i-top"/>
                        <outputCondition id="o-top"/>
                        <task id="StartTask" name="Start Task">
                            <flow id="flow_parallel" condition="true" target="ParallelSplit"/>
                        </task>
                        <task id="ParallelSplit" name="Parallel Split" join="xor" split="and">
                            <flow id="flow_task1" condition="true" target="Task1"/>
                            <flow id="flow_task2" condition="true" target="Task2"/>
                            <flow id="flow_task3" condition="true" target="Task3"/>
                        </task>
                        <task id="Task1" name="Parallel Task 1">
                            <flow id="flow_task1_end" condition="true" target="ParallelJoin"/>
                        </task>
                        <task id="Task2" name="Parallel Task 2">
                            <flow id="flow_task2_end" condition="true" target="ParallelJoin"/>
                        </task>
                        <task id="Task3" name="Parallel Task 3">
                            <flow id="flow_task3_end" condition="true" target="ParallelJoin"/>
                        </task>
                        <task id="ParallelJoin" name="Parallel Join" join="and" split="xor">
                            <flow id="flow_end" condition="true" target="o-top"/>
                        </task>
                        <decomposition id="StartTask_decomposition" decompositionRef="ParallelWorkflow_process"/>
                        <decomposition id="ParallelSplit_decomposition" decompositionRef="ParallelWorkflow_process"/>
                        <decomposition id="Task1_decomposition" decompositionRef="ParallelWorkflow_process"/>
                        <decomposition id="Task2_decomposition" decompositionRef="ParallelWorkflow_process"/>
                        <decomposition id="Task3_decomposition" decompositionRef="ParallelWorkflow_process"/>
                        <decomposition id="ParallelJoin_decomposition" decompositionRef="ParallelWorkflow_process"/>
                    </process>
                </specification>
            </specificationSet>
            """;
    }
}