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

package org.yawlfoundation.yawl.integration.processmining.pnml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PnmlParser.
 * Chicago TDD: real parsing of PNML XML, real validation.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("PnmlParser Tests")
class PnmlParserTest {

    private PnmlParser parser;

    @BeforeEach
    void setUp() {
        parser = new PnmlParser();
    }

    @Test
    @DisplayName("Parse single task process (start -> task -> end)")
    void testParseSingleTransition_success() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="net1" name="SimpleProcess">
                <place id="p_start">
                  <name><text>start</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p_end">
                  <name><text>end</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t_task1">
                  <name><text>Execute Task</text></name>
                </transition>
                <arc id="arc1" source="p_start" target="t_task1" />
                <arc id="arc2" source="t_task1" target="p_end" />
              </net>
            </pnml>
            """;

        PnmlProcess process = parser.parse(pnmlXml);

        assertNotNull(process);
        assertEquals("net1", process.id());
        assertEquals("SimpleProcess", process.name());
        assertEquals(2, process.places().size());
        assertEquals(1, process.transitions().size());
        assertEquals(2, process.arcs().size());
    }

    @Test
    @DisplayName("Parse linear sequence A->B->C")
    void testParseLinearProcess_success() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="seq" name="Linear">
                <place id="p_start">
                  <name><text>start</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p_middle">
                  <name><text>middle</text></name>
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
                <arc id="arc1" source="p_start" target="t_a" />
                <arc id="arc2" source="t_a" target="p_middle" />
                <arc id="arc3" source="p_middle" target="t_b" />
                <arc id="arc4" source="t_b" target="p_end" />
              </net>
            </pnml>
            """;

        PnmlProcess process = parser.parse(pnmlXml);

