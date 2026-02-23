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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive JUnit 5 tests for Workflow Control Patterns (WCPs) 29-36.
 *
 * <p>Tests cover business scenarios for advanced workflow patterns:
 * <ul>
 *   <li><strong>WCP-29</strong>: Dynamic Parallel Multi-Instance Activity</li>
 *   <li><strong>WCP-30</strong>: Acyclic Spaghetti Process</li>
 *   <li><strong>WCP-31</strong>: Implicit Termination with AND-Join</li>
 *   <li><strong>WCP-32</strong>: Thread Merge</li>
 *   <li><strong>WCP-33</strong>: Thread Split</li>
 *   <li><strong>WCP-34</strong>: Static Partial Join for Multiple Instances</li>
 *   <li><strong>WCP-35</strong>: Cancelling Partial Join for Multiple Instances</li>
 *   <li><strong>WCP-36</strong>: Dynamic Partial Join for Multiple Instances</li>
 * </ul>
 *
 * <p>Uses real workflow specifications (not mocks) based on business scenarios:
 * e-commerce processing, expense approvals, multi-resource reviews, and
 * quality assurance workflows. All tests operate via real
 * {@link WorkflowSoundnessVerifier} instances.
 *
 * <p>Test coverage includes:
 * <ul>
 *   <li>Happy-path workflows (sound structures)</li>
 *   <li>Unsound workflows (dead-ends, missing flows)</li>
 *   <li>Edge cases (single vs. multiple instances, threshold boundaries)</li>
 *   <li>Multi-instance configuration validation</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("WCP 29-36: Business Patterns (Dynamic Multi-Instance, Spaghetti, Joins)")
class WcpBusinessPatterns29to36Test {

