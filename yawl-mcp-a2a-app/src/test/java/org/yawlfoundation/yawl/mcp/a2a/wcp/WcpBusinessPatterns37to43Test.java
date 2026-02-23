/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.wcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.mcp.a2a.example.WorkflowSoundnessVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive JUnit 5 tests for YAWL Workflow Control Patterns (WCP) 37-43,
 * written from a BUSINESS PERSPECTIVE with real-world enterprise scenarios.
 *
 * <p>Each WCP is tested with:
 * <ul>
 *   <li><strong>Happy path (sound)</strong>: The pattern correctly models the workflow</li>
 *   <li><strong>Edge case or variant (sound)</strong>: Alternative valid implementation</li>
 *   <li><strong>Unsound case or dead-end</strong>: Missing flows or undefined tasks</li>
 * </ul>
 *
 * <p>Business Scenarios:
 * <ul>
 *   <li><strong>WCP-37 (Explicit Termination)</strong>:
 *       Supervisor explicitly closes incident ticket when condition met,
 *       ignoring open sub-tasks. Example: Incident.CloseIncident() terminates workflow
 *       regardless of pending investigations.</li>
 *   <li><strong>WCP-38 (Cancel Multiple Instance Activity)</strong>:
 *       Cancellation of a multi-instance task mid-execution. Example: Executive override
 *       cancels all pending approval requests when decision made.</li>
 *   <li><strong>WCP-39 (Critical Section)</strong>:
 *       Tasks that must execute atomically/exclusively without interruption.
 *       Example: Updating account balance must not be interrupted by concurrent transactions.</li>
 *   <li><strong>WCP-40 (Interleaved Routing)</strong>:
 *       Tasks can execute in any order but not simultaneously.
 *       Example: Inspection tasks must all complete but only one at a time.</li>
 *   <li><strong>WCP-41 (Thread Split)</strong>:
 *       Creating new execution threads mid-process.
 *       Example: Spawning sub-process for each detected issue in an audit.</li>
 *   <li><strong>WCP-42 (Thread Merge)</strong>:
 *       Merging threads back after parallel activities.
 *       Example: Combining results from all parallel audit threads.</li>
 *   <li><strong>WCP-43 (Explicit Termination with Cancellation Region)</strong>:
 *       Explicit termination that cancels all active tasks in a region.
 *       Example: Fraud detected → cancel all active order fulfillment tasks immediately.</li>
 * </ul>
 *
 * <p>All tests use REAL {@link WorkflowSoundnessVerifier} instances on actual workflow
 * specifications. No mocks, stubs, or fake implementations (Chicago/Detroit TDD).
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("WCP 37-43: Advanced Business Patterns Test Suite")
public class WcpBusinessPatterns37to43Test {

    private WorkflowSoundnessVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new WorkflowSoundnessVerifier();
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Builds a task specification map with the given ID and flow targets.
     *
     * @param id    The task identifier
     * @param flows Target task IDs (use "end" for the output condition)
     * @return A task specification map
     */
    private Map<String, Object> buildTask(String id, String... flows) {
        return Map.of(
            "id", id,
            "flows", List.of(flows)
        );
    }

    /**
     * Builds a complete workflow specification map.
     *
     * @param name  The workflow name
     * @param first The ID of the first task (entry point)
     * @param tasks List of task specifications
     * @return A workflow specification map
     */
    private Map<String, Object> buildWorkflow(String name, String first,
                                               List<Map<String, Object>> tasks) {
        return Map.of(
            "name", name,
            "first", first,
            "tasks", tasks
        );
    }

    // =========================================================================
    // WCP-37: Explicit Termination
    // =========================================================================

    @Nested
    @DisplayName("WCP-37: Explicit Termination - Workflow explicitly terminated when condition met")
    class ExplicitTerminationPattern {

