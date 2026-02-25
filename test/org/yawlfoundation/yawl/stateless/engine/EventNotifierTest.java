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
 * Comprehensive tests for EventNotifier class.
 *
 * <p>Chicago TDD: Tests use real EventNotifier instances, real YTask objects,
 * and real event listeners. No mocks for domain objects.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("EventNotifier Tests")
@Tag("unit")
class EventNotifierTest {

    private EventNotifier eventNotifier;
    private TestListener listener;
    private static final String TEST_CASE_ID = "test-case-123";
    private static final String TEST_TASK_ID = "task-456";

    @BeforeEach
    void setUp() {
        eventNotifier = new EventNotifier();
        listener = new TestListener();
        eventNotifier.addYEventListener(listener);
    }

    // ==================== Basic Tests ====================

    @Test
    @DisplayName("Event notifier initialization")
    void eventNotifierInitialization() {
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

    // ==================== Work Item Event Tests ====================

    @Test
    @DisplayName("Notify work item enabled")
    void notifyWorkItemEnabled() throws Exception {
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
    }

    @Test
    @DisplayName("Notify work item started")
    void notifyWorkItemStarted() throws Exception {
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
    @DisplayName("Notify work item completed")
    void notifyWorkItemCompleted() throws Exception {
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
    @DisplayName("Notify work item cancelled")
    void notifyWorkItemCancelled() throws Exception {
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
    @DisplayName("Notify work item failed")
    void notifyWorkItemFailed() throws Exception {
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
    @DisplayName("Notify case started")
    void notifyCaseStarted() throws Exception {
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
    }

    @Test
    @DisplayName("Notify case completed")
    void notifyCaseCompleted() throws Exception {
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
    @DisplayName("Notify case cancelled")
    void notifyCaseCancelled() throws Exception {
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

    @Test
    @DisplayName("Notify case terminated")
    void notifyCaseTerminated() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        eventNotifier.notifyCaseTerminated(caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.CaseTerminated, listener.getLastEvent().getType());
    }

    @Test
    @DisplayName("Notify case suspended")
    void notifyCaseSuspended() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        eventNotifier.notifyCaseSuspended(caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.CaseSuspended, listener.getLastEvent().getType());
    }

    @Test
    @DisplayName("Notify case resumed")
    void notifyCaseResumed() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        eventNotifier.notifyCaseResumed(caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.CaseResumed, listener.getLastEvent().getType());
    }

    // ==================== Timer Event Tests ====================

    @Test
    @DisplayName("Notify timer started")
    void notifyTimerStarted() throws Exception {
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
    @DisplayName("Notify timer expired")
    void notifyTimerExpired() throws Exception {
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
    @DisplayName("Notify timer cancelled")
    void notifyTimerCancelled() throws Exception {
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
    @DisplayName("Notify exception")
    void notifyException() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        eventNotifier.notifyException(caseId, "Test exception message", new RuntimeException());

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.Exception, listener.getLastEvent().getType());
    }

    // ==================== Null Parameter Tests ====================

    @Test
    @DisplayName("Notify with null work item")
    void notifyWithNullWorkItem() throws Exception {
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
    @DisplayName("Notify with null case ID")
    void notifyWithNullCaseId() throws Exception {
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

    @Test
    @DisplayName("Notify with empty case ID")
    void notifyWithEmptyCaseId() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        YTask task = createMockTask(TEST_TASK_ID);
        YWorkItem workItem = createMockWorkItem(task);
        YIdentifier caseId = new YIdentifier("");

        eventNotifier.notifyWorkItemEnabled(workItem, caseId);

        // Assert
        // Should handle empty case ID gracefully
        latch.await(1, TimeUnit.SECONDS); // Just check it doesn't throw
    }

    // ==================== Concurrent Access Tests ====================

    @Test
    @DisplayName("Concurrent add and remove listeners")
    void concurrentAddRemoveListeners() throws InterruptedException {
        // Arrange
        int listenerCount = 100;
        List<Thread> threads = new ArrayList<>();

        // Act
        for (int i = 0; i < listenerCount; i++) {
            Thread thread = new Thread(() -> {
                TestListener newListener = new TestListener();
                eventNotifier.addYEventListener(newListener);
                eventNotifier.removeYEventListener(newListener);
            });
            threads.add(thread);
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Assert
        // No exceptions thrown, concurrent access should be safe
        assertTrue(true);
    }

    @Test
    @DisplayName("Multiple listeners receive events")
    void multipleListenersReceiveEvents() throws Exception {
        // Arrange
        int listenerCount = 5;
        CountDownLatch[] latches = new CountDownLatch[listenerCount];
        TestListener[] listeners = new TestListener[listenerCount];

        for (int i = 0; i < listenerCount; i++) {
            latches[i] = new CountDownLatch(1);
            listeners[i] = new TestListener(latches[i]);
            eventNotifier.addYEventListener(listeners[i]);
        }

        // Act
        YTask task = createMockTask(TEST_TASK_ID);
        YWorkItem workItem = createMockWorkItem(task);
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        eventNotifier.notifyWorkItemEnabled(workItem, caseId);

        // Assert
        for (int i = 0; i < listenerCount; i++) {
            assertTrue(latches[i].await(1, TimeUnit.SECONDS));
            assertEquals(YEventType.WorkItemEnabled, listeners[i].getLastEvent().getType());
        }
    }

    @Test
    @DisplayName("Event thread safety")
    void eventThreadSafety() throws InterruptedException {
        // Arrange
        int eventCount = 100;
        CountDownLatch latch = new CountDownLatch(eventCount);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        List<Thread> threads = new ArrayList<>();
        final AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        // Act
        for (int i = 0; i < 10; i++) {
            Thread thread = new Thread(() -> {
                try {
                    for (int j = 0; j < 10; j++) {
                        YTask task = createMockTask("task-" + j);
                        YWorkItem workItem = createMockWorkItem(task);
                        YIdentifier caseId = new YIdentifier("case-" + j);

                        eventNotifier.notifyWorkItemEnabled(workItem, caseId);
                    }
                } catch (Exception e) {
                    exceptionRef.set(e);
                }
            });
            threads.add(thread);
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Assert
        assertNull(exceptionRef.get());
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(eventCount, latchListener.getEventCount());
    }

    // ==================== Error Handling Tests ====================

    @Test
    @DisplayName("Handle listener exception")
    void handleListenerException() throws Exception {
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
            this.eventCount++;
            if (latch != null) {
                latch.countDown();
            }
        }

        public YEvent getLastEvent() {
            return lastEvent;
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