package org.yawlfoundation.yawl.examples.integration;

import org.yawlfoundation.yawl.integration.mcp.YawlMcpClient;

/**
 * MCP Client Example - Demonstrates connecting to YAWL MCP Server
 *
 * This example shows how to:
 * 1. Connect to a YAWL MCP server
 * 2. Call workflow tools with AI enhancement
 * 3. Fetch workflow resources
 * 4. Use workflow-design prompts
 *
 * Prerequisites:
 * - YAWL MCP Server running (see McpServerExample.java)
 * - ZAI_API_KEY environment variable set (optional, for AI features)
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class McpClientExample {

    private static final String MCP_SERVER_URL = "http://localhost:3000";

    public static void main(String[] args) {
        System.out.println("=== YAWL MCP Client Example ===\n");

        // Check for Z.AI API key
        String zaiApiKey = System.getenv("ZAI_API_KEY");
        boolean aiEnabled = (zaiApiKey != null && !zaiApiKey.isEmpty());

        if (!aiEnabled) {
            System.out.println("Note: ZAI_API_KEY not set. AI features will be limited.");
            System.out.println("Set with: export ZAI_API_KEY=your_key_here\n");
        }

        try {
            // Step 1: Connect to MCP Server
            System.out.println("Step 1: Connecting to YAWL MCP Server...");
            YawlMcpClient client = aiEnabled
                ? new YawlMcpClient(MCP_SERVER_URL, zaiApiKey)
                : new YawlMcpClient(MCP_SERVER_URL);

            client.connect();
            System.out.println("Connected to: " + MCP_SERVER_URL);
            System.out.println("AI Enhanced: " + (client.isAIEnabled() ? "Yes" : "No"));

            // Step 2: List available tools
            System.out.println("\nStep 2: Listing Available Tools...");
            String[] tools = client.listTools();
            System.out.println("Available tools:");
            for (String tool : tools) {
                System.out.println("  - " + tool);
            }

            // Step 3: Launch a case using direct tool call
            System.out.println("\nStep 3: Launching Order Fulfillment Case...");
            String orderData = "{"
                + "\"specId\": \"OrderFulfillment\", "
                + "\"caseData\": \"<data><orderID>ORD-2026-001</orderID><customer>Acme Corp</customer><amount>2500.00</amount></data>\""
                + "}";

            System.out.println("Tool: startWorkflow");
            System.out.println("Parameters: " + orderData);

            try {
                String result = client.callTool("startWorkflow", orderData);
                System.out.println("Result: " + result);
            } catch (UnsupportedOperationException e) {
                System.out.println("Note: MCP SDK integration required for actual tool calls");
                System.out.println("Expected result would be: {\"caseId\": \"12345\", \"status\": \"running\"}");
            }

            // Step 4: Use AI-enhanced tool call
            if (client.isAIEnabled()) {
                System.out.println("\nStep 4: AI-Enhanced Order Launch...");
                String naturalRequest =
                    "Launch an order fulfillment workflow for customer 'Global Industries' with order amount $5000";
                String context = "{\"urgency\": \"high\", \"paymentMethod\": \"credit\"}";

                System.out.println("Natural language request: " + naturalRequest);
                System.out.println("Context: " + context);

                String aiResult = client.callToolWithAI(naturalRequest, context);
                System.out.println("AI-enhanced result: " + aiResult);
            } else {
                System.out.println("\nStep 4: Skipped (AI features require ZAI_API_KEY)");
            }

            // Step 5: Fetch case status resource
            System.out.println("\nStep 5: Fetching Case Status Resource...");
            String resourceUri = "yawl://cases/12345";
            System.out.println("Resource URI: " + resourceUri);

            try {
                String resourceData = client.getResource(resourceUri);
                System.out.println("Resource data: " + resourceData);
            } catch (UnsupportedOperationException e) {
                System.out.println("Note: MCP SDK integration required for resource fetching");
                System.out.println("Expected resource data:");
                System.out.println("  {");
                System.out.println("    \"caseId\": \"12345\",");
                System.out.println("    \"specId\": \"OrderFulfillment\",");
                System.out.println("    \"status\": \"running\",");
                System.out.println("    \"currentTasks\": [");
                System.out.println("      {\"taskId\": \"ordering\", \"status\": \"enabled\"}");
                System.out.println("    ]");
                System.out.println("  }");
            }

            // Step 6: Analyze resource with AI
            if (client.isAIEnabled()) {
                System.out.println("\nStep 6: AI-Powered Resource Analysis...");
                String analysisRequest = "Identify any bottlenecks or issues in this workflow execution";

                try {
                    String analysis = client.analyzeResourceWithAI(resourceUri, analysisRequest);
                    System.out.println("AI Analysis: " + analysis);
                } catch (Exception e) {
                    System.out.println("Analysis example: The workflow is progressing normally. The 'ordering' task is ready for execution.");
                }
            } else {
                System.out.println("\nStep 6: Skipped (AI analysis requires ZAI_API_KEY)");
            }

            // Step 7: Get tool recommendation
            if (client.isAIEnabled()) {
                System.out.println("\nStep 7: AI Tool Recommendation...");
                String task = "I need to check if a specific order has been approved";

                String recommendation = client.getToolRecommendation(task);
                System.out.println("Task: " + task);
                System.out.println("Recommendation: " + recommendation);
            } else {
                System.out.println("\nStep 7: Skipped (Tool recommendations require ZAI_API_KEY)");
            }

            // Step 8: Use workflow-design prompt
            System.out.println("\nStep 8: Workflow Design Prompt Example...");
            System.out.println("Prompt templates available:");
            System.out.println("  - workflow-design: Guides workflow specification creation");
            System.out.println("  - workflow-debug: Helps troubleshoot execution issues");
            System.out.println("  - data-mapping: Assists with task parameter mapping");
            System.out.println("\nExample usage with AI model:");
            System.out.println("  Prompt: 'Using workflow-design template, create a purchase approval workflow'");
            System.out.println("  AI would generate YAWL XML specification with proper structure");

            // Cleanup
            client.disconnect();
            System.out.println("\n=== MCP Client Example Complete ===");

            System.out.println("\nNext Steps:");
            System.out.println("  1. Start YAWL MCP Server (see McpServerExample.java)");
            System.out.println("  2. Configure MCP SDK when available");
            System.out.println("  3. Set ZAI_API_KEY for AI-enhanced features");
            System.out.println("  4. Load OrderFulfillment specification into YAWL Engine");

        } catch (IllegalStateException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("\nTroubleshooting:");
            System.err.println("  - Check if MCP server is running at " + MCP_SERVER_URL);
            System.err.println("  - Verify ZAI_API_KEY if using AI features");
            System.err.println("  - Review server logs for connection issues");
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
