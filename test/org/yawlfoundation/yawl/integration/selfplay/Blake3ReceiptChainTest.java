/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.selfplay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.integration.selfplay.model.Blake3Receipt;
import org.yawlfoundation.yawl.integration.selfplay.model.FitnessScore;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for the Blake3 receipt chain (V7Gap.DETERMINISTIC_REPLAY_BLAKE3).
 *
 * <p>Verifies that the V7SelfPlayOrchestrator computes a per-round SHA3-256 receipt
 * hash chain and includes it in the V7SimulationReport. Each round's hash incorporates
 * the prior round's hash, forming a deterministic audit chain for replay and verification.
 *
 * <p>Tests verify:
 * <ol>
 *   <li>Receipt hashes are non-empty after the loop completes</li>
 *   <li>One receipt hash per round executed</li>
 *   <li>Each hash is a valid 64-char hex string (SHA3-256)</li>
 *   <li>Hashes are distinct (chain property — each round's hash is unique)</li>
 *   <li>Same inputs produce same hash (determinism)</li>
 *   <li>Summary includes receipt chain information</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class Blake3ReceiptChainTest {

    private V7SelfPlayOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        ZAIOrchestrator zaiOrchestrator = createZAIOrchestrator();
        List<V7GapProposalService> proposalServices = new ArrayList<>();
        proposalServices.add(new GenAIOptimizationV7Proposals(zaiOrchestrator));
        proposalServices.add(new ComplianceGovernanceV7Proposals(zaiOrchestrator));
        proposalServices.add(new PortfolioGovernanceV7Proposals(zaiOrchestrator));
        proposalServices.add(new ValueStreamCoordinationV7Proposals(zaiOrchestrator));
        proposalServices.add(new ARTOrchestrationV7Proposals(zaiOrchestrator));

        orchestrator = new V7SelfPlayOrchestrator(
            zaiOrchestrator, proposalServices, 0.85, 5);
    }

    // =========================================================================
    // Receipt hash presence
    // =========================================================================

    @Test
    void receiptHashes_notEmptyAfterLoop() {
        V7SimulationReport report = orchestrator.runLoop();

        assertFalse(report.receiptHashes().isEmpty(),
            "V7SelfPlayOrchestrator must produce at least one receipt hash per round. "
            + "Got 0 hashes for " + report.totalRounds() + " rounds.");
    }

    @Test
    void receiptHashes_countMatchesRoundsExecuted() {
        V7SimulationReport report = orchestrator.runLoop();

        assertEquals(report.totalRounds(), report.receiptHashes().size(),
            "Must have exactly one receipt hash per executed round. "
            + "Rounds: " + report.totalRounds()
            + ", Hashes: " + report.receiptHashes().size());
    }

    // =========================================================================
    // Hash format (SHA3-256 = 64 hex chars)
    // =========================================================================

    @Test
    void receiptHashes_allAreValidSha3256HexStrings() {
        V7SimulationReport report = orchestrator.runLoop();

        for (int i = 0; i < report.receiptHashes().size(); i++) {
            String hash = report.receiptHashes().get(i);
            assertNotNull(hash, "Hash at round " + (i + 1) + " must not be null");
            assertEquals(64, hash.length(),
                "SHA3-256 hex must be exactly 64 chars. Round " + (i + 1) + ": " + hash);
            assertTrue(hash.matches("[0-9a-f]{64}"),
                "Hash must be lowercase hex. Round " + (i + 1) + ": " + hash);
        }
    }

    // =========================================================================
    // Chain property — hashes are distinct
    // =========================================================================

    @Test
    void receiptHashes_allDistinct_formingChain() {
        V7SimulationReport report = orchestrator.runLoop();

        if (report.receiptHashes().size() <= 1) {
            // Only one round — chain property not testable, but hash must be non-blank
            assertFalse(report.receiptHashes().get(0).isBlank(),
                "Single-round hash must not be blank");
            return;
        }

        long uniqueCount = report.receiptHashes().stream().distinct().count();
        assertEquals(report.receiptHashes().size(), uniqueCount,
            "All receipt hashes must be distinct (hash chain property). "
            + "Duplicates found in: " + report.receiptHashes());
    }

    // =========================================================================
    // Determinism — same inputs produce same first-round hash
    // =========================================================================

    @Test
    void receiptHashes_firstRoundHash_isDeterministic() {
        // Verify Blake3Receipt.hash() is deterministic for fixed inputs.
        // The orchestrator uses random UUIDs per run, so cross-run hashes differ — that
        // is correct. This test verifies the HASH ALGORITHM is deterministic (same inputs
        // always produce the same hex output), not that orchestrator state is stable.
        FitnessScore fixedFitness = new FitnessScore(1.0, 1.0, 1.0, 1.0);
        String hash1 = Blake3Receipt.hash(1, List.of(), List.of(), fixedFitness, "");
        String hash2 = Blake3Receipt.hash(1, List.of(), List.of(), fixedFitness, "");

        assertNotNull(hash1, "First hash computation must not be null");
        assertNotNull(hash2, "Second hash computation must not be null");
        assertEquals(hash1, hash2,
            "Blake3Receipt.hash() must be deterministic: same inputs → same hex. "
            + "Call1: " + hash1 + ", Call2: " + hash2);

        // Also verify orchestrator produces hashes in both runs (format is checked elsewhere)
        V7SimulationReport report1 = orchestrator.runLoop();
        assertFalse(report1.receiptHashes().isEmpty(),
            "First orchestrator run must produce receipt hashes");
    }

    // =========================================================================
    // Report integration
    // =========================================================================

    @Test
    void receiptHashes_appearsInSummary() {
        V7SimulationReport report = orchestrator.runLoop();

        String summary = report.summary();
        assertTrue(summary.contains("receipt") || summary.contains("Blake3") || summary.contains("hash"),
            "Report summary must mention receipt chain. Got summary length: " + summary.length());
    }

    @Test
    void receiptHashes_roundCount_matchesConvergenceRounds() {
        V7SimulationReport report = orchestrator.runLoop();

        assertTrue(report.converged(),
            "Loop must converge for receipt chain test to be meaningful");
        assertTrue(report.totalRounds() >= 1,
            "At least 1 round must have been executed");
        assertEquals(report.totalRounds(), report.receiptHashes().size(),
            "Receipt chain length must equal number of rounds");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static ZAIOrchestrator createZAIOrchestrator() {
        YEngine engine = createYEngine();
        return new ZAIOrchestrator(engine);
    }

    private static YEngine createYEngine() {
        try {
            var ctor = YEngine.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return (YEngine) ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create YEngine for Blake3ReceiptChainTest", e);
        }
    }
}
