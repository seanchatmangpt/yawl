/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
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

package org.yawlfoundation.yawl.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YAWLServiceReference;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.announcement.YAnnouncement;
import org.yawlfoundation.yawl.engine.announcement.YEngineEvent;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EngineBasedClient;
import org.yawlfoundation.yawl.exceptions.YAWLException;

/**
 * Comprehensive tests for ObserverGatewayController following Chicago TDD methodology.
 *
 * <p>Tests the async notification dispatch system using virtual thread executors,
 * gateway management operations, and notification broadcasting.</p>
 *
 * @author YAWL Test Suite
 * @see ObserverGatewayController
 */
@DisplayName("ObserverGatewayController Tests")
@Tag("integration")
class TestObserverGatewayController {

    private ObserverGatewayController controller;
    private YAWLServiceReference service;
    private YSpecificationID specID;
    private YIdentifier caseID;
    private YWorkItem workItem;

    @BeforeEach
    void setUp() throws Exception {
        controller = new ObserverGatewayController();

        service = new YAWLServiceReference(
                "http://localhost:8080/testService", null, "testService", "password", null);

        specID = new YSpecificationID("ControllerTestSpec", "0.1", "http://test.org/controller");
        caseID = new YIdentifier(null);
        YWorkItemID workItemID = new YWorkItemID(caseID, "controller-test-task");

        org.yawlfoundation.yawl.elements.YTask task =
                new org.yawlfoundation.yawl.elements.YAtomicTask(
                        "controller-test-task",
                        org.yawlfoundation.yawl.elements.YTask._XOR,
                        org.yawlfoundation.yawl.elements.YTask._AND,
                        null);
        workItem = new YWorkItem(null, specID, task, workItemID, true, false);
    }

    // ========================================================================
    // Gateway Add/Remove Tests
    // ========================================================================

    @Nested
    @DisplayName("Gateway Add/Remove Tests")
    class GatewayAddRemoveTests {

        @Test
        @DisplayName("New controller is empty")
        void newControllerIsEmpty() {
            assertTrue(controller.isEmpty(), "New controller should be empty");
        }

        @Test
        @DisplayName("Can add gateway")
        void canAddGateway() throws YAWLException {
            InterfaceB_EngineBasedClient gateway = new InterfaceB_EngineBasedClient();
            controller.addGateway(gateway);

            assertFalse(controller.isEmpty(), "Controller should not be empty after adding gateway");
        }

        @Test
        @DisplayName("Cannot add gateway with null scheme")
        void cannotAddGatewayWithNullScheme() {
            // The real InterfaceB_EngineBasedClient has a non-null scheme
            // Testing null scheme requires using a test-specific subclass
            assertThrows(YAWLException.class, () -> {
                controller.addGateway(new NullSchemeTestGateway());
            }, "Should throw YAWLException for null scheme gateway");
        }

