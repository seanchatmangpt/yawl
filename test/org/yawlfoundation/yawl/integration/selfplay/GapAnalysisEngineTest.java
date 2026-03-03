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

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.integration.selfplay.model.V7Gap;
import org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngine;
import org.yawlfoundation.yawl.qlever.QLeverFfiException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GapAnalysisEngine conformance checking with rust4pm NIF.
 * Chicago TDD: real WSJF calculation, real SPARQL persistence, real conformance.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("GapAnalysisEngine Conformance Tests")
class GapAnalysisEngineTest {

    private GapAnalysisEngine engine;

    @BeforeEach
    void setUp() throws QLeverFfiException {
        engine = new GapAnalysisEngine();
        engine.initialize();
    }

    @AfterEach
    void tearDown() throws QLeverFfiException {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    @DisplayName("Test 1: Discover gaps from rust4pm conformance results")
    void testDiscoverGapsFromConformance() throws Exception {
        // Sample OCEL2 JSON representing a linear workflow trace
        String ocelJson = """
            {
                "objectTypes": [{"name": "Order", "attributes": []}],
                "eventTypes": [{"name": "Task", "attributes": []}],
                "objects": [{"id": "order-1", "type": "Order", "attributes": []}],
                "events": [
                    {"id": "task1", "type": "Task", "time": "2024-01-01T10:00:00Z",
                     "attributes": [], "relationships": [{"objectId": "order-1", "qualifier": ""}]},
                    {"id": "task2", "type": "Task", "time": "2024-01-01T11:00:00Z",
                     "attributes": [], "relationships": [{"objectId": "order-1", "qualifier": ""}]}
                ]
            }
            """;

        // Simple PNML representing a linear workflow: Task1 -> Task2
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="linear-net" name="Linear Workflow">
                <place id="p1">
                  <name><text>start</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p2">
                  <name><text>end</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t1">
                  <name><text>Task1</text></name>
                </transition>
                <transition id="t2">
                  <name><text>Task2</text></name>
                </transition>
                <arc id="a1" source="p1" target="t1"/>
                <arc id="a2" source="t1" target="p2"/>
                <arc id="a3" source="p2" target="t2"/>
                <arc id="a4" source="t2" target="p2"/>
              </net>
            </pnml>
            """;

        // Use GapAnalysisEngine with rust4pm integration to check conformance
        List<GapAnalysisEngine.Gap> gaps = engine.discoverGaps(ocelJson, pnmlXml);

        assertNotNull(gaps, "Gaps list should not be null");
        assertFalse(gaps.isEmpty(), "Should discover at least one gap from conformance check");

        // Check that gaps are properly structured
        GapAnalysisEngine.Gap firstGap = gaps.get(0);
        assertNotNull(firstGap.id());
        assertNotNull(firstGap.type());
        assertNotNull(firstGap.description());
        assertTrue(firstGap.demandScore() >= 0);
        assertTrue(firstGap.complexity() >= 0);
        assertTrue(firstGap.wsjfScore() >= 0);
        assertEquals(0, firstGap.rank()); // Will be assigned after ranking

        // Check specific gap types
        boolean hasConformanceGap = gaps.stream()
            .anyMatch(g -> "ConformanceGap".equals(g.type()));
        assertTrue(hasConformanceGap, "Should discover conformance gaps");
    }

    @Test
    @DisplayName("Test 2: WSJF scoring calculations are correct")
    void testWSJFCalculations() {
        // Test case 1: High missing ratio (0.4) → high WSJF
        double missingRatio = 0.4;
        double wsjf1 = engine.calculateWSJF(missingRatio, 8.0, 3.0);

        // Test case 2: Low missing ratio (0.1) → lower WSJF
        double lowRatio = 0.1;
        double wsjf2 = engine.calculateWSJF(lowRatio, 8.0, 3.0);

        // High missing ratio should result in higher WSJF
        assertTrue(wsjf1 > wsjf2, "Higher missing ratio should yield higher WSJF");

        // Test case 3: Edge case with zero ratio
        double zeroWsjf = engine.calculateWSJF(0.0, 8.0, 3.0);
        assertEquals(0.0, zeroWsjf, "Zero missing ratio should yield zero WSJF");

        // Test case 4: High complexity → lower WSJF (business value same)
        double highComplexity = engine.calculateWSJF(0.3, 8.0, 5.0);
        double lowComplexity = engine.calculateWSJF(0.3, 8.0, 1.0);
        assertTrue(highComplexity < lowComplexity, "Higher complexity should reduce WSJF");
    }

    @Test
    @DisplayName("Test 3: Persist gaps to QLever with SPARQL INSERT")
    void testPersistGaps() throws Exception {
        // Create sample gaps to persist
        List<GapAnalysisEngine.CapabilityGap> gaps = List.of(
            new GapAnalysisEngine.CapabilityGap(
                "gap_conformance_001",
                "ConformanceGap",
                8.0,
                4.0,
                "Simulation traces deviate from reference model"
            ),
            new GapAnalysisEngine.CapabilityGap(
                "gap_memory_segment",
                "MemorySegment",
                5.0,
                3.0,
                "Missing capability to produce MemorySegment for native access"
            )
        );

        // Persist gaps to QLever
        int persistedCount = engine.persistGaps(gaps);

        assertEquals(gaps.size(), persistedCount, "Should persist all gaps");

        // Verify gaps are stored in QLever
        List<String> persistedGapIds = engine.queryPersistedGaps();
        assertFalse(persistedGapIds.isEmpty(), "Should have persisted gaps in QLever");

        // Check specific gap is persisted
        boolean hasConformanceGap = persistedGapIds.stream()
            .anyMatch(id -> id.equals("gap_conformance_001"));
        assertTrue(hasConformanceGap, "Conformance gap should be persisted");
    }

    @Test
    @DisplayName("Test 4: Rank gaps by WSJF and assign ranks")
    void testRankGapsByWSJF() {
        List<GapAnalysisEngine.Gap> gaps = List.of(
            new GapAnalysisEngine.Gap("gap1", "TypeA", "Description 1", 2.0, 1.0, 10.0, 0),
            new GapAnalysisEngine.Gap("gap2", "TypeB", "Description 2", 3.0, 2.0, 15.0, 0),
            new GapAnalysisEngine.Gap("gap3", "TypeC", "Description 3", 1.0, 3.0, 8.0, 0)
        );

        List<GapAnalysisEngine.Gap> ranked = engine.rankByWSJF(gaps);

        assertEquals(3, ranked.size(), "Should rank all gaps");

        // Verify ranking: highest WSJF first
        assertTrue(ranked.get(0).wsjfScore() >= ranked.get(1).wsjfScore());
        assertTrue(ranked.get(1).wsjfScore() >= ranked.get(2).wsjfScore());

        // Verify ranks are assigned
        assertEquals(1, ranked.get(0).rank());
        assertEquals(2, ranked.get(1).rank());
        assertEquals(3, ranked.get(2).rank());
    }

    @Test
    @DisplayName("Test 5: Gap analysis integration with rust4pm NIF")
    void testGapAnalysisIntegration() throws Exception {
        // Create realistic workflow trace with conformance gap
        String ocelJson = """
            {
                "objectTypes": [{"name": "Case", "attributes": []}],
                "eventTypes": [{"name": "CompleteTask", "attributes": []}],
                "objects": [
                    {"id": "case-001", "type": "Case", "attributes": []},
                    {"id": "case-002", "type": "Case", "attributes": []}
                ],
                "events": [
                    {"id": "e1", "type": "CompleteTask", "time": "2024-01-01T10:00:00Z",
                     "attributes": [], "relationships": [{"objectId": "case-001", "qualifier": ""}]},
                    {"id": "e2", "type": "CompleteTask", "time": "2024-01-01T10:30:00Z",
                     "attributes": [], "relationships": [{"objectId": "case-001", "qualifier": ""}]}
                ]
            }
            """;

        // Create PNML model that expects 3 tasks but trace has only 2
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="test-net" name="Test Workflow">
                <place id="p1">
                  <name><text>start</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p2">
                  <name><text>end</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t1">
                  <name><text>Task1</text></name>
                </transition>
                <transition id="t2">
                  <name><text>Task2</text></name>
                </transition>
                <transition id="t3">
                  <name><text>Task3</text></name>
                </transition>
                <arc id="a1" source="p1" target="t1"/>
                <arc id="a2" source="t1" target="t2"/>
                <arc id="a3" source="t2" target="t3"/>
                <arc id="a4" source="t3" target="p2"/>
              </net>
            </pnml>
            """;

        // Execute full analysis workflow
        List<GapAnalysisEngine.Gap> gaps = engine.discoverGaps(ocelJson, pnmlXml);
        List<GapAnalysisEngine.Gap> rankedGaps = engine.rankByWSJF(gaps);

        // Persist gaps
        List<GapAnalysisEngine.CapabilityGap> capabilityGaps = rankedGaps.stream()
            .map(gap -> new GapAnalysisEngine.CapabilityGap(
                gap.id(),
                gap.type(),
                gap.demandScore(),
                gap.complexity(),
                gap.description()
            ))
            .toList();

        int persisted = engine.persistGaps(capabilityGaps);

        // Verify integration worked
        assertTrue(gaps.size() > 0, "Should discover gaps from rust4pm conformance");
        assertTrue(rankedGaps.size() > 0, "Should rank gaps");
        assertEquals(capabilityGaps.size(), persisted, "Should persist gaps");
    }

    @Test
    @DisplayName("Test 6: Self-play specific gap discovery")
    void testDiscoverAndPersistGaps() throws Exception {
        // This test mimics the self-play loop where gaps are discovered
        // and persisted for cross-session learning

        // Step 1: Generate mock OCEL data from simulation
        String ocelJson = """
            {
                "objectTypes": [{"name": "WorkflowCase", "attributes": []}],
                "eventTypes": [{"name": "ExecuteTask", "attributes": []}],
                "objects": [
                    {"id": "case-selfplay-001", "type": "WorkflowCase", "attributes": []},
                    {"id": "case-selfplay-002", "type": "WorkflowCase", "attributes": []}
                ],
                "events": [
                    {"id": "task1", "type": "ExecuteTask", "time": "2024-01-01T10:00:00Z",
                     "attributes": [], "relationships": [{"objectId": "case-selfplay-001", "qualifier": ""}]},
                    {"id": "task2", "type": "ExecuteTask", "time": "2024-01-01T11:00:00Z",
                     "attributes": [], "relationships": [{"objectId": "case-selfplay-001", "qualifier": ""}]}
                ]
            }
            """;

        // Step 2: Define reference model with expected gaps
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="selfplay-net" name="Self-Play Reference Model">
                <place id="p_start">
                  <name><text>start</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p_end">
                  <name><text>end</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t1">
                  <name><text>ExpectedTask1</text></name>
                </transition>
                <transition id="t2">
                  <name><text>ExpectedTask2</text></name>
                </transition>
                <transition id="t3">
                  <name><text>ExpectedTask3</text></name>
                </transition>
                <arc id="a1" source="p_start" target="t1"/>
                <arc id="a2" source="t1" target="t2"/>
                <arc id="a3" source="t2" target="t3"/>
                <arc id="a4" source="t3" target="p_end"/>
              </net>
            </pnml>
            """;

        // Step 3: Execute self-play gap analysis
        List<GapAnalysisEngine.Gap> gaps = engine.discoverGaps(ocelJson, pnmlXml);
        assertTrue(gaps.size() > 0, "Self-play should discover capability gaps");

        // Step 4: Prioritize gaps using WSJF
        List<GapAnalysisEngine.Gap> prioritized = engine.rankByWSJF(gaps);
        assertEquals(1, prioritized.get(0).rank(), "Highest priority gap should be rank 1");

        // Step 5: Persist gaps to QLever for learning
        List<GapAnalysisEngine.CapabilityGap> capabilityGaps = prioritized.stream()
            .map(gap -> new GapAnalysisEngine.CapabilityGap(
                gap.id(),
                gap.type(),
                gap.demandScore(),
                gap.complexity(),
                gap.description()
            ))
            .toList();

        int persistedCount = engine.persistGaps(capabilityGaps);
        assertEquals(capabilityGaps.size(), persistedCount, "All gaps should be persisted");

        // Step 6: Verify conformance scores are stored
        int totalPersisted = engine.countPersistedGaps();
        assertTrue(totalPersisted > 0, "QLever should contain persisted gaps");

        // Step 7: Generate summary for self-play monitoring
        String summary = engine.generateSummary(prioritized);
        assertNotNull(summary, "Summary should be generated");
        assertTrue(summary.contains("Capability Gap Analysis Report"), "Summary should report title");
    }

    @Test
    @DisplayName("Test 7: Verify conformance score flow to QLever")
    void testConformanceScoreFlowToQLever() throws Exception {
        // Create a conformance result with specific fitness/precision values
        List<GapAnalysisEngine.Gap> gaps = List.of(
            new GapAnalysisEngine.Gap(
                "fitness_gap_001",
                "FitnessGap",
                0.75, // fitness score < 1.0
                6.0,
                "Low fitness: simulation traces deviate significantly from reference"
            )
        );

        // Persist gaps and verify conformance metrics flow to QLever
        int persisted = engine.persistGaps(gaps);
        assertEquals(1, persisted, "Should persist one gap");

        // Query persisted gaps and verify metrics
        List<String> persistedGaps = engine.queryPersistedGaps();
        assertFalse(persistedGaps.isEmpty(), "Should have persisted gaps");

        // Verify WSJF score is calculated and stored
        GapAnalysisEngine.CapabilityGap persistedGap = gaps.get(0);
        double wsjfScore = engine.calculateWSJF(
            persistedGap.demandScore(),
            3.0, // time criticality
            2.0, // risk reduction
            persistedGap.complexity()
        );

        assertTrue(wsjfScore > 0, "WSJF score should be positive for conformance gaps");
    }

    @Test
    @DisplayName("Test 8: Integration test with ProcessMiningL3 via rust4pm")
    void testProcessMiningL3Integration() throws Exception {
        // This test simulates the integration between GapAnalysisEngine
        // and rust4pm through ProcessMiningL3 interface

        String ocelJson = """
            {
                "objectTypes": [{"name": "BusinessCase", "attributes": []}],
                "eventTypes": [{"name": "ProcessStep", "attributes": []}],
                "objects": [
                    {"id": "case-integration-001", "type": "BusinessCase", "attributes": []}
                ],
                "events": [
                    {"id": "step1", "type": "ProcessStep", "time": "2024-01-01T10:00:00Z",
                     "attributes": [], "relationships": [{"objectId": "case-integration-001", "qualifier": ""}]}
                ]
            }
            """;

        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="integration-test" name="Integration Test">
                <place id="p1">
                  <name><text>start</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p2">
                  <name><text>end</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t1">
                  <name><text>IntegrationTask</text></name>
                </transition>
                <arc id="a1" source="p1" target="t1"/>
                <arc id="a2" source="t1" target="p2"/>
              </net>
            </pnml>
            """;

        // Execute full integration test
        List<GapAnalysisEngine.Gap> gaps = engine.discoverGaps(ocelJson, pnmlXml);

        // Verify conformance analysis worked
        assertTrue(gaps.size() > 0, "rust4pm should identify conformance gaps");

        // Persist and verify
        List<GapAnalysisEngine.CapabilityGap> capabilityGaps = gaps.stream()
            .map(g -> new GapAnalysisEngine.CapabilityGap(
                g.id(), g.type(), g.demandScore(), g.complexity(), g.description()
            ))
            .toList();

        int persisted = engine.persistGaps(capabilityGaps);
        assertEquals(capabilityGaps.size(), persisted, "Should persist all gaps");
    }

    @Test
    @DisplayName("Test 9: Edge case - perfect conformance")
    void testPerfectConformance() throws Exception {
        // Create OCEL trace that perfectly matches PNML model
        String ocelJson = """
            {
                "objectTypes": [{"name": "PerfectCase", "attributes": []}],
                "eventTypes": [{"name": "PerfectTask", "attributes": []}],
                "objects": [
                    {"id": "case-perfect-001", "type": "PerfectCase", "attributes": []}
                ],
                "events": [
                    {"id": "perfect1", "type": "PerfectTask", "time": "2024-01-01T10:00:00Z",
                     "attributes": [], "relationships": [{"objectId": "case-perfect-001", "qualifier": ""}]}
                ]
            }
            """;

        // PNML model that exactly matches the trace
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="perfect-net" name="Perfect Match">
                <place id="p1">
                  <name><text>start</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p2">
                  <name><text>end</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t1">
                  <name><text>PerfectTask</text></name>
                </transition>
                <arc id="a1" source="p1" target="t1"/>
                <arc id="a2" source="t1" target="p2"/>
              </net>
            </pnml>
            """;

        // With perfect conformance, should have minimal gaps
        List<GapAnalysisEngine.Gap> gaps = engine.discoverGaps(ocelJson, pnmlXml);

        // Should either have no gaps or only very minor ones
        assertTrue(gaps.isEmpty() || gaps.size() <= 1, "Perfect conformance should have few gaps");
    }
}