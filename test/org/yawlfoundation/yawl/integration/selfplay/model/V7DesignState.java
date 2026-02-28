package org.yawlfoundation.yawl.integration.selfplay.model;

import org.yawlfoundation.yawl.integration.coordination.events.AgentDecisionEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable snapshot of the YAWL v7 design state after one self-play round.
 *
 * <p>Each round, the orchestrator calls {@link #nextRound} to produce a new state
 * incorporating the round's proposals (as AgentDecisionEvent), challenges, and newly-accepted proposals.
 * Accepted proposals accumulate across rounds (once accepted, always accepted).
 *
 * <p>This version uses AgentDecisionEvent (enterprise-standard immutable audit trail)
 * instead of the custom DesignProposal/Blake3Receipt approach.
 *
 * @param round round number (0 = initial seed state)
 * @param allProposals all proposals ever generated (as AgentDecisionEvent across all rounds)
 * @param allChallenges all challenges ever issued (across all rounds)
 * @param acceptedProposals proposals that have been accepted (cumulative, AgentDecisionEvent)
 * @param fitnessScore fitness computed after this round
 * @param auditLogEventIds ZAI audit log event IDs for traceability (replaces Blake3Receipt)
 * @param agentIds list of agents that participated in design
 * @param proposalsByAgent map of agentId -> AgentDecisionEvent for provenance
 * @param timestamp when this state was produced
 */
public record V7DesignState(
    int round,
    List<AgentDecisionEvent> allProposals,
    List<DesignChallenge> allChallenges,
    List<AgentDecisionEvent> acceptedProposals,
    FitnessScore fitnessScore,
    List<String> auditLogEventIds,
    List<String> agentIds,
    Map<String, AgentDecisionEvent> proposalsByAgent,
    Instant timestamp
) {

    public V7DesignState {
        allProposals = allProposals != null
            ? Collections.unmodifiableList(new ArrayList<>(allProposals))
            : List.of();
        allChallenges = allChallenges != null
            ? Collections.unmodifiableList(new ArrayList<>(allChallenges))
            : List.of();
        acceptedProposals = acceptedProposals != null
            ? Collections.unmodifiableList(new ArrayList<>(acceptedProposals))
            : List.of();
        auditLogEventIds = auditLogEventIds != null
            ? Collections.unmodifiableList(new ArrayList<>(auditLogEventIds))
            : List.of();
        agentIds = agentIds != null
            ? Collections.unmodifiableList(new ArrayList<>(agentIds))
            : List.of();
        proposalsByAgent = proposalsByAgent != null
            ? Collections.unmodifiableMap(new HashMap<>(proposalsByAgent))
            : Map.of();
        if (fitnessScore == null) {
            fitnessScore = FitnessScore.zero();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    /** Create the initial (empty) design state before any self-play rounds. */
    public static V7DesignState initial() {
        return new V7DesignState(
            0,
            List.of(),
            List.of(),
            List.of(),
            FitnessScore.zero(),
            List.of(),
            List.of(),
            Map.of(),
            Instant.now()
        );
    }

    /**
     * Produce the next state after a round completes.
     *
     * @param newProposals proposals generated in this round (AgentDecisionEvent)
     * @param newChallenges challenges issued in this round
     * @param newlyAccepted proposals that passed challenges in this round (AgentDecisionEvent)
     * @param newFitness fitness score computed for the cumulative accepted set
     * @param newAuditLogEventIds ZAI audit log event IDs for this round
     * @param newAgentIds agents participating in this round
     * @param newProposalsByAgent map of agentId -> AgentDecisionEvent for this round's proposals
     * @return new immutable state for round+1
     */
    public V7DesignState nextRound(
        List<AgentDecisionEvent> newProposals,
        List<DesignChallenge> newChallenges,
        List<AgentDecisionEvent> newlyAccepted,
        FitnessScore newFitness,
        List<String> newAuditLogEventIds,
        List<String> newAgentIds,
        Map<String, AgentDecisionEvent> newProposalsByAgent
    ) {
        List<AgentDecisionEvent> updatedProposals = new ArrayList<>(allProposals);
        updatedProposals.addAll(newProposals);

        List<DesignChallenge> updatedChallenges = new ArrayList<>(allChallenges);
        updatedChallenges.addAll(newChallenges);

        List<AgentDecisionEvent> updatedAccepted = new ArrayList<>(acceptedProposals);
        updatedAccepted.addAll(newlyAccepted);

        List<String> updatedAuditIds = new ArrayList<>(auditLogEventIds);
        if (newAuditLogEventIds != null) {
            updatedAuditIds.addAll(newAuditLogEventIds);
        }

        List<String> updatedAgentIds = new ArrayList<>(agentIds);
        if (newAgentIds != null) {
            for (String agentId : newAgentIds) {
                if (!updatedAgentIds.contains(agentId)) {
                    updatedAgentIds.add(agentId);
                }
            }
        }

        Map<String, AgentDecisionEvent> updatedProposalsByAgent = new HashMap<>(proposalsByAgent);
        if (newProposalsByAgent != null) {
            updatedProposalsByAgent.putAll(newProposalsByAgent);
        }

        return new V7DesignState(
            round + 1,
            updatedProposals,
            updatedChallenges,
            updatedAccepted,
            newFitness,
            updatedAuditIds,
            updatedAgentIds,
            updatedProposalsByAgent,
            Instant.now()
        );
    }

    /**
     * Returns the set of gaps that have at least one accepted proposal.
     * Extracts gap from AgentDecisionEvent metadata.
     */
    public Set<V7Gap> addressedGaps() {
        return acceptedProposals.stream()
            .map(event -> {
                // Gap is stored in metadata under 'gap' key
                String gapStr = (String) event.getMetadata().get("gap");
                if (gapStr != null) {
                    try {
                        return V7Gap.valueOf(gapStr);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                }
                return null;
            })
            .filter(g -> g != null)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(V7Gap.class)));
    }

    /**
     * Returns the set of gaps that have NOT yet been accepted.
     */
    public Set<V7Gap> unaddressedGaps() {
        Set<V7Gap> all = EnumSet.allOf(V7Gap.class);
        all.removeAll(addressedGaps());
        return all;
    }
}
