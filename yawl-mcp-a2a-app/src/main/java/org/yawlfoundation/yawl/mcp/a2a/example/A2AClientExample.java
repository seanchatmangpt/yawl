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
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentInterface;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TaskIdParams;
import io.a2a.spec.TaskQueryParams;
import io.a2a.spec.TextPart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Simple A2A client example demonstrating message construction patterns.
 *
 * <p>This example shows how to:</p>
 * <ul>
 *   <li>Create A2A messages using the A2A utility class</li>
 *   <li>Extract text from messages</li>
 *   <li>Query task status</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>For actual client-server communication, use the Client class from
 * a2a-java-sdk-client with one of the transport modules:</p>
 * <pre>{@code
 * // JSON-RPC transport
 * Client client = Client.builder(agentCard)
 *     .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
 *     .build();
 *
 * // REST transport
 * Client client = Client.builder(agentCard)
 *     .withTransport(RestTransport.class, new RestTransportConfig())
 *     .build();
 *
 * // gRPC transport
 * Client client = Client.builder(agentCard)
 *     .withTransport(GrpcTransport.class, new GrpcTransportConfig(channelFactory))
 *     .build();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class A2AClientExample {

    private static final Logger LOGGER = LoggerFactory.getLogger(A2AClientExample.class);

    private final AgentCard serverCard;

    /**
     * Creates a new A2A client example.
     *
     * @param serverCard the agent card of the server to connect to
     */
    public A2AClientExample(AgentCard serverCard) {
        this.serverCard = serverCard;
        LOGGER.info("Client initialized for agent: {} v{}", serverCard.name(), serverCard.version());
    }

    /**
     * Creates a user message with the given text.
     *
     * @param text the message text
     * @return the message
     */
    public Message createMessage(String text) {
        return A2A.toUserMessage(text);
    }

    /**
     * Creates task query params for getting task status.
     *
     * @param taskId the task ID
     * @return the task query params
     */
    public TaskQueryParams createTaskQuery(String taskId) {
        return new TaskQueryParams(taskId);
    }

    /**
     * Creates task ID params for cancellation.
     *
     * @param taskId the task ID
     * @return the task ID params
     */
    public TaskIdParams createTaskIdParams(String taskId) {
        return new TaskIdParams(taskId);
    }

    /**
     * Extracts text content from a message.
     *
     * @param message the message to extract from
     * @return the text content
     * @throws IllegalArgumentException if message has no text content
     */
    public String extractText(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        if (message.parts() == null) {
            throw new IllegalArgumentException("Message parts cannot be null");
        }
        StringBuilder sb = new StringBuilder();
        for (Part<?> part : message.parts()) {
            if (part instanceof TextPart textPart) {
                sb.append(textPart.text());
            }
        }
        return sb.toString();
    }

    /**
     * Gets the server's agent card.
     *
     * @return the agent card
     */
    public AgentCard getServerCard() {
        return serverCard;
    }

    /**
     * Main method demonstrating client patterns.
     */
    public static void main(String[] args) {
        System.out.println("\n=== A2A Client Example ===");
        System.out.println("This example shows how to create A2A messages and work with the SDK.");
        System.out.println();

        // Create a sample agent card (normally obtained from server)
        AgentCard serverCard = AgentCard.builder()
                .name("yawl-workflow-agent")
                .description("YAWL workflow management agent")
                .version("6.0.0-Beta")
                .skills(List.of(
                        io.a2a.spec.AgentSkill.builder()
                                .id("launch-case")
                                .name("Launch Case")
                                .description("Launch a new workflow case")
                                .tags(List.of("workflow", "launch"))
                                .build()
                ))
                .supportedInterfaces(List.of(
                        new AgentInterface("a2a-rest", "http://localhost:8082/a2a")
                ))
                .build();

        // Create client example
        A2AClientExample client = new A2AClientExample(serverCard);

        // Show agent info
        System.out.println("Connected to Agent:");
        System.out.println("  Name: " + serverCard.name());
        System.out.println("  Version: " + serverCard.version());
        System.out.println("  Description: " + serverCard.description());
        System.out.println("  Skills:");
        serverCard.skills().forEach(skill ->
            System.out.println("    - " + skill.id() + ": " + skill.description())
        );

        // Demonstrate message creation
        System.out.println("\n--- Message Creation ---");
        Message msg = client.createMessage("list specifications");
        System.out.println("Created message with text: " + client.extractText(msg));

        // Demonstrate task query creation
        System.out.println("\n--- Task Query ---");
        TaskQueryParams query = client.createTaskQuery("task-123");
        System.out.println("Task query for ID: " + query.id());

        System.out.println("\n--- Integration Guide ---");
        System.out.println("To connect to a real A2A server, add transport dependencies:");
        System.out.println();
        System.out.println("JSON-RPC transport:");
        System.out.println("  <dependency>");
        System.out.println("    <groupId>io.github.a2asdk</groupId>");
        System.out.println("    <artifactId>a2a-java-sdk-client-transport-jsonrpc</artifactId>");
        System.out.println("    <version>1.0.0.Alpha2</version>");
        System.out.println("  </dependency>");
        System.out.println();
        System.out.println("REST transport:");
        System.out.println("  <dependency>");
        System.out.println("    <groupId>io.github.a2asdk</groupId>");
        System.out.println("    <artifactId>a2a-java-sdk-client-transport-rest</artifactId>");
        System.out.println("    <version>1.0.0.Alpha2</version>");
        System.out.println("  </dependency>");
        System.out.println();
        System.out.println("gRPC transport:");
        System.out.println("  <dependency>");
        System.out.println("    <groupId>io.github.a2asdk</groupId>");
        System.out.println("    <artifactId>a2a-java-sdk-client-transport-grpc</artifactId>");
        System.out.println("    <version>1.0.0.Alpha2</version>");
        System.out.println("  </dependency>");
        System.out.println();
        System.out.println("Then use Client.builder() to create a client with your chosen transport.");
    }
}