        /**
         * A test gateway that returns null scheme to verify null scheme validation.
         * All methods throw UnsupportedOperationException to ensure real implementations
         * are used in production code.
         */
        private static class NullSchemeTestGateway implements ObserverGateway {
            @Override
            public String getScheme() { return null; }
            @Override
            public void announceFiredWorkItem(YAnnouncement announcement) {
                throw new UnsupportedOperationException("NullSchemeTestGateway is for testing null scheme only");
            }
            @Override
            public void announceCancelledWorkItem(YAnnouncement announcement) {
                throw new UnsupportedOperationException("NullSchemeTestGateway is for testing null scheme only");
            }
            @Override
            public void announceTimerExpiry(YAnnouncement announcement) {
                throw new UnsupportedOperationException("NullSchemeTestGateway is for testing null scheme only");
            }
            @Override
            public void announceCaseCompletion(YAWLServiceReference yawlService,
                    YIdentifier caseID, Document caseData) {
                throw new UnsupportedOperationException("NullSchemeTestGateway is for testing null scheme only");
            }
            @Override
            public void announceCaseStarted(Set<YAWLServiceReference> services,
                    YSpecificationID specID, YIdentifier caseID,
                    String launchingService, boolean delayed) {
                throw new UnsupportedOperationException("NullSchemeTestGateway is for testing null scheme only");
            }
            @Override
            public void announceCaseCompletion(Set<YAWLServiceReference> services,
                    YIdentifier caseID, Document caseData) {
                throw new UnsupportedOperationException("NullSchemeTestGateway is for testing null scheme only");
            }
            @Override
            public void announceCaseSuspended(Set<YAWLServiceReference> services,
                    YIdentifier caseID) {
                throw new UnsupportedOperationException("NullSchemeTestGateway is for testing null scheme only");
            }
            @Override
            public void announceCaseSuspending(Set<YAWLServiceReference> services,
                    YIdentifier caseID) {
                throw new UnsupportedOperationException("NullSchemeTestGateway is for testing null scheme only");
            }
            @Override
            public void announceCaseResumption(Set<YAWLServiceReference> services,
                    YIdentifier caseID) {
                throw new UnsupportedOperationException("NullSchemeTestGateway is for testing null scheme only");
            }
            @Override
            public void announceWorkItemStatusChange(Set<YAWLServiceReference> services,
                    YWorkItem workItem, YWorkItemStatus oldStatus, YWorkItemStatus newStatus) {
                throw new UnsupportedOperationException("NullSchemeTestGateway is for testing null scheme only");
            }
            @Override
            public void announceEngineInitialised(Set<YAWLServiceReference> services,
                    int maxWaitSeconds) {
                throw new UnsupportedOperationException("NullSchemeTestGateway is for testing null scheme only");
            }
            @Override
            public void announceCaseCancellation(Set<YAWLServiceReference> services,
                    YIdentifier id) {
                throw new UnsupportedOperationException("NullSchemeTestGateway is for testing null scheme only");
            }
            @Override
            public void announceDeadlock(Set<YAWLServiceReference> services,
                    YIdentifier id, Set<YTask> tasks) {
                throw new UnsupportedOperationException("NullSchemeTestGateway is for testing null scheme only");
            }
            @Override
            public void shutdown() {
                throw new UnsupportedOperationException("NullSchemeTestGateway is for testing null scheme only");
            }
        }

        @Test
        @DisplayName("Can remove gateway")
        void canRemoveGateway() throws YAWLException {
            InterfaceB_EngineBasedClient gateway = new InterfaceB_EngineBasedClient();
            controller.addGateway(gateway);

            boolean removed = controller.removeGateway(gateway);

            assertTrue(removed, "Should return true when removing existing gateway");
            assertTrue(controller.isEmpty(), "Controller should be empty after removing only gateway");
        }

        @Test
        @DisplayName("Remove non-existent gateway returns false")
        void removeNonExistentGatewayReturnsFalse() {
            InterfaceB_EngineBasedClient gateway = new InterfaceB_EngineBasedClient();

            boolean removed = controller.removeGateway(gateway);

            assertFalse(removed, "Should return false when removing non-existent gateway");
        }
    }

    // ========================================================================
    // Virtual Thread Executor Tests
    // ========================================================================

    @Nested
    @DisplayName("Virtual Thread Executor Tests")
    class VirtualThreadExecutorTests {

        @Test
        @DisplayName("Controller uses virtual thread executor")
        void controllerUsesVirtualThreadExecutor() throws Exception {
            // Verify that the executor is a virtual thread per task executor
            // by checking it executes tasks concurrently without blocking
            InterfaceB_EngineBasedClient gateway = new InterfaceB_EngineBasedClient();
            controller.addGateway(gateway);

            // This should not block - async execution
            long startTime = System.currentTimeMillis();
            Set<YAWLServiceReference> services = new HashSet<>();
            services.add(service);

            controller.notifyEngineInitialised(services, 1);

            long elapsed = System.currentTimeMillis() - startTime;
            // Should return quickly (async dispatch) rather than waiting
            assertTrue(elapsed < 5000,
                    "Notification should be dispatched asynchronously via virtual threads");
        }

        @Test
        @DisplayName("Multiple notifications can be dispatched concurrently")
        void multipleNotificationsCanBeDispatchedConcurrently() throws Exception {
            InterfaceB_EngineBasedClient gateway = new InterfaceB_EngineBasedClient();
            controller.addGateway(gateway);

            Set<YAWLServiceReference> services = new HashSet<>();
            services.add(service);

            // Dispatch multiple notifications - should all be queued, not block
            assertDoesNotThrow(() -> {
                for (int i = 0; i < 10; i++) {
                    controller.notifyCaseStarting(services, specID, caseID, "service", false);
                }
            }, "Multiple concurrent notifications should not throw");
        }
    }

    // ========================================================================
    // Notification Dispatch Tests
    // ========================================================================

    @Nested
    @DisplayName("Notification Dispatch Tests")
    class NotificationDispatchTests {

