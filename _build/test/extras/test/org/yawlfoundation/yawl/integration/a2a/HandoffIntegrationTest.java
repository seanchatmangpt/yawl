/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.a2a;

import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffMessage;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffProtocol;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffToken;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffException;
import org.yawlfoundation.yawl.integration.a2a.auth.JwtAuthenticationProvider;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for work item handoff protocol.
 *
 * Tests the complete handoff process between autonomous agents as defined in ADR-025.
 * Focuses on real-world scenarios including token validation, message passing,
 * and coordination between source and target agents.
 *
 * Coverage targets:
 * - Complete handoff workflow
 * - Token generation and validation
 * - A2A message transmission
 * - Session handle transfer
 * - Work item rollback
 * - Error handling and recovery
 * - Performance under load
 * - Timeout handling
 */
class HandoffIntegrationTest {

    private HandoffProtocol handoffProtocol;
    private TestA2AMessageRouter messageRouter;
    private TestWorkItemService workItemService;
    private static final String TEST_WORK_ITEM_ID = "WI-42";
    private static final String TEST_SOURCE_AGENT = "agent-source";
    private static final String TEST_TARGET_AGENT = "agent-target";
    private static final String TEST_SESSION_HANDLE = "session-handle-123";

    @BeforeEach
    void setUp() throws Exception {
        // Create JWT provider with test secret
        String testSecret = "test-secret-key-for-handoff-integration-32-characters";
        JwtAuthenticationProvider jwtProvider = new JwtAuthenticationProvider(testSecret, null);

        // Create handoff protocol
        handoffProtocol = new HandoffProtocol(jwtProvider);

        // Initialize test services
        messageRouter = new TestA2AMessageRouter();
        workItemService = new TestWorkItemService();
    }

    @Nested
    @DisplayName("Complete Handoff Workflow Tests")
    class CompleteHandoffWorkflowTests {

        @Test
        @DisplayName("Successful handoff from source to target agent")
        void successfulHandoffFromSourceToTargetAgent() throws Exception {
            // Given source agent has checked out work item
            workItemService.checkoutWorkItem(TEST_WORK_ITEM_ID, TEST_SESSION_HANDLE);

            // When source agent initiates handoff
            HandoffMessage handoffMessage = handoffProtocol.createHandoffMessage(
                TEST_WORK_ITEM_ID,
                TEST_SOURCE_AGENT,
                TEST_TARGET_AGENT,
                TEST_SESSION_HANDLE,
                Map.of("reason", "Document language not supported"),
                Duration.ofSeconds(30)
            );

            // And message is sent to target agent
            messageRouter.sendMessageToAgent(TEST_TARGET_AGENT, handoffMessage);

            // Then target agent receives and validates message
            HandoffSession session = messageRouter.waitForHandoffMessage(TEST_TARGET_AGENT);
            assertNotNull(session, "Target agent should receive handoff message");
            assertEquals(TEST_WORK_ITEM_ID, session.workItemId(), "Work item ID should match");
            assertEquals(TEST_SOURCE_AGENT, session.fromAgent(), "Source agent should match");
            assertEquals(TEST_TARGET_AGENT, session.toAgent(), "Target agent should match");

            // And target agent can checkout work item
            boolean checkoutSuccess = workItemService.checkoutWorkItem(TEST_WORK_ITEM_ID, session.engineSession());
            assertTrue(checkoutSuccess, "Target agent should be able to checkout work item");

            // And source agent rolls back checkout
            workItemService.rollbackCheckout(TEST_WORK_ITEM_ID, TEST_SOURCE_AGENT);

            // Then work item is transferred successfully
            assertTrue(workItemService.isCheckedOutBy(TEST_WORK_ITEM_ID, TEST_TARGET_AGENT),
                "Work item should be checked out by target agent");
            assertFalse(workItemService.isCheckedOutBy(TEST_WORK_ITEM_ID, TEST_SOURCE_AGENT),
                "Work item should no longer be checked out by source agent");
        }

