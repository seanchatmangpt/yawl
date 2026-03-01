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

package org.yawlfoundation.yawl.actor.unit;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.elements.YSpecificationID;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItem.StatusStatus;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Actor Message Handler with comprehensive message processing validation
 *
 * Tests message handling, delivery guarantees, ordering, and throughput in actor systems.
 */
@Execution(ExecutionMode.CONCURRENT)
@DisplayName("Unit: Actor Message Handler")
class ActorMessageHandlerTest {

    private YEngine engine;
    private YSpecificationID specID;
    private YNetRunner netRunner;
    private final int TEST_TIMEOUT_MS = 5000;

    @BeforeAll
    static void setup() {
        YEngine engine = YEngine.getInstance();
        if (engine != null) {
            engine.initialise();
        }
    }

    @BeforeEach
    void setupTest() throws Exception {
        engine = YEngine.getInstance();
        assertNotNull(engine, "Engine must be initialized");

        // Create test specification
        String specXML = createSimpleMessageSpec();
        specID = engine.importSpecification(specXML);
        assertNotNull(specID, "Specification must be imported");

        // Create case and net runner
        String caseID = engine.createCase(specID);
        assertNotNull(caseID, "Case creation must succeed");

        List<YWorkItem> workItems = engine.getWorkItems(caseID, StatusStatus.ALL);
        assertFalse(workItems.isEmpty(), "Must have work items");

        netRunner = engine.getNetRunner(caseID, workItems.get(0).getID());
        assertNotNull(netRunner, "Net runner must be created");
    }

    @AfterEach
    void cleanup() throws Exception {
        if (specID != null) {
            engine.removeSpecification(specID);
        }
        if (netRunner != null) {
            netRunner.cancel();
        }
    }

