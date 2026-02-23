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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.mcp.a2a.example.WorkflowSoundnessVerifier;
import org.yawlfoundation.yawl.mcp.a2a.example.WorkflowSoundnessVerifier.SoundnessResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 tests for Workflow Control Patterns 10-18 (WCP-10 through WCP-18).
 *
 * <p>These tests verify workflow patterns from a business perspective using real workflow
 * specifications expressed in YAML. Each pattern is tested for soundness (reachability,
 * option to complete) and typical business scenarios.</p>
 *
 * <p>Pattern Coverage:
 * <ul>
 *   <li>WCP-10: Arbitrary Cycles (loops, rework scenarios)</li>
 *   <li>WCP-11: Implicit Termination (all paths complete)</li>
 *   <li>WCP-12: Multiple Instances Without Sync (independent parallel work)</li>
 *   <li>WCP-13: Multiple Instances With Design-Time Known Sync (fixed N instances)</li>
 *   <li>WCP-14: Multiple Instances With Run-Time Known Sync (dynamic N)</li>
 *   <li>WCP-15: Multiple Instances Without Run-Time Known Sync (threshold/quorum)</li>
 *   <li>WCP-16: Deferred Choice (external event determines branch)</li>
 *   <li>WCP-17: Interleaved Parallel Routing (any order, any timing)</li>
 *   <li>WCP-18: Milestone (task enabled only if another is in specific state)</li>
 * </ul>
 *
 * <p>Tests use {@link WorkflowSoundnessVerifier} to validate real workflow specifications
 * with Chicago TDD principles: no mocks, real objects, comprehensive coverage.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("WCP-10 to WCP-18 Business Patterns Test Suite")
class WcpBusinessPatterns10to18Test {

    private final WorkflowSoundnessVerifier verifier = new WorkflowSoundnessVerifier();

    /**
     * WCP-10: Arbitrary Cycles.
     *
     * <p>Business context: Quality check workflow where failed items loop back for rework.</p>
     */
    @Nested
    @DisplayName("WCP-10: Arbitrary Cycles")
    class Wcp10ArbitraryCycles {

        @Test
        @DisplayName("Should be sound: quality check with rework loop")
        void testQualityCheckReworkCycle() {
            String yaml = """
                name: QualityCheckWorkflow
                first: ReceiveItem
                tasks:
                  - id: ReceiveItem
                    flows: [PerformQualityCheck]
                  - id: PerformQualityCheck
                    flows: [ApproveItem, ReworkItem]
                    condition: quality_ok -> ApproveItem
                    default: ReworkItem
                    split: xor
                  - id: ReworkItem
                    flows: [PerformQualityCheck]
                  - id: ApproveItem
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Quality check with rework loop should be sound. Violations: " + result.violations());
        }

        @Test
        @DisplayName("Should be sound: document review with multiple revision cycles")
        void testDocumentReviewCycle() {
            String yaml = """
                name: DocumentReview
                first: SubmitDocument
                tasks:
                  - id: SubmitDocument
                    flows: [ReviewDocument]
                  - id: ReviewDocument
                    flows: [MakeRevisions, ApproveDocument]
                    condition: approved -> ApproveDocument
                    default: MakeRevisions
                    split: xor
                  - id: MakeRevisions
                    flows: [ReviewDocument]
                  - id: ApproveDocument
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Document review cycles should be sound. Violations: " + result.violations());
        }

        @Test
        @DisplayName("Should be unsound: orphan task left by rework cycle")
        void testCycleWithOrphanTask() {
            String yaml = """
                name: BadQualityCheck
                first: CheckQuality
                tasks:
                  - id: CheckQuality
                    flows: [FixIssue, ShipItem]
                    condition: ok -> ShipItem
                    default: FixIssue
                    split: xor
                  - id: FixIssue
                    flows: [CheckQuality]
                  - id: ShipItem
                    flows: [end]
                  - id: NotifyCustomer
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(), "Workflow with unreachable task should be unsound");
            assertTrue(result.violations().stream().anyMatch(v -> v.contains("NotifyCustomer")),
                    "Violations should mention unreachable NotifyCustomer");
        }

        @Test
        @DisplayName("Should be sound: nested quality check cycles")
        void testNestedQualityCycles() {
            String yaml = """
                name: NestedQualityCheck
                first: InitialCheck
                tasks:
                  - id: InitialCheck
                    flows: [FirstReview, Discard]
                    condition: candidate -> FirstReview
                    default: Discard
                    split: xor
                  - id: FirstReview
                    flows: [SecondReview, Reject]
                    condition: promising -> SecondReview
                    default: Reject
                    split: xor
                  - id: SecondReview
                    flows: [FirstReview, Approve]
                    condition: pass -> Approve
                    default: FirstReview
                    split: xor
                  - id: Reject
                    flows: [end]
                  - id: Approve
                    flows: [end]
                  - id: Discard
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Nested quality check cycles should be sound. Violations: " + result.violations());
        }
    }

