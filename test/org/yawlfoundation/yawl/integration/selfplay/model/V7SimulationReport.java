package org.yawlfoundation.yawl.integration.selfplay.model;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Final output of the YAWL v7 self-play design loop.
 *
 * <p>Produced by {@code V7SelfPlayOrchestrator} when the loop either converges
 * (fitness ≥ threshold) or exhausts the maximum number of rounds.
 *
 * @param totalRounds number of self-play rounds executed
 * @param converged true if the fitness threshold was reached
 * @param finalFitness fitness score at the end of the last round
 * @param acceptedProposals all proposals accepted across all rounds
 * @param allChallenges all challenges issued across all rounds
 * @param receiptHashes Blake3/SHA3-256 receipt hashes per round (index = round - 1)
 * @param addressedGaps set of v7 gaps that have at least one accepted proposal
 * @param durationMs total wall-clock time for the simulation in milliseconds
 * @param completedAt when the simulation completed
 */
public record V7SimulationReport(
    int totalRounds,
    boolean converged,
    FitnessScore finalFitness,
    List<DesignProposal> acceptedProposals,
    List<DesignChallenge> allChallenges,
    List<String> receiptHashes,
    Set<V7Gap> addressedGaps,
    long durationMs,
    Instant completedAt
) {

    public V7SimulationReport {
        if (totalRounds < 0) {
            throw new IllegalArgumentException("totalRounds must be >= 0, got: " + totalRounds);
        }
        if (finalFitness == null) {
            throw new IllegalArgumentException("finalFitness is required");
        }
        if (acceptedProposals == null) {
            acceptedProposals = List.of();
        }
        if (allChallenges == null) {
            allChallenges = List.of();
        }
        if (receiptHashes == null) {
            receiptHashes = List.of();
        }
        if (addressedGaps == null) {
            addressedGaps = Set.of();
        }
        if (completedAt == null) {
            completedAt = Instant.now();
        }
    }

    /**
     * Returns a human-readable summary suitable for logging or writing to a spec file.
     */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== YAWL v7 Self-Play Design Loop Report ===\n");
        sb.append("Completed at: ").append(completedAt).append("\n");
        sb.append("Duration: ").append(durationMs).append(" ms\n");
        sb.append("Rounds executed: ").append(totalRounds).append("\n");
        sb.append("Converged: ").append(converged).append("\n");
        sb.append("Final fitness: ").append(finalFitness).append("\n");
        sb.append("Gaps addressed: ").append(addressedGaps.size())
            .append("/").append(V7Gap.values().length).append("\n");
        sb.append("\nAccepted proposals (").append(acceptedProposals.size()).append("):\n");
        for (DesignProposal p : acceptedProposals) {
            sb.append("  [").append(p.gap().name()).append("] ")
                .append(p.title()).append(" (round ").append(p.round()).append(")\n");
        }
        sb.append("\nAudit trail (").append(receiptHashes.size()).append(" receipts):\n");
        for (int i = 0; i < receiptHashes.size(); i++) {
            sb.append("  Round ").append(i + 1).append(": ").append(receiptHashes.get(i)).append("\n");
        }
        return sb.toString();
    }
}
