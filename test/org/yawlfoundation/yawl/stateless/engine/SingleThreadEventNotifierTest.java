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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stateless.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;
import org.yawlfoundation.yawl.stateless.elements.YTask;
import org.yawlfoundation.yawl.stateless.listener.YEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;

/**
 * Comprehensive tests for SingleThreadEventNotifier class.
 *
 * <p>Chicago TDD: Tests use real SingleThreadEventNotifier instances, real YTask objects,
 * and real event listeners. No mocks for domain objects.</p>
 *
 * <p>This test focuses on the single-threaded behavior of the notifier.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("SingleThreadEventNotifier Tests")
@Tag("unit")
class SingleThreadEventNotifierTest {

    private SingleThreadEventNotifier eventNotifier;
    private TestListener listener;
    private static final String TEST_CASE_ID = "test-case-123";
    private static final String TEST_TASK_ID = "task-456";

    @BeforeEach
    void setUp() {
        eventNotifier = new SingleThreadEventNotifier();
        listener = new TestListener();
        eventNotifier.addYEventListener(listener);
    }

    // ==================== Basic Tests ====================

    @Test
    @DisplayName("Single thread event notifier initialization")
    void singleThreadEventNotifierInitialization() {
        // Assert
        assertNotNull(eventNotifier);
        // Initial state should have no listeners
        assertTrue(eventNotifier.getYEventListeners().isEmpty());
    }

    @Test
    @DisplayName("Add event listener")
    void addEventListener() {
        // Arrange
        TestListener newListener = new TestListener();

        // Act
        eventNotifier.addYEventListener(newListener);

        // Assert
        assertTrue(eventNotifier.getYEventListeners().contains(newListener));
    }

    @Test
    @DisplayName("Add null event listener")
    void addNullEventListener() {
        // Act & Assert
        assertDoesNotThrow(() -> eventNotifier.addYEventListener(null));
    }

    @Test
    @DisplayName("Remove event listener")
    void removeEventListener() {
        // Arrange
        TestListener newListener = new TestListener();
        eventNotifier.addYEventListener(newListener);

        // Act
        eventNotifier.removeYEventListener(newListener);

        // Assert
        assertFalse(eventNotifier.getYEventListeners().contains(newListener));
    }

    @Test
    @DisplayName("Remove null event listener")
    void removeNullEventListener() {
        // Act & Assert
        assertDoesNotThrow(() -> eventNotifier.removeYEventListener(null));
    }

    @Test
    @DisplayName("Remove non-existent event listener")
    void removeNonExistentEventListener() {
        // Arrange
        TestListener nonExistentListener = new TestListener();

        // Act & Assert
        assertDoesNotThrow(() -> eventNotifier.removeYEventListener(nonExistentListener));
    }

    // ==================== Single Thread Behavior Tests ====================

    @Test
    @DisplayName("Single threaded event delivery")
    void singleThreadedEventDelivery() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        YTask task = createMockTask(TEST_TASK_ID);
        YWorkItem workItem = createMockWorkItem(task);
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        // All events should be processed in the same thread
        eventNotifier.notifyWorkItemEnabled(workItem, caseId);
        eventNotifier.notifyWorkItemStarted(workItem, caseId);
        eventNotifier.notifyWorkItemCompleted(workItem, caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.WorkItemEnabled, listener.getLastEvent().getType());
        assertEquals(3, listener.getEventCount());
    }

    @Test
    @DisplayName("Sequential event processing")
    void sequentialEventProcessing() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(3);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        YTask task = createMockTask(TEST_TASK_ID);
        YWorkItem workItem = createMockWorkItem(task);
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        // Act
        eventNotifier.notifyWorkItemEnabled(workItem, caseId);
        eventNotifier.notifyWorkItemStarted(workItem, caseId);
        eventNotifier.notifyWorkItemCompleted(workItem, caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));

        // Check that all events were received
        List<YEvent> events = listener.getEvents();
        assertEquals(3, events.size());

        // Check event order
        assertEquals(YEventType.WorkItemEnabled, events.get(0).getType());
        assertEquals(YEventType.WorkItemStarted, events.get(1).getType());
        assertEquals(YEventType.WorkItemCompleted, events.get(2).getType());
    }

    // ==================== Work Item Event Tests ====================

    @Test
    @DisplayName("Notify work item enabled - single thread")
    void notifyWorkItemEnabled_singleThread() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        YTask task = createMockTask(TEST_TASK_ID);
        YWorkItem workItem = createMockWorkItem(task);
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        eventNotifier.notifyWorkItemEnabled(workItem, caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.WorkItemEnabled, listener.getLastEvent().getType());

        // Verify thread information if available
        YWorkItemEvent workEvent = (YWorkItemEvent) listener.getLastEvent();
        assertNotNull(workEvent.getWorkItem());
        assertEquals(workItem, workEvent.getWorkItem());
    }

    @Test
    @DisplayName("Notify work item started - single thread")
    void notifyWorkItemStarted_singleThread() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        YTask task = createMockTask(TEST_TASK_ID);
        YWorkItem workItem = createMockWorkItem(task);
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        eventNotifier.notifyWorkItemStarted(workItem, caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.WorkItemStarted, listener.getLastEvent().getType());
    }

    @Test
    @DisplayName("Notify work item completed - single thread")
    void notifyWorkItemCompleted_singleThread() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        YTask task = createMockTask(TEST_TASK_ID);
        YWorkItem workItem = createMockWorkItem(task);
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        eventNotifier.notifyWorkItemCompleted(workItem, caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.WorkItemCompleted, listener.getLastEvent().getType());
    }

    @Test
    @DisplayName("Notify work item cancelled - single thread")
    void notifyWorkItemCancelled_singleThread() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        YTask task = createMockTask(TEST_TASK_ID);
        YWorkItem workItem = createMockWorkItem(task);
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        eventNotifier.notifyWorkItemCancelled(workItem, caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.WorkItemCancelled, listener.getLastEvent().getType());
    }

    @Test
    @DisplayName("Notify work item failed - single thread")
    void notifyWorkItemFailed_singleThread() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        YTask task = createMockTask(TEST_TASK_ID);
        YWorkItem workItem = createMockWorkItem(task);
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        eventNotifier.notifyWorkItemFailed(workItem, caseId, "Test failure");

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.WorkItemFailed, listener.getLastEvent().getType());
    }

    // ==================== Case Event Tests ====================

    @Test
    @DisplayName("Notify case started - single thread")
    void notifyCaseStarted_singleThread() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        eventNotifier.notifyCaseStarted(caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.CaseStarted, listener.getLastEvent().getType());
        assertEquals(caseId, listener.getLastEvent().getSource());
    }

    @Test
    @DisplayName("Notify case completed - single thread")
    void notifyCaseCompleted_singleThread() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        eventNotifier.notifyCaseCompleted(caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.CaseCompleted, listener.getLastEvent().getType());
    }

    @Test
    @DisplayName("Notify case cancelled - single thread")
    void notifyCaseCancelled_singleThread() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        eventNotifier.notifyCaseCancelled(caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.CaseCancelled, listener.getLastEvent().getType());
    }

    // ==================== Timer Event Tests ====================

    @Test
    @DisplayName("Notify timer started - single thread")
    void notifyTimerStarted_singleThread() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        eventNotifier.notifyTimerStarted(caseId, "timer1");

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.TimerStarted, listener.getLastEvent().getType());
    }

    @Test
    @DisplayName("Notify timer expired - single thread")
    void notifyTimerExpired_singleThread() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        eventNotifier.notifyTimerExpired(caseId, "timer1");

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.TimerExpired, listener.getLastEvent().getType());
    }

    @Test
    @DisplayName("Notify timer cancelled - single thread")
    void notifyTimerCancelled_singleThread() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        eventNotifier.notifyTimerCancelled(caseId, "timer1");

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.TimerCancelled, listener.getLastEvent().getType());
    }

    // ==================== Exception Event Tests ====================

    @Test
    @DisplayName("Notify exception - single thread")
    void notifyException_singleThread() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);
        RuntimeException exception = new RuntimeException("Test exception");

        eventNotifier.notifyException(caseId, "Test exception message", exception);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.Exception, listener.getLastEvent().getType());
    }

    // ==================== Null Parameter Tests ====================

    @Test
    @DisplayName("Notify with null work item - single thread")
    void notifyWithNullWorkItem_singleThread() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        eventNotifier.notifyWorkItemEnabled(null, caseId);

        // Assert
        // Should handle null work item gracefully
        latch.await(1, TimeUnit.SECONDS); // Just check it doesn't throw
    }

    @Test
    @DisplayName("Notify with null case ID - single thread")
    void notifyWithNullCaseId_singleThread() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        YTask task = createMockTask(TEST_TASK_ID);
        YWorkItem workItem = createMockWorkItem(task);

        eventNotifier.notifyWorkItemEnabled(workItem, null);

        // Assert
        // Should handle null case ID gracefully
        latch.await(1, TimeUnit.SECONDS); // Just check it doesn't throw
    }

    // ==================== Concurrency Tests ====================

    @Test
    @DisplayName("Single thread safety")
    void singleThreadSafety() throws InterruptedException {
        // Arrange
        int eventCount = 50;
        CountDownLatch latch = new CountDownLatch(eventCount);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        for (int i = 0; i < eventCount; i++) {
            YTask task = createMockTask("task-" + i);
            YWorkItem workItem = createMockWorkItem(task);
            YIdentifier caseId = new YIdentifier("case-" + i);

            eventNotifier.notifyWorkItemEnabled(workItem, caseId);
        }

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(eventCount, latchListener.getEventCount());
    }

    @Test
    @DisplayName("Thread listener addition/removal during event notification")
    void threadListenerAdditionRemovalDuringNotification() throws InterruptedException {
        // Arrange
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(1);

        // Listener that adds/removes other listeners
        TestListener modifyingListener = new TestListener() {
            @Override
            public void eventOccurred(YEvent event) {
                super.eventOccurred(event);
                try {
                    startLatch.countDown();
                    endLatch.await(1, TimeUnit.SECONDS); // Wait while processing

                    // Add and remove listeners during event processing
                    TestListener tempListener = new TestListener();
                    eventNotifier.addYEventListener(tempListener);
                    eventNotifier.removeYEventListener(tempListener);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        eventNotifier.addYEventListener(modifyingListener);

        // Act
        Thread notificationThread = new Thread(() -> {
            YTask task = createMockTask(TEST_TASK_ID);
            YWorkItem workItem = createMockWorkItem(task);
            YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

            try {
                startLatch.await(); // Wait for the modifying listener to start
                for (int i = 0; i < 10; i++) {
                    eventNotifier.notifyWorkItemEnabled(workItem, caseId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        notificationThread.start();

        // Assert
        // Should handle concurrent modification gracefully
        assertTrue(endLatch.await(2, TimeUnit.SECONDS));
        notificationThread.join();
    }

    // ==================== Error Handling Tests ====================

    @Test
    @DisplayName("Handle listener exception - single thread")
    void handleListenerException_singleThread() throws Exception {
        // Arrange
        ThrowingListener throwingListener = new ThrowingListener();
        eventNotifier.addYEventListener(throwingListener);

        TestListener normalListener = new TestListener();
        eventNotifier.addYEventListener(normalListener);

        // Act
        YTask task = createMockTask(TEST_TASK_ID);
        YWorkItem workItem = createMockWorkItem(task);
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        eventNotifier.notifyWorkItemEnabled(workItem, caseId);

        // Assert
        // Other listeners should still receive the event
        assertEquals(1, normalListener.getEventCount());
    }

    // ==================== Helper Methods ====================

    private YTask createMockTask(String taskId) {
        // Create a mock YTask for testing
        YTask task = new YTask();
        task.setID(taskId);
        task.setName("Mock Task");
        return task;
    }

    private YWorkItem createMockWorkItem(YTask task) {
        // Create a mock YWorkItem for testing
        YWorkItem workItem = new YWorkItem();
        workItem.setCaseID(TEST_CASE_ID);
        workItem.setTask(task);
        workItem.setID(new YWorkItemID(TEST_CASE_ID, task.getID()));
        return workItem;
    }

    // ==================== Test Listener Classes ====================

    private static class TestListener implements YEventListener {
        private YEvent lastEvent;
        private List<YEvent> events = Collections.synchronizedList(new ArrayList<>());
        private int eventCount;
        private CountDownLatch latch;

        public TestListener() {
            this.latch = null;
            this.eventCount = 0;
        }

        public TestListener(CountDownLatch latch) {
            this.latch = latch;
            this.eventCount = 0;
        }

        @Override
        public void eventOccurred(YEvent event) {
            this.lastEvent = event;
            this.events.add(event);
            this.eventCount++;
            if (latch != null) {
                latch.countDown();
            }
        }

        public YEvent getLastEvent() {
            return lastEvent;
        }

        public List<YEvent> getEvents() {
            return events;
        }

        public int getEventCount() {
            return eventCount;
        }
    }

    private static class ThrowingListener implements YEventListener {
        @Override
        public void eventOccurred(YEvent event) {
            throw new RuntimeException("Listener exception");
        }
    }
}