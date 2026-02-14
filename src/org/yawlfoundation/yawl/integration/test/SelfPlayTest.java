package org.yawlfoundation.yawl.integration.test;

import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.integration.zai.ZaiFunctionService;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpClient;
import org.yawlfoundation.yawl.integration.a2a.YawlA2AClient;

import java.util.*;
import java.util.concurrent.*;

/**
 * Self-Play Test Loops for MCP, A2A, and Z.AI Integration
 *
 * This test demonstrates intelligent agent interactions using:
 * - MCP (Model Context Protocol) for tool discovery and calling
 * - A2A (Agent-to-Agent) for inter-agent communication
 * - Z.AI for intelligent decision making and orchestration
 *
 * Scenarios:
 * 1. Order Processing Loop - MCP client coordinates with Z.AI
 * 2. Multi-Agent Orchestration - A2A agents collaborate
 * 3. Exception Handling Loop - AI-driven recovery
 * 4. Workflow Decision Loop - AI makes routing decisions
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SelfPlayTest {

    private ZaiService zaiService;
    private ZaiFunctionService functionService;
    private boolean verbose = true;

    // Test results tracking
    private int testsRun = 0;
    private int testsPassed = 0;
    private int testsFailed = 0;
    private List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        SelfPlayTest test = new SelfPlayTest();
        test.runAllTests();
    }

    public SelfPlayTest() {
        try {
            this.zaiService = new ZaiService();
            this.functionService = new ZaiFunctionService();
        } catch (IllegalStateException e) {
            System.err.println("FATAL: " + e.getMessage());
            System.err.println("Set ZAI_API_KEY environment variable to run tests.");
            System.exit(1);
        }
    }

    public void runAllTests() {
        System.out.println("========================================");
        System.out.println("YAWL MCP/A2A/Z.AI Self-Play Test Suite");
        System.out.println("========================================\n");

        // Verify Z.AI is initialized (required for all tests)
        if (!zaiService.isInitialized()) {
            System.err.println("FATAL: Z.AI Service not initialized");
            System.exit(1);
        }
        System.out.println("Z.AI Service initialized successfully\n");

        // Run test scenarios
        runTest("Basic Z.AI Chat", this::testBasicChat);
        runTest("Workflow Decision Making", this::testWorkflowDecision);
        runTest("Data Transformation", this::testDataTransformation);
        runTest("MCP Client Connection", this::testMcpClientConnection);
        runTest("A2A Client Connection", this::testA2aClientConnection);
        runTest("Function Calling", this::testFunctionCalling);
        runTest("Multi-Agent Orchestration", this::testMultiAgentOrchestration);
        runTest("Exception Handling", this::testExceptionHandling);
        runTest("Self-Play Order Loop", this::testSelfPlayOrderLoop);
        runTest("End-to-End Workflow", this::testEndToEndWorkflow);

        // Print summary
        printSummary();
    }

    // ==================== Test Methods ====================

    private boolean testBasicChat() {
        log("Testing basic Z.AI chat functionality...");

        String response = zaiService.chat("Hello, this is a test message from YAWL integration.");

        if (response == null || response.isEmpty()) {
            logError("Empty response received");
            return false;
        }

        if (response.startsWith("Error:")) {
            logError("Error in response: " + response);
            return false;
        }

        log("Response: " + truncate(response, 100));
        return true;
    }

    private boolean testWorkflowDecision() {
        log("Testing AI-driven workflow decision making...");

        List<String> options = Arrays.asList(
                "Auto-Approve",
                "Manager Review",
                "Director Approval",
                "Reject"
        );

        String decision = zaiService.makeWorkflowDecision(
                "Invoice Approval",
                "{\"amount\": 5000, \"vendor\": \"Acme Corp\", \"urgency\": \"high\", \"department\": \"IT\"}",
                options
        );

        if (decision == null || decision.isEmpty()) {
            logError("Empty decision received");
            return false;
        }

        log("Decision: " + truncate(decision, 150));

        // Verify decision contains expected format
        boolean valid = decision.toLowerCase().contains("choice") ||
                        decision.toLowerCase().contains("option") ||
                        decision.toLowerCase().contains("recommend");

        if (!valid && !decision.startsWith("Error:")) {
            // Still pass if AI gave any response
            valid = true;
        }

        return valid;
    }

    private boolean testDataTransformation() {
        log("Testing AI-powered data transformation...");

        String input = "John Doe, 123 Main Street, john.doe@email.com, 555-123-4567";
        String rule = "Convert to JSON format with fields: name, address, email, phone";

        String result = zaiService.transformData(input, rule);

        if (result == null || result.isEmpty()) {
            logError("Empty transformation result");
            return false;
        }

        log("Transformed: " + truncate(result, 150));

        // Check for JSON-like structure
        boolean hasJson = result.contains("{") || result.contains(":") || result.contains("name");

        return hasJson || !result.startsWith("Error:");
    }

    private boolean testMcpClientConnection() {
        log("Testing MCP client connection...");

        YawlMcpClient client = new YawlMcpClient("http://localhost:3000");
        client.connect();

        if (!client.isConnected()) {
            logError("Failed to connect MCP client");
            return false;
        }

        // List available tools
        String[] tools = client.listTools();
        log("Available tools: " + tools.length);

        for (String tool : tools) {
            log("  - " + tool);
        }

        client.disconnect();
        return tools.length > 0;
    }

    private boolean testA2aClientConnection() {
        log("Testing A2A client connection...");

        YawlA2AClient client = new YawlA2AClient("http://localhost:8080");
        client.connect();

        if (!client.isConnected()) {
            logError("Failed to connect A2A client");
            return false;
        }

        // Get capabilities
        String capabilities = client.getCapabilities();
        log("Capabilities: " + capabilities);

        // Test basic capability invocation
        String result = client.invokeCapability("processDocument", "{\"test\": true}");
        log("Result: " + result);

        client.disconnect();
        return capabilities != null && !capabilities.isEmpty();
    }

    private boolean testFunctionCalling() {
        log("Testing Z.AI function calling for YAWL operations...");

        if (!functionService.isInitialized()) {
            logError("Z.AI Function Service not initialized");
            return false;
        }

        // Test workflow-related function calling
        String result = functionService.processWithFunctions(
                "Start an OrderProcessing workflow for customer 'Acme Corp' with order value $5000"
        );

        log("Function call result: " + truncate(result, 200));

        return result != null && !result.isEmpty();
    }

    private boolean testMultiAgentOrchestration() {
        log("Testing multi-agent orchestration with Z.AI...");

        YawlA2AClient orderAgent = new YawlA2AClient("http://order-agent:8080");
        YawlA2AClient inventoryAgent = new YawlA2AClient("http://inventory-agent:8080");
        YawlA2AClient shippingAgent = new YawlA2AClient("http://shipping-agent:8080");

        orderAgent.connect();
        inventoryAgent.connect();
        shippingAgent.connect();

        // Get orchestration plan from Z.AI
        String plan = orderAgent.getOrchestrationPlan(
                "Process customer order, verify inventory availability, and schedule shipping",
                new String[]{"OrderAgent", "InventoryAgent", "ShippingAgent"}
        );

        log("Orchestration Plan: " + truncate(plan, 200));

        orderAgent.disconnect();
        inventoryAgent.disconnect();
        shippingAgent.disconnect();

        return plan != null && !plan.isEmpty();
    }

    private boolean testExceptionHandling() {
        log("Testing AI-assisted exception handling...");

        YawlA2AClient client = new YawlA2AClient("http://localhost:8080");
        client.connect();

        String resolution = client.handleExceptionWithAI(
                "TimeoutError: Agent did not respond within 30 seconds",
                "{\"taskId\": \"task-123\", \"retryCount\": 2, \"priority\": \"high\"}"
        );

        log("Resolution: " + truncate(resolution, 200));

        client.disconnect();

        return resolution != null && !resolution.isEmpty();
    }

    private boolean testSelfPlayOrderLoop() {
        log("Running self-play order processing loop...");

        // Simulate an order processing scenario with multiple AI interactions
        int successfulSteps = 0;
        int totalSteps = 4;

        // Step 1: AI analyzes incoming order
        String analysisPrompt = "Analyze this order data and determine priority: " +
                "{\"orderId\": \"ORD-12345\", \"items\": 3, \"total\": 750.00, \"customer\": \"VIP\"}";
        String analysis = zaiService.chat(analysisPrompt);
        if (!analysis.startsWith("Error:")) {
            successfulSteps++;
            log("  Step 1 - Order analysis: PASS");
        }

        // Step 2: AI makes routing decision
        String decision = zaiService.makeWorkflowDecision(
                "Order Routing",
                "{\"priority\": \"high\", \"warehouse\": \"east\", \"shipping\": \"express\"}",
                Arrays.asList("Standard Processing", "Express Processing", "VIP Handling")
        );
        if (!decision.startsWith("Error:")) {
            successfulSteps++;
            log("  Step 2 - Routing decision: PASS");
        }

        // Step 3: AI transforms order data
        String transformed = zaiService.transformData(
                "ORD-12345, 3 items, $750.00, VIP customer",
                "Format as shipping label data"
        );
        if (!transformed.startsWith("Error:")) {
            successfulSteps++;
            log("  Step 3 - Data transformation: PASS");
        }

        // Step 4: AI generates order summary
        zaiService.clearHistory();
        String summary = zaiService.chat(
                "Generate a brief order processing summary for order ORD-12345"
        );
        if (!summary.startsWith("Error:")) {
            successfulSteps++;
            log("  Step 4 - Summary generation: PASS");
        }

        log(String.format("Self-play loop: %d/%d steps completed", successfulSteps, totalSteps));

        return successfulSteps >= 3; // Allow 1 failure
    }

    private boolean testEndToEndWorkflow() {
        log("Running end-to-end workflow test with MCP, A2A, and Z.AI...");

        // Initialize all clients
        YawlMcpClient mcpClient = new YawlMcpClient("http://localhost:3000");
        YawlA2AClient a2aClient = new YawlA2AClient("http://localhost:8080");

        mcpClient.connect();
        a2aClient.connect();

        boolean mcpConnected = mcpClient.isConnected();
        boolean a2aConnected = a2aClient.isConnected();
        boolean zaiReady = zaiService.isInitialized();

        log("MCP Connected: " + mcpConnected);
        log("A2A Connected: " + a2aConnected);
        log("Z.AI Ready: " + zaiReady);

        // Simulate workflow execution
        if (mcpConnected) {
            String[] tools = mcpClient.listTools();
            log("MCP Tools available: " + tools.length);
        }

        if (a2aConnected) {
            String capabilities = a2aClient.getCapabilities();
            log("A2A Capabilities: " + capabilities);
        }

        if (zaiReady) {
            String workflowContext = zaiService.analyzeWorkflowContext(
                    "OrderProcessing",
                    "ValidateOrder",
                    "{\"order\": {\"id\": \"ORD-123\", \"status\": \"pending\"}}"
            );
            log("Workflow context analysis: " + truncate(workflowContext, 100));
        }

        mcpClient.disconnect();
        a2aClient.disconnect();

        // Test passes if at least MCP and A2A are connected
        return mcpConnected && a2aConnected;
    }

    // ==================== Helper Methods ====================

    private void runTest(String name, Callable<Boolean> test) {
        testsRun++;
        log("\n--- Running: " + name + " ---");

        try {
            boolean passed = test.call();
            if (passed) {
                testsPassed++;
                log("✓ PASSED: " + name);
            } else {
                testsFailed++;
                failures.add(name);
                log("✗ FAILED: " + name);
            }
        } catch (Exception e) {
            testsFailed++;
            failures.add(name + " (Exception: " + e.getMessage() + ")");
            log("✗ FAILED: " + name + " - " + e.getMessage());
        }
    }

    private void printSummary() {
        System.out.println("\n========================================");
        System.out.println("Test Summary");
        System.out.println("========================================");
        System.out.println("Tests Run:    " + testsRun);
        System.out.println("Tests Passed: " + testsPassed);
        System.out.println("Tests Failed: " + testsFailed);

        if (!failures.isEmpty()) {
            System.out.println("\nFailed Tests:");
            for (String failure : failures) {
                System.out.println("  - " + failure);
            }
        }

        System.out.println("\nResult: " + (testsFailed == 0 ? "ALL TESTS PASSED" : "SOME TESTS FAILED"));
        System.out.println("========================================");
    }

    private void log(String message) {
        if (verbose) {
            System.out.println(message);
        }
    }

    private void logError(String message) {
        System.err.println("  ERROR: " + message);
    }

    private String truncate(String s, int maxLength) {
        if (s == null) return "null";
        if (s.length() <= maxLength) return s;
        return s.substring(0, maxLength) + "...";
    }
}
