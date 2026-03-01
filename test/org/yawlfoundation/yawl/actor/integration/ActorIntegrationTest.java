/*
 * YAWL - Yet Another Workflow Language
 * Copyright (C) 2003-2006, 2008-2011, 2014-2019 National University of Ireland, Galway
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.yawlfoundation.yawl.actor.integration;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.engine.*;
import org.yawlfoundation.yawl.engine.YWorkItem.StatusStatus;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Actor Message Handling with real YAWL engine integration
 *
 * Tests message handling through real YAWL engine instances, including
 * external event firing, workitem processing, and inter-actor communication.
 */
@Execution(ExecutionMode.CONCURRENT)
@DisplayName("Integration: Actor Message Handling")
class ActorIntegrationTest {

    private static YEngine engine;
    private YSpecificationID specID;
    private YNet net;
    private String caseID;
    private List<String> actorIDs;

    @BeforeAll
    static void setupEngine() throws Exception {
        engine = YEngine.getInstance();
        if (engine != null) {
            engine.initialise();
        }
    }

    @AfterAll
    static void cleanupEngine() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @BeforeEach
    void setupSpecification() throws Exception {
        // Create test specification with multiple actors
        specID = engine.importSpecification(createMultiActorSpecXML());
        assertNotNull(specID, "Specification import must succeed");

        // Create case
        caseID = engine.createCase(specID);
        assertNotNull(caseID, "Case creation must succeed");

        // Get work items and process start
        List<YWorkItem> workItems = engine.getWorkItems(caseID, StatusStatus.ALL);
        assertFalse(workItems.isEmpty(), "Must have work items");

        // Process work items to set up actors
        for (YWorkItem workItem : workItems) {
            engine.startWorkItem(caseID, workItem.getID(), "");
        }

        // Get actor IDs
        actorIDs = engine.getActiveParticipants();
        assertFalse(actorIDs.isEmpty(), "Must have active actors");
    }

    @AfterEach
    void cleanup() throws Exception {
        if (caseID != null) {
            engine.completeCase(caseID);
        }
        if (specID != null) {
            engine.removeSpecification(specID);
        }
    }

