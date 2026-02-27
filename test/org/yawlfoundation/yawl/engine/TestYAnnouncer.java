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

import java.lang.reflect.Field;
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
import org.yawlfoundation.yawl.authentication.YClient;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YAWLServiceGateway;
import org.yawlfoundation.yawl.elements.YAWLServiceReference;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.announcement.AnnouncementContext;
import org.yawlfoundation.yawl.engine.announcement.YAnnouncement;
import org.yawlfoundation.yawl.engine.announcement.YEngineEvent;
import org.yawlfoundation.yawl.engine.interfce.interfaceX.InterfaceX_EngineSideClient;
import org.yawlfoundation.yawl.exceptions.YAWLException;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Comprehensive tests for YAnnouncer following Chicago TDD methodology.
 *
 * <p>Tests the announcement system that notifies observers of engine events
 * including case lifecycle events, work item status changes, and timer expiries.</p>
 *
 * @author YAWL Test Suite
 * @see YAnnouncer
 * @see ObserverGatewayController
 */
@DisplayName("YAnnouncer Tests")
@Tag("integration")
@Execution(ExecutionMode.SAME_THREAD)
class TestYAnnouncer {

    private YAnnouncer announcer;
    private YEngine engine;
    private YSpecificationID specID;
    private YIdentifier caseID;
    private YWorkItemID workItemID;
    private YTask task;
    private YWorkItem workItem;

    @BeforeEach
    void setUp() throws Exception {
        // Get engine instance - real integration test
        engine = YEngine.getInstance();

        // Use reflection to access the announcer since it's protected
        Field announcerField = YEngine.class.getDeclaredField("_announcer");
        announcerField.setAccessible(true);
        announcer = (YAnnouncer) announcerField.get(engine);

        // Create test fixtures with real objects
        specID = new YSpecificationID("AnnouncerTestSpec", "0.1", "http://test.org/announcer");
        caseID = new YIdentifier(null);
        workItemID = new YWorkItemID(caseID, "announcer-test-task");
        task = new YAtomicTask("announcer-test-task", YTask._XOR, YTask._AND, null);
        workItem = new YWorkItem(null, specID, task, workItemID, true, false);
    }

    // ========================================================================
    // Gateway Registration Tests
    // ========================================================================

    @Nested
    @DisplayName("Gateway Registration Tests")
    class GatewayRegistrationTests {

        @Test
        @DisplayName("Can get observer gateway controller")
        void canGetObserverGatewayController() {
            ObserverGatewayController controller = announcer.getObserverGatewayController();
            assertNotNull(controller, "ObserverGatewayController should not be null");
        }

        @Test
        @DisplayName("Observer gateway controller is initialized")
        void observerGatewayControllerIsInitialized() {
            ObserverGatewayController controller = announcer.getObserverGatewayController();
            // Controller should have default HTTP/HTTPS gateways registered
            assertNotNull(controller, "Controller should be initialized");
        }
    }

    // ========================================================================
    // Interface X Listener Tests
    // ========================================================================

    @Nested
    @DisplayName("Interface X Listener Tests")
    class InterfaceXListenerTests {

        @Test
        @DisplayName("Can add InterfaceX listener by URI")
        void canAddInterfaceXListenerByUri() throws Exception {
            String testUri = "http://localhost:8080/testXService";

            // Use reflection to call protected method
            Method addListenerMethod = YAnnouncer.class.getDeclaredMethod(
                    "addInterfaceXListener", String.class);
            addListenerMethod.setAccessible(true);
            addListenerMethod.invoke(announcer, testUri);

            // Verify listener was added
            Method hasListenersMethod = YAnnouncer.class.getDeclaredMethod(
                    "hasInterfaceXListeners");
            hasListenersMethod.setAccessible(true);
            boolean hasListeners = (Boolean) hasListenersMethod.invoke(announcer);
            assertTrue(hasListeners, "Should have InterfaceX listeners after adding");
        }

