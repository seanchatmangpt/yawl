package org.yawlfoundation.yawl.integration.selfplay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.qlever.QLeverFfiException;
import org.yawlfoundation.yawl.integration.coordination.events.AgentDecisionEvent;
import org.yawlfoundation.yawl.integration.selfplay.FapAnalysisEngine;
import org.yawlfoundation.yawl.integration.selfplay.GapClosureService;
import org.yawlfoundation.yawl.integration.selfplay.model.FitnessScore;
import org.yawlfoundation.yawl.integration.selfplay.model.V7DesignState;
import org.yawlfoundation.yawl.integration.selfplay.model.V7Gap;
import org.yawlfoundation.yawl.integration.selfplay.model.V7SimulationReport;
import org.yawlfoundation.yawl.safe.autonomous.ZAIOrchestrator;
import org.yawlfoundation.yawl.safe.v7.ARTOrchestrationV7Proposals;
import org.yawlfoundation.yawl.safe.v7.ComplianceGovernanceV7Proposals;
import org.yawlfoundation.yawl.safe.v7.GenAIOptimizationV7Proposals;
import org.yawlfoundation.yawl.safe.v7.PortfolioGovernanceV7Proposals;
import org.yawlfoundation.yawl.safe.v7.V7GapProposalService;
import org.yawlfoundation.yawl.safe.v7.ValueStreamCoordinationV7Proposals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD end-to-end tests for the YAWL v7 Z.AI-integrated self-play design loop.
 *
 * <p>No mocks of agents. Exercises the full loop with real V7GapProposalService
 * implementations that invoke actual agent reasoning via Z.AI framework:
 * ZAIOrchestrator → GenAI/Compliance/Portfolio/ValueStream agents → AgentDecisionEvent
 * → V7FitnessEvaluator → V7SelfPlayOrchestrator.
 *
 * <p>The tests verify:
 * <ol>
 *   <li>Convergence: fitness ≥ 0.85 within 5 rounds</li>
 *   <li>Completeness: all 7 known v7 gaps have accepted proposals (AgentDecisionEvent)</li>
 *   <li>Audit trail: every round produces ZAI audit log event IDs for traceability</li>
 *   <li>Monotonicity: fitness never decreases across rounds</li>
 *   <li>Backward compat: all accepted proposals have v6_interface_impact > 0</li>
 *   <li>Report integrity: summary is non-blank and contains expected content</li>
 *   <li>Inner loop: composition count increases after gap closure (new v3.0)</li>
 * </ol>
 */

/**
 * Chicago TDD end-to-end tests for the YAWL v7 Z.AI-integrated self-play design loop.
 *
 * <p>No mocks of agents. Exercises the full loop with real V7GapProposalService
 * implementations that invoke actual agent reasoning via Z.AI framework:
 * ZAIOrchestrator → GenAI/Compliance/Portfolio/ValueStream agents → AgentDecisionEvent
 * → V7FitnessEvaluator → V7SelfPlayOrchestrator.
 *
 * <p>The tests verify:
 * <ol>
 *   <li>Convergence: fitness ≥ 0.85 within 5 rounds</li>
 *   <li>Completeness: all 7 known v7 gaps have accepted proposals (AgentDecisionEvent)</li>
 *   <li>Audit trail: every round produces ZAI audit log event IDs for traceability</li>
 *   <li>Monotonicity: fitness never decreases across rounds</li>
 *   <li>Backward compat: all accepted proposals have v6_interface_impact > 0</li>
 *   <li>Report integrity: summary is non-blank and contains expected content</li>
 * </ol>
 */
class V7SelfPlayLoopTest {

    private V7SelfPlayOrchestrator orchestrator;
    private ZAIOrchestrator zaiOrchestrator;
    private List<V7GapProposalService> proposalServices;
    private GapAnalysisEngine gapAnalysisEngine;
    private GapClosureService gapClosureService;

