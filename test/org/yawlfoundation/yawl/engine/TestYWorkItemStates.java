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

import java.time.Instant;

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
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.YTimerParameters;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;

/**
 * Comprehensive state machine tests for YWorkItem.
 *
 * <p>Tests the complete lifecycle of work items following Chicago TDD methodology:
 * using real YAWL engine objects, not mocks. Each test verifies state transitions,
 * parent-child relationships, timer handling, cancellation, and data binding.</p>
 *
 * <h2>State Machine Under Test</h2>
 * <pre>
 *   Enabled -> Fired -> Executing -> Complete
 *                   \-> Failed
 *                   \-> ForcedComplete
 *   Enabled/Fired/Executing -> Suspended -> (previous state)
 *   Any state -> Deleted/Discarded
 * </pre>
 *
 * @author YAWL Test Suite
 * @see YWorkItem
 * @see YWorkItemStatus
 */
@DisplayName("YWorkItem State Machine Tests")
@Tag("integration")
class TestYWorkItemStates {

    private YIdentifier caseIdentifier;
    private YWorkItemID workItemID;
    private YTask task;
    private YWorkItem workItem;
    private YSpecificationID specID;

    @BeforeEach
    void setUp() throws Exception {
        caseIdentifier = new YIdentifier(null);
        workItemID = new YWorkItemID(caseIdentifier, "task-state-test");
        task = new YAtomicTask("task-state-test", YTask._XOR, YTask._AND, null);
        specID = new YSpecificationID("StateTestSpec");
        workItem = new YWorkItem(null, specID, task, workItemID, true, false);
    }

    // ========================================================================
    // Initial State Tests
    // ========================================================================

    @Nested
    @DisplayName("Initial State Tests")
    class InitialStateTests {

        @Test
        @DisplayName("Work item starts in Enabled state")
        void workItemInitialState() {
            assertEquals(YWorkItemStatus.statusEnabled, workItem.getStatus(),
                    "Work item should start in Enabled state");
        }

        @Test
        @DisplayName("Work item has no parent on creation")
        void workItemHasNoParentOnCreation() {
            assertNull(workItem.getParent(),
                    "Work item should not have a parent when first created");
        }

        @Test
        @DisplayName("Work item has enablement time recorded")
        void workItemHasEnablementTime() {
            Instant enablementTime = workItem.getEnablementTime();
            assertNotNull(enablementTime, "Enablement time should be set on creation");
            assertTrue(enablementTime.isBefore(Instant.now().plusSeconds(1)),
                    "Enablement time should be at or before now");
        }

        @Test
        @DisplayName("Work item has correct specification ID")
        void workItemHasCorrectSpecificationID() {
            assertEquals(specID, workItem.getSpecificationID(),
                    "Work item should have the specification ID provided at creation");
        }

        @Test
        @DisplayName("Work item has correct task ID")
        void workItemHasCorrectTaskID() {
            assertEquals("task-state-test", workItem.getTaskID(),
                    "Work item should have the task ID provided at creation");
        }

        @Test
        @DisplayName("Work item has correct case ID")
        void workItemHasCorrectCaseID() {
            assertEquals(caseIdentifier, workItem.getCaseID(),
                    "Work item should have the case ID provided at creation");
        }
    }

    // ========================================================================
    // State Transition Tests
    // ========================================================================

    @Nested
    @DisplayName("State Transition Tests")
    class StateTransitionTests {

        @Test
        @DisplayName("Work item is live when Enabled")
        void workItemIsLiveWhenEnabled() {
            assertTrue(workItem.hasLiveStatus(),
                    "Work item should have live status when Enabled");
        }

        @Test
        @DisplayName("Work item is not finished when Enabled")
        void workItemIsNotFinishedWhenEnabled() {
            assertFalse(workItem.hasFinishedStatus(),
                    "Work item should not have finished status when Enabled");
        }

        @Test
        @DisplayName("Work item has unfinished status when Enabled")
        void workItemHasUnfinishedStatusWhenEnabled() {
            assertTrue(workItem.hasUnfinishedStatus(),
                    "Work item should have unfinished status when Enabled");
        }

