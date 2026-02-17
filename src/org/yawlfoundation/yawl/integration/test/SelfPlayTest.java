package org.yawlfoundation.yawl.integration.test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.yawlfoundation.yawl.integration.a2a.YawlA2AClient;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpClient;
import org.yawlfoundation.yawl.integration.zai.ZaiFunctionService;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentSkill;
import io.modelcontextprotocol.spec.McpSchema;

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
    private static int checksSkipped = 0;

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
        if (System.getenv("YAWL_ENGINE_URL") == null || System.getenv("YAWL_ENGINE_URL").isEmpty()) {
            runSkip("Function Calling", "YAWL_ENGINE_URL not set (ZaiFunctionService requires engine)");
            return;
        }
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
        String engineUrl = System.getenv("YAWL_ENGINE_URL");
        if (engineUrl == null || engineUrl.isEmpty()) {
            runSkip("MCP Client", "YAWL_ENGINE_URL not set (cannot spawn MCP server)");
            return;
        }
        final String engUrl = engineUrl;
        final String username = System.getenv("YAWL_USERNAME") != null ? System.getenv("YAWL_USERNAME") : "admin";
        final String password = System.getenv("YAWL_PASSWORD") != null ? System.getenv("YAWL_PASSWORD") : "YAWL";

        runCheck("MCP Client", () -> {
            String cp = "classes:build/3rdParty/lib/*";
            YawlMcpClient client = new YawlMcpClient();
            client.connectStdio("java",
                "-cp", cp,
                "-DYAWL_ENGINE_URL=" + engUrl,
                "-DYAWL_USERNAME=" + username,
                "-DYAWL_PASSWORD=" + password,
                "org.yawlfoundation.yawl.integration.mcp.YawlMcpServer");

            if (!client.isConnected()) {
                throw new AssertionError("MCP client not connected");
            }

            List<McpSchema.Tool> tools = client.listTools();
            if (tools == null || tools.isEmpty()) {
                throw new AssertionError("No tools available");
            }
            System.out.println("  Tools: " + tools.size() + " available");

            McpSchema.CallToolResult listResult = client.callTool(
                "yawl_list_specifications", Collections.emptyMap());
            if (listResult == null) {
                throw new AssertionError("yawl_list_specifications returned null");
            }
            System.out.println("  yawl_list_specifications: OK");

            client.close();
        });
    }

    static void verifyA2aClient() {
        String agentUrl = System.getenv("A2A_AGENT_URL");
        if (agentUrl == null || agentUrl.isEmpty()) {
            agentUrl = "http://localhost:8081";
        }
        final String url = agentUrl;

        YawlA2AClient client = new YawlA2AClient(url);
        try {
            client.connect();
        } catch (Exception e) {
            runSkip("A2A Client", "A2A server not reachable at " + url + ": " + e.getMessage());
            return;
        }

        runCheck("A2A Client", () -> {
            try {
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

                String response = client.sendMessage("List all loaded workflow specifications");
                if (response == null || response.isEmpty()) {
                    throw new AssertionError("Empty response from sendMessage");
                }
                System.out.println("  sendMessage: " + response.substring(0, Math.min(60, response.length())) + "...");
            } finally {
                client.close();
            }
        });
    }

    static void verifyMultiAgentOrchestration() {
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
        try {
            orderAgent.connect();
            inventoryAgent.connect();
        } catch (Exception e) {
            runSkip("Multi-Agent Orchestration",
                "Order/Inventory agents not reachable (" + orderUrl + ", " + inventoryUrl + "): " + e.getMessage());
            return;
        }

        runCheck("Multi-Agent Orchestration", () -> {

            try {
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
            } finally {
                orderAgent.close();
                inventoryAgent.close();
            }
        });
    }

    static void verifyEndToEndWorkflow() {
        runCheck("End-to-End Workflow", () -> {
            ZaiService zai = new ZaiService();
            ZaiFunctionService functions = new ZaiFunctionService();

            String engineUrl = System.getenv("YAWL_ENGINE_URL");
            final String mcpUsername = System.getenv("YAWL_USERNAME") != null ? System.getenv("YAWL_USERNAME") : "admin";
            final String mcpPassword = System.getenv("YAWL_PASSWORD") != null ? System.getenv("YAWL_PASSWORD") : "YAWL";

            YawlMcpClient mcp = new YawlMcpClient();
            if (engineUrl != null && !engineUrl.isEmpty()) {
                String cp = "classes:build/3rdParty/lib/*";
                mcp.connectStdio("java",
                    "-cp", cp,
                    "-DYAWL_ENGINE_URL=" + engineUrl,
                    "-DYAWL_USERNAME=" + mcpUsername,
                    "-DYAWL_PASSWORD=" + mcpPassword,
                    "org.yawlfoundation.yawl.integration.mcp.YawlMcpServer");
            } else {
                String sseUrl = System.getenv("MCP_SERVER_URL");
                if (sseUrl == null || sseUrl.isEmpty()) sseUrl = "http://localhost:3000";
                mcp.connectSse(sseUrl);
            }

            String agentUrl = System.getenv("A2A_AGENT_URL");
            if (agentUrl == null || agentUrl.isEmpty()) {
                agentUrl = "http://localhost:8081";
            }
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

    static void runSkip(String name, String reason) {
        checksRun++;
        checksSkipped++;
        System.out.println("\n--- Running: " + name + " ---");
        System.out.println("SKIPPED: " + name);
        System.out.println("  Reason: " + reason);
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
        int failed = checksRun - checksPassed - checksSkipped;
        System.out.println("\n========================================");
        System.out.println("Check Summary");
        System.out.println("========================================");
        System.out.println("Checks Run:    " + checksRun);
        System.out.println("Checks Passed: " + checksPassed);
        System.out.println("Checks Skipped: " + checksSkipped);
        System.out.println("Checks Failed: " + failed);
        System.out.println();

        if (failed > 0) {
            System.out.println("Result: SOME CHECKS FAILED");
            System.exit(1);
        }
        if (checksPassed + checksSkipped == checksRun) {
            System.out.println("Result: ALL CHECKS PASSED (some skipped)");
            System.exit(0);
        }
        System.out.println("Result: ALL CHECKS PASSED");
        System.exit(0);
    }
}
