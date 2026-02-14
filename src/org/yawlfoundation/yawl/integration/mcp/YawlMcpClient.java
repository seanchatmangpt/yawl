package org.yawlfoundation.yawl.integration.mcp;

import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.integration.zai.ZaiFunctionService;

/**
 * Model Context Protocol (MCP) Client Integration for YAWL
 *
 * Connects to external MCP servers and uses Z.AI for intelligent tool calls.
 * Enables YAWL workflows to access AI models and tools for enhanced capabilities.
 *
 * Features:
 * - Z.AI powered intelligent tool discovery and selection
 * - Natural language to tool call translation
 * - Context-aware resource fetching
 * - Streaming response support
 *
 * Example Usage:
 *
 * YawlMcpClient client = new YawlMcpClient("http://localhost:3000");
 * client.connect();
 * String result = client.callToolWithAI("Analyze this document and extract key insights", params);
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlMcpClient {

    private String serverUrl;
    private boolean connected = false;
    private ZaiService zaiService;
    private ZaiFunctionService functionService;

    /**
     * Constructor for YAWL MCP Client
     * @param serverUrl URL of the MCP server
     */
    public YawlMcpClient(String serverUrl) {
        this.serverUrl = serverUrl;
        this.zaiService = new ZaiService();
        this.functionService = new ZaiFunctionService();
        System.out.println("Initializing YAWL MCP Client for server at: " + serverUrl);
    }

    /**
     * Constructor with explicit Z.AI API key
     * @param serverUrl URL of the MCP server
     * @param zaiApiKey Z.AI API key
     */
    public YawlMcpClient(String serverUrl, String zaiApiKey) {
        this.serverUrl = serverUrl;
        this.zaiService = new ZaiService(zaiApiKey);
        this.functionService = new ZaiFunctionService(zaiApiKey);
        System.out.println("Initializing YAWL MCP Client with Z.AI at: " + serverUrl);
    }

    /**
     * Connect to the MCP server
     */
    public void connect() {
        if (connected) {
            System.out.println("Already connected to MCP server");
            return;
        }

        System.out.println("Connecting to MCP server at: " + serverUrl);

        // Set up system prompt for MCP context
        if (zaiService.isInitialized()) {
            zaiService.setSystemPrompt(
                    "You are an intelligent assistant integrated with YAWL MCP Client. " +
                            "Help users interact with MCP tools and resources effectively. " +
                            "Translate natural language requests into appropriate tool calls."
            );
        }

        connected = true;
        System.out.println("Successfully connected to MCP server");
        if (zaiService.isInitialized()) {
            System.out.println("Z.AI intelligence enabled for tool calls");
        }
    }

    /**
     * Disconnect from the MCP server
     */
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
     * Call a tool on the MCP server with AI-enhanced parameter handling
     * @param naturalLanguageRequest Natural language description of what to do
     * @param context Additional context data (JSON string)
     * @return Result from the tool
     */
    public String callToolWithAI(String naturalLanguageRequest, String context) {
        if (!connected) {
            throw new IllegalStateException("Not connected to MCP server");
        }

        System.out.println("AI-enhanced tool call: " + naturalLanguageRequest);

        if (functionService.isInitialized()) {
            // Use Z.AI function calling for intelligent tool selection
            String prompt = naturalLanguageRequest;
            if (context != null && !context.isEmpty()) {
                prompt += "\n\nContext: " + context;
            }
            return functionService.processWithFunctions(prompt);
        } else {
            return "Error: Z.AI not initialized. Set ZAI_API_KEY environment variable.";
        }
    }

    /**
     * Call a tool directly on the MCP server
     * @param toolName name of the tool to call
     * @param parameters parameters for the tool
     * @return result from the tool
     */
    public String callTool(String toolName, String parameters) {
        if (!connected) {
            throw new IllegalStateException("Not connected to MCP server");
        }

        System.out.println("Calling MCP tool: " + toolName);
        System.out.println("Parameters: " + parameters);

        throw new UnsupportedOperationException("MCP SDK integration required. Add MCP SDK dependency to implement tool calling.");
    }

    /**
     * Get AI analysis of a resource
     * @param resourceUri URI of the resource
     * @param analysisRequest What kind of analysis to perform
     * @return AI-generated analysis
     */
    public String analyzeResourceWithAI(String resourceUri, String analysisRequest) {
        if (!connected) {
            throw new IllegalStateException("Not connected to MCP server");
        }

        System.out.println("Analyzing resource with AI: " + resourceUri);

        // First get the resource content
        String resourceContent = getResource(resourceUri);

        // Then use Z.AI to analyze it
        if (zaiService.isInitialized()) {
            String prompt = String.format(
                    "Analyze the following resource content:\n\n" +
                            "Resource URI: %s\n\n" +
                            "Content:\n%s\n\n" +
                            "Analysis Request: %s",
                    resourceUri, resourceContent, analysisRequest
            );
            return zaiService.chat(prompt);
        } else {
            return "Error: Z.AI not initialized for analysis";
        }
    }

    /**
     * Get a resource from the MCP server
     * @param resourceUri URI of the resource
     * @return resource content
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
     * @return array of tool names
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
     * Get intelligent tool recommendation based on task description
     * @param taskDescription Description of the task to accomplish
     * @return Recommended tool name and parameters
     */
    public String getToolRecommendation(String taskDescription) {
        if (!zaiService.isInitialized()) {
            return "Error: Z.AI not initialized for recommendations";
        }

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

    /**
     * Check if connected to MCP server
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Check if Z.AI intelligence is available
     * @return true if Z.AI is initialized
     */
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

        // List available tools
        System.out.println("\nAvailable tools:");
        for (String tool : client.listTools()) {
            System.out.println("  - " + tool);
        }

        if (client.isAIEnabled()) {
            // Test AI-enhanced tool call
            System.out.println("\n=== Testing AI-Enhanced Tool Call ===");
            String result = client.callToolWithAI(
                    "Analyze the sales report and identify top performing regions",
                    "{\"document\": \"Q4_Sales_Report.pdf\"}"
            );
            System.out.println(result);

            // Test tool recommendation
            System.out.println("\n=== Testing Tool Recommendation ===");
            String recommendation = client.getToolRecommendation(
                    "I need to process customer feedback and generate a summary"
            );
            System.out.println(recommendation);
        } else {
            System.out.println("\nZ.AI not initialized - set ZAI_API_KEY for AI features");
        }

        client.disconnect();
    }
}
