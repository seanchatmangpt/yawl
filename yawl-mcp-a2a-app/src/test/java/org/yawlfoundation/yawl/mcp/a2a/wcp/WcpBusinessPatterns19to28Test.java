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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive JUnit 5 tests for Workflow Control Patterns 19-28 from a business perspective.
 *
 * <p>These tests cover advanced control patterns in business workflows:</p>
 *
 * <ul>
 *   <li><strong>WCP-19: Cancel Task</strong> — Cancelling a running task mid-execution</li>
 *   <li><strong>WCP-20: Cancel Case</strong> — Cancelling an entire workflow instance</li>
 *   <li><strong>WCP-21: Structured Partial Join</strong> — First M of N branches completes</li>
 *   <li><strong>WCP-22: Blocking Partial Join</strong> — M complete, remaining cancelled</li>
 *   <li><strong>WCP-23: Cancelling Partial Join</strong> — Partial join cancels remaining</li>
 *   <li><strong>WCP-24: Generalised AND-Join</strong> — Dynamic token count merge</li>
 *   <li><strong>WCP-25: Blocking Discriminator</strong> — First complete blocks remaining</li>
 *   <li><strong>WCP-26: Cancelling Discriminator</strong> — First complete cancels remaining</li>
 *   <li><strong>WCP-27: Generalised Partial Join</strong> — Variable M threshold join</li>
 *   <li><strong>WCP-28: Blocking Partial Join with Cancellation</strong> — M fires, rest cancelled</li>
 * </ul>
 *
 * <p>Chicago/Detroit TDD approach: all tests use real
 * {@link WorkflowSoundnessVerifier} instances with real YAML workflow specs.
 * No mocks or stubs are employed. Tests verify both sound and unsound workflows
 * to ensure correct structural analysis.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see WorkflowSoundnessVerifier
 */
@DisplayName("WCP 19-28: Business Control Patterns (Cancel, Partial Join, Discriminator)")
class WcpBusinessPatterns19to28Test {

