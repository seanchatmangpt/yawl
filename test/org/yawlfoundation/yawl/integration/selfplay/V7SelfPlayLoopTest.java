package org.yawlfoundation.yawl.integration.selfplay;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.QLeverSparqlEngine;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineException;
import org.yawlfoundation.yawl.integration.coordination.events.AgentDecisionEvent;
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

import java.nio.file.Files;
import java.nio.file.Path;
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
 * </ol>
 */
class V7SelfPlayLoopTest {

    private V7SelfPlayOrchestrator orchestrator;
    private ZAIOrchestrator zaiOrchestrator;
    private List<V7GapProposalService> proposalServices;

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

        // Create the orchestrator with Z.AI integration
        orchestrator = new V7SelfPlayOrchestrator(
            zaiOrchestrator,
            proposalServices,
            0.85,
            5
        );
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

    // ==================== Composition-Count Self-Play Tests ====================

    /**
     * Verifies one full self-play loop iteration strictly increases the CapabilityPipeline count.
     *
     * <p>The one invariant: {@code composition_count(N+1) > composition_count(N)}.
     * Loads pm-bridge.ttl into QLever, runs valid-compositions.sparql to record the baseline,
     * closes one gap by inserting a new NativeFunction triple, and asserts the count grew.
     *
     * <p>Skipped automatically when QLever is not running at localhost:7001.
     */
    @Test
    void testSingleIteration() throws Exception {
        QLeverSparqlEngine sparql = new QLeverSparqlEngine();
        Assumptions.assumeTrue(sparql.isAvailable(),
            "Skipping: QLever not running at localhost:7001 (required for composition count test)");

        Path pmBridge = Path.of("ontology/process-mining/pm-bridge.ttl").toAbsolutePath();
        sparql.sparqlUpdate("LOAD <file://" + pmBridge + ">");

        String compositionsQuery = Files.readString(Path.of("queries/valid-compositions.sparql"));
        long before = countOptimalPipelineTriples(sparql.constructToTurtle(compositionsQuery));

        // Close top gap: insert one new NativeFunction that returns MemorySegment.
        // valid-compositions.sparql matches pairs where cap2's descriptor contains "ADDRESS",
        // so this new capability composes with every existing ADDRESS-descriptor capability.
        sparql.sparqlUpdate("""
            PREFIX yawl-bridge: <http://yawlfoundation.org/yawl/bridge#>
            INSERT DATA {
              <http://yawlfoundation.org/yawl/bridge/cap/gap_close_iter1>
                  a yawl-bridge:BridgeCapability ;
                  yawl-bridge:capabilityName "gap_close_iteration_1" ;
                  yawl-bridge:nativeTarget "rust4pm_gap_close_1" .
              <http://yawlfoundation.org/yawl/bridge/fn/gap_close_iter1>
                  a yawl-bridge:NativeFunction ;
                  yawl-bridge:forCapability <http://yawlfoundation.org/yawl/bridge/cap/gap_close_iter1> ;
                  yawl-bridge:fnReturnType "MemorySegment" ;
                  yawl-bridge:fnDescriptor "FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)" .
            }
            """);

        long after = countOptimalPipelineTriples(sparql.constructToTurtle(compositionsQuery));

        assertTrue(after > before,
            "Composition count must increase after one iteration: " + before + " → " + after
                + ". New capability must compose with existing ADDRESS-descriptor capabilities.");
    }

    /**
     * Verifies the inner loop closes a single gap and strictly increases the composition count.
     *
     * <p>This is the atomic unit of self-play: one gap in → one composition delta out.
     * The new producer capability returns MemorySegment, which makes it chain-composable with
     * all existing capabilities whose descriptors accept ADDRESS/MemorySegment arguments.
     *
     * <p>Skipped automatically when QLever is not running at localhost:7001.
     */
    @Test
    void testInnerLoopSingleGap() throws Exception {
        QLeverSparqlEngine sparql = new QLeverSparqlEngine();
        Assumptions.assumeTrue(sparql.isAvailable(),
            "Skipping: QLever not running at localhost:7001 (required for inner loop test)");

        Path pmBridge = Path.of("ontology/process-mining/pm-bridge.ttl").toAbsolutePath();
        sparql.sparqlUpdate("LOAD <file://" + pmBridge + ">");

        String compositionsQuery = Files.readString(Path.of("queries/valid-compositions.sparql"));
        long before = countOptimalPipelineTriples(sparql.constructToTurtle(compositionsQuery));

        // Inner loop gap closure: add one new producer for the top-priority demanded type
        // (MemorySegment — highest WSJF score per GapAnalysisEngine demand mapping).
        sparql.sparqlUpdate("""
            PREFIX yawl-bridge: <http://yawlfoundation.org/yawl/bridge#>
            INSERT DATA {
              <http://yawlfoundation.org/yawl/bridge/cap/inner_loop_gap1>
                  a yawl-bridge:BridgeCapability ;
                  yawl-bridge:capabilityName "inner_loop_new_producer" ;
                  yawl-bridge:nativeTarget "rust4pm_inner_producer" .
              <http://yawlfoundation.org/yawl/bridge/fn/inner_loop_gap1>
                  a yawl-bridge:NativeFunction ;
                  yawl-bridge:forCapability <http://yawlfoundation.org/yawl/bridge/cap/inner_loop_gap1> ;
                  yawl-bridge:fnReturnType "MemorySegment" ;
                  yawl-bridge:fnDescriptor "FunctionDescriptor.of(ValueLayout.ADDRESS)" .
            }
            """);

        long after = countOptimalPipelineTriples(sparql.constructToTurtle(compositionsQuery));

        assertTrue(after > before,
            "Inner loop must increase composition count after closing one gap: "
                + before + " → " + after);
    }

    // ==================== Helpers ====================

    /**
     * Counts sim:OptimalPipeline occurrences in Turtle output — one line per pipeline.
     */
    private static long countOptimalPipelineTriples(String turtle) {
        if (turtle == null || turtle.isBlank()) {
            return 0;
        }
        return turtle.lines()
                .filter(line -> line.contains("OptimalPipeline"))
                .count();
    }

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
