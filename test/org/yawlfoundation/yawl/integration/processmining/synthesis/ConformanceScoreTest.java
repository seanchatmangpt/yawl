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

package org.yawlfoundation.yawl.integration.processmining.synthesis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.processmining.pnml.PnmlArc;
import org.yawlfoundation.yawl.integration.processmining.pnml.PnmlParseException;
import org.yawlfoundation.yawl.integration.processmining.pnml.PnmlParser;
import org.yawlfoundation.yawl.integration.processmining.pnml.PnmlPlace;
import org.yawlfoundation.yawl.integration.processmining.pnml.PnmlProcess;
import org.yawlfoundation.yawl.integration.processmining.pnml.PnmlTransition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConformanceScore.
 * Chicago TDD: real scoring algorithm, real metric computation.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("ConformanceScore Tests")
class ConformanceScoreTest {

    @Test
    @DisplayName("Create conformance score with valid metrics")
    void testCreate_validScore() {
        ConformanceScore score = new ConformanceScore(0.95, 0.87, 0.91, 5, 3, 8);

        assertEquals(0.95, score.fitness());
        assertEquals(0.87, score.precision());
        assertEquals(0.91, score.generalization());
        assertEquals(5, score.placeCount());
        assertEquals(3, score.transitionCount());
        assertEquals(8, score.arcCount());
    }

