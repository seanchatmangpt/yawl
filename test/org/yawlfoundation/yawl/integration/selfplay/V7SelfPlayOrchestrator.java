package org.yawlfoundation.yawl.integration.selfplay;

import org.yawlfoundation.yawl.integration.selfplay.model.Blake3Receipt;
import org.yawlfoundation.yawl.integration.selfplay.model.DesignChallenge;
import org.yawlfoundation.yawl.integration.selfplay.model.DesignProposal;
import org.yawlfoundation.yawl.integration.selfplay.model.FitnessScore;
import org.yawlfoundation.yawl.integration.selfplay.model.V7DesignState;
import org.yawlfoundation.yawl.integration.selfplay.model.V7SimulationReport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * YAWL v7 Self-Play Orchestrator — dogfooding YAWL's own workflow concepts to design YAWL v7.
 *
 * <p>Runs a self-play design loop where the same agent family both proposes and challenges
 * YAWL v7 specifications (AlphaGo-style). The loop models a YAWL workflow with tasks:
 * <ol>
 *   <li><b>INITIALIZE</b> — seed the design state from the v6 gap inventory</li>
 *   <li><b>PROPOSE</b> — V7DesignAgent proposes specs for all unaddressed gaps</li>
 *   <li><b>CHALLENGE</b> — V7ChallengeAgent attacks the proposals (self-play)</li>
 *   <li><b>EVALUATE</b> — accepted proposals merge into the cumulative design state</li>
 *   <li><b>MEASURE_FITNESS</b> — V7FitnessEvaluator scores the state</li>
 *   <li><b>XOR split</b> — if fitness ≥ threshold → FINALIZE; else → loop back to PROPOSE</li>
 * </ol>
 *
 * <p>Convergence condition: {@code fitness.total() >= fitnessThreshold} (default: 0.85).
 * The loop also stops if {@code round >= maxRounds} (default: 10).
 *
 * <p>Output: a {@link V7SimulationReport} with all accepted proposals, challenges,
 * per-round Blake3 receipt hashes, and the final fitness score.
 *
 * <p><b>Dogfooding</b>: This orchestrator uses YAWL's design vocabulary (work items as
 * proposals, OR-split for parallel proposal generation, XOR-split for convergence routing)
 * to structure its own logic — the system being designed is also the system doing the designing.
 */
public class V7SelfPlayOrchestrator {

    private static final double DEFAULT_FITNESS_THRESHOLD = 0.85;
    private static final int DEFAULT_MAX_ROUNDS = 10;

    private final V7DesignAgent designAgent;
    private final V7ChallengeAgent challengeAgent;
    private final double fitnessThreshold;
    private final int maxRounds;

    /**
     * Create an orchestrator with default convergence parameters.
     *
     * @param agentId identifier for both the design and challenge agents
     *                (self-play: same agent, dual roles)
     */
    public V7SelfPlayOrchestrator(String agentId) {
        this(agentId, DEFAULT_FITNESS_THRESHOLD, DEFAULT_MAX_ROUNDS);
    }

