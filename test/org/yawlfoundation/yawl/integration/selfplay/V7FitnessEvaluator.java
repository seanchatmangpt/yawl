package org.yawlfoundation.yawl.integration.selfplay;

import org.yawlfoundation.yawl.integration.selfplay.model.DesignChallenge;
import org.yawlfoundation.yawl.integration.selfplay.model.DesignProposal;
import org.yawlfoundation.yawl.integration.selfplay.model.FitnessScore;
import org.yawlfoundation.yawl.integration.selfplay.model.V7Gap;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computes the fitness score for the YAWL v7 design state.
 *
 * <p>Four weighted axes:
 * <ol>
 *   <li><b>completeness</b> (35%): fraction of the 7 known v7 gaps that have at least
 *       one accepted proposal.</li>
 *   <li><b>consistency</b> (25%): 1.0 if no inter-proposal contradictions exist.
 *       Proposals for orthogonal gaps are always consistent. Contradiction = two accepted
 *       proposals for the same gap that disagree on backward-compat direction.</li>
 *   <li><b>compatibility</b> (25%): mean backward-compat score of accepted proposals.
 *       If no proposals are accepted, returns 0.0.</li>
 *   <li><b>performance</b> (15%): mean estimated performance gain of accepted proposals.
 *       If no proposals are accepted, returns 0.0.</li>
 * </ol>
 *
 * <p>All methods are stateless and thread-safe.
 */
public final class V7FitnessEvaluator {

    private static final int TOTAL_GAPS = V7Gap.values().length;

    private V7FitnessEvaluator() {
        throw new UnsupportedOperationException("V7FitnessEvaluator is a utility class");
    }

    /**
     * Evaluate the fitness of the current cumulative set of accepted proposals.
     *
     * @param acceptedProposals all proposals accepted across all self-play rounds
     * @param allChallenges all challenges issued across all self-play rounds
     * @return computed fitness score
     */
    public static FitnessScore evaluate(
        List<DesignProposal> acceptedProposals,
        List<DesignChallenge> allChallenges
    ) {
        double completeness = computeCompleteness(acceptedProposals);
        double consistency  = computeConsistency(acceptedProposals);
        double compatibility = computeCompatibility(acceptedProposals);
        double performance   = computePerformance(acceptedProposals);

        return new FitnessScore(completeness, consistency, compatibility, performance);
    }

    /**
     * Completeness: fraction of 7 known v7 gaps that have at least one accepted proposal.
     */
    static double computeCompleteness(List<DesignProposal> accepted) {
        if (accepted.isEmpty()) {
            return 0.0;
        }
        Set<V7Gap> addressedGaps = accepted.stream()
            .map(DesignProposal::gap)
            .collect(Collectors.toSet());
        return (double) addressedGaps.size() / TOTAL_GAPS;
    }

    /**
     * Consistency: 1.0 if no contradictions between accepted proposals.
     * A contradiction occurs when two accepted proposals for the same gap have
     * backward-compat scores that differ by more than 0.3 (conflicting design directions).
     */
    static double computeConsistency(List<DesignProposal> accepted) {
        if (accepted.size() <= 1) {
            return 1.0;
        }

        int contradictions = 0;
        // Group by gap, detect conflicting compat directions within the same gap
        for (V7Gap gap : V7Gap.values()) {
            List<DesignProposal> forGap = accepted.stream()
                .filter(p -> p.gap() == gap)
                .toList();
            if (forGap.size() < 2) continue;
            double minCompat = forGap.stream().mapToDouble(DesignProposal::backwardCompatScore).min().orElse(0);
            double maxCompat = forGap.stream().mapToDouble(DesignProposal::backwardCompatScore).max().orElse(0);
            if (maxCompat - minCompat > 0.3) {
                contradictions++;
            }
        }

        int maxPossibleContradictions = TOTAL_GAPS;
        return 1.0 - ((double) contradictions / maxPossibleContradictions);
    }

    /**
     * Compatibility: mean backward-compat score of accepted proposals.
     * Returns 0.0 if no proposals accepted.
     */
    static double computeCompatibility(List<DesignProposal> accepted) {
        if (accepted.isEmpty()) {
            return 0.0;
        }
        return accepted.stream()
            .mapToDouble(DesignProposal::backwardCompatScore)
            .average()
            .orElse(0.0);
    }

    /**
     * Performance: mean estimated performance gain of accepted proposals.
     * Returns 0.0 if no proposals accepted.
     */
    static double computePerformance(List<DesignProposal> accepted) {
        if (accepted.isEmpty()) {
            return 0.0;
        }
        return accepted.stream()
            .mapToDouble(DesignProposal::performanceGain)
            .average()
            .orElse(0.0);
    }
}
