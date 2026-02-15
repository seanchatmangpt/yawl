package org.yawlfoundation.yawl.integration.test;

import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.integration.zai.ZaiFunctionService;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpClient;
import org.yawlfoundation.yawl.integration.a2a.YawlA2AClient;

import java.util.Arrays;

/**
 * Integration tests for MCP, A2A, and Z.AI
 *
 * Chicago TDD: Tests drive the implementation.
 * All tests require ZAI_API_KEY environment variable.
 */
public class SelfPlayTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("YAWL MCP/A2A/Z.AI Integration Tests");
        System.out.println("========================================\n");

        String apiKey = System.getenv("ZAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("ZAI_API_KEY environment variable not set");
            System.err.println("Set ZAI_API_KEY and run again");
            System.exit(1);
        }
        System.out.println("Z.AI Service initialized successfully\n");

        testZaiConnection();
        testBasicChat();
        testWorkflowDecision();
        testDataTransformation();
        testFunctionCalling();
        testMcpClient();
        testA2aClient();
        testMultiAgentOrchestration();
        testEndToEndWorkflow();

        printSummary();
    }

    static void testZaiConnection() {
        runTest("Z.AI Connection", () -> {
            ZaiService service = new ZaiService();
            boolean connected = service.verifyConnection();
            if (!connected) {
                throw new AssertionError("Failed to connect to Z.AI API");
            }
        });
    }

    static void testBasicChat() {
        runTest("Basic Chat", () -> {
            ZaiService service = new ZaiService();
            service.setSystemPrompt("You are a helpful assistant for YAWL workflows.");

            String response = service.chat("Say 'hello' in exactly one word");
            if (response == null || response.isEmpty()) {
                throw new AssertionError("Empty response from chat");
            }
            System.out.println("  Response: " + response.substring(0, Math.min(50, response.length())) + "...");
        });
    }

    static void testWorkflowDecision() {
        runTest("Workflow Decision", () -> {
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

    static void testDataTransformation() {
        runTest("Data Transformation", () -> {
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

    static void testFunctionCalling() {
        runTest("Function Calling", () -> {
            ZaiFunctionService service = new ZaiFunctionService();

            String result = service.processWithFunctions("List all available workflows");
            if (result == null || result.isEmpty()) {
                throw new AssertionError("Empty function response");
            }
            System.out.println("  Result: " + result.substring(0, Math.min(60, result.length())) + "...");
        });
    }

    static void testMcpClient() {
        runTest("MCP Client", () -> {
            YawlMcpClient client = new YawlMcpClient("http://localhost:3000");
            client.connect();

            if (!client.isConnected()) {
                throw new AssertionError("MCP client not connected");
            }

            String[] tools = client.listTools();
            if (tools == null || tools.length == 0) {
                throw new AssertionError("No tools available");
            }
            System.out.println("  Tools: " + tools.length + " available");

            client.disconnect();
        });
    }

    static void testA2aClient() {
        runTest("A2A Client", () -> {
            YawlA2AClient client = new YawlA2AClient("http://localhost:8080");
            client.connect();

            if (!client.isConnected()) {
                throw new AssertionError("A2A client not connected");
            }

            String capabilities = client.getCapabilities();
            if (capabilities == null || capabilities.isEmpty()) {
                throw new AssertionError("No capabilities available");
            }
            System.out.println("  Capabilities: " + capabilities.substring(0, Math.min(40, capabilities.length())) + "...");

            client.disconnect();
        });
    }

    static void testMultiAgentOrchestration() {
        runTest("Multi-Agent Orchestration", () -> {
            YawlA2AClient orderAgent = new YawlA2AClient("http://order-agent:8080");
            YawlA2AClient inventoryAgent = new YawlA2AClient("http://inventory-agent:8080");

            orderAgent.connect();
            inventoryAgent.connect();

            String plan = orderAgent.getOrchestrationPlan(
                    "Process customer order and check inventory",
                    new String[]{"OrderAgent", "InventoryAgent"}
            );

            if (plan == null || plan.isEmpty()) {
                throw new AssertionError("Empty orchestration plan");
            }
            System.out.println("  Plan: " + plan.substring(0, Math.min(50, plan.length())) + "...");

            orderAgent.disconnect();
            inventoryAgent.disconnect();
        });
    }

    static void testEndToEndWorkflow() {
        runTest("End-to-End Workflow", () -> {
            // Initialize all services
            ZaiService zai = new ZaiService();
            ZaiFunctionService functions = new ZaiFunctionService();
            YawlMcpClient mcp = new YawlMcpClient("http://localhost:3000");
            YawlA2AClient a2a = new YawlA2AClient("http://localhost:8080");

            mcp.connect();
            a2a.connect();

            // Verify all connected
            if (!mcp.isConnected()) throw new AssertionError("MCP not connected");
            if (!a2a.isConnected()) throw new AssertionError("A2A not connected");

            // Use AI to analyze workflow context
            String analysis = zai.analyzeWorkflowContext(
                    "OrderProcessing",
                    "ValidateOrder",
                    "{\"orderId\": \"ORD-123\"}"
            );
            if (analysis == null || analysis.isEmpty()) {
                throw new AssertionError("Empty analysis");
            }

            // Execute function via AI
            String result = functions.processWithFunctions("Check status of case case-test-001");
            if (result == null || result.isEmpty()) {
                throw new AssertionError("Empty function result");
            }

            System.out.println("  All services integrated successfully");

            mcp.disconnect();
            a2a.disconnect();
        });
    }

    static void runTest(String name, Runnable test) {
        testsRun++;
        System.out.println("\n--- Running: " + name + " ---");
        try {
            test.run();
            testsPassed++;
            System.out.println("✓ PASSED: " + name);
        } catch (Exception e) {
            System.out.println("✗ FAILED: " + name);
            System.out.println("  Error: " + e.getMessage());
        }
    }

    static void printSummary() {
        System.out.println("\n========================================");
        System.out.println("Test Summary");
        System.out.println("========================================");
        System.out.println("Tests Run:    " + testsRun);
        System.out.println("Tests Passed: " + testsPassed);
        System.out.println("Tests Failed: " + (testsRun - testsPassed));
        System.out.println();

        if (testsPassed == testsRun) {
            System.out.println("Result: ALL TESTS PASSED");
            System.exit(0);
        } else {
            System.out.println("Result: SOME TESTS FAILED");
            System.exit(1);
        }
    }
}
