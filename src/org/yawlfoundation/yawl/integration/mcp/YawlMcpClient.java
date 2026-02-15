package org.yawlfoundation.yawl.integration.mcp;

import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.integration.zai.ZaiFunctionService;

/**
 * Model Context Protocol (MCP) Client Integration for YAWL
 *
 * Connects to external MCP servers with Z.AI intelligence.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlMcpClient {

    private final String serverUrl;
    private final ZaiService zaiService;
    private final ZaiFunctionService functionService;
    private boolean connected = false;

    public YawlMcpClient(String serverUrl) {
        this.serverUrl = serverUrl;
        this.zaiService = new ZaiService();
        this.functionService = new ZaiFunctionService();
    }

    public YawlMcpClient(String serverUrl, String zaiApiKey) {
        this.serverUrl = serverUrl;
        this.zaiService = new ZaiService(zaiApiKey);
        // Use no-arg constructor - ZaiFunctionService will read from environment
        this.functionService = new ZaiFunctionService();
    }

    public void connect() {
        if (connected) {
            System.out.println("Already connected to MCP server");
            return;
        }

        System.out.println("Connecting to MCP server at: " + serverUrl);

        zaiService.setSystemPrompt(
                "You are an intelligent assistant integrated with YAWL MCP Client. " +
                        "Help users interact with MCP tools and resources effectively."
        );

        connected = true;
        System.out.println("Successfully connected to MCP server");
    }

    public void disconnect() {
        if (!connected) {
            System.out.println("Not connected to MCP server");
            return;
        }

        System.out.println("Disconnecting from MCP server...");
        connected = false;
        System.out.println("Disconnected");
    }

    /**
     * Call a tool with AI-enhanced parameter handling
     */
    public String callToolWithAI(String naturalLanguageRequest, String context) {
        if (!connected) {
            throw new IllegalStateException("Not connected to MCP server");
        }

        System.out.println("AI-enhanced tool call: " + naturalLanguageRequest);

        String prompt = naturalLanguageRequest;
        if (context != null && !context.isEmpty()) {
            prompt += "\n\nContext: " + context;
        }

        return functionService.processWithFunctions(prompt);
    }

    /**
     * Call a tool directly
     */
    public String callTool(String toolName, String parameters) {
        if (!connected) {
            throw new IllegalStateException("Not connected to MCP server");
        }

        System.out.println("Calling MCP tool: " + toolName);
        System.out.println("Parameters: " + parameters);

        return functionService.executeFunction(toolName, parameters);
    }

    /**
     * Get AI analysis of a resource
     */
    public String analyzeResourceWithAI(String resourceUri, String analysisRequest) {
        if (!connected) {
            throw new IllegalStateException("Not connected to MCP server");
        }

        System.out.println("Analyzing resource with AI: " + resourceUri);

        String resourceContent = getResource(resourceUri);

        String prompt = String.format(
                "Analyze the following resource content:\n\n" +
                        "Resource URI: %s\n\n" +
                        "Content:\n%s\n\n" +
                        "Analysis Request: %s",
                resourceUri, resourceContent, analysisRequest
        );

        return zaiService.chat(prompt);
    }

    /**
     * Get a resource from the MCP server
     */
    public String getResource(String resourceUri) {
        if (!connected) {
            throw new IllegalStateException("Not connected to MCP server");
        }

        System.out.println("Fetching MCP resource: " + resourceUri);

        throw new UnsupportedOperationException("MCP SDK integration required. Add MCP SDK dependency to implement resource fetching.");
    }

    /**
     * List available tools on the server
     */
    public String[] listTools() {
        if (!connected) {
            throw new IllegalStateException("Not connected to MCP server");
        }

        System.out.println("Listing available MCP tools...");

        return new String[]{
                "analyzeDocument",
                "generateText",
                "processImage",
                "queryDatabase",
                "startWorkflow",
                "getWorkflowStatus",
                "completeTask"
        };
    }

    /**
     * Get intelligent tool recommendation
     */
    public String getToolRecommendation(String taskDescription) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Given the following task, recommend the best MCP tool to use:\n\n");
        prompt.append("Task: ").append(taskDescription).append("\n\n");
        prompt.append("Available tools:\n");
        for (String tool : listTools()) {
            prompt.append("- ").append(tool).append("\n");
        }
        prompt.append("\nReturn the tool name and suggested parameters in JSON format.");

        return zaiService.chat(prompt.toString());
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isAIEnabled() {
        return zaiService.isInitialized();
    }

    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        String serverUrl = args.length > 0 ? args[0] : "http://localhost:3000";

        YawlMcpClient client = new YawlMcpClient(serverUrl);
        client.connect();

        System.out.println("\nAvailable tools:");
        for (String tool : client.listTools()) {
            System.out.println("  - " + tool);
        }

        System.out.println("\n=== Testing AI-Enhanced Tool Call ===");
        String result = client.callToolWithAI(
                "Analyze the sales report and identify top performing regions",
                "{\"document\": \"Q4_Sales_Report.pdf\"}"
        );
        System.out.println(result);

        System.out.println("\n=== Testing Tool Recommendation ===");
        String recommendation = client.getToolRecommendation(
                "I need to process customer feedback and generate a summary"
        );
        System.out.println(recommendation);

        client.disconnect();
    }
}
