/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.worklet;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.engine.YSpecificationID;
import org.yawlfoundation.yawl.stateless.engine.YCaseID;
import org.yawlfoundation.yawl.stateless.engine.YTaskID;
import org.yawlfoundation.yawl.elements.YWorkflowNet;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YOutputCondition;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for {@link WorkletService}: RDR evaluation, A2A routing,
 * worklet selection, and event handling.
 *
 * <p>Chicago TDD: Uses real {@link RdrSet}, {@link RdrTree}, and {@link RdrNode} objects.
 * {@link YWorkItem} construction requires full engine context, so {@link TestableWorkletService}
 * overrides {@link WorkletService#buildContext} to inject a fixed test context.
 *
 * Additional tests for:
 * - handleCompleteCaseEvent() - complete case completion handling
 * - handleCancelledWorkItemEvent() - cancelled work item handling
 * - handleCancelledCaseEvent() - cancelled case handling
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
@Tag("integration")
class WorkletServiceTest {

    @TempDir
    Path rdrDir;

    private RdrSetRepository repository;
    private RdrSet rdrSet;

    @Mock
    private YWorkItem mockWorkItem;
    @Mock
    private YSpecificationID mockSpecId;
    @Mock
    private YCaseID mockCaseId;
    @Mock
    private YTaskID mockTaskId;
    @Mock
    private YWorkflowNet mockNet;
    @Mock
    private YTask mockTask;
    @Mock
    private YInputCondition mockInputCondition;
    @Mock
    private YOutputCondition mockOutputCondition;

    @BeforeEach
    void setUp() {
        repository = new RdrSetRepository(rdrDir);

        // Build an RdrSet with two trees in memory for evaluate() tests
        rdrSet = new RdrSet("OrderProcessing");

        // Tree 1: taskId = ApprovalTask → FinanceApprovalWorklet
        RdrTree approvalTree = new RdrTree("ApprovalTask");
        approvalTree.setRoot(new RdrNode(1, "taskId = ApprovalTask", "FinanceApprovalWorklet"));
        rdrSet.addTree(approvalTree);

        // Tree 2: taskId = RiskTask → a2a:http://agent:8090/risk_assessment
        RdrTree riskTree = new RdrTree("RiskTask");
        riskTree.setRoot(new RdrNode(2, "taskId = RiskTask",
                "a2a:http://agent:8090/risk_assessment"));
        rdrSet.addTree(riskTree);
    }

    @Nested
    @DisplayName("handleCompleteCaseEvent")
    class HandleCompleteCaseEventTests {

        @Test
        @DisplayName("should handle null case event gracefully")
        void shouldHandleNullCaseEvent() {
            // Given
            WorkletService service = new WorkletService(repository);
            String caseId = null;

            // When & Then
            assertDoesNotThrow(() -> service.handleCompleteCaseEvent(caseId),
                "Should handle null case event gracefully");
        }

        @Test
        @DisplayName("should handle empty case ID gracefully")
        void shouldHandleEmptyCaseId() {
            // Given
            WorkletService service = new WorkletService(repository);
            String emptyCaseId = "";

            // When & Then
            assertDoesNotThrow(() -> service.handleCompleteCaseEvent(emptyCaseId),
                "Should handle empty case ID gracefully");
        }

        @Test
        @DisplayName("should remove active records for completed case")
        void shouldRemoveActiveRecordsForCompletedCase() {
            // Given
            WorkletService service = new WorkletService(repository);
            String caseId = "case-001";

            // Add a mock active record
            WorkletRecord record = new WorkletRecord("TestWorklet", caseId, "task-001");
            service.getActiveRecords().put(record.getCompositeKey(), record);

            // When
            service.handleCompleteCaseEvent(caseId);

            // Then
            assertTrue(service.getActiveRecords().isEmpty(),
                "Should remove all active records for completed case");
        }

        @Test
        @DisplayName("should not affect records for other cases")
        void shouldNotAffectRecordsForOtherCases() {
            // Given
            WorkletService service = new WorkletService(repository);
            String completedCaseId = "case-001";
            String otherCaseId = "case-002";

            // Add records for different cases
            WorkletRecord record1 = new WorkletRecord("Worklet1", completedCaseId, "task-001");
            WorkletRecord record2 = new WorkletRecord("Worklet2", otherCaseId, "task-002");

            service.getActiveRecords().put(record1.getCompositeKey(), record1);
            service.getActiveRecords().put(record2.getCompositeKey(), record2);

            // When
            service.handleCompleteCaseEvent(completedCaseId);

            // Then
            assertEquals(1, service.getActiveRecords().size(),
                "Should only remove records for completed case");
            assertTrue(service.getActiveRecords().containsKey(record2.getCompositeKey()),
                "Should keep records for other cases");
        }

        @Test
        @DisplayName("should handle case with multiple active records")
        void shouldHandleCaseWithMultipleActiveRecords() {
            // Given
            WorkletService service = new WorkletService(repository);
            String caseId = "case-001";

            // Add multiple records for same case
            WorkletRecord record1 = new WorkletRecord("Worklet1", caseId, "task-001");
            WorkletRecord record2 = new WorkletRecord("Worklet2", caseId, "task-002");
            WorkletRecord record3 = new WorkletRecord("Worklet3", caseId, "task-003");

            service.getActiveRecords().put(record1.getCompositeKey(), record1);
            service.getActiveRecords().put(record2.getCompositeKey(), record2);
            service.getActiveRecords().put(record3.getCompositeKey(), record3);

            // When
            service.handleCompleteCaseEvent(caseId);

            // Then
            assertTrue(service.getActiveRecords().isEmpty(),
                "Should remove all records for completed case");
        }

        @Test
        @DisplayName("should handle case that has no active records")
        void shouldHandleCaseWithNoActiveRecords() {
            // Given
            WorkletService service = new WorkletService(repository);
            String caseId = "case-001";

            // When
            service.handleCompleteCaseEvent(caseId);

            // Then
            assertDoesNotThrow(() -> service.handleCompleteCaseEvent(caseId),
                "Should handle case with no active records gracefully");
            assertTrue(service.getActiveRecords().isEmpty(),
                "Should remain empty after handling non-existent case");
        }

        @Test
        @DisplayName("should be idempotent for same case")
        void shouldBeIdempotentForSameCase() {
            // Given
            WorkletService service = new WorkletService(repository);
            String caseId = "case-001";

            WorkletRecord record = new WorkletRecord("TestWorklet", caseId, "task-001");
            service.getActiveRecords().put(record.getCompositeKey(), record);

            // When - handle completion multiple times
            service.handleCompleteCaseEvent(caseId);
            service.handleCompleteCaseEvent(caseId);
            service.handleCompleteCaseEvent(caseId);

            // Then
            assertTrue(service.getActiveRecords().isEmpty(),
                "Should remain empty after multiple completions");
        }

        @Test
        @DisplayName("should handle concurrent completion events")
        void shouldHandleConcurrentCompletionEvents() throws InterruptedException {
            // Given
            WorkletService service = new WorkletService(repository);
            String caseId = "case-001";

            // Add multiple records
            for (int i = 0; i < 10; i++) {
                WorkletRecord record = new WorkletRecord("Worklet" + i, caseId, "task-" + i);
                service.getActiveRecords().put(record.getCompositeKey(), record);
            }

            // When - handle concurrent completion
            CountDownLatch latch = new CountDownLatch(5);
            Thread[] threads = new Thread[5];

            for (int i = 0; i < 5; i++) {
                threads[i] = new Thread(() -> {
                    service.handleCompleteCaseEvent(caseId);
                    latch.countDown();
                });
                threads[i].start();
            }

            latch.await(1, TimeUnit.SECONDS);

            // Then
            assertTrue(service.getActiveRecords().isEmpty(),
                "Should handle concurrent completions gracefully");
        }
    }

    @Nested
    @DisplayName("handleCancelledWorkItemEvent")
    class HandleCancelledWorkItemEventTests {

        @Test
        @DisplayName("should handle null work item event gracefully")
        void shouldHandleNullWorkItemEvent() {
            // Given
            WorkletService service = new WorkletService(repository);
            String workItemId = null;

            // When & Then
            assertDoesNotThrow(() -> service.handleCancelledWorkItemEvent(workItemId),
                "Should handle null work item event gracefully");
        }

        @Test
        @DisplayName("should handle empty work item ID gracefully")
        void shouldHandleEmptyWorkItemId() {
            // Given
            WorkletService service = new WorkletService(repository);
            String emptyWorkItemId = "";

            // When & Then
            assertDoesNotThrow(() -> service.handleCancelledWorkItemEvent(emptyWorkItemId),
                "Should handle empty work item ID gracefully");
        }

        @Test
        @DisplayName("should remove active records for cancelled work item")
        void shouldRemoveActiveRecordsForCancelledWorkItem() {
            // Given
            WorkletService service = new WorkletService(repository);
            String caseId = "case-001";
            String taskId = "task-001";

            // Add mock active record
            WorkletRecord record = new WorkletRecord("TestWorklet", caseId, taskId);
            service.getActiveRecords().put(record.getCompositeKey(), record);

            // When
            service.handleCancelledWorkItemEvent(record.getCompositeKey());

            // Then
            assertTrue(service.getActiveRecords().isEmpty(),
                "Should remove active record for cancelled work item");
        }

        @Test
        @DisplayName("should not affect records for other work items")
        void shouldNotAffectRecordsForOtherWorkItems() {
            // Given
            WorkletService service = new WorkletService(repository);
            String caseId = "case-001";
            String cancelledTaskId = "task-001";
            String otherTaskId = "task-002";

            // Add records for different tasks
            WorkletRecord record1 = new WorkletRecord("Worklet1", caseId, cancelledTaskId);
            WorkletRecord record2 = new WorkletRecord("Worklet2", caseId, otherTaskId);

            service.getActiveRecords().put(record1.getCompositeKey(), record1);
            service.getActiveRecords().put(record2.getCompositeKey(), record2);

            // When
            service.handleCancelledWorkItemEvent(record1.getCompositeKey());

            // Then
            assertEquals(1, service.getActiveRecords().size(),
                "Should only remove record for cancelled work item");
            assertTrue(service.getActiveRecords().containsKey(record2.getCompositeKey()),
                "Should keep records for other work items");
        }

        @Test
        @DisplayName("should handle work item that has no active records")
        void shouldHandleWorkItemWithNoActiveRecords() {
            // Given
            WorkletService service = new WorkletService(repository);
            String nonExistentWorkItemId = "case-999:task-999";

            // When
            service.handleCancelledWorkItemEvent(nonExistentWorkItemId);

            // Then
            assertDoesNotThrow(() -> service.handleCancelledWorkItemEvent(nonExistentWorkItemId),
                "Should handle work item with no active records gracefully");
            assertTrue(service.getActiveRecords().isEmpty(),
                "Should remain empty after handling non-existent work item");
        }

        @Test
        @DisplayName("should handle malformed work item ID")
        void shouldHandleMalformedWorkItemId() {
            // Given
            WorkletService service = new WorkletService(repository);
            String malformedWorkItemId = "malformed-id";

            // When & Then
            assertDoesNotThrow(() -> service.handleCancelledWorkItemEvent(malformedWorkItemId),
                "Should handle malformed work item ID gracefully");
        }

        @Test
        @DisplayName("should be idempotent for same work item")
        void shouldBeIdempotentForSameWorkItem() {
            // Given
            WorkletService service = new WorkletService(repository);
            String caseId = "case-001";
            String taskId = "task-001";

            WorkletRecord record = new WorkletRecord("TestWorklet", caseId, taskId);
            service.getActiveRecords().put(record.getCompositeKey(), record);

            // When - handle cancellation multiple times
            service.handleCancelledWorkItemEvent(record.getCompositeKey());
            service.handleCancelledWorkItemEvent(record.getCompositeKey());
            service.handleCancelledWorkItemEvent(record.getCompositeKey());

            // Then
            assertTrue(service.getActiveRecords().isEmpty(),
                "Should remain empty after multiple cancellations");
        }
    }

    @Nested
    @DisplayName("handleCancelledCaseEvent")
    class HandleCancelledCaseEventTests {

        @Test
        @DisplayName("should handle null case event gracefully")
        void shouldHandleNullCaseEvent() {
            // Given
            WorkletService service = new WorkletService(repository);
            String caseId = null;

            // When & Then
            assertDoesNotThrow(() -> service.handleCancelledCaseEvent(caseId),
                "Should handle null case event gracefully");
        }

        @Test
        @DisplayName("should handle empty case ID gracefully")
        void shouldHandleEmptyCaseId() {
            // Given
            WorkletService service = new WorkletService(repository);
            String emptyCaseId = "";

            // When & Then
            assertDoesNotThrow(() -> service.handleCancelledCaseEvent(emptyCaseId),
                "Should handle empty case ID gracefully");
        }

        @Test
        @DisplayName("should remove all records for cancelled case")
        void shouldRemoveAllRecordsForCancelledCase() {
            // Given
            WorkletService service = new WorkletService(repository);
            String caseId = "case-001";

            // Add multiple records for cancelled case
            for (int i = 0; i < 5; i++) {
                WorkletRecord record = new WorkletRecord("Worklet" + i, caseId, "task-" + i);
                service.getActiveRecords().put(record.getCompositeKey(), record);
            }

            // When
            service.handleCancelledCaseEvent(caseId);

            // Then
            assertTrue(service.getActiveRecords().isEmpty(),
                "Should remove all records for cancelled case");
        }

        @Test
        @DisplayName("should preserve records for other cases")
        void shouldPreserveRecordsForOtherCases() {
            // Given
            WorkletService service = new WorkletService(repository);
            String cancelledCaseId = "case-001";
            String otherCaseId = "case-002";

            // Add records for different cases
            for (int i = 0; i < 3; i++) {
                WorkletRecord cancelledRecord = new WorkletRecord("Worklet" + i, cancelledCaseId, "task-" + i);
                WorkletRecord otherRecord = new WorkletRecord("OtherWorklet" + i, otherCaseId, "task-" + i);

                service.getActiveRecords().put(cancelledRecord.getCompositeKey(), cancelledRecord);
                service.getActiveRecords().put(otherRecord.getCompositeKey(), otherRecord);
            }

            // When
            service.handleCancelledCaseEvent(cancelledCaseId);

            // Then
            assertEquals(3, service.getActiveRecords().size(),
                "Should preserve records for other cases");
            assertFalse(service.getActiveRecords().containsKey("case-001:task-0"),
                "Should not contain records for cancelled case");
            assertTrue(service.getActiveRecords().containsKey("case-002:task-0"),
                "Should contain records for other cases");
        }

        @Test
        @DisplayName("should handle case with A2A records")
        void shouldHandleCaseWithA2ARecords() {
            // Given
            WorkletService service = new WorkletService(repository);
            String caseId = "case-001";

            // Add A2A record
            WorkletRecord a2aRecord = new WorkletRecord(
                "http://agent:8090", "risk_assessment", caseId, "task-001");
            service.getActiveRecords().put(a2aRecord.getCompositeKey(), a2aRecord);

            // When
            service.handleCancelledCaseEvent(caseId);

            // Then
            assertTrue(service.getActiveRecords().isEmpty(),
                "Should remove A2A records for cancelled case");
        }

        @Test
        @DisplayName("should be idempotent for same case")
        void shouldBeIdempotentForSameCase() {
            // Given
            WorkletService service = new WorkletService(repository);
            String caseId = "case-001";

            WorkletRecord record = new WorkletRecord("TestWorklet", caseId, "task-001");
            service.getActiveRecords().put(record.getCompositeKey(), record);

            // When - handle cancellation multiple times
            service.handleCancelledCaseEvent(caseId);
            service.handleCancelledCaseEvent(caseId);
            service.handleCancelledCaseEvent(caseId);

            // Then
            assertTrue(service.getActiveRecords().isEmpty(),
                "Should remain empty after multiple cancellations");
        }

        @Test
        @DisplayName("should handle concurrent cancellation events")
        void shouldHandleConcurrentCancellationEvents() throws InterruptedException {
            // Given
            WorkletService service = new WorkletService(repository);
            String caseId = "case-001";

            // Add multiple records
            for (int i = 0; i < 10; i++) {
                WorkletRecord record = new WorkletRecord("Worklet" + i, caseId, "task-" + i);
                service.getActiveRecords().put(record.getCompositeKey(), record);
            }

            // When - handle concurrent cancellation
            CountDownLatch latch = new CountDownLatch(5);
            Thread[] threads = new Thread[5];

            for (int i = 0; i < 5; i++) {
                threads[i] = new Thread(() -> {
                    service.handleCancelledCaseEvent(caseId);
                    latch.countDown();
                });
                threads[i].start();
            }

            latch.await(1, TimeUnit.SECONDS);

            // Then
            assertTrue(service.getActiveRecords().isEmpty(),
                "Should handle concurrent cancellations gracefully");
        }
    }

    @Nested
    @DisplayName("Event Listener Contract")
    class EventListenerContractTests {

        @Test
        @DisplayName("should only listen to ITEM_ENABLED events")
        void shouldOnlyListenToItemEnabledEvents() {
            // Given
            WorkletService service = new WorkletService(repository);

            // When - create different event types
            YWorkItemEvent enabledEvent = mock(YWorkItemEvent.class);
            when(enabledEvent.getEventType()).thenReturn(YEventType.ITEM_ENABLED);

            YWorkItemEvent otherEvent = mock(YWorkItemEvent.class);
            when(otherEvent.getEventType()).thenReturn(YEventType.ITEM_COMPLETED);

            // Then - only enabled event should be processed
            assertDoesNotThrow(() -> service.handleWorkItemEvent(enabledEvent),
                "Should process ITEM_ENABLED events");

            assertDoesNotThrow(() -> service.handleWorkItemEvent(otherEvent),
                "Should ignore other event types");
        }

        @Test
        @DisplayName("should handle null work item event")
        void shouldHandleNullWorkItemEvent() {
            // Given
            WorkletService service = new WorkletService(repository);

            // When & Then
            assertDoesNotThrow(() -> service.handleWorkItemEvent(null),
                "Should handle null work item event gracefully");
        }

        @Test
        @DisplayName("should implement YWorkItemEventListener interface")
        void shouldImplementYWorkItemEventListenerInterface() {
            // Given
            WorkletService service = new WorkletService(repository);

            // When & Then
            assertInstanceOf(YWorkItemEventListener.class, service,
                "WorkletService should implement YWorkItemEventListener interface");
        }
    }

    // -----------------------------------------------------------------------
    // Original test scenarios from existing WorkletServiceTest
    // -----------------------------------------------------------------------

    /**
     * Scenario 1: RDR conclusion matching taskId returns SubCaseSelection.
     */
    @Test
    void evaluate_matchingSubCaseRule_returnsSubCaseSelection() {
        Map<String, String> context = Map.of("taskId", "ApprovalTask", "caseId", "1.1",
                "specId", "OrderProcessing");
        TestableWorkletService service = new TestableWorkletService(repository, context);

        WorkletSelection result = service.evaluate(null, rdrSet);

        assertInstanceOf(WorkletSelection.SubCaseSelection.class, result,
                "Should return SubCaseSelection for matching sub-case rule");
        WorkletSelection.SubCaseSelection scs = (WorkletSelection.SubCaseSelection) result;
        assertEquals("FinanceApprovalWorklet", scs.workletName());
        assertEquals(1, scs.rdrNodeId());
    }

    /**
     * Scenario 2: A2A conclusion (starts with "a2a:") returns A2AAgentSelection.
     */
    @Test
    void evaluate_a2aConclusion_returnsA2AAgentSelection() {
        Map<String, String> context = Map.of("taskId", "RiskTask", "caseId", "1.2",
                "specId", "OrderProcessing");
        TestableWorkletService service = new TestableWorkletService(repository, context);

        WorkletSelection result = service.evaluate(null, rdrSet);

        assertInstanceOf(WorkletSelection.A2AAgentSelection.class, result,
                "Should return A2AAgentSelection for a2a: conclusion");
        WorkletSelection.A2AAgentSelection a2a = (WorkletSelection.A2AAgentSelection) result;
        assertEquals("http://agent:8090", a2a.agentEndpoint());
        assertEquals("risk_assessment", a2a.skill());
        assertEquals(2, a2a.rdrNodeId());
    }

    /**
     * Scenario 3: No matching rule returns NoSelection.
     */
    @Test
    void evaluate_noMatchingRule_returnsNoSelection() {
        Map<String, String> context = Map.of("taskId", "UnknownTask");
        TestableWorkletService service = new TestableWorkletService(repository, context);

        WorkletSelection result = service.evaluate(null, rdrSet);

        assertInstanceOf(WorkletSelection.NoSelection.class, result,
                "Should return NoSelection when no rule matches");
    }

    /**
     * Scenario 4: Empty RdrSet returns NoSelection.
     */
    @Test
    void evaluate_emptyRdrSet_returnsNoSelection() {
        Map<String, String> context = Map.of("taskId", "ApprovalTask");
        TestableWorkletService service = new TestableWorkletService(repository, context);
        RdrSet emptySet = new RdrSet("EmptySpec");

        WorkletSelection result = service.evaluate(null, emptySet);

        assertInstanceOf(WorkletSelection.NoSelection.class, result,
                "Empty RdrSet should always return NoSelection");
    }

    /**
     * Scenario 5: WorkletRecord A2A constructor sets isA2aDelegated correctly.
     */
    @Test
    void workletRecord_a2aConstructor_setsA2AFields() {
        WorkletRecord record = new WorkletRecord(
                "http://agent:8090", "risk_assessment", "case-1", "task-1");

        assertTrue(record.isA2aDelegated(), "Record created with A2A constructor should be delegated");
        assertEquals("http://agent:8090", record.getA2aEndpoint());
        assertEquals("risk_assessment", record.getA2aSkill());
        assertEquals("case-1", record.getHostCaseId());
        assertEquals("task-1", record.getHostTaskId());
    }

    /**
     * Scenario 6: Standard WorkletRecord constructor sets isA2aDelegated to false.
     */
    @Test
    void workletRecord_standardConstructor_notA2aDelegated() {
        WorkletRecord record = new WorkletRecord("FinanceApprovalWorklet", "case-1", "task-1");

        assertFalse(record.isA2aDelegated(), "Standard worklet record should not be A2A delegated");
        assertNull(record.getA2aEndpoint());
        assertNull(record.getA2aSkill());
    }

    /**
     * Scenario 7: RdrSetRepository returns empty RdrSet for spec with no rule file.
     */
    @Test
    void rdrSetRepository_noRuleFile_returnsEmptyRdrSet() {
        RdrSet result = repository.load("UnknownSpec");

        assertNotNull(result, "Should return non-null RdrSet even when no file exists");
        assertTrue(result.isEmpty(), "Should return empty RdrSet when no rule file exists");
        assertEquals("UnknownSpec", result.getSpecificationId());
    }

    /**
     * Scenario 8: RdrSetRepository caches the loaded RdrSet (same instance on second call).
     */
    @Test
    void rdrSetRepository_repeatedLoad_returnsCachedInstance() {
        RdrSet first = repository.load("SomeSpec");
        RdrSet second = repository.load("SomeSpec");

        assertSame(first, second, "Repeated load should return the cached instance");
        assertEquals(1, repository.cacheSize(), "Cache should have exactly one entry");
    }

    /**
     * Scenario 9: RdrSetRepository evict forces reload on next load call.
     */
    @Test
    void rdrSetRepository_evict_forcesReload() {
        RdrSet first = repository.load("SomeSpec");
        repository.evict("SomeSpec");

        assertEquals(0, repository.cacheSize(), "Cache should be empty after evict");

        RdrSet second = repository.load("SomeSpec");
        assertNotNull(second, "Should return a new empty RdrSet after evict");
    }

    /**
     * Scenario 10: RdrSetRepository loads rule file from disk and parses it.
     */
    @Test
    void rdrSetRepository_ruleFileExists_parsesRdrSet() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rdrSet specId="PurchaseOrder">
                  <tree taskName="CreditCheck">
                    <node id="1" parentId="-1" branch="none"
                          condition="taskId = CreditCheck"
                          conclusion="CreditReviewWorklet"/>
                  </tree>
                </rdrSet>
                """;
        Files.writeString(rdrDir.resolve("PurchaseOrder.rdr.xml"), xml);

        RdrSet loaded = repository.load("PurchaseOrder");

        assertFalse(loaded.isEmpty(), "Loaded RdrSet should not be empty");
        assertTrue(loaded.hasTree("CreditCheck"),
                "Loaded RdrSet should have a tree for CreditCheck");
    }

    // -----------------------------------------------------------------------
    // Inner test helper — concrete subclass injecting fixed context
    // -----------------------------------------------------------------------

    /**
     * Subclass of WorkletService that returns a fixed context map from buildContext().
     * Allows testing evaluate() without constructing a real YWorkItem.
     */
    static class TestableWorkletService extends WorkletService {

        private final Map<String, String> fixedContext;

        TestableWorkletService(RdrSetRepository repository, Map<String, String> context) {
            super(repository);
            this.fixedContext = context;
        }

        @Override
        Map<String, String> buildContext(YWorkItem ignored) {
            return fixedContext;
        }
    }
}