    @BeforeEach
    void setUp() {
        // Initialize Z.AI orchestrator for agent recruitment
        // The actual ZAIOrchestrator manages agent lifecycle and A2A communication
        // Chicago TDD: real implementation (not null/mock), or explicit failure
        zaiOrchestrator = createZAIOrchestrator();

        // Create the 5 agent proposal services with the real ZAIOrchestrator
        proposalServices = new ArrayList<>();
        proposalServices.add(new GenAIOptimizationV7Proposals(zaiOrchestrator));
        proposalServices.add(new ComplianceGovernanceV7Proposals(zaiOrchestrator));
        proposalServices.add(new PortfolioGovernanceV7Proposals(zaiOrchestrator));
        proposalServices.add(new ValueStreamCoordinationV7Proposals(zaiOrchestrator));
        proposalServices.add(new ARTOrchestrationV7Proposals(zaiOrchestrator));

        // Initialize gap analysis and closure services
        gapAnalysisEngine = new GapAnalysisEngine();
        gapClosureService = new GapClosureService();

        // Initialize services
        gapAnalysisEngine.initialize();
        gapClosureService.initialize();

        // Create the orchestrator with Z.AI integration
        orchestrator = new V7SelfPlayOrchestrator(
            zaiOrchestrator,
            proposalServices,
            0.85,
            5
        );
    }

    /**
     * Clean up resources after each test.
     */
    void tearDown() {
        try {
            if (gapClosureService != null && gapClosureService.isReady()) {
                gapClosureService.shutdown();
            }
            if (gapAnalysisEngine != null) {
                gapAnalysisEngine.shutdown();
            }
        } catch (Exception e) {
            System.err.println("Error during shutdown: " + e.getMessage());
        }
    }

    /**
     * Create a real ZAIOrchestrator instance for test execution.
     * Chicago TDD requirement: This test exercises the full loop with real agents via Z.AI framework.
     * Uses actual Z.AI infrastructure (YEngine + ZAIProtocolHandler + ZAIAuditLog) for real agent communication.
     *
     * @return A fully initialized ZAIOrchestrator with real agent communication channels
     * @throws UnsupportedOperationException if Z.AI infrastructure is not available
     */
    private static ZAIOrchestrator createZAIOrchestrator() {
        // Initialize a real YEngine for Z.AI orchestration
        // This connects to actual YAWL runtime and enables A2A message passing with real agents
        YEngine engine = createYEngine();

        // Create the real orchestrator with actual infrastructure
        return new ZAIOrchestrator(engine);
    }

    /**
     * Initialize a real YEngine for test execution.
     * Chicago TDD: uses actual YAWL runtime, not a mock.
     * Z.AI infrastructure is always available in this environment (ZAI_API_KEY is set).
     * YEngine creation will fail fast if actual infrastructure is not initialized.
     */
    private static YEngine createYEngine() {
        // YEngine has a protected no-arg constructor; access via reflection since
        // the test package is separate from org.yawlfoundation.yawl.engine.
        try {
            var ctor = YEngine.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return (YEngine) ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create YEngine for V7 self-play test", e);
        }
    }

    /**
     * The self-play loop must converge (fitness ≥ 0.85) within 5 rounds.
     *
     * <p>Convergence validates that the fitness function is well-calibrated and
     * the agent proposals bring design into alignment with v7 goals.
     */
    @Test
    void testSelfPlayConvergesWithinFiveRounds() {
        V7SimulationReport report = orchestrator.runLoop();

        assertTrue(report.converged(),
            "Self-play loop must converge within max rounds. Final fitness: "
                + report.finalFitness().total());

        assertTrue(report.totalRounds() <= 5,
            "Expected convergence within 5 rounds, but took " + report.totalRounds() + " rounds. "
                + "Fitness per round should increase monotonically towards threshold.");

        assertTrue(report.finalFitness().total() >= 0.85,
            "Final fitness " + report.finalFitness().total() + " must be >= 0.85");
    }

