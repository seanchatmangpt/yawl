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

/**
 * Comprehensive tests for ObserverGateway interface and implementations.
 *
 * <p>Tests the InterfaceB client implementation as the primary gateway,
 * verifying the observer contract for event notifications.</p>
 *
 * @author YAWL Test Suite
 * @see ObserverGateway
 * @see InterfaceB_EngineBasedClient
 */
@DisplayName("ObserverGateway Tests")
@Tag("integration")
class TestObserverGateway {

    private InterfaceB_EngineBasedClient gateway;
    private YSpecificationID specID;
    private YIdentifier caseID;
    private YWorkItem workItem;
    private YAWLServiceReference service;

    @BeforeEach
    void setUp() throws Exception {
        gateway = new InterfaceB_EngineBasedClient();

        specID = new YSpecificationID("GatewayTestSpec", "0.1", "http://test.org/gateway");
        caseID = new YIdentifier(null);
        YWorkItemID workItemID = new YWorkItemID(caseID, "gateway-test-task");

        org.yawlfoundation.yawl.elements.YTask task =
                new org.yawlfoundation.yawl.elements.YAtomicTask(
                        "gateway-test-task",
                        org.yawlfoundation.yawl.elements.YTask._XOR,
                        org.yawlfoundation.yawl.elements.YTask._AND,
                        null);
        workItem = new YWorkItem(null, specID, task, workItemID, true, false);

        service = new YAWLServiceReference(
                "http://localhost:8080/testService", null, "testService", "password", null);
    }

    // ========================================================================
    // Interface Contract Tests
    // ========================================================================

    @Nested
    @DisplayName("Interface Contract Tests")
    class InterfaceContractTests {

        @Test
        @DisplayName("Gateway implements ObserverGateway interface")
        void gatewayImplementsObserverGatewayInterface() {
            assertTrue(gateway instanceof ObserverGateway,
                    "InterfaceB_EngineBasedClient should implement ObserverGateway");
        }

        @Test
        @DisplayName("Gateway has valid scheme")
        void gatewayHasValidScheme() {
            String scheme = gateway.getScheme();
            assertNotNull(scheme, "Scheme should not be null");
            assertTrue(scheme.length() > 0, "Scheme should not be empty");
        }

        @Test
        @DisplayName("Gateway scheme is http for non-HTTPS client")
        void gatewaySchemeIsHttpForNonHttpsClient() {
            String scheme = gateway.getScheme();
            assertEquals("http", scheme,
                    "Non-HTTPS gateway should return 'http' scheme");
        }
    }

    // ========================================================================
    // Work Item Announcement Tests
    // ========================================================================

    @Nested
    @DisplayName("Work Item Announcement Tests")
    class WorkItemAnnouncementTests {

        @Test
        @DisplayName("Can call announceFiredWorkItem without exception")
        void canCallAnnounceFiredWorkItemWithoutException() {
            YAnnouncement announcement = createTestAnnouncement(YEngineEvent.ITEM_ADD);

            assertDoesNotThrow(() -> {
                gateway.announceFiredWorkItem(announcement);
            }, "announceFiredWorkItem should not throw for valid announcement");
        }

        @Test
        @DisplayName("Can call announceCancelledWorkItem without exception")
        void canCallAnnounceCancelledWorkItemWithoutException() {
            YAnnouncement announcement = createTestAnnouncement(YEngineEvent.ITEM_CANCEL);

            assertDoesNotThrow(() -> {
                gateway.announceCancelledWorkItem(announcement);
            }, "announceCancelledWorkItem should not throw for valid announcement");
        }

        @Test
        @DisplayName("Can call announceTimerExpiry without exception")
        void canCallAnnounceTimerExpiryWithoutException() {
            YAnnouncement announcement = createTestAnnouncement(YEngineEvent.TIMER_EXPIRED);

            assertDoesNotThrow(() -> {
                gateway.announceTimerExpiry(announcement);
            }, "announceTimerExpiry should not throw for valid announcement");
        }

        private YAnnouncement createTestAnnouncement(YEngineEvent event) {
            return new YAnnouncement(service, workItem, event);
        }
    }

    // ========================================================================
    // Case Announcement Tests
    // ========================================================================

    @Nested
    @DisplayName("Case Announcement Tests")
    class CaseAnnouncementTests {

        @Test
        @DisplayName("Can call announceCaseCompletion with single service")
        void canCallAnnounceCaseCompletionWithSingleService() {
            Document caseData = new Document(new Element("caseOutput"));

            assertDoesNotThrow(() -> {
                gateway.announceCaseCompletion(service, caseID, caseData);
            }, "announceCaseCompletion with single service should not throw");
        }

