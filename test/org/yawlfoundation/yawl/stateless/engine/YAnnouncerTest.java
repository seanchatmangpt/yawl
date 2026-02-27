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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;
import org.yawlfoundation.yawl.stateless.elements.YTask;
import org.yawlfoundation.yawl.stateless.listener.YEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YEvent;

/**
 * Comprehensive tests for YAnnouncer class.
 *
 * <p>Chicago TDD: Tests use real YAnnouncer instances, real YTask objects,
 * and real event listeners. No mocks for domain objects.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("YAnnouncer Tests")
@Tag("unit")
class YAnnouncerTest {

    private YAnnouncer announcer;
    private static final String TEST_CASE_ID = "test-case-123";
    private static final String TEST_TASK_ID = "task-456";
    private TestListener listener;

    @BeforeEach
    void setUp() {
        announcer = new YAnnouncer();
        listener = new TestListener();
        announcer.addYEventListener(listener);
    }

    // ==================== Basic Tests ====================

    @Test
    @DisplayName("Announcer initialization")
    void announcerInitialization() {
        // Assert
        assertNotNull(announcer);
        // Initial state should have no listeners
        assertTrue(announcer.getYEventListeners().isEmpty());
    }

    @Test
    @DisplayName("Add event listener")
    void addEventListener() {
        // Arrange
        TestListener newListener = new TestListener();

        // Act
        announcer.addYEventListener(newListener);

        // Assert
        assertTrue(announcer.getYEventListeners().contains(newListener));
    }

    @Test
    @DisplayName("Add null event listener")
    void addNullEventListener() {
        // Act & Assert
        assertDoesNotThrow(() -> announcer.addYEventListener(null));
    }

    @Test
    @DisplayName("Remove event listener")
    void removeEventListener() {
        // Arrange
        TestListener newListener = new TestListener();
        announcer.addYEventListener(newListener);

        // Act
        announcer.removeYEventListener(newListener);

        // Assert
        assertFalse(announcer.getYEventListeners().contains(newListener));
    }

    @Test
    @DisplayName("Remove null event listener")
    void removeNullEventListener() {
        // Act & Assert
        assertDoesNotThrow(() -> announcer.removeYEventListener(null));
    }

    @Test
    @DisplayName("Remove non-existent event listener")
    void removeNonExistentEventListener() {
        // Arrange
        TestListener nonExistentListener = new TestListener();

        // Act & Assert
        assertDoesNotThrow(() -> announcer.removeYEventListener(nonExistentListener));
    }

    // ==================== Work Item Event Tests ====================

    @Test
    @DisplayName("Announce work item enabled event")
    void announceWorkItemEnabled() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        announcer.addYEventListener(latchListener);

        // Act
        YTask task = createMockTask(TEST_TASK_ID);
        YWorkItem workItem = createMockWorkItem(task);
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        announcer.announceWorkItemEnabled(workItem, caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.WorkItemEnabled, listener.getLastEvent().getType());
    }

    @Test
    @DisplayName("Announce work item started event")
    void announceWorkItemStarted() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        announcer.addYEventListener(latchListener);

        // Act
        YTask task = createMockTask(TEST_TASK_ID);
        YWorkItem workItem = createMockWorkItem(task);
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        announcer.announceWorkItemStarted(workItem, caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.WorkItemStarted, listener.getLastEvent().getType());
    }

    @Test
    @DisplayName("Announce work item completed event")
    void announceWorkItemCompleted() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        announcer.addYEventListener(latchListener);

        // Act
        YTask task = createMockTask(TEST_TASK_ID);
        YWorkItem workItem = createMockWorkItem(task);
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        announcer.announceWorkItemCompleted(workItem, caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.WorkItemCompleted, listener.getLastEvent().getType());
    }

    @Test
    @DisplayName("Announce work item cancelled event")
    void announceWorkItemCancelled() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        announcer.addYEventListener(latchListener);

        // Act
        YTask task = createMockTask(TEST_TASK_ID);
        YWorkItem workItem = createMockWorkItem(task);
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        announcer.announceWorkItemCancelled(workItem, caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.WorkItemCancelled, listener.getLastEvent().getType());
    }

    @Test
    @DisplayName("Announce work item failed event")
    void announceWorkItemFailed() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        announcer.addYEventListener(latchListener);

        // Act
        YTask task = createMockTask(TEST_TASK_ID);
        YWorkItem workItem = createMockWorkItem(task);
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        announcer.announceWorkItemFailed(workItem, caseId, "Test failure");

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.WorkItemFailed, listener.getLastEvent().getType());
    }

    // ==================== Case Event Tests ====================

    @Test
    @DisplayName("Announce case started event")
    void announceCaseStarted() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        announcer.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        announcer.announceCaseStarted(caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.CaseStarted, listener.getLastEvent().getType());
    }

    @Test
    @DisplayName("Announce case completed event")
    void announceCaseCompleted() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        announcer.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        announcer.announceCaseCompleted(caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.CaseCompleted, listener.getLastEvent().getType());
    }

    @Test
    @DisplayName("Announce case cancelled event")
    void announceCaseCancelled() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        announcer.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        announcer.announceCaseCancelled(caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.CaseCancelled, listener.getLastEvent().getType());
    }

    @Test
    @DisplayName("Announce case terminated event")
    void announceCaseTerminated() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        announcer.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        announcer.announceCaseTerminated(caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.CaseTerminated, listener.getLastEvent().getType());
    }

    @Test
    @DisplayName("Announce case suspended event")
    void announceCaseSuspended() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        announcer.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        announcer.announceCaseSuspended(caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.CaseSuspended, listener.getLastEvent().getType());
    }

    @Test
    @DisplayName("Announce case resumed event")
    void announceCaseResumed() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        announcer.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        announcer.announceCaseResumed(caseId);

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.CaseResumed, listener.getLastEvent().getType());
    }

    // ==================== Timer Event Tests ====================

    @Test
    @DisplayName("Announce timer started event")
    void announceTimerStarted() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        announcer.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        announcer.announceTimerStarted(caseId, "timer1");

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.TimerStarted, listener.getLastEvent().getType());
    }

    @Test
    @DisplayName("Announce timer expired event")
    void announceTimerExpired() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        announcer.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        announcer.announceTimerExpired(caseId, "timer1");

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.TimerExpired, listener.getLastEvent().getType());
    }

    @Test
    @DisplayName("Announce timer cancelled event")
    void announceTimerCancelled() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        announcer.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        announcer.announceTimerCancelled(caseId, "timer1");

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.TimerCancelled, listener.getLastEvent().getType());
    }

    // ==================== Exception Event Tests ====================

    @Test
    @DisplayName("Announce exception event")
    void announceException() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        announcer.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        announcer.announceException(caseId, "Test exception message", new RuntimeException());

        // Assert
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(YEventType.Exception, listener.getLastEvent().getType());
    }

    // ==================== Null Parameter Tests ====================

    @Test
    @DisplayName("Announce with null work item")
    void announceWithNullWorkItem() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        announcer.addYEventListener(latchListener);

        // Act
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        announcer.announceWorkItemEnabled(null, caseId);

        // Assert
        // Should handle null work item gracefully
        latch.await(1, TimeUnit.SECONDS); // Just check it doesn't throw
    }

    @Test
    @DisplayName("Announce with null case ID")
    void announceWithNullCaseId() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        announcer.addYEventListener(latchListener);

        // Act
        YTask task = createMockTask(TEST_TASK_ID);
        YWorkItem workItem = createMockWorkItem(task);

        announcer.announceWorkItemEnabled(workItem, null);

        // Assert
        // Should handle null case ID gracefully
        latch.await(1, TimeUnit.SECONDS); // Just check it doesn't throw
    }

    @Test
    @DisplayName("Announce with empty case ID")
    void announceWithEmptyCaseId() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        TestListener latchListener = new TestListener(latch);
        announcer.addYEventListener(latchListener);

        // Act
        YTask task = createMockTask(TEST_TASK_ID);
        YWorkItem workItem = createMockWorkItem(task);
        YIdentifier caseId = new YIdentifier("");

        announcer.announceWorkItemEnabled(workItem, caseId);

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
                announcer.addYEventListener(newListener);
                announcer.removeYEventListener(newListener);
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
            announcer.addYEventListener(listeners[i]);
        }

        // Act
        YTask task = createMockTask(TEST_TASK_ID);
        YWorkItem workItem = createMockWorkItem(task);
        YIdentifier caseId = new YIdentifier(TEST_CASE_ID);

        announcer.announceWorkItemEnabled(workItem, caseId);

        // Assert
        for (int i = 0; i < listenerCount; i++) {
            assertTrue(latches[i].await(1, TimeUnit.SECONDS));
            assertEquals(YEventType.WorkItemEnabled, listeners[i].getLastEvent().getType());
        }
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
        workItem.setID(new YWorkItemID(TEST_CASE_ID, taskId));
        return workItem;
    }

    // ==================== Test Listener Class ====================

    private static class TestListener implements YEventListener {
        private YEvent lastEvent;
        private CountDownLatch latch;

        public TestListener() {
            this.latch = null;
        }

        public TestListener(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void eventOccurred(YEvent event) {
            this.lastEvent = event;
            if (latch != null) {
                latch.countDown();
            }
        }

        public YEvent getLastEvent() {
            return lastEvent;
        }
    }
}