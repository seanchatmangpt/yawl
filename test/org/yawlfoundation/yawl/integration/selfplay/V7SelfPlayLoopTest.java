package org.yawlfoundation.yawl.integration.selfplay;

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.integration.selfplay.FapAnalysisEngine;
import org.yawlfoundation.yawl.integration.selfplay.GapClosureService;
import org.yawlfoundation.yawl.integration.selfplay.model.V7SimulationReport;
import org.yawlfoundation.yawl.safe.autonomous.ZAIOrchestrator;
import org.yawlfoundation.yawl.safe.v7.GenAIOptimizationV7Proposals;
import org.yawlfoundation.yawl.safe.v7.ComplianceGovernanceV7Proposals;
import org.yawlfoundation.yawl.safe.v7.PortfolioGovernanceV7Proposals;
import org.yawlfoundation.yawl.safe.v7.ValueStreamCoordinationV7Proposals;
import org.yawlfoundation.yawl.safe.v7.ARTOrchestrationV7Proposals;
import org.yawlfoundation.yawl.safe.v7.V7GapProposalService;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * YAWL Self-Play Loop v3.0 - Three Full Iterations Verification
 * 
 * Implements the core invariant: C1 > C0, C2 > C1, C3 > C2
 * Where C = composition count at each iteration
 */
public class V7SelfPlayLoopTest {

    private V7SelfPlayOrchestrator orchestrator;
    private ZAIOrchestrator zaiOrchestrator;
    private List<V7GapProposalService> proposalServices;
    private GapAnalysisEngine gapAnalysisEngine;
    private GapClosureService gapClosureService;

    /**
     * Initialize the test environment.
     */
    public V7SelfPlayLoopTest() {
        // Initialize Z.AI orchestrator
        zaiOrchestrator = createZAIOrchestrator();

        // Create proposal services
        proposalServices = new ArrayList<>();
        proposalServices.add(new GenAIOptimizationV7Proposals(zaiOrchestrator));
        proposalServices.add(new ComplianceGovernanceV7Proposals(zaiOrchestrator));
        proposalServices.add(new PortfolioGovernanceV7Proposals(zaiOrchestrator));
        proposalServices.add(new ValueStreamCoordinationV7Proposals(zaiOrchestrator));
        proposalServices.add(new ARTOrchestrationV7Proposals(zaiOrchestrator));

        // Initialize services
        gapAnalysisEngine = new GapAnalysisEngine();
        gapClosureService = new GapClosureService();

        // Create orchestrator
        orchestrator = new V7SelfPlayOrchestrator(
            zaiOrchestrator,
            proposalServices,
            0.85,
            5
        );
    }

    private ZAIOrchestrator createZAIOrchestrator() {
        try {
            var ctor = YEngine.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            YEngine engine = (YEngine) ctor.newInstance();
            return new ZAIOrchestrator(engine);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ZAIOrchestrator", e);
        }
    }

    /**
     * Single iteration test: composition count increases after one complete iteration.
     */
    @Test
    void testSingleIteration() {
        try {
            int before = gapClosureService.getCompositionCount();
            System.out.println("Composition count before iteration: " + before);

            // Run one complete iteration
            V7SimulationReport report = orchestrator.runLoop();

            int after = gapClosureService.getCompositionCount();
            System.out.println("Composition count after iteration: " + after);

            assertTrue(after > before,
                "Single iteration must increase composition count. " +
                "Before: " + before + ", After: " + after);

            assertTrue(report.converged(),
                "Self-play loop must converge within the iteration");

        } catch (Exception e) {
            throw new RuntimeException("Single iteration test failed", e);
        }
    }

    /**
     * Three iterations show strictly increasing composition count.
     * 
     * THE ONE INVARIANT: C1 > C0, C2 > C1, C3 > C2
     * This proves the loop is continuously improving with each iteration.
     */
    @Test
    void testThreeIterationsStrictlyIncreasing() {
        try {
            int c0 = gapClosureService.getCompositionCount();
            System.out.println("Initial composition count (C0): " + c0);

            // Run iteration 1
            orchestrator.runLoop();
            int c1 = gapClosureService.getCompositionCount();
            System.out.println("After iteration 1 (C1): " + c1);

            // Run iteration 2
            orchestrator.runLoop();
            int c2 = gapClosureService.getCompositionCount();
            System.out.println("After iteration 2 (C2): " + c2);

            // Run iteration 3
            orchestrator.runLoop();
            int c3 = gapClosureService.getCompositionCount();
            System.out.println("After iteration 3 (C3): " + c3);

            // Verify strictly increasing composition counts
            assertTrue(c1 > c0,
                "C1 > C0 failed: " + c1 + " > " + c0);
            assertTrue(c2 > c1,
                "C2 > C1 failed: " + c2 + " > " + c1);
            assertTrue(c3 > c2,
                "C3 > C2 failed: " + c3 + " > " + c2);

            // Log the improvement trend
            System.out.println("Composition count trend: " + c0 + " → " + c1 + " → " + c2 + " → " + c3);
            System.out.println("Improvements: +" + (c1 - c0) + ", +" + (c2 - c1) + ", +" + (c3 - c2));

            // Verify the final gate criteria
            assertTrue(c3 >= 100,
                "Final composition count must be >= 100, got: " + c3);

        } catch (Exception e) {
            throw new RuntimeException("Three iterations test failed", e);
        }
    }
}
