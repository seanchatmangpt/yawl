package org.yawlfoundation.yawl.integration.a2a;

import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.integration.zai.ZaiFunctionService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A2A Client Integration for YAWL with Z.AI Intelligence
 *
 * Connects YAWL to other agents using the A2A protocol with AI capabilities.
 * Implements real A2A protocol communication via HTTP.
 *
 * Features:
 * - Fetch AgentCard from /.well-known/agent.json
 * - Send messages via JSON-RPC 2.0
 * - Invoke skills with real HTTP calls
 * - AI-enhanced request processing
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see <a href="https://a2a-protocol.org">A2A Protocol Specification</a>
 */
public class YawlA2AClient {

    private static final long DEFAULT_TIMEOUT_MS = 60000;

    private final String agentUrl;
    private final A2AHttpClient a2aClient;
    private final ZaiService zaiService;
    private final ZaiFunctionService functionService;
    private A2ATypes.AgentCard agentCard;
    private boolean connected = false;
    private long timeoutMs = DEFAULT_TIMEOUT_MS;

    /**
     * Create A2A client for an agent
     *
     * @param agentUrl the base URL of the remote agent
     */
    public YawlA2AClient(String agentUrl) {
        if (agentUrl == null || agentUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent URL is required");
        }
        this.agentUrl = agentUrl;
        this.a2aClient = new A2AHttpClient(agentUrl);
        this.zaiService = createZaiService();
        this.functionService = null;
        System.out.println("Initializing YAWL A2A Client for agent at: " + agentUrl);
    }

    /**
     * Create A2A client with Z.AI API key
     *
     * @param agentUrl the base URL of the remote agent
     * @param zaiApiKey the Z.AI API key for AI-enhanced features
     */
    public YawlA2AClient(String agentUrl, String zaiApiKey) {
        if (agentUrl == null || agentUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent URL is required");
        }
        this.agentUrl = agentUrl;
        this.a2aClient = new A2AHttpClient(agentUrl);
        this.zaiService = zaiApiKey != null ? new ZaiService(zaiApiKey) : createZaiService();
        this.functionService = null;
        System.out.println("Initializing YAWL A2A Client with Z.AI for agent at: " + agentUrl);
    }

    private ZaiService createZaiService() {
        try {
            return new ZaiService();
        } catch (IllegalStateException e) {
            // ZAI_API_KEY not set - AI features disabled
            return null;
        }
    }

    /**
     * Connect to the remote agent
     *
     * Fetches the AgentCard and validates protocol compatibility.
     *
     * @throws A2AException if connection fails
     */
    public void connect() throws A2AException {
        if (connected) {
            System.out.println("Already connected to agent");
            return;
        }

        System.out.println("Connecting to A2A agent at: " + agentUrl);

        // Fetch the real AgentCard from the agent
        this.agentCard = a2aClient.fetchAgentCard();

        if (zaiService != null) {
            zaiService.setSystemPrompt(
                    "You are an intelligent assistant for YAWL A2A (Agent-to-Agent) communication. " +
                            "Help coordinate between YAWL workflows and external agents. " +
                            "Available agent capabilities: " + getCapabilitiesDescription()
            );
        }

        connected = true;
        System.out.println("Successfully connected to A2A agent: " + agentCard.getName());
        System.out.println("Protocol version: " + agentCard.getProtocolVersion());
        System.out.println("Available skills: " + agentCard.getSkills().size());
    }

    /**
     * Disconnect from the agent
     */
    public void disconnect() {
        if (!connected) {
            System.out.println("Not connected to any agent");
            return;
        }

        System.out.println("Disconnecting from A2A agent...");
        a2aClient.clearCache();
        agentCard = null;
        connected = false;
        System.out.println("Disconnected");
    }

