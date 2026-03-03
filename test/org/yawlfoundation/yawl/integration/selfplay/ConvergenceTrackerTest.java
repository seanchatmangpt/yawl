package org.yawlfoundation.yawl.integration.selfplay;

import org.yawlfoundation.yawl.integration.selfplay.model.FitnessScore;
import org.yawlfoundation.yawl.integration.selfplay.model.V7SimulationReport;
import org.yawlfoundation.yawl.integration.selfplay.model.V7Gap;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.yawlfoundation.yawl.integration.coordination.events.AgentDecisionEvent;

/**
 * Comprehensive test for the ConvergenceTracker system.
 *
 * <p>Demonstrates how to use the convergence tracking system with realistic
 * self-play simulation data and validates the analysis results.
 */
public class ConvergenceTrackerTest {

    public static void main(String[] args) {
        System.out.println("=== Convergence Tracker Test ===\n");

        // Create a convergence tracker with custom configuration
        ConvergenceTracker tracker = new ConvergenceTracker.Builder()
            .maxHistorySize(20)
            .plateauThreshold(0.005)
            .divergenceThreshold(0.15)
            .plateauMinSamples(5)
            .trendWindow(10)
            .build();

        // Simulate multiple self-play runs
        simulateMultipleRuns(tracker);

        // Perform analysis
        ConvergenceAnalysis analysis = tracker.analyzeConvergence();

        // Display results
        displayAnalysisResults(analysis);
        displayTrackingSummary(tracker.getSummary());

        // Demonstrate alert handling
        demonstrateAlertHandling(analysis);

        System.out.println("\n=== Test completed successfully ===");
    }

    /**
     * Simulates multiple self-play runs with different convergence scenarios.
     */
    private static void simulateMultipleRuns(ConvergenceTracker tracker) {
        System.out.println("Simulating self-play runs...\n");

        // Simulate 15 runs showing different convergence patterns
        double[][] fitnessData = {
            // run, rounds, fitness, converged
            {1, 3, 0.45, false},   // Initial low fitness
            {2, 3, 0.52, false},   // Slow improvement
            {3, 3, 0.58, false},   // Continuing improvement
            {4, 3, 0.65, false},   // Getting better
            {5, 3, 0.72, false},   // Approaching threshold
            {6, 3, 0.78, false},   // Close to convergence
            {7, 3, 0.82, false},   // Very close
            {8, 3, 0.85, true},    // CONVERGED!
            {9, 2, 0.87, true},    // Continued high performance
            {10, 2, 0.86, true},   // Slight variation
            {11, 2, 0.84, true},   // Small decline
            {12, 3, 0.85, true},   // Recovery
            {13, 3, 0.85, true},   // Plateau
            {14, 3, 0.85, true},   // Plateau continues
            {15, 3, 0.85, true}    // Plateau persists
        };

        for (double[] data : fitnessData) {
            int run = (int) data[0];
            int rounds = (int) data[1];
            double fitness = data[2];
            boolean converged = data[3] == 1.0;

            System.out.printf("Simulating run %d: fitness=%.3f, converged=%b%n", run, fitness, converged);

            V7SimulationReport report = createMockReport(run, rounds, fitness, converged);
            ConvergenceAnalysis analysis = tracker.recordSimulation(report);

            // Print key metrics every few runs
            if (run % 5 == 0 || converged) {
                System.out.println("  Analysis: " + analysis.getStatus());
                if (!analysis.getAlerts().isEmpty()) {
                    System.out.println("  Alerts: " + analysis.getAlerts().size());
                }
            }
            System.out.println();
        }
    }

    /**
     * Creates a mock simulation report for testing.
     */
    private static V7SimulationReport createMockReport(
        int runNumber,
        int totalRounds,
        double fitness,
        boolean converged
    ) {
        FitnessScore score = new FitnessScore(fitness * 0.9, 0.95, fitness, fitness * 0.8);
        Instant completedAt = Instant.now();

        // Create mock accepted proposals
        List<AgentDecisionEvent> acceptedProposals = List.of(
            createMockDecisionEvent("gap1", runNumber, fitness),
            createMockDecisionEvent("gap2", runNumber, fitness * 0.9)
        );

        // Create mock challenges
        List<DesignChallenge> challenges = List.of(
            new DesignChallenge("challenge1", "gap1", runNumber, 0.8),
            new DesignChallenge("challenge2", "gap2", runNumber, 0.7)
        );

        return new V7SimulationReport(
            totalRounds,
            converged,
            score,
            acceptedProposals,
            challenges,
            List.of("audit-" + UUID.randomUUID().toString()),
            Set.of(V7Gap.ASYNC_A2A_GOSSIP, V7Gap.MCP_SERVERS_SLACK_GITHUB_OBS),
            1000 + runNumber * 100, // duration
            completedAt,
            List.of("receipt-" + runNumber)
        );
    }