    /**
     * WCP-11: Implicit Termination.
     *
     * <p>Business context: Workflow ends when all parallel branches complete naturally
     * (no explicit synchronization barrier).</p>
     */
    @Nested
    @DisplayName("WCP-11: Implicit Termination")
    class Wcp11ImplicitTermination {

        @Test
        @DisplayName("Should be sound: all parallel branches eventually reach end")
        void testImplicitTerminationWithAndSplit() {
            String yaml = """
                name: OrderProcessing
                first: ProcessOrder
                tasks:
                  - id: ProcessOrder
                    flows: [ProcessPayment, CheckInventory, PrepareShipping]
                    split: and
                  - id: ProcessPayment
                    flows: [end]
                  - id: CheckInventory
                    flows: [end]
                  - id: PrepareShipping
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "AND split with all branches reaching end should be sound. Violations: " + result.violations());
        }

        @Test
        @DisplayName("Should be sound: parallel branches with xor-join implicit completion")
        void testImplicitTerminationWithXorJoin() {
            String yaml = """
                name: ApprovalProcess
                first: RequestApproval
                tasks:
                  - id: RequestApproval
                    flows: [EmailApprover, CallApprover]
                    split: and
                  - id: EmailApprover
                    flows: [end]
                  - id: CallApprover
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Parallel branches completing independently should be sound. Violations: " + result.violations());
        }

        @Test
        @DisplayName("Should be unsound: parallel branch deadlock (missing flows)")
        void testImplicitTerminationWithDeadlock() {
            String yaml = """
                name: DeadlockProcess
                first: SplitTask
                tasks:
                  - id: SplitTask
                    flows: [TaskA, TaskB]
                    split: and
                  - id: TaskA
                    flows: [end]
                  - id: TaskB
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(), "Workflow with parallel deadlock should be unsound");
            assertTrue(result.violations().stream().anyMatch(v -> v.contains("dead-end") || v.contains("o-top")),
                    "Violations should indicate deadlock");
        }

        @Test
        @DisplayName("Should be sound: complex parallel termination")
        void testComplexParallelTermination() {
            String yaml = """
                name: ComplexOrderFulfillment
                first: StartOrder
                tasks:
                  - id: StartOrder
                    flows: [PaymentPath, InventoryPath, ShippingPath]
                    split: and
                  - id: PaymentPath
                    flows: [ChargeCard, NotifyPaymentGateway]
                    split: and
                  - id: ChargeCard
                    flows: [end]
                  - id: NotifyPaymentGateway
                    flows: [end]
                  - id: InventoryPath
                    flows: [ReserveItems, UpdateWarehouse]
                    split: and
                  - id: ReserveItems
                    flows: [end]
                  - id: UpdateWarehouse
                    flows: [end]
                  - id: ShippingPath
                    flows: [PrepareLabel, NotifyCarrier]
                    split: and
                  - id: PrepareLabel
                    flows: [end]
                  - id: NotifyCarrier
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Complex parallel termination should be sound. Violations: " + result.violations());
        }
    }

    /**
     * WCP-12: Multiple Instances Without Sync.
     *
     * <p>Business context: Send notifications to N customers independently;
     * each completes without waiting for others.</p>
     */
    @Nested
    @DisplayName("WCP-12: Multiple Instances Without Sync")
    class Wcp12MultipleInstancesWithoutSync {

        @Test
        @DisplayName("Should be sound: send notifications to N customers independently")
        void testIndependentNotificationInstances() {
            String yaml = """
                name: NotifyCustomers
                first: RetrieveCustomerList
                tasks:
                  - id: RetrieveCustomerList
                    flows: [SendNotification]
                  - id: SendNotification
                    flows: [end]
                    multiInstance:
                      mode: dynamic
                      min: 1
                      max: 1000
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Independent multi-instance notification should be sound. Violations: " + result.violations());
        }

        @Test
        @DisplayName("Should be sound: email multiple invoices independently")
        void testIndependentEmailInstances() {
            String yaml = """
                name: EmailInvoices
                first: PrepareInvoiceList
                tasks:
                  - id: PrepareInvoiceList
                    flows: [EmailInvoice]
                  - id: EmailInvoice
                    flows: [end]
                    multiInstance:
                      mode: static
                      min: 1
                      max: 500
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Independent email instances should be sound. Violations: " + result.violations());
        }

