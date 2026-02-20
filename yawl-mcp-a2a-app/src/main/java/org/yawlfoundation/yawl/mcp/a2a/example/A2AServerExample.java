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

import java.util.List;

/**
 * Simple A2A server example demonstrating the AgentExecutor pattern.
 *
 * <p>This example shows how to:</p>
 * <ul>
 *   <li>Create an AgentCard defining agent capabilities</li>
 *   <li>Implement the AgentExecutor interface</li>
 *   <li>Handle incoming messages and produce responses</li>
 *   <li>Support task cancellation</li>
 * </ul>
 *
 * <h2>Integration with Spring Boot</h2>
 * <p>In a Spring Boot application, you would:</p>
 * <pre>{@code
 * @Component
 * public class MyAgentExecutor implements AgentExecutor {
 *     @Override
 *     public void execute(RequestContext context, AgentEmitter emitter) {
 *         // Handle the message
 *     }
 *
 *     @Override
 *     public void cancel(RequestContext context, AgentEmitter emitter) {
 *         // Handle cancellation
 *     }
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class A2AServerExample {

    private static final Logger LOGGER = LoggerFactory.getLogger(A2AServerExample.class);

    private final int port;
    private final String path;

    /**
     * Creates a new A2A server example.
     *
     * @param port the server port
     * @param path the A2A endpoint path
     */
    public A2AServerExample(int port, String path) {
        this.port = port;
        this.path = path;
    }

    /**
     * Creates an AgentCard for a simple echo agent.
     *
     * @return the agent card
     */
    public AgentCard createAgentCard() {
        return AgentCard.builder()
                .name("echo-agent")
                .description("Simple echo agent that responds to messages")
                .version("1.0.0")
                .provider(new AgentProvider("YAWL Foundation", "https://yawlfoundation.github.io"))
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(
                        AgentSkill.builder()
                                .id("echo")
                                .name("Echo")
                                .description("Echoes back the received message")
                                .tags(List.of("echo", "test"))
                                .inputModes(List.of("text"))
                                .outputModes(List.of("text"))
                                .build(),
                        AgentSkill.builder()
                                .id("reverse")
                                .name("Reverse")
                                .description("Reverses the received message")
                                .tags(List.of("reverse", "test"))
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
     * Creates the agent executor for this server.
     *
     * @return a new agent executor
     */
    public AgentExecutor createExecutor() {
        return new EchoAgentExecutor();
    }

    /**
     * Simple AgentExecutor implementation that echoes or reverses messages.
     */
    public static class EchoAgentExecutor implements AgentExecutor {

        private static final Logger LOGGER = LoggerFactory.getLogger(EchoAgentExecutor.class);

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
                LOGGER.info("Received message: {}", userText);

                // Process the message
                String response = processMessage(userText);

                // Complete with artifact
                emitter.complete(A2A.toAgentMessage(response));

                LOGGER.info("Task completed successfully");

            } catch (Exception e) {
                LOGGER.error("Error processing message: {}", e.getMessage(), e);
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
         * Processes the incoming message and returns a response.
         */
        private String processMessage(String text) {
            String lower = text.toLowerCase().trim();

            if (lower.startsWith("reverse:")) {
                String toReverse = text.substring(8).trim();
                return new StringBuilder(toReverse).reverse().toString();
            }

            if (lower.startsWith("echo:")) {
                return text.substring(5).trim();
            }

            if (lower.contains("hello") || lower.contains("hi")) {
                return "Hello! I'm the echo agent. Send me 'echo: <text>' or 'reverse: <text>' to test.";
            }

            if (lower.contains("help")) {
                return """
                    Echo Agent Commands:
                    - echo: <text>  - Echo back the text
                    - reverse: <text> - Reverse the text
                    - help - Show this help message
                    """;
            }

            // Default: echo the message
            return "Echo: " + text;
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
     * Main method demonstrating server setup.
     */
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8082;
        String path = args.length > 1 ? args[1] : "/a2a";

        A2AServerExample server = new A2AServerExample(port, path);

        System.out.println("\n=== A2A Server Example ===");
        System.out.println("This example shows how to create an A2A server.");
        System.out.println();

        // Create agent card
        AgentCard card = server.createAgentCard();
        System.out.println("Agent Card:");
        System.out.println("  Name: " + card.name());
        System.out.println("  Version: " + card.version());
        System.out.println("  Description: " + card.description());
        System.out.println("  Skills:");
        card.skills().forEach(skill ->
            System.out.println("    - " + skill.id() + ": " + skill.description())
        );

        // Create executor
        AgentExecutor executor = server.createExecutor();
        System.out.println("\nAgent Executor: " + executor.getClass().getSimpleName());

        System.out.println("\n--- Integration Guide ---");
        System.out.println("To use this in a Spring Boot application:");
        System.out.println();
        System.out.println("1. Add dependency:");
        System.out.println("   <dependency>");
        System.out.println("     <groupId>io.github.a2asdk</groupId>");
        System.out.println("     <artifactId>a2a-java-sdk-reference-jsonrpc</artifactId>");
        System.out.println("     <version>1.0.0.Alpha2</version>");
        System.out.println("   </dependency>");
        System.out.println();
        System.out.println("2. Create AgentCard bean:");
        System.out.println("   @Bean");
        System.out.println("   public AgentCard agentCard() {");
        System.out.println("       return new A2AServerExample(8082, \"/a2a\").createAgentCard();");
        System.out.println("   }");
        System.out.println();
        System.out.println("3. Create AgentExecutor bean:");
        System.out.println("   @Bean");
        System.out.println("   public AgentExecutor agentExecutor() {");
        System.out.println("       return new EchoAgentExecutor();");
        System.out.println("   }");
        System.out.println();
        System.out.println("4. Run with: mvn spring-boot:run");
        System.out.println();
        System.out.println("Or use the YawlA2AAgentCard and YawlA2AExecutor in yawl-mcp-a2a-app");
        System.out.println("for full YAWL workflow integration.");
    }
}
