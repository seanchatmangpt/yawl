package org.yawlfoundation.yawl.integration.processmining;

import org.yawlfoundation.yawl.integration.processmining.bridge.ProcessMiningL3;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Gap Analysis Engine for Self-Play Simulation Loop v3.0.
 *
 * <p>Identifies capability gaps by analyzing simulation traces against a reference
 * process model and ranking discovered gaps using WSJF (Weighted Shortest Job First).
 *
 * <p>This engine enables the A4 (Process Mining and Gap Analysis) agent to:
 * <ul>
 *   <li>Compare observed behavior against expected process model</li>
 *   <li>Identify missing capabilities that block optimal pipeline execution</li>
 *   <li>Rank gaps by WSJF score for prioritization</li>
 *   <li>Generate actionable improvement proposals</li>
 * </ul>
 *
 * <p>WSJF Formula:
 * <pre>
 * WSJF = (BusinessValue + TimeCriticality + RiskReduction) / JobSize
 * </pre>
 *
 * @see ConformanceAnalyzer
 * @see ProcessMiningL3
 */
public final class GapAnalysisEngine {

    private static final GapAnalysisEngine INSTANCE = new GapAnalysisEngine();

    private final ProcessMiningL3 processMining;
    private final Map<String, Double> wsjfCache;

    private GapAnalysisEngine() {
        this.processMining = ProcessMiningL3.getInstance();
        this.wsjfCache = new ConcurrentHashMap<>();
    }

    public static GapAnalysisEngine getInstance() {
        return INSTANCE;
    }

    /**
     * Record representing a discovered capability gap.
     */
    public static final class Gap {
        private final String id;
        private final String type;
        private final String description;
        private final double demandScore;
        private final double complexity;
        private final double wsjfScore;
        private final int rank;

        public Gap(String id, String type, String description,
                   double demandScore, double complexity, double wsjfScore, int rank) {
            this.id = id;
            this.type = type;
            this.description = description;
            this.demandScore = demandScore;
            this.complexity = complexity;
            this.wsjfScore = wsjfScore;
            this.rank = rank;
        }

        public String id() { return id; }
        public String type() { return type; }
        public String description() { return description; }
        public double demandScore() { return demandScore; }
        public double complexity() { return complexity; }
        public double wsjfScore() { return wsjfScore; }
        public int rank() { return rank; }

        @Override
        public String toString() {
            return String.format("Gap[id=%s, type=%s, wsjf=%.2f, rank=%d]",
                id, type, wsjfScore, rank);
        }
    }

    /**
     * Discover capability gaps from simulation traces.
     *
     * <p>Compares the DFG discovered from the simulation traces against
     * a reference model to identify missing capabilities.
     *
     * @param ocelJson the OCEL2 event log as a JSON string
     * @param referenceModelPath path to the reference Petri net in PNML format
     * @return list of discovered gaps, ordered by WSJF score (descending)
     * @throws GapAnalysisException if analysis fails
     */
    public List<Gap> discoverGaps(String ocelJson, String referenceModelPath) throws GapAnalysisException {
        if (ocelJson == null || ocelJson.isBlank()) {
            throw new GapAnalysisException("OCEL JSON cannot be null or empty");
        }
        if (referenceModelPath == null || referenceModelPath.isBlank()) {
            throw new GapAnalysisException("Reference model path cannot be null or empty");
        }

        // Get conformance result from process mining
        ProcessMiningL3.ConformanceResult conformance = processMining.checkConformance(ocelJson, referenceModelPath);

        if (conformance == null) {
            throw new GapAnalysisException("Failed to compute conformance: null result");
        }

        List<Gap> gaps = new ArrayList<>();

        // Identify conformance gaps (fitness < 1.0)
        if (conformance.fitness() < 1.0) {
            double missingRatio = 1.0 - conformance.fitness();
            double wsjf = calculateWSJF(missingRatio, 8.0, 3.0);
            gaps.add(new Gap(
                "GAP_MISSING_CONFORMANCE",
                "ConformanceGap",
                "Simulation traces do not fully conform to reference model. Missing ratio: " + missingRatio,
                missingRatio,
                8.0,  // High complexity for conformance issues
                wsjf,
                0
            ));
        }

        // Identify precision gaps (precision < 1.0)
        if (conformance.precision() < 1.0) {
            double unusedRatio = 1.0 - conformance.precision();
            double wsjf = calculateWSJF(unusedRatio, 6.0, 2.0);
            gaps.add(new Gap(
                "GAP_UNDERUTILIZED_PRECISION",
                "PrecisionGap",
                "Reference model has paths that are never executed in simulation. Unused ratio: " + unusedRatio,
                unusedRatio,
                2.0,  // Lower complexity for precision issues
                wsjf,
                2
            ));
        }

        // Check for low fitness (triggers re-simulation)
        if (conformance.fitness() < 0.60) {
            double wsjf = calculateWSJF(0.95, 9.0, 4.0);
            gaps.add(new Gap(
                "GAP_LOW_FITNESS",
                "FitnessGap",
                "Simulation fitness is critically low: " + conformance.fitness(),
                0.95,  // Very high demand - critical for convergence
                4.0,  // Medium complexity
                wsjf,
                0             ));
        }

        return rankGaps(gaps);
    }