        @Test
        @DisplayName("Should be unsound: missing transition from multi-instance task")
        void testMissingFlowFromMultiInstance() {
            String yaml = """
                name: BadNotify
                first: GetCustomers
                tasks:
                  - id: GetCustomers
                    flows: [NotifyCustomer]
                  - id: NotifyCustomer
                    multiInstance:
                      mode: dynamic
                      min: 1
                      max: 100
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(), "Multi-instance task without flows should be unsound");
            assertTrue(result.violations().stream().anyMatch(v -> v.contains("dead-end") || v.contains("o-top")),
                    "Should indicate task cannot reach output");
        }

        @Test
        @DisplayName("Should be sound: process orders asynchronously without synchronization")
        void testAsynchronousOrderProcessing() {
            String yaml = """
                name: AsynchronousOrderProcessing
                first: SubmitOrders
                tasks:
                  - id: SubmitOrders
                    flows: [ProcessOrder]
                  - id: ProcessOrder
                    flows: [end]
                    multiInstance:
                      mode: dynamic
                      min: 1
                      max: 10000
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Asynchronous order processing should be sound. Violations: " + result.violations());
        }
    }

    /**
     * WCP-13: Multiple Instances With Design-Time Known Sync.
     *
     * <p>Business context: Exactly 3 loan officers must review and approve a loan;
     * all 3 must complete before proceeding.</p>
     */
    @Nested
    @DisplayName("WCP-13: Multiple Instances With Design-Time Known Sync")
    class Wcp13MultipleInstancesDesignTimeSyncTest {

        @Test
        @DisplayName("Should be sound: exactly 3 loan reviewers must approve")
        void testFixedThreeLoanReviewers() {
            String yaml = """
                name: LoanApprovalProcess
                first: ReceiveLoanApplication
                tasks:
                  - id: ReceiveLoanApplication
                    flows: [ReviewLoan]
                  - id: ReviewLoan
                    flows: [MakeLoanDecision]
                    multiInstance:
                      min: 3
                      max: 3
                      mode: static
                  - id: MakeLoanDecision
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Fixed 3-instance review should be sound. Violations: " + result.violations());
        }

        @Test
        @DisplayName("Should be sound: all 5 committee members must vote")
        void testFixedFiveVotes() {
            String yaml = """
                name: CommitteeVoting
                first: StartVote
                tasks:
                  - id: StartVote
                    flows: [CastVote]
                  - id: CastVote
                    flows: [TallyResults]
                    multiInstance:
                      min: 5
                      max: 5
                      mode: static
                  - id: TallyResults
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Fixed 5-vote instance should be sound. Violations: " + result.violations());
        }

        @Test
        @DisplayName("Should be unsound: instances missing continuation task")
        void testMissingContinuationAfterInstances() {
            String yaml = """
                name: BadApproval
                first: StartProcess
                tasks:
                  - id: StartProcess
                    flows: [ReviewDocument]
                  - id: ReviewDocument
                    multiInstance:
                      min: 2
                      max: 2
                      mode: static
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(), "Multi-instance without continuation should be unsound");
            assertTrue(result.violations().stream().anyMatch(v -> v.contains("dead-end") || v.contains("ReviewDocument")),
                    "Should indicate ReviewDocument is a dead-end");
        }

        @Test
        @DisplayName("Should be sound: all 10 quality inspectors must sign off")
        void testFixedQualityInspection() {
            String yaml = """
                name: QualitySignOff
                first: PrepareProduct
                tasks:
                  - id: PrepareProduct
                    flows: [InspectProduct]
                  - id: InspectProduct
                    flows: [ShipProduct]
                    multiInstance:
                      min: 10
                      max: 10
                      mode: static
                  - id: ShipProduct
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Fixed 10-inspector workflow should be sound. Violations: " + result.violations());
        }
    }

    /**
     * WCP-14: Multiple Instances With Run-Time Known Sync.
     *
     * <p>Business context: Audit all accounts retrieved from database;
     * N determined at runtime from query result.</p>
     */
    @Nested
    @DisplayName("WCP-14: Multiple Instances With Run-Time Known Sync")
    class Wcp14MultipleInstancesRunTimeSyncTest {

