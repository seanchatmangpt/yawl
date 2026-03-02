package org.yawlfoundation.yawl.integration.selfplay;

import org.yawlfoundation.yawl.integration.selfplay.model.V7SimulationReport;
import org.yawlfoundation.yawl.safe.autonomous.ZAIOrchestrator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Integration example demonstrating how to use ConvergenceTracker with YAWL self-play.
 *
 * <p>This class shows how to:
 * <ul>
 *   <li>Set up convergence tracking with self-play</li>
 *   <li>Run multiple self-play sessions with monitoring</li>
 *   <li>Analyze convergence patterns and detect issues</li>
 *   <li>Generate actionable insights and recommendations</li>
 *   <li>Handle alerts and adjust strategies accordingly</li>
 * </ul>
 */
public class SelfPlayWithConvergenceTracking {

    private final ZAIOrchestrator zaiOrchestrator;
    private final ConvergenceTracker convergenceTracker;
    private final List<SelfPlaySession> sessionHistory;
    private final int maxSessions;

    /**
     * Creates a new self-play convergence tracking instance.
     *
     * @param zaiOrchestrator the Z.AI orchestrator for agent recruitment
     * @param maxSessions maximum number of self-play sessions to run
     */
    public SelfPlayWithConvergenceTracking(ZAIOrchestrator zaiOrchestrator, int maxSessions) {
        this.zaiOrchestrator = zaiOrchestrator;
        this.maxSessions = maxSessions;
        this.sessionHistory = new ArrayList<>();

        // Configure convergence tracker for YAWL self-play
        this.convergenceTracker = new ConvergenceTracker.Builder()
            .maxHistorySize(maxSessions)
            .plateauThreshold(0.01)  // 1% fitness change threshold
            .divergenceThreshold(0.1) // 10% performance degradation threshold
            .plateauMinSamples(3)     // Detect plateau after 3 similar sessions
            .trendWindow(5)          // Analyze trend over last 5 sessions
            .build();
    }

    /**
     * Runs multiple self-play sessions with convergence tracking.
     *
     * @return the final convergence analysis
     */
    public ConvergenceAnalysis runTrackingSessions() {
        System.out.println("=== Starting YAWL Self-Play with Convergence Tracking ===\n");

        for (int session = 1; session <= maxSessions; session++) {
            System.out.println("Running session " + session + " of " + maxSessions + "...");

            try {
                // Run a single self-play session
                V7SelfPlayOrchestrator orchestrator = createOrchestratorForSession(session);
                V7SimulationReport report = orchestrator.runLoop();

                // Record the session for convergence analysis
                ConvergenceAnalysis analysis = convergenceTracker.recordSimulation(report);

                // Store session information
                SelfPlaySession sessionRecord = new SelfPlaySession(
                    session,
                    report,
                    analysis,
                    Instant.now()
                );
                sessionHistory.add(sessionRecord);

                // Display session results
                displaySessionResults(session, report, analysis);

                // Check for alerts and take action if needed
                handleSessionAlerts(analysis, session);

                // Check if we should early terminate due to convergence
                if (shouldEarlyTerminate(analysis, session)) {
                    System.out.println("\nEarly termination triggered by convergence criteria.");
                    break;
                }

                // Small delay between sessions (optional)
                // TimeUnit.SECONDS.sleep(1);

            } catch (Exception e) {
                System.err.println("Error in session " + session + ": " + e.getMessage());
                // Continue with next session
            }
        }

        // Perform final analysis
        ConvergenceAnalysis finalAnalysis = convergenceTracker.analyzeConvergence();
        displayFinalAnalysis(finalAnalysis);

        return finalAnalysis;
    }

    /**
     * Creates a self-play orchestrator configured for a specific session.
     */
    private V7SelfPlayOrchestrator createOrchestratorForSession(int session) {
        // Get proposal services (simplified for example)
        List<GroqV7GapProposalService> proposalServices = getProposalServicesForSession(session);

        // Adjust parameters based on session number
        double fitnessThreshold = calculateFitnessThreshold(session);
        int maxRounds = calculateMaxRounds(session);

        return new V7SelfPlayOrchestrator(
            zaiOrchestrator,
            proposalServices,
            fitnessThreshold,
            maxRounds
        );
    }