    private WorkflowSoundnessVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new WorkflowSoundnessVerifier();
    }

    // =========================================================================
    // WCP-29: Dynamic Parallel Multi-Instance Activity
    // =========================================================================

    /**
     * WCP-29: Dynamically spawned parallel tasks based on runtime data.
     *
     * <p>Business scenario: E-commerce order fulfillment where items in a
     * shopping cart are processed in parallel dynamically (cart size unknown
     * until runtime).
     */
    @Nested
    @DisplayName("WCP-29: Dynamic Parallel Multi-Instance Activity")
    class Wcp29DynamicMultiInstanceTests {

        /**
         * Happy path: Order processing with dynamic item processing.
         * Each item in the cart is processed in parallel, with minimum of 1
         * and maximum of 100 items.
         */
        @Test
        @DisplayName("ProcessOrder with dynamic parallel item processing (sound)")
        void testProcessOrderWithDynamicItemsSound() {
            String yaml = """
                    name: DynamicCartProcessing
                    first: ValidateOrder
                    tasks:
                      - id: ValidateOrder
                        flows: [ProcessItems]
                      - id: ProcessItems
                        flows: [end]
                        multiInstance:
                          min: 1
                          max: 100
                          mode: dynamic
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Dynamic multi-instance cart processing should be sound");
        }

        /**
         * Realistic business scenario: Process items with threshold for
         * early completion. If 50% of items complete successfully, begin
         * shipment planning immediately.
         */
        @Test
        @DisplayName("ProcessItems with dynamic threshold for partial completion (sound)")
        void testProcessItemsWithDynamicThresholdSound() {
            String yaml = """
                    name: AdaptiveShipping
                    first: ValidateOrder
                    tasks:
                      - id: ValidateOrder
                        flows: [ProcessItems]
                      - id: ProcessItems
                        flows: [ShipOrder]
                        multiInstance:
                          min: 1
                          max: 1000
                          threshold: 50
                          mode: dynamic
                      - id: ShipOrder
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Dynamic threshold-based item processing should be sound");
        }

        /**
         * Unsound: Dynamic multi-instance with no exit flow defined.
         */
        @Test
        @DisplayName("ProcessItems with no exit flow (unsound dead-end)")
        void testProcessItemsNoExitFlowUnsound() {
            String yaml = """
                    name: BrokenDynamicProcessing
                    first: ValidateOrder
                    tasks:
                      - id: ValidateOrder
                        flows: [ProcessItems]
                      - id: ProcessItems
                        multiInstance:
                          min: 1
                          max: 100
                          mode: dynamic
                    """;

            var result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(),
                "Dynamic multi-instance with no exit should be unsound");
        }

        /**
         * Real-world scenario: Process payment for each item in parallel,
         * with dynamic count. Payment processing may vary by item type.
         */
        @Test
        @DisplayName("ProcessPayments for variable-size order (sound)")
        void testProcessPaymentsVariableOrderSound() {
            String yaml = """
                    name: ParallelPaymentProcessing
                    first: ValidateOrder
                    tasks:
                      - id: ValidateOrder
                        flows: [ProcessPayments]
                      - id: ProcessPayments
                        flows: [ConfirmShipping]
                        multiInstance:
                          min: 1
                          max: 50
                          mode: dynamic
                      - id: ConfirmShipping
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Parallel payment processing should be sound");
        }
    }

    // =========================================================================
    // WCP-30: Acyclic Spaghetti Process
    // =========================================================================

    /**
     * WCP-30: Complex non-structured flow with many conditional paths.
     *
     * <p>Business scenario: Insurance claims processing with multiple
     * decision points, documentation checks, and conditional escalations.
     */
    @Nested
    @DisplayName("WCP-30: Acyclic Spaghetti Process")
    class Wcp30SpaghettiProcessTests {

        /**
         * Happy path: Complex insurance claims workflow with multiple
         * conditional branches and converging paths. All paths lead to
         * resolution.
         */
        @Test
        @DisplayName("InsuranceClaim with complex branching (sound)")
        void testInsuranceClaimComplexBranchingSound() {
            String yaml = """
                    name: InsuranceClaimsProcessing
                    first: RegisterClaim
                    tasks:
                      - id: RegisterClaim
                        flows: [InitialReview, DocumentationRequest]
                      - id: InitialReview
                        flows: [VerifyDocuments, RequestSpecialistReview, Deny]
                      - id: DocumentationRequest
                        flows: [VerifyDocuments]
                      - id: VerifyDocuments
                        flows: [Approve, RequestSpecialistReview, Deny]
                      - id: RequestSpecialistReview
                        flows: [SpecialistApproval, Deny]
                      - id: SpecialistApproval
                        flows: [Approve, Deny]
                      - id: Approve
                        flows: [ProcessPayment]
                      - id: Deny
                        flows: [NotifyDenial]
                      - id: ProcessPayment
                        flows: [end]
                      - id: NotifyDenial
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Complex insurance claim workflow should be sound");
        }

        /**
         * Real-world complexity: Claim processing with multiple escalation
         * paths for different claim types and amounts.
         */
        @Test
        @DisplayName("MultiPathClaimReview with different escalations (sound)")
        void testMultiPathClaimReviewSound() {
            String yaml = """
                    name: EscalatedClaimsProcessing
                    first: ReceiveClaim
                    tasks:
                      - id: ReceiveClaim
                        flows: [AssessAmount, CheckDocumentation]
                      - id: AssessAmount
                        flows: [StandardReview, HighValueReview]
                      - id: CheckDocumentation
                        flows: [StandardReview, HighValueReview, RequestAdditional]
                      - id: RequestAdditional
                        flows: [StandardReview, HighValueReview]
                      - id: StandardReview
                        flows: [ApproveStandard, RequestMedialInfo, Escalate]
                      - id: HighValueReview
                        flows: [RequestMedialInfo, ManualApproval]
                      - id: RequestMedialInfo
                        flows: [ManualApproval, Reject]
                      - id: ManualApproval
                        flows: [ApproveStandard, Reject]
                      - id: Escalate
                        flows: [ManualApproval, Reject]
                      - id: ApproveStandard
                        flows: [IssuePayment]
                      - id: Reject
                        flows: [NotifyRejection]
                      - id: IssuePayment
                        flows: [end]
                      - id: NotifyRejection
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Multi-path claim processing should be sound");
        }

        /**
         * Unsound: Spaghetti process with a task that has no path to
         * completion (dead-end).
         */
        @Test
        @DisplayName("SpaghettiBroken with unreachable end (unsound dead-end)")
        void testSpaghettiBrokenDeadEndUnsound() {
            String yaml = """
                    name: BrokenClaimsProcessing
                    first: RegisterClaim
                    tasks:
                      - id: RegisterClaim
                        flows: [Review, QuickApprove]
                      - id: Review
                        flows: [Approve, Deny]
                      - id: QuickApprove
                        flows: [end]
                      - id: Approve
                        flows: [end]
                      - id: Deny
                        flows: [NotifyDenial]
                      - id: NotifyDenial
                    """;

            var result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(),
                "Spaghetti with dead-end task should be unsound");
        }

        /**
         * Complex spaghetti with multiple convergence points: expense
         * approval workflow with escalations and manager review.
         */
        @Test
        @DisplayName("ExpenseApproval with manager escalation (sound)")
        void testExpenseApprovalWithEscalationSound() {
            String yaml = """
                    name: ExpenseApprovalSpaghetti
                    first: SubmitExpense
                    tasks:
                      - id: SubmitExpense
                        flows: [AutoApprove, ManagerReview]
                      - id: AutoApprove
                        flows: [CheckBudget]
                      - id: ManagerReview
                        flows: [ApproveExpense, RequestReceipts]
                      - id: RequestReceipts
                        flows: [CheckBudget, ManagerReview]
                      - id: CheckBudget
                        flows: [ApproveExpense, DenyExpense, EscalateToDirector]
                      - id: EscalateToDirector
                        flows: [ApproveExpense, DenyExpense]
                      - id: ApproveExpense
                        flows: [ProcessReimbursement]
                      - id: DenyExpense
                        flows: [NotifyRejection]
                      - id: ProcessReimbursement
                        flows: [end]
                      - id: NotifyRejection
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Expense approval workflow should be sound");
        }
    }

    // =========================================================================
    // WCP-31: Implicit Termination with AND-Join
    // =========================================================================

    /**
     * WCP-31: Workflow terminates when all parallel paths complete,
     * including those joined by AND-join operators.
     *
     * <p>Business scenario: Order fulfillment where multiple tasks
     * (inventory check, payment processing, shipping preparation) must
     * complete before final confirmation.
     */
    @Nested
    @DisplayName("WCP-31: Implicit Termination with AND-Join")
    class Wcp31ImplicitTerminationTests {

        /**
         * Happy path: Order processing with AND-join where all three tasks
         * must complete before order confirmation.
         */
        @Test
        @DisplayName("OrderFulfillment with AND-join on three parallel paths (sound)")
        void testOrderFulfillmentAndJoinSound() {
            String yaml = """
                    name: OrderFulfillmentAndJoin
                    first: ProcessOrder
                    tasks:
                      - id: ProcessOrder
                        flows: [CheckInventory, ProcessPayment, PrepareShipping]
                      - id: CheckInventory
                        flows: [ConfirmOrder]
                      - id: ProcessPayment
                        flows: [ConfirmOrder]
                      - id: PrepareShipping
                        flows: [ConfirmOrder]
                      - id: ConfirmOrder
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "AND-join order fulfillment should be sound");
        }

        /**
         * Realistic scenario: Loan processing with multiple parallel
         * verification tasks that must all succeed before approval.
         */
        @Test
        @DisplayName("LoanApproval with AND-join on identity, credit, income checks (sound)")
        void testLoanApprovalAndJoinSound() {
            String yaml = """
                    name: LoanProcessingAndJoin
                    first: ReceiveApplication
                    tasks:
                      - id: ReceiveApplication
                        flows: [VerifyIdentity, CheckCreditHistory, VerifyIncome]
                      - id: VerifyIdentity
                        flows: [ApproveOrDeny]
                      - id: CheckCreditHistory
                        flows: [ApproveOrDeny]
                      - id: VerifyIncome
                        flows: [ApproveOrDeny]
                      - id: ApproveOrDeny
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Loan approval AND-join should be sound");
        }

        /**
         * Unsound: AND-join where one path has no outgoing flows (dead-end).
         */
        @Test
        @DisplayName("AndJoinBroken with dead-end path (unsound)")
        void testAndJoinBrokenMissingFlowUnsound() {
            String yaml = """
                    name: BrokenAndJoin
                    first: ProcessOrder
                    tasks:
                      - id: ProcessOrder
                        flows: [CheckInventory, ProcessPayment, PrepareShipping]
                      - id: CheckInventory
                        flows: [ConfirmOrder]
                      - id: ProcessPayment
                        flows: []
                      - id: PrepareShipping
                        flows: [ConfirmOrder]
                      - id: ConfirmOrder
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(),
                "AND-join with dead-end path should be unsound");
        }

        /**
         * Real-world scenario: Project approval with AND-join across
         * budget review, technical review, and stakeholder approval.
         */
        @Test
        @DisplayName("ProjectApproval with three concurrent reviews (sound)")
        void testProjectApprovalConcurrentReviewsSound() {
            String yaml = """
                    name: ProjectApprovalProcess
                    first: SubmitProject
                    tasks:
                      - id: SubmitProject
                        flows: [BudgetReview, TechnicalReview, StakeholderReview]
                      - id: BudgetReview
                        flows: [MakeDecision]
                      - id: TechnicalReview
                        flows: [MakeDecision]
                      - id: StakeholderReview
                        flows: [MakeDecision]
                      - id: MakeDecision
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Project approval with concurrent reviews should be sound");
        }
    }

    // =========================================================================
    // WCP-32: Thread Merge
    // =========================================================================

    /**
     * WCP-32: Converging multiple parallel threads after concurrent
     * activities (XOR-join merging multiple paths).
     *
     * <p>Business scenario: Customer service routing where requests may
     * take different paths (expedited vs. standard review) but ultimately
     * converge for resolution.
     */
    @Nested
    @DisplayName("WCP-32: Thread Merge")
    class Wcp32ThreadMergeTests {

        /**
         * Happy path: Support ticket routing with XOR-merge converging
         * fast-track and standard review paths.
         */
        @Test
        @DisplayName("SupportTicket with XOR-merge on expedited/standard (sound)")
        void testSupportTicketXorMergeSound() {
            String yaml = """
                    name: CustomerSupportMerge
                    first: ReceiveTicket
                    tasks:
                      - id: ReceiveTicket
                        flows: [FastTrackReview, StandardReview]
                      - id: FastTrackReview
                        flows: [ResolveTicket]
                      - id: StandardReview
                        flows: [ResolveTicket]
                      - id: ResolveTicket
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "XOR-merge support ticket should be sound");
        }

        /**
         * Real-world scenario: Procurement with thread merge across
         * competitive bidding vs. direct vendor paths.
         */
        @Test
        @DisplayName("Procurement with XOR-merge on competitive/direct paths (sound)")
        void testProcurementXorMergeSound() {
            String yaml = """
                    name: ProcurementRouting
                    first: InitiateProcurement
                    tasks:
                      - id: InitiateProcurement
                        flows: [CompetitiveBidding, DirectVendor]
                      - id: CompetitiveBidding
                        flows: [SelectVendor]
                      - id: DirectVendor
                        flows: [SelectVendor]
                      - id: SelectVendor
                        flows: [CreatePurchaseOrder]
                      - id: CreatePurchaseOrder
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Procurement XOR-merge should be sound");
        }

        /**
         * Complex thread merge: Employee onboarding with different
         * department-specific tracks converging on final orientation.
         */
        @Test
        @DisplayName("EmployeeOnboarding with department-specific track merge (sound)")
        void testEmployeeOnboardingTrackMergeSound() {
            String yaml = """
                    name: EmployeeOnboardingMerge
                    first: RegisterEmployee
                    tasks:
                      - id: RegisterEmployee
                        flows: [ItTraining, HrTraining, DepartmentTraining]
                      - id: ItTraining
                        flows: [SetupWorkstation]
                      - id: HrTraining
                        flows: [SetupWorkstation]
                      - id: DepartmentTraining
                        flows: [SetupWorkstation]
                      - id: SetupWorkstation
                        flows: [FinalOrientation]
                      - id: FinalOrientation
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Onboarding track merge should be sound");
        }

        /**
         * Unsound: XOR-merge with one path having no outgoing flows (dead-end).
         */
        @Test
        @DisplayName("ThreadMergeBroken with dead-end path (unsound dead-end)")
        void testThreadMergeBrokenDeadEndUnsound() {
            String yaml = """
                    name: BrokenThreadMerge
                    first: ReceiveRequest
                    tasks:
                      - id: ReceiveRequest
                        flows: [PathA, PathB]
                      - id: PathA
                        flows: []
                      - id: PathB
                        flows: [Merge]
                      - id: Merge
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(),
                "Thread merge with dead-end path should be unsound");
        }
    }

    // =========================================================================
    // WCP-33: Thread Split
    // =========================================================================

    /**
     * WCP-33: Splitting into threads that may have different numbers of
     * tokens flowing through.
     *
     * <p>Business scenario: Order processing where different order types
     * require different processing paths with varying complexity.
     */
    @Nested
    @DisplayName("WCP-33: Thread Split")
    class Wcp33ThreadSplitTests {

        /**
         * Happy path: AND-split where all branches must be traversed.
         */
        @Test
        @DisplayName("ProcessOrder with AND-split on verification and payment (sound)")
        void testProcessOrderAndSplitSound() {
            String yaml = """
                    name: OrderProcessingAndSplit
                    first: ValidateOrder
                    tasks:
                      - id: ValidateOrder
                        flows: [VerifyInventory, ProcessPayment]
                      - id: VerifyInventory
                        flows: [ScheduleShipment]
                      - id: ProcessPayment
                        flows: [ScheduleShipment]
                      - id: ScheduleShipment
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "AND-split order processing should be sound");
        }

        /**
         * Real-world scenario: Claim processing with AND-split where all
         * three verification branches must execute concurrently.
         */
        @Test
        @DisplayName("ClaimProcessing with AND-split on three verifications (sound)")
        void testClaimProcessingAndSplitSound() {
            String yaml = """
                    name: ClaimVerificationAndSplit
                    first: ReceiveClaim
                    tasks:
                      - id: ReceiveClaim
                        flows: [VerifyPolicyholder, VerifyIncident, VerifyBeneficiary]
                      - id: VerifyPolicyholder
                        flows: [ApproveClaim]
                      - id: VerifyIncident
                        flows: [ApproveClaim]
                      - id: VerifyBeneficiary
                        flows: [ApproveClaim]
                      - id: ApproveClaim
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Claim verification AND-split should be sound");
        }

        /**
         * Realistic complex scenario: Content publication with AND-split
         * across editorial review, legal review, and marketing approval.
         */
        @Test
        @DisplayName("ContentPublicationAndSplit with concurrent reviews (sound)")
        void testContentPublicationAndSplitSound() {
            String yaml = """
                    name: ContentPublicationAndSplit
                    first: SubmitContent
                    tasks:
                      - id: SubmitContent
                        flows: [EditorialReview, LegalReview, MarketingApproval]
                      - id: EditorialReview
                        flows: [PublishContent]
                      - id: LegalReview
                        flows: [PublishContent]
                      - id: MarketingApproval
                        flows: [PublishContent]
                      - id: PublishContent
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Content publication AND-split should be sound");
        }

        /**
         * Unsound: AND-split where one branch is missing a task definition.
         */
        @Test
        @DisplayName("AndSplitBroken with undefined flow target (unsound)")
        void testAndSplitBrokenUndefinedFlowUnsound() {
            String yaml = """
                    name: BrokenAndSplit
                    first: ValidateOrder
                    tasks:
                      - id: ValidateOrder
                        flows: [VerifyInventory, MissingTask]
                      - id: VerifyInventory
                        flows: [ScheduleShipment]
                      - id: ScheduleShipment
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(),
                "AND-split with undefined flow should be unsound");
        }
    }

    // =========================================================================
    // WCP-34: Static Partial Join for Multiple Instances
    // =========================================================================

    /**
     * WCP-34: Fixed quorum requirement for multi-instance tasks.
     *
     * <p>Business scenario: Quality assurance where 3 of 5 quality checks
     * must pass before product release (fixed threshold set at design time).
     */
    @Nested
    @DisplayName("WCP-34: Static Partial Join for Multiple Instances")
    class Wcp34StaticPartialJoinTests {

        /**
         * Happy path: Quality checks with static threshold (3 of 5 must pass).
         */
        @Test
        @DisplayName("QualityChecks with static threshold 3 of 5 (sound)")
        void testQualityChecksStaticThresholdSound() {
            String yaml = """
                    name: QualityAssuranceStatic
                    first: InitiateQA
                    tasks:
                      - id: InitiateQA
                        flows: [PerformQualityCheck]
                      - id: PerformQualityCheck
                        flows: [end]
                        multiInstance:
                          min: 5
                          max: 5
                          threshold: 3
                          mode: static
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Static partial join with threshold should be sound");
        }

        /**
         * Real-world scenario: Approval process where 2 of 3 managers must
         * approve high-value decisions.
         */
        @Test
        @DisplayName("HighValueApproval with static threshold 2 of 3 (sound)")
        void testHighValueApprovalStaticThresholdSound() {
            String yaml = """
                    name: MultiManagerApprovalStatic
                    first: SubmitForApproval
                    tasks:
                      - id: SubmitForApproval
                        flows: [ObtainApproval]
                      - id: ObtainApproval
                        flows: [ProcessDecision]
                        multiInstance:
                          min: 3
                          max: 3
                          threshold: 2
                          mode: static
                      - id: ProcessDecision
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Multi-manager approval with static threshold should be sound");
        }

        /**
         * Complex scenario: Document review where 4 of 10 reviewers must
         * approve, with all reviews happening in parallel.
         */
        @Test
        @DisplayName("DocumentReview with static threshold 4 of 10 (sound)")
        void testDocumentReviewStaticThresholdSound() {
            String yaml = """
                    name: DocumentReviewProcess
                    first: SubmitDocument
                    tasks:
                      - id: SubmitDocument
                        flows: [ReviewDocument]
                      - id: ReviewDocument
                        flows: [FinalizeApproval]
                        multiInstance:
                          min: 10
                          max: 10
                          threshold: 4
                          mode: static
                      - id: FinalizeApproval
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Document review static threshold should be sound");
        }

        /**
         * Unsound: Static partial join with no exit flow.
         */
        @Test
        @DisplayName("StaticThresholdBroken with no exit (unsound dead-end)")
        void testStaticThresholdBrokenNoExitUnsound() {
            String yaml = """
                    name: BrokenStaticThreshold
                    first: InitiateQA
                    tasks:
                      - id: InitiateQA
                        flows: [PerformQualityCheck]
                      - id: PerformQualityCheck
                        multiInstance:
                          min: 5
                          max: 5
                          threshold: 3
                          mode: static
                    """;

            var result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(),
                "Static threshold with no exit should be unsound");
        }
    }

    // =========================================================================
    // WCP-35: Cancelling Partial Join for Multiple Instances
    // =========================================================================

    /**
     * WCP-35: Quorum met → remaining instances cancelled.
     *
     * <p>Business scenario: Competitive bidding where first 3 acceptable
     * bids received triggers immediate cancellation of remaining bid
     * collection and selection process begins.
     */
    @Nested
    @DisplayName("WCP-35: Cancelling Partial Join for Multiple Instances")
    class Wcp35CancellingPartialJoinTests {

        /**
         * Happy path: Competitive bidding with early termination at
         * threshold.
         */
        @Test
        @DisplayName("CompetitiveBidding with threshold-based cancellation (sound)")
        void testCompetitiveBiddingCancellingThresholdSound() {
            String yaml = """
                    name: CompetitiveBiddingCancelling
                    first: PublishBidRequest
                    tasks:
                      - id: PublishBidRequest
                        flows: [ReceiveBids]
                      - id: ReceiveBids
                        flows: [SelectWinner]
                        multiInstance:
                          min: 1
                          max: 20
                          threshold: 3
                          mode: dynamic
                      - id: SelectWinner
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Competitive bidding with threshold cancellation should be sound");
        }

        /**
         * Real-world scenario: Insurance quote collection where first 5
         * compliant quotes triggers comparison and selection.
         */
        @Test
        @DisplayName("InsuranceQuoteCollection with early termination (sound)")
        void testInsuranceQuoteCollectionSound() {
            String yaml = """
                    name: InsuranceQuoteCancelling
                    first: RequestQuotes
                    tasks:
                      - id: RequestQuotes
                        flows: [CollectQuotes]
                      - id: CollectQuotes
                        flows: [CompareQuotes]
                        multiInstance:
                          min: 1
                          max: 50
                          threshold: 5
                          mode: dynamic
                      - id: CompareQuotes
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Insurance quote collection should be sound");
        }

        /**
         * Scenario: Supplier approval where 3 positive certifications
         * triggers immediate supplier onboarding (remaining verification
         * stopped).
         */
        @Test
        @DisplayName("SupplierApprovalCancelling with certification threshold (sound)")
        void testSupplierApprovalCancellingSound() {
            String yaml = """
                    name: SupplierOnboardingCancelling
                    first: SubmitSupplierProfile
                    tasks:
                      - id: SubmitSupplierProfile
                        flows: [VerifyCertification]
                      - id: VerifyCertification
                        flows: [OnboardSupplier]
                        multiInstance:
                          min: 1
                          max: 10
                          threshold: 3
                          mode: dynamic
                      - id: OnboardSupplier
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Supplier approval with cancellation should be sound");
        }

        /**
         * Unsound: Cancelling partial join with flow to undefined task.
         */
        @Test
        @DisplayName("CancellingThresholdBroken with undefined flow (unsound)")
        void testCancellingThresholdBrokenUndefinedFlowUnsound() {
            String yaml = """
                    name: BrokenCancellingThreshold
                    first: PublishBidRequest
                    tasks:
                      - id: PublishBidRequest
                        flows: [ReceiveBids]
                      - id: ReceiveBids
                        flows: [UndefinedTask]
                        multiInstance:
                          min: 1
                          max: 20
                          threshold: 3
                          mode: dynamic
                    """;

            var result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(),
                "Cancelling threshold with undefined flow should be unsound");
        }
    }

    // =========================================================================
    // WCP-36: Dynamic Partial Join for Multiple Instances
    // =========================================================================

    /**
     * WCP-36: Quorum determined at runtime.
     *
     * <p>Business scenario: Survey collection where the required number of
     * responses is calculated dynamically based on population size or other
     * runtime factors.
     */
    @Nested
    @DisplayName("WCP-36: Dynamic Partial Join for Multiple Instances")
    class Wcp36DynamicPartialJoinTests {

        /**
         * Happy path: Survey with dynamic threshold based on runtime data.
         */
        @Test
        @DisplayName("SurveyCollection with dynamic threshold (sound)")
        void testSurveyCollectionDynamicThresholdSound() {
            String yaml = """
                    name: SurveyCollectionDynamic
                    first: InitiateSurvey
                    tasks:
                      - id: InitiateSurvey
                        flows: [CollectResponse]
                      - id: CollectResponse
                        flows: [AnalyzeResults]
                        multiInstance:
                          min: 1
                          max: 1000
                          threshold: null
                          mode: dynamic
                      - id: AnalyzeResults
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Survey with dynamic threshold should be sound");
        }

        /**
         * Real-world scenario: User feedback collection where required
         * sample size is determined at runtime based on user population.
         */
        @Test
        @DisplayName("UserFeedbackCollection with runtime sample size (sound)")
        void testUserFeedbackRuntimeSampleSound() {
            String yaml = """
                    name: UserFeedbackDynamic
                    first: CalculateSampleSize
                    tasks:
                      - id: CalculateSampleSize
                        flows: [CollectFeedback]
                      - id: CollectFeedback
                        flows: [CompileFeedback]
                        multiInstance:
                          min: 1
                          max: 500
                          threshold: null
                          mode: dynamic
                      - id: CompileFeedback
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "User feedback with runtime threshold should be sound");
        }

        /**
         * Complex scenario: Incident response where escalation level
         * determines required approvers dynamically.
         */
        @Test
        @DisplayName("IncidentResponseDynamic with runtime escalation level (sound)")
        void testIncidentResponseDynamicEscalationSound() {
            String yaml = """
                    name: IncidentResponseDynamic
                    first: DetermineEscalationLevel
                    tasks:
                      - id: DetermineEscalationLevel
                        flows: [ObtainApproval]
                      - id: ObtainApproval
                        flows: [ResolveIncident]
                        multiInstance:
                          min: 1
                          max: 20
                          threshold: null
                          mode: dynamic
                      - id: ResolveIncident
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Incident response dynamic escalation should be sound");
        }

        /**
         * Scenario: Consensus-based decision making where quorum is
         * calculated based on participant count.
         */
        @Test
        @DisplayName("ConsensusDecisionMaking with dynamic quorum (sound)")
        void testConsensusDecisionDynamicQuorumSound() {
            String yaml = """
                    name: ConsensusDecisionDynamic
                    first: GatherParticipants
                    tasks:
                      - id: GatherParticipants
                        flows: [CollectVote]
                      - id: CollectVote
                        flows: [CalculateConsensus]
                        multiInstance:
                          min: 1
                          max: 100
                          threshold: null
                          mode: dynamic
                      - id: CalculateConsensus
                        flows: [end]
                    """;

            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                "Consensus decision with dynamic quorum should be sound");
        }

        /**
         * Unsound: Dynamic partial join with no exit path after tasks.
         */
        @Test
        @DisplayName("DynamicThresholdBroken with no exit (unsound dead-end)")
        void testDynamicThresholdBrokenNoExitUnsound() {
            String yaml = """
                    name: BrokenDynamicThreshold
                    first: InitiateSurvey
                    tasks:
                      - id: InitiateSurvey
                        flows: [CollectResponse]
                      - id: CollectResponse
                        multiInstance:
                          min: 1
                          max: 1000
                          threshold: null
                          mode: dynamic
                    """;

            var result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(),
                "Dynamic threshold with no exit should be unsound");
        }
    }
}
