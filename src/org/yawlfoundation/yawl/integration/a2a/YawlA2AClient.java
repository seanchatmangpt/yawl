package org.yawlfoundation.yawl.integration.a2a;

import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.integration.zai.ZaiFunctionService;

/**
 * A2A Client Integration for YAWL with Z.AI Intelligence
 *
 * Connects YAWL to other agents using the A2A protocol with AI-enhanced capabilities.
 * Uses Z.AI for intelligent capability discovery, selection, and invocation.
 *
 * Features:
 * - Z.AI powered intelligent agent communication
 * - Natural language to capability mapping
 * - Context-aware request formulation
 * - Multi-agent orchestration support
 *
 * Example Usage:
 *
 * YawlA2AClient client = new YawlA2AClient("http://external-agent.example.com:8080");
 * client.connect();
 * String result = client.invokeWithAI("Process this invoice and handle exceptions", invoiceData);
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlA2AClient {

    private String agentUrl;
    private boolean connected = false;
    private ZaiService zaiService;
    private ZaiFunctionService functionService;
    private String agentCapabilities;

    /**
     * Constructor for YAWL A2A Client
     * @param agentUrl URL of the remote A2A agent
     */
    public YawlA2AClient(String agentUrl) {
        this.agentUrl = agentUrl;
        this.zaiService = new ZaiService();
        this.functionService = new ZaiFunctionService();
        System.out.println("Initializing YAWL A2A Client for agent at: " + agentUrl);
    }

    /**
     * Constructor with explicit Z.AI API key
     * @param agentUrl URL of the remote A2A agent
     * @param zaiApiKey Z.AI API key
     */
    public YawlA2AClient(String agentUrl, String zaiApiKey) {
        this.agentUrl = agentUrl;
        this.zaiService = new ZaiService(zaiApiKey);
        this.functionService = new ZaiFunctionService(zaiApiKey);
        System.out.println("Initializing YAWL A2A Client with Z.AI for agent at: " + agentUrl);
    }

    /**
     * Connect to the remote A2A agent
     */
    public void connect() {
        if (connected) {
            System.out.println("Already connected to agent");
            return;
        }

        System.out.println("Connecting to A2A agent at: " + agentUrl);

        // Set up system prompt for A2A context
        if (zaiService.isInitialized()) {
            zaiService.setSystemPrompt(
                    "You are an intelligent assistant for YAWL A2A (Agent-to-Agent) communication. " +
                            "Help coordinate between YAWL workflows and external agents. " +
                            "Translate workflow requirements into agent capability calls."
            );
        }

        this.agentCapabilities = discoverCapabilities();

        connected = true;
        System.out.println("Successfully connected to A2A agent");
        if (zaiService.isInitialized()) {
            System.out.println("Z.AI intelligence enabled for agent communication");
        }
    }

    /**
     * Disconnect from the remote agent
     */
    public void disconnect() {
        if (!connected) {
            System.out.println("Not connected to any agent");
            return;
        }

        System.out.println("Disconnecting from A2A agent...");
        connected = false;
        System.out.println("Disconnected");
    }

    /**
     * Discover available capabilities from the agent
     */
    private String discoverCapabilities() {
        throw new UnsupportedOperationException("A2A SDK integration required to discover agent capabilities. Add A2A SDK dependency.");
    }

    /**
     * Invoke a capability using natural language with AI enhancement
     * @param naturalLanguageRequest What to accomplish
     * @param data Data to process
     * @return Result from the capability
     */
    public String invokeWithAI(String naturalLanguageRequest, String data) {
        if (!connected) {
            throw new IllegalStateException("Not connected to agent");
        }

        System.out.println("AI-enhanced invocation: " + naturalLanguageRequest);

        if (zaiService.isInitialized()) {
            // Use AI to determine the best capability and format the request
            String prompt = String.format(
                    "Given the task: '%s'\n\n" +
                            "Available capabilities: %s\n\n" +
                            "Data to process: %s\n\n" +
                            "Determine the best capability to use and format the request appropriately. " +
                            "Return in format: CAPABILITY: [name] PARAMS: [json]",
                    naturalLanguageRequest, agentCapabilities, data
            );

            String aiResponse = zaiService.chat(prompt);

            // Parse AI response and invoke capability
            String capability = extractCapability(aiResponse);
            String params = extractParams(aiResponse, data);

            return invokeCapability(capability, params);
        } else {
            return "Error: Z.AI not initialized. Set ZAI_API_KEY environment variable.";
        }
    }

    /**
     * Invoke a capability on the remote agent
     * @param capabilityName name of the capability to invoke
     * @param data data to send to the capability
     * @return result from the capability
     */
    public String invokeCapability(String capabilityName, String data) {
        if (!connected) {
            throw new IllegalStateException("Not connected to agent");
        }

        System.out.println("Invoking capability: " + capabilityName);

        throw new UnsupportedOperationException("A2A SDK integration required to invoke agent capabilities. Add A2A SDK dependency.");
    }

    /**
     * Get AI-powered orchestration plan for multi-step workflow
     * @param workflowDescription Description of the workflow to orchestrate
     * @param availableAgents List of available agents
     * @return Orchestration plan
     */
    public String getOrchestrationPlan(String workflowDescription, String[] availableAgents) {
        if (!zaiService.isInitialized()) {
            return "Error: Z.AI not initialized";
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Create an orchestration plan for the following workflow:\n\n");
        prompt.append("Workflow: ").append(workflowDescription).append("\n\n");
        prompt.append("Available Agents:\n");
        for (String agent : availableAgents) {
            prompt.append("- ").append(agent).append("\n");
        }
        prompt.append("\nProvide a step-by-step plan with agent assignments.");

        return zaiService.chat(prompt.toString());
    }

    /**
     * Handle exception with AI assistance
     * @param exceptionDetails Details of the exception
     * @param context Workflow context
     * @return Resolution recommendation
     */
    public String handleExceptionWithAI(String exceptionDetails, String context) {
        if (!zaiService.isInitialized()) {
            return "Error: Z.AI not initialized";
        }

        String prompt = String.format(
                "An A2A agent interaction encountered an exception:\n\n" +
                        "Exception: %s\n\n" +
                        "Context: %s\n\n" +
                        "Available capabilities: %s\n\n" +
                        "Suggest the best recovery action or alternative capability to use.",
                exceptionDetails, context, agentCapabilities
        );

        return zaiService.chat(prompt.toString());
    }

    /**
     * Transform data for agent compatibility using AI
     * @param inputData Input data
     * @param targetAgentCapabilities Target agent's expected format
     * @return Transformed data
     */
    public String transformForAgent(String inputData, String targetAgentCapabilities) {
        if (!zaiService.isInitialized()) {
            return inputData; // Return unchanged if AI not available
        }

        return zaiService.transformData(inputData,
                "Transform to be compatible with agent capabilities: " + targetAgentCapabilities);
    }

    /**
     * Get available capabilities
     * @return Comma-separated list of capabilities
     */
    public String getCapabilities() {
        return agentCapabilities;
    }

    /**
     * Check if connected to agent
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

    // Helper methods

    private String extractCapability(String aiResponse) {
        String lower = aiResponse.toLowerCase();
        if (lower.contains("processdocument") || lower.contains("process document")) {
            return "processDocument";
        } else if (lower.contains("analyzedata") || lower.contains("analyze data")) {
            return "analyzeData";
        } else if (lower.contains("generatereport") || lower.contains("generate report")) {
            return "generateReport";
        } else if (lower.contains("handleexception") || lower.contains("handle exception")) {
            return "handleException";
        } else if (lower.contains("notifyuser") || lower.contains("notify user")) {
            return "notifyUser";
        }
        return "processDocument"; // Default
    }

    private String extractParams(String aiResponse, String originalData) {
        // Simple extraction - in production, use proper JSON parsing
        if (aiResponse.contains("PARAMS:")) {
            int start = aiResponse.indexOf("PARAMS:") + 7;
            int end = aiResponse.length();
            return aiResponse.substring(start, end).trim();
        }
        return originalData;
    }

    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        String agentUrl = args.length > 0 ? args[0] : "http://localhost:8080";

        YawlA2AClient client = new YawlA2AClient(agentUrl);
        client.connect();

        System.out.println("\nAvailable capabilities: " + client.getCapabilities());

        if (client.isAIEnabled()) {
            // Test AI-enhanced invocation
            System.out.println("\n=== Testing AI-Enhanced Invocation ===");
            String result = client.invokeWithAI(
                    "Process this invoice and check for any discrepancies",
                    "{\"invoiceId\": \"INV-123\", \"amount\": 1500, \"vendor\": \"Acme Corp\"}"
            );
            System.out.println(result);

            // Test exception handling
            System.out.println("\n=== Testing Exception Handling ===");
            String resolution = client.handleExceptionWithAI(
                    "Timeout while waiting for agent response",
                    "{\"taskId\": \"task-456\", \"retryCount\": 2}"
            );
            System.out.println(resolution);

            // Test orchestration
            System.out.println("\n=== Testing Orchestration Plan ===");
            String plan = client.getOrchestrationPlan(
                    "Process customer order, verify inventory, and schedule delivery",
                    new String[]{"OrderAgent", "InventoryAgent", "DeliveryAgent"}
            );
            System.out.println(plan);
        } else {
            System.out.println("\nZ.AI not initialized - set ZAI_API_KEY for AI features");

            // Test basic capability invocation
            String result = client.invokeCapability("processDocument", "{\"doc\": \"test.pdf\"}");
            System.out.println("Result: " + result);
        }

        client.disconnect();
    }
}
