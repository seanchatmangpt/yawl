package org.yawlfoundation.yawl.integration.a2a;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.a2a.A2A;
import io.a2a.client.Client;
import io.a2a.client.MessageEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.transport.rest.RestTransport;
import io.a2a.client.transport.rest.RestTransportConfig;
import io.a2a.spec.*;

/**
 * Agent-to-Agent (A2A) Client for YAWL using the official A2A Java SDK.
 *
 * Connects to remote A2A agents to invoke their capabilities from within
 * YAWL workflows. Supports agent card discovery, message sending, task
 * management, and streaming events.
 *
 * Uses the official A2A Java SDK with REST transport.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlA2AClient implements AutoCloseable {

    private final String agentUrl;
    private AgentCard agentCard;
    private Client a2aClient;
    private boolean connected;

    /**
     * Construct a YAWL A2A Client for a remote agent.
     *
     * @param agentUrl base URL of the A2A agent (e.g. http://localhost:8081)
     */
    public YawlA2AClient(String agentUrl) {
        if (agentUrl == null || agentUrl.isEmpty()) {
            throw new IllegalArgumentException("Agent URL is required");
        }
        this.agentUrl = agentUrl;
        this.connected = false;
    }

    /**
     * Connect to the remote A2A agent by fetching its agent card.
     *
     * @throws A2AClientException if the agent card cannot be fetched
     * @throws A2AClientError if there is a protocol-level error
     * @throws A2AClientJSONError if JSON deserialization fails
     */
    public void connect() throws A2AClientException, A2AClientError, A2AClientJSONError {
        if (connected) {
            throw new IllegalStateException("Already connected to agent at " + agentUrl);
        }

        agentCard = A2A.getAgentCard(agentUrl);

        a2aClient = Client.builder(agentCard)
            .withTransport(RestTransport.class, new RestTransportConfig())
            .build();

        connected = true;
        System.out.println("Connected to A2A agent: " + agentCard.name()
            + " v" + agentCard.version());
    }

    /**
     * Send a text message to the remote agent and wait for a response.
     *
     * @param text the text message to send
     * @return the agent's text response
     * @throws A2AClientException if the message cannot be sent
     */
    public String sendMessage(String text) throws A2AClientException {
        ensureConnected();

        Message userMessage = A2A.toUserMessage(text);

        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        a2aClient.sendMessage(userMessage,
            List.of((event, card) -> {
                if (event instanceof TaskEvent taskEvent) {
                    Task task = taskEvent.getTask();
                    if (task.status() != null && task.status().state().isFinal()) {
                        if (task.status().message() != null) {
                            result.set(extractTextFromMessage(task.status().message()));
                        }
                        latch.countDown();
                    }
                } else if (event instanceof MessageEvent msgEvent) {
                    Message msg = msgEvent.getMessage();
                    result.set(extractTextFromMessage(msg));
                    latch.countDown();
                }
            }),
            err -> {
                error.set(err);
                latch.countDown();
            },
            null
        );

        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                throw new RuntimeException(
                    "Timeout waiting for agent response after 60 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for agent response", e);
        }

        Throwable err = error.get();
        if (err != null) {
            throw new RuntimeException("Agent communication error: " + err.getMessage(), err);
        }

        String response = result.get();
        if (response == null) {
            throw new RuntimeException("Agent returned no response content");
        }
        return response;
    }

    /**
     * Get a task by its ID from the remote agent.
     *
     * @param taskId the task ID to query
     * @return the task details
     * @throws A2AClientException if the task cannot be fetched
     */
    public Task getTask(String taskId) throws A2AClientException {
        ensureConnected();
        TaskQueryParams params = new TaskQueryParams(taskId);
        return a2aClient.getTask(params, null);
    }

    /**
     * Cancel a task on the remote agent.
     *
     * @param taskId the task ID to cancel
     * @return the cancelled task
     * @throws A2AClientException if the task cannot be cancelled
     */
    public Task cancelTask(String taskId) throws A2AClientException {
        ensureConnected();
        TaskIdParams params = new TaskIdParams(taskId);
        return a2aClient.cancelTask(params, null);
    }

    /**
     * Get the agent card of the connected agent.
     *
     * @return the agent card describing the agent's capabilities
     */
    public AgentCard getAgentCard() {
        ensureConnected();
        return agentCard;
    }

    /**
     * Get the agent's skills/capabilities.
     *
     * @return list of agent skills
     */
    public List<AgentSkill> getSkills() {
        ensureConnected();
        return agentCard.skills();
    }

    /**
     * Get the agent's capabilities (streaming, push notifications, etc.).
     *
     * @return the agent capabilities
     */
    public AgentCapabilities getCapabilities() {
        ensureConnected();
        return agentCard.capabilities();
    }

    /**
     * Check if connected to an agent.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Disconnect from the remote agent and release resources.
     */
    @Override
    public void close() {
        if (a2aClient != null) {
            a2aClient.close();
            a2aClient = null;
        }
        agentCard = null;
        connected = false;
    }

    private void ensureConnected() {
        if (!connected || a2aClient == null) {
            throw new IllegalStateException(
                "Not connected to an A2A agent. Call connect() first.");
        }
    }

    private String extractTextFromMessage(Message message) {
        if (message == null || message.parts() == null) {
            throw new RuntimeException("Message has no content parts");
        }
        StringBuilder text = new StringBuilder();
        for (Part<?> part : message.parts()) {
            if (part instanceof TextPart textPart) {
                text.append(textPart.text());
            }
        }
        if (text.length() == 0) {
            throw new RuntimeException("Message contains no text parts");
        }
        return text.toString();
    }

    /**
     * Entry point for testing the A2A client.
     *
     * Usage: java YawlA2AClient &lt;agent-url&gt;
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java YawlA2AClient <agent-url>");
            System.err.println("Example: java YawlA2AClient http://localhost:8081");
            System.exit(1);
        }

        String agentUrl = args[0];
        YawlA2AClient client = new YawlA2AClient(agentUrl);

        try {
            client.connect();

            AgentCard card = client.getAgentCard();
            System.out.println("Agent: " + card.name());
            System.out.println("Description: " + card.description());
            System.out.println("Version: " + card.version());
            System.out.println("Provider: " + card.provider().organization());

            System.out.println("\nSkills:");
            for (AgentSkill skill : client.getSkills()) {
                System.out.println("  - " + skill.name() + ": " + skill.description());
            }

            System.out.println("\nSending test message...");
            String response = client.sendMessage("List all loaded workflow specifications");
            System.out.println("Response: " + response);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.close();
        }
    }
}