        @Test
        @DisplayName("Should be sound: audit N accounts from runtime query")
        void testRuntimeDeterminedAuditInstances() {
            String yaml = """
                name: AuditAllAccounts
                first: QueryDatabase
                tasks:
                  - id: QueryDatabase
                    flows: [AuditAccount]
                  - id: AuditAccount
                    flows: [end]
                    multiInstance:
                      mode: dynamic
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Runtime-determined audit instances should be sound. Violations: " + result.violations());
        }

        @Test
        @DisplayName("Should be sound: process N items from external feed")
        void testExternalFeedItemProcessing() {
            String yaml = """
                name: ProcessExternalItems
                first: ReadExternalFeed
                tasks:
                  - id: ReadExternalFeed
                    flows: [ProcessItem]
                  - id: ProcessItem
                    flows: [PublishResults]
                    multiInstance:
                      mode: dynamic
                  - id: PublishResults
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "External feed processing should be sound. Violations: " + result.violations());
        }

        @Test
        @DisplayName("Should be unsound: missing task after runtime instances")
        void testMissingPostInstanceTask() {
            String yaml = """
                name: BadRuntimeProcess
                first: ReadData
                tasks:
                  - id: ReadData
                    flows: [ProcessItem]
                  - id: ProcessItem
                    multiInstance:
                      mode: dynamic
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(), "Missing post-instance task should be unsound");
            assertTrue(result.violations().stream().anyMatch(v -> v.contains("ProcessItem") || v.contains("dead-end")),
                    "Should identify ProcessItem as unreachable or dead-end");
        }

        @Test
        @DisplayName("Should be sound: N suppliers deliver, then coordinate")
        void testRuntimeSupplierDeliveries() {
            String yaml = """
                name: SupplierCoordination
                first: RequestSupplies
                tasks:
                  - id: RequestSupplies
                    flows: [ReceiveShipment]
                  - id: ReceiveShipment
                    flows: [CoordinateInventory]
                    multiInstance:
                      mode: dynamic
                  - id: CoordinateInventory
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Runtime supplier coordination should be sound. Violations: " + result.violations());
        }
    }

    /**
     * WCP-15: Multiple Instances Without Run-Time Known Sync.
     *
     * <p>Business context: Gather responses until quorum (threshold) is reached;
     * first M complete triggers continuation, others ignored.</p>
     */
    @Nested
    @DisplayName("WCP-15: Multiple Instances Without Run-Time Known Sync (Threshold)")
    class Wcp15MultipleInstancesThresholdTest {

        @Test
        @DisplayName("Should be sound: collect feedback until 5 responses received")
        void testFeedbackQuorumThreshold() {
            String yaml = """
                name: CollectFeedback
                first: SendSurvey
                tasks:
                  - id: SendSurvey
                    flows: [ReceiveFeedback]
                  - id: ReceiveFeedback
                    flows: [AnalyzeFeedback]
                    multiInstance:
                      mode: dynamic
                      threshold: 5
                  - id: AnalyzeFeedback
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Threshold-based feedback collection should be sound. Violations: " + result.violations());
        }

        @Test
        @DisplayName("Should be sound: auction continues until 3 bids received")
        void testAuctionBidThreshold() {
            String yaml = """
                name: OnlineAuction
                first: OpenAuction
                tasks:
                  - id: OpenAuction
                    flows: [AcceptBid]
                  - id: AcceptBid
                    flows: [ClosAuction]
                    multiInstance:
                      mode: dynamic
                      threshold: 3
                  - id: ClosAuction
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Auction bid threshold should be sound. Violations: " + result.violations());
        }

        @Test
        @DisplayName("Should be unsound: threshold task missing continuation")
        void testThresholdMissingContinuation() {
            String yaml = """
                name: BadThreshold
                first: StartProcess
                tasks:
                  - id: StartProcess
                    flows: [CollectData]
                  - id: CollectData
                    multiInstance:
                      mode: dynamic
                      threshold: 10
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(), "Threshold task without continuation should be unsound");
            assertTrue(result.violations().stream().anyMatch(v -> v.contains("dead-end") || v.contains("CollectData")),
                    "Should indicate CollectData is unreachable or dead-end");
        }

        @Test
        @DisplayName("Should be sound: multiple response polling with majority threshold")
        void testMajorityThresholdVoting() {
            String yaml = """
                name: MajorityVoting
                first: StartVoting
                tasks:
                  - id: StartVoting
                    flows: [CastVote]
                  - id: CastVote
                    flows: [CountVotes]
                    multiInstance:
                      mode: dynamic
                      threshold: 51
                  - id: CountVotes
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Majority voting threshold should be sound. Violations: " + result.violations());
        }
    }

    /**
     * WCP-16: Deferred Choice.
     *
     * <p>Business context: Workflow branches based on external event (customer response),
     * not internal logic; only one branch executes.</p>
     */
    @Nested
    @DisplayName("WCP-16: Deferred Choice")
    class Wcp16DeferredChoiceTest {

        @Test
        @DisplayName("Should be sound: external event determines service path")
        void testExternalEventServiceChoice() {
            String yaml = """
                name: CustomerServicePath
                first: WaitForCustomerInput
                tasks:
                  - id: WaitForCustomerInput
                    flows: [ProcessStandardService, ProcessPremiumService, ProcessRefund]
                    split: xor
                  - id: ProcessStandardService
                    flows: [end]
                  - id: ProcessPremiumService
                    flows: [end]
                  - id: ProcessRefund
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "External event choice should be sound. Violations: " + result.violations());
        }

        @Test
        @DisplayName("Should be sound: customer response determines next action")
        void testCustomerResponseDeferredChoice() {
            String yaml = """
                name: ResponseDrivenWorkflow
                first: ReceiveCustomerResponse
                tasks:
                  - id: ReceiveCustomerResponse
                    flows: [AcceptOffer, RejectOffer, NegotiatePrice]
                    split: xor
                  - id: AcceptOffer
                    flows: [end]
                  - id: RejectOffer
                    flows: [end]
                  - id: NegotiatePrice
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Customer response choice should be sound. Violations: " + result.violations());
        }

        @Test
        @DisplayName("Should be unsound: orphan branch in deferred choice")
        void testDeferredChoiceWithOrphanBranch() {
            String yaml = """
                name: BadDeferredChoice
                first: WaitForEvent
                tasks:
                  - id: WaitForEvent
                    flows: [HandlerA, HandlerB]
                    split: xor
                  - id: HandlerA
                    flows: [end]
                  - id: HandlerB
                    flows: [end]
                  - id: UnreachableTask
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(), "Deferred choice with unreachable task should be unsound");
            assertTrue(result.violations().stream().anyMatch(v -> v.contains("UnreachableTask")),
                    "Should identify unreachable task");
        }

        @Test
        @DisplayName("Should be sound: payment method external choice")
        void testPaymentMethodDeferredChoice() {
            String yaml = """
                name: PaymentMethodSelection
                first: PromptPaymentMethod
                tasks:
                  - id: PromptPaymentMethod
                    flows: [ProcessCreditCard, ProcessBankTransfer, ProcessCash]
                    split: xor
                  - id: ProcessCreditCard
                    flows: [end]
                  - id: ProcessBankTransfer
                    flows: [end]
                  - id: ProcessCash
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Payment method choice should be sound. Violations: " + result.violations());
        }
    }

    /**
     * WCP-17: Interleaved Parallel Routing.
     *
     * <p>Business context: Tasks execute in some order but not necessarily simultaneously
     * (e.g., office must be cleaned AND stocked, order doesn't matter).</p>
     */
    @Nested
    @DisplayName("WCP-17: Interleaved Parallel Routing")
    class Wcp17InterleavedParallelRoutingTest {

        @Test
        @DisplayName("Should be sound: office cleaning and stocking in any order")
        void testOfficePreparationInterleavedOrder() {
            String yaml = """
                name: OfficePreparation
                first: StartPrep
                tasks:
                  - id: StartPrep
                    flows: [CleanOffice, StockSupplies]
                    split: and
                  - id: CleanOffice
                    flows: [end]
                  - id: StockSupplies
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Parallel interleaved order should be sound. Violations: " + result.violations());
        }

        @Test
        @DisplayName("Should be sound: project initialization tasks (any order)")
        void testProjectInitializationInterleavedOrder() {
            String yaml = """
                name: ProjectInitialization
                first: StartProject
                tasks:
                  - id: StartProject
                    flows: [SetupVersion, CreateTeam, DefineRoles]
                    split: and
                  - id: SetupVersion
                    flows: [end]
                  - id: CreateTeam
                    flows: [end]
                  - id: DefineRoles
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Project initialization should be sound. Violations: " + result.violations());
        }

        @Test
        @DisplayName("Should be unsound: parallel task missing completion path")
        void testInterleavedWithDeadlock() {
            String yaml = """
                name: BadInterleaved
                first: Start
                tasks:
                  - id: Start
                    flows: [TaskA, TaskB]
                    split: and
                  - id: TaskA
                    flows: [end]
                  - id: TaskB
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(), "Parallel interleaved with deadlock should be unsound");
            assertTrue(result.violations().stream().anyMatch(v -> v.contains("TaskB") || v.contains("dead-end")),
                    "Should identify TaskB as problematic");
        }

        @Test
        @DisplayName("Should be sound: complex interleaved operations")
        void testComplexInterleavedOrder() {
            String yaml = """
                name: ComplexInterleaved
                first: InitializeSystem
                tasks:
                  - id: InitializeSystem
                    flows: [LoadConfig, StartServices, InitializeDB, CacheData]
                    split: and
                  - id: LoadConfig
                    flows: [end]
                  - id: StartServices
                    flows: [end]
                  - id: InitializeDB
                    flows: [end]
                  - id: CacheData
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Complex interleaved tasks should be sound. Violations: " + result.violations());
        }
    }

    /**
     * WCP-18: Milestone.
     *
     * <p>Business context: Cancel order is enabled only if payment has NOT yet been processed;
     * once payment starts, cancel becomes disabled.</p>
     */
    @Nested
    @DisplayName("WCP-18: Milestone")
    class Wcp18MilestoneTest {

        @Test
        @DisplayName("Should be sound: cancel only possible before payment")
        void testMilestonePreventsCancelAfterPayment() {
            String yaml = """
                name: OrderWithMilestone
                first: ReceiveOrder
                tasks:
                  - id: ReceiveOrder
                    flows: [ProcessPayment, CancelOrder]
                    split: xor
                  - id: ProcessPayment
                    flows: [ConfirmOrder]
                    split: xor
                  - id: ConfirmOrder
                    flows: [end]
                  - id: CancelOrder
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Milestone order workflow should be sound. Violations: " + result.violations());
        }

        @Test
        @DisplayName("Should be sound: modify order only before checkout milestone")
        void testModifyOrderMilestone() {
            String yaml = """
                name: ModifyOrderBeforeCheckout
                first: BrowseProducts
                tasks:
                  - id: BrowseProducts
                    flows: [AddToCart, Checkout]
                    split: xor
                  - id: AddToCart
                    flows: [BrowseProducts, Checkout]
                    split: xor
                  - id: Checkout
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Modify before milestone should be sound. Violations: " + result.violations());
        }

        @Test
        @DisplayName("Should be unsound: milestone path missing continuation")
        void testMilestoneWithDeadlock() {
            String yaml = """
                name: BadMilestone
                first: Start
                tasks:
                  - id: Start
                    flows: [MilestoneEvent, EnabledAction]
                    split: xor
                  - id: MilestoneEvent
                    flows: [DisabledAction]
                  - id: EnabledAction
                  - id: DisabledAction
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(), "Milestone with missing flows should be unsound");
            assertTrue(result.violations().stream().anyMatch(v -> v.contains("dead-end") || v.contains("EnabledAction")),
                    "Should identify dead-end tasks");
        }

        @Test
        @DisplayName("Should be sound: temporary edit window before publication")
        void testEditWindowMilestone() {
            String yaml = """
                name: EditUntilPublished
                first: CreateDocument
                tasks:
                  - id: CreateDocument
                    flows: [EditDocument, PublishDocument]
                    split: xor
                  - id: EditDocument
                    flows: [CreateDocument, PublishDocument]
                    split: xor
                    condition: ready -> PublishDocument
                    default: CreateDocument
                  - id: PublishDocument
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Edit until published milestone should be sound. Violations: " + result.violations());
        }

        @Test
        @DisplayName("Should be sound: approve document before archival milestone")
        void testApproveBeforeArchive() {
            String yaml = """
                name: ApproveBeforeArchive
                first: SubmitDocument
                tasks:
                  - id: SubmitDocument
                    flows: [ReviewDocument, ArchiveDocument]
                    split: xor
                  - id: ReviewDocument
                    flows: [ApproveDocument, RejectDocument]
                    split: xor
                    condition: approved -> ApproveDocument
                    default: RejectDocument
                  - id: ApproveDocument
                    flows: [ArchiveDocument]
                  - id: RejectDocument
                    flows: [end]
                  - id: ArchiveDocument
                    flows: [end]
                """;

            SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Approve before archive milestone should be sound. Violations: " + result.violations());
        }
    }
}