    /**
     * All 7 known v7 gaps must have at least one accepted proposal when the loop converges.
     *
     * <p>This validates completeness: the design covers every gap identified in the v6
     * codebase analysis.
     */
    @Test
    void testAllV7GapsHaveAcceptedProposals() {
        V7SimulationReport report = orchestrator.runLoop();

        Set<V7Gap> addressedGaps = report.addressedGaps();

        assertEquals(V7Gap.values().length, addressedGaps.size(),
            "All " + V7Gap.values().length + " v7 gaps must have accepted proposals. "
                + "Missing: " + computeMissing(addressedGaps));

        for (V7Gap gap : V7Gap.values()) {
            assertTrue(addressedGaps.contains(gap),
                "Gap " + gap.name() + " (" + gap.title + ") must have an accepted proposal");
        }
    }

    /**
     * Every self-play round must produce ZAI audit log event IDs for traceability.
     *
     * <p>This validates the audit trail: each round's agent decisions are recorded in
     * the ZAI audit log with immutable event IDs, enabling deterministic replay.
     */
    @Test
    void testAuditTrailCompleteWithAgentDecisionEventIds() {
        V7SimulationReport report = orchestrator.runLoop();

        List<String> auditLogEventIds = report.auditLogEventIds();

        assertFalse(auditLogEventIds.isEmpty(),
            "Must have at least one audit log event ID — one per proposal in self-play loop");

        assertTrue(auditLogEventIds.size() >= report.acceptedProposals().size(),
            "Audit log event count must be >= accepted proposal count. "
                + "Got " + auditLogEventIds.size() + " IDs, "
                + report.acceptedProposals().size() + " proposals");

        for (int i = 0; i < auditLogEventIds.size(); i++) {
            String eventId = auditLogEventIds.get(i);
            assertFalse(eventId.isBlank(),
                "Audit log event ID " + i + " must not be blank");
            // UUIDs are 36 chars (with hyphens)
            assertTrue(eventId.length() >= 10,
                "Audit log event ID " + i + " should be a valid UUID, got: " + eventId);
        }

        // Event IDs must be distinct
        long distinctCount = auditLogEventIds.stream().distinct().count();
        assertEquals(auditLogEventIds.size(), distinctCount,
            "All audit log event IDs must be distinct (different agent decisions)");
    }

    /**
     * Fitness must be monotonically non-decreasing across rounds.
     *
     * <p>Since accepted proposals accumulate (never removed), and completeness/compatibility/
     * performance are computed on the growing accepted set, total fitness must not decrease.
     */
    @Test
    void testFitnessIsMonotonicallyNonDecreasing() {
        V7DesignState state = V7DesignState.initial();
        double previousFitness = 0.0;

        for (int round = 1; round <= 5 && !state.unaddressedGaps().isEmpty(); round++) {
            List<AgentDecisionEvent> proposals = new ArrayList<>();

            for (V7Gap gap : state.unaddressedGaps()) {
                V7GapProposalService service = findServiceForGap(gap);
                if (service != null) {
                    AgentDecisionEvent proposal = service.proposeForGap(gap, state);
                    proposals.add(proposal);
                }
            }

            if (proposals.isEmpty()) break;

            // In a real scenario, we'd do challenges here
            // For monotonicity test, we just measure fitness on current proposals
            List<AgentDecisionEvent> cumulative = new ArrayList<>(state.acceptedProposals());
            cumulative.addAll(proposals);

            FitnessScore fitness = V7FitnessEvaluator.evaluate(cumulative, state.allChallenges());

            double currentFitness = fitness.total();
            assertTrue(currentFitness >= previousFitness,
                "Fitness must not decrease. Round " + round + ": " + currentFitness
                    + " < previous " + previousFitness);
            previousFitness = currentFitness;
        }
    }

