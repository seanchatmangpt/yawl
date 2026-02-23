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
 * Comprehensive JUnit 5 tests for YAWL Workflow Control Patterns (WCP) 1-9,
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
 *   <li><strong>WCP-1 (Sequence)</strong>: Order fulfillment pipeline (Order → Verify → Pack → Ship)</li>
 *   <li><strong>WCP-2 (Parallel Split)</strong>: Insurance claim triggers parallel reviews</li>
 *   <li><strong>WCP-3 (Synchronization)</strong>: All parallel reviews must complete before approval</li>
 *   <li><strong>WCP-4 (Exclusive Choice)</strong>: Credit check decision (approve or reject)</li>
 *   <li><strong>WCP-5 (Simple Merge)</strong>: Multiple approval routes merge before final processing</li>
 *   <li><strong>WCP-6 (Multi-Choice)</strong>: Product order with optional and mandatory processing</li>
 *   <li><strong>WCP-7 (Structured Synchronizing Merge)</strong>: OR-join waiting for all active branches</li>
 *   <li><strong>WCP-8 (Multi-Merge)</strong>: Multiple activations at merge trigger separately</li>
 *   <li><strong>WCP-9 (Structured Discriminator)</strong>: First-to-complete wins pattern</li>
 * </ul>
 *
 * <p>All tests use REAL {@link WorkflowSoundnessVerifier} instances on actual workflow
 * specifications. No mocks, stubs, or fake implementations (Chicago/Detroit TDD).
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("WCP 1-9: Business Patterns Test Suite")
public class WcpBusinessPatterns1to9Test {

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
    // WCP-1: Sequence
    // =========================================================================

    @Nested
    @DisplayName("WCP-1: Sequence - Simple sequential task execution")
    class SequencePattern {