    @Test
    @DisplayName("Reject fitness outside [0.0, 1.0]")
    void testCreate_invalidFitness() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConformanceScore(1.5, 0.5, 0.5, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
            () -> new ConformanceScore(-0.1, 0.5, 0.5, 1, 1, 1));
    }

    @Test
    @DisplayName("Reject precision outside [0.0, 1.0]")
    void testCreate_invalidPrecision() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConformanceScore(0.5, 1.1, 0.5, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
            () -> new ConformanceScore(0.5, -0.1, 0.5, 1, 1, 1));
    }

    @Test
    @DisplayName("Reject negative structure counts")
    void testCreate_negativeStructureCounts() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConformanceScore(0.5, 0.5, 0.5, -1, 1, 1));
        assertThrows(IllegalArgumentException.class,
            () -> new ConformanceScore(0.5, 0.5, 0.5, 1, -1, 1));
        assertThrows(IllegalArgumentException.class,
            () -> new ConformanceScore(0.5, 0.5, 0.5, 1, 1, -1));
    }

    @Test
    @DisplayName("Identify high conformance (fitness >= 0.9 AND precision >= 0.8)")
    void testIsHighConformance_true() {
        ConformanceScore high = new ConformanceScore(0.95, 0.85, 0.80, 1, 1, 1);
        assertTrue(high.isHighConformance());
    }

    @Test
    @DisplayName("Identify low fitness as not high conformance")
    void testIsHighConformance_lowFitness() {
        ConformanceScore low = new ConformanceScore(0.85, 0.95, 0.95, 1, 1, 1);
        assertFalse(low.isHighConformance());
    }

    @Test
    @DisplayName("Identify low precision as not high conformance")
    void testIsHighConformance_lowPrecision() {
        ConformanceScore low = new ConformanceScore(0.95, 0.75, 0.95, 1, 1, 1);
        assertFalse(low.isHighConformance());
    }

    @Test
    @DisplayName("Compute overall score as weighted combination")
    void testOverallScore_weightedCombination() {
        ConformanceScore score = new ConformanceScore(0.80, 0.60, 0.40, 1, 1, 1);

        // 0.5 * 0.80 + 0.3 * 0.60 + 0.2 * 0.40
        // = 0.40 + 0.18 + 0.08 = 0.66
        double expected = 0.5 * 0.80 + 0.3 * 0.60 + 0.2 * 0.40;
        assertEquals(expected, score.overallScore(), 0.001);
    }

    @Test
    @DisplayName("Overall score is bounded by [0.0, 1.0]")
    void testOverallScore_bounded() {
        ConformanceScore perfect = new ConformanceScore(1.0, 1.0, 1.0, 1, 1, 1);
        ConformanceScore worst = new ConformanceScore(0.0, 0.0, 0.0, 1, 1, 1);

        assertTrue(perfect.overallScore() <= 1.0);
        assertTrue(worst.overallScore() >= 0.0);
    }

    @Test
    @DisplayName("Summary string contains all metrics")
    void testSummary_containsAllMetrics() {
        ConformanceScore score = new ConformanceScore(0.95, 0.87, 0.91, 5, 3, 8);
        String summary = score.summary();

        assertNotNull(summary);
        assertTrue(summary.contains("Fitness"));
        assertTrue(summary.contains("0.95"));
        assertTrue(summary.contains("Precision"));
        assertTrue(summary.contains("0.87"));
        assertTrue(summary.contains("Generalization"));
        assertTrue(summary.contains("0.91"));
        assertTrue(summary.contains("Overall"));
    }

    @Test
    @DisplayName("Detailed summary includes structure info")
    void testDetailedSummary_includesStructure() {
        ConformanceScore score = new ConformanceScore(0.95, 0.87, 0.91, 5, 3, 8);
        String detailed = score.detailedSummary();

        assertNotNull(detailed);
        assertTrue(detailed.contains("Places"));
        assertTrue(detailed.contains("5"));
        assertTrue(detailed.contains("Transitions"));
        assertTrue(detailed.contains("3"));
        assertTrue(detailed.contains("Arcs"));
        assertTrue(detailed.contains("8"));
    }

    @Test
    @DisplayName("Compute structural conformance from well-formed process")
    void testFromProcess_wellformed() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="test" name="Test">
                <place id="p_start">
                  <name><text>start</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p_end">
                  <name><text>end</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t1">
                  <name><text>Task</text></name>
                </transition>
                <arc id="a1" source="p_start" target="t1" />
                <arc id="a2" source="t1" target="p_end" />
              </net>
            </pnml>
            """;

        PnmlParser parser = new PnmlParser();
        PnmlProcess process = parser.parse(pnmlXml);

        ConformanceScore score = ConformanceScore.fromProcess(process);

        assertNotNull(score);
        assertTrue(score.fitness() >= 0.0 && score.fitness() <= 1.0);
        assertTrue(score.precision() >= 0.0 && score.precision() <= 1.0);
        assertTrue(score.generalization() >= 0.0 && score.generalization() <= 1.0);
        assertEquals(2, score.placeCount());
        assertEquals(1, score.transitionCount());
        assertEquals(2, score.arcCount());
    }

    @Test
    @DisplayName("Reject invalid process in fromProcess()")
    void testFromProcess_invalidThrows() {
        List<PnmlPlace> places = List.of(
            new PnmlPlace("p1", "place1", 0),
            new PnmlPlace("p2", "place2", 0)
        );
        List<PnmlTransition> transitions = List.of(
            new PnmlTransition("t1", "task1", false)
        );
        List<PnmlArc> arcs = List.of();

        PnmlProcess invalid = new PnmlProcess("inv", "Invalid", places, transitions, arcs);

        assertThrows(IllegalArgumentException.class, () -> ConformanceScore.fromProcess(invalid));
    }

    @Test
    @DisplayName("Balanced process receives good structural score")
    void testFromProcess_balancedProcess() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="balanced" name="Balanced">
                <place id="p1">
                  <name><text>p1</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p2">
                  <name><text>p2</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <place id="p3">
                  <name><text>p3</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t1">
                  <name><text>T1</text></name>
                </transition>
                <transition id="t2">
                  <name><text>T2</text></name>
                </transition>
                <arc id="a1" source="p1" target="t1" />
                <arc id="a2" source="t1" target="p2" />
                <arc id="a3" source="p2" target="t2" />
                <arc id="a4" source="t2" target="p3" />
              </net>
            </pnml>
            """;

        PnmlParser parser = new PnmlParser();
        PnmlProcess process = parser.parse(pnmlXml);

        ConformanceScore score = ConformanceScore.fromProcess(process);

        // Balanced process should have reasonable scores
        assertTrue(score.precision() >= 0.7, "Precision should be good for balanced process");
    }

    @Test
    @DisplayName("Simple linear process has high fitness")
    void testFromProcess_linearProcessFitness() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="linear" name="Linear">
                <place id="p_start">
                  <name><text>start</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p_end">
                  <name><text>end</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t1">
                  <name><text>Task</text></name>
                </transition>
                <arc id="a1" source="p_start" target="t1" />
                <arc id="a2" source="t1" target="p_end" />
              </net>
            </pnml>
            """;

        PnmlParser parser = new PnmlParser();
        PnmlProcess process = parser.parse(pnmlXml);

        ConformanceScore score = ConformanceScore.fromProcess(process);

        // Simple linear process with proper start/end should have good fitness
        assertTrue(score.fitness() >= 0.5);
    }

    @Test
    @DisplayName("Complex dense process may have lower precision")
    void testFromProcess_complexProcessPrecision() throws PnmlParseException {
        // Create a highly connected (overfitted) process
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="complex" name="Complex">
                <place id="p1">
                  <name><text>p1</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p2">
                  <name><text>p2</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <place id="p3">
                  <name><text>p3</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t1">
                  <name><text>T1</text></name>
                </transition>
                <transition id="t2">
                  <name><text>T2</text></name>
                </transition>
                <!-- Highly connected: every arc possible -->
                <arc id="a1" source="p1" target="t1" />
                <arc id="a2" source="p1" target="t2" />
                <arc id="a3" source="t1" target="p2" />
                <arc id="a4" source="t1" target="p3" />
                <arc id="a5" source="t2" target="p2" />
                <arc id="a6" source="t2" target="p3" />
              </net>
            </pnml>
            """;

        PnmlParser parser = new PnmlParser();
        PnmlProcess process = parser.parse(pnmlXml);

        ConformanceScore score = ConformanceScore.fromProcess(process);

        // Complex dense process may have lower precision due to overfitting
        assertNotNull(score);
        assertEquals(3, score.placeCount());
        assertEquals(2, score.transitionCount());
        assertEquals(6, score.arcCount());
    }

    @Test
    @DisplayName("Score boundary values at 0.0 and 1.0")
    void testBoundaryScores() {
        ConformanceScore zero = new ConformanceScore(0.0, 0.0, 0.0, 1, 1, 1);
        ConformanceScore one = new ConformanceScore(1.0, 1.0, 1.0, 1, 1, 1);

        assertTrue(zero.overallScore() >= 0.0);
        assertTrue(one.overallScore() <= 1.0);
        assertFalse(zero.isHighConformance());
        assertTrue(one.isHighConformance());
    }

    @Test
    @DisplayName("Summary is deterministic (same input -> same output)")
    void testSummary_deterministic() {
        ConformanceScore score1 = new ConformanceScore(0.92, 0.87, 0.89, 3, 2, 5);
        ConformanceScore score2 = new ConformanceScore(0.92, 0.87, 0.89, 3, 2, 5);

        assertEquals(score1.summary(), score2.summary());
        assertEquals(score1.detailedSummary(), score2.detailedSummary());
    }
}