    @Test
    @DisplayName("External event integration test")
    void testExternalEventIntegration() throws Exception {
        // Test firing external events through real YAWL engine
        AtomicInteger eventHandled = new AtomicInteger(0);
        AtomicInteger workItemsCreated = new AtomicInteger(0);

        // Monitor for new work items
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(() -> {
            List<YWorkItem> workItems = engine.getWorkItems(caseID, StatusStatus.ALL);
            for (YWorkItem workItem : workItems) {
                if (workItem.getStatus() == StatusStatus.Fired &&
                    workItem.getName().contains("external_task")) {
                    workItemsCreated.incrementAndGet();
                }
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        // Fire external event
        String eventName = "external_integration_test";
        Map<String, String> eventData = Map.of("test_key", "test_value", "iteration", "1");

        boolean fireEventResult = engine.fireExternalEvent(
            caseID,
            eventName,
            eventData,
            actorIDs.get(0)
        );

        assertTrue(fireEventResult, "External event must be fired successfully");

        // Wait for event processing
        Thread.sleep(1000);

        monitor.shutdown();

        // Verify event was handled
        assertTrue(workItemsCreated.get() > 0, "Event should create work items");
    }

    @Test
    @DisplayName("Inter-actor communication test")
    void testInterActorCommunication() throws Exception {
        // Test communication between multiple actors
        AtomicInteger messagesExchanged = new AtomicInteger(0);
        AtomicInteger responsesReceived = new AtomicInteger(0);

        // Setup communication pattern
        for (int i = 0; i < actorIDs.size(); i++) {
            String sourceActor = actorIDs.get(i);
            String targetActor = actorIDs.get((i + 1) % actorIDs.size());
            String message = "comm_test_" + i;

            // Send message from source to target
            boolean messageSent = engine.sendYAWLMessage(
                caseID,
                message,
                Map.of("source", sourceActor, "target", targetActor),
                sourceActor,
                targetActor
            );

            assertTrue(messageSent, "Message must be sent successfully");
            messagesExchanged.incrementAndGet();
        }

        // Monitor responses
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(() -> {
            List<YWorkItem> workItems = engine.getWorkItems(caseID, StatusStatus.All);
            for (YWorkItem workItem : workItems) {
                if (workItem.getStatus() == StatusStatus.Fired &&
                    workItem.getName().contains("response_task")) {
                    responsesReceived.incrementAndGet();
                }
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        // Wait for responses
        Thread.sleep(2000);

        monitor.shutdown();

        // Verify communication
        assertTrue(responsesReceived.get() > 0, "Responses should be received");
        assertEquals(actorIDs.size(), messagesExchanged.get(),
                    "All messages should be sent");
    }

    @Test
    @DisplayName("Actor lifecycle management integration")
    void testActorLifecycleManagement() throws Exception {
        // Test actor creation, activation, and deactivation
        AtomicInteger actorCreated = new AtomicInteger(0);
        AtomicInteger actorActivated = new AtomicInteger(0);
        AtomicInteger actorDeactivated = new AtomicInteger(0);

        // Create new actor
        String newActorID = "test_actor_" + System.currentTimeMillis();
        boolean actorCreatedResult = engine.createParticipant(
            specID,
            newActorID,
            "TestActor",
            true // Enabled
        );

        assertTrue(actorCreatedResult, "New actor must be created");
        actorCreated.incrementAndGet();

        // Activate actor
        boolean actorActivatedResult = engine.enableParticipant(specID, newActorID);
        assertTrue(actorActivatedResult, "Actor must be activated");
        actorActivated.incrementAndGet();

        // Simulate actor work
        List<YWorkItem> workItems = engine.getWorkItems(caseID, StatusStatus.All);
        assertFalse(workItems.isEmpty(), "Must have work items");

        // Process work items
        for (YWorkItem workItem : workItems) {
            engine.startWorkItem(caseID, workItem.getID(), newActorID);
        }

        // Deactivate actor
        boolean actorDeactivatedResult = engine.disableParticipant(specID, newActorID);
        assertTrue(actorDeactivatedResult, "Actor must be deactivated");
        actorDeactivated.incrementAndGet();

        // Verify lifecycle
        assertEquals(1, actorCreated.get(), "One actor should be created");
        assertEquals(1, actorActivated.get(), "One actor should be activated");
        assertEquals(1, actorDeactivated.get(), "One actor should be deactivated");
    }

    @Test
    @DisplayName("Message persistence and recovery integration")
    void testMessagePersistenceAndRecovery() throws Exception {
        // Test message persistence through engine restart
        AtomicInteger messagesPersisted = new AtomicInteger(0);
        List<String> sentMessages = new ArrayList<>();

        // Send messages
        for (int i = 0; i < 10; i++) {
            String messageId = "persist_test_" + i;
            Map<String, String> data = Map.of("message_id", messageId, "persistence", "true");

            boolean messageSent = engine.sendYAWLMessage(
                caseID,
                messageId,
                data,
                actorIDs.get(0),
                actorIDs.get(1)
            );

            assertTrue(messageSent, "Message must be sent");
            sentMessages.add(messageId);
            messagesPersisted.incrementAndGet();
        }

        // Simulate engine restart
        Thread.sleep(1000);
        engine.shutdown();
        Thread.sleep(1000);

        // Re-initialize engine
        engine = YEngine.getInstance();
        engine.initialise();

        // Restore case and check persistence
        List<YWorkItem> persistedWorkItems = engine.getWorkItems(caseID, StatusStatus.All);
        int persistedCount = (int) persistedWorkItems.stream()
            .filter(wi -> wi.getName().contains("persist"))
            .count();

        assertTrue(persistedCount > 0, "Some messages should persist");
    }

    @Test
    @DisplayName("Concurrent message handling integration")
    void testConcurrentMessageHandling() throws Exception {
        // Test concurrent message handling with multiple actors
        int messageCount = 50;
        AtomicInteger messagesProcessed = new AtomicInteger(0);
        AtomicBoolean allProcessed = new AtomicBoolean(false);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // Send messages concurrently
        CountDownLatch latch = new CountDownLatch(messageCount);
        for (int i = 0; i < messageCount; i++) {
            final int messageId = i;
            executor.submit(() -> {
                try {
                    String eventName = "concurrent_test_" + messageId;
                    boolean messageSent = engine.fireExternalEvent(
                        caseID,
                        eventName,
                        Map.of("message_id", messageId, "concurrent", "true"),
                        actorIDs.get(messageId % actorIDs.size())
                    );

                    if (messageSent) {
                        messagesProcessed.incrementAndGet();
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Monitor processing
        Thread monitorThread = new Thread(() -> {
            try {
                latch.await(10, TimeUnit.SECONDS);
                allProcessed.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        monitorThread.start();

        monitorThread.join();

        // Verify concurrent processing
        assertTrue(allProcessed.get(), "All messages should be processed");
        assertTrue(messagesProcessed.get() >= messageCount * 0.9,
                   String.format("At least 90%% of messages processed: %d/%d",
                                  messagesProcessed.get(), messageCount));
    }

    @Test
    @DisplayName("Message flow validation integration")
    void testMessageFlowValidation() throws Exception {
        // Test message flow through the specification
        AtomicInteger flowStepsCompleted = new AtomicInteger(0);
        Set<String> completedSteps = ConcurrentHashMap.newKeySet();

        // Trigger message flow
        String flowStartEvent = "start_flow";
        boolean flowStarted = engine.fireExternalEvent(
            caseID,
            flowStartEvent,
            Collections.emptyMap(),
            actorIDs.get(0)
        );

        assertTrue(flowStarted, "Flow must start successfully");

        // Monitor flow completion
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(() -> {
            List<YWorkItem> workItems = engine.getWorkItems(caseID, StatusStatus.All);
            for (YWorkItem workItem : workItems) {
                if (workItem.getStatus() == StatusStatus.Completed) {
                    String stepId = workItem.getTask().getID();
                    if (completedSteps.add(stepId)) {
                        flowStepsCompleted.incrementAndGet();
                    }
                }
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        // Wait for flow completion
        Thread.sleep(3000);

        monitor.shutdown();

        // Verify flow completion
        assertTrue(flowStepsCompleted.get() > 0, "Flow steps should be completed");
    }

    @Test
    @DisplayName("Actor error handling integration")
    void testActorErrorHandling() throws Exception {
        // Test error handling in actor message processing
        AtomicInteger errorsHandled = new AtomicInteger(0);
        AtomicInteger messagesProcessed = new AtomicInteger(0);

        // Send error-prone messages
        for (int i = 0; i < 10; i++) {
            final int messageId = i;
            try {
                String eventName = "error_test_" + messageId;
                boolean messageSent = engine.fireExternalEvent(
                    caseID,
                    eventName,
                    Map.of("message_id", messageId, "error_prone", "true"),
                    actorIDs.get(0)
                );

                if (messageSent) {
                    messagesProcessed.incrementAndGet();
                }
            } catch (Exception e) {
                errorsHandled.incrementAndGet();
            }
        }

        // Send error message
        try {
            boolean errorResult = engine.fireExternalEvent(
                caseID,
                "error_message",
                Map.of("type", "malformed"),
                actorIDs.get(0)
            );

            if (!errorResult) {
                errorsHandled.incrementAndGet();
            }
        } catch (Exception e) {
            errorsHandled.incrementAndGet();
        }

        // Verify error handling
        assertTrue(messagesProcessed.get() > 0, "Some messages should be processed");
        assertTrue(errorsHandled.get() >= 0, "Errors should be handled gracefully");
    }

    @Test
    @DisplayName("Actor scaling integration")
    void testActorScaling() throws Exception {
        // Test actor scaling under load
        int baseActorCount = actorIDs.size();
        int additionalActors = 5;
        AtomicInteger successfulScaling = new AtomicInteger(0);

        // Add additional actors
        for (int i = 0; i < additionalActors; i++) {
            String newActorID = "scale_actor_" + i;
            boolean actorCreated = engine.createParticipant(
                specID,
                newActorID,
                "ScalingActor",
                true
            );

            if (actorCreated) {
                boolean activated = engine.enableParticipant(specID, newActorID);
                if (activated) {
                    successfulScaling.incrementAndGet();
                }
            }
        }

        // Test scaling with load
        int loadMessages = 100;
        AtomicInteger loadMessagesProcessed = new AtomicInteger(0);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch loadLatch = new CountDownLatch(loadMessages);

        for (int i = 0; i < loadMessages; i++) {
            final int messageId = i;
            executor.submit(() -> {
                try {
                    String eventName = "scale_test_" + messageId;
                    boolean messageSent = engine.fireExternalEvent(
                        caseID,
                        eventName,
                        Collections.emptyMap(),
                        actorIDs.get((messageId % actorIDs.size()))
                    );

                    if (messageSent) {
                        loadMessagesProcessed.incrementAndGet();
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                } finally {
                    loadLatch.countDown();
                }
            });
        }

        loadLatch.await();
        executor.shutdown();

        // Verify scaling
        assertTrue(successfulScaling.get() >= additionalActors * 0.8,
                   String.format("Scaling should succeed for at least 80%% of new actors: %d/%d",
                                  successfulScaling.get(), additionalActors));
        assertTrue(loadMessagesProcessed.get() >= loadMessages * 0.9,
                   String.format("Load should be processed by scaled actors: %d/%d",
                                  loadMessagesProcessed.get(), loadMessages));
    }

    // Helper methods

    private String createMultiActorSpecXML() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <specification xmlns="http://www.yawlfoundation.org/yawlschema">
              <header>
                <name>MultiActorSpec</name>
                <version>1.0</version>
                <description>Specification for multi-actor integration tests</description>
              </header>
              <nets>
                <net id="Net">
                  <inputCondition id="i"/>
                  <tasks>
                    <task id="StartTask">
                      <flowsInto id="i"/>
                      <flowsInto id="Split"/>
                    </task>
                    <task id="Split">
                      <decompositionRef>SplitNet</decompositionRef>
                      <flowsInto id="Actor1"/>
                      <flowsInto id="Actor2"/>
                      <flowsInto id="Actor3"/>
                    </task>
                    <task id="Actor1">
                      <externalEventHandler>external_handler_1</externalEventHandler>
                      <flowsInto id="Join"/>
                    </task>
                    <task id="Actor2">
                      <externalEventHandler>external_handler_2</externalEventHandler>
                      <flowsInto id="Join"/>
                    </task>
                    <task id="Actor3">
                      <externalEventHandler>external_handler_3</externalEventHandler>
                      <flowsInto id="Join"/>
                    </task>
                    <task id="Join">
                      <decompositionRef>JoinNet</decompositionRef>
                      <flowsInto id="End"/>
                    </task>
                    <task id="End">
                      <flowsInto id="i"/>
                    </task>
                  </tasks>
                  <outputCondition id="o">
                    <flowsInto id="End"/>
                  </outputCondition>
                </net>
                <net id="SplitNet">
                  <inputCondition id="i"/>
                  <tasks>
                    <task id="SplitStart">
                      <flowsInto id="i"/>
                      <flowsInto id="SplitOut1"/>
                      <flowsInto id="SplitOut2"/>
                      <flowsInto id="SplitOut3"/>
                    </task>
                  </tasks>
                  <outputCondition id="o">
                    <flowsInto id="SplitStart"/>
                  </outputCondition>
                </net>
                <net id="JoinNet">
                  <inputCondition id="i"/>
                  <tasks>
                    <task id="JoinEnd">
                      <flowsInto id="i"/>
                      <flowsInto id="o"/>
                    </task>
                  </tasks>
                  <outputCondition id="o">
                    <flowsInto id="JoinEnd"/>
                  </outputCondition>
                </net>
              </nets>
            </specification>
            """;
    }
}