        @Test
        @DisplayName("Sound: OrderFulfillment pipeline (Order → Verify → Pack → Ship)")
        void testOrderFulfillmentSequence() {
            var workflow = buildWorkflow(
                "OrderFulfillment",
                "ReceiveOrder",
                List.of(
                    buildTask("ReceiveOrder", "VerifyPayment"),
                    buildTask("VerifyPayment", "CheckInventory"),
                    buildTask("CheckInventory", "PackOrder"),
                    buildTask("PackOrder", "ShipOrder"),
                    buildTask("ShipOrder", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "OrderFulfillment sequence should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Sound: Simplified purchase order (Request → Approve → Process)")
        void testSimplifiedPurchaseOrder() {
            var workflow = buildWorkflow(
                "PurchaseOrder",
                "RequestOrder",
                List.of(
                    buildTask("RequestOrder", "ApproveOrder"),
                    buildTask("ApproveOrder", "ProcessPayment"),
                    buildTask("ProcessPayment", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Purchase order sequence should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Unsound: Missing final flow in sequence creates dead-end")
        void testSequenceWithMissingFlow() {
            var workflow = buildWorkflow(
                "BrokenSequence",
                "FirstTask",
                List.of(
                    buildTask("FirstTask", "SecondTask"),
                    buildTask("SecondTask", "ThirdTask"),
                    buildTask("ThirdTask")  // No flows - dead-end
                )
            );

            var result = verifier.verify(workflow);

            assertFalse(result.sound(),
                "Sequence with missing final flow should be unsound");
            assertTrue(result.violations().stream()
                    .anyMatch(v -> v.contains("dead-end") || v.contains("cannot reach")),
                "Should report dead-end violation: " + result.violations());
        }
    }

    // =========================================================================
    // WCP-2: Parallel Split (AND-Split)
    // =========================================================================

    @Nested
    @DisplayName("WCP-2: Parallel Split - AND-split fan-out")
    class ParallelSplitPattern {

        @Test
        @DisplayName("Sound: Insurance claim triggers parallel medical and financial reviews")
        void testInsuranceClaimParallelSplit() {
            var workflow = buildWorkflow(
                "InsuranceClaim",
                "ReceiveClaim",
                List.of(
                    buildTask("ReceiveClaim", "MedicalReview", "FinancialReview", "LegalReview"),
                    buildTask("MedicalReview", "end"),
                    buildTask("FinancialReview", "end"),
                    buildTask("LegalReview", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Parallel split in insurance claim should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Sound: Customer order triggers parallel gift wrap and express shipping")
        void testOrderParallelOptions() {
            var workflow = buildWorkflow(
                "OrderProcessing",
                "StartOrder",
                List.of(
                    buildTask("StartOrder", "StandardProcessing", "GiftWrapOption"),
                    buildTask("StandardProcessing", "end"),
                    buildTask("GiftWrapOption", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Order parallel processing should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Unsound: Parallel split references undefined task")
        void testParallelSplitWithUndefinedTarget() {
            var workflow = buildWorkflow(
                "BrokenParallel",
                "StartProcess",
                List.of(
                    buildTask("StartProcess", "Branch1", "UndefinedBranch"),
                    buildTask("Branch1", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertFalse(result.sound(),
                "Parallel split with undefined target should be unsound");
            assertTrue(result.violations().stream()
                    .anyMatch(v -> v.contains("unknown") || v.contains("undefined") || v.contains("cannot reach")),
                "Should report undefined task or unreachable condition; violations: " + result.violations());
        }
    }

    // =========================================================================
    // WCP-3: Synchronization (AND-Join)
    // =========================================================================

    @Nested
    @DisplayName("WCP-3: Synchronization - AND-join (all branches must complete)")
    class SynchronizationPattern {

        @Test
        @DisplayName("Sound: All parallel reviews must complete before final approval")
        void testInsuranceClaimSynchronization() {
            var workflow = buildWorkflow(
                "InsuranceApproval",
                "ReceiveClaim",
                List.of(
                    buildTask("ReceiveClaim", "MedicalReview", "FinancialReview"),
                    buildTask("MedicalReview", "FinalApproval"),
                    buildTask("FinancialReview", "FinalApproval"),
                    buildTask("FinalApproval", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Claim with synchronizing approval should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Sound: Three-way AND-join (medical, financial, legal all required)")
        void testThreeWayAndJoin() {
            var workflow = buildWorkflow(
                "ComplexInsuranceClaim",
                "ReceiveClaim",
                List.of(
                    buildTask("ReceiveClaim", "MedicalReview", "FinancialReview", "LegalReview"),
                    buildTask("MedicalReview", "Consolidate"),
                    buildTask("FinancialReview", "Consolidate"),
                    buildTask("LegalReview", "Consolidate"),
                    buildTask("Consolidate", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Three-way AND-join should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Unsound: AND-join missing incoming branch (incomplete synchronization)")
        void testAndJoinWithMissingBranch() {
            var workflow = buildWorkflow(
                "IncompleteSync",
                "StartProcess",
                List.of(
                    buildTask("StartProcess", "Branch1", "Branch2"),
                    buildTask("Branch1", "Merge"),
                    buildTask("Branch2"),  // Dead-end: doesn't flow to Merge
                    buildTask("Merge", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertFalse(result.sound(),
                "AND-join with unreachable output should be unsound");
            assertTrue(result.violations().stream()
                    .anyMatch(v -> v.contains("dead-end") || v.contains("cannot reach")),
                "Should report dead-end or unreachable condition; violations: " + result.violations());
        }
    }

    // =========================================================================
    // WCP-4: Exclusive Choice (XOR)
    // =========================================================================

    @Nested
    @DisplayName("WCP-4: Exclusive Choice - XOR decision (one branch taken)")
    class ExclusiveChoicePattern {

        @Test
        @DisplayName("Sound: Customer credit check decides approve or reject path")
        void testCreditCheckDecision() {
            var workflow = buildWorkflow(
                "CreditEvaluation",
                "SubmitApplication",
                List.of(
                    buildTask("SubmitApplication", "CheckCredit"),
                    buildTask("CheckCredit", "ApproveCredit", "RejectCredit"),
                    buildTask("ApproveCredit", "IssueLoan"),
                    buildTask("RejectCredit", "ReturnApplication"),
                    buildTask("IssueLoan", "end"),
                    buildTask("ReturnApplication", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Credit decision with XOR should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Sound: Loan application routes to fast-track or standard approval")
        void testLoanApplicationRouting() {
            var workflow = buildWorkflow(
                "LoanApplication",
                "ReceiveApplication",
                List.of(
                    buildTask("ReceiveApplication", "EvaluateAmount"),
                    buildTask("EvaluateAmount", "FastTrack", "StandardReview"),
                    buildTask("FastTrack", "FinalDecision"),
                    buildTask("StandardReview", "FinalDecision"),
                    buildTask("FinalDecision", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Loan routing with XOR should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Unsound: XOR split to undefined rejection path")
        void testXorWithUndefinedPath() {
            var workflow = buildWorkflow(
                "BrokenDecision",
                "EvaluateRequest",
                List.of(
                    buildTask("EvaluateRequest", "Approve", "NonExistentReject"),
                    buildTask("Approve", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertFalse(result.sound(),
                "XOR with undefined target should be unsound");
            assertTrue(result.violations().stream()
                    .anyMatch(v -> v.contains("unknown") || v.contains("cannot reach")),
                "Should report undefined or unreachable task; violations: " + result.violations());
        }
    }

    // =========================================================================
    // WCP-5: Simple Merge (XOR-Join)
    // =========================================================================

    @Nested
    @DisplayName("WCP-5: Simple Merge - XOR-join (first branch to complete continues)")
    class SimpleMergePattern {

        @Test
        @DisplayName("Sound: Multiple approval routes merge before final processing")
        void testApprovalMerge() {
            var workflow = buildWorkflow(
                "PurchaseApproval",
                "SubmitRequest",
                List.of(
                    buildTask("SubmitRequest", "ManagerReview", "DirectorReview"),
                    buildTask("ManagerReview", "FinalProcess"),
                    buildTask("DirectorReview", "FinalProcess"),
                    buildTask("FinalProcess", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Approval merge should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Sound: Return/Escalation paths merge to notification task")
        void testReturnEscalationMerge() {
            var workflow = buildWorkflow(
                "DisputeHandling",
                "ReceiveDispute",
                List.of(
                    buildTask("ReceiveDispute", "AutoResolve", "ManualReview"),
                    buildTask("AutoResolve", "SendNotification"),
                    buildTask("ManualReview", "SendNotification"),
                    buildTask("SendNotification", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Dispute merge should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Unsound: Simple merge with one path leading to dead-end")
        void testMergeWithDeadEnd() {
            var workflow = buildWorkflow(
                "BrokenMerge",
                "StartProcess",
                List.of(
                    buildTask("StartProcess", "Path1", "Path2"),
                    buildTask("Path1", "Merge"),
                    buildTask("Path2"),  // Dead-end
                    buildTask("Merge", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertFalse(result.sound(),
                "Merge with dead-end path should be unsound");
            assertTrue(result.violations().stream()
                    .anyMatch(v -> v.contains("dead-end")),
                "Should report dead-end violation; violations: " + result.violations());
        }
    }

    // =========================================================================
    // WCP-6: Multi-Choice (OR-Split)
    // =========================================================================

    @Nested
    @DisplayName("WCP-6: Multi-Choice - OR-split (one or more branches taken)")
    class MultiChoicePattern {

        @Test
        @DisplayName("Sound: Product order with optional gift-wrap and express shipping")
        void testOrderWithOptionalServices() {
            var workflow = buildWorkflow(
                "ProductOrder",
                "StartOrder",
                List.of(
                    buildTask("StartOrder", "ProcessPayment", "AddGiftWrap", "ExpressShipping"),
                    buildTask("ProcessPayment", "PrepareShipment"),
                    buildTask("AddGiftWrap", "PrepareShipment"),
                    buildTask("ExpressShipping", "PrepareShipment"),
                    buildTask("PrepareShipment", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "OR-split with optional services should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Sound: Conference registration with optional workshop and networking")
        void testConferenceRegistration() {
            var workflow = buildWorkflow(
                "ConferenceReg",
                "Register",
                List.of(
                    buildTask("Register", "AttendMainTrack", "AttendWorkshop", "AttendNetworking"),
                    buildTask("AttendMainTrack", "GenerateCertificate"),
                    buildTask("AttendWorkshop", "GenerateCertificate"),
                    buildTask("AttendNetworking", "GenerateCertificate"),
                    buildTask("GenerateCertificate", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Conference OR-split should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Unsound: OR-split with unreachable convergence point")
        void testOrSplitWithUnreachableConvergence() {
            var workflow = buildWorkflow(
                "BrokenOrSplit",
                "Start",
                List.of(
                    buildTask("Start", "Option1", "Option2", "Option3"),
                    buildTask("Option1", "end"),
                    buildTask("Option2", "end"),
                    buildTask("Option3")  // Dead-end
                )
            );

            var result = verifier.verify(workflow);

            assertFalse(result.sound(),
                "OR-split with dead-end should be unsound");
            assertTrue(result.violations().stream()
                    .anyMatch(v -> v.contains("dead-end")),
                "Should report dead-end violation; violations: " + result.violations());
        }
    }

    // =========================================================================
    // WCP-7: Structured Synchronizing Merge (OR-Join)
    // =========================================================================

    @Nested
    @DisplayName("WCP-7: Structured Synchronizing Merge - OR-join (wait for all active branches)")
    class StructuredSynchronizingMergePattern {

        @Test
        @DisplayName("Sound: Optional services converge before shipment (all active paths waited)")
        void testOptionalServicesConverge() {
            var workflow = buildWorkflow(
                "ShipmentPrep",
                "StartProcess",
                List.of(
                    buildTask("StartProcess", "ProcessPayment", "OptionalWrapping", "OptionalInsurance"),
                    buildTask("ProcessPayment", "ReadyShipment"),
                    buildTask("OptionalWrapping", "ReadyShipment"),
                    buildTask("OptionalInsurance", "ReadyShipment"),
                    buildTask("ReadyShipment", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "OR-join with optional branches should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Sound: Claim processing with conditional parallel reviews")
        void testConditionalClaimReviews() {
            var workflow = buildWorkflow(
                "ConditionalClaim",
                "StartClaim",
                List.of(
                    buildTask("StartClaim", "QuickCheck", "MedicalReview", "FinancialReview"),
                    buildTask("QuickCheck", "ApplyDecision"),
                    buildTask("MedicalReview", "ApplyDecision"),
                    buildTask("FinancialReview", "ApplyDecision"),
                    buildTask("ApplyDecision", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Conditional claim reviews should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Unsound: OR-join with one branch as dead-end")
        void testOrJoinWithDeadEnd() {
            var workflow = buildWorkflow(
                "BrokenOrJoin",
                "Start",
                List.of(
                    buildTask("Start", "Branch1", "Branch2"),
                    buildTask("Branch1", "Converge"),
                    buildTask("Branch2"),  // Dead-end
                    buildTask("Converge", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertFalse(result.sound(),
                "OR-join with dead-end branch should be unsound");
            assertTrue(result.violations().stream()
                    .anyMatch(v -> v.contains("dead-end")),
                "Should report dead-end violation; violations: " + result.violations());
        }
    }

    // =========================================================================
    // WCP-8: Multi-Merge (Multiple Instances at Merge)
    // =========================================================================

    @Nested
    @DisplayName("WCP-8: Multi-Merge - Multiple activations at merge trigger separately")
    class MultiMergePattern {

        @Test
        @DisplayName("Sound: Loop creates multiple tokens that each process separately")
        void testMultiMergeWithLoop() {
            var workflow = buildWorkflow(
                "BatchProcessing",
                "StartBatch",
                List.of(
                    buildTask("StartBatch", "ProcessItem"),
                    buildTask("ProcessItem", "CheckMore", "NextStep"),
                    buildTask("CheckMore", "ProcessItem"),  // Loop creates multiple tokens
                    buildTask("NextStep", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Loop-based multi-merge should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Sound: Recursive approval with merge point")
        void testRecursiveApprovalMerge() {
            var workflow = buildWorkflow(
                "RecursiveApproval",
                "SubmitRequest",
                List.of(
                    buildTask("SubmitRequest", "ReviewLevel1"),
                    buildTask("ReviewLevel1", "EscalateToLevel2", "ApproveRequest"),
                    buildTask("EscalateToLevel2", "ReviewLevel1"),  // Back to Level1
                    buildTask("ApproveRequest", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Recursive approval should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Unsound: Loop exits to undefined task")
        void testMultiMergeWithUndefinedExit() {
            var workflow = buildWorkflow(
                "BrokenLoop",
                "Start",
                List.of(
                    buildTask("Start", "Process"),
                    buildTask("Process", "Loop", "UndefinedEnd"),
                    buildTask("Loop", "Process")
                )
            );

            var result = verifier.verify(workflow);

            assertFalse(result.sound(),
                "Loop with undefined exit should be unsound");
            assertTrue(result.violations().stream()
                    .anyMatch(v -> v.contains("unknown") || v.contains("cannot reach")),
                "Should report undefined or unreachable task; violations: " + result.violations());
        }
    }

    // =========================================================================
    // WCP-9: Structured Discriminator
    // =========================================================================

    @Nested
    @DisplayName("WCP-9: Structured Discriminator - First-to-complete triggers continuation")
    class StructuredDiscriminatorPattern {

        @Test
        @DisplayName("Sound: Parallel competitive bids, first accepted bid wins")
        void testCompetitiveBids() {
            var workflow = buildWorkflow(
                "CompetitiveBidding",
                "OpenBidding",
                List.of(
                    buildTask("OpenBidding", "ReceiveBid1", "ReceiveBid2", "ReceiveBid3"),
                    buildTask("ReceiveBid1", "SelectWinner"),
                    buildTask("ReceiveBid2", "SelectWinner"),
                    buildTask("ReceiveBid3", "SelectWinner"),
                    buildTask("SelectWinner", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Competitive bids should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Sound: Parallel shipment methods, fastest arrival wins")
        void testShipmentMethodDiscriminator() {
            var workflow = buildWorkflow(
                "FastShipment",
                "OrderShipment",
                List.of(
                    buildTask("OrderShipment", "SendAirFreight", "SendOceanFreight", "SendTruck"),
                    buildTask("SendAirFreight", "ReceiveShipment"),
                    buildTask("SendOceanFreight", "ReceiveShipment"),
                    buildTask("SendTruck", "ReceiveShipment"),
                    buildTask("ReceiveShipment", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertTrue(result.sound(),
                "Shipment method discriminator should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Unsound: Discriminator with incomplete branches")
        void testDiscriminatorWithIncompleteBranch() {
            var workflow = buildWorkflow(
                "BrokenDiscriminator",
                "Start",
                List.of(
                    buildTask("Start", "Branch1", "Branch2"),
                    buildTask("Branch1", "Winner"),
                    buildTask("Branch2"),  // Dead-end
                    buildTask("Winner", "end")
                )
            );

            var result = verifier.verify(workflow);

            assertFalse(result.sound(),
                "Discriminator with dead-end should be unsound");
            assertTrue(result.violations().stream()
                    .anyMatch(v -> v.contains("dead-end")),
                "Should report dead-end violation; violations: " + result.violations());
        }
    }
}
