package org.yawlfoundation.yawl.integration.selfplay.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable snapshot of the YAWL v7 design state after one self-play round.
 *
 * <p>Each round, the orchestrator calls {@link #nextRound} to produce a new state
 * incorporating the round's proposals, challenges, and newly-accepted proposals.
 * Accepted proposals accumulate across rounds (once accepted, always accepted).
 *
 * @param round round number (0 = initial seed state)
 * @param allProposals all proposals ever generated (across all rounds)
 * @param allChallenges all challenges ever issued (across all rounds)
 * @param acceptedProposals proposals that have been accepted (cumulative, not per-round)
 * @param fitnessScore fitness computed after this round
 * @param receiptHash Blake3/SHA3-256 receipt hash for this round (empty for round 0)
 * @param timestamp when this state was produced
 */
public record V7DesignState(
    int round,
    List<DesignProposal> allProposals,
    List<DesignChallenge> allChallenges,
    List<DesignProposal> acceptedProposals,
    FitnessScore fitnessScore,
    String receiptHash,
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
        if (fitnessScore == null) {
            fitnessScore = FitnessScore.zero();
        }
        if (receiptHash == null) {
            receiptHash = "";
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
            "",
            Instant.now()
        );
    }

    /**
     * Produce the next state after a round completes.
     *
     * @param newProposals proposals generated in this round
     * @param newChallenges challenges issued in this round
     * @param newlyAccepted proposals that passed challenges in this round
     * @param newFitness fitness score computed for the cumulative accepted set
     * @param receiptHash Blake3 receipt hash for this round
     * @return new immutable state for round+1
     */
    public V7DesignState nextRound(
        List<DesignProposal> newProposals,
        List<DesignChallenge> newChallenges,
        List<DesignProposal> newlyAccepted,
        FitnessScore newFitness,
        String receiptHash
    ) {
        List<DesignProposal> updatedProposals = new ArrayList<>(allProposals);
        updatedProposals.addAll(newProposals);

        List<DesignChallenge> updatedChallenges = new ArrayList<>(allChallenges);
        updatedChallenges.addAll(newChallenges);

        List<DesignProposal> updatedAccepted = new ArrayList<>(acceptedProposals);
        updatedAccepted.addAll(newlyAccepted);

        return new V7DesignState(
            round + 1,
            updatedProposals,
            updatedChallenges,
            updatedAccepted,
            newFitness,
            receiptHash,
            Instant.now()
        );
    }

    /**
     * Returns the set of gaps that have at least one accepted proposal.
     */
    public Set<V7Gap> addressedGaps() {
        return acceptedProposals.stream()
            .map(DesignProposal::gap)
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