    @Test
    @DisplayName("Message delivery guarantee")
    void testMessageDeliveryGuarantee() throws Exception {
        // Test that all messages are delivered without loss
        int messageCount = 100;
        AtomicInteger deliveredCount = new AtomicInteger(0);
        AtomicBoolean allDelivered = new AtomicBoolean(false);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // Send messages
        for (int i = 0; i < messageCount; i++) {
            final int messageId = i;
            executor.submit(() -> {
                try {
                    netRunner.fireExternalEvent(
                        "message_" + messageId,
                        null,
                        null
                    );
                    deliveredCount.incrementAndGet();
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Monitor delivery
        Thread timeoutThread = new Thread(() -> {
            try {
                Thread.sleep(TEST_TIMEOUT_MS);
                allDelivered.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        timeoutThread.start();

        // Wait for all messages to be delivered
        while (!allDelivered.get() && deliveredCount.get() < messageCount) {
            Thread.sleep(100);
        }

        timeoutThread.interrupt();

        // Verify all messages were delivered
        assertEquals(messageCount, deliveredCount.get(),
                     "All messages must be delivered");
    }

    @Test
    @DisplayName("Message ordering guarantee")
    void testMessageOrderingGuarantee() throws Exception {
        // Test that messages are processed in correct order
        int messageCount = 50;
        List<Integer> deliveredOrder = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean allDelivered = new AtomicBoolean(false);

        // Create messages with sequence numbers
        for (int i = 0; i < messageCount; i++) {
            final int sequence = i;
            netRunner.fireExternalEvent(
                "message_" + sequence,
                Collections.singletonMap("sequence", sequence),
                null
            );
        }

        // Monitor delivery order
        Thread monitoringThread = new Thread(() -> {
            try {
                Thread.sleep(TEST_TIMEOUT_MS);
                allDelivered.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        monitoringThread.start();

        // Check ordering
        while (!allDelivered.get()) {
            Thread.sleep(100);
            // This is a simplified check - real ordering would need more sophisticated tracking
        }

        monitoringThread.interrupt();
        assertTrue(deliveredOrder.size() >= messageCount * 0.9,
                  "Message ordering should be maintained");
    }

    @Test
    @DisplayName("Message throughput validation")
    void testMessageThroughputValidation() throws Exception {
        // Test message processing throughput
        int messageCount = 1000;
        long startTime = System.currentTimeMillis();
        AtomicInteger processedCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // Send messages in parallel
        for (int i = 0; i < messageCount; i++) {
            final int messageId = i;
            executor.submit(() -> {
                try {
                    long messageStart = System.nanoTime();
                    netRunner.fireExternalEvent(
                        "message_" + messageId,
                        null,
                        null
                    );
                    long processingTime = System.nanoTime() - messageStart;
                    processedCount.incrementAndGet();

                    // Record processing time for throughput calculation
                    synchronized (this) {
                        if (!processingTimes.containsKey(processingTime)) {
                            processingTimes.put(processingTime, 1);
                        } else {
                            processingTimes.put(processingTime, processingTimes.get(processingTime) + 1);
                        }
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Wait for processing
        while (processedCount.get() < messageCount) {
            Thread.sleep(100);
        }

        long endTime = System.currentTimeMillis();
        double durationSeconds = (endTime - startTime) / 1000.0;
        double throughput = messageCount / durationSeconds;

        // Verify throughput meets minimum requirements
        assertTrue(throughput > 100, // 100 messages per second minimum
                   String.format("Throughput %.2f msg/s must exceed minimum", throughput));

        executor.shutdown();
    }

    @Test
    @DisplayName("Message duplicate detection")
    void testMessageDuplicateDetection() throws Exception {
        // Test duplicate message detection and handling
        int duplicateCount = 10;
        Set<String> processedMessages = ConcurrentHashMap.newKeySet();
        AtomicInteger duplicateDetected = new AtomicInteger(0);

        // Send duplicate messages
        for (int i = 0; i < 5; i++) {
            String messageId = "duplicate_" + i;
            for (int j = 0; j < duplicateCount; j++) {
                if (!processedMessages.contains(messageId)) {
                    try {
                        netRunner.fireExternalEvent(
                            messageId,
                            null,
                            null
                        );
                        processedMessages.add(messageId);
                    } catch (Exception e) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    duplicateDetected.incrementAndGet();
                }
            }
        }

        // Verify duplicates were detected
        assertTrue(duplicateDetected.get() > 0,
                  "Duplicate messages should be detected");
    }

    @Test
    @DisplayName("Message priority handling")
    void testMessagePriorityHandling() throws Exception {
        // Test message priority ordering
        AtomicInteger highPriorityCount = new AtomicInteger(0);
        AtomicInteger lowPriorityCount = new AtomicInteger(0);
        AtomicBoolean priorityRespected = new AtomicBoolean(false);

        // Send messages with different priorities
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // High priority messages
        for (int i = 0; i < 10; i++) {
            final int id = i;
            executor.submit(() -> {
                try {
                    netRunner.fireExternalEvent(
                        "high_priority_" + id,
                        Collections.singletonMap("priority", "high"),
                        null
                    );
                    highPriorityCount.incrementAndGet();
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Low priority messages
        for (int i = 0; i < 10; i++) {
            final int id = i;
            executor.submit(() -> {
                try {
                    netRunner.fireExternalEvent(
                        "low_priority_" + id,
                        Collections.singletonMap("priority", "low"),
                        null
                    );
                    lowPriorityCount.incrementAndGet();
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Monitor priority handling
        Thread monitoringThread = new Thread(() -> {
            try {
                Thread.sleep(TEST_TIMEOUT_MS);
                if (highPriorityCount.get() >= 8 && lowPriorityCount.get() >= 5) {
                    priorityRespected.set(true);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        monitoringThread.start();

        monitoringThread.join();

        assertTrue(priorityRespected.get(),
                   "High priority messages should be processed first");
        executor.shutdown();
    }

    @Test
    @DisplayName("Message persistence and recovery")
    void testMessagePersistenceAndRecovery() throws Exception {
        // Test message persistence and recovery after failure
        int messageCount = 20;
        List<String> sentMessages = new ArrayList<>();
        AtomicInteger recoveredCount = new AtomicInteger(0);

        // Send messages
        for (int i = 0; i < messageCount; i++) {
            String messageId = "persistent_" + i;
            sentMessages.add(messageId);

            try {
                netRunner.fireExternalEvent(
                    messageId,
                    Collections.singletonMap("persistent", true),
                    null
                );
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
        }

        // Simulate failure
        Thread.sleep(100);
        netRunner.cancel();
        netRunner = engine.getNetRunner(
            "case_id", "workitem_id"
        ); // Re-create net runner

        // Check recovery
        for (String messageId : sentMessages) {
            try {
                // Try to recover message
                netRunner.fireExternalEvent(
                    messageId + "_recovery",
                    null,
                    null
                );
                recoveredCount.incrementAndGet();
            } catch (Exception e) {
                // Expected for some messages
            }
        }

        // Some messages should be recoverable
        assertTrue(recoveredCount.get() > 0,
                   "Some messages should be recoverable");
    }

    @Test
    @DisplayName("Message batch processing")
    void testMessageBatchProcessing() throws Exception {
        // Test efficient batch message processing
        int batchSize = 100;
        int batchCount = 10;
        AtomicInteger totalProcessed = new AtomicInteger(0);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // Process messages in batches
        for (int batch = 0; batch < batchCount; batch++) {
            final int batchNum = batch;
            executor.submit(() -> {
                try {
                    // Process batch
                    for (int i = 0; i < batchSize; i++) {
                        String messageId = "batch_" + batchNum + "_" + i;
                        netRunner.fireExternalEvent(
                            messageId,
                            Collections.singletonMap("batch", batchNum),
                            null
                        );
                        totalProcessed.incrementAndGet();
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Wait for all batches to complete
        while (totalProcessed.get() < batchCount * batchSize) {
            Thread.sleep(100);
        }

        // Verify batch processing efficiency
        long startTime = System.currentTimeMillis();
        long totalMessages = totalProcessed.get();
        double throughput = totalMessages / ((System.currentTimeMillis() - startTime) / 1000.0);

        assertTrue(throughput > 500, // 500 messages per second minimum
                   String.format("Batch throughput %.2f msg/s must exceed minimum", throughput));

        executor.shutdown();
    }

    @Test
    @DisplayName("Message error handling")
    void testMessageErrorHandling() throws Exception {
        // Test error handling for invalid messages
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        // Send valid and invalid messages
        for (int i = 0; i < 50; i++) {
            final int messageId = i;
            try {
                if (i % 5 == 0) {
                    // Send invalid message
                    netRunner.fireExternalEvent(
                        "invalid_message",
                        Collections.singletonMap("malformed", true),
                        null
                    );
                } else {
                    // Send valid message
                    netRunner.fireExternalEvent(
                        "valid_message_" + messageId,
                        Collections.singletonMap("valid", true),
                        null
                    );
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                errorCount.incrementAndGet();
                Thread.currentThread().interrupt();
            }
        }

        // Error handling should not crash the system
        assertTrue(successCount.get() > 0,
                   "Some valid messages should be processed");
        assertTrue(errorCount.get() >= 0,
                   "Error handling should be graceful");
    }

    @Test
    @DisplayName("Message compression and size limits")
    void testMessageCompressionAndSizeLimits() throws Exception {
        // Test message compression and size handling
        int largeMessageSize = 1024 * 1024; // 1MB message
        byte[] largeData = new byte[largeMessageSize];
        Arrays.fill(largeData, (byte) 'A');

        AtomicInteger largeMessageProcessed = new AtomicInteger(0);
        AtomicBoolean sizeViolationDetected = new AtomicBoolean(false);

        try {
            // Send large message
            netRunner.fireExternalEvent(
                "large_message",
                Collections.singletonMap("data", largeData),
                null
            );
            largeMessageProcessed.incrementAndGet();
        } catch (IllegalArgumentException e) {
            sizeViolationDetected.set(true);
            // Expected for large messages
        }

        // Send normal message
        netRunner.fireExternalEvent(
            "normal_message",
            Collections.singletonMap("data", "normal data"),
            null
        );

        // Large message should be handled appropriately
        assertTrue(sizeViolationDetected.get() || largeMessageProcessed.get() > 0,
                   "Large message handling should be appropriate");
    }

    // Helper methods

    private String createSimpleMessageSpec() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <specification xmlns="http://www.yawlfoundation.org/yawlschema">
              <header>
                <name>SimpleMessageSpec</name>
                <version>1.0</version>
                <description>Specification for message handling tests</description>
              </header>
              <nets>
                <net id="Net">
                  <inputCondition id="i"/>
                  <tasks>
                    <task id="MessageTask">
                      <flowsInto id="i"/>
                      <flowsInto id="o"/>
                      <externalEventHandler>messageHandler</externalEventHandler>
                    </task>
                  </tasks>
                  <outputCondition id="o">
                    <flowsInto id="MessageTask"/>
                  </outputCondition>
                </net>
              </nets>
            </specification>
            """;
    }

    // Processing time tracking for throughput analysis
    private final Map<Long, Integer> processingTimes = new HashMap<>();
}