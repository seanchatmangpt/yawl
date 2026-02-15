package org.yawlfoundation.yawl.integration.test;

import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentSkill;
import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.integration.zai.ZaiFunctionService;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpClient;
import org.yawlfoundation.yawl.integration.a2a.YawlA2AClient;

import java.util.Arrays;
import java.util.List;

/**
 * Integration verification for MCP, A2A, and Z.AI services.
 *
 * Chicago TDD: Real integration checks drive the implementation.
 * Requires ZAI_API_KEY environment variable for Z.AI checks.
 * Requires running MCP server for MCP checks.
 * Requires running A2A server for A2A checks.
 */
public class SelfPlayTest {

    private static int checksRun = 0;
    private static int checksPassed = 0;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("YAWL MCP/A2A/Z.AI Integration Checks");
        System.out.println("========================================\n");

        String apiKey = System.getenv("ZAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("ZAI_API_KEY environment variable not set");
            System.err.println("Set ZAI_API_KEY and run again");
            System.exit(1);
        }
        System.out.println("Z.AI Service initialized successfully\n");

        verifyZaiConnection();
        verifyBasicChat();
        verifyWorkflowDecision();
        verifyDataTransformation();
        verifyFunctionCalling();
        verifyMcpClient();
        verifyA2aClient();
        verifyMultiAgentOrchestration();
        verifyEndToEndWorkflow();