        @Test
        @DisplayName("Work item cannot start directly from Enabled state")
        void cannotStartDirectlyFromEnabledState() {
            YClient client = new YClient("testUser", "password", null);
            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> workItem.setStatusToStarted(null, client),
                    "Should throw exception when trying to start from Enabled state");
            assertTrue(exception.getMessage().contains("cannot be moved to"),
                    "Exception message should indicate invalid state transition");
        }

        @Test
        @DisplayName("Work item can be suspended from Enabled state")
        void canSuspendFromEnabledState() throws YPersistenceException {
            workItem.setStatusToSuspended(null);
            assertEquals(YWorkItemStatus.statusSuspended, workItem.getStatus(),
                    "Work item should be Suspended after suspend operation");
        }

        @Test
        @DisplayName("Work item tracks previous status when suspended")
        void tracksPreviousStatusWhenSuspended() throws YPersistenceException {
            YWorkItemStatus originalStatus = workItem.getStatus();
            workItem.setStatusToSuspended(null);
            assertEquals(originalStatus.toString(), workItem.get_prevStatus(),
                    "Previous status should be tracked when suspended");
        }

        @Test
        @DisplayName("Work item can be unsuspended to return to previous state")
        void canUnsuspendToReturnToPreviousState() throws YPersistenceException {
            YWorkItemStatus originalStatus = workItem.getStatus();
            workItem.setStatusToSuspended(null);
            workItem.setStatusToUnsuspended(null);
            assertEquals(originalStatus, workItem.getStatus(),
                    "Work item should return to original state after unsuspend");
        }

