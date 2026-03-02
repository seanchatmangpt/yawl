package org.yawlfoundation.yawl.integration.selfplay.model;

import org.yawlfoundation.yawl.integration.coordination.events.AgentDecisionEvent;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Final output of the YAWL v7 self-play design loop.
 *
 * <p>Produced by {@code V7SelfPlayOrchestrator} when the loop either converges
 * (fitness ≥ threshold) or exhausts the maximum number of rounds.
 *
 * <p>Uses Z.AI enterprise-standard AgentDecisionEvent for all proposals,
 * with immutable audit trail via ZAI audit log event IDs.
 *
 * @param totalRounds number of self-play rounds executed
 * @param converged true if the fitness threshold was reached
 * @param finalFitness fitness score at the end of the last round
 * @param acceptedProposals all proposals accepted across all rounds (AgentDecisionEvent)
 * @param allChallenges all challenges issued across all rounds
 * @param auditLogEventIds ZAI audit log event IDs for immutable traceability
 * @param addressedGaps set of v7 gaps that have at least one accepted proposal
 * @param durationMs total wall-clock time for the simulation in milliseconds
 * @param completedAt when the simulation completed
 * @param receiptHashes Blake3 (SHA3-256) receipt hashes forming a deterministic audit chain —
 *                      one hash per round; hash[i] incorporates hash[i-1] (hash chain).
 *                      Closes V7Gap.DETERMINISTIC_REPLAY_BLAKE3.
 */
public record V7SimulationReport(
    int totalRounds,
    boolean converged,
    FitnessScore finalFitness,
    List<AgentDecisionEvent> acceptedProposals,
    List<DesignChallenge> allChallenges,
    List<String> auditLogEventIds,
    Set<V7Gap> addressedGaps,
    long durationMs,
    Instant completedAt,
    List<String> receiptHashes
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
        if (auditLogEventIds == null) {
            auditLogEventIds = List.of();
        }
        if (addressedGaps == null) {
            addressedGaps = Set.of();
        }
        if (completedAt == null) {
            completedAt = Instant.now();
        }
        if (receiptHashes == null) {
            receiptHashes = List.of();
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
        for (AgentDecisionEvent p : acceptedProposals) {
            Object gap = p.getMetadata().get("gap");
            sb.append("  [").append(gap).append("] ")
                .append("(agent: ").append(p.getAgentId()).append(")\n");
        }
        sb.append("\nAudit trail (").append(auditLogEventIds.size()).append(" event IDs):\n");
        for (int i = 0; i < auditLogEventIds.size(); i++) {
            sb.append("  Event ").append(i + 1).append(": ").append(auditLogEventIds.get(i)).append("\n");
        }
        if (!receiptHashes.isEmpty()) {
            sb.append("\nBlake3 receipt chain (").append(receiptHashes.size()).append(" hashes):\n");
            for (int i = 0; i < receiptHashes.size(); i++) {
                sb.append("  Round ").append(i + 1).append(": ").append(receiptHashes.get(i)).append("\n");
            }
        }
        return sb.toString();
    }
}