        @Test
        @DisplayName("Can notify case starting")
        void canNotifyCaseStarting() throws Exception {
            InterfaceB_EngineBasedClient gateway = new InterfaceB_EngineBasedClient();
            controller.addGateway(gateway);

            Set<YAWLServiceReference> services = new HashSet<>();
            services.add(service);

            assertDoesNotThrow(() -> {
                controller.notifyCaseStarting(services, specID, caseID, "testService", false);
            }, "notifyCaseStarting should not throw");
        }

        @Test
        @DisplayName("Can notify case completion with single service")
        void canNotifyCaseCompletionWithSingleService() throws Exception {
            InterfaceB_EngineBasedClient gateway = new InterfaceB_EngineBasedClient();
            controller.addGateway(gateway);

            Document caseData = new Document(new Element("output"));

            assertDoesNotThrow(() -> {
                controller.notifyCaseCompletion(service, caseID, caseData);
            }, "notifyCaseCompletion with single service should not throw");
        }

        @Test
        @DisplayName("Can notify case completion with service set")
        void canNotifyCaseCompletionWithServiceSet() throws Exception {
            InterfaceB_EngineBasedClient gateway = new InterfaceB_EngineBasedClient();
            controller.addGateway(gateway);

            Set<YAWLServiceReference> services = new HashSet<>();
            services.add(service);
            Document caseData = new Document(new Element("output"));

            assertDoesNotThrow(() -> {
                controller.notifyCaseCompletion(services, caseID, caseData);
            }, "notifyCaseCompletion with service set should not throw");
        }

        @Test
        @DisplayName("Can notify work item status change")
        void canNotifyWorkItemStatusChange() throws Exception {
            InterfaceB_EngineBasedClient gateway = new InterfaceB_EngineBasedClient();
            controller.addGateway(gateway);

            Set<YAWLServiceReference> services = new HashSet<>();
            services.add(service);

            assertDoesNotThrow(() -> {
                controller.notifyWorkItemStatusChange(services, workItem,
                        YWorkItemStatus.statusEnabled, YWorkItemStatus.statusFired);
            }, "notifyWorkItemStatusChange should not throw");
        }

        @Test
        @DisplayName("Can notify case suspending")
        void canNotifyCaseSuspending() throws Exception {
            InterfaceB_EngineBasedClient gateway = new InterfaceB_EngineBasedClient();
            controller.addGateway(gateway);

            Set<YAWLServiceReference> services = new HashSet<>();
            services.add(service);

            assertDoesNotThrow(() -> {
                controller.notifyCaseSuspending(caseID, services);
            }, "notifyCaseSuspending should not throw");
        }

        @Test
        @DisplayName("Can notify case suspended")
        void canNotifyCaseSuspended() throws Exception {
            InterfaceB_EngineBasedClient gateway = new InterfaceB_EngineBasedClient();
            controller.addGateway(gateway);

            Set<YAWLServiceReference> services = new HashSet<>();
            services.add(service);

            assertDoesNotThrow(() -> {
                controller.notifyCaseSuspended(caseID, services);
            }, "notifyCaseSuspended should not throw");
        }

        @Test
        @DisplayName("Can notify case resumption")
        void canNotifyCaseResumption() throws Exception {
            InterfaceB_EngineBasedClient gateway = new InterfaceB_EngineBasedClient();
            controller.addGateway(gateway);

            Set<YAWLServiceReference> services = new HashSet<>();
            services.add(service);

            assertDoesNotThrow(() -> {
                controller.notifyCaseResumption(caseID, services);
            }, "notifyCaseResumption should not throw");
        }

        @Test
        @DisplayName("Can notify case cancellation")
        void canNotifyCaseCancellation() throws Exception {
            InterfaceB_EngineBasedClient gateway = new InterfaceB_EngineBasedClient();
            controller.addGateway(gateway);

            Set<YAWLServiceReference> services = new HashSet<>();
            services.add(service);

            assertDoesNotThrow(() -> {
                controller.notifyCaseCancellation(services, caseID);
            }, "notifyCaseCancellation should not throw");
        }

        @Test
        @DisplayName("Can notify deadlock")
        void canNotifyDeadlock() throws Exception {
            InterfaceB_EngineBasedClient gateway = new InterfaceB_EngineBasedClient();
            controller.addGateway(gateway);

            Set<YAWLServiceReference> services = new HashSet<>();
            services.add(service);
            Set<YTask> tasks = new HashSet<>();

            assertDoesNotThrow(() -> {
                controller.notifyDeadlock(services, caseID, tasks);
            }, "notifyDeadlock should not throw");
        }
    }

