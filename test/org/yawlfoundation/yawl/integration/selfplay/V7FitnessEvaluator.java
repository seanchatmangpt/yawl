package org.yawlfoundation.yawl.integration.selfplay;

import org.yawlfoundation.yawl.integration.coordination.events.AgentDecisionEvent;
import org.yawlfoundation.yawl.integration.selfplay.model.DesignChallenge;
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
 *   <li><b>completeness</b> (35%): fraction of the known v7 gaps that have at least
 *       one accepted proposal (AgentDecisionEvent).</li>
 *   <li><b>consistency</b> (25%): 1.0 if no inter-proposal contradictions exist.
 *       Proposals for orthogonal gaps are always consistent. Contradiction = two accepted
 *       proposals for the same gap that have v6_interface_impact scores that differ by >0.3.</li>
 *   <li><b>compatibility</b> (25%): mean v6_interface_impact score of accepted proposals.
 *       Extracted from event.metadata("v6_interface_impact"). Returns 0.0 if no proposals.</li>
 *   <li><b>performance</b> (15%): mean estimated_gain of accepted proposals.
 *       Extracted from event.metadata("estimated_gain"). Returns 0.0 if no proposals.</li>
 * </ol>
 *
 * <p>All methods are stateless and thread-safe.
 * Uses AgentDecisionEvent (enterprise-standard) instead of custom DesignProposal.
 */
public final class V7FitnessEvaluator {

    private static final int TOTAL_GAPS = V7Gap.values().length;

    private V7FitnessEvaluator() {
        throw new UnsupportedOperationException("V7FitnessEvaluator is a utility class");
    }

    /**
     * Evaluate the fitness of the current cumulative set of accepted proposals.
     *
     * @param acceptedProposals all proposals accepted across all self-play rounds (AgentDecisionEvent)
     * @param allChallenges all challenges issued across all self-play rounds
     * @return computed fitness score
     */
    public static FitnessScore evaluate(
        List<AgentDecisionEvent> acceptedProposals,
        List<DesignChallenge> allChallenges
    ) {
        double completeness = computeCompleteness(acceptedProposals);
        double consistency  = computeConsistency(acceptedProposals);
        double compatibility = computeCompatibility(acceptedProposals);
        double performance   = computePerformance(acceptedProposals);

        return new FitnessScore(completeness, consistency, compatibility, performance);
    }

    /**
     * Completeness: fraction of known v7 gaps that have at least one accepted proposal.
     */
    static double computeCompleteness(List<AgentDecisionEvent> accepted) {
        if (accepted.isEmpty()) {
            return 0.0;
        }
        Set<V7Gap> addressedGaps = accepted.stream()
            .map(event -> extractGapFromEvent(event))
            .filter(gap -> gap != null)
            .collect(Collectors.toSet());
        return (double) addressedGaps.size() / TOTAL_GAPS;
    }

    /**
     * Consistency: 1.0 if no contradictions between accepted proposals.
     * A contradiction occurs when two accepted proposals for the same gap have
     * v6_interface_impact scores that differ by more than 0.3 (conflicting design directions).
     */
    static double computeConsistency(List<AgentDecisionEvent> accepted) {
        if (accepted.size() <= 1) {
            return 1.0;
        }

        int contradictions = 0;
        // Group by gap, detect conflicting compat directions within the same gap
        for (V7Gap gap : V7Gap.values()) {
            List<AgentDecisionEvent> forGap = accepted.stream()
                .filter(event -> extractGapFromEvent(event) == gap)
                .toList();
            if (forGap.size() < 2) continue;
            double minCompat = forGap.stream()
                .mapToDouble(V7FitnessEvaluator::extractCompatibilityScore)
                .min().orElse(0);
            double maxCompat = forGap.stream()
                .mapToDouble(V7FitnessEvaluator::extractCompatibilityScore)
                .max().orElse(0);
            if (maxCompat - minCompat > 0.3) {
                contradictions++;
            }
        }

        int maxPossibleContradictions = TOTAL_GAPS;
        return 1.0 - ((double) contradictions / maxPossibleContradictions);
    }

    /**
     * Compatibility: mean v6_interface_impact score of accepted proposals.
     * Returns 0.0 if no proposals accepted.
     * Extracted from event.metadata("v6_interface_impact").
     */
    static double computeCompatibility(List<AgentDecisionEvent> accepted) {
        if (accepted.isEmpty()) {
            return 0.0;
        }
        return accepted.stream()
            .mapToDouble(V7FitnessEvaluator::extractCompatibilityScore)
            .average()
            .orElse(0.0);
    }

    /**
     * Performance: mean estimated_gain of accepted proposals.
     * Returns 0.0 if no proposals accepted.
     * Extracted from event.metadata("estimated_gain").
     */
    static double computePerformance(List<AgentDecisionEvent> accepted) {
        if (accepted.isEmpty()) {
            return 0.0;
        }
        return accepted.stream()
            .mapToDouble(V7FitnessEvaluator::extractPerformanceGain)
            .average()
            .orElse(0.0);
    }

    /**
     * Extract V7Gap from AgentDecisionEvent metadata.
     * Gap is stored under "gap" key as String name.
     */
    static V7Gap extractGapFromEvent(AgentDecisionEvent event) {
        Object gapObj = event.getMetadata().get("gap");
        if (gapObj instanceof String gapStr) {
            try {
                return V7Gap.valueOf(gapStr);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Extract backward compatibility score from AgentDecisionEvent metadata.
     * Score is stored under "v6_interface_impact" key as Double (0.0–1.0).
     * Default to 0.5 if not present (neutral).
     */
    static double extractCompatibilityScore(AgentDecisionEvent event) {
        Object compatObj = event.getMetadata().get("v6_interface_impact");
        if (compatObj instanceof Number num) {
            double score = num.doubleValue();
            return Math.max(0.0, Math.min(1.0, score)); // Clamp to [0.0, 1.0]
        }
        return 0.5; // Default neutral score if not specified
    }

    /**
     * Extract performance gain from AgentDecisionEvent metadata.
     * Gain is stored under "estimated_gain" key as Double (0.0–1.0).
     * Default to 0.0 if not present (no gain).
     */
    static double extractPerformanceGain(AgentDecisionEvent event) {
        Object gainObj = event.getMetadata().get("estimated_gain");
        if (gainObj instanceof Number num) {
            double gain = num.doubleValue();
            return Math.max(0.0, Math.min(1.0, gain)); // Clamp to [0.0, 1.0]
        }
        return 0.0; // Default no gain if not specified
    }
}