        @Test
        @DisplayName("Can check if has InterfaceX listeners")
        void canCheckIfHasInterfaceXListeners() throws Exception {
            Method hasListenersMethod = YAnnouncer.class.getDeclaredMethod(
                    "hasInterfaceXListeners");
            hasListenersMethod.setAccessible(true);

            // Result depends on current state - just verify method works
            Boolean hasListeners = (Boolean) hasListenersMethod.invoke(announcer);
            assertNotNull(hasListeners, "hasInterfaceXListeners should return non-null");
        }

        @Test
        @DisplayName("Can remove InterfaceX listener by URI")
        void canRemoveInterfaceXListenerByUri() throws Exception {
            String testUri = "http://localhost:8080/testRemoveService";

            // Add first
            Method addListenerMethod = YAnnouncer.class.getDeclaredMethod(
                    "addInterfaceXListener", String.class);
            addListenerMethod.setAccessible(true);
            addListenerMethod.invoke(announcer, testUri);

            // Then remove
            Method removeListenerMethod = YAnnouncer.class.getDeclaredMethod(
                    "removeInterfaceXListener", String.class);
            removeListenerMethod.setAccessible(true);
            Boolean removed = (Boolean) removeListenerMethod.invoke(announcer, testUri);
            assertTrue(removed, "Should return true when removing existing listener");
        }
    }

    // ========================================================================
    // Announcement Context Tests
    // ========================================================================

    @Nested
    @DisplayName("Announcement Context Tests")
    class AnnouncementContextTests {

        @Test
        @DisplayName("Announcement context starts as NORMAL")
        void announcementContextStartsAsNormal() throws Exception {
            Method getContextMethod = YAnnouncer.class.getDeclaredMethod(
                    "getAnnouncementContext");
            getContextMethod.setAccessible(true);

            AnnouncementContext context = (AnnouncementContext) getContextMethod.invoke(announcer);
            assertEquals(AnnouncementContext.NORMAL, context,
                    "Initial announcement context should be NORMAL");
        }

        @Test
        @DisplayName("Announcement context enum has correct values")
        void announcementContextEnumHasCorrectValues() {
            AnnouncementContext[] values = AnnouncementContext.values();
            assertEquals(2, values.length, "Should have exactly 2 context types");

            assertTrue(containsEnumValue(values, AnnouncementContext.NORMAL),
                    "Should contain NORMAL context");
            assertTrue(containsEnumValue(values, AnnouncementContext.RECOVERING),
                    "Should contain RECOVERING context");
        }

        private boolean containsEnumValue(AnnouncementContext[] values, AnnouncementContext target) {
            for (AnnouncementContext value : values) {
                if (value == target) return true;
            }
            return false;
        }
    }

    // ========================================================================
    // Announcement Creation Tests
    // ========================================================================

    @Nested
    @DisplayName("Announcement Creation Tests")
    class AnnouncementCreationTests {

        @Test
        @DisplayName("Can create announcement from work item")
        void canCreateAnnouncementFromWorkItem() throws Exception {
            // Create a gateway with a service for the task
            YSpecification spec = new YSpecification("testSpec");
            YAWLServiceGateway gateway = new YAWLServiceGateway("testGateway", spec);
            YAWLServiceReference service = new YAWLServiceReference(
                    "http://localhost:8080/testService", null, "testService", "password", null);
            gateway.setYawlService(service);
            task.setDecompositionPrototype(gateway);

            Method createAnnouncementMethod = YAnnouncer.class.getDeclaredMethod(
                    "createAnnouncement", YWorkItem.class, YEngineEvent.class);
            createAnnouncementMethod.setAccessible(true);

            YAnnouncement announcement = (YAnnouncement) createAnnouncementMethod.invoke(
                    announcer, workItem, YEngineEvent.ITEM_ADD);

            assertNotNull(announcement, "Should create announcement for work item");
            assertEquals(YEngineEvent.ITEM_ADD, announcement.getEvent(),
                    "Announcement should have correct event type");
            assertEquals(workItem, announcement.getItem(),
                    "Announcement should reference the work item");
        }