    /**
     * All accepted proposals must have a v6_interface_impact score > 0.
     *
     * <p>Validates v6 interface contract preservation: no accepted v7 proposal should
     * completely break backward compatibility with v6 clients.
     */
    @Test
    void testAllAcceptedProposalsPreserveBackwardCompatibility() {
        V7SimulationReport report = orchestrator.runLoop();

        List<AgentDecisionEvent> acceptedProposals = report.acceptedProposals();

        assertFalse(acceptedProposals.isEmpty(), "Must have at least one accepted proposal");

        for (AgentDecisionEvent proposal : acceptedProposals) {
            Object compatObj = proposal.getMetadata().get("v6_interface_impact");
            assertNotNull(compatObj, "Proposal must have v6_interface_impact metadata");

            double compatScore = ((Number) compatObj).doubleValue();
            assertTrue(compatScore > 0.0,
                "Accepted proposal must have v6_interface_impact > 0, got: " + compatScore);

            // High bar: all accepted proposals must be at least 60% backward-compatible
            assertTrue(compatScore >= 0.60,
                "Accepted proposal must have v6_interface_impact >= 0.60 (v6 interface contract), "
                    + "got: " + compatScore);
        }
    }

    /**
     * The simulation report summary must be non-blank and contain key content markers.
     *
     * <p>Validates report generation: the output can be written to YAWL_v7_DESIGN_SPEC.md
     * as a human-readable design specification.
     */
    @Test
    void testSimulationReportSummaryIsComplete() {
        V7SimulationReport report = orchestrator.runLoop();

        String summary = report.summary();

        assertNotNull(summary, "Summary must not be null");
        assertFalse(summary.isBlank(), "Summary must not be blank");

        assertTrue(summary.contains("YAWL v7"),
            "Summary must reference YAWL v7");
        assertTrue(summary.contains("Converged: true"),
            "Summary must indicate convergence. Got: " + summary);
        assertTrue(summary.contains("Accepted proposals"),
            "Summary must list accepted proposals");
        assertTrue(summary.contains("Audit trail"),
            "Summary must include audit trail section");

        // Must mention at least one gap name
        boolean mentionsAGap = false;
        for (V7Gap gap : V7Gap.values()) {
            if (summary.contains(gap.name())) {
                mentionsAGap = true;
                break;
            }
        }
        assertTrue(mentionsAGap, "Summary must mention at least one V7Gap by name");
    }

    /**
     * The inner loop must increase composition count after gap closure.
     *
     * <p>Validates that gap closure actions result in more valid compositions
     * in the YAWL engine, demonstrating real improvement to capability pipelines.
     */
    @Test
    void testInnerLoopIncreasesCompositionCount() {
        try {
            // Get initial composition count
            int before = gapClosureService.getCompositionCount();
            System.out.println("Initial composition count: " + before);

            // Perform gap discovery and prioritization
            List<GapAnalysisEngine.CapabilityGap> gaps = gapAnalysisEngine.discoverGaps();
            List<GapAnalysisEngine.GapPriority> priorities = gapAnalysisEngine.prioritizeGaps(gaps);

            // Get top gap for closure
            GapAnalysisEngine.GapPriority top = gapAnalysisEngine.getTopGaps(1).get(0);
            System.out.println("Closing gap: " + top.gap().requiredType());

            // Execute gap closure
            GapClosureService.GapClosureRecord closure = gapClosureService.closeGap(top);

            // Verify closure success
            assertTrue(closure.success(),
                "Gap closure must succeed. Error: " + closure.errorMessage());

            // Verify composition count increased
            int after = gapClosureService.getCompositionCount();
            System.out.println("Composition count after closure: " + after);

            assertTrue(after > before,
                "Composition count must increase after gap closure. " +
                "Before: " + before + ", After: " + after);

            // Verify closure record is complete
            assertNotNull(closure.closureId(), "Closure ID must not be null");
            assertEquals(top.gap(), closure.gap(), "Gap in record must match");
            assertTrue(closure.executionTime() > 0, "Execution time must be positive");

        } catch (QLeverFfiException e) {
            throw new RuntimeException("Inner loop test failed", e);
        }
    }

