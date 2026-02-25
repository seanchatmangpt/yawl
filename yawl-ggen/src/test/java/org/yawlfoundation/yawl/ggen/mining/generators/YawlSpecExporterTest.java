/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.mining.generators;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.ggen.mining.model.Arc;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.mining.model.Place;
import org.yawlfoundation.yawl.ggen.mining.model.Transition;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link YawlSpecExporter}: PetriNet → YAWL v4 XML conversion.
 * All scenarios use real PetriNet objects — no mocks.
 */
class YawlSpecExporterTest {

    private PetriNet simplePetriNet;
    private YawlSpecExporter exporter;

    /**
     * Minimal net: p_start(marking=1) → t_begin → p_middle → t_finish → p_end
     */
    @BeforeEach
    void setUp() {
        exporter = new YawlSpecExporter();

        simplePetriNet = new PetriNet("test_process", "Test Process");

        Place startPlace = new Place("p_start", "Start", 1);
        Place middlePlace = new Place("p_middle", "Middle");
        Place endPlace = new Place("p_end", "End");

        simplePetriNet.addPlace(startPlace);
        simplePetriNet.addPlace(middlePlace);
        simplePetriNet.addPlace(endPlace);

        Transition beginTransition = new Transition("t_begin", "Begin Task");
        Transition finishTransition = new Transition("t_finish", "Finish Task");

        simplePetriNet.addTransition(beginTransition);
        simplePetriNet.addTransition(finishTransition);

        simplePetriNet.addArc(new Arc("a1", startPlace, beginTransition));
        simplePetriNet.addArc(new Arc("a2", beginTransition, middlePlace));
        simplePetriNet.addArc(new Arc("a3", middlePlace, finishTransition));
        simplePetriNet.addArc(new Arc("a4", finishTransition, endPlace));
    }

    /**
     * Scenario 1: simple net produces XML containing specificationSet, task elements,
     * and condition elements.
     */
    @Test
    void export_simplePetriNet_containsRequiredElements() {
        String xml = exporter.export(simplePetriNet);

        assertNotNull(xml, "Export should not return null");
        assertTrue(xml.contains("<specificationSet"), "Should contain specificationSet element");
        assertTrue(xml.contains("<task "), "Should contain at least one task element");
        assertTrue(xml.contains("<condition "), "Should contain at least one condition element");
        assertTrue(xml.contains("<decomposition "), "Should contain decomposition elements for tasks");
        assertTrue(xml.contains("test_process"), "Should reference process ID");
        assertTrue(xml.contains("Test Process"), "Should reference process name");
    }

    /**
     * Scenario 2: initial place (isInitialPlace=true) maps to inputCondition.
     */
    @Test
    void export_initialPlace_generatesInputCondition() {
        String xml = exporter.export(simplePetriNet);

        assertTrue(xml.contains("<inputCondition "), "Initial place should map to <inputCondition>");
        assertTrue(xml.contains("p_start"), "inputCondition should use the initial place ID");
    }

    /**
     * Scenario 3: final place (isFinalPlace=true) maps to outputCondition.
     */
    @Test
    void export_finalPlace_generatesOutputCondition() {
        String xml = exporter.export(simplePetriNet);

        assertTrue(xml.contains("<outputCondition "), "Final place should map to <outputCondition>");
        assertTrue(xml.contains("p_end"), "outputCondition should use the final place ID");
    }

    /**
     * Scenario 4: gateway transition (2+ outgoing arcs) includes XOR split type attribute.
     */
    @Test
    void export_gatewayTransition_includesSplitTypeAttribute() {
        // Build net with a gateway: p_start(1) → t_split → p_branch1
        //                                                  → p_branch2
        PetriNet gatewayNet = new PetriNet("gateway_net", "Gateway Net");

        Place start = new Place("p_start", "Start", 1);
        Place branch1 = new Place("p_branch1", "Branch1");
        Place branch2 = new Place("p_branch2", "Branch2");
        // We need at least one final place with no outgoing arcs
        // p_branch1 and p_branch2 have no outgoing arcs → both are final

        Transition splitTask = new Transition("t_split", "Split Task");

        gatewayNet.addPlace(start);
        gatewayNet.addPlace(branch1);
        gatewayNet.addPlace(branch2);
        gatewayNet.addTransition(splitTask);

        gatewayNet.addArc(new Arc("a1", start, splitTask));
        gatewayNet.addArc(new Arc("a2", splitTask, branch1));
        gatewayNet.addArc(new Arc("a3", splitTask, branch2));

        String xml = exporter.export(gatewayNet);

        assertTrue(xml.contains("split code=\"XOR\""), "Gateway transition should have XOR split code");
    }

    /**
     * Scenario 5: null model throws IllegalArgumentException.
     */
    @Test
    void export_nullModel_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> exporter.export(null),
                "Null model should throw IllegalArgumentException");
    }

    /**
     * Scenario 6: net with no initial place (no place with initialMarking > 0 and no
     * incoming arcs) throws YawlExportException.
     */
    @Test
    void export_noInitialPlace_throwsYawlExportException() {
        // p1 has no incoming arcs but initialMarking=0 → isInitialPlace()=false
        PetriNet noStartNet = new PetriNet("no_start", "No Start");

        Place p1 = new Place("p1", "P1");  // initialMarking=0 → not initial
        Place p2 = new Place("p2", "P2");
        Transition t1 = new Transition("t1", "T1");

        noStartNet.addPlace(p1);
        noStartNet.addPlace(p2);
        noStartNet.addTransition(t1);
        noStartNet.addArc(new Arc("a1", p1, t1));
        noStartNet.addArc(new Arc("a2", t1, p2));

        assertThrows(YawlExportException.class, () -> exporter.export(noStartNet),
                "Net without initial place should throw YawlExportException");
    }

    /**
     * Scenario 7: ProcessExporterFactory dispatches YAWL_SPEC to YawlSpecExporter correctly.
     */
    @Test
    void processExporterFactory_yawlSpecFormat_returnsValidXml() {
        String xml = ProcessExporterFactory.export(simplePetriNet, "YAWL_SPEC");

        assertNotNull(xml, "Factory YAWL_SPEC export should not return null");
        assertTrue(xml.contains("<specificationSet"),
                "Factory YAWL_SPEC output should contain specificationSet");
        assertTrue(ProcessExporterFactory.supportedFormats().contains("YAWL_SPEC"),
                "YAWL_SPEC should be listed in supported formats");
    }
}
