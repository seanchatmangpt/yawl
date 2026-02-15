package org.yawlfoundation.yawl.integration.a2a;

import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.integration.zai.ZaiFunctionService;

/**
 * A2A Client Integration for YAWL with Z.AI Intelligence
 *
 * Connects YAWL to other agents using the A2A protocol with AI capabilities.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlA2AClient {

    private final String agentUrl;
    private final ZaiService zaiService;
    private final ZaiFunctionService functionService;
    private String agentCapabilities;
    private boolean connected = false;

    public YawlA2AClient(String agentUrl) {
        this.agentUrl = agentUrl;
        this.zaiService = new ZaiService();
        this.functionService = new ZaiFunctionService();
        System.out.println("Initializing YAWL A2A Client for agent at: " + agentUrl);
    }

    public YawlA2AClient(String agentUrl, String zaiApiKey) {
        this.agentUrl = agentUrl;
        this.zaiService = new ZaiService(zaiApiKey);
        // Use no-arg constructor - ZaiFunctionService will read from environment
        this.functionService = new ZaiFunctionService();
        System.out.println("Initializing YAWL A2A Client with Z.AI for agent at: " + agentUrl);
    }

    public void connect() {
        if (connected) {
            System.out.println("Already connected to agent");
            return;
        }

        System.out.println("Connecting to A2A agent at: " + agentUrl);

        zaiService.setSystemPrompt(
                "You are an intelligent assistant for YAWL A2A (Agent-to-Agent) communication. " +
                        "Help coordinate between YAWL workflows and external agents."
        );

        this.agentCapabilities = discoverCapabilities();
        connected = true;
        System.out.println("Successfully connected to A2A agent");
    }

    public void disconnect() {
        if (!connected) {
            System.out.println("Not connected to any agent");
            return;
        }

        System.out.println("Disconnecting from A2A agent...");
        connected = false;
        System.out.println("Disconnected");
    }

    private String discoverCapabilities() {
        throw new UnsupportedOperationException("A2A SDK integration required to discover agent capabilities. Add A2A SDK dependency.");
    }

    /**
     * Invoke a capability using natural language with AI enhancement
     */
    public String invokeWithAI(String naturalLanguageRequest, String data) {
        if (!connected) {
            throw new IllegalStateException("Not connected to agent");
        }

        System.out.println("AI-enhanced invocation: " + naturalLanguageRequest);

        String prompt = String.format(
                "Given the task: '%s'\n\n" +
                        "Available capabilities: %s\n\n" +
                        "Data to process: %s\n\n" +
                        "Determine the best capability to use and format the request appropriately. " +
                        "Return in format: CAPABILITY: [name] PARAMS: [json]",
                naturalLanguageRequest, agentCapabilities, data
        );

        String aiResponse = zaiService.chat(prompt);

        String capability = extractCapability(aiResponse);
        String params = extractParams(aiResponse, data);

        return invokeCapability(capability, params);
    }

    /**
     * Invoke a capability on the remote agent
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
     */
    public String getOrchestrationPlan(String workflowDescription, String[] availableAgents) {
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
     */
    public String handleExceptionWithAI(String exceptionDetails, String context) {
        String prompt = String.format(
                "An A2A agent interaction encountered an exception:\n\n" +
                        "Exception: %s\n\n" +
                        "Context: %s\n\n" +
                        "Available capabilities: %s\n\n" +
                        "Suggest the best recovery action or alternative capability to use.",
                exceptionDetails, context, agentCapabilities
        );

        return zaiService.chat(prompt);
    }

    /**
     * Transform data for agent compatibility using AI
     */
    public String transformForAgent(String inputData, String targetAgentCapabilities) {
        return zaiService.transformData(inputData,
                "Transform to be compatible with agent capabilities: " + targetAgentCapabilities);
    }

    public String getCapabilities() {
        return agentCapabilities;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isAIEnabled() {
        return zaiService.isInitialized();
    }

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
        return "processDocument";
    }

    private String extractParams(String aiResponse, String originalData) {
        if (aiResponse.contains("PARAMS:")) {
            int start = aiResponse.indexOf("PARAMS:") + 7;
            return aiResponse.substring(start).trim();
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

        System.out.println("\n=== Testing AI-Enhanced Invocation ===");
        String result = client.invokeWithAI(
                "Process this invoice and check for any discrepancies",
                "{\"invoiceId\": \"INV-123\", \"amount\": 1500, \"vendor\": \"Acme Corp\"}"
        );
        System.out.println(result);

        System.out.println("\n=== Testing Exception Handling ===");
        String resolution = client.handleExceptionWithAI(
                "Timeout while waiting for agent response",
                "{\"taskId\": \"task-456\", \"retryCount\": 2}"
        );
        System.out.println(resolution);

        System.out.println("\n=== Testing Orchestration Plan ===");
        String plan = client.getOrchestrationPlan(
                "Process customer order, verify inventory, and schedule delivery",
                new String[]{"OrderAgent", "InventoryAgent", "DeliveryAgent"}
        );
        System.out.println(plan);

        client.disconnect();
    }
}