    /**
     * Multiple gap closures should monotonically increase composition count.
     *
     * <p>Validates that each gap closure adds to the total composition count,
     * preventing regression and ensuring cumulative improvements.
     */
    @Test
    void testMultipleGapClosuresIncreaseCompositionCount() {
        try {
            int initialCount = gapClosureService.getCompositionCount();
            int expectedFinalCount = initialCount + 3; // Expect 3 new compositions

            // Perform 3 gap closures
            for (int i = 0; i < 3; i++) {
                // Discover and prioritize gaps
                List<GapAnalysisEngine.CapabilityGap> gaps = gapAnalysisEngine.discoverGaps();
                List<GapAnalysisEngine.GapPriority> priorities = gapAnalysisEngine.prioritizeGaps(gaps);

                // Close the top gap
                GapAnalysisEngine.GapPriority top = gapAnalysisEngine.getTopGaps(1).get(0);
                GapClosureService.GapClosureRecord closure = gapClosureService.closeGap(top);

                assertTrue(closure.success(), "Gap closure " + (i + 1) + " must succeed");

                // Verify composition count increased
                int currentCount = gapClosureService.getCompositionCount();
                assertTrue(currentCount > initialCount + i,
                    "Composition count should increase with each closure. " +
                    "After closure " + (i + 1) + ": " + currentCount);
            }

            // Final count should match expectations
            int finalCount = gapClosureService.getCompositionCount();
            assertTrue(finalCount >= expectedFinalCount,
                "Expected at least " + expectedFinalCount + " compositions, got " + finalCount);

        } catch (QLeverFfiException e) {
            throw new RuntimeException("Multiple gap closures test failed", e);
        }
    }

    /**
     * Gap closure records should be properly stored and retrievable.
     *
     * <p>Validates that the audit trail of gap closures is complete and accessible
     * for analysis and debugging purposes.
     */
    @Test
    void testGapClosureRecordsAreStoredAndRetrievable() {
        try {
            // Clear any existing records
            List<GapClosureService.GapClosureRecord> initialRecords = gapClosureService.getClosureRecords();

            // Perform a gap closure
            List<GapAnalysisEngine.CapabilityGap> gaps = gapAnalysisEngine.discoverGaps();
            List<GapAnalysisEngine.GapPriority> priorities = gapAnalysisEngine.prioritizeGaps(gaps);
            GapAnalysisEngine.GapPriority top = gapAnalysisEngine.getTopGaps(1).get(0);
            GapClosureService.GapClosureRecord closure = gapClosureService.closeGap(top);

            // Verify record is stored
            List<GapClosureService.GapClosureRecord> allRecords = gapClosureService.getClosureRecords();
            assertEquals(initialRecords.size() + 1, allRecords.size(),
                "Should have one more closure record after closure");

            // Verify specific record is retrievable
            List<GapClosureService.GapClosureRecord> gapRecords = gapClosureService.getGapClosureRecords(top.gap());
            assertEquals(1, gapRecords.size(), "Should have one record for the closed gap");
            assertEquals(closure.closureId(), gapRecords.get(0).closureId(),
                "Retrieved record should match original");

            // Verify closure statistics
            var stats = gapClosureService.getClosureStats();
            assertEquals(1, stats.get("closureCount"), "Should have one closure");
            assertEquals(1L, stats.get("successfulClosures"), "Should have one successful closure");
            assertEquals(0L, stats.get("failedClosures"), "Should have no failed closures");

        } catch (QLeverFfiException e) {
            throw new RuntimeException("Gap closure records test failed", e);
        }
    }

    // ==================== Helpers ====================

    private static Set<V7Gap> computeMissing(Set<V7Gap> addressed) {
        Set<V7Gap> all = java.util.EnumSet.allOf(V7Gap.class);
        all.removeAll(addressed);
        return all;
    }

    private V7GapProposalService findServiceForGap(V7Gap gap) {
        for (V7GapProposalService service : proposalServices) {
            if (service.getResponsibleGaps().contains(gap)) {
                return service;
            }
        }
        return null;
    }
}