        @Test
        @DisplayName("Announcement has correct context")
        void announcementHasCorrectContext() throws Exception {
            YSpecification spec = new YSpecification("testSpec");
            YAWLServiceGateway gateway = new YAWLServiceGateway("testGateway", spec);
            YAWLServiceReference service = new YAWLServiceReference(
                    "http://localhost:8080/testService", null, "testService", "password", null);
            gateway.setYawlService(service);
            task.setDecompositionPrototype(gateway);

            Method createAnnouncementMethod = YAnnouncer.class.getDeclaredMethod(
                    "createAnnouncement", YWorkItem.class, YEngineEvent.class);
            createAnnouncementMethod.setAccessible(true);

            YAnnouncement announcement = (YAnnouncement) createAnnouncementMethod.invoke(
                    announcer, workItem, YEngineEvent.ITEM_CANCEL);

            assertNotNull(announcement, "Should create announcement");
            assertEquals(AnnouncementContext.NORMAL, announcement.getContext(),
                    "Announcement should have NORMAL context by default");
        }
    }

    // ========================================================================
    // YEngineEvent Tests
    // ========================================================================

    @Nested
    @DisplayName("YEngineEvent Tests")
    class YEngineEventTests {

        @Test
        @DisplayName("YEngineEvent enum has all expected values")
        void yEngineEventEnumHasAllExpectedValues() {
            YEngineEvent[] events = YEngineEvent.values();

            // Verify we have all expected event types
            assertTrue(containsEvent(events, YEngineEvent.ITEM_ADD),
                    "Should have ITEM_ADD event");
            assertTrue(containsEvent(events, YEngineEvent.ITEM_STATUS),
                    "Should have ITEM_STATUS event");
            assertTrue(containsEvent(events, YEngineEvent.ITEM_CANCEL),
                    "Should have ITEM_CANCEL event");
            assertTrue(containsEvent(events, YEngineEvent.CASE_START),
                    "Should have CASE_START event");
            assertTrue(containsEvent(events, YEngineEvent.CASE_COMPLETE),
                    "Should have CASE_COMPLETE event");
            assertTrue(containsEvent(events, YEngineEvent.CASE_CANCELLED),
                    "Should have CASE_CANCELLED event");
            assertTrue(containsEvent(events, YEngineEvent.TIMER_EXPIRED),
                    "Should have TIMER_EXPIRED event");
        }

        @Test
        @DisplayName("YEngineEvent can be parsed from string")
        void yEngineEventCanBeParsedFromString() {
            assertEquals(YEngineEvent.ITEM_ADD, YEngineEvent.fromString("announceItemEnabled"),
                    "Should parse ITEM_ADD from correct label");
            assertEquals(YEngineEvent.CASE_START, YEngineEvent.fromString("announceCaseStarted"),
                    "Should parse CASE_START from correct label");
            assertEquals(YEngineEvent.NO_EVENT, YEngineEvent.fromString("unknownLabel"),
                    "Should return NO_EVENT for unknown label");
        }

        @Test
        @DisplayName("YEngineEvent broadcast flag is correct")
        void yEngineEventBroadcastFlagIsCorrect() {
            assertTrue(YEngineEvent.ITEM_STATUS.isBroadcast(),
                    "ITEM_STATUS should be broadcast");
            assertTrue(YEngineEvent.CASE_START.isBroadcast(),
                    "CASE_START should be broadcast");
            assertFalse(YEngineEvent.ITEM_ADD.isBroadcast(),
                    "ITEM_ADD should not be broadcast");
            assertFalse(YEngineEvent.TIMER_EXPIRED.isBroadcast(),
                    "TIMER_EXPIRED should not be broadcast");
        }

        private boolean containsEvent(YEngineEvent[] events, YEngineEvent target) {
            for (YEngineEvent event : events) {
                if (event == target) return true;
            }
            return false;
        }
    }

    // ========================================================================
    // YAnnouncement Value Object Tests
    // ========================================================================

    @Nested
    @DisplayName("YAnnouncement Value Object Tests")
    class YAnnouncementValueTests {

