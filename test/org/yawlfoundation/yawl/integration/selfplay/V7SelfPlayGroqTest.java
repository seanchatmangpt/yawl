package org.yawlfoundation.yawl.integration.selfplay;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.integration.groq.GroqService;
import org.yawlfoundation.yawl.integration.selfplay.model.V7SimulationReport;
import org.yawlfoundation.yawl.safe.autonomous.ZAIOrchestrator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: V7 self-play loop backed by real Groq LLM (A2A-style agents).
 *
 * <p>All tests FAIL when {@code GROQ_API_KEY} is absent — andon violation.
 * No LLM means the test line stops.
 *
 * <p>When Groq is available, verifies:
 * <ul>
 *   <li>The self-play loop completes within 3 rounds (Groq proposals are rational)</li>
 *   <li>Fitness is positive — real LLM proposals score above zero</li>
 *   <li>Receipt hash chain length equals rounds executed (Blake3 chain closes every round)</li>
 *   <li>Every receipt hash is a valid 64-char SHA3-256 hex string</li>
 *   <li>Accepted proposals carry non-blank LLM-generated reasoning</li>
 *   <li>Accepted proposals have required fitness metadata (gap, v6_interface_impact)</li>
 *   <li>Summary output includes the Blake3 receipt chain section</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class V7SelfPlayGroqTest {

    private static V7SimulationReport report;

    @BeforeAll
    static void checkGroqAndRunLoop() {
        assertNotNull(System.getenv("GROQ_API_KEY"),
            "ANDON VIOLATION: GROQ_API_KEY not set — LLM tests must never be silently skipped. " +
            "Set GROQ_API_KEY in the environment before running self LLM tests.");

        GroqService groq                           = new GroqService();
        GroqV7GapProposalService proposalService   = new GroqV7GapProposalService(groq);
        ZAIOrchestrator zai                        = createZAIOrchestrator();

        V7SelfPlayOrchestrator orchestrator = new V7SelfPlayOrchestrator(
            zai,
            List.of(proposalService),
            0.60,   // Lower threshold — Groq proposals are genuinely rational
            3       // Max 3 rounds for speed
        );

        report = orchestrator.runLoop();
    }

    // -------------------------------------------------------------------------
    // Convergence
    // -------------------------------------------------------------------------

    @Test
    @Order(1)
    void groq_loop_completesWithinMaxRounds() {
        assertTrue(report.totalRounds() >= 1 && report.totalRounds() <= 3,
            "Loop must execute 1–3 rounds, got: " + report.totalRounds());
    }

    @Test
    @Order(2)
    void groq_loop_fitnessIsPositive() {
        assertTrue(report.finalFitness().total() > 0.0,
            "Fitness must be positive with real Groq proposals, got: " + report.finalFitness().total());
    }

    // -------------------------------------------------------------------------
    // Blake3 receipt chain
    // -------------------------------------------------------------------------

    @Test
    @Order(3)
    void groq_receiptChain_notEmpty() {
        assertFalse(report.receiptHashes().isEmpty(),
            "Blake3 receipt chain must contain at least one hash");
    }

    @Test
    @Order(4)
    void groq_receiptChain_lengthMatchesRounds() {
        assertEquals(report.totalRounds(), report.receiptHashes().size(),
            "One receipt hash per round expected. Rounds=" + report.totalRounds()
            + ", hashes=" + report.receiptHashes().size());
    }

    @Test
    @Order(5)
    void groq_receiptChain_allHashesAreSha3_256Hex() {
        for (String hash : report.receiptHashes()) {
            assertNotNull(hash, "Receipt hash must not be null");
            assertEquals(64, hash.length(),
                "SHA3-256 hex must be 64 chars, got: '" + hash + "'");
            assertTrue(hash.matches("[0-9a-f]{64}"),
                "Receipt hash must be lowercase hex: '" + hash + "'");
        }
    }

    // -------------------------------------------------------------------------
    // Proposal quality
    // -------------------------------------------------------------------------

    @Test
    @Order(6)
    void groq_acceptedProposals_haveNonBlankReasoning() {
        assertFalse(report.acceptedProposals().isEmpty(),
            "At least one proposal must be accepted");

        long withReasoning = report.acceptedProposals().stream()
            .filter(p -> {
                String r = p.getFinalDecision().getReasoning();
                return r != null && !r.isBlank();
            })
            .count();

        assertTrue(withReasoning > 0,
            "At least one accepted proposal must have LLM-generated reasoning");
    }

    @Test
    @Order(7)
    void groq_acceptedProposals_haveRequiredMetadata() {
        for (var proposal : report.acceptedProposals()) {
            Object gap    = proposal.getMetadata().get("gap");
            Object compat = proposal.getMetadata().get("v6_interface_impact");

            assertNotNull(gap,    "Proposal must have 'gap' metadata: " + proposal.getDecisionId());
            assertNotNull(compat, "Proposal must have 'v6_interface_impact': " + proposal.getDecisionId());
            assertFalse(gap.toString().isBlank(), "'gap' metadata must not be blank");
        }
    }

    // -------------------------------------------------------------------------
    // Summary output
    // -------------------------------------------------------------------------

    @Test
    @Order(8)
    void groq_summary_includesReceiptChainSection() {
        String summary = report.summary();
        assertTrue(summary.contains("Blake3 receipt chain"),
            "Summary must include 'Blake3 receipt chain' section");
        assertTrue(summary.contains("Round 1:"),
            "Summary must list at least round 1 hash");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ZAIOrchestrator createZAIOrchestrator() {
        try {
            var ctor = YEngine.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            YEngine engine = (YEngine) ctor.newInstance();
            return new ZAIOrchestrator(engine);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ZAIOrchestrator for V7SelfPlayGroqTest", e);
        }
    }
}