        @Test
        @DisplayName("Can call announceCaseCompletion with service set")
        void canCallAnnounceCaseCompletionWithServiceSet() {
            Set<YAWLServiceReference> services = new HashSet<>();
            services.add(service);
            Document caseData = new Document(new Element("caseOutput"));

            assertDoesNotThrow(() -> {
                gateway.announceCaseCompletion(services, caseID, caseData);
            }, "announceCaseCompletion with service set should not throw");
        }

        @Test
        @DisplayName("Can call announceCaseStarted")
        void canCallAnnounceCaseStarted() {
            Set<YAWLServiceReference> services = new HashSet<>();
            services.add(service);

            assertDoesNotThrow(() -> {
                gateway.announceCaseStarted(services, specID, caseID, "testService", false);
            }, "announceCaseStarted should not throw");
        }

        @Test
        @DisplayName("Can call announceCaseCancellation")
        void canCallAnnounceCaseCancellation() {
            Set<YAWLServiceReference> services = new HashSet<>();
            services.add(service);

            assertDoesNotThrow(() -> {
                gateway.announceCaseCancellation(services, caseID);
            }, "announceCaseCancellation should not throw");
        }
    }

    // ========================================================================
    // Suspension Announcement Tests
    // ========================================================================

    @Nested
    @DisplayName("Suspension Announcement Tests")
    class SuspensionAnnouncementTests {

        @Test
        @DisplayName("Can call announceCaseSuspending")
        void canCallAnnounceCaseSuspending() {
            Set<YAWLServiceReference> services = new HashSet<>();
            services.add(service);

            assertDoesNotThrow(() -> {
                gateway.announceCaseSuspending(services, caseID);
            }, "announceCaseSuspending should not throw");
        }

        @Test
        @DisplayName("Can call announceCaseSuspended")
        void canCallAnnounceCaseSuspended() {
            Set<YAWLServiceReference> services = new HashSet<>();
            services.add(service);

            assertDoesNotThrow(() -> {
                gateway.announceCaseSuspended(services, caseID);
            }, "announceCaseSuspended should not throw");
        }

        @Test
        @DisplayName("Can call announceCaseResumption")
        void canCallAnnounceCaseResumption() {
            Set<YAWLServiceReference> services = new HashSet<>();
            services.add(service);

            assertDoesNotThrow(() -> {
                gateway.announceCaseResumption(services, caseID);
            }, "announceCaseResumption should not throw");
        }
    }

    // ========================================================================
    // Work Item Status Change Tests
    // ========================================================================

    @Nested
    @DisplayName("Work Item Status Change Tests")
    class WorkItemStatusChangeTests {

        @Test
        @DisplayName("Can call announceWorkItemStatusChange")
        void canCallAnnounceWorkItemStatusChange() {
            Set<YAWLServiceReference> services = new HashSet<>();
            services.add(service);

            assertDoesNotThrow(() -> {
                gateway.announceWorkItemStatusChange(services, workItem,
                        YWorkItemStatus.statusEnabled, YWorkItemStatus.statusFired);
            }, "announceWorkItemStatusChange should not throw");
        }
    }

    // ========================================================================
    // Engine Lifecycle Tests
    // ========================================================================

    @Nested
    @DisplayName("Engine Lifecycle Tests")
    class EngineLifecycleTests {

        @Test
        @DisplayName("Can call announceEngineInitialised")
        void canCallAnnounceEngineInitialised() {
            Set<YAWLServiceReference> services = new HashSet<>();
            services.add(service);

            assertDoesNotThrow(() -> {
                gateway.announceEngineInitialised(services, 60);
            }, "announceEngineInitialised should not throw");
        }

        @Test
        @DisplayName("Can call shutdown")
        void canCallShutdown() {
            assertDoesNotThrow(() -> {
                gateway.shutdown();
            }, "shutdown should not throw");
        }
    }

    // ========================================================================
    // Deadlock Announcement Tests
    // ========================================================================

    @Nested
    @DisplayName("Deadlock Announcement Tests")
    class DeadlockAnnouncementTests {

        @Test
        @DisplayName("Can call announceDeadlock")
        void canCallAnnounceDeadlock() {
            Set<YAWLServiceReference> services = new HashSet<>();
            services.add(service);
            Set<YTask> tasks = new HashSet<>();

            assertDoesNotThrow(() -> {
                gateway.announceDeadlock(services, caseID, tasks);
            }, "announceDeadlock should not throw");
        }
    }
}