    /**
     * Rank gaps by WSJF score.
     *
     * <p>Orders gaps by WSJF score (descending) and assigns rank values.
     *
     * @param gaps list of gaps to rank
     * @return new list with rank values assigned
     */
    public List<Gap> rankByWSJF(List<Gap> gaps) {
        if (gaps == null || gaps.isEmpty()) {
            return List.of();
        }

        // Sort by WSJF score descending
        List<Gap> sorted = gaps.stream()
            .sorted(Comparator.comparingDouble(Gap::wsjfScore).reversed())
            .collect(Collectors.toList());

        // Assign ranks
        List<Gap> ranked = new ArrayList<>();
        int rank = 1;
        for (Gap g : sorted) {
            ranked.add(new Gap(
                g.id(),
                g.type(),
                g.description(),
                g.demandScore(),
                g.complexity(),
                g.wsjfScore(),
                rank++
            ));
            rank++;
        }

        return ranked;
    }

    /**
     * Calculate WSJF score for a gap.
     *
     * <p>WSJF = (BusinessValue + TimeCriticality + RiskReduction) / JobSize
     *
     * @param businessValue value to stakeholders (0-10)
     * @param timeCriticality urgency of resolution (1-10)
     * @param riskReduction how much risk this reduces (1-10)
     * @param jobSize estimated effort to implement (1-10)
     * @return WSJF score
     */
    public double calculateWSJF(double businessValue, double timeCriticality,
                                 double riskReduction, double jobSize) {
                if (jobSize <= 0) {
                    throw new IllegalArgumentException("Job size must be positive");
                }
                double wsjf = (businessValue + timeCriticality + riskReduction) / jobSize;

                // Cache the result
                String cacheKey = String.format("wsjf:%.2f:%.2f:%.2f:%.2f",
                    businessValue, timeCriticality, riskReduction, jobSize);
                wsjfCache.put(cacheKey, wsjf);

                return wsjf;
    }

    /**
     * Get cached WSJF score if available.
     *
     * @param businessValue value to stakeholders
     * @param time다기하는 timeCriticality urgency
     * @param riskReduction risk reduction value
     * @param jobSize implementation effort
     * @return cached score or recalculated if not cached
     */
    public double getCachedWSJF(double businessValue, double timeCriticality,
                                 double riskReduction, double jobSize) {
                String cacheKey = String.format("wsjf:%.2f:%.2f:%.2f:%.2f",
                    businessValue, timeCriticality, riskReduction, jobSize);
                Double cached = wsjfCache.get(cacheKey);
                return cached != null ? cached : calculateWSJF(businessValue, timeCriticality, riskReduction, jobSize);
    }

    /**
     * Generate a summary of all discovered gaps.
     *
     * @param gaps list of gaps to summarize
     * @return formatted summary string
     */
    public String generateSummary(List<Gap> gaps) {
                if (gaps == null || gaps.isEmpty()) {
                    return "No capability gaps discovered - simulation is performing optimally.";
                }

                StringBuilder sb = new StringBuilder();
                sb.append("=== Capability Gap Analysis Report ===\n");
                sb.append(String.format("Total gaps discovered: %d%n", gaps.size()));
                sb.append("Gaps by WSJF priority:\n");

                for (Gap g : gaps) {
                    sb.append(String.format("  %d. %s (WSJF=%.2f): %s\n",
                        g.rank(), g.id(), g.wsjfScore(), g.description()));
                }

                double avgWsjf = gaps.stream()
                    .mapToDouble(Gap::wsjfScore)
                    .average()
                    .orElse(0.0);
                sb.append(String.format("\nAverage WSJF score: %.2f\n", avgWsjf));

                return sb.toString();
    }
}

/**
 * Exception thrown by GapAnalysisEngine operations.
 */
final class GapAnalysisException extends Exception {
    GapAnalysisException(String message) {
        super(message);
    }

    GapAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