        @Test
        @DisplayName("IsEnabledSuspended returns true only when suspended from Enabled")
        void isEnabledSuspendedReturnsTrueOnlyWhenSuspendedFromEnabled() throws YPersistenceException {
            assertFalse(workItem.isEnabledSuspended(),
                    "Should not be enabled suspended when not suspended");
            workItem.setStatusToSuspended(null);
            assertTrue(workItem.isEnabledSuspended(),
                    "Should be enabled suspended when suspended from Enabled state");
        }
    }

    // ========================================================================
    // Multi-Instance Parent-Child Tests
    // ========================================================================

    @Nested
    @DisplayName("Parent-Child Relationship Tests")
    class ParentChildTests {

        @Test
        @DisplayName("Parent work item becomes IsParent when child created")
        void parentBecomesIsParentWhenChildCreated() throws YPersistenceException {
            YIdentifier childIdentifier = caseIdentifier.createChild(null);
            YWorkItem child = workItem.createChild(null, childIdentifier);

            assertNotNull(child, "Child work item should be created");
            assertEquals(YWorkItemStatus.statusIsParent, workItem.getStatus(),
                    "Parent work item should have IsParent status after child creation");
        }

        @Test
        @DisplayName("Child work item has Fired status on creation")
        void childWorkItemHasFiredStatusOnCreation() throws YPersistenceException {
            YIdentifier childIdentifier = caseIdentifier.createChild(null);
            YWorkItem child = workItem.createChild(null, childIdentifier);

            assertEquals(YWorkItemStatus.statusFired, child.getStatus(),
                    "Child work item should start in Fired state");
        }

        @Test
        @DisplayName("Child work item has correct parent reference")
        void childWorkItemHasCorrectParentReference() throws YPersistenceException {
            YIdentifier childIdentifier = caseIdentifier.createChild(null);
            YWorkItem child = workItem.createChild(null, childIdentifier);

            assertEquals(workItem, child.getParent(),
                    "Child should have reference to parent work item");
        }

        @Test
        @DisplayName("Parent work item has children collection")
        void parentWorkItemHasChildrenCollection() throws YPersistenceException {
            YIdentifier childIdentifier = caseIdentifier.createChild(null);
            workItem.createChild(null, childIdentifier);

            assertTrue(workItem.hasChildren(),
                    "Parent should have children collection");
            assertEquals(1, workItem.getChildren().size(),
                    "Parent should have exactly one child");
        }

        @Test
        @DisplayName("Child inherits enablement time from parent")
        void childInheritsEnablementTimeFromParent() throws YPersistenceException {
            YIdentifier childIdentifier = caseIdentifier.createChild(null);
            YWorkItem child = workItem.createChild(null, childIdentifier);

            assertEquals(workItem.getEnablementTime(), child.getEnablementTime(),
                    "Child should inherit enablement time from parent");
        }

        @Test
        @DisplayName("Child has firing time set on creation")
        void childHasFiringTimeSetOnCreation() throws YPersistenceException {
            YIdentifier childIdentifier = caseIdentifier.createChild(null);
            YWorkItem child = workItem.createChild(null, childIdentifier);

            assertNotNull(child.getFiringTime(),
                    "Child should have firing time set on creation");
            assertFalse(child.getFiringTime().isBefore(workItem.getEnablementTime()),
                    "Child firing time should not be before parent enablement time");
        }

        @Test
        @DisplayName("Child cannot create its own children")
        void childCannotCreateChildren() throws YPersistenceException {
            YIdentifier childIdentifier = caseIdentifier.createChild(null);
            YWorkItem child = workItem.createChild(null, childIdentifier);

            YIdentifier grandchildIdentifier = childIdentifier.createChild(null);
            YWorkItem grandchild = child.createChild(null, grandchildIdentifier);

            assertNull(grandchild,
                    "Child work item should not be able to create its own children");
        }

        @Test
        @DisplayName("Child inherits allowsDynamicCreation from parent")
        void childInheritsAllowsDynamicCreationFromParent() throws YPersistenceException {
            YIdentifier childIdentifier = caseIdentifier.createChild(null);
            YWorkItem child = workItem.createChild(null, childIdentifier);

            assertTrue(child.allowsDynamicCreation(),
                    "Child should inherit allowsDynamicCreation from parent");
        }

        @Test
        @DisplayName("isParent returns true for parent work items")
        void isParentReturnsTrueForParentWorkItems() throws YPersistenceException {
            assertFalse(workItem.isParent(),
                    "Regular work item should not be a parent initially");

            YIdentifier childIdentifier = caseIdentifier.createChild(null);
            workItem.createChild(null, childIdentifier);

            assertTrue(workItem.isParent(),
                    "Work item should be parent after child creation");
        }

        @Test
        @DisplayName("Cannot create child with invalid case ID")
        void cannotCreateChildWithInvalidCaseID() throws YPersistenceException {
            // Create an unrelated case ID (no parent relationship)
            YIdentifier unrelatedIdentifier = new YIdentifier(null);

            YWorkItem child = workItem.createChild(null, unrelatedIdentifier);

            assertNull(child,
                    "Should not create child with case ID that has no parent relationship");
        }
    }

    // ========================================================================
    // Started State Tests
    // ========================================================================

    @Nested
    @DisplayName("Started State Tests")
    class StartedStateTests {

        private YWorkItem childItem;
        private YClient client;

        @BeforeEach
        void setUpChildItem() throws YPersistenceException {
            YIdentifier childIdentifier = caseIdentifier.createChild(null);
            childItem = workItem.createChild(null, childIdentifier);
            client = new YClient("testOperator", "secretPassword", null);
        }

        @Test
        @DisplayName("Child work item can be started")
        void childWorkItemCanBeStarted() throws YPersistenceException {
            childItem.setStatusToStarted(null, client);

            assertEquals(YWorkItemStatus.statusExecuting, childItem.getStatus(),
                    "Child work item should be in Executing state after start");
        }

        @Test
        @DisplayName("Started work item records start time")
        void startedWorkItemRecordsStartTime() throws YPersistenceException {
            Instant beforeStart = Instant.now();
            childItem.setStatusToStarted(null, client);
            Instant afterStart = Instant.now();

            assertNotNull(childItem.getStartTime(),
                    "Started work item should have start time recorded");
            assertTrue(childItem.getStartTime().isAfter(beforeStart.minusSeconds(1)),
                    "Start time should be at or after the start call");
            assertTrue(childItem.getStartTime().isBefore(afterStart.plusSeconds(1)),
                    "Start time should be at or before now");
        }

        @Test
        @DisplayName("Started work item records external client")
        void startedWorkItemRecordsExternalClient() throws YPersistenceException {
            childItem.setStatusToStarted(null, client);

            assertNotNull(childItem.getExternalClient(),
                    "Started work item should have external client recorded");
            assertEquals("testOperator", childItem.getExternalClient().getUserName(),
                    "External client username should match the starting client");
        }

        @Test
        @DisplayName("Started work item is live")
        void startedWorkItemIsLive() throws YPersistenceException {
            childItem.setStatusToStarted(null, client);

            assertTrue(childItem.hasLiveStatus(),
                    "Work item in Executing state should have live status");
        }

        @Test
        @DisplayName("Started work item can be rolled back to Fired")
        void startedWorkItemCanBeRolledBackToFired() throws YPersistenceException {
            childItem.setStatusToStarted(null, client);
            childItem.rollBackStatus(null);

            assertEquals(YWorkItemStatus.statusFired, childItem.getStatus(),
                    "Rolled back work item should be in Fired state");
        }

        @Test
        @DisplayName("Rollback clears start time")
        void rollbackClearsStartTime() throws YPersistenceException {
            childItem.setStatusToStarted(null, client);
            childItem.rollBackStatus(null);

            assertNull(childItem.getStartTime(),
                    "Rolled back work item should have null start time");
        }

        @Test
        @DisplayName("Rollback clears external client")
        void rollbackClearsExternalClient() throws YPersistenceException {
            childItem.setStatusToStarted(null, client);
            childItem.rollBackStatus(null);

            assertNull(childItem.getExternalClient(),
                    "Rolled back work item should have null external client");
        }

        @Test
        @DisplayName("Cannot rollback from Fired state")
        void cannotRollbackFromFiredState() {
            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> childItem.rollBackStatus(null),
                    "Should throw exception when trying to rollback from Fired state");
            assertTrue(exception.getMessage().contains("cannot be rolled back"),
                    "Exception message should indicate invalid rollback");
        }
    }

    // ========================================================================
    // Completion State Tests
    // ========================================================================

    @Nested
    @DisplayName("Completion State Tests")
    class CompletionStateTests {

        private YWorkItem childItem;
        private YClient client;

        @BeforeEach
        void setUpChildItemForCompletion() throws YPersistenceException {
            YIdentifier childIdentifier = caseIdentifier.createChild(null);
            childItem = workItem.createChild(null, childIdentifier);
            client = new YClient("completingUser", "password", null);
            childItem.setStatusToStarted(null, client);
        }

        @Test
        @DisplayName("Work item can complete normally")
        void workItemCanCompleteNormally() throws YPersistenceException {
            childItem.setStatusToComplete(null, WorkItemCompletion.Normal);

            assertEquals(YWorkItemStatus.statusComplete, childItem.getStatus(),
                    "Work item should be in Complete state after normal completion");
        }

        @Test
        @DisplayName("Work item can be force completed")
        void workItemCanBeForceCompleted() throws YPersistenceException {
            childItem.setStatusToComplete(null, WorkItemCompletion.Force);

            assertEquals(YWorkItemStatus.statusForcedComplete, childItem.getStatus(),
                    "Work item should be in ForcedComplete state after force completion");
        }

        @Test
        @DisplayName("Work item can fail")
        void workItemCanFail() throws YPersistenceException {
            childItem.setStatusToComplete(null, WorkItemCompletion.Fail);

            assertEquals(YWorkItemStatus.statusFailed, childItem.getStatus(),
                    "Work item should be in Failed state after failed completion");
        }

        @Test
        @DisplayName("Completed work item has finished status")
        void completedWorkItemHasFinishedStatus() throws YPersistenceException {
            childItem.setStatusToComplete(null, WorkItemCompletion.Normal);

            assertTrue(childItem.hasFinishedStatus(),
                    "Completed work item should have finished status");
        }

        @Test
        @DisplayName("Completed work item has completed status")
        void completedWorkItemHasCompletedStatus() throws YPersistenceException {
            childItem.setStatusToComplete(null, WorkItemCompletion.Normal);

            assertTrue(childItem.hasCompletedStatus(),
                    "Completed work item should have completed status");
        }

        @Test
        @DisplayName("Failed work item has finished status but not completed")
        void failedWorkItemHasFinishedButNotCompletedStatus() throws YPersistenceException {
            childItem.setStatusToComplete(null, WorkItemCompletion.Fail);

            assertTrue(childItem.hasFinishedStatus(),
                    "Failed work item should have finished status");
            assertFalse(childItem.hasCompletedStatus(),
                    "Failed work item should not have completed status");
        }

        @Test
        @DisplayName("Forced complete has completed status")
        void forcedCompleteHasCompletedStatus() throws YPersistenceException {
            childItem.setStatusToComplete(null, WorkItemCompletion.Force);

            assertTrue(childItem.hasCompletedStatus(),
                    "Forced complete work item should have completed status");
        }

        @Test
        @DisplayName("Cannot complete from Fired state")
        void cannotCompleteFromFiredState() throws YPersistenceException {
            // Create a new child that is in Fired state (not started)
            YIdentifier childIdentifier = caseIdentifier.createChild(null);
            YWorkItem firedChild = workItem.createChild(null, childIdentifier);

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> firedChild.setStatusToComplete(null, WorkItemCompletion.Normal),
                    "Should throw exception when completing from Fired state");
            assertTrue(exception.getMessage().contains("cannot be moved to"),
                    "Exception message should indicate invalid state transition");
        }

        @Test
        @DisplayName("Work item can be deleted")
        void workItemCanBeDeleted() throws YPersistenceException {
            childItem.setStatusToDeleted(null);

            assertEquals(YWorkItemStatus.statusDeleted, childItem.getStatus(),
                    "Work item should be in Deleted state after deletion");
            assertTrue(childItem.hasFinishedStatus(),
                    "Deleted work item should have finished status");
        }

        @Test
        @DisplayName("Work item can be discarded")
        void workItemCanBeDiscarded() throws YPersistenceException {
            childItem.setStatusToDiscarded();

            assertEquals(YWorkItemStatus.statusDiscarded, childItem.getStatus(),
                    "Work item should be in Discarded state after discarding");
        }
    }

    // ========================================================================
    // Suspension State Tests
    // ========================================================================

    @Nested
    @DisplayName("Suspension State Tests")
    class SuspensionStateTests {

        @Test
        @DisplayName("Work item can be suspended from Enabled")
        void canSuspendFromEnabled() throws YPersistenceException {
            assertTrue(workItem.hasLiveStatus(),
                    "Work item should be live before suspension");

            workItem.setStatusToSuspended(null);

            assertEquals(YWorkItemStatus.statusSuspended, workItem.getStatus(),
                    "Work item should be in Suspended state");
            assertFalse(workItem.hasLiveStatus(),
                    "Suspended work item should not have live status");
            assertTrue(workItem.hasUnfinishedStatus(),
                    "Suspended work item should have unfinished status");
        }

        @Test
        @DisplayName("Cannot suspend finished work item")
        void cannotSuspendFinishedWorkItem() throws YPersistenceException {
            YIdentifier childIdentifier = caseIdentifier.createChild(null);
            YWorkItem childItem = workItem.createChild(null, childIdentifier);
            childItem.setStatusToStarted(null, new YClient("user", "pass", null));
            childItem.setStatusToComplete(null, WorkItemCompletion.Normal);

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> childItem.setStatusToSuspended(null),
                    "Should throw exception when suspending finished work item");
            assertTrue(exception.getMessage().contains("cannot be moved to"),
                    "Exception message should indicate invalid suspension");
        }

        @Test
        @DisplayName("Can suspend from Executing")
        void canSuspendFromExecuting() throws YPersistenceException {
            YIdentifier childIdentifier = caseIdentifier.createChild(null);
            YWorkItem childItem = workItem.createChild(null, childIdentifier);
            childItem.setStatusToStarted(null, new YClient("user", "pass", null));

            childItem.setStatusToSuspended(null);

            assertEquals(YWorkItemStatus.statusSuspended, childItem.getStatus(),
                    "Work item should be suspended from Executing state");
        }

        @Test
        @DisplayName("Unsuspend returns to previous status from Executing")
        void unsuspendReturnsToPreviousStatusFromExecuting() throws YPersistenceException {
            YIdentifier childIdentifier = caseIdentifier.createChild(null);
            YWorkItem childItem = workItem.createChild(null, childIdentifier);
            childItem.setStatusToStarted(null, new YClient("user", "pass", null));
            childItem.setStatusToSuspended(null);
            childItem.setStatusToUnsuspended(null);

            assertEquals(YWorkItemStatus.statusExecuting, childItem.getStatus(),
                    "Work item should return to Executing after unsuspend");
        }
    }

    // ========================================================================
    // Timer Tests
    // ========================================================================

    @Nested
    @DisplayName("Timer Handling Tests")
    class TimerTests {

        @Test
        @DisplayName("Work item without timer has Nil timer status")
        void workItemWithoutTimerHasNilTimerStatus() {
            assertEquals("Nil", workItem.getTimerStatus(),
                    "Work item without timer should have Nil timer status");
        }

        @Test
        @DisplayName("Work item can have timer parameters set")
        void workItemCanHaveTimerParametersSet() {
            YTimerParameters params = new YTimerParameters();
            workItem.setTimerParameters(params);

            assertNotNull(workItem.getTimerParameters(),
                    "Work item should have timer parameters after setting");
        }

        @Test
        @DisplayName("Work item hasTimerStarted returns false initially")
        void workItemHasTimerStartedReturnsFalseInitially() {
            assertFalse(workItem.hasTimerStarted(),
                    "Work item should not have timer started initially");
        }

        @Test
        @DisplayName("Work item can set timer expiry")
        void workItemCanSetTimerExpiry() {
            long expectedExpiry = System.currentTimeMillis() + 3600000; // 1 hour from now
            workItem.setTimerExpiry(expectedExpiry);

            assertEquals(expectedExpiry, workItem.getTimerExpiry(),
                    "Work item should have correct timer expiry");
        }

        @Test
        @DisplayName("Work item with timer expiry in future has Active status when timer params set")
        void workItemWithTimerExpiryInFutureHasActiveStatusWhenTimerParamsSet() {
            // Timer status requires both timer parameters AND expiry to be set
            workItem.setTimerParameters(new YTimerParameters());
            workItem.setTimerExpiry(System.currentTimeMillis() + 3600000);

            assertEquals("Active", workItem.getTimerStatus(),
                    "Work item with timer params and future expiry should have Active timer status");
        }

        @Test
        @DisplayName("Work item with expired timer has Expired status when timer params set")
        void workItemWithExpiredTimerHasExpiredStatusWhenTimerParamsSet() {
            // Timer status requires both timer parameters AND expiry to be set
            workItem.setTimerParameters(new YTimerParameters());
            workItem.setTimerExpiry(System.currentTimeMillis() - 1000);

            assertEquals("Expired", workItem.getTimerStatus(),
                    "Work item with timer params and past expiry should have Expired timer status");
        }

        @Test
        @DisplayName("Work item with no expiry has Dormant status if has timer params")
        void workItemWithNoExpiryHasDormantStatusIfHasTimerParams() {
            workItem.setTimerParameters(new YTimerParameters());

            assertEquals("Dormant", workItem.getTimerStatus(),
                    "Work item with timer params but no expiry should have Dormant status");
        }
    }

    // ========================================================================
    // Data Binding Tests
    // ========================================================================

    @Nested
    @DisplayName("Data Binding Tests")
    class DataBindingTests {

        @Test
        @DisplayName("Work item can have initial data set")
        void workItemCanHaveInitialDataSet() {
            Element data = new Element("initData");
            data.addContent(new Element("param").setText("initial"));

            workItem.setInitData(data);

            assertNotNull(workItem.getDataElement(),
                    "Work item should have data element after setting init data");
            assertNotNull(workItem.getDataString(),
                    "Work item should have data string after setting init data");
        }

        @Test
        @DisplayName("Work item getDataString returns null when no data set")
        void workItemGetDataStringReturnsNullWhenNoDataSet() {
            assertNull(workItem.getDataString(),
                    "Data string should be null when no data has been set");
        }

        @Test
        @DisplayName("Work item getDataElement returns null when no data set")
        void workItemGetDataElementReturnsNullWhenNoDataSet() {
            assertNull(workItem.getDataElement(),
                    "Data element should be null when no data has been set");
        }
    }

    // ========================================================================
    // Attribute Tests
    // ========================================================================

    @Nested
    @DisplayName("Attribute Tests")
    class AttributeTests {

        @Test
        @DisplayName("Work item can have attributes set")
        void workItemCanHaveAttributesSet() {
            java.util.Map<String, String> attrs = new java.util.HashMap<>();
            attrs.put("priority", "high");
            attrs.put("category", "workflow");

            workItem.setAttributes(attrs);

            assertNotNull(workItem.getAttributes(),
                    "Work item should have attributes after setting");
            assertEquals("high", workItem.getAttributes().get("priority"),
                    "Priority attribute should be accessible");
            assertEquals("workflow", workItem.getAttributes().get("category"),
                    "Category attribute should be accessible");
        }

        @Test
        @DisplayName("Work item can have codelet set")
        void workItemCanHaveCodeletSet() {
            workItem.setCodelet("AutoCompleteCodelet");

            assertEquals("AutoCompleteCodelet", workItem.getCodelet(),
                    "Codelet should be set correctly");
        }

        @Test
        @DisplayName("Work item can have custom form URL set")
        void workItemCanHaveCustomFormUrlSet() throws Exception {
            java.net.URL formUrl = new java.net.URL("http://example.com/form/custom");

            workItem.setCustomFormURL(formUrl);

            assertEquals(formUrl, workItem.getCustomFormURL(),
                    "Custom form URL should be set correctly");
        }

        @Test
        @DisplayName("Work item can have requires manual resourcing flag set")
        void workItemCanHaveRequiresManualResourcingFlagSet() {
            workItem.setRequiresManualResourcing(true);

            assertTrue(workItem.requiresManualResourcing(),
                    "Requires manual resourcing should be true after setting");
        }

        @Test
        @DisplayName("Work item allows dynamic creation flag")
        void workItemAllowsDynamicCreationFlag() {
            assertTrue(workItem.allowsDynamicCreation(),
                    "Work item should allow dynamic creation as set in setUp");
            assertTrue(workItem.get_allowsDynamicCreation(),
                    "Getter should return the same value");
        }
    }

    // ========================================================================
    // Identity and Equality Tests
    // ========================================================================

    @Nested
    @DisplayName("Identity and Equality Tests")
    class IdentityTests {

        @Test
        @DisplayName("Work item has unique ID string")
        void workItemHasUniqueIdString() {
            assertNotNull(workItem.getIDString(),
                    "Work item should have ID string");
            assertTrue(workItem.getIDString().contains("task-state-test"),
                    "ID string should contain task ID");
        }

        @Test
        @DisplayName("Work item has internal ID")
        void workItemHasInternalId() {
            assertNotNull(workItem.get_thisID(),
                    "Work item should have internal ID");
        }

        @Test
        @DisplayName("Equal work items have same internal ID")
        void equalWorkItemsHaveSameInternalId() {
            assertEquals(workItem, workItem,
                    "Work item should equal itself");
        }

        @Test
        @DisplayName("Work item toString contains class name")
        void workItemToStringContainsClassName() {
            String str = workItem.toString();
            assertTrue(str.contains("YWorkItem"),
                    "toString should contain class name");
        }

        @Test
        @DisplayName("Work item hash code is consistent")
        void workItemHashCodeIsConsistent() {
            int hash1 = workItem.hashCode();
            int hash2 = workItem.hashCode();
            assertEquals(hash1, hash2,
                    "Hash code should be consistent across calls");
        }
    }

    // ========================================================================
    // XML Output Tests
    // ========================================================================

    @Nested
    @DisplayName("XML Output Tests")
    class XmlOutputTests {

        @Test
        @DisplayName("Work item can generate XML representation")
        void workItemCanGenerateXmlRepresentation() {
            String xml = workItem.toXML();

            assertNotNull(xml, "XML representation should not be null");
            assertTrue(xml.startsWith("<workItem"),
                    "XML should start with workItem element");
            assertTrue(xml.contains("</workItem>"),
                    "XML should end with closing workItem tag");
        }

        @Test
        @DisplayName("Work item XML contains task ID")
        void workItemXmlContainsTaskId() {
            String xml = workItem.toXML();

            assertTrue(xml.contains("<taskid>task-state-test</taskid>"),
                    "XML should contain task ID");
        }

        @Test
        @DisplayName("Work item XML contains status")
        void workItemXmlContainsStatus() {
            String xml = workItem.toXML();

            assertTrue(xml.contains("<status>Enabled</status>"),
                    "XML should contain current status");
        }

        @Test
        @DisplayName("Work item XML contains enablement time")
        void workItemXmlContainsEnablementTime() {
            String xml = workItem.toXML();

            assertTrue(xml.contains("<enablementTime>"),
                    "XML should contain enablement time");
            assertTrue(xml.contains("<enablementTimeMs>"),
                    "XML should contain enablement time in milliseconds");
        }

        @Test
        @DisplayName("Work item XML contains case ID")
        void workItemXmlContainsCaseId() {
            String xml = workItem.toXML();

            assertTrue(xml.contains("<caseid>"),
                    "XML should contain case ID");
        }
    }

    // ========================================================================
    // Deadlocked State Tests
    // ========================================================================

    @Nested
    @DisplayName("Deadlocked State Tests")
    class DeadlockedStateTests {

        @Test
        @DisplayName("Work item can be created in deadlocked state")
        void workItemCanBeCreatedInDeadlockedState() throws YPersistenceException {
            YWorkItem deadlockedItem = new YWorkItem(null, specID, task,
                    new YWorkItemID(new YIdentifier(null), "deadlocked-task"),
                    false, true); // isDeadlocked = true

            assertEquals(YWorkItemStatus.statusDeadlocked, deadlockedItem.getStatus(),
                    "Work item should be in Deadlocked state when created with deadlock flag");
        }

        @Test
        @DisplayName("Deadlocked work item has unfinished status")
        void deadlockedWorkItemHasUnfinishedStatus() throws YPersistenceException {
            YWorkItem deadlockedItem = new YWorkItem(null, specID, task,
                    new YWorkItemID(new YIdentifier(null), "deadlocked-task"),
                    false, true);

            assertTrue(deadlockedItem.hasUnfinishedStatus(),
                    "Deadlocked work item should have unfinished status");
            assertFalse(deadlockedItem.hasLiveStatus(),
                    "Deadlocked work item should not have live status");
            assertFalse(deadlockedItem.hasFinishedStatus(),
                    "Deadlocked work item should not have finished status");
        }
    }

    // ========================================================================
    // WorkItemCompletion Enum Tests
    // ========================================================================

    @Nested
    @DisplayName("WorkItemCompletion Enum Tests")
    class WorkItemCompletionEnumTests {

        @Test
        @DisplayName("WorkItemCompletion values are correctly defined")
        void workItemCompletionValuesAreCorrectlyDefined() {
            WorkItemCompletion[] values = WorkItemCompletion.values();
            assertEquals(4, values.length,
                    "Should have 4 completion types: Normal, Force, Fail, Invalid");
        }

        @Test
        @DisplayName("WorkItemCompletion can be created from int")
        void workItemCompletionCanBeCreatedFromInt() {
            assertEquals(WorkItemCompletion.Normal, WorkItemCompletion.fromInt(0),
                    "fromInt(0) should return Normal");
            assertEquals(WorkItemCompletion.Force, WorkItemCompletion.fromInt(1),
                    "fromInt(1) should return Force");
            assertEquals(WorkItemCompletion.Fail, WorkItemCompletion.fromInt(2),
                    "fromInt(2) should return Fail");
            assertEquals(WorkItemCompletion.Invalid, WorkItemCompletion.fromInt(999),
                    "fromInt(999) should return Invalid");
        }

        @Test
        @DisplayName("WorkItemCompletion Invalid returned for unknown values")
        void workItemCompletionInvalidReturnedForUnknownValues() {
            assertEquals(WorkItemCompletion.Invalid, WorkItemCompletion.fromInt(-1),
                    "fromInt(-1) should return Invalid");
            assertEquals(WorkItemCompletion.Invalid, WorkItemCompletion.fromInt(100),
                    "fromInt(100) should return Invalid");
        }
    }
}
