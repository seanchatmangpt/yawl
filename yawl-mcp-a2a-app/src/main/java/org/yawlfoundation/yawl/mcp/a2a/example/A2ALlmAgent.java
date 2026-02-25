/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.example;

import io.a2a.A2A;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.tasks.AgentEmitter;
import io.a2a.spec.A2AError;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentInterface;
import io.a2a.spec.AgentProvider;
import io.a2a.spec.AgentSkill;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TaskNotCancelableError;
import io.a2a.spec.TaskState;
import io.a2a.spec.TextPart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

import java.util.List;

/**
 * LLM-powered A2A agent using Z.AI for intelligent responses.
 *
 * <p>This example demonstrates how to integrate Z.AI (Zhipu) LLM capabilities
 * into an A2A agent for intelligent message processing.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Natural language understanding via Z.AI GLM-4.7-Flash model</li>
 *   <li>Context-aware conversation history</li>
 *   <li>Workflow-specific capabilities (analyze, decide, transform)</li>
 *   <li>Streaming support ready</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>Set the following environment variable:</p>
 * <pre>{@code
 * export ZAI_API_KEY="your-zhipu-api-key"
 * }</pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create LLM agent
 * A2ALlmAgent agent = new A2ALlmAgent(8082, "/a2a");
 *
 * // Get the agent card
 * AgentCard card = agent.createAgentCard();
 *
 * // Get the executor for Spring Boot integration
 * AgentExecutor executor = agent.createExecutor();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class A2ALlmAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(A2ALlmAgent.class);

    private final int port;
    private final String path;
    private final String systemPrompt;

    /**
     * Creates a new LLM-powered A2A agent.
     *
     * @param port the server port
     * @param path the A2A endpoint path
     */
    public A2ALlmAgent(int port, String path) {
        this(port, path, null);
    }

    /**
     * Creates a new LLM-powered A2A agent with custom system prompt.
     *
     * @param port the server port
     * @param path the A2A endpoint path
     * @param systemPrompt custom system prompt for the LLM
     */
    public A2ALlmAgent(int port, String path, String systemPrompt) {
        this.port = port;
        this.path = path;
        this.systemPrompt = systemPrompt != null ? systemPrompt : getDefaultSystemPrompt();
    }

    /**
     * Returns the default system prompt for the YAWL workflow agent.
     */
    private String getDefaultSystemPrompt() {
        return """
            You are an intelligent YAWL workflow assistant. Your role is to help users:
            - Launch and manage workflow cases
            - Query workflow status and work items
            - Make intelligent decisions about workflow routing
            - Transform and analyze workflow data
            - Generate workflow documentation

            Be concise and helpful. When asked to perform actions, confirm what you will do
            and explain the results clearly.

            Available commands you can help with:
            - list specifications: Show available workflow definitions
            - launch <spec>: Start a new workflow case
            - status <case>: Check case progress
            - cancel <case>: Cancel a running case
            - work items: Show active work items

            If you need to perform actual operations, guide the user through the process.
            """;
    }

    /**
     * Creates an AgentCard for the LLM-powered agent.
     *
     * @return the agent card
     */
    public AgentCard createAgentCard() {
        return AgentCard.builder()
                .name("yawl-llm-agent")
                .description("Intelligent YAWL workflow agent powered by Z.AI GLM-4.7")
                .version("6.0.0-Beta")
                .provider(new AgentProvider("YAWL Foundation", "https://yawlfoundation.github.io"))
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .pushNotifications(false)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(
                        AgentSkill.builder()
                                .id("chat")
                                .name("Natural Language Chat")
                                .description("Converse naturally about workflows and get intelligent assistance")
                                .tags(List.of("chat", "ai", "assistant"))
                                .inputModes(List.of("text"))
                                .outputModes(List.of("text"))
                                .build(),
                        AgentSkill.builder()
                                .id("analyze")
                                .name("Workflow Analysis")
                                .description("Analyze workflow context and suggest optimal actions")
                                .tags(List.of("analyze", "workflow", "recommend"))
                                .inputModes(List.of("text"))
                                .outputModes(List.of("text"))
                                .build(),
                        AgentSkill.builder()
                                .id("decide")
                                .name("Intelligent Decision")
                                .description("Make workflow routing decisions based on data and context")
                                .tags(List.of("decision", "routing", "ai"))
                                .inputModes(List.of("text"))
                                .outputModes(List.of("text"))
                                .build(),
                        AgentSkill.builder()
                                .id("transform")
                                .name("Data Transformation")
                                .description("Transform workflow data using natural language rules")
                                .tags(List.of("transform", "data", "convert"))
                                .inputModes(List.of("text"))
                                .outputModes(List.of("text"))
                                .build()
                ))
                .supportedInterfaces(List.of(
                        new AgentInterface("a2a-rest", "http://localhost:" + port + path)
                ))
                .build();
    }

    /**
     * Creates the LLM-powered agent executor.
     *
     * @return a new agent executor
     */
    public AgentExecutor createExecutor() {
        return new LlmAgentExecutor(systemPrompt);
    }

    /**
     * LLM-powered AgentExecutor implementation using Z.AI.
     */
    public static class LlmAgentExecutor implements AgentExecutor {

        private static final Logger LOGGER = LoggerFactory.getLogger(LlmAgentExecutor.class);

        private final ZaiService zaiService;
        private final String systemPrompt;

        /**
         * Creates a new LLM agent executor.
         *
         * @param systemPrompt the system prompt for the LLM
         * @throws IllegalStateException if ZAI_API_KEY is not set
         */
        public LlmAgentExecutor(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            this.zaiService = createZaiService();
            this.zaiService.setSystemPrompt(systemPrompt);
            LOGGER.info("LLM Agent Executor initialized with Z.AI");
        }

        /**
         * Creates the Z.AI service. Throws if API key is not configured.
         *
         * @throws IllegalStateException if ZAI_API_KEY is not set
         */
        private ZaiService createZaiService() {
            String apiKey = System.getenv("ZAI_API_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalStateException(
                    "ZAI_API_KEY environment variable is required. " +
                    "Set it in your shell: export ZAI_API_KEY=\"your-zhipu-api-key\" " +
                    "Get a key from: https://open.bigmodel.cn/"
                );
            }
            return new ZaiService(apiKey);
        }

        @Override
        public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
            // Mark task as submitted if new
            if (context.getTask() == null) {
                emitter.submit();
            }
            emitter.startWork();

            try {
                // Extract text from message
                String userText = extractTextFromMessage(context.getMessage());
                LOGGER.info("LLM Agent received: {}", userText);

                // Process with LLM
                String response = processWithLlm(userText);

                // Complete with response
                emitter.complete(A2A.toAgentMessage(response));

                LOGGER.info("LLM Agent completed successfully");

            } catch (Exception e) {
                LOGGER.error("LLM Agent error: {}", e.getMessage(), e);
                emitter.fail(A2A.toAgentMessage("Error: " + e.getMessage()));
            }
        }

        @Override
        public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
            Task task = context.getTask();

            if (task == null) {
                throw new TaskNotCancelableError();
            }

            TaskState state = task.status().state();
            if (state == TaskState.CANCELED || state == TaskState.COMPLETED) {
                throw new TaskNotCancelableError();
            }

            LOGGER.info("Cancelling task: {}", task.id());
            emitter.cancel(A2A.toAgentMessage("Task cancelled by user request"));
        }

        /**
         * Processes the message using Z.AI LLM.
         *
         * @param text the user message
         * @return the LLM response
         */
        private String processWithLlm(String text) {
            String lower = text.toLowerCase().trim();

            try {
                // Route to specialized handlers based on intent
                if (lower.contains("analyze") || lower.contains("recommend")) {
                    return handleAnalysis(text);
                }

                if (lower.contains("decide") || lower.contains("choose") || lower.contains("option")) {
                    return handleDecision(text);
                }

                if (lower.contains("transform") || lower.contains("convert")) {
                    return handleTransformation(text);
                }

                // Default: chat with the LLM
                return zaiService.chat(text);

            } catch (Exception e) {
                LOGGER.error("LLM processing error: {}", e.getMessage(), e);
                return "I encountered an error processing your request: " + e.getMessage();
            }
        }

        /**
         * Handles workflow analysis requests.
         */
        private String handleAnalysis(String text) {
            // Extract context from the message
            String context = text.replaceFirst("(?i)analyze\\s*", "")
                                  .replaceFirst("(?i)recommend\\s*", "");

            return zaiService.analyzeWorkflowContext(
                    "current-session",
                    "user-request",
                    context
            );
        }

        /**
         * Handles decision-making requests.
         */
        private String handleDecision(String text) {
            // Default options for workflow decisions
            List<String> defaultOptions = List.of(
                    "Approve and continue",
                    "Request more information",
                    "Escalate to manager",
                    "Reject and terminate"
            );

            return zaiService.makeWorkflowDecision(
                    "User Request",
                    text,
                    defaultOptions
            );
        }

        /**
         * Handles data transformation requests.
         */
        private String handleTransformation(String text) {
            // Parse transformation request
            String[] parts = text.split("(?i)to|into|as", 2);
            String inputData = parts[0].replaceFirst("(?i)transform\\s*|convert\\s*", "").trim();
            String targetFormat = parts.length > 1 ? parts[1].trim() : "structured format";

            return zaiService.transformData(inputData, "Convert to " + targetFormat);
        }

        /**
         * Extracts text content from an A2A message.
         *
         * @param message the message to extract from
         * @return the text content
         * @throws IllegalArgumentException if message has no text content
         */
        private String extractTextFromMessage(Message message) {
            if (message == null) {
                throw new IllegalArgumentException("Message cannot be null");
            }
            if (message.parts() == null) {
                throw new IllegalArgumentException("Message has no parts");
            }

            StringBuilder textBuilder = new StringBuilder();
            for (Part<?> part : message.parts()) {
                if (part instanceof TextPart textPart) {
                    textBuilder.append(textPart.text());
                }
            }

            if (textBuilder.length() == 0) {
                throw new IllegalArgumentException("Message contains no text parts");
            }

            return textBuilder.toString();
        }
    }

    /**
     * Main method demonstrating LLM agent setup.
     *
     * @throws IllegalStateException if ZAI_API_KEY is not configured
     */
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8082;
        String path = args.length > 1 ? args[1] : "/a2a";

        A2ALlmAgent agent = new A2ALlmAgent(port, path);

        System.out.println("\n=== A2A LLM Agent Example ===");
        System.out.println("Intelligent YAWL workflow agent powered by Z.AI");
        System.out.println();

        // Create agent card
        AgentCard card = agent.createAgentCard();
        System.out.println("Agent Card:");
        System.out.println("  Name: " + card.name());
        System.out.println("  Version: " + card.version());
        System.out.println("  Description: " + card.description());
        System.out.println("  Streaming: " + card.capabilities().streaming());
        System.out.println("  Skills:");
        card.skills().forEach(skill ->
            System.out.println("    - " + skill.id() + ": " + skill.description())
        );

        // Create executor - this will throw if ZAI_API_KEY is not set
        AgentExecutor executor = agent.createExecutor();
        System.out.println("\nAgent Executor: " + executor.getClass().getSimpleName());
        System.out.println("Z.AI Service: Connected");

        System.out.println("\n--- Spring Boot Integration ---");
        System.out.println("@Bean");
        System.out.println("public AgentCard agentCard() {");
        System.out.println("    return new A2ALlmAgent(8082, \"/a2a\").createAgentCard();");
        System.out.println("}");
        System.out.println();
        System.out.println("@Bean");
        System.out.println("public AgentExecutor agentExecutor() {");
        System.out.println("    return new A2ALlmAgent.LlmAgentExecutor(null);");
        System.out.println("}");
    }
}
