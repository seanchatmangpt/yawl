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

import org.junit.jupiter.api.BeforeEach;
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
 * Unit tests for YawlSpecSynthesizer.
 * Chicago TDD: real XML generation, real YAWL spec structure.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("YawlSpecSynthesizer Tests")
class YawlSpecSynthesizerTest {

    private YawlSpecSynthesizer synthesizer;
    private PnmlParser parser;

    @BeforeEach
    void setUp() {
        synthesizer = new YawlSpecSynthesizer(
            "http://example.com/workflow/test",
            "Test Workflow"
        );
        parser = new PnmlParser();
    }

    @Test
    @DisplayName("Synthesize minimal process generates valid YAWL XML structure")
    void testSynthesizeMinimal_validXml() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="minimal" name="Minimal">
                <place id="p_start">
                  <name><text>start</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p_end">
                  <name><text>end</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t1">
                  <name><text>Task One</text></name>
                </transition>
                <arc id="a1" source="p_start" target="t1" />
                <arc id="a2" source="t1" target="p_end" />
              </net>
            </pnml>
            """;

        PnmlProcess process = parser.parse(pnmlXml);
        String yawlXml = synthesizer.synthesize(process);

        assertNotNull(yawlXml);
        assertTrue(yawlXml.contains("<?xml"));
        assertTrue(yawlXml.contains("specificationSet"));
        assertTrue(yawlXml.contains("specification"));
        assertTrue(yawlXml.contains("decomposition"));
        assertTrue(yawlXml.contains("http://www.yawlfoundation.org/yawlschema"));
    }

    @Test
    @DisplayName("Generated YAWL XML contains specification URI")
    void testGeneratedXml_containsSpecificationUri() throws PnmlParseException {
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

        PnmlProcess process = parser.parse(pnmlXml);
        String yawlXml = synthesizer.synthesize(process);

        assertTrue(yawlXml.contains("http://example.com/workflow/test"));
    }

    @Test
    @DisplayName("Generated YAWL XML contains specification name")
    void testGeneratedXml_containsSpecificationName() throws PnmlParseException {
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

        PnmlProcess process = parser.parse(pnmlXml);
        String yawlXml = synthesizer.synthesize(process);

        assertTrue(yawlXml.contains("Test Workflow"));
    }

    @Test
    @DisplayName("Synthesize linear process includes all tasks")
    void testSynthesizeLinear_allTasksIncluded() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="linear" name="Linear">
                <place id="p_start">
                  <name><text>start</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p_mid">
                  <name><text>mid</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <place id="p_end">
                  <name><text>end</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t_a">
                  <name><text>Task A</text></name>
                </transition>
                <transition id="t_b">
                  <name><text>Task B</text></name>
                </transition>
                <arc id="a1" source="p_start" target="t_a" />
                <arc id="a2" source="t_a" target="p_mid" />
                <arc id="a3" source="p_mid" target="t_b" />
                <arc id="a4" source="t_b" target="p_end" />
              </net>
            </pnml>
            """;

        PnmlProcess process = parser.parse(pnmlXml);
        String yawlXml = synthesizer.synthesize(process);

        // Both tasks should appear
        assertTrue(yawlXml.contains("Task A"));
        assertTrue(yawlXml.contains("Task B"));

