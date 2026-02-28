package org.yawlfoundation.yawl.integration.selfplay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.selfplay.model.DesignProposal;
import org.yawlfoundation.yawl.integration.selfplay.model.FitnessScore;
import org.yawlfoundation.yawl.integration.selfplay.model.V7DesignState;
import org.yawlfoundation.yawl.integration.selfplay.model.V7Gap;
import org.yawlfoundation.yawl.integration.selfplay.model.V7SimulationReport;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD end-to-end tests for the YAWL v7 self-play design loop.
 *
 * <p>No mocks. Every test exercises the full loop through real agent logic:
 * V7DesignAgent → V7ChallengeAgent → V7FitnessEvaluator → V7SelfPlayOrchestrator.
 *
 * <p>The tests verify:
 * <ol>
 *   <li>Convergence: fitness ≥ 0.85 within 5 rounds</li>
 *   <li>Completeness: all 7 known v7 gaps have accepted proposals</li>
 *   <li>Audit trail: every round produces a non-empty Blake3 receipt hash</li>
 *   <li>Monotonicity: fitness never decreases across rounds</li>
 *   <li>Backward compat: all accepted proposals have backwardCompatScore > 0</li>
 *   <li>Report integrity: summary is non-blank and contains expected content</li>
 * </ol>
 */
class V7SelfPlayLoopTest {