        @Test
        @DisplayName("Sound: Incident ticket closure by supervisor (CloseIncident explicit exit)")
        void testIncidentTicketExplicitTermination() {
            var workflow = buildWorkflow(
                "IncidentManagement",
                "ReceiveIncident",
                List.of(
                    buildTask("ReceiveIncident", "AssignInvestigator", "CloseIncident"),
                    buildTask("AssignInvestigator", "ConductInvestigation"),
                    buildTask("ConductInvestigation", "DocumentFindings"),
                    buildTask("DocumentFindings", "SupervisorReview"),
                    buildTask("SupervisorReview", "CloseIncident", "ReopenIncident"),
                    buildTask("ReopenIncident", "AssignInvestigator"),
                    buildTask("CloseIncident", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Incident termination with explicit CloseIncident exit should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Sound: Service request rejection (explicit early termination)")
        void testServiceRequestEarlyTermination() {
            var workflow = buildWorkflow(
                "ServiceRequestWorkflow",
                "SubmitRequest",
                List.of(
                    buildTask("SubmitRequest", "ValidateRequest"),
                    buildTask("ValidateRequest", "ProcessRequest", "RejectRequest"),
                    buildTask("ProcessRequest", "CompleteRequest"),
                    buildTask("CompleteRequest", "end"),
                    buildTask("RejectRequest", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Service request with early termination should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Sound: Loan application explicit termination on fraud detection")
        void testLoanApplicationFraudTermination() {
            var workflow = buildWorkflow(
                "LoanApplication",
                "SubmitApplication",
                List.of(
                    buildTask("SubmitApplication", "CheckFraud"),
                    buildTask("CheckFraud", "FraudTerminate", "ValidateIncome"),
                    buildTask("ValidateIncome", "AssessCredit"),
                    buildTask("AssessCredit", "ApproveApplication"),
                    buildTask("ApproveApplication", "end"),
                    buildTask("FraudTerminate", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Loan application fraud termination should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Unsound: Explicit termination path has no outgoing flow (dead-end)")
        void testExplicitTerminationDeadEnd() {
            var workflow = buildWorkflow(
                "BadTermination",
                "StartTask",
                List.of(
                    buildTask("StartTask", "ProcessTask", "TerminateTask"),
                    buildTask("ProcessTask", "end"),
                    buildTask("TerminateTask")
                )
            );

            var result = verifier.verify(workflow);

            assertFalse(result.sound(),
                "Explicit termination without outgoing flow should be unsound (dead-end)");
        }

        @Test
        @DisplayName("Unsound: Explicit termination references undefined task")
        void testExplicitTerminationUndefinedTask() {
            var workflow = buildWorkflow(
                "BadTerminationRef",
                "StartTask",
                List.of(
                    buildTask("StartTask", "ProcessTask", "TerminateUndefined"),
                    buildTask("ProcessTask", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertFalse(result.sound(),
                "Explicit termination referencing undefined task should be unsound");
        }
    }

    // =========================================================================
    // WCP-38: Cancel Multiple Instance Activity
    // =========================================================================

    @Nested
    @DisplayName("WCP-38: Cancel Multiple Instance Activity - Cancellation mid-execution")
    class CancelMultipleInstancePattern {

        @Test
        @DisplayName("Sound: Executive override cancels all pending approvals")
        void testExecutiveOverrideCancelApprovals() {
            var workflow = buildWorkflow(
                "ApprovalProcess",
                "SubmitRequest",
                List.of(
                    buildTask("SubmitRequest", "RequestApprovals"),
                    buildTask("RequestApprovals", "CollectApprovals", "ExecutiveOverride"),
                    buildTask("CollectApprovals", "FinalizApproval"),
                    buildTask("FinalizApproval", "end"),
                    buildTask("ExecutiveOverride", "CancelAllApprovals"),
                    buildTask("CancelAllApprovals", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Executive override with cancellation should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Sound: Batch approval cancellation (reject all pending reviews)")
        void testBatchApprovalCancellation() {
            var workflow = buildWorkflow(
                "BatchApprovalCancellation",
                "SubmitBatch",
                List.of(
                    buildTask("SubmitBatch", "InitiateReviews"),
                    buildTask("InitiateReviews", "ReviewItemA", "ReviewItemB", "ReviewItemC"),
                    buildTask("ReviewItemA", "ConsolidateReviews"),
                    buildTask("ReviewItemB", "ConsolidateReviews"),
                    buildTask("ReviewItemC", "ConsolidateReviews"),
                    buildTask("ConsolidateReviews", "FinalApproval", "CancelAllReviews"),
                    buildTask("FinalApproval", "end"),
                    buildTask("CancelAllReviews", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Batch approval cancellation should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Sound: Purchase order cancellation halts all processing")
        void testPurchaseOrderCancellation() {
            var workflow = buildWorkflow(
                "PurchaseOrderCancellation",
                "ReceivePO",
                List.of(
                    buildTask("ReceivePO", "ValidatePO"),
                    buildTask("ValidatePO", "ProcessPayment", "BuyerCancel"),
                    buildTask("ProcessPayment", "ArrangeShipment"),
                    buildTask("ArrangeShipment", "GenerateInvoice"),
                    buildTask("GenerateInvoice", "end"),
                    buildTask("BuyerCancel", "CancelPayment"),
                    buildTask("CancelPayment", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Purchase order cancellation should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Unsound: Cancellation path incomplete (missing end)")
        void testCancellationPathIncomplete() {
            var workflow = buildWorkflow(
                "IncompleteCancellation",
                "StartTask",
                List.of(
                    buildTask("StartTask", "WorkTask", "CancelTask"),
                    buildTask("WorkTask", "end"),
                    buildTask("CancelTask", "NotificationTask")
                )
            );

            var result = verifier.verify(workflow);

            assertFalse(result.sound(),
                "Cancellation path without proper termination should be unsound");
        }

        @Test
        @DisplayName("Unsound: Cancelled task reference is undefined (unknown flow target)")
        void testCancellationUndefinedReference() {
            var workflow = buildWorkflow(
                "BadCancellation",
                "StartTask",
                List.of(
                    buildTask("StartTask", "ProcessTask", "CancelPath"),
                    buildTask("ProcessTask", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertFalse(result.sound(),
                "Cancellation referencing undefined task should be unsound (CancelPath not defined)");
        }
    }

    // =========================================================================
    // WCP-39: Critical Section
    // =========================================================================

    @Nested
    @DisplayName("WCP-39: Critical Section - Exclusive atomic execution region")
    class CriticalSectionPattern {

        @Test
        @DisplayName("Sound: Account balance update (atomic critical section)")
        void testAccountBalanceUpdateCriticalSection() {
            var workflow = buildWorkflow(
                "BankingTransaction",
                "ReceiveWithdrawal",
                List.of(
                    buildTask("ReceiveWithdrawal", "CheckBalance"),
                    buildTask("CheckBalance", "LockAccount"),
                    buildTask("LockAccount", "UpdateBalance"),
                    buildTask("UpdateBalance", "RecordTransaction"),
                    buildTask("RecordTransaction", "UnlockAccount"),
                    buildTask("UnlockAccount", "NotifyCustomer"),
                    buildTask("NotifyCustomer", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Account balance critical section should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Sound: Inventory deduction (sequential exclusive access)")
        void testInventoryDeductionCriticalSection() {
            var workflow = buildWorkflow(
                "InventoryManagement",
                "ReceiveOrder",
                List.of(
                    buildTask("ReceiveOrder", "AcquireInventoryLock"),
                    buildTask("AcquireInventoryLock", "VerifyStock"),
                    buildTask("VerifyStock", "DeductInventory"),
                    buildTask("DeductInventory", "UpdateRecords"),
                    buildTask("UpdateRecords", "ReleaseInventoryLock"),
                    buildTask("ReleaseInventoryLock", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Inventory deduction critical section should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Sound: Database record update (serialized execution)")
        void testDatabaseRecordUpdateCriticalSection() {
            var workflow = buildWorkflow(
                "DatabaseUpdate",
                "RequestUpdate",
                List.of(
                    buildTask("RequestUpdate", "EnterCriticalSection"),
                    buildTask("EnterCriticalSection", "FetchCurrentRecord"),
                    buildTask("FetchCurrentRecord", "ModifyRecord"),
                    buildTask("ModifyRecord", "VerifyConstraints"),
                    buildTask("VerifyConstraints", "CommitChanges"),
                    buildTask("CommitChanges", "ExitCriticalSection"),
                    buildTask("ExitCriticalSection", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Database record update critical section should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Unsound: Critical section missing exit (dead-end unlock task)")
        void testCriticalSectionMissingExit() {
            var workflow = buildWorkflow(
                "BadCriticalSection",
                "StartTask",
                List.of(
                    buildTask("StartTask", "LockResource"),
                    buildTask("LockResource", "UpdateResource"),
                    buildTask("UpdateResource", "UnlockResource"),
                    buildTask("UnlockResource")
                )
            );

            var result = verifier.verify(workflow);

            assertFalse(result.sound(),
                "Critical section with unreachable unlock (dead-end) should be unsound");
        }
    }

    // =========================================================================
    // WCP-40: Interleaved Routing
    // =========================================================================

    @Nested
    @DisplayName("WCP-40: Interleaved Routing - Sequential but arbitrary ordering")
    class InterleavedRoutingPattern {

        @Test
        @DisplayName("Sound: Quality inspection (all tasks complete, one at a time)")
        void testQualityInspectionInterleaved() {
            var workflow = buildWorkflow(
                "QualityInspection",
                "ReceiveProduct",
                List.of(
                    buildTask("ReceiveProduct", "InspectDimensions"),
                    buildTask("InspectDimensions", "InspectColor"),
                    buildTask("InspectColor", "InspectFunctionality"),
                    buildTask("InspectFunctionality", "ConsolidateInspections"),
                    buildTask("ConsolidateInspections", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Quality inspection with interleaved routing should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Sound: Loan document collection (sequential gathering)")
        void testLoanDocumentCollection() {
            var workflow = buildWorkflow(
                "LoanDocumentCollection",
                "StartProcessing",
                List.of(
                    buildTask("StartProcessing", "CollectPayStubs"),
                    buildTask("CollectPayStubs", "CollectTaxReturns"),
                    buildTask("CollectTaxReturns", "CollectBankStatements"),
                    buildTask("CollectBankStatements", "VerifyDocuments"),
                    buildTask("VerifyDocuments", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Loan document collection should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Sound: Audit inspection tasks (sequential, all mandatory)")
        void testAuditInspectionSequential() {
            var workflow = buildWorkflow(
                "AuditProcess",
                "StartAudit",
                List.of(
                    buildTask("StartAudit", "ReviewFinancialRecords"),
                    buildTask("ReviewFinancialRecords", "VerifyCompliance"),
                    buildTask("VerifyCompliance", "InterviewStaff"),
                    buildTask("InterviewStaff", "DocumentFindings"),
                    buildTask("DocumentFindings", "PrepareReport"),
                    buildTask("PrepareReport", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Audit inspection sequential tasks should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Unsound: Interleaved task missing successor (dead-end)")
        void testInterleavedTaskDeadEnd() {
            var workflow = buildWorkflow(
                "BadInterleaved",
                "StartTask",
                List.of(
                    buildTask("StartTask", "FirstTask"),
                    buildTask("FirstTask", "SecondTask"),
                    buildTask("SecondTask")
                )
            );

            var result = verifier.verify(workflow);

            assertFalse(result.sound(),
                "Interleaved routing with dead-end task should be unsound");
        }
    }

    // =========================================================================
    // WCP-41: Thread Split
    // =========================================================================

    @Nested
    @DisplayName("WCP-41: Thread Split - Creating new execution threads mid-process")
    class ThreadSplitPattern {

        @Test
        @DisplayName("Sound: Audit spawns sub-processes for each detected issue")
        void testAuditThreadSplit() {
            var workflow = buildWorkflow(
                "AuditWithThreadSplit",
                "PerformInitialAudit",
                List.of(
                    buildTask("PerformInitialAudit", "AnalyzeFinancials"),
                    buildTask("AnalyzeFinancials", "SpawnIssueThreads"),
                    buildTask("SpawnIssueThreads", "InvestigateAccounting", "InvestigateCompliance",
                        "InvestigateOperations"),
                    buildTask("InvestigateAccounting", "DocumentAccounting"),
                    buildTask("InvestigateCompliance", "DocumentCompliance"),
                    buildTask("InvestigateOperations", "DocumentOperations"),
                    buildTask("DocumentAccounting", "FinalizeAudit"),
                    buildTask("DocumentCompliance", "FinalizeAudit"),
                    buildTask("DocumentOperations", "FinalizeAudit"),
                    buildTask("FinalizeAudit", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Audit thread split should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Sound: Insurance claim spawns parallel reviewer threads")
        void testInsuranceClaimThreadSplit() {
            var workflow = buildWorkflow(
                "InsuranceClaimThreadSplit",
                "ReceiveClaim",
                List.of(
                    buildTask("ReceiveClaim", "InitialValidation"),
                    buildTask("InitialValidation", "DispatchToReviewers"),
                    buildTask("DispatchToReviewers", "MedicalReview", "LegalReview", "FinancialReview"),
                    buildTask("MedicalReview", "AggregateReviews"),
                    buildTask("LegalReview", "AggregateReviews"),
                    buildTask("FinancialReview", "AggregateReviews"),
                    buildTask("AggregateReviews", "MakeFinalDecision"),
                    buildTask("MakeFinalDecision", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Insurance claim thread split should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Sound: Product order creates parallel fulfillment threads")
        void testProductOrderThreadSplit() {
            var workflow = buildWorkflow(
                "ProductOrderThreadSplit",
                "ProcessOrder",
                List.of(
                    buildTask("ProcessOrder", "ValidateOrder"),
                    buildTask("ValidateOrder", "SpawnFulfillmentThreads"),
                    buildTask("SpawnFulfillmentThreads", "ProcessPayment", "PackageItems", "NotifyShipper"),
                    buildTask("ProcessPayment", "ConfirmOrder"),
                    buildTask("PackageItems", "ConfirmOrder"),
                    buildTask("NotifyShipper", "ConfirmOrder"),
                    buildTask("ConfirmOrder", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Product order thread split should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Unsound: Thread split target is unreachable")
        void testThreadSplitUnreachableThread() {
            var workflow = buildWorkflow(
                "BadThreadSplit",
                "StartTask",
                List.of(
                    buildTask("StartTask", "SplitPoint"),
                    buildTask("SplitPoint", "Thread1", "Thread2"),
                    buildTask("Thread1", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertFalse(result.sound(),
                "Thread split with unreachable thread target should be unsound");
        }
    }

    // =========================================================================
    // WCP-42: Thread Merge
    // =========================================================================

    @Nested
    @DisplayName("WCP-42: Thread Merge - Merging threads back after parallel activities")
    class ThreadMergePattern {

        @Test
        @DisplayName("Sound: Merging audit investigation threads")
        void testAuditThreadMerge() {
            var workflow = buildWorkflow(
                "AuditThreadMerge",
                "StartAudit",
                List.of(
                    buildTask("StartAudit", "SplitInvestigations"),
                    buildTask("SplitInvestigations", "InvestigateFinancials", "InvestigateCompliance"),
                    buildTask("InvestigateFinancials", "MergeInvestigations"),
                    buildTask("InvestigateCompliance", "MergeInvestigations"),
                    buildTask("MergeInvestigations", "PrepareReport"),
                    buildTask("PrepareReport", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Audit thread merge should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Sound: Merging parallel approval threads")
        void testApprovalThreadMerge() {
            var workflow = buildWorkflow(
                "ApprovalThreadMerge",
                "SubmitRequest",
                List.of(
                    buildTask("SubmitRequest", "DispatchReviewers"),
                    buildTask("DispatchReviewers", "TechnicalReview", "FinancialReview", "LegalReview"),
                    buildTask("TechnicalReview", "MergeReviews"),
                    buildTask("FinancialReview", "MergeReviews"),
                    buildTask("LegalReview", "MergeReviews"),
                    buildTask("MergeReviews", "MakeFinalDecision"),
                    buildTask("MakeFinalDecision", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Approval thread merge should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Sound: Merging fulfillment process threads")
        void testFulfillmentThreadMerge() {
            var workflow = buildWorkflow(
                "FulfillmentThreadMerge",
                "ReceiveOrder",
                List.of(
                    buildTask("ReceiveOrder", "ParallelizeProcessing"),
                    buildTask("ParallelizeProcessing", "PickItems", "ProcessPayment", "PrepareShipment"),
                    buildTask("PickItems", "ConsolidateResults"),
                    buildTask("ProcessPayment", "ConsolidateResults"),
                    buildTask("PrepareShipment", "ConsolidateResults"),
                    buildTask("ConsolidateResults", "ShipOrder"),
                    buildTask("ShipOrder", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Fulfillment thread merge should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Unsound: Merge point not connected to end")
        void testThreadMergeDeadEnd() {
            var workflow = buildWorkflow(
                "BadThreadMerge",
                "StartTask",
                List.of(
                    buildTask("StartTask", "SplitTask"),
                    buildTask("SplitTask", "Thread1", "Thread2"),
                    buildTask("Thread1", "MergeTask"),
                    buildTask("Thread2", "MergeTask")
                )
            );

            var result = verifier.verify(workflow);

            assertFalse(result.sound(),
                "Thread merge not connected to end should be unsound");
        }

        @Test
        @DisplayName("Unsound: Merge references undefined task")
        void testThreadMergeUndefinedReference() {
            var workflow = buildWorkflow(
                "BadThreadMergeRef",
                "StartTask",
                List.of(
                    buildTask("StartTask", "SplitTask"),
                    buildTask("SplitTask", "Thread1", "Thread2"),
                    buildTask("Thread1", "end"),
                    buildTask("Thread2", "UndefinedMerge")
                )
            );

            var result = verifier.verify(workflow);

            assertFalse(result.sound(),
                "Thread merge referencing undefined task should be unsound");
        }
    }

    // =========================================================================
    // WCP-43: Explicit Termination with Cancellation Region
    // =========================================================================

    @Nested
    @DisplayName("WCP-43: Explicit Termination with Cancellation - Atomic region cancellation")
    class ExplicitTerminationWithCancellationRegionPattern {

        @Test
        @DisplayName("Sound: Fraud detection cancels entire order fulfillment region")
        void testFraudDetectionCancellationRegion() {
            var workflow = buildWorkflow(
                "OrderWithFraudCancellation",
                "ReceiveOrder",
                List.of(
                    buildTask("ReceiveOrder", "ValidateOrder"),
                    buildTask("ValidateOrder", "CheckFraud"),
                    buildTask("CheckFraud", "FraudDetected", "ProcessOrder"),
                    buildTask("FraudDetected", "CancelOrderFulfillment"),
                    buildTask("ProcessOrder", "PickItems"),
                    buildTask("PickItems", "PackItems"),
                    buildTask("PackItems", "ProcessPayment"),
                    buildTask("ProcessPayment", "GenerateShipment"),
                    buildTask("GenerateShipment", "end"),
                    buildTask("CancelOrderFulfillment", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Fraud detection with region cancellation should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Sound: Business rule violation terminates entire processing region")
        void testBusinessRuleViolationCancellation() {
            var workflow = buildWorkflow(
                "LoanWithBusinessRuleCancellation",
                "ReceiveApplication",
                List.of(
                    buildTask("ReceiveApplication", "InitialValidation"),
                    buildTask("InitialValidation", "CheckBusinessRules"),
                    buildTask("CheckBusinessRules", "RuleViolated", "ProcessApplication"),
                    buildTask("RuleViolated", "TerminateApplicationProcessing"),
                    buildTask("ProcessApplication", "ValidateIncome"),
                    buildTask("ValidateIncome", "CheckCredit"),
                    buildTask("CheckCredit", "MakeDecision"),
                    buildTask("MakeDecision", "end"),
                    buildTask("TerminateApplicationProcessing", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Business rule violation with region cancellation should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Sound: Critical issue halts all parallel operations")
        void testCriticalIssueCancellationRegion() {
            var workflow = buildWorkflow(
                "ParallelProcessWithCriticalIssue",
                "StartProcessing",
                List.of(
                    buildTask("StartProcessing", "MonitorSystemHealth"),
                    buildTask("MonitorSystemHealth", "CriticalIssueDetermined", "ContinueProcessing"),
                    buildTask("CriticalIssueDetermined", "CancelAllOperations"),
                    buildTask("ContinueProcessing", "ProcessPayment", "ProcessInventory", "ProcessShipment"),
                    buildTask("ProcessPayment", "end"),
                    buildTask("ProcessInventory", "end"),
                    buildTask("ProcessShipment", "end"),
                    buildTask("CancelAllOperations", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Critical issue region cancellation should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Sound: Policy violation triggers entire cancellation region")
        void testPolicyViolationCancellationRegion() {
            var workflow = buildWorkflow(
                "ComplianceWorkflowWithCancellation",
                "SubmitTransaction",
                List.of(
                    buildTask("SubmitTransaction", "CheckCompliance"),
                    buildTask("CheckCompliance", "PolicyViolation", "ContinueTransaction"),
                    buildTask("PolicyViolation", "CancelTransaction"),
                    buildTask("ContinueTransaction", "AuthorizePayment"),
                    buildTask("AuthorizePayment", "ExecutePayment"),
                    buildTask("ExecutePayment", "ConfirmCompletion"),
                    buildTask("ConfirmCompletion", "end"),
                    buildTask("CancelTransaction", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Policy violation cancellation region should be sound; violations: "
                    + result.violations());
        }

        @Test
        @DisplayName("Unsound: Cancellation region missing exit (incomplete termination)")
        void testCancellationRegionMissingExit() {
            var workflow = buildWorkflow(
                "BadCancellationRegion",
                "StartTask",
                List.of(
                    buildTask("StartTask", "CheckCondition"),
                    buildTask("CheckCondition", "RegularPath", "CancelPath"),
                    buildTask("RegularPath", "end"),
                    buildTask("CancelPath", "CancelOperations")
                )
            );

            var result = verifier.verify(workflow);

            assertFalse(result.sound(),
                "Cancellation region without complete exit should be unsound");
        }

        @Test
        @DisplayName("Unsound: Termination references undefined cancellation task")
        void testCancellationRegionUndefinedTask() {
            var workflow = buildWorkflow(
                "BadCancellationRef",
                "StartTask",
                List.of(
                    buildTask("StartTask", "CheckCondition"),
                    buildTask("CheckCondition", "ProcessPath", "UndefinedCancellation"),
                    buildTask("ProcessPath", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertFalse(result.sound(),
                "Cancellation region referencing undefined task should be unsound");
        }
    }
}