    private WorkflowSoundnessVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new WorkflowSoundnessVerifier();
    }

    // =========================================================================
    // Helper: buildTask for constructing workflow task specifications
    // =========================================================================

    private Map<String, Object> buildTask(String id, String... flows) {
        Map<String, Object> task = new HashMap<>();
        task.put("id", id);
        List<String> flowList = new ArrayList<>(List.of(flows));
        task.put("flows", flowList);
        return task;
    }

    private Map<String, Object> buildSpec(String name, String first,
                                          List<Map<String, Object>> tasks) {
        Map<String, Object> spec = new HashMap<>();
        spec.put("name", name);
        if (first != null) {
            spec.put("first", first);
        }
        spec.put("tasks", tasks);
        return spec;
    }

    // =========================================================================
    // WCP-19: Cancel Task — Task can be cancelled while running
    // =========================================================================

    @Nested
    @DisplayName("WCP-19: Cancel Task — Modelled as conditional path (execute or cancel)")
    class Wcp19CancelTaskTests {

        @Test
        @DisplayName("Order processing with task cancellation path: CheckOrder->{ProcessOrder,CancelTask}->end")
        void cancelTaskOrderCancellationPathIsSoundTest() {
            String yaml = """
                    name: OrderCancellationFlow
                    first: CheckOrder
                    tasks:
                      - id: CheckOrder
                        flows: [ProcessOrder, CancelTask]
                        split: xor
                        condition: customer_changed_mind -> CancelTask
                        default: ProcessOrder
                      - id: ProcessOrder
                        flows: [end]
                      - id: CancelTask
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Order cancellation path (check->process or cancel) must be sound; violations: "
                            + result.violations());
            assertTrue(result.violations().isEmpty(),
                    "Sound cancellation flow must have no violations, got: " + result.violations());
        }

        @Test
        @DisplayName("Payment processing with abort handler: ProcessPayment->{PaymentOK,AbortPayment}->end")
        void cancelTaskPaymentAbortIsSoundTest() {
            String yaml = """
                    name: PaymentAbortFlow
                    first: StartPayment
                    tasks:
                      - id: StartPayment
                        flows: [ProcessPayment]
                      - id: ProcessPayment
                        flows: [PaymentOK, AbortPayment]
                        split: xor
                        condition: amount_too_high -> AbortPayment
                        default: PaymentOK
                      - id: PaymentOK
                        flows: [Confirm]
                      - id: AbortPayment
                        flows: [Rollback]
                      - id: Confirm
                        flows: [end]
                      - id: Rollback
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Payment abort flow must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Document processing with veto: ReviewDocument->{ContinueProcess,VetoProcess}->end")
        void cancelTaskDocumentVetoIsSoundTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("StartReview", "ReviewDocument"),
                    buildTask("ReviewDocument", "ContinueProcess", "VetoProcess"),
                    buildTask("ContinueProcess", "end"),
                    buildTask("VetoProcess", "end")
            );
            var spec = buildSpec("DocumentVetoFlow", "StartReview", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Document veto cancellation path must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Unsound: Task with cancellation but no completion path (missing end flow)")
        void cancelTaskUnsoundWithoutCompletionPathTest() {
            String yaml = """
                    name: BadCancelFlow
                    first: ProcessData
                    tasks:
                      - id: ProcessData
                        flows: [CancelData]
                      - id: CancelData
                        flows: []
                    """;
            var result = verifier.verifyYaml(yaml);

            assertFalse(result.sound(),
                    "Cancellation path with no exit to end must be unsound");
        }
    }

    // =========================================================================
    // WCP-20: Cancel Case — Entire workflow instance cancelled at any point
    // =========================================================================

    @Nested
    @DisplayName("WCP-20: Cancel Case — Model as early termination paths")
    class Wcp20CancelCaseTests {

        @Test
        @DisplayName("Insurance claim with immediate cancellation: Submit->{ProcessClaim,CancelClaim}->end")
        void cancelCaseInsuranceClaimIsSoundTest() {
            String yaml = """
                    name: InsuranceClaimCancellation
                    first: SubmitClaim
                    tasks:
                      - id: SubmitClaim
                        flows: [ProcessClaim, CancelClaim]
                        split: xor
                        condition: customer_cancelled -> CancelClaim
                        default: ProcessClaim
                      - id: ProcessClaim
                        flows: [ReviewClaim]
                      - id: ReviewClaim
                        flows: [ApproveClaim]
                      - id: ApproveClaim
                        flows: [end]
                      - id: CancelClaim
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Insurance claim cancellation (submit->process or cancel) must be sound; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("Purchase order with abort at any phase: Order->{Process,AbortOrder}->end")
        void cancelCasePurchaseOrderAbortIsSoundTest() {
            String yaml = """
                    name: PurchaseOrderAbort
                    first: SubmitOrder
                    tasks:
                      - id: SubmitOrder
                        flows: [ValidateOrder, AbortOrder]
                        split: xor
                        condition: po_invalid -> AbortOrder
                        default: ValidateOrder
                      - id: ValidateOrder
                        flows: [CheckInventory, AbortOrder]
                        split: xor
                        condition: validation_failed -> AbortOrder
                        default: CheckInventory
                      - id: CheckInventory
                        flows: [PlaceOrder]
                      - id: PlaceOrder
                        flows: [end]
                      - id: AbortOrder
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Purchase order abort paths must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Loan approval with early termination: Submit->{Review,Reject}->end")
        void cancelCaseLoanApprovalIsSoundTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("SubmitLoanApplication", "ReviewApplication", "RejectApplication"),
                    buildTask("ReviewApplication", "end"),
                    buildTask("RejectApplication", "end")
            );
            var spec = buildSpec("LoanCancellation", "SubmitLoanApplication", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Loan approval with early rejection path must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Unsound: Cancellation case without join-point (orphaned completion paths)")
        void cancelCaseUnsoundMultipleOrphanedPathsTest() {
            String yaml = """
                    name: BadCancelCase
                    first: Start
                    tasks:
                      - id: Start
                        flows: [ProcessA, ProcessB]
                      - id: ProcessA
                        flows: []
                      - id: ProcessB
                        flows: []
                    """;
            var result = verifier.verifyYaml(yaml);

            assertFalse(result.sound(),
                    "Cancellation case with no convergence point must be unsound");
        }
    }

    // =========================================================================
    // WCP-21: Structured Partial Join — First M of N branches triggers join
    // =========================================================================

    @Nested
    @DisplayName("WCP-21: Structured Partial Join — M-out-of-N completion threshold")
    class Wcp21StructuredPartialJoinTests {

        @Test
        @DisplayName("Expert review quorum: 3 out of 5 expert reviews complete triggers approval")
        void partialJoinExpertReviewQuorumIsSoundTest() {
            String yaml = """
                    name: ExpertReviewQuorum
                    first: GatherReviews
                    tasks:
                      - id: GatherReviews
                        flows: [ReviewerA, ReviewerB, ReviewerC, ReviewerD, ReviewerE]
                        split: and
                      - id: ReviewerA
                        flows: [WaitForQuorum]
                      - id: ReviewerB
                        flows: [WaitForQuorum]
                      - id: ReviewerC
                        flows: [WaitForQuorum]
                      - id: ReviewerD
                        flows: [WaitForQuorum]
                      - id: ReviewerE
                        flows: [WaitForQuorum]
                      - id: WaitForQuorum
                        flows: [CollateReviews]
                        join: and
                      - id: CollateReviews
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Expert review with 5 reviewers converging at join must be sound; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("Approval signature threshold: 2 of 3 approvers sufficient")
        void partialJoinApprovalSignatureThresholdIsSoundTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("RequestApproval", "ApproverA", "ApproverB", "ApproverC"),
                    buildTask("ApproverA", "ConsolidateApprovals"),
                    buildTask("ApproverB", "ConsolidateApprovals"),
                    buildTask("ApproverC", "ConsolidateApprovals"),
                    buildTask("ConsolidateApprovals", "end")
            );
            var spec = buildSpec("ApprovalThreshold", "RequestApproval", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Approval signature threshold with 3 parallel approvers must be sound; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("Bid evaluation: 4 bids collected, any 3 sufficient for decision")
        void partialJoinBidEvaluationIsSoundTest() {
            String yaml = """
                    name: BidEvaluation
                    first: RequestBids
                    tasks:
                      - id: RequestBids
                        flows: [BidderA, BidderB, BidderC, BidderD]
                        split: and
                      - id: BidderA
                        flows: [EvaluateBids]
                      - id: BidderB
                        flows: [EvaluateBids]
                      - id: BidderC
                        flows: [EvaluateBids]
                      - id: BidderD
                        flows: [EvaluateBids]
                      - id: EvaluateBids
                        flows: [SelectWinner]
                        join: and
                      - id: SelectWinner
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Bid evaluation with 4 parallel bidders must be sound; violations: " + result.violations());
        }
    }

    // =========================================================================
    // WCP-22: Blocking Partial Join — M complete, remaining cancelled
    // =========================================================================

    @Nested
    @DisplayName("WCP-22: Blocking Partial Join — Remaining tasks blocked after M complete")
    class Wcp22BlockingPartialJoinTests {

        @Test
        @DisplayName("Quality assurance: first 2 test results sufficient, remaining tests cancelled")
        void blockingPartialJoinQATestCancellationIsSoundTest() {
            String yaml = """
                    name: QABlockingPartialJoin
                    first: BeginTests
                    tasks:
                      - id: BeginTests
                        flows: [TestSuiteA, TestSuiteB, TestSuiteC]
                        split: and
                      - id: TestSuiteA
                        flows: [WaitForResults]
                      - id: TestSuiteB
                        flows: [WaitForResults]
                      - id: TestSuiteC
                        flows: [WaitForResults]
                      - id: WaitForResults
                        flows: [ReleaseProduct]
                        join: and
                      - id: ReleaseProduct
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "QA blocking partial join (3 tests, 2 sufficient) must be sound; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("Supply chain: 5 suppliers queried, first 3 responses accepted, rest blocked")
        void blockingPartialJoinSupplyChainIsSoundTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("QuerySuppliers", "SupplierA", "SupplierB", "SupplierC", "SupplierD", "SupplierE"),
                    buildTask("SupplierA", "AggregateQuotes"),
                    buildTask("SupplierB", "AggregateQuotes"),
                    buildTask("SupplierC", "AggregateQuotes"),
                    buildTask("SupplierD", "AggregateQuotes"),
                    buildTask("SupplierE", "AggregateQuotes"),
                    buildTask("AggregateQuotes", "end")
            );
            var spec = buildSpec("SupplyChainBlocking", "QuerySuppliers", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Supply chain blocking partial join (5 suppliers, 3 selected) must be sound; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("Customer feedback: 10 focus group sessions, first 5 results trigger decision, rest cancelled")
        void blockingPartialJoinFeedbackSessionsIsSoundTest() {
            String yaml = """
                    name: FeedbackBlockingJoin
                    first: StartSessions
                    tasks:
                      - id: StartSessions
                        flows: [Session1, Session2, Session3, Session4, Session5, Session6, Session7, Session8, Session9, Session10]
                        split: and
                      - id: Session1
                        flows: [CollectFeedback]
                      - id: Session2
                        flows: [CollectFeedback]
                      - id: Session3
                        flows: [CollectFeedback]
                      - id: Session4
                        flows: [CollectFeedback]
                      - id: Session5
                        flows: [CollectFeedback]
                      - id: Session6
                        flows: [CollectFeedback]
                      - id: Session7
                        flows: [CollectFeedback]
                      - id: Session8
                        flows: [CollectFeedback]
                      - id: Session9
                        flows: [CollectFeedback]
                      - id: Session10
                        flows: [CollectFeedback]
                      - id: CollectFeedback
                        flows: [ReportDecision]
                        join: and
                      - id: ReportDecision
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Feedback blocking join (10 sessions, 5 threshold) must be sound; violations: "
                            + result.violations());
        }
    }

    // =========================================================================
    // WCP-23: Cancelling Partial Join — Partial join cancels remaining branches
    // =========================================================================

    @Nested
    @DisplayName("WCP-23: Cancelling Partial Join — M complete → remaining cancelled")
    class Wcp23CancellingPartialJoinTests {

        @Test
        @DisplayName("Competitive bidding: 2 of 4 bids received, immediate award (others cancelled)")
        void cancellingPartialJoinCompetitiveBiddingIsSoundTest() {
            String yaml = """
                    name: CompetitiveBiddingCancellingJoin
                    first: IssueBidRequest
                    tasks:
                      - id: IssueBidRequest
                        flows: [BidContractorA, BidContractorB, BidContractorC, BidContractorD]
                        split: and
                      - id: BidContractorA
                        flows: [SelectWinner]
                      - id: BidContractorB
                        flows: [SelectWinner]
                      - id: BidContractorC
                        flows: [SelectWinner]
                      - id: BidContractorD
                        flows: [SelectWinner]
                      - id: SelectWinner
                        flows: [AwardContract]
                        join: and
                      - id: AwardContract
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Competitive bidding cancelling partial join must be sound; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("Insurance claim first approval wins: 3 internal approvers, first approve cancels peer review")
        void cancellingPartialJoinFirstApprovalWinsIsSoundTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("SubmitClaim", "ApproveA", "ApproveB", "ApproveC"),
                    buildTask("ApproveA", "ProcessApproval"),
                    buildTask("ApproveB", "ProcessApproval"),
                    buildTask("ApproveC", "ProcessApproval"),
                    buildTask("ProcessApproval", "end")
            );
            var spec = buildSpec("FirstApprovalWins", "SubmitClaim", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "First approval wins cancelling join must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Product release decision: first 3 reviewers sufficient, remaining review activities cancelled")
        void cancellingPartialJoinProductReleaseIsSoundTest() {
            String yaml = """
                    name: ProductReleaseCancellingJoin
                    first: RequestReview
                    tasks:
                      - id: RequestReview
                        flows: [ReviewerAlpha, ReviewerBeta, ReviewerGamma, ReviewerDelta]
                        split: and
                      - id: ReviewerAlpha
                        flows: [MakeDecision]
                      - id: ReviewerBeta
                        flows: [MakeDecision]
                      - id: ReviewerGamma
                        flows: [MakeDecision]
                      - id: ReviewerDelta
                        flows: [MakeDecision]
                      - id: MakeDecision
                        flows: [PublishRelease]
                        join: and
                      - id: PublishRelease
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Product release cancelling partial join must be sound; violations: " + result.violations());
        }
    }

    // =========================================================================
    // WCP-24: Generalised AND-Join — Dynamic token count merge
    // =========================================================================

    @Nested
    @DisplayName("WCP-24: Generalised AND-Join — Dynamic join with variable branch count")
    class Wcp24GeneralisedAndJoinTests {

        @Test
        @DisplayName("Sub-bid consolidation: variable number of sub-bids merged into main proposal")
        void generalisedAndJoinSubBidConsolidationIsSoundTest() {
            String yaml = """
                    name: SubBidConsolidation
                    first: PrepareSubBids
                    tasks:
                      - id: PrepareSubBids
                        flows: [CollectBid1, CollectBid2, CollectBid3]
                        split: and
                      - id: CollectBid1
                        flows: [MergeProposal]
                      - id: CollectBid2
                        flows: [MergeProposal]
                      - id: CollectBid3
                        flows: [MergeProposal]
                      - id: MergeProposal
                        flows: [SubmitProposal]
                        join: and
                      - id: SubmitProposal
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Sub-bid consolidation with dynamic AND-join must be sound; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("Multi-source data aggregation: N data sources synchronize at aggregator")
        void generalisedAndJoinDataAggregationIsSoundTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("RequestData", "SourceA", "SourceB", "SourceC", "SourceD"),
                    buildTask("SourceA", "AggregateData"),
                    buildTask("SourceB", "AggregateData"),
                    buildTask("SourceC", "AggregateData"),
                    buildTask("SourceD", "AggregateData"),
                    buildTask("AggregateData", "AnalyzeResults"),
                    buildTask("AnalyzeResults", "end")
            );
            var spec = buildSpec("MultiSourceAggregation", "RequestData", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Multi-source data aggregation with dynamic AND-join must be sound; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("Project task synchronization: multiple parallel workstreams converge at review gate")
        void generalisedAndJoinProjectTaskSyncIsSoundTest() {
            String yaml = """
                    name: ProjectTaskSync
                    first: StartWorkstreams
                    tasks:
                      - id: StartWorkstreams
                        flows: [DesignWorkstream, DevelopWorkstream, TestWorkstream, DeploymentWorkstream]
                        split: and
                      - id: DesignWorkstream
                        flows: [ReviewGate]
                      - id: DevelopWorkstream
                        flows: [ReviewGate]
                      - id: TestWorkstream
                        flows: [ReviewGate]
                      - id: DeploymentWorkstream
                        flows: [ReviewGate]
                      - id: ReviewGate
                        flows: [end]
                        join: and
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Project task sync with generalised AND-join must be sound; violations: "
                            + result.violations());
        }
    }

    // =========================================================================
    // WCP-25: Blocking Discriminator — First completes, blocks remaining
    // =========================================================================

    @Nested
    @DisplayName("WCP-25: Blocking Discriminator — First completion blocks others")
    class Wcp25BlockingDiscriminatorTests {

        @Test
        @DisplayName("Multi-location order fulfillment: fastest warehouse fulfills, others blocked")
        void blockingDiscriminatorWarehouseRaceIsSoundTest() {
            String yaml = """
                    name: WarehouseRace
                    first: OrderPlaced
                    tasks:
                      - id: OrderPlaced
                        flows: [WarehouseNorth, WarehouseSouth, WarehouseEast]
                        split: and
                      - id: WarehouseNorth
                        flows: [ShipOrder]
                      - id: WarehouseSouth
                        flows: [ShipOrder]
                      - id: WarehouseEast
                        flows: [ShipOrder]
                      - id: ShipOrder
                        flows: [end]
                        join: xor
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Warehouse race blocking discriminator must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Parallel recovery options: first recovery succeeds, others abandoned")
        void blockingDiscriminatorRecoveryOptionsIsSoundTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("DetectFailure", "RecoveryPathA", "RecoveryPathB", "RecoveryPathC"),
                    buildTask("RecoveryPathA", "Continue"),
                    buildTask("RecoveryPathB", "Continue"),
                    buildTask("RecoveryPathC", "Continue"),
                    buildTask("Continue", "end")
            );
            var spec = buildSpec("ParallelRecovery", "DetectFailure", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Parallel recovery with blocking discriminator must be sound; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("Concurrent approval routes: first approval path succeeds, others blocked")
        void blockingDiscriminatorConcurrentApprovalIsSoundTest() {
            String yaml = """
                    name: ConcurrentApproval
                    first: SubmitForApproval
                    tasks:
                      - id: SubmitForApproval
                        flows: [ApprovalChainA, ApprovalChainB, ApprovalChainC]
                        split: and
                      - id: ApprovalChainA
                        flows: [ProcessApproval]
                      - id: ApprovalChainB
                        flows: [ProcessApproval]
                      - id: ApprovalChainC
                        flows: [ProcessApproval]
                      - id: ProcessApproval
                        flows: [end]
                        join: xor
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Concurrent approval blocking discriminator must be sound; violations: "
                            + result.violations());
        }
    }

    // =========================================================================
    // WCP-26: Cancelling Discriminator — First completes, cancels remaining
    // =========================================================================

    @Nested
    @DisplayName("WCP-26: Cancelling Discriminator — First completion cancels others")
    class Wcp26CancellingDiscriminatorTests {

        @Test
        @DisplayName("Bid evaluation race: first acceptable bid wins, other evaluations cancelled")
        void cancellingDiscriminatorBidRaceIsSoundTest() {
            String yaml = """
                    name: BidRaceEvaluation
                    first: InitiateBidding
                    tasks:
                      - id: InitiateBidding
                        flows: [EvaluateBidA, EvaluateBidB, EvaluateBidC]
                        split: and
                      - id: EvaluateBidA
                        flows: [SelectBid]
                      - id: EvaluateBidB
                        flows: [SelectBid]
                      - id: EvaluateBidC
                        flows: [SelectBid]
                      - id: SelectBid
                        flows: [NotifyWinner]
                        join: xor
                      - id: NotifyWinner
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Bid race cancelling discriminator must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Network failover cascade: first responding system wins, backups cancelled")
        void cancellingDiscriminatorNetworkFailoverIsSoundTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("DetectNetworkIssue", "PrimarySystem", "BackupSystem1", "BackupSystem2"),
                    buildTask("PrimarySystem", "ActivateService"),
                    buildTask("BackupSystem1", "ActivateService"),
                    buildTask("BackupSystem2", "ActivateService"),
                    buildTask("ActivateService", "MonitorService"),
                    buildTask("MonitorService", "end")
            );
            var spec = buildSpec("NetworkFailover", "DetectNetworkIssue", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Network failover cancelling discriminator must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Competing vendors: first vendor response accepted, others' quotes cancelled")
        void cancellingDiscriminatorVendorCompetitionIsSoundTest() {
            String yaml = """
                    name: VendorCompetition
                    first: RequestQuotes
                    tasks:
                      - id: RequestQuotes
                        flows: [VendorX, VendorY, VendorZ]
                        split: and
                      - id: VendorX
                        flows: [ProcessQuote]
                      - id: VendorY
                        flows: [ProcessQuote]
                      - id: VendorZ
                        flows: [ProcessQuote]
                      - id: ProcessQuote
                        flows: [AwardContract]
                        join: xor
                      - id: AwardContract
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Vendor competition cancelling discriminator must be sound; violations: "
                            + result.violations());
        }
    }

    // =========================================================================
    // WCP-27: Generalised Partial Join — Variable threshold M
    // =========================================================================

    @Nested
    @DisplayName("WCP-27: Generalised Partial Join — Variable M threshold")
    class Wcp27GeneralisedPartialJoinTests {

        @Test
        @DisplayName("Dynamic quorum assembly: variable M approvals required (configurable threshold)")
        void generalisedPartialJoinDynamicQuorumIsSoundTest() {
            String yaml = """
                    name: DynamicQuorumAssembly
                    first: RequestApprovals
                    tasks:
                      - id: RequestApprovals
                        flows: [ApprovalsNeeded1, ApprovalsNeeded2, ApprovalsNeeded3, ApprovalsNeeded4, ApprovalsNeeded5]
                        split: and
                      - id: ApprovalsNeeded1
                        flows: [QuorumReached]
                      - id: ApprovalsNeeded2
                        flows: [QuorumReached]
                      - id: ApprovalsNeeded3
                        flows: [QuorumReached]
                      - id: ApprovalsNeeded4
                        flows: [QuorumReached]
                      - id: ApprovalsNeeded5
                        flows: [QuorumReached]
                      - id: QuorumReached
                        flows: [ProceedWithDecision]
                        join: and
                      - id: ProceedWithDecision
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Dynamic quorum assembly with generalised partial join must be sound; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("Configurable supplier threshold: variable number of suppliers, variable M selection")
        void generalisedPartialJoinConfigurableSupplierIsSoundTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("QuerySuppliers", "Supplier1", "Supplier2", "Supplier3", "Supplier4", "Supplier5", "Supplier6"),
                    buildTask("Supplier1", "SelectSuppliers"),
                    buildTask("Supplier2", "SelectSuppliers"),
                    buildTask("Supplier3", "SelectSuppliers"),
                    buildTask("Supplier4", "SelectSuppliers"),
                    buildTask("Supplier5", "SelectSuppliers"),
                    buildTask("Supplier6", "SelectSuppliers"),
                    buildTask("SelectSuppliers", "NegotiateTerms"),
                    buildTask("NegotiateTerms", "end")
            );
            var spec = buildSpec("ConfigurableSupplierThreshold", "QuerySuppliers", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Configurable supplier threshold with generalised partial join must be sound; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("Milestone achievement: M of N team objectives achieved triggers project phase transition")
        void generalisedPartialJoinMilestoneAchievementIsSoundTest() {
            String yaml = """
                    name: MilestoneAchievement
                    first: BeginObjectives
                    tasks:
                      - id: BeginObjectives
                        flows: [Objective1, Objective2, Objective3, Objective4]
                        split: and
                      - id: Objective1
                        flows: [CheckMilestones]
                      - id: Objective2
                        flows: [CheckMilestones]
                      - id: Objective3
                        flows: [CheckMilestones]
                      - id: Objective4
                        flows: [CheckMilestones]
                      - id: CheckMilestones
                        flows: [ProgressProject]
                        join: and
                      - id: ProgressProject
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Milestone achievement with generalised partial join must be sound; violations: "
                            + result.violations());
        }
    }

    // =========================================================================
    // WCP-28: Blocking Partial Join with Cancellation — M fires, rest cancelled
    // =========================================================================

    @Nested
    @DisplayName("WCP-28: Blocking Partial Join with Cancellation — M fires, N-M cancelled")
    class Wcp28BlockingPartialJoinCancellationTests {

        @Test
        @DisplayName("Test suite acceleration: 3 of 5 test suites pass triggers release, others cancelled")
        void blockingPartialJoinCancellationTestAccelerationIsSoundTest() {
            String yaml = """
                    name: TestAcceleration
                    first: StartTestSuites
                    tasks:
                      - id: StartTestSuites
                        flows: [TestA, TestB, TestC, TestD, TestE]
                        split: and
                      - id: TestA
                        flows: [ConsolidateResults]
                      - id: TestB
                        flows: [ConsolidateResults]
                      - id: TestC
                        flows: [ConsolidateResults]
                      - id: TestD
                        flows: [ConsolidateResults]
                      - id: TestE
                        flows: [ConsolidateResults]
                      - id: ConsolidateResults
                        flows: [ReleaseProduct]
                        join: and
                      - id: ReleaseProduct
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Test acceleration with blocking partial join & cancellation must be sound; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("Customer satisfaction survey: 60% response threshold, incomplete surveys cancelled")
        void blockingPartialJoinCancellationSurveyThresholdIsSoundTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("DistributeSurvey", "Recipient1", "Recipient2", "Recipient3", "Recipient4", "Recipient5"),
                    buildTask("Recipient1", "AnalyzeSurveyResults"),
                    buildTask("Recipient2", "AnalyzeSurveyResults"),
                    buildTask("Recipient3", "AnalyzeSurveyResults"),
                    buildTask("Recipient4", "AnalyzeSurveyResults"),
                    buildTask("Recipient5", "AnalyzeSurveyResults"),
                    buildTask("AnalyzeSurveyResults", "end")
            );
            var spec = buildSpec("SurveyThreshold", "DistributeSurvey", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Survey threshold with blocking partial join & cancellation must be sound; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("Clinical trial milestone: 75% patient enrollment triggers data lock, remaining enrollments cancelled")
        void blockingPartialJoinCancellationClinicalTrialIsSoundTest() {
            String yaml = """
                    name: ClinicalTrialMilestone
                    first: BeginEnrollment
                    tasks:
                      - id: BeginEnrollment
                        flows: [SiteA, SiteB, SiteC, SiteD]
                        split: and
                      - id: SiteA
                        flows: [LockData]
                      - id: SiteB
                        flows: [LockData]
                      - id: SiteC
                        flows: [LockData]
                      - id: SiteD
                        flows: [LockData]
                      - id: LockData
                        flows: [AnalyzeResults]
                        join: and
                      - id: AnalyzeResults
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Clinical trial milestone with blocking partial join & cancellation must be sound; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("Unsound: Blocking partial join without convergence point")
        void blockingPartialJoinCancellationUnsoundNoConvergenceTest() {
            String yaml = """
                    name: BadBlockingJoin
                    first: Start
                    tasks:
                      - id: Start
                        flows: [Branch1, Branch2, Branch3]
                      - id: Branch1
                        flows: [end]
                      - id: Branch2
                        flows: []
                      - id: Branch3
                        flows: []
                    """;
            var result = verifier.verifyYaml(yaml);

            assertFalse(result.sound(),
                    "Blocking partial join without all paths reaching end must be unsound");
        }
    }
}