    /**
     * Discover agent capabilities
     *
     * Returns the real capabilities from the AgentCard.
     *
     * @return comma-separated list of skill IDs
     * @throws A2AException if not connected
     */
    public String discoverCapabilities() throws A2AException {
        if (!connected || agentCard == null) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Not connected to agent",
                "Call connect() first to establish connection."
            );
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < agentCard.getSkills().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(agentCard.getSkills().get(i).getId());
        }
        return sb.toString();
    }

    /**
     * Get the full AgentCard
     *
     * @return the agent's capability card
     * @throws A2AException if not connected
     */
    public A2ATypes.AgentCard getAgentCard() throws A2AException {
        if (!connected || agentCard == null) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Not connected to agent",
                "Call connect() first to establish connection."
            );
        }
        return agentCard;
    }

    /**
     * Invoke a capability using natural language with AI enhancement
     *
     * Uses Z.AI to determine the best skill and format parameters.
     *
     * @param naturalLanguageRequest the request in natural language
     * @param data the data to process
     * @return the result from the agent
     * @throws A2AException if invocation fails
     */
    public String invokeWithAI(String naturalLanguageRequest, String data) throws A2AException {
        if (!connected) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Not connected to agent",
                "Call connect() first to establish connection."
            );
        }

        if (zaiService == null) {
            throw new A2AException(
                A2AException.ErrorCode.TASK_EXECUTION_FAILED,
                "AI enhancement requires ZAI_API_KEY environment variable",
                "Set ZAI_API_KEY or use invokeCapability() directly with the skill ID."
            );
        }

        System.out.println("AI-enhanced invocation: " + naturalLanguageRequest);

        String prompt = String.format(
                "Given the task: '%s'\n\n" +
                        "Available capabilities: %s\n\n" +
                        "Data to process: %s\n\n" +
                        "Determine the best capability to use and format the request appropriately. " +
                        "Return in format: CAPABILITY: [name] PARAMS: [json]",
                naturalLanguageRequest, getCapabilities(), data
        );

        String aiResponse = zaiService.chat(prompt);

        String capability = extractCapability(aiResponse);
        String params = extractParams(aiResponse, data);

        return invokeCapability(capability, params);
    }

    /**
     * Invoke a capability on the remote agent
     *
     * Makes a real HTTP call to the A2A endpoint.
     *
     * @param capabilityName the skill ID to invoke
     * @param data the data to send
     * @return the result from the agent
     * @throws A2AException if invocation fails
     */
    public String invokeCapability(String capabilityName, String data) throws A2AException {
        if (!connected) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Not connected to agent",
                "Call connect() first to establish connection."
            );
        }

        System.out.println("Invoking capability: " + capabilityName);

        // Verify the skill exists
        A2ATypes.AgentSkill skill = agentCard.getSkillById(capabilityName);
        if (skill == null) {
            List<String> availableSkills = new ArrayList<>();
            for (A2ATypes.AgentSkill s : agentCard.getSkills()) {
                availableSkills.add(s.getId());
            }
            throw A2AException.skillNotFound(capabilityName, availableSkills);
        }

        // Make the real HTTP call to invoke the skill
        String result = a2aClient.invokeSkill(capabilityName, data, timeoutMs);

        System.out.println("Capability '" + capabilityName + "' completed successfully");
        return result;
    }

    /**
     * Send a message to the agent
     *
     * @param message the message to send
     * @return the created task
     * @throws A2AException if sending fails
     */
    public A2ATypes.Task sendMessage(A2ATypes.Message message) throws A2AException {
        if (!connected) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Not connected to agent",
                "Call connect() first to establish connection."
            );
        }

        return a2aClient.sendMessage(message);
    }

    /**
     * Send a simple text message
     *
     * @param text the message text
     * @return the created task
     * @throws A2AException if sending fails
     */
    public A2ATypes.Task sendTextMessage(String text) throws A2AException {
        return sendMessage(A2ATypes.Message.userMessage(text));
    }

    /**
     * Get task status
     *
     * @param taskId the task ID
     * @return the current task state
     * @throws A2AException if retrieval fails
     */
    public A2ATypes.Task getTask(String taskId) throws A2AException {
        return a2aClient.getTask(taskId);
    }

    /**
     * Wait for task completion
     *
     * @param taskId the task ID
     * @return the completed task
     * @throws A2AException if waiting fails or times out
     */
    public A2ATypes.Task waitForCompletion(String taskId) throws A2AException {
        return a2aClient.waitForCompletion(taskId, timeoutMs);
    }

    /**
     * Cancel a task
     *
     * @param taskId the task ID to cancel
     * @return the canceled task
     * @throws A2AException if cancellation fails
     */
    public A2ATypes.Task cancelTask(String taskId) throws A2AException {
        return a2aClient.cancelTask(taskId);
    }

    /**
     * Get AI-powered orchestration plan for multi-step workflow
     *
     * @param workflowDescription the workflow description
     * @param availableAgents list of available agent URLs
     * @return the orchestration plan
     */
    public String getOrchestrationPlan(String workflowDescription, String[] availableAgents) {
        if (zaiService == null) {
            return "AI orchestration requires ZAI_API_KEY environment variable";
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
     *
     * @param exceptionDetails the exception details
     * @param context the context
     * @return suggested recovery action
     */
    public String handleExceptionWithAI(String exceptionDetails, String context) {
        if (zaiService == null) {
            return "AI exception handling requires ZAI_API_KEY environment variable";
        }

        String prompt = String.format(
                "An A2A agent interaction encountered an exception:\n\n" +
                        "Exception: %s\n\n" +
                        "Context: %s\n\n" +
                        "Available capabilities: %s\n\n" +
                        "Suggest the best recovery action or alternative capability to use.",
                exceptionDetails, context, getCapabilities()
        );

        return zaiService.chat(prompt);
    }

    /**
     * Transform data for agent compatibility using AI
     *
     * @param inputData the input data
     * @param targetAgentCapabilities the target capabilities
     * @return transformed data
     */
    public String transformForAgent(String inputData, String targetAgentCapabilities) {
        if (zaiService == null) {
            return inputData;
        }
        return zaiService.transformData(inputData,
                "Transform to be compatible with agent capabilities: " + targetAgentCapabilities);
    }

    /**
     * Get available capabilities
     *
     * @return comma-separated list of skill IDs
     */
    public String getCapabilities() {
        if (agentCard == null) {
            return "";
        }
        try {
            return discoverCapabilities();
        } catch (A2AException e) {
            return "";
        }
    }

    /**
     * Check if connected
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Check if AI features are enabled
     *
     * @return true if Z.AI is configured
     */
    public boolean isAIEnabled() {
        return zaiService != null && zaiService.isInitialized();
    }

    /**
     * Set operation timeout
     *
     * @param timeoutMs timeout in milliseconds
     */
    public void setTimeout(long timeoutMs) {
        this.timeoutMs = timeoutMs;
        a2aClient.setConnectTimeout((int) Math.min(timeoutMs, Integer.MAX_VALUE));
        a2aClient.setReadTimeout((int) Math.min(timeoutMs, Integer.MAX_VALUE));
    }

    /**
     * Get the underlying A2A HTTP client
     *
     * @return the A2A HTTP client
     */
    public A2AHttpClient getA2AClient() {
        return a2aClient;
    }

    private String getCapabilitiesDescription() {
        if (agentCard == null) {
            return "Not connected";
        }

        StringBuilder sb = new StringBuilder();
        for (A2ATypes.AgentSkill skill : agentCard.getSkills()) {
            sb.append("- ").append(skill.getId()).append(": ").append(skill.getName()).append("\n");
        }
        return sb.toString();
    }

    private String extractCapability(String aiResponse) {
        if (agentCard == null) {
            return "";
        }

        String lower = aiResponse.toLowerCase();

        // Check each skill
        for (A2ATypes.AgentSkill skill : agentCard.getSkills()) {
            String skillId = skill.getId().toLowerCase();
            String skillName = skill.getName().toLowerCase();

            if (lower.contains(skillId) || lower.contains(skillName)) {
                return skill.getId();
            }
        }

        // Fallback to first skill if no match
        if (!agentCard.getSkills().isEmpty()) {
            return agentCard.getSkills().get(0).getId();
        }

        return "";
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
        String agentUrl = args.length > 0 ? args[0] : "http://localhost:8082";

        System.out.println("YAWL A2A Client Test");
        System.out.println("====================");
        System.out.println("Agent URL: " + agentUrl);
        System.out.println();

        YawlA2AClient client = new YawlA2AClient(agentUrl);

        // Test connection
        System.out.println("Testing connection...");
        try {
            client.connect();

            System.out.println("\nAgent Information:");
            A2ATypes.AgentCard card = client.getAgentCard();
            System.out.println("  Name: " + card.getName());
            System.out.println("  Description: " + card.getDescription());
            System.out.println("  URL: " + card.getUrl());
            System.out.println("  Version: " + card.getVersion());
            System.out.println("  Protocol: " + card.getProtocolVersion());

            System.out.println("\nAvailable capabilities: " + client.getCapabilities());

            System.out.println("\nSkills:");
            for (A2ATypes.AgentSkill skill : card.getSkills()) {
                System.out.println("  - " + skill.getId() + ": " + skill.getName());
                System.out.println("    " + skill.getDescription());
            }

            // Test skill invocation
            System.out.println("\n=== Testing Skill Invocation ===");
            try {
                String result = client.invokeCapability(
                    "getSpecifications",
                    "{}"
                );
                System.out.println("Result: " + result);
            } catch (A2AException e) {
                System.out.println("Skill invocation: " + e.getMessage());
            }

            // Test AI-enhanced invocation if available
            if (client.isAIEnabled()) {
                System.out.println("\n=== Testing AI-Enhanced Invocation ===");
                try {
                    String result = client.invokeWithAI(
                        "Get available workflows",
                        "{}"
                    );
                    System.out.println("Result: " + result);
                } catch (A2AException e) {
                    System.out.println("AI invocation: " + e.getMessage());
                }
            } else {
                System.out.println("\n=== AI Features Disabled ===");
                System.out.println("Set ZAI_API_KEY environment variable to enable AI features.");
            }

            client.disconnect();

        } catch (A2AException e) {
            System.err.println("Connection failed: " + e.getFullReport());
            System.err.println("\nMake sure the A2A server is running at " + agentUrl);
        }
    }
}