    /**
     * Gets proposal services for a session (simplified implementation).
     */
    private List<GroqV7GapProposalService> getProposalServicesForSession(int session) {
        // In a real implementation, this would recruit different agents
        // based on session number and performance history

        // For now, return a mock implementation
        return List.of(new OfflineV7GapProposalService());
    }

    /**
     * Calculates fitness threshold based on session number.
     */
    private double calculateFitnessThreshold(int session) {
        // Start with lower threshold, gradually increase
        return 0.75 + (session * 0.02); // From 0.75 to 0.85+ over sessions
    }

    /**
     * Calculates maximum rounds based on session number.
     */
    private int calculateMaxRounds(int session) {
        // Increase max rounds as sessions progress
        return Math.min(5, 2 + (session / 3)); // From 2 to 5 rounds
    }

    /**
     * Displays results for a single session.
     */
    private void displaySessionResults(int session, V7SimulationReport report, ConvergenceAnalysis analysis) {
        System.out.printf("\nSession %d Results:%n", session);
        System.out.printf("  - Rounds: %d%n", report.totalRounds());
        System.out.printf("  - Final Fitness: %.4f%n", report.finalFitness().total());
        System.out.printf("  - Converged: %s%n", report.converged());
        System.out.printf("  - Execution Time: %d ms%n", report.durationMs());
        System.out.printf("  - Status: %s%n", analysis.getStatus());

        if (!analysis.getAlerts().isEmpty()) {
            System.out.printf("  - Alerts: %d%n", analysis.getAlerts().size());
            analysis.getAlerts().forEach(alert ->
                System.out.printf("    * [%s] %s: %s%n",
                    alert.getSeverity(), alert.getType(), alert.getMessage())
            );
        }

        if (analysis.getTrend() != null) {
            System.out.printf("  - Trend: %s (slope: %.6f)%n",
                analysis.getTrend().getDirection(), analysis.getTrend().getSlope());
        }

        System.out.println();
    }

    /**
     * Handles alerts detected in a session.
     */
    private void handleSessionAlerts(ConvergenceAnalysis analysis, int session) {
        if (analysis.hasProblems()) {
            System.out.println("Session " + session + " detected issues:");

            for (ConvergenceAlert alert : analysis.getAlerts()) {
                switch (alert.getType()) {
                    case DIVERGENCE:
                        System.out.println("  → Divergence detected: Adjusting fitness evaluation");
                        // Implement divergence handling strategy
                        handleDivergence(session);
                        break;
                    case PLATEAU:
                        System.out.println("  → Plateau detected: Introducing new strategies");
                        // Implement plateau breaking strategy
                        handlePlateau(session);
                        break;
                    case SLOW_PROGRESS:
                        System.out.println("  → Slow progress: Optimizing proposal generation");
                        // Implement optimization strategy
                        handleSlowProgress(session);
                        break;
                    case OSCILLATION:
                        System.out.println("  → Oscillation detected: Stabilizing evaluation");
                        // Implement stabilization strategy
                        handleOscillation(session);
                        break;
                }
            }
        }
    }

    /**
     * Handles divergence detected in sessions.
     */
    private void handleDivergence(int session) {
        // Strategy: Reduce fitness threshold temporarily, add validation
        System.out.println("  Strategy: Lowering fitness threshold and adding validation checks");
        // Implementation would involve:
        // 1. Temporarily reduce threshold
        // 2. Add additional validation checks
        // 3. Monitor for recovery
    }

    /**
     * Handles plateau detected in sessions.
     */
    private void handlePlateau(int session) {
        // Strategy: Introduce new proposal services or increase diversity
        System.out.println("  Strategy: Introducing new agent types and increasing diversity");
        // Implementation would involve:
        // 1. Recruit new agent types
        // 2. Increase randomness in proposal generation
        // 3. Try different fitness evaluation weights
    }