    private V7SelfPlayOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new V7SelfPlayOrchestrator("v7-design-agent", 0.85, 10);
    }

    /**
     * The self-play loop must converge (fitness ≥ 0.85) within 5 rounds.
     *
     * <p>Convergence validates that the fitness function is well-calibrated and
     * the challenge threshold decay brings proposals into alignment with design goals.
     */
    @Test
    void testSelfPlayConvergesWithinFiveRounds() {
        V7SimulationReport report = orchestrator.runLoop();

        assertTrue(report.converged(),
            "Self-play loop must converge within max rounds. Final fitness: "
                + report.finalFitness().total());

        assertTrue(report.totalRounds() <= 5,
            "Expected convergence within 5 rounds, but took " + report.totalRounds() + " rounds. "
                + "Fitness per round should increase monotonically towards threshold.");

        assertTrue(report.finalFitness().total() >= 0.85,
            "Final fitness " + report.finalFitness().total() + " must be >= 0.85");
    }

    /**
     * All 7 known v7 gaps must have at least one accepted proposal when the loop converges.
     *
     * <p>This validates completeness: the design covers every gap identified in the v6
     * codebase analysis.
     */
    @Test
    void testAllV7GapsHaveAcceptedProposals() {
        V7SimulationReport report = orchestrator.runLoop();

        Set<V7Gap> addressedGaps = report.addressedGaps();

        assertEquals(V7Gap.values().length, addressedGaps.size(),
            "All " + V7Gap.values().length + " v7 gaps must have accepted proposals. "
                + "Missing: " + computeMissing(addressedGaps));

        for (V7Gap gap : V7Gap.values()) {
            assertTrue(addressedGaps.contains(gap),
                "Gap " + gap.name() + " (" + gap.title + ") must have an accepted proposal");
        }
    }

    /**
     * Every self-play round must produce a non-empty Blake3 receipt hash.
     *
     * <p>This validates the audit trail: each round is cryptographically receipted,
     * forming a tamper-evident chain that enables deterministic replay.
     */
    @Test
    void testAuditTrailCompleteWithNonEmptyHashes() {
        V7SimulationReport report = orchestrator.runLoop();

        List<String> receipts = report.receiptHashes();

        assertFalse(receipts.isEmpty(),
            "Must have at least one receipt hash — one per self-play round");

        assertEquals(report.totalRounds(), receipts.size(),
            "Receipt count " + receipts.size() + " must equal round count " + report.totalRounds());

        for (int i = 0; i < receipts.size(); i++) {
            String receipt = receipts.get(i);
            assertFalse(receipt.isBlank(),
                "Round " + (i + 1) + " receipt hash must not be blank");
            // SHA3-256 produces 64 hex chars
            assertEquals(64, receipt.length(),
                "Round " + (i + 1) + " receipt hash must be 64-char SHA3-256 hex, got: " + receipt);
        }

        // Receipts must be distinct (different rounds produce different hashes)
        long distinctCount = receipts.stream().distinct().count();
        assertEquals(receipts.size(), distinctCount,
            "All round receipts must be distinct (different proposals + fitness each round)");
    }

    /**
     * Fitness must be monotonically non-decreasing across rounds.
     *
     * <p>Since accepted proposals accumulate (never removed), and completeness/compatibility/
     * performance are computed on the growing accepted set, total fitness must not decrease
     * from one round to the next.
     */
    @Test
    void testFitnessIsMonotonicallyNonDecreasing() {
        // Run the loop round by round and collect fitness per round
        V7DesignAgent agent = new V7DesignAgent("monotone-proposer");
        V7ChallengeAgent challenger = new V7ChallengeAgent("monotone-challenger");

        V7DesignState state = V7DesignState.initial();
        double previousFitness = 0.0;
        String priorHash = "";

        for (int round = 1; round <= 5 && !state.unaddressedGaps().isEmpty(); round++) {
            List<DesignProposal> proposals = agent.propose(state);
            if (proposals.isEmpty()) break;

            var challenges = challenger.challenge(proposals, round);

            List<DesignProposal> accepted = proposals.stream()
                .filter(p -> challenges.stream()
                    .filter(c -> c.proposalId().equals(p.proposalId()))
                    .allMatch(c -> !c.isRejected()))
                .toList();

            List<DesignProposal> cumulative = new java.util.ArrayList<>(state.acceptedProposals());
            cumulative.addAll(accepted);

            FitnessScore fitness = V7FitnessEvaluator.evaluate(cumulative, state.allChallenges());
            String receipt = org.yawlfoundation.yawl.integration.selfplay.model.Blake3Receipt.hash(
                round, proposals, challenges, fitness, priorHash);
            priorHash = receipt;

            state = state.nextRound(proposals, challenges, accepted, fitness, receipt);

            double currentFitness = fitness.total();
            assertTrue(currentFitness >= previousFitness,
                "Fitness must not decrease. Round " + round + ": " + currentFitness
                    + " < previous " + previousFitness);
            previousFitness = currentFitness;
        }
    }

    /**
     * All accepted proposals must have a backwardCompatScore > 0.
     *
     * <p>Validates v6 interface contract preservation: no accepted v7 proposal should
     * completely break backward compatibility with v6 clients.
     */
    @Test
    void testAllAcceptedProposalsPreserveBackwardCompatibility() {
        V7SimulationReport report = orchestrator.runLoop();

        List<DesignProposal> accepted = report.acceptedProposals();

        assertFalse(accepted.isEmpty(), "Must have at least one accepted proposal");

        for (DesignProposal proposal : accepted) {
            assertTrue(proposal.backwardCompatScore() > 0.0,
                "Accepted proposal for gap " + proposal.gap().name()
                    + " must have backwardCompatScore > 0, got: " + proposal.backwardCompatScore());

            // High bar: all accepted proposals must be at least 60% backward-compatible
            assertTrue(proposal.backwardCompatScore() >= 0.60,
                "Accepted proposal for " + proposal.gap().name()
                    + " must have backwardCompatScore >= 0.60 (v6 interface contract), "
                    + "got: " + proposal.backwardCompatScore());
        }
    }

    /**
     * The simulation report summary must be non-blank and contain key content markers.
     *
     * <p>Validates report generation: the output can be written to YAWL_v7_DESIGN_SPEC.md
     * as a human-readable design specification.
     */
    @Test
    void testSimulationReportSummaryIsComplete() {
        V7SimulationReport report = orchestrator.runLoop();

        String summary = report.summary();

        assertNotNull(summary, "Summary must not be null");
        assertFalse(summary.isBlank(), "Summary must not be blank");

        assertTrue(summary.contains("YAWL v7"),
            "Summary must reference YAWL v7");
        assertTrue(summary.contains("Converged: true"),
            "Summary must indicate convergence. Got: " + summary);
        assertTrue(summary.contains("Accepted proposals"),
            "Summary must list accepted proposals");
        assertTrue(summary.contains("Audit trail"),
            "Summary must include audit trail section");

        // Must mention at least one gap name
        boolean mentionsAGap = false;
        for (V7Gap gap : V7Gap.values()) {
            if (summary.contains(gap.name())) {
                mentionsAGap = true;
                break;
            }
        }
        assertTrue(mentionsAGap, "Summary must mention at least one V7Gap by name");
    }

    // ==================== Helpers ====================

    private static Set<V7Gap> computeMissing(Set<V7Gap> addressed) {
        Set<V7Gap> all = java.util.EnumSet.allOf(V7Gap.class);
        all.removeAll(addressed);
        return all;
    }
}
