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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;

/**
 * Comprehensive tests for MultiThreadEventNotifier class.
 *
 * <p>Chicago TDD: Tests use real MultiThreadEventNotifier instances, real YTask objects,
 * and real event listeners. No mocks for domain objects.</p>
 *
 * <p>This test focuses on the multi-threaded behavior of the notifier.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("MultiThreadEventNotifier Tests")
@Tag("unit")
class MultiThreadEventNotifierTest {

    private MultiThreadEventNotifier eventNotifier;
    private TestListener listener;
    private static final String TEST_CASE_ID = "test-case-123";
    private static final String TEST_TASK_ID = "task-456";
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        eventNotifier = new MultiThreadEventNotifier();
        listener = new TestListener();
        eventNotifier.addYEventListener(listener);
        executorService = Executors.newFixedThreadPool(10);
    }

    @AfterEach
    void tearDown() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    // ==================== Basic Tests ====================

    @Test
    @DisplayName("Multi thread event notifier initialization")
    void multiThreadEventNotifierInitialization() {
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

    // ==================== Multi-threaded Event Delivery Tests ====================

    @Test
    @DisplayName("Multiple threads delivering events")
    void multipleThreadsDeliveringEvents() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        int eventsPerThread = 10;
        CountDownLatch latch = new CountDownLatch(threadCount * eventsPerThread);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                for (int j = 0; j < eventsPerThread; j++) {
                    YTask task = createMockTask("task-" + j);
                    YWorkItem workItem = createMockWorkItem(task);
                    YIdentifier caseId = new YIdentifier("case-" + j);

                    eventNotifier.notifyWorkItemEnabled(workItem, caseId);
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
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(threadCount * eventsPerThread, latchListener.getEventCount());
    }

    @Test
    @DisplayName("Concurrent event delivery to multiple listeners")
    void concurrentEventDeliveryMultipleListeners() throws InterruptedException {
        // Arrange
        int listenerCount = 5;
        int eventCount = 20;
        CountDownLatch[] latches = new CountDownLatch[listenerCount];
        TestListener[] listeners = new TestListener[listenerCount];

        for (int i = 0; i < listenerCount; i++) {
            latches[i] = new CountDownLatch(eventCount);
            listeners[i] = new TestListener(latches[i]);
            eventNotifier.addYEventListener(listeners[i]);
        }

        // Act
        for (int i = 0; i < eventCount; i++) {
            YTask task = createMockTask("task-" + i);
            YWorkItem workItem = createMockWorkItem(task);
            YIdentifier caseId = new YIdentifier("case-" + i);

            executorService.submit(() -> {
                eventNotifier.notifyWorkItemEnabled(workItem, caseId);
            });
        }

        // Wait for all events to be processed
        for (CountDownLatch latch : latches) {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }

        // Assert
        for (TestListener testListener : listeners) {
            assertEquals(eventCount, testListener.getEventCount());
        }
    }

    // ==================== Work Item Event Tests ====================

    @Test
    @DisplayName("Notify work item enabled - multiple threads")
    void notifyWorkItemEnabled_multipleThreads() throws Exception {
        // Arrange
        int eventCount = 50;
        CountDownLatch latch = new CountDownLatch(eventCount);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Thread thread = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    YTask task = createMockTask("task-" + j);
                    YWorkItem workItem = createMockWorkItem(task);
                    YIdentifier caseId = new YIdentifier("case-" + j);

                    eventNotifier.notifyWorkItemEnabled(workItem, caseId);
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
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(eventCount, latchListener.getEventCount());
    }

    @Test
    @DisplayName("Notify work item started - multiple threads")
    void notifyWorkItemStarted_multipleThreads() throws Exception {
        // Arrange
        int eventCount = 50;
        CountDownLatch latch = new CountDownLatch(eventCount);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        for (int i = 0; i < eventCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                YTask task = createMockTask("task-" + index);
                YWorkItem workItem = createMockWorkItem(task);
                YIdentifier caseId = new YIdentifier("case-" + index);

                eventNotifier.notifyWorkItemStarted(workItem, caseId);
            });
        }

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(eventCount, latchListener.getEventCount());
    }

    @Test
    @DisplayName("Notify work item completed - multiple threads")
    void notifyWorkItemCompleted_multipleThreads() throws Exception {
        // Arrange
        int eventCount = 50;
        CountDownLatch latch = new CountDownLatch(eventCount);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        for (int i = 0; i < eventCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                YTask task = createMockTask("task-" + index);
                YWorkItem workItem = createMockWorkItem(task);
                YIdentifier caseId = new YIdentifier("case-" + index);

                eventNotifier.notifyWorkItemCompleted(workItem, caseId);
            });
        }

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(eventCount, latchListener.getEventCount());
    }

    @Test
    @DisplayName("Notify work item cancelled - multiple threads")
    void notifyWorkItemCancelled_multipleThreads() throws Exception {
        // Arrange
        int eventCount = 50;
        CountDownLatch latch = new CountDownLatch(eventCount);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        for (int i = 0; i < eventCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                YTask task = createMockTask("task-" + index);
                YWorkItem workItem = createMockWorkItem(task);
                YIdentifier caseId = new YIdentifier("case-" + index);

                eventNotifier.notifyWorkItemCancelled(workItem, caseId);
            });
        }

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(eventCount, latchListener.getEventCount());
    }

    @Test
    @DisplayName("Notify work item failed - multiple threads")
    void notifyWorkItemFailed_multipleThreads() throws Exception {
        // Arrange
        int eventCount = 50;
        CountDownLatch latch = new CountDownLatch(eventCount);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        for (int i = 0; i < eventCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                YTask task = createMockTask("task-" + index);
                YWorkItem workItem = createMockWorkItem(task);
                YIdentifier caseId = new YIdentifier("case-" + index);

                eventNotifier.notifyWorkItemFailed(workItem, caseId, "Test failure");
            });
        }

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(eventCount, latchListener.getEventCount());
    }

    // ==================== Case Event Tests ====================

    @Test
    @DisplayName("Notify case events - multiple threads")
    void notifyCaseEvents_multipleThreads() throws Exception {
        // Arrange
        int eventCount = 10;
        CountDownLatch latch = new CountDownLatch(eventCount * 2); // 2 events per case
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final int index = i;
            Thread thread = new Thread(() -> {
                YIdentifier caseId = new YIdentifier("case-" + index);

                eventNotifier.notifyCaseStarted(caseId);
                eventNotifier.notifyCaseCompleted(caseId);
            });
            threads.add(thread);
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(eventCount * 2, latchListener.getEventCount());
    }

    // ==================== Timer Event Tests ====================

    @Test
    @DisplayName("Notify timer events - multiple threads")
    void notifyTimerEvents_multipleThreads() throws Exception {
        // Arrange
        int timerCount = 20;
        CountDownLatch latch = new CountDownLatch(timerCount * 3); // 3 events per timer
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        for (int i = 0; i < timerCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                YIdentifier caseId = new YIdentifier("case-" + index);

                eventNotifier.notifyTimerStarted(caseId, "timer-" + index);
                eventNotifier.notifyTimerExpired(caseId, "timer-" + index);
                eventNotifier.notifyTimerCancelled(caseId, "timer-" + index);
            });
        }

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(timerCount * 3, latchListener.getEventCount());
    }

    // ==================== Exception Event Tests ====================

    @Test
    @DisplayName("Notify exception - multiple threads")
    void notifyException_multipleThreads() throws Exception {
        // Arrange
        int exceptionCount = 20;
        CountDownLatch latch = new CountDownLatch(exceptionCount);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        for (int i = 0; i < exceptionCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                YIdentifier caseId = new YIdentifier("case-" + index);
                RuntimeException exception = new RuntimeException("Test exception");

                eventNotifier.notifyException(caseId, "Test exception message", exception);
            });
        }

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(exceptionCount, latchListener.getEventCount());
    }

    // ==================== Thread Safety Tests ====================

    @Test
    @DisplayName("Concurrent listener management and event notification")
    void concurrentListenerManagementAndNotification() throws InterruptedException {
        // Arrange
        final AtomicInteger listenerAddCount = new AtomicInteger(0);
        final AtomicInteger listenerRemoveCount = new AtomicInteger(0);
        final CountDownLatch latch = new CountDownLatch(100);
        final AtomicBoolean exceptionThrown = new AtomicBoolean(false);

        // Act
        for (int i = 0; i < 100; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    // Randomly add or remove listeners
                    if (index % 2 == 0) {
                        TestListener newListener = new TestListener();
                        eventNotifier.addYEventListener(newListener);
                        listenerAddCount.incrementAndGet();
                    } else {
                        if (!eventNotifier.getYEventListeners().isEmpty()) {
                            TestListener listenerToRemove = eventNotifier.getYEventListeners().iterator().next();
                            eventNotifier.removeYEventListener(listenerToRemove);
                            listenerRemoveCount.incrementAndGet();
                        }
                    }

                    // Always notify an event
                    YTask task = createMockTask("task-" + index);
                    YWorkItem workItem = createMockWorkItem(task);
                    YIdentifier caseId = new YIdentifier("case-" + index);

                    eventNotifier.notifyWorkItemEnabled(workItem, caseId);
                    latch.countDown();
                } catch (Exception e) {
                    exceptionThrown.set(true);
                }
            });
        }

        // Assert
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertFalse(exceptionThrown.get());
        assertTrue(listenerAddCount.get() > 0);
        assertTrue(listenerRemoveCount.get() > 0);
    }

    @Test
    @DisplayName("Event processing isolation")
    void eventProcessingIsolation() throws InterruptedException {
        // Arrange
        final CountDownLatch latch = new CountDownLatch(50);
        final AtomicReference<Exception> exceptionRef = new AtomicReference<>();
        final TestListener safeListener = new TestListener();

        eventNotifier.addYEventListener(safeListener);

        // Act
        for (int i = 0; i < 50; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    YTask task = createMockTask("task-" + index);
                    YWorkItem workItem = createMockWorkItem(task);
                    YIdentifier caseId = new YIdentifier("case-" + index);

                    eventNotifier.notifyWorkItemEnabled(workItem, caseId);
                    latch.countDown();
                } catch (Exception e) {
                    exceptionRef.set(e);
                }
            });
        }

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(exceptionRef.get());
        assertEquals(50, safeListener.getEventCount());
    }

    // ==================== Performance Tests ====================

    @Test
    @DisplayName("High throughput event notification")
    void highThroughputEventNotification() throws InterruptedException {
        // Arrange
        int eventCount = 1000;
        CountDownLatch latch = new CountDownLatch(eventCount);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < eventCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                YTask task = createMockTask("task-" + index);
                YWorkItem workItem = createMockWorkItem(task);
                YIdentifier caseId = new YIdentifier("case-" + index);

                eventNotifier.notifyWorkItemEnabled(workItem, caseId);
            });
        }

        // Assert
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("Processed " + eventCount + " events in " + duration + "ms");
        System.out.println("Throughput: " + (eventCount * 1000.0 / duration) + " events/second");

        // Should be able to process at least 100 events per second
        assertTrue(duration < 10000); // Should complete in under 10 seconds
        assertEquals(eventCount, latchListener.getEventCount());
    }

    // ==================== Null Parameter Tests ====================

    @Test
    @DisplayName("Notify with null parameters - multiple threads")
    void notifyWithNullParameters_multipleThreads() throws Exception {
        // Arrange
        int eventCount = 20;
        CountDownLatch latch = new CountDownLatch(eventCount);
        TestListener latchListener = new TestListener(latch);
        eventNotifier.addYEventListener(latchListener);

        // Act
        for (int i = 0; i < eventCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                // Randomly null work item or case ID
                if (index % 2 == 0) {
                    YTask task = createMockTask("task-" + index);
                    YWorkItem workItem = createMockWorkItem(task);
                    YIdentifier caseId = null;

                    eventNotifier.notifyWorkItemEnabled(workItem, caseId);
                } else {
                    YTask task = createMockTask("task-" + index);
                    YWorkItem workItem = null;
                    YIdentifier caseId = new YIdentifier("case-" + index);

                    eventNotifier.notifyWorkItemEnabled(workItem, caseId);
                }
                latch.countDown();
            });
        }

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(eventCount, latchListener.getEventCount());
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
}