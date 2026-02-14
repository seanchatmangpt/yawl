package org.yawlfoundation.yawl.examples.integration;

import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.io.IOException;

/**
 * MCP Server Example - Demonstrates YAWL MCP Server Integration
 *
 * This example shows how to:
 * 1. Start an MCP server that exposes YAWL workflow capabilities
 * 2. Register tools and resources for AI model access
 * 3. Handle client connections and requests
 *
 * Prerequisites:
 * - YAWL Engine running at http://localhost:8080/yawl/ib
 * - Valid YAWL admin credentials
 * - MCP SDK in classpath (when available)
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class McpServerExample {

    private static final String ENGINE_URL = "http://localhost:8080/yawl/ib";
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = "YAWL";

    public static void main(String[] args) {
        System.out.println("=== YAWL MCP Server Example ===\n");

        try {
            // Step 1: Connect to YAWL Engine
            System.out.println("Step 1: Connecting to YAWL Engine...");
            InterfaceB_EnvironmentBasedClient client =
                new InterfaceB_EnvironmentBasedClient(ENGINE_URL);

            String sessionHandle = client.connect(ADMIN_USER, ADMIN_PASSWORD);
            System.out.println("Connected with session: " + sessionHandle);

            // Step 2: Initialize MCP Server (requires MCP SDK)
            System.out.println("\nStep 2: Initializing MCP Server...");
            System.out.println("NOTE: MCP SDK integration required.");
            System.out.println("When available, the server will expose these tools:");
            System.out.println("  - launch_case: Start a new workflow instance");
            System.out.println("  - get_case_status: Retrieve current case state");
            System.out.println("  - complete_task: Complete a work item");
            System.out.println("  - list_workflows: List available specifications");

            // Step 3: Register YAWL tools with MCP
            System.out.println("\nStep 3: Tool Registration Example");
            System.out.println("Tools would be registered with these capabilities:");

            System.out.println("\n  launch_case tool:");
            System.out.println("    Input: {specId, version, caseData}");
            System.out.println("    Output: {caseId, status}");
            System.out.println("    Description: Launches a new case instance");

            System.out.println("\n  get_case_status tool:");
            System.out.println("    Input: {caseId}");
            System.out.println("    Output: {caseId, status, currentTasks, data}");
            System.out.println("    Description: Retrieves case execution status");

            // Step 4: Register YAWL resources with MCP
            System.out.println("\nStep 4: Resource Registration Example");
            System.out.println("Resources would be exposed at:");
            System.out.println("  yawl://workflows - List of all workflow specifications");
            System.out.println("  yawl://cases/{caseId} - Specific case data");
            System.out.println("  yawl://tasks/{taskId} - Task information");

            // Step 5: Example MCP Prompt
            System.out.println("\nStep 5: Prompt Template Example");
            System.out.println("AI models can use prompts like:");
            System.out.println("  'workflow-design': Helps design YAWL workflows");
            System.out.println("  'workflow-debug': Analyzes workflow execution issues");
            System.out.println("  'data-mapping': Assists with task data mapping");

            // Step 6: Demonstrate what a client would receive
            System.out.println("\nStep 6: Client Interaction Example");
            System.out.println("When an AI model calls launch_case:");
            System.out.println("  Request: {");
            System.out.println("    tool: 'launch_case',");
            System.out.println("    arguments: {");
            System.out.println("      specId: 'OrderFulfillment',");
            System.out.println("      caseData: '<data><orderID>12345</orderID></data>'");
            System.out.println("    }");
            System.out.println("  }");

            // Demonstrate actual YAWL engine call
            System.out.println("\n  This would execute:");
            YSpecificationID specId = new YSpecificationID("OrderFulfillment", "0.1", "0.1");
            String caseData = "<data><orderID>12345</orderID></data>";

            try {
                String caseId = client.launchCase(specId, caseData, null, sessionHandle);
                if (caseId != null && !caseId.contains("fault")) {
                    System.out.println("  Response: {");
                    System.out.println("    caseId: '" + caseId + "',");
                    System.out.println("    status: 'running'");
                    System.out.println("  }");
                } else {
                    System.out.println("  Response: {");
                    System.out.println("    error: 'Specification not loaded'");
                    System.out.println("  }");
                    System.out.println("  Note: Load OrderFulfillment spec first");
                }
            } catch (IOException e) {
                System.out.println("  Error: " + e.getMessage());
            }

            // Cleanup
            client.disconnect(sessionHandle);
            System.out.println("\n=== MCP Server Example Complete ===");
            System.out.println("\nTo implement full MCP server:");
            System.out.println("  1. Add MCP SDK dependency to build.xml");
            System.out.println("  2. Implement McpServer initialization");
            System.out.println("  3. Register tool handlers for each YAWL operation");
            System.out.println("  4. Register resource providers for workflow data");
            System.out.println("  5. Start server on designated port (default 3000)");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("\nTroubleshooting:");
            System.err.println("  - Ensure YAWL Engine is running at " + ENGINE_URL);
            System.err.println("  - Check admin credentials");
            System.err.println("  - Verify network connectivity");
        }
    }
}