        printSummary();
    }

    static void verifyZaiConnection() {
        runCheck("Z.AI Connection", () -> {
            ZaiService service = new ZaiService();
            boolean connected = service.verifyConnection();
            if (!connected) {
                throw new AssertionError("Failed to connect to Z.AI API");
            }
        });
    }

    static void verifyBasicChat() {
        runCheck("Basic Chat", () -> {
            ZaiService service = new ZaiService();
            service.setSystemPrompt("You are a helpful assistant for YAWL workflows.");

            String response = service.chat("Say 'hello' in exactly one word");
            if (response == null || response.isEmpty()) {
                throw new AssertionError("Empty response from chat");
            }
            System.out.println("  Response: " + response.substring(0, Math.min(50, response.length())) + "...");
        });
    }

    static void verifyWorkflowDecision() {
        runCheck("Workflow Decision", () -> {
            ZaiService service = new ZaiService();

            String decision = service.makeWorkflowDecision(
                    "Approval Level",
                    "{\"amount\": 5000, \"department\": \"IT\"}",
                    Arrays.asList("Manager Approval", "Director Approval", "Auto-Approve", "Reject")
            );

            if (decision == null || decision.isEmpty()) {
                throw new AssertionError("Empty decision response");
            }
            System.out.println("  Decision: " + decision.substring(0, Math.min(60, decision.length())) + "...");
        });
    }

    static void verifyDataTransformation() {
        runCheck("Data Transformation", () -> {
            ZaiService service = new ZaiService();

            String transformed = service.transformData(
                    "John Doe, 123 Main St",
                    "Convert to JSON with fields: name, address"
            );

            if (transformed == null || transformed.isEmpty()) {
                throw new AssertionError("Empty transformation response");
            }
            System.out.println("  Transformed: " + transformed.substring(0, Math.min(50, transformed.length())) + "...");
        });
    }

    static void verifyFunctionCalling() {
        runCheck("Function Calling", () -> {
            ZaiFunctionService service = new ZaiFunctionService();

            String result = service.processWithFunctions("List all available workflows");
            if (result == null || result.isEmpty()) {
                throw new AssertionError("Empty function response");
            }
            System.out.println("  Result: " + result.substring(0, Math.min(60, result.length())) + "...");
        });
    }

    static void verifyMcpClient() {
        runCheck("MCP Client", () -> {
            String sseUrl = System.getenv("MCP_SERVER_URL");
            if (sseUrl == null || sseUrl.isEmpty()) {
                sseUrl = "http://localhost:3000";
            }

            YawlMcpClient client = new YawlMcpClient();
            client.connectSse(sseUrl);

            if (!client.isConnected()) {
                throw new AssertionError("MCP client not connected");
            }

            List<McpSchema.Tool> tools = client.listTools();
            if (tools == null || tools.isEmpty()) {
                throw new AssertionError("No tools available");
            }
            System.out.println("  Tools: " + tools.size() + " available");

            client.close();
        });
    }

    static void verifyA2aClient() {
        runCheck("A2A Client", () -> {
            String agentUrl = System.getenv("A2A_AGENT_URL");
            if (agentUrl == null || agentUrl.isEmpty()) {
                agentUrl = "http://localhost:8081";
            }

            YawlA2AClient client = new YawlA2AClient(agentUrl);
            client.connect();

            if (!client.isConnected()) {
                throw new AssertionError("A2A client not connected");
            }

            AgentCapabilities capabilities = client.getCapabilities();
            if (capabilities == null) {
                throw new AssertionError("No capabilities available");
            }
            System.out.println("  Streaming: " + capabilities.streaming());
            System.out.println("  Push Notifications: " + capabilities.pushNotifications());

            List<AgentSkill> skills = client.getSkills();
            System.out.println("  Skills: " + skills.size() + " available");

            client.close();
        });
    }

    static void verifyMultiAgentOrchestration() {
        runCheck("Multi-Agent Orchestration", () -> {
            String orderUrl = System.getenv("ORDER_AGENT_URL");
            if (orderUrl == null || orderUrl.isEmpty()) {
                orderUrl = "http://order-agent:8081";
            }
            String inventoryUrl = System.getenv("INVENTORY_AGENT_URL");
            if (inventoryUrl == null || inventoryUrl.isEmpty()) {
                inventoryUrl = "http://inventory-agent:8081";
            }

            YawlA2AClient orderAgent = new YawlA2AClient(orderUrl);
            YawlA2AClient inventoryAgent = new YawlA2AClient(inventoryUrl);

            orderAgent.connect();
            inventoryAgent.connect();

            String orderResponse = orderAgent.sendMessage(
                    "List all loaded workflow specifications");
            if (orderResponse == null || orderResponse.isEmpty()) {
                throw new AssertionError("Empty orchestration response from order agent");
            }
            System.out.println("  Order Agent: " + orderResponse.substring(0, Math.min(50, orderResponse.length())) + "...");

            String inventoryResponse = inventoryAgent.sendMessage(
                    "Show all work items");
            if (inventoryResponse == null || inventoryResponse.isEmpty()) {
                throw new AssertionError("Empty orchestration response from inventory agent");
            }
            System.out.println("  Inventory Agent: " + inventoryResponse.substring(0, Math.min(50, inventoryResponse.length())) + "...");

            orderAgent.close();
            inventoryAgent.close();
        });
    }

    static void verifyEndToEndWorkflow() {
        runCheck("End-to-End Workflow", () -> {
            ZaiService zai = new ZaiService();
            ZaiFunctionService functions = new ZaiFunctionService();

            String sseUrl = System.getenv("MCP_SERVER_URL");
            if (sseUrl == null || sseUrl.isEmpty()) {
                sseUrl = "http://localhost:3000";
            }
            String agentUrl = System.getenv("A2A_AGENT_URL");
            if (agentUrl == null || agentUrl.isEmpty()) {
                agentUrl = "http://localhost:8081";
            }

            YawlMcpClient mcp = new YawlMcpClient();
            mcp.connectSse(sseUrl);

            YawlA2AClient a2a = new YawlA2AClient(agentUrl);
            a2a.connect();

            if (!mcp.isConnected()) throw new AssertionError("MCP not connected");
            if (!a2a.isConnected()) throw new AssertionError("A2A not connected");

            String analysis = zai.analyzeWorkflowContext(
                    "OrderProcessing",
                    "ValidateOrder",
                    "{\"orderId\": \"ORD-123\"}"
            );
            if (analysis == null || analysis.isEmpty()) {
                throw new AssertionError("Empty analysis");
            }

            String result = functions.processWithFunctions("Check status of case case-001");
            if (result == null || result.isEmpty()) {
                throw new AssertionError("Empty function result");
            }

            System.out.println("  All services integrated successfully");

            mcp.close();
            a2a.close();
        });
    }

    static void runCheck(String name, Runnable check) {
        checksRun++;
        System.out.println("\n--- Running: " + name + " ---");
        try {
            check.run();
            checksPassed++;
            System.out.println("PASSED: " + name);
        } catch (Exception e) {
            System.out.println("FAILED: " + name);
            System.out.println("  Error: " + e.getMessage());
        }
    }

    static void printSummary() {
        System.out.println("\n========================================");
        System.out.println("Check Summary");
        System.out.println("========================================");
        System.out.println("Checks Run:    " + checksRun);
        System.out.println("Checks Passed: " + checksPassed);
        System.out.println("Checks Failed: " + (checksRun - checksPassed));
        System.out.println();

        if (checksPassed == checksRun) {
            System.out.println("Result: ALL CHECKS PASSED");
            System.exit(0);
        } else {
            System.out.println("Result: SOME CHECKS FAILED");
            System.exit(1);
        }
    }
}