        assertEquals(3, process.places().size());
        assertEquals(2, process.transitions().size());
        assertEquals(4, process.arcs().size());
        assertTrue(process.isValid());
        assertEquals(2, process.observableTransitionCount());
        assertEquals(0, process.silentTransitionCount());
    }

    @Test
    @DisplayName("Identify start place with initial marking")
    void testParseWithInitialMarking_startPlaceIdentified() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="net1" name="Test">
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
                <arc id="arc1" source="p_start" target="t1" />
                <arc id="arc2" source="t1" target="p_end" />
              </net>
            </pnml>
            """;

        PnmlProcess process = parser.parse(pnmlXml);

        PnmlPlace startPlace = process.startPlace();
        assertEquals("p_start", startPlace.id());
        assertEquals(1, startPlace.initialMarking());
        assertTrue(startPlace.isStartPlace());
    }

    @Test
    @DisplayName("Identify end places (no outgoing arcs)")
    void testParseWithEndPlaces_identified() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="net1" name="Test">
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
                <arc id="arc1" source="p_start" target="t1" />
                <arc id="arc2" source="t1" target="p_end" />
              </net>
            </pnml>
            """;

        PnmlProcess process = parser.parse(pnmlXml);

        var endPlaces = process.endPlaces();
        assertEquals(1, endPlaces.size());
        assertEquals("p_end", endPlaces.get(0).id());
    }

    @Test
    @DisplayName("Parse process with silent transitions")
    void testParseSilentTransitions_identified() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="net1" name="Silent">
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
                <transition id="t_silent">
                  <name><text>tau</text></name>
                </transition>
                <transition id="t_obs">
                  <name><text>Observe</text></name>
                </transition>
                <arc id="arc1" source="p_start" target="t_silent" />
                <arc id="arc2" source="t_silent" target="p_mid" />
                <arc id="arc3" source="p_mid" target="t_obs" />
                <arc id="arc4" source="t_obs" target="p_end" />
              </net>
            </pnml>
            """;

        PnmlProcess process = parser.parse(pnmlXml);

        assertEquals(1, process.silentTransitionCount());
        assertEquals(1, process.observableTransitionCount());
    }

    @Test
    @DisplayName("Reject invalid XML (malformed)")
    void testInvalidXml_throwsPnmlParseException() {
        String invalidXml = """
            <?xml version="1.0"?>
            <pnml>
              <net id="test" name="Bad">
                <place id="p1" <-- MISSING CLOSING TAG
              </net>
            </pnml>
            """;

        assertThrows(PnmlParseException.class, () -> parser.parse(invalidXml));
    }

    @Test
    @DisplayName("Reject PNML without net element")
    void testMissingNetElement_throwsPnmlParseException() {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <!-- no net element -->
            </pnml>
            """;

        assertThrows(PnmlParseException.class, () -> parser.parse(pnmlXml));
    }

    @Test
    @DisplayName("Reject PNML without root pnml element")
    void testMissingPnmlRoot_throwsPnmlParseException() {
        String notPnml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <something>
              <net id="test" />
            </something>
            """;

        assertThrows(PnmlParseException.class, () -> parser.parse(notPnml));
    }

    @Test
    @DisplayName("Reject null or empty PNML")
    void testNullOrEmptyPnml_throwsPnmlParseException() {
        assertThrows(PnmlParseException.class, () -> parser.parse(null));
        assertThrows(PnmlParseException.class, () -> parser.parse(""));
    }

    @Test
    @DisplayName("Find transition by ID")
    void testTransitionById_found() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="net1" name="Test">
                <place id="p1">
                  <name><text>p1</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p2">
                  <name><text>p2</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t_foo">
                  <name><text>Foo Task</text></name>
                </transition>
                <arc id="a1" source="p1" target="t_foo" />
                <arc id="a2" source="t_foo" target="p2" />
              </net>
            </pnml>
            """;

        PnmlProcess process = parser.parse(pnmlXml);

        var t_foo = process.transitionById("t_foo");
        assertTrue(t_foo.isPresent());
        assertEquals("Foo Task", t_foo.get().name());
    }

    @Test
    @DisplayName("Find place by ID")
    void testPlaceById_found() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="net1" name="Test">
                <place id="p_test">
                  <name><text>Test Place</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <transition id="t1">
                  <name><text>T1</text></name>
                </transition>
                <arc id="a1" source="p_test" target="t1" />
              </net>
            </pnml>
            """;

        PnmlProcess process = parser.parse(pnmlXml);

        var place = process.placeById("p_test");
        assertTrue(place.isPresent());
        assertEquals("Test Place", place.get().name());
        assertEquals(1, place.get().initialMarking());
    }

    @Test
    @DisplayName("Compute incoming/outgoing arcs correctly")
    void testArcConnectivity_computed() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="net1" name="Test">
                <place id="p1">
                  <name><text>p1</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p2">
                  <name><text>p2</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t1">
                  <name><text>T1</text></name>
                </transition>
                <arc id="a1" source="p1" target="t1" />
                <arc id="a2" source="t1" target="p2" />
              </net>
            </pnml>
            """;

        PnmlProcess process = parser.parse(pnmlXml);

        // p1 has 1 outgoing arc
        assertEquals(1, process.outgoingArcs("p1").size());

        // t1 has 1 incoming arc, 1 outgoing
        assertEquals(1, process.incomingArcs("t1").size());
        assertEquals(1, process.outgoingArcs("t1").size());

        // p2 has 1 incoming arc
        assertEquals(1, process.incomingArcs("p2").size());
    }

    @Test
    @DisplayName("Validate well-formed process")
    void testIsValid_wellformed() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="net1" name="Valid">
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
        assertTrue(process.isValid());
    }

    @Test
    @DisplayName("Reject process with missing start place")
    void testIsValid_noStartPlace() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="net1" name="NoStart">
                <place id="p1">
                  <name><text>p1</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t1">
                  <name><text>Task</text></name>
                </transition>
                <arc id="a1" source="p1" target="t1" />
              </net>
            </pnml>
            """;

        PnmlProcess process = parser.parse(pnmlXml);
        assertFalse(process.isValid());
    }

    @Test
    @DisplayName("Reject process with dangling arc reference")
    void testIsValid_danglingArcReference() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="net1" name="Dangling">
                <place id="p1">
                  <name><text>p1</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <transition id="t1">
                  <name><text>Task</text></name>
                </transition>
                <arc id="a1" source="p1" target="t_nonexistent" />
              </net>
            </pnml>
            """;

        PnmlProcess process = parser.parse(pnmlXml);
        assertFalse(process.isValid());
    }

    @Test
    @DisplayName("Parse process with multiple start markings (picks first as start)")
    void testParseMultipleMarkedPlaces_firstIsStart() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="net1" name="MultiStart">
                <place id="p1">
                  <name><text>p1</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p2">
                  <name><text>p2</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <transition id="t1">
                  <name><text>Task</text></name>
                </transition>
                <arc id="a1" source="p1" target="t1" />
                <arc id="a2" source="p2" target="t1" />
              </net>
            </pnml>
            """;

        PnmlProcess process = parser.parse(pnmlXml);

        // Process has multiple start places, so isValid() should be false
        assertFalse(process.isValid());
    }

    @Test
    @DisplayName("Parse place without initial marking (defaults to 0)")
    void testParseUnmarkedPlace_defaultsToZero() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="net1" name="NoMarking">
                <place id="p_unmarked">
                  <name><text>unmarked</text></name>
                </place>
                <place id="p_start">
                  <name><text>start</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <transition id="t1">
                  <name><text>Task</text></name>
                </transition>
                <arc id="a1" source="p_start" target="t1" />
                <arc id="a2" source="t1" target="p_unmarked" />
              </net>
            </pnml>
            """;

        PnmlProcess process = parser.parse(pnmlXml);

        var place = process.placeById("p_unmarked").orElseThrow();
        assertEquals(0, place.initialMarking());
        assertFalse(place.isStartPlace());
    }

    @Test
    @DisplayName("Parse transition without name (empty name)")
    void testParseUnnamedTransition_emptyName() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="net1" name="Unnamed">
                <place id="p1">
                  <name><text>start</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p2">
                  <name><text>end</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t_unnamed">
                </transition>
                <arc id="a1" source="p1" target="t_unnamed" />
                <arc id="a2" source="t_unnamed" target="p2" />
              </net>
            </pnml>
            """;

        PnmlProcess process = parser.parse(pnmlXml);

        var transition = process.transitionById("t_unnamed").orElseThrow();
        assertTrue(transition.isSilent());
    }
}