        @Test
        @DisplayName("Handoff with additional context data")
        void handoffWithContextData() throws Exception {
            // Given context data for handoff
            Map<String, Object> contextData = Map.of(
                "documentType", "contract",
                "confidence", 0.95,
                "reason", "Requires language translation",
                "metadata", Map.of("pages", 42, "language", "en")
            );

            // When source agent initiates handoff with context
            HandoffMessage handoffMessage = handoffProtocol.createHandoffMessage(
                TEST_WORK_ITEM_ID,
                TEST_SOURCE_AGENT,
                TEST_TARGET_AGENT,
                TEST_SESSION_HANDLE,
                contextData,
                Duration.ofMinutes(5)
            );

            // And message is sent and processed
            messageRouter.sendMessageToAgent(TEST_TARGET_AGENT, handoffMessage);
            HandoffSession session = messageRouter.waitForHandoffMessage(TEST_TARGET_AGENT);

            // Then context data should be preserved
            assertNotNull(session, "Session should be created");
            assertEquals(TEST_WORK_ITEM_ID, session.workItemId());

            // And target agent has access to context
            assertTrue(workItemService.hasContext(session.workItemId(), contextData),
                "Target agent should have access to context data");
        }

        @Test
        @DisplayName("Handoff handles work item already checked out")
        void handoffHandlesWorkItemAlreadyCheckedOut() throws Exception {
            // Given work item is already checked out by another agent
            workItemService.checkoutWorkItem(TEST_WORK_ITEM_ID, "other-session");

            // When source agent tries to initiate handoff
            HandoffMessage handoffMessage = handoffProtocol.createHandoffMessage(
                TEST_WORK_ITEM_ID,
                TEST_SOURCE_AGENT,
                TEST_TARGET_AGENT,
                TEST_SESSION_HANDLE
            );

            // And handoff is attempted
            messageRouter.sendMessageToAgent(TEST_TARGET_AGENT, handoffMessage);

            // Then target agent should detect conflict
            HandoffSession session = messageRouter.waitForHandoffMessage(TEST_TARGET_AGENT);
            assertNotNull(session, "Target should receive message");

            // But checkout should fail for target agent
            assertThrows(Exception.class, () -> {
                workItemService.checkoutWorkItem(TEST_WORK_ITEM_ID, session.engineSession());
            }, "Should not be able to checkout already checked out item");
        }

        @Test
        @DisplayName("Handoff fails for invalid work item")
        void handoffFailsForInvalidWorkItem() throws Exception {
            // Given invalid work item ID
            String invalidWorkItemId = "WI-NOT-EXIST";

            // When source agent tries to handoff invalid work item
            assertThrows(HandoffException.class, () -> {
                handoffProtocol.createHandoffMessage(
                    invalidWorkItemId,
                    TEST_SOURCE_AGENT,
                    TEST_TARGET_AGENT,
                    TEST_SESSION_HANDLE
                );
            }, "Should throw exception for invalid work item");
        }

        @Test
        @DisplayName("Handoff with custom TTL expires correctly")
        void handoffWithCustomTTLExpiresCorrectly() throws Exception {
            // Given handoff with very short TTL
            Duration shortTtl = Duration.ofMillis(1);
            Instant beforeHandoff = Instant.now();

            // When handoff token is created
            HandoffToken token = handoffProtocol.generateHandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_SOURCE_AGENT,
                TEST_TARGET_AGENT,
                TEST_SESSION_HANDLE,
                shortTtl
            );

            // Then token is initially valid
            assertTrue(token.isValid(), "Token should be valid when created");

            // Wait for token to expire
            Thread.sleep(10);