    /**
     * Creates a mock agent decision event.
     */
    private static AgentDecisionEvent createMockDecisionEvent(String gapId, int run, double fitness) {
        return new AgentDecisionEvent(
            UUID.randomUUID().toString(),
            "test-agent",
            "proposal-" + run,
            null,
            AgentDecisionEvent.DecisionType.RESOURCE_ALLOCATION,
            java.util.Map.of("gap", gapId, "v6_interface_impact", fitness),
            Instant.now(),
            null,
            new AgentDecisionEvent.DecisionOption[0],
            new AgentDecisionEvent.DecisionFactor[0],
            new AgentDecisionEvent.Decision("ACCEPTED", "test-agent", fitness, "Mock decision"),
            new AgentDecisionEvent.ExecutionPlan(
                new String[]{"analyze"},
                new String[]{"implement"},
                java.util.Map.of("gain", fitness)
            ),
            java.util.Map.of("round", run)
        );
    }

    /**
     * Displays detailed analysis results.
     */
    private static void displayAnalysisResults(ConvergenceAnalysis analysis) {
        System.out.println("=== Convergence Analysis Results ===\n");
        System.out.println(analysis.summary());

        System.out.println("\nDetailed Analysis:");
        System.out.println("Status: " + analysis.getStatus());
        System.out.println("Current Fitness: " + String.format("%.4f", analysis.getCurrentFitness()));
        System.out.println("Total Simulations: " + analysis.getTotalSimulations());
        System.out.println("Improvement Rate: " + String.format(".4f", analysis.getAverageImprovementRate()));

        if (analysis.getTrend() != null) {
            System.out.println("\nTrend Analysis:");
            System.out.println("  Direction: " + analysis.getTrend().getDirection());
            System.out.println("  Slope: " + String.format("%.6f", analysis.getTrend().getSlope()));
            System.out.println("  Correlation: " + String.format("%.3f", analysis.getTrend().getCorrelation()));
            System.out.println("  Interpretation: " + analysis.getTrend().getInterpretation());
        }

        if (analysis.getPlateau() != null) {
            System.out.println("\nPlateau Detection:");
            System.out.println("  Detected: " + analysis.getPlateau().isDetected());
            System.out.println("  Range: " + String.format("%.6f", analysis.getPlateau().getRange()));
            System.out.println("  Sample Size: " + analysis.getPlateau().getSampleSize());
            System.out.println("  Interpretation: " + analysis.getPlateau().getInterpretation());
        }

        if (!analysis.getAlerts().isEmpty()) {
            System.out.println("\nAlerts (" + analysis.getAlerts().size() + "):");
            for (ConvergenceAlert alert : analysis.getAlerts()) {
                System.out.println("  [" + alert.getSeverity() + "] " + alert.getType() + ": " + alert.getMessage());
            }
        }

        System.out.println("\nRecommendations:");
        System.out.println(analysis.getRecommendations());
    }

    /**
     * Displays tracking summary.
     */
    private static void displayTrackingSummary(TrackingSummary summary) {
        System.out.println("\n=== Tracking Summary ===");
        System.out.println(summary.getSummary());
    }

    /**
     * Demonstrates how to handle alerts from convergence analysis.
     */
    private static void demonstrateAlertHandling(ConvergenceAnalysis analysis) {
        System.out.println("\n=== Alert Handling Demonstration ===");

        if (analysis.hasProblems()) {
            System.out.println("Detected " + analysis.getAlerts().size() + " issues requiring attention:");

            for (ConvergenceAlert alert : analysis.getAlerts()) {
                System.out.println("\nAlert: " + alert);

                if (alert.requiresImmediateAttention()) {
                    System.out.println("  → REQUIRES IMMEDIATE ATTENTION");
                }

                if (alert.isConfigurationIssue()) {
                    System.out.println("  → Action: Check configuration and parameters");
                } else if (alert.isPerformanceIssue()) {
                    System.out.println("  → Action: Optimize performance and algorithms");
                }

                // Show recommended actions based on alert type
                switch (alert.getType()) {
                    case DIVERGENCE:
                        System.out.println("  → Recommended: Validate data sources and fitness calculations");
                        break;
                    case PLATEAU:
                        System.out.println("  → Recommended: Introduce diversity or new strategies");
                        break;
                    case SLOW_PROGRESS:
                        System.out.println("  → Recommended: Review fitness evaluation criteria");
                        break;
                    case OSCILLATION:
                        System.out.println("  → Recommended: Stabilize evaluation process");
                        break;
                }
            }
        } else {
            System.out.println("No issues detected. Convergence is progressing normally.");
        }

        // Demonstrate priority handling
        System.out.println("\nPriority Handling:");
        analysis.getAlerts().stream()
            .sorted((a1, a2) -> {
                // Higher severity first, then by type
                int severityCompare = a2.getSeverity().compareTo(a1.getSeverity());
                if (severityCompare != 0) return severityCompare;
                return a1.getType().compareTo(a2.getType());
            })
            .forEach(alert -> {
                String priority = alert.getSeverity() == ConvergenceAlert.AlertSeverity.HIGH ? "HIGH" :
                                 alert.getSeverity() == ConvergenceAlert.AlertSeverity.MEDIUM ? "MEDIUM" : "LOW";
                System.out.printf("[%s Priority] %s%n", priority, alert.getType());
            });
    }
}