    /**
     * Handles slow progress detected in sessions.
     */
    private void handleSlowProgress(int session) {
        // Strategy: Optimize proposal generation and increase parallelism
        System.out.println("  Strategy: Optimizing proposal generation and parallel execution");
        // Implementation would involve:
        // 1. Cache frequently used proposals
        // 2. Increase parallel execution of proposals
        // 3. Optimize fitness evaluation
    }

    /**
     * Handles oscillation detected in sessions.
     */
    private void handleOscillation(int session) {
        // Strategy: Stabilize with moving averages and smoothing
        System.out.println("  Strategy: Implementing smoothing and moving averages");
        // Implementation would involve:
        // 1. Use moving average for fitness scores
        // 2. Add smoothing to evaluation process
        // 3. Reduce threshold sensitivity
    }

    /**
     * Determines if early termination should be triggered.
     */
    private boolean shouldEarlyTerminate(ConvergenceAnalysis analysis, int session) {
        // Early termination conditions:
        return analysis.isConverged() && session >= 3; // Need at least 3 sessions
    }

    /**
     * Displays final convergence analysis.
     */
    private void displayFinalAnalysis(ConvergenceAnalysis analysis) {
        System.out.println("=== Final Convergence Analysis ===");
        System.out.println(analysis.summary());

        System.out.println("\nSession History Summary:");
        System.out.println("Total Sessions: " + sessionHistory.size());
        System.out.println("Converged Sessions: " +
            sessionHistory.stream().mapToInt(s -> s.report().converged() ? 1 : 0).sum());
        System.out.println("Average Fitness: " +
            sessionHistory.stream().mapToDouble(s -> s.report().finalFitness().total()).average().orElse(0.0));

        // Display progression
        System.out.println("\nFitness Progression:");
        sessionHistory.forEach(session -> {
            double fitness = session.report().finalFitness().total();
            System.out.printf("  Session %2d: %.4f %s%n",
                session.sessionNumber(), fitness,
                session.report().converged() ? "✓" : "");
        });
    }

    /**
     * Records and saves convergence tracking data.
     */
    public void saveTrackingData(String filePath) {
        // Implementation would save tracking data to file
        // Could include: session history, analysis results, alerts
        System.out.println("Saving tracking data to: " + filePath);
    }

    /**
     * Gets the session history.
     */
    public List<SelfPlaySession> getSessionHistory() {
        return new ArrayList<>(sessionHistory);
    }

    /**
     * Gets the convergence tracker.
     */
    public ConvergenceTracker getConvergenceTracker() {
        return convergenceTracker;
    }

    /**
     * Record for storing self-play session information.
     */
    public record SelfPlaySession(
        int sessionNumber,
        V7SimulationReport report,
        ConvergenceAnalysis analysis,
        Instant timestamp
    ) {}

    /**
     * Example usage.
     */
    public static void main(String[] args) {
        try {
            // Create ZAI orchestrator
            ZAIOrchestrator zaiOrchestrator = createZAIOrchestrator();

            // Set up convergence tracking
            SelfPlayWithConvergenceTracking tracking =
                new SelfPlayWithConvergenceTracking(zaiOrchestrator, 10);

            // Run tracking sessions
            ConvergenceAnalysis finalAnalysis = tracking.runTrackingSessions();

            // Save tracking data
            tracking.saveTrackingData("/Users/sac/yawl/convergence-tracking-data.json");

            // Display final recommendations
            System.out.println("\n=== Final Recommendations ===");
            System.out.println(finalAnalysis.getRecommendations());

        } catch (Exception e) {
            System.err.println("Error in self-play with convergence tracking: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates a ZAI orchestrator (simplified implementation).
     */
    private static ZAIOrchestrator createZAIOrchestrator() {
        try {
            var ctor = Class.forName("org.yawlfoundation.yawl.safe.autonomous.ZAIOrchestrator")
                .getDeclaredConstructor();
            ctor.setAccessible(true);
            return (ZAIOrchestrator) ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ZAIOrchestrator", e);
        }
    }
}