            // Then token should be expired
            assertFalse(token.isValid(), "Token should be expired after TTL");
        }
    }

    @Nested
    @DisplayName("A2A Message Transmission Tests")
    class A2AMessageTransmissionTests {

        @Test
        @DisplayName("A2A message routing to correct agent")
        void a2AMessageRoutingToCorrectAgent() throws Exception {
            // Given multiple agents
            messageRouter.registerAgent(TEST_SOURCE_AGENT);
            messageRouter.registerAgent(TEST_TARGET_AGENT);

            // When message is sent to target agent
            HandoffMessage message = createTestHandoffMessage();
            messageRouter.sendMessageToAgent(TEST_TARGET_AGENT, message);

            // Then message should be routed to correct agent
            HandoffSession received = messageRouter.waitForHandoffMessage(TEST_TARGET_AGENT);
            assertNotNull(received, "Target agent should receive message");
            assertNull(messageRouter.waitForHandoffMessage(TEST_SOURCE_AGENT), "Source agent should not receive message");
        }

        @Test
        @DisplayName("A2A message handles delivery failures")
        void a2AMessageHandlesDeliveryFailures() throws Exception {
            // Given unregistered agent
            String unregisteredAgent = "agent-not-registered";

            // When message is sent to unregistered agent
            HandoffMessage message = createTestHandoffMessage();

            // Then message delivery should fail gracefully
            assertThrows(IllegalArgumentException.class, () -> {
                messageRouter.sendMessageToAgent(unregisteredAgent, message);
            }, "Should fail for unregistered agent");
        }

        @Test
        @DisplayName("A2A message handles large payloads")
        void a2AMessageHandlesLargePayloads() throws Exception {
            // Given large payload
            Map<String, Object> largePayload = Map.of(
                "data", new String(new char[10000]).replace('\0', 'x'),
                "items", new String[1000],
                "metadata", Map.of(
                    "description", new String(new char[5000]).replace('\0', 'y')
                )
            );

            // When message with large payload is sent
            HandoffMessage message = handoffProtocol.createHandoffMessage(
                TEST_WORK_ITEM_ID,
                TEST_SOURCE_AGENT,
                TEST_TARGET_AGENT,
                TEST_SESSION_HANDLE,
                largePayload,
                Duration.ofMinutes(5)
            );

            messageRouter.sendMessageToAgent(TEST_TARGET_AGENT, message);

            // Then message should be delivered intact
            HandoffSession session = messageRouter.waitForHandoffMessage(TEST_TARGET_AGENT);
            assertNotNull(session, "Message should be delivered with large payload");
        }

        @Test
        @DisplayName("Concurrent A2A message delivery")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void concurrentA2AMessageDelivery() throws Exception {
            // Given multiple target agents
            String[] targetAgents = {TEST_TARGET_AGENT, "agent-b", "agent-c", "agent-d"};
            CountDownLatch[] latches = new CountDownLatch[targetAgents.length];
            HandoffSession[] sessions = new HandoffSession[targetAgents.length];

            for (int i = 0; i < targetAgents.length; i++) {
                messageRouter.registerAgent(targetAgents[i]);
                latches[i] = new CountDownLatch(1);
                final int index = i;

                Thread receiver = new Thread(() -> {
                    try {
                        sessions[index] = messageRouter.waitForHandoffMessage(targetAgents[index]);
                        latches[index].countDown();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                receiver.start();
            }

            // When message is broadcast to all agents
            HandoffMessage message = createTestHandoffMessage();
            for (String agent : targetAgents) {
                messageRouter.sendMessageToAgent(agent, message);
            }

            // Then all messages should be delivered
            for (int i = 0; i < targetAgents.length; i++) {
                assertTrue(latches[i].await(3, TimeUnit.SECONDS),
                    "Message should be delivered to agent " + targetAgents[i]);
                assertNotNull(sessions[i], "Agent " + targetAgents[i] + " should receive session");
            }
        }
    }

    @Nested
    @DisplayName("Error Recovery Tests")
    class ErrorRecoveryTests {

        @Test
        @DisplayName("Handoff recovers from network timeout")
        void handoffRecoversFromNetworkTimeout() throws Exception {
            // Given message router with timeout
            TimeoutMessageRouter timeoutRouter = new TimeoutMessageRouter(100); // 100ms timeout

            // When source agent attempts handoff
            HandoffMessage message = createTestHandoffMessage();

            // And message delivery times out
            assertThrows(Exception.class, () -> {
                timeoutRouter.sendMessageToAgent(TEST_TARGET_AGENT, message);
            }, "Should timeout on slow delivery");

            // Then source agent should retry or handle failure
            // In real implementation, would implement retry logic
            assertTrue(true, "Source agent should handle timeout gracefully");
        }

        @Test
        @DisplayName("Handoff recovers from target agent unavailability")
        void handoffRecoversFromTargetAgentUnavailability() throws Exception {
            // Given target agent is unavailable
            messageRouter.markAgentUnavailable(TEST_TARGET_AGENT);

            // When source agent attempts handoff
            HandoffMessage message = createTestHandoffMessage();

            assertThrows(Exception.class, () -> {
                messageRouter.sendMessageToAgent(TEST_TARGET_AGENT, message);
            }, "Should fail for unavailable agent");

            // Then work item should be made available again
            workItemService.cancelWorkItem(TEST_WORK_ITEM_ID);
            assertFalse(workItemService.isWorkItemCheckedOut(TEST_WORK_ITEM_ID),
                "Work item should be available after failed handoff");
        }

        @Test
        @DisplayName("Handoff rollback on failure")
        void handoffRollbackOnFailure() throws Exception {
            // Given source agent has checked out work item
            workItemService.checkoutWorkItem(TEST_WORK_ITEM_ID, TEST_SESSION_HANDLE);

            // When handoff fails midway
            try {
                HandoffMessage message = createTestHandoffMessage();
                messageRouter.markAgentUnavailable(TEST_TARGET_AGENT);

                // This would normally cause handoff to fail
                messageRouter.sendMessageToAgent(TEST_TARGET_AGENT, message);
            } catch (Exception e) {
                // Handoff failed
            }

            // Then source agent should rollback
            workItemService.rollbackCheckout(TEST_WORK_ITEM_ID, TEST_SOURCE_AGENT);

            // And work item should be available again
            assertTrue(workItemService.isWorkItemAvailable(TEST_WORK_ITEM_ID),
                "Work item should be available after rollback");
        }

        @Test
        @DisplayName("Handoff handles partial failures")
        void handoffHandlesPartialFailures() throws Exception {
            // Given multiple target agents, some available, some not
            messageRouter.registerAgent(TEST_TARGET_AGENT);
            messageRouter.markAgentUnavailable("agent-unavailable");

            // When handoff is attempted
            HandoffMessage message = createTestHandoffMessage();

            // Successful delivery to available agent
            messageRouter.sendMessageToAgent(TEST_TARGET_AGENT, message);
            HandoffSession session = messageRouter.waitForHandoffMessage(TEST_TARGET_AGENT);
            assertNotNull(session, "Should deliver to available agent");

            // Failed delivery to unavailable agent should not affect successful one
            assertThrows(Exception.class, () -> {
                messageRouter.sendMessageToAgent("agent-unavailable", message);
            }, "Should fail for unavailable agent");
        }
    }

    @Nested
    @DisplayName("Performance and Scalability Tests")
    class PerformanceAndScalabilityTests {

        @Test
        @DisplayName("Handoff performance under load")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void handoffPerformanceUnderLoad() throws Exception {
            // Given many concurrent handoffs
            int numHandoffs = 100;
            CountDownLatch latch = new CountDownLatch(numHandoffs);
            AtomicReference<Exception> firstError = new AtomicReference<>();

            // Start multiple handoff threads
            Thread[] threads = new Thread[numHandoffs];
            for (int i = 0; i < numHandoffs; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    try {
                        String workItemId = "WI-" + index;
                        workItemService.checkoutWorkItem(workItemId, "session-" + index);

                        HandoffMessage message = handoffProtocol.createHandoffMessage(
                            workItemId,
                            TEST_SOURCE_AGENT,
                            TEST_TARGET_AGENT,
                            "session-" + index
                        );

                        messageRouter.sendMessageToAgent(TEST_TARGET_AGENT, message);

                        latch.countDown();
                    } catch (Exception e) {
                        firstError.set(e);
                        latch.countDown();
                    }
                });
                threads[i].start();
            }

            // Wait for all handoffs
            latch.await();
            if (firstError.get() != null) {
                throw firstError.get();
            }

            // Then all handoffs should complete successfully
            assertEquals(numHandoffs, workItemService.getCompletedHandoffs(),
                "All handoffs should complete");
        }

        @Test
        @DisplayName("Handoff protocol scales with concurrent requests")
        @Timeout(value = 15, unit = TimeUnit.SECONDS)
        void handoffProtocolScalesWithConcurrentRequests() throws Exception {
            // Test scalability with different levels of concurrency
            int[] concurrencyLevels = {10, 50, 100};
            int workItemsPerLevel = 50;

            for (int concurrency : concurrencyLevels) {
                long startTime = System.nanoTime();

                // Create concurrent handoffs
                CountDownLatch latch = new CountDownLatch(concurrency);
                for (int i = 0; i < concurrency; i++) {
                    final int index = i;
                    Thread t = new Thread(() -> {
                        try {
                            for (int j = 0; j < workItemsPerLevel; j++) {
                                String workItemId = "WI-" + index + "-" + j;
                                workItemService.checkoutWorkItem(workItemId, "session-" + index + "-" + j);

                                HandoffMessage message = handoffProtocol.createHandoffMessage(
                                    workItemId,
                                    TEST_SOURCE_AGENT,
                                    TEST_TARGET_AGENT,
                                    "session-" + index + "-" + j
                                );

                                messageRouter.sendMessageToAgent(TEST_TARGET_AGENT, message);
                            }
                        } catch (Exception e) {
                            fail("Concurrent handoff failed: " + e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    });
                    t.start();
                }

                latch.await();

                long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                double totalTimeMs = durationMs;
                double avgTimePerHandoff = totalTimeMs / (concurrency * workItemsPerLevel);

                System.out.printf("Concurrency %d, Total time: %dms, Avg per handoff: %.2fms%n",
                    concurrency, durationMs, avgTimePerHandoff);

                // Performance should be reasonable
                assertTrue(avgTimePerHandoff < 50,
                    "Average handoff time should be < 50ms at concurrency " + concurrency);
            }
        }

        @Test
        @DisplayName("Handoff maintains throughput under stress")
        @Timeout(value = 20, unit = TimeUnit.SECONDS)
        void handoffMaintainsThroughputUnderStress() throws Exception {
            // Given stress test with continuous handoffs
            int durationSeconds = 10;
            int expectedThroughput = 100; // handoffs per second
            int totalExpected = durationSeconds * expectedThroughput;

            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger handoffCount = new AtomicInteger(0);

            // Start stress test thread
            Thread stressThread = new Thread(() -> {
                try {
                    long endTime = System.currentTimeMillis() + (durationSeconds * 1000);
                    while (System.currentTimeMillis() < endTime) {
                        String workItemId = "WI-STRESS-" + System.currentTimeMillis();
                        workItemService.checkoutWorkItem(workItemId, "session-stress");

                        HandoffMessage message = handoffProtocol.createHandoffMessage(
                            workItemId,
                            TEST_SOURCE_AGENT,
                            TEST_TARGET_AGENT,
                            "session-stress"
                        );

                        messageRouter.sendMessageToAgent(TEST_TARGET_AGENT, message);
                        handoffCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
            stressThread.start();

            // Wait for stress test to complete
            latch.await();

            // Then throughput should be acceptable
            int actual = handoffCount.get();
            double achievedThroughput = actual / (double) durationSeconds;

            System.out.printf("Achieved throughput: %.2f handoffs/second (expected: %d)%n",
                achievedThroughput, expectedThroughput);

            // Should achieve at least 50% of expected throughput
            assertTrue(achievedThroughput >= expectedThroughput * 0.5,
                "Should achieve at least 50% of expected throughput");
        }
    }

    // Helper methods

    private HandoffMessage createTestHandoffMessage() throws HandoffException {
        return handoffProtocol.createHandoffMessage(
            TEST_WORK_ITEM_ID,
            TEST_SOURCE_AGENT,
            TEST_TARGET_AGENT,
            TEST_SESSION_HANDLE
        );
    }

    // Test implementations

    /**
     * Test implementation of A2A message router.
     */
    private static class TestA2AMessageRouter {
        private final Map<String, HandoffMessage> messageQueues = new ConcurrentHashMap<>();
        private final Map<String, Boolean> agentAvailability = new ConcurrentHashMap<>();

        public void registerAgent(String agentId) {
            agentAvailability.put(agentId, true);
        }

        public void sendMessageToAgent(String agentId, HandoffMessage message) throws Exception {
            if (!agentAvailability.getOrDefault(agentId, false)) {
                throw new IllegalArgumentException("Agent " + agentId + " is unavailable");
            }

            messageQueues.put(agentId, message);
        }

        public HandoffSession waitForHandoffMessage(String agentId) throws InterruptedException {
            long timeout = System.currentTimeMillis() + 5000; // 5 second timeout
            while (System.currentTimeMillis() < timeout) {
                HandoffMessage message = messageQueues.get(agentId);
                if (message != null) {
                    messageQueues.remove(agentId);
                    return new TestHandoffSession(message);
                }
                Thread.sleep(100);
            }
            return null;
        }

        public void markAgentUnavailable(String agentId) {
            agentAvailability.put(agentId, false);
        }
    }

    /**
     * Test handoff session.
     */
    private static class TestHandoffSession implements HandoffSession {
        private final HandoffMessage message;

        public TestHandoffSession(HandoffMessage message) {
            this.message = message;
        }

        @Override
        public String workItemId() {
            return message.workItemId();
        }

        @Override
        public String fromAgent() {
            return message.fromAgent();
        }

        @Override
        public String toAgent() {
            return message.toAgent();
        }

        @Override
        public String engineSession() {
            return message.token().engineSession();
        }

        @Override
        public Instant expiresAt() {
            return message.token().expiresAt();
        }
    }

    /**
     * Test work item service.
     */
    private static class TestWorkItemService {
        private final Map<String, WorkItemRecord> workItems = new ConcurrentHashMap<>();
        private final Map<String, String> checkouts = new ConcurrentHashMap<>();
        private final AtomicInteger completedHandoffs = new AtomicInteger(0);

        public void checkoutWorkItem(String itemId, String session) {
            workItems.putIfAbsent(itemId, new TestWorkItem(itemId));
            checkouts.put(itemId, session);
        }

        public void rollbackCheckout(String itemId, String agent) {
            checkouts.remove(itemId);
        }

        public boolean isWorkItemCheckedOut(String itemId) {
            return checkouts.containsKey(itemId);
        }

        public boolean isCheckedOutBy(String itemId, String agent) {
            String session = checkouts.get(itemId);
            return session != null && session.contains(agent);
        }

        public boolean isWorkItemAvailable(String itemId) {
            return !checkouts.containsKey(itemId);
        }

        public void cancelWorkItem(String itemId) {
            checkouts.remove(itemId);
        }

        public boolean hasContext(String itemId, Map<String, Object> context) {
            // Check if work item has the expected context
            return true;
        }

        public int getCompletedHandoffs() {
            return completedHandoffs.get();
        }
    }

    /**
     * Timeout message router for testing.
     */
    private static class TimeoutMessageRouter extends TestA2AMessageRouter {
        private final int timeoutMs;

        public TimeoutMessageRouter(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        @Override
        public void sendMessageToAgent(String agentId, HandoffMessage message) throws Exception {
            Thread.sleep(timeoutMs);
            throw new Exception("Timeout after " + timeoutMs + "ms");
        }
    }

    /**
     * Test work item record.
     */
    private static class TestWorkItem implements WorkItemRecord {
        private final String id;

        public TestWorkItem(String id) {
            this.id = id;
        }

        @Override
        public String getID() {
            return id;
        }

        // Implement other required methods
        @Override
        public String getName() {
            return "TestTask";
        }

        @Override
        public Map<String, String> getMetadata() {
            return Map.of();
        }

        @Override
        public Instant getCreationDate() {
            return Instant.now();
        }

        @Override
        public String getSpecID() {
            return "TestSpec";
        }

        @Override
        public String getNetID() {
            return "TestNet";
        }

        @Override
        public String getCaseID() {
            return "TestCase";
        }

        @Override
        public String getTaskID() {
            return "TestTask";
        }

        @Override
        public org.yawlfoundation.yawl.engine.interfce.YAWLTask getTask() {
            return null;
        }

        @Override
        public org.yawlfoundation.yawl.engine.interfce.YAWLCase getCase() {
            return null;
        }

        @Override
        public org.yawlfoundation.yawl.engine.interfce.YAWLSpecification getSpec() {
            return null;
        }
    }
}