        @Test
        @DisplayName("YAnnouncement equality is based on all fields")
        void yAnnouncementEqualityIsBasedOnAllFields() throws Exception {
            YAWLServiceReference service = new YAWLServiceReference(
                    "http://localhost:8080/test", null, "test", "pass", null);

            YAnnouncement a1 = new YAnnouncement(service, workItem, YEngineEvent.ITEM_ADD);
            YAnnouncement a2 = new YAnnouncement(service, workItem, YEngineEvent.ITEM_ADD);

            assertEquals(a1, a2, "Identical announcements should be equal");
            assertEquals(a1.hashCode(), a2.hashCode(),
                    "Identical announcements should have same hash code");
        }

        @Test
        @DisplayName("YAnnouncement with different events are not equal")
        void yAnnouncementWithDifferentEventsAreNotEqual() throws Exception {
            YAWLServiceReference service = new YAWLServiceReference(
                    "http://localhost:8080/test", null, "test", "pass", null);

            YAnnouncement a1 = new YAnnouncement(service, workItem, YEngineEvent.ITEM_ADD);
            YAnnouncement a2 = new YAnnouncement(service, workItem, YEngineEvent.ITEM_CANCEL);

            assertNotEquals(a1, a2, "Announcements with different events should not be equal");
        }

        @Test
        @DisplayName("YAnnouncement getScheme returns service scheme")
        void yAnnouncementGetSchemeReturnsServiceScheme() throws Exception {
            YAWLServiceReference service = new YAWLServiceReference(
                    "http://localhost:8080/test", null, "test", "pass", null);

            YAnnouncement announcement = new YAnnouncement(service, workItem, YEngineEvent.ITEM_ADD);

            assertEquals("http", announcement.getScheme(),
                    "Should return the scheme from the service");
        }
    }

    // ========================================================================
    // Case Announcement Tests
    // ========================================================================

    @Nested
    @DisplayName("Case Announcement Tests")
    class CaseAnnouncementTests {

        @Test
        @DisplayName("Can announce case start")
        void canAnnounceCaseStart() throws Exception {
            Set<YAWLServiceReference> services = new HashSet<>();

            // This should not throw
            assertDoesNotThrow(() -> {
                Method announceMethod = YAnnouncer.class.getDeclaredMethod(
                        "announceCaseStart", YSpecificationID.class, YIdentifier.class,
                        String.class, boolean.class);
                announceMethod.setAccessible(true);
                announceMethod.invoke(announcer, specID, caseID, "testService", false);
            }, "Announcing case start should not throw");
        }

        @Test
        @DisplayName("Can announce case completion")
        void canAnnounceCaseCompletion() throws Exception {
            Document caseData = new Document(new Element("caseOutput"));

            assertDoesNotThrow(() -> {
                Method announceMethod = YAnnouncer.class.getDeclaredMethod(
                        "announceCaseCompletion", YAWLServiceReference.class,
                        YIdentifier.class, Document.class);
                announceMethod.setAccessible(true);
                announceMethod.invoke(announcer, null, caseID, caseData);
            }, "Announcing case completion should not throw");
        }
    }

    // ========================================================================
    // Work Item Announcement Tests
    // ========================================================================

    @Nested
    @DisplayName("Work Item Announcement Tests")
    class WorkItemAnnouncementTests {

        @Test
        @DisplayName("Can announce work item status change")
        void canAnnounceWorkItemStatusChange() throws Exception {
            assertDoesNotThrow(() -> {
                Method announceMethod = YAnnouncer.class.getDeclaredMethod(
                        "announceWorkItemStatusChange", YWorkItem.class,
                        YWorkItemStatus.class, YWorkItemStatus.class);
                announceMethod.setAccessible(true);
                announceMethod.invoke(announcer, workItem,
                        YWorkItemStatus.statusEnabled, YWorkItemStatus.statusFired);
            }, "Announcing work item status change should not throw");
        }

        @Test
        @DisplayName("Can announce cancelled work item")
        void canAnnounceCancelledWorkItem() {
            assertDoesNotThrow(() -> {
                announcer.announceCancelledWorkItem(workItem);
            }, "Announcing cancelled work item should not throw");
        }

        @Test
        @DisplayName("Can announce timer expiry event")
        void canAnnounceTimerExpiryEvent() {
            assertDoesNotThrow(() -> {
                announcer.announceTimerExpiryEvent(workItem);
            }, "Announcing timer expiry should not throw");
        }
    }
}