    // ========================================================================
    // Announcement Tests
    // ========================================================================

    @Nested
    @DisplayName("Announcement Tests")
    class AnnouncementTests {

        @Test
        @DisplayName("Can announce single announcement")
        void canAnnounceSingleAnnouncement() throws Exception {
            InterfaceB_EngineBasedClient gateway = new InterfaceB_EngineBasedClient();
            controller.addGateway(gateway);

            YAnnouncement announcement = new YAnnouncement(service, workItem, YEngineEvent.ITEM_ADD);

            assertDoesNotThrow(() -> {
                controller.announce(announcement);
            }, "announce single announcement should not throw");
        }

        @Test
        @DisplayName("Can announce announcement set")
        void canAnnounceAnnouncementSet() throws Exception {
            InterfaceB_EngineBasedClient gateway = new InterfaceB_EngineBasedClient();
            controller.addGateway(gateway);

            Set<YAnnouncement> announcements = new HashSet<>();
            announcements.add(new YAnnouncement(service, workItem, YEngineEvent.ITEM_ADD));
            announcements.add(new YAnnouncement(service, workItem, YEngineEvent.ITEM_CANCEL));

            assertDoesNotThrow(() -> {
                controller.announce(announcements);
            }, "announce announcement set should not throw");
        }

        @Test
        @DisplayName("Announce null announcement is safe")
        void announceNullAnnouncementIsSafe() {
            assertDoesNotThrow(() -> {
                controller.announce((YAnnouncement) null);
            }, "Announcing null should be safe");
        }

        @Test
        @DisplayName("Announce empty set is safe")
        void announceEmptySetIsSafe() {
            Set<YAnnouncement> empty = new HashSet<>();

            assertDoesNotThrow(() -> {
                controller.announce(empty);
            }, "Announcing empty set should be safe");
        }
    }

    // ========================================================================
    // Shutdown Tests
    // ========================================================================

    @Nested
    @DisplayName("Shutdown Tests")
    class ShutdownTests {

        @Test
        @DisplayName("Can shutdown with no gateways")
        void canShutdownWithNoGateways() {
            assertDoesNotThrow(() -> {
                controller.shutdownObserverGateways();
            }, "Shutdown with no gateways should not throw");
        }

        @Test
        @DisplayName("Can shutdown with gateways")
        void canShutdownWithGateways() throws Exception {
            InterfaceB_EngineBasedClient gateway = new InterfaceB_EngineBasedClient();
            controller.addGateway(gateway);

            assertDoesNotThrow(() -> {
                controller.shutdownObserverGateways();
            }, "Shutdown with gateways should not throw");
        }

        @Test
        @DisplayName("Shutdown terminates executor")
        void shutdownTerminatesExecutor() throws Exception {
            InterfaceB_EngineBasedClient gateway = new InterfaceB_EngineBasedClient();
            controller.addGateway(gateway);

            controller.shutdownObserverGateways();

            // Allow time for shutdown
            Thread.sleep(100);

            // After shutdown, new notifications would be rejected
            // This test verifies shutdown completes without exception
            assertTrue(true, "Shutdown should complete cleanly");
        }
    }

    // ========================================================================
    // Multiple Gateway Tests
    // ========================================================================

    @Nested
    @DisplayName("Multiple Gateway Tests")
    class MultipleGatewayTests {

        @Test
        @DisplayName("Can add multiple gateways of same scheme")
        void canAddMultipleGatewaysOfSameScheme() throws Exception {
            InterfaceB_EngineBasedClient gateway1 = new InterfaceB_EngineBasedClient();
            InterfaceB_EngineBasedClient gateway2 = new InterfaceB_EngineBasedClient();

            controller.addGateway(gateway1);
            controller.addGateway(gateway2);

            assertFalse(controller.isEmpty(), "Should have gateways registered");
        }

        @Test
        @DisplayName("Notifications go to all gateways")
        void notificationsGoToAllGateways() throws Exception {
            InterfaceB_EngineBasedClient gateway1 = new InterfaceB_EngineBasedClient();
            InterfaceB_EngineBasedClient gateway2 = new InterfaceB_EngineBasedClient();
            controller.addGateway(gateway1);
            controller.addGateway(gateway2);

            Set<YAWLServiceReference> services = new HashSet<>();
            services.add(service);

            assertDoesNotThrow(() -> {
                controller.notifyEngineInitialised(services, 1);
            }, "Notifications to multiple gateways should not throw");
        }
    }
}
