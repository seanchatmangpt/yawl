package org.yawlfoundation.yawl.examples.integration;

import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.io.IOException;
import java.util.List;

/**
 * A2A Server Example - Demonstrates YAWL Agent-to-Agent Server Integration
 *
 * This example shows how to:
 * 1. Start an A2A server that exposes YAWL as an agent
 * 2. Register agent capabilities
 * 3. Delegate work items to external agents
 * 4. Handle agent responses
 *
 * Prerequisites:
 * - YAWL Engine running at http://localhost:8080/yawl/ib
 * - Valid YAWL admin credentials
 * - A2A SDK in classpath (when available)
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class A2aServerExample {

    private static final String ENGINE_URL = "http://localhost:8080/yawl/ib";
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = "YAWL";
    private static final int A2A_SERVER_PORT = 8090;

    public static void main(String[] args) {
        System.out.println("=== YAWL A2A Server Example ===\n");

        try {
            // Step 1: Connect to YAWL Engine
            System.out.println("Step 1: Connecting to YAWL Engine...");
            InterfaceB_EnvironmentBasedClient client =
                new InterfaceB_EnvironmentBasedClient(ENGINE_URL);

            String sessionHandle = client.connect(ADMIN_USER, ADMIN_PASSWORD);
            System.out.println("Connected with session: " + sessionHandle);

            // Step 2: Define Agent Capabilities
            System.out.println("\nStep 2: Defining YAWL Agent Capabilities...");
            System.out.println("AgentCard for YAWL:");
            System.out.println("{");
            System.out.println("  \"name\": \"YAWL Workflow Engine\",");
            System.out.println("  \"version\": \"5.2\",");
            System.out.println("  \"capabilities\": [");
            System.out.println("    {");
            System.out.println("      \"name\": \"executeWorkItem\",");
            System.out.println("      \"description\": \"Execute a YAWL work item\",");
            System.out.println("      \"input\": {\"workItemId\": \"string\", \"data\": \"object\"},");
            System.out.println("      \"output\": {\"result\": \"string\", \"status\": \"string\"}");
            System.out.println("    },");
            System.out.println("    {");
            System.out.println("      \"name\": \"launchWorkflow\",");
            System.out.println("      \"description\": \"Start a new workflow instance\",");
            System.out.println("      \"input\": {\"specId\": \"string\", \"caseData\": \"object\"},");
            System.out.println("      \"output\": {\"caseId\": \"string\"}");
            System.out.println("    },");
            System.out.println("    {");
            System.out.println("      \"name\": \"queryWorkflow\",");
            System.out.println("      \"description\": \"Query workflow execution status\",");
            System.out.println("      \"input\": {\"caseId\": \"string\"},");
            System.out.println("      \"output\": {\"status\": \"string\", \"data\": \"object\"}");
            System.out.println("    }");
            System.out.println("  ]");
            System.out.println("}");

            // Step 3: Initialize A2A Server
            System.out.println("\nStep 3: Initializing A2A Server...");
            System.out.println("Server would start on port: " + A2A_SERVER_PORT);
            System.out.println("Supported transports:");
            System.out.println("  - JSON-RPC 2.0 over HTTP");
            System.out.println("  - gRPC (when available)");
            System.out.println("  - WebSocket (for streaming)");

            // Step 4: Demonstrate work item delegation
            System.out.println("\nStep 4: Work Item Delegation Example...");

            // Get live work items
            List<WorkItemRecord> workItems = client.getCompleteListOfLiveWorkItems(sessionHandle);
            System.out.println("Current live work items: " + workItems.size());

            if (!workItems.isEmpty()) {
                WorkItemRecord item = workItems.get(0);
                System.out.println("\nDelegating work item to external agent:");
                System.out.println("  Work Item ID: " + item.getID());
                System.out.println("  Task: " + item.getTaskID());
                System.out.println("  Case: " + item.getCaseID());

                System.out.println("\nA2A Request to External Agent:");
                System.out.println("{");
                System.out.println("  \"capability\": \"processOrderApproval\",");
                System.out.println("  \"parameters\": {");
                System.out.println("    \"workItemId\": \"" + item.getID() + "\",");
                System.out.println("    \"taskData\": " + (item.getDataString() != null ? item.getDataString() : "{}"));
                System.out.println("  }");
                System.out.println("}");

                System.out.println("\nExpected Agent Response:");
                System.out.println("{");
                System.out.println("  \"status\": \"success\",");
                System.out.println("  \"result\": {");
                System.out.println("    \"approved\": true,");
                System.out.println("    \"approver\": \"AI_Agent_001\",");
                System.out.println("    \"comments\": \"Order approved automatically - low risk\"");
                System.out.println("  }");
                System.out.println("}");

                // Simulate completing the work item with agent response
                System.out.println("\nCompleting work item with agent result...");
                String resultData = "<data><approved>true</approved><approver>AI_Agent_001</approver></data>";

                try {
                    String result = client.checkInWorkItem(item.getID(), resultData, "A2A Agent", sessionHandle);
                    if (result != null && !result.contains("fault")) {
                        System.out.println("Work item completed successfully");
                    } else {
                        System.out.println("Note: " + result);
                    }
                } catch (IOException e) {
                    System.out.println("Error completing work item: " + e.getMessage());
                }
            } else {
                System.out.println("No work items available for delegation");
                System.out.println("\nTo test delegation:");
                System.out.println("  1. Load a workflow specification");
                System.out.println("  2. Launch a case instance");
                System.out.println("  3. Re-run this example");
            }

            // Step 5: Demonstrate capability invocation
            System.out.println("\nStep 5: Capability Invocation Example...");
            System.out.println("External agent requests workflow launch:");
            System.out.println("{");
            System.out.println("  \"capability\": \"launchWorkflow\",");
            System.out.println("  \"parameters\": {");
            System.out.println("    \"specId\": \"OrderFulfillment\",");
            System.out.println("    \"caseData\": {");
            System.out.println("      \"orderID\": \"ORD-2026-002\",");
            System.out.println("      \"customer\": \"Tech Solutions Inc\",");
            System.out.println("      \"amount\": 7500.00");
            System.out.println("    }");
            System.out.println("  }");
            System.out.println("}");

            // Execute the capability
            YSpecificationID specId = new YSpecificationID("OrderFulfillment", "0.1", "0.1");
            String caseData = "<data><orderID>ORD-2026-002</orderID><customer>Tech Solutions Inc</customer><amount>7500.00</amount></data>";

            try {
                String caseId = client.launchCase(specId, caseData, null, sessionHandle);
                if (caseId != null && !caseId.contains("fault")) {
                    System.out.println("\nCapability Response:");
                    System.out.println("{");
                    System.out.println("  \"status\": \"success\",");
                    System.out.println("  \"result\": {");
                    System.out.println("    \"caseId\": \"" + caseId + "\"");
                    System.out.println("  }");
                    System.out.println("}");
                } else {
                    System.out.println("\nNote: Specification not loaded");
                    System.out.println("Load OrderFulfillment spec to test capability");
                }
            } catch (IOException e) {
                System.out.println("Error: " + e.getMessage());
            }

            // Step 6: Agent Discovery
            System.out.println("\nStep 6: Agent Discovery Example...");
            System.out.println("A2A server advertises capabilities via:");
            System.out.println("  GET /a2a/capabilities");
            System.out.println("\nResponse:");
            System.out.println("{");
            System.out.println("  \"agent\": \"YAWL Workflow Engine\",");
            System.out.println("  \"version\": \"5.2\",");
            System.out.println("  \"endpoint\": \"http://localhost:" + A2A_SERVER_PORT + "/a2a\",");
            System.out.println("  \"capabilities\": [\"executeWorkItem\", \"launchWorkflow\", \"queryWorkflow\"]");
            System.out.println("}");

            // Cleanup
            client.disconnect(sessionHandle);
            System.out.println("\n=== A2A Server Example Complete ===");

            System.out.println("\nTo implement full A2A server:");
            System.out.println("  1. Add A2A SDK dependency to build.xml");
            System.out.println("  2. Implement AgentCard definition");
            System.out.println("  3. Create AgentExecutor for YAWL operations");
            System.out.println("  4. Configure transport layer (JSON-RPC/gRPC)");
            System.out.println("  5. Start server on port " + A2A_SERVER_PORT);
            System.out.println("  6. Register with agent discovery service");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("\nTroubleshooting:");
            System.err.println("  - Ensure YAWL Engine is running at " + ENGINE_URL);
            System.err.println("  - Check admin credentials");
            System.err.println("  - Verify network connectivity");
        }
    }
}