    /**
     * Create an orchestrator with custom convergence parameters.
     *
     * @param agentId identifier for both the design and challenge agents
     * @param fitnessThreshold fitness score at which the loop declares convergence
     * @param maxRounds maximum number of self-play rounds before forced termination
     */
    public V7SelfPlayOrchestrator(String agentId, double fitnessThreshold, int maxRounds) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId is required");
        }
        if (fitnessThreshold <= 0.0 || fitnessThreshold > 1.0) {
            throw new IllegalArgumentException(
                "fitnessThreshold must be in (0.0, 1.0], got: " + fitnessThreshold);
        }
        if (maxRounds < 1) {
            throw new IllegalArgumentException("maxRounds must be >= 1, got: " + maxRounds);
        }
        this.designAgent = new V7DesignAgent(agentId + "-proposer");
        this.challengeAgent = new V7ChallengeAgent(agentId + "-challenger");
        this.fitnessThreshold = fitnessThreshold;
        this.maxRounds = maxRounds;
    }

    /**
     * Run the self-play design loop to completion.
     *
     * <p>Workflow (models YAWL XOR-loop pattern):
     * <pre>
     *   [INITIALIZE] → [PROPOSE] → [CHALLENGE] → [EVALUATE] → [MEASURE_FITNESS]
     *                     ↑                                          |
     *                     |          fitness < threshold             |
     *                     +------------------------------------------+
     *                                                                |
     *                              fitness >= threshold              |
     *                                                         [FINALIZE]
     * </pre>
     *
     * @return simulation report with all proposals, challenges, receipts, and final fitness
     */
    public V7SimulationReport runLoop() {
        Instant started = Instant.now();

        // INITIALIZE: seed the design state from the v6 gap inventory
        V7DesignState state = V7DesignState.initial();
        List<String> receiptHashes = new ArrayList<>();

        String priorHash = "";
        boolean converged = false;

        // YAWL XOR-loop: repeat until fitness >= threshold or rounds exhausted
        for (int round = 1; round <= maxRounds; round++) {

            // PROPOSE task: agent proposes designs for all unaddressed gaps
            List<DesignProposal> proposals = designAgent.propose(state);

            if (proposals.isEmpty()) {
                // All gaps addressed — XOR-split routes to FINALIZE
                converged = state.fitnessScore().hasConverged(fitnessThreshold);
                break;
            }

            // CHALLENGE task: same agent family challenges the proposals (self-play)
            List<DesignChallenge> challenges = challengeAgent.challenge(proposals, round);

            // EVALUATE task: determine which proposals passed the challenge
            List<DesignProposal> newlyAccepted = resolveAccepted(proposals, challenges);

            // MEASURE_FITNESS task: evaluate cumulative fitness
            List<DesignProposal> cumulativeAccepted = new ArrayList<>(state.acceptedProposals());
            cumulativeAccepted.addAll(newlyAccepted);

            List<DesignChallenge> cumulativeChallenges = new ArrayList<>(state.allChallenges());
            cumulativeChallenges.addAll(challenges);

            FitnessScore fitness = V7FitnessEvaluator.evaluate(cumulativeAccepted, cumulativeChallenges);

            // Blake3 receipt: hash this round's evidence into the audit chain
            String receiptHash = Blake3Receipt.hash(round, proposals, challenges, fitness, priorHash);
            receiptHashes.add(receiptHash);
            priorHash = receiptHash;

            // Advance state
            state = state.nextRound(proposals, challenges, newlyAccepted, fitness, receiptHash);

            // XOR split: fitness >= threshold → FINALIZE
            if (fitness.hasConverged(fitnessThreshold)) {
                converged = true;
                break;
            }
        }

        long durationMs = Instant.now().toEpochMilli() - started.toEpochMilli();

        return new V7SimulationReport(
            state.round(),
            converged,
            state.fitnessScore(),
            state.acceptedProposals(),
            state.allChallenges(),
            receiptHashes,
            state.addressedGaps(),
            durationMs,
            Instant.now()
        );
    }

    /**
     * OR-join: determine which proposals survived the challenge round.
     * A proposal is accepted if its challenge verdict is ACCEPTED or MODIFIED.
     * REJECTED proposals re-enter the queue for the next PROPOSE round.
     */
    private static List<DesignProposal> resolveAccepted(
        List<DesignProposal> proposals,
        List<DesignChallenge> challenges
    ) {
        Set<String> rejectedIds = challenges.stream()
            .filter(DesignChallenge::isRejected)
            .map(DesignChallenge::proposalId)
            .collect(Collectors.toSet());

        return proposals.stream()
            .filter(p -> !rejectedIds.contains(p.proposalId()))
            .collect(Collectors.toList());
    }

    public double getFitnessThreshold() {
        return fitnessThreshold;
    }

    public int getMaxRounds() {
        return maxRounds;
    }
}
