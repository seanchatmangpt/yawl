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

package org.yawlfoundation.yawl.integration.a2a;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Demo application showing A2A wiring for buried engines in YAWL.
 *
 * <p>This demo demonstrates the complete A2A integration system including:
 * <ul>
 *   <li>BuriedEngineA2AAdapter for engine communication</li>
 *   <li>AgentA2AIntegration for agent communication</li>
 *   <li>Agent-to-agent message routing through buried engines</li>
 *   <li>Async message delivery with virtual threads</li>
 *   <li>Integration with existing Agent class patterns</li>
 * </ul>
 */
public class A2ADemo {

    private static final String PEER_REGISTRY_ID = "demo-registry";
    private static final String ENGINE_ID = "engine-demo";
    private static final String AGENT_ID = "agent-demo";
    private static final String WORKFLOW_GROUP = "demo-workflow";

    public static void main(String[] args) {
        System.out.println("=== YAWL A2A Integration Demo ===");
        System.out.println("This demo shows A2A wiring for buried engines in YAWL.\n");

        // Create peer registry
        PeerRegistry peerRegistry = new PeerRegistry();
        System.out.println("✓ Created peer registry: " + PEER_REGISTRY_ID);

        // Create buried engine adapter
        BuriedEngineA2AAdapter engineAdapter = new BuriedEngineA2AAdapter(
            ENGINE_ID,
            WORKFLOW_GROUP,
            peerRegistry,
            Duration.ofSeconds(10),
            50
        );
        System.out.println("✓ Created buried engine adapter: " + ENGINE_ID);

        // Create agent integration
        AgentA2AIntegration agentIntegration = new AgentA2AIntegration(
            AGENT_ID,
            WORKFLOW_GROUP,
            peerRegistry,
            Duration.ofSeconds(10)
        );
        System.out.println("✓ Created agent integration: " + AGENT_ID);

        // Set up message handlers
        AtomicReference<String> lastEngineMessage = new AtomicReference<>();
        AtomicReference<String> lastAgentMessage = new AtomicReference<>();
        AtomicBoolean engineMessageReceived = new AtomicBoolean(false);
        AtomicBoolean agentMessageReceived = new AtomicBoolean(false);

        // Engine message handler
        engineAdapter.setMessageHandler(message -> {
            System.out.println("\n📩 Engine received message:");
            System.out.println("   Type: " + message.type());
            System.out.println("   From: " + message.sourceEngine());
            System.out.println("   Payload: " + message.payload());
            lastEngineMessage.set(message.messageId());
            engineMessageReceived.set(true);
        });

        // Agent event handlers
        agentIntegration.subscribeToAllEvents(message -> {
            System.out.println("\n📥 Agent received event:");
            System.out.println("   Type: " + message.type());
            System.out.println("   From: " + message.sourceEngine());
            System.out.println("   Payload: " + message.payload());
            lastAgentMessage.set(message.messageId());
            agentMessageReceived.set(true);
        });

        // Start the system
        engineAdapter.start();
        agentIntegration.start();

        // Register participants as peers
        peerRegistry.registerPeer(ENGINE_ID,
            new PeerConnection.InMemoryPeerConnection(ENGINE_ID, engineAdapter));
        peerRegistry.registerPeer(AGENT_ID,
            new PeerConnection.InMemoryPeerConnection(AGENT_ID, agentIntegration));
        System.out.println("✓ Registered participants as peers");

        // Demo scenarios
        Scanner scanner = new Scanner(System.in);

        try {
            // Scenario 1: Agent to Engine Communication
            System.out.println("\n--- Scenario 1: Agent to Engine Communication ---");
            System.out.println("Agent sends workflow event to engine...");

            boolean sent1 = agentIntegration.sendToEngine(ENGINE_ID, "workflow-event",
                Map.of(
                    "caseId", "case-" + System.currentTimeMillis(),
                    "event", "task-completed",
                    "timestamp", Instant.now()
                ));

            System.out.println("Message sent: " + sent1);
            System.out.println("Waiting for engine to receive message...");
            Thread.sleep(1000);

            if (engineMessageReceived.get()) {
                System.out.println("✓ Engine received the message successfully!");
            } else {
                System.out.println("⚠ Engine did not receive the message");
            }

            // Scenario 2: Engine to Agent Communication
            System.out.println("\n--- Scenario 2: Engine to Agent Communication ---");
            System.out.println("Engine sends response to agent...");

            boolean sent2 = engineAdapter.sendToAgent(AGENT_ID,
                A2AMessage.builder()
                    .type("engine-response")
                    .payload(Map.of(
                        "responseCode", "200",
                        "message", "Task processed successfully"
                    ))
                    .build());

            System.out.println("Response sent: " + sent2);
            System.out.println("Waiting for agent to receive response...");
            Thread.sleep(1000);

            if (agentMessageReceived.get()) {
                System.out.println("✓ Agent received the response successfully!");
            } else {
                System.out.println("⚠ Agent did not receive the response");
            }

            // Scenario 3: Engine Broadcast
            System.out.println("\n--- Scenario 3: Engine Broadcast to Group ---");
            System.out.println("Engine broadcasts message to all agents in group...");

            A2AMessage broadcastMessage = A2AMessage.builder()
                .type("broadcast-test")
                .payload(Map.of(
                    "broadcastId", "broadcast-" + System.currentTimeMillis(),
                    "message", "This is a broadcast message"
                ))
                .build();

            int broadcastCount = engineAdapter.broadcastToGroup(broadcastMessage);
            System.out.println("Broadcast sent to " + broadcastCount + " peers");

            // Add another agent to show broadcast
            AgentA2AIntegration secondAgent = new AgentA2AIntegration(
                "agent-demo-2",
                WORKFLOW_GROUP,
                peerRegistry,
                Duration.ofSeconds(10)
            );

            secondAgent.start();
            peerRegistry.registerPeer("agent-demo-2",
                new PeerConnection.InMemoryPeerConnection("agent-demo-2", secondAgent));

            secondAgent.subscribeToAllEvents(message -> {
                System.out.println("\n📥 Second agent received broadcast:");
                System.out.println("   Type: " + message.type());
                System.out.println("   Payload: " + message.payload());
            });

            // Broadcast again with second agent present
            int secondBroadcastCount = engineAdapter.broadcastToGroup(broadcastMessage);
            System.out.println("Second broadcast sent to " + secondBroadcastCount + " peers");

            System.out.println("\n✓ Demo completed successfully!");
            System.out.println("\nKey features demonstrated:");
            System.out.println("  • Agent-to-engine message communication");
            System.out.println("  • Engine-to-agent response communication");
            System.out.println("  • Message routing through peer network");
            System.out.println("  • Async message delivery with virtual threads");
            System.out.println("  • Integration with existing YAWL patterns");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Demo interrupted: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error during demo: " + e.getMessage());
        } finally {
            // Clean up
            System.out.println("\n--- Shutting down demo ---");
            engineAdapter.stop();
            agentIntegration.stop();
            System.out.println("✓ Demo shut down successfully");
        }
    }
}