        // Count task elements
        int taskCount = countOccurrences(yawlXml, "<task id=");
        assertEquals(2, taskCount);
    }

    @Test
    @DisplayName("Synthesize with conformance returns SynthesisResult")
    void testSynthesizeWithConformance_returnsResult() throws PnmlParseException {
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

        PnmlProcess process = parser.parse(pnmlXml);
        SynthesisResult result = synthesizer.synthesizeWithConformance(process);

        assertNotNull(result);
        assertNotNull(result.getYawlXml());
        assertNotNull(result.getConformanceScore());
        assertNotNull(result.getSourceProcess());
        assertTrue(result.getSynthesisTimeMs() >= 0);
    }

    @Test
    @DisplayName("Synthesis result has correct task count")
    void testSynthesisResult_hasCorrectTaskCount() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="test" name="Test">
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
                  <name><text>Task 1</text></name>
                </transition>
                <transition id="t2">
                  <name><text>Task 2</text></name>
                </transition>
                <arc id="a1" source="p1" target="t1" />
                <arc id="a2" source="t1" target="p2" />
                <arc id="a3" source="p2" target="t2" />
                <arc id="a4" source="t2" target="p3" />
              </net>
            </pnml>
            """;

        PnmlProcess process = parser.parse(pnmlXml);
        SynthesisResult result = synthesizer.synthesizeWithConformance(process);

        assertEquals(2, result.tasksGenerated());
    }

    @Test
    @DisplayName("Synthesis result has correct condition count")
    void testSynthesisResult_hasCorrectConditionCount() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="test" name="Test">
                <place id="p1">
                  <name><text>p1</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p2">
                  <name><text>p2</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t1">
                  <name><text>Task</text></name>
                </transition>
                <arc id="a1" source="p1" target="t1" />
                <arc id="a2" source="t1" target="p2" />
              </net>
            </pnml>
            """;

        PnmlProcess process = parser.parse(pnmlXml);
        SynthesisResult result = synthesizer.synthesizeWithConformance(process);

        // 2 places + 2 (input + output conditions) = 4
        assertEquals(4, result.conditionsGenerated());
    }

    @Test
    @DisplayName("Conformance score is computed for process")
    void testSynthesizeWithConformance_scoreNotNull() throws PnmlParseException {
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

        PnmlProcess process = parser.parse(pnmlXml);
        SynthesisResult result = synthesizer.synthesizeWithConformance(process);
        ConformanceScore score = result.getConformanceScore();

        assertNotNull(score);
        assertTrue(score.fitness() >= 0.0 && score.fitness() <= 1.0);
        assertTrue(score.precision() >= 0.0 && score.precision() <= 1.0);
        assertTrue(score.generalization() >= 0.0 && score.generalization() <= 1.0);
    }

    @Test
    @DisplayName("Input condition flows to first task")
    void testGeneratedXml_inputConditionConnected() throws PnmlParseException {
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
                <transition id="t_first">
                  <name><text>First Task</text></name>
                </transition>
                <arc id="a1" source="p_start" target="t_first" />
                <arc id="a2" source="t_first" target="p_end" />
              </net>
            </pnml>
            """;

        PnmlProcess process = parser.parse(pnmlXml);
        String yawlXml = synthesizer.synthesize(process);

        assertTrue(yawlXml.contains("InputCondition"));
        assertTrue(yawlXml.contains("First_Task"));
    }

    @Test
    @DisplayName("Output condition is generated")
    void testGeneratedXml_outputConditionPresent() throws PnmlParseException {
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

        PnmlProcess process = parser.parse(pnmlXml);
        String yawlXml = synthesizer.synthesize(process);

        assertTrue(yawlXml.contains("OutputCondition"));
    }

    @Test
    @DisplayName("Reject invalid process (no start place)")
    void testSynthesizeInvalid_throwsException() {
        List<PnmlPlace> places = List.of(
            new PnmlPlace("p1", "place1", 0),
            new PnmlPlace("p2", "place2", 0)
        );
        List<PnmlTransition> transitions = List.of(
            new PnmlTransition("t1", "task1", false)
        );
        List<PnmlArc> arcs = List.of();

        PnmlProcess invalidProcess = new PnmlProcess("invalid", "Invalid", places, transitions, arcs);

        assertThrows(IllegalArgumentException.class, () -> synthesizer.synthesize(invalidProcess));
    }

    @Test
    @DisplayName("Synthesizer requires non-empty URI")
    void testSynthesizerCreation_requiresUri() {
        assertThrows(IllegalArgumentException.class,
            () -> new YawlSpecSynthesizer("", "Name"));
        assertThrows(IllegalArgumentException.class,
            () -> new YawlSpecSynthesizer(null, "Name"));
    }

    @Test
    @DisplayName("Synthesizer requires non-empty name")
    void testSynthesizerCreation_requiresName() {
        assertThrows(IllegalArgumentException.class,
            () -> new YawlSpecSynthesizer("http://example.com", ""));
        assertThrows(IllegalArgumentException.class,
            () -> new YawlSpecSynthesizer("http://example.com", null));
    }

    @Test
    @DisplayName("Silent transitions are excluded from YAWL tasks")
    void testSynthesizeWithSilentTransitions_excludedFromTasks() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="test" name="Test">
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
                <transition id="t_silent">
                  <name><text>tau</text></name>
                </transition>
                <transition id="t_obs">
                  <name><text>Observable</text></name>
                </transition>
                <arc id="a1" source="p1" target="t_silent" />
                <arc id="a2" source="t_silent" target="p2" />
                <arc id="a3" source="p2" target="t_obs" />
                <arc id="a4" source="t_obs" target="p3" />
              </net>
            </pnml>
            """;

        PnmlProcess process = parser.parse(pnmlXml);
        String yawlXml = synthesizer.synthesize(process);

        // Only observable transition should generate a task
        assertTrue(yawlXml.contains("Observable"));
        // Silent transition should not appear as a task
        int taskCount = countOccurrences(yawlXml, "<task id=");
        assertEquals(1, taskCount);
    }

    @Test
    @DisplayName("XML special characters are escaped in output")
    void testGeneratedXml_specialCharactersEscaped() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="test" name="Test &lt; &gt;">
                <place id="p_start">
                  <name><text>start</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p_end">
                  <name><text>end</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t1">
                  <name><text>Task &amp; More</text></name>
                </transition>
                <arc id="a1" source="p_start" target="t1" />
                <arc id="a2" source="t1" target="p_end" />
              </net>
            </pnml>
            """;

        PnmlProcess process = parser.parse(pnmlXml);
        String yawlXml = synthesizer.synthesize(process);

        // XML should be well-formed and not cause parsing errors
        assertTrue(yawlXml.contains("&amp;"));
        assertTrue(yawlXml.contains("&lt;"));
        assertTrue(yawlXml.contains("&gt;"));
    }

    @Test
    @DisplayName("Synthesis result validates XML structure")
    void testSynthesisResult_isValidXml() throws PnmlParseException {
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

        PnmlProcess process = parser.parse(pnmlXml);
        SynthesisResult result = synthesizer.synthesizeWithConformance(process);

        assertTrue(result.isValidXml());
    }

    @Test
    @DisplayName("Synthesis result produces readable summary")
    void testSynthesisResult_summaryIsReadable() throws PnmlParseException {
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

        PnmlProcess process = parser.parse(pnmlXml);
        SynthesisResult result = synthesizer.synthesizeWithConformance(process);

        String summary = result.summary();
        assertNotNull(summary);
        assertTrue(summary.contains("tasks"));
        assertTrue(summary.contains("conditions"));
        assertTrue(summary.contains("ms"));
    }

    /**
     * Helper method to count occurrences of a substring.
     *
     * @param text  Text to search in
     * @param search Substring to find
     * @return count of non-overlapping occurrences
     */
    private int countOccurrences(String text, String search) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(search, index)) != -1) {
            count++;
            index += search.length();
        }
        return count;
    }
}
