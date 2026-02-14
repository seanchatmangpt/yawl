package org.yawlfoundation.yawl.examples.integration;

import org.yawlfoundation.yawl.integration.a2a.YawlA2AClient;

/**
 * A2A Client Example - Demonstrates connecting to external agents via A2A protocol
 *
 * This example shows how to:
 * 1. Connect to an external A2A agent
 * 2. Discover agent capabilities
 * 3. Invoke agent capabilities with data
 * 4. Handle agent responses
 * 5. Use AI for intelligent agent selection
 *
 * Prerequisites:
 * - External A2A agent running (or YAWL A2A Server from A2aServerExample)
 * - ZAI_API_KEY environment variable set (optional, for AI features)
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class A2aClientExample {

    private static final String AGENT_URL = "http://localhost:8090/a2a";

    public static void main(String[] args) {
        System.out.println("=== YAWL A2A Client Example ===\n");

        // Check for Z.AI API key
        String zaiApiKey = System.getenv("ZAI_API_KEY");
        boolean aiEnabled = (zaiApiKey != null && !zaiApiKey.isEmpty());

        if (!aiEnabled) {
            System.out.println("Note: ZAI_API_KEY not set. AI features will be limited.");
            System.out.println("Set with: export ZAI_API_KEY=your_key_here\n");
        }

        try {
            // Step 1: Connect to A2A Agent
            System.out.println("Step 1: Connecting to A2A Agent...");
            YawlA2AClient client = aiEnabled
                ? new YawlA2AClient(AGENT_URL, zaiApiKey)
                : new YawlA2AClient(AGENT_URL);

            try {
                client.connect();
                System.out.println("Connected to: " + AGENT_URL);
                System.out.println("AI Enhanced: " + (client.isAIEnabled() ? "Yes" : "No"));
            } catch (UnsupportedOperationException e) {
                System.out.println("Note: A2A SDK integration required for actual connections");
                System.out.println("Continuing with example simulation...\n");
            }

            // Step 2: Discover agent capabilities
            System.out.println("\nStep 2: Discovering Agent Capabilities...");
            String capabilities = "processOrderApproval, validateInventory, scheduleDelivery, notifyCustomer";

            System.out.println("Agent capabilities:");
            for (String capability : capabilities.split(", ")) {
                System.out.println("  - " + capability);
            }

            // Step 3: Invoke order approval capability
            System.out.println("\nStep 3: Invoking Order Approval Capability...");
            String orderData = "{"
                + "\"orderId\": \"ORD-2026-003\", "
                + "\"customer\": \"Retail Corp\", "
                + "\"amount\": 15000, "
                + "\"items\": [{\"sku\": \"PROD-123\", \"quantity\": 50}]"
                + "}";

            System.out.println("Capability: processOrderApproval");
            System.out.println("Order data: " + orderData);

            try {
                String result = client.invokeCapability("processOrderApproval", orderData);
                System.out.println("Agent response: " + result);
            } catch (UnsupportedOperationException e) {
                System.out.println("Expected agent response:");
                System.out.println("{");
                System.out.println("  \"approved\": true,");
                System.out.println("  \"approvalLevel\": \"director\",");
                System.out.println("  \"approvedBy\": \"AI_Agent_Approval\",");
                System.out.println("  \"reason\": \"Order within budget limits and customer has good credit\"");
                System.out.println("}");
            }

            // Step 4: Use AI-enhanced invocation
            if (client.isAIEnabled()) {
                System.out.println("\nStep 4: AI-Enhanced Agent Invocation...");
                String naturalRequest =
                    "Process this large order and determine if additional approvals are needed";
                String largeOrderData = "{"
                    + "\"orderId\": \"ORD-2026-004\", "
                    + "\"customer\": \"Enterprise Solutions\", "
                    + "\"amount\": 50000, "
                    + "\"urgency\": \"high\""
                    + "}";

                System.out.println("Natural language request: " + naturalRequest);
                System.out.println("Order data: " + largeOrderData);

                String aiResult = client.invokeWithAI(naturalRequest, largeOrderData);
                System.out.println("AI-enhanced result: " + aiResult);
            } else {
                System.out.println("\nStep 4: Skipped (AI features require ZAI_API_KEY)");
            }

            // Step 5: Multi-agent orchestration
            if (client.isAIEnabled()) {
                System.out.println("\nStep 5: AI-Powered Multi-Agent Orchestration...");
                String workflow = "Complete order fulfillment: verify inventory, process payment, schedule delivery, notify customer";
                String[] availableAgents = {
                    "InventoryAgent - checks stock availability",
                    "PaymentAgent - processes credit card payments",
                    "DeliveryAgent - schedules logistics",
                    "NotificationAgent - sends customer emails"
                };

                System.out.println("Workflow: " + workflow);
                System.out.println("\nAvailable agents:");
                for (String agent : availableAgents) {
                    System.out.println("  - " + agent);
                }

                String orchestrationPlan = client.getOrchestrationPlan(workflow, availableAgents);
                System.out.println("\nAI Orchestration Plan:");
                System.out.println(orchestrationPlan);
            } else {
                System.out.println("\nStep 5: Skipped (Orchestration requires ZAI_API_KEY)");
                System.out.println("Example orchestration plan:");
                System.out.println("  1. InventoryAgent: Check stock for all items");
                System.out.println("  2. PaymentAgent: Process payment if stock available");
                System.out.println("  3. DeliveryAgent: Schedule delivery if payment successful");
                System.out.println("  4. NotificationAgent: Send confirmation to customer");
            }

            // Step 6: Exception handling with AI
            if (client.isAIEnabled()) {
                System.out.println("\nStep 6: AI-Assisted Exception Handling...");
                String exceptionDetails = "PaymentAgent returned timeout after 30 seconds";
                String context = "{"
                    + "\"orderId\": \"ORD-2026-005\", "
                    + "\"paymentAttempts\": 2, "
                    + "\"timeoutThreshold\": 30"
                    + "}";

                System.out.println("Exception: " + exceptionDetails);
                System.out.println("Context: " + context);

                String resolution = client.handleExceptionWithAI(exceptionDetails, context);
                System.out.println("AI-suggested resolution: " + resolution);
            } else {
                System.out.println("\nStep 6: Skipped (Exception handling requires ZAI_API_KEY)");
                System.out.println("Example resolution:");
                System.out.println("  - Retry payment with exponential backoff");
                System.out.println("  - Switch to backup PaymentAgent if available");
                System.out.println("  - Queue order for manual review if retries fail");
            }

            // Step 7: Data transformation for agent compatibility
            if (client.isAIEnabled()) {
                System.out.println("\nStep 7: AI-Powered Data Transformation...");
                String yawlData = "<data><orderID>ORD-001</orderID><amount>1500</amount></data>";
                String targetFormat = "JSON format compatible with REST API: {orderId, totalAmount, currency}";

                System.out.println("YAWL data: " + yawlData);
                System.out.println("Target format: " + targetFormat);

                String transformed = client.transformForAgent(yawlData, targetFormat);
                System.out.println("Transformed data: " + transformed);
            } else {
                System.out.println("\nStep 7: Skipped (Data transformation requires ZAI_API_KEY)");
                System.out.println("Example transformation:");
                System.out.println("  Input:  <data><orderID>ORD-001</orderID><amount>1500</amount></data>");
                System.out.println("  Output: {\"orderId\": \"ORD-001\", \"totalAmount\": 1500, \"currency\": \"USD\"}");
            }

            // Step 8: Complete order fulfillment workflow
            System.out.println("\nStep 8: Complete Order Fulfillment via A2A...");
            System.out.println("Workflow execution with multiple agents:");

            String[] steps = {
                "1. InventoryAgent.checkStock({\"items\": [\"PROD-123\"]})",
                "   Response: {\"available\": true, \"quantity\": 100}",
                "",
                "2. PaymentAgent.processPayment({\"amount\": 2500, \"method\": \"credit\"})",
                "   Response: {\"success\": true, \"transactionId\": \"TXN-789\"}",
                "",
                "3. DeliveryAgent.scheduleDelivery({\"address\": \"123 Main St\", \"date\": \"2026-02-20\"})",
                "   Response: {\"scheduled\": true, \"trackingId\": \"TRACK-456\"}",
                "",
                "4. NotificationAgent.notifyCustomer({\"email\": \"customer@example.com\", \"orderId\": \"ORD-001\"})",
                "   Response: {\"sent\": true, \"messageId\": \"MSG-123\"}"
            };

            for (String step : steps) {
                System.out.println(step);
            }

            // Cleanup
            client.disconnect();
            System.out.println("\n=== A2A Client Example Complete ===");

            System.out.println("\nNext Steps:");
            System.out.println("  1. Start A2A-enabled agents (or use A2aServerExample)");
            System.out.println("  2. Configure A2A SDK when available");
            System.out.println("  3. Set ZAI_API_KEY for AI-enhanced features");
            System.out.println("  4. Implement error handling and retry logic");
            System.out.println("  5. Monitor agent performance and availability");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("\nTroubleshooting:");
            System.err.println("  - Check if A2A agent is running at " + AGENT_URL);
            System.err.println("  - Verify agent supports required capabilities");
            System.err.println("  - Review agent logs for errors");
            System.err.println("  - Confirm network connectivity");
        }
    }
}
