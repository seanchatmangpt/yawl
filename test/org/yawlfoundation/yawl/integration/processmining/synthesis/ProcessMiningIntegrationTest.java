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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.processmining.pnml.PnmlParseException;
import org.yawlfoundation.yawl.integration.processmining.pnml.PnmlParser;
import org.yawlfoundation.yawl.integration.processmining.pnml.PnmlProcess;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the complete PNML → YAWL synthesis pipeline.
 * Tests end-to-end workflows: parse PNML, validate, synthesize YAWL, assess conformance.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("Process Mining Integration Tests")
class ProcessMiningIntegrationTest {

    @Test
    @DisplayName("Complete pipeline: PNML parse -> YAWL synthesis -> conformance score")
    void testCompleteWorkflow_order() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="order_process" name="Order Processing">
                <place id="p_start">
                  <name><text>start</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p_received">
                  <name><text>order_received</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <place id="p_validated">
                  <name><text>order_validated</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <place id="p_picked">
                  <name><text>order_picked</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <place id="p_shipped">
                  <name><text>order_shipped</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <place id="p_end">
                  <name><text>end</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t_receive">
                  <name><text>Receive Order</text></name>
                </transition>
                <transition id="t_validate">
                  <name><text>Validate Order</text></name>
                </transition>
                <transition id="t_pick">
                  <name><text>Pick Items</text></name>
                </transition>
                <transition id="t_ship">
                  <name><text>Ship Order</text></name>
                </transition>
                <arc id="a1" source="p_start" target="t_receive" />
                <arc id="a2" source="t_receive" target="p_received" />
                <arc id="a3" source="p_received" target="t_validate" />
                <arc id="a4" source="t_validate" target="p_validated" />
                <arc id="a5" source="p_validated" target="t_pick" />
                <arc id="a6" source="t_pick" target="p_picked" />
                <arc id="a7" source="p_picked" target="t_ship" />
                <arc id="a8" source="t_ship" target="p_shipped" />
                <arc id="a9" source="p_shipped" target="p_end" />
              </net>
            </pnml>
            """;

        // 1. Parse PNML
        PnmlParser parser = new PnmlParser();
        PnmlProcess process = parser.parse(pnmlXml);

        // Validation
        assertNotNull(process);
        assertEquals("order_process", process.id());
        assertEquals("Order Processing", process.name());
        assertEquals(6, process.places().size());
        assertEquals(4, process.transitions().size());
        assertEquals(9, process.arcs().size());
        assertTrue(process.isValid());

        // 2. Synthesize YAWL
        YawlSpecSynthesizer synthesizer = new YawlSpecSynthesizer(
            "http://example.com/order-process",
            "Order Processing Workflow"
        );
        SynthesisResult result = synthesizer.synthesizeWithConformance(process);

        // Validation
        assertNotNull(result);
        assertNotNull(result.getYawlXml());
        assertTrue(result.isValidXml());
        assertTrue(result.getYawlXml().contains("Receive_Order"));
        assertTrue(result.getYawlXml().contains("Validate_Order"));
        assertTrue(result.getYawlXml().contains("Pick_Items"));
        assertTrue(result.getYawlXml().contains("Ship_Order"));

        // 3. Conformance score
        ConformanceScore score = result.getConformanceScore();
        assertNotNull(score);
        assertTrue(score.fitness() >= 0.0);
        assertTrue(score.precision() >= 0.0);
        assertTrue(score.generalization() >= 0.0);

        // Linear process should have reasonable conformance
        assertTrue(score.precision() >= 0.6, "Linear workflow should have decent precision");
    }

    @Test
    @DisplayName("Workflow with split/join converges to single output")
    void testCompleteWorkflow_parallelGateway() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="parallel" name="Parallel Process">
                <place id="p_start">
                  <name><text>start</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p_after_split">
                  <name><text>after_split</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <place id="p_task_a_done">
                  <name><text>task_a_done</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <place id="p_task_b_done">
                  <name><text>task_b_done</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <place id="p_joined">
                  <name><text>joined</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <place id="p_end">
                  <name><text>end</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t_split">
                  <name><text>Start Parallel</text></name>
                </transition>
                <transition id="t_task_a">
                  <name><text>Task A</text></name>
                </transition>
                <transition id="t_task_b">
                  <name><text>Task B</text></name>
                </transition>
                <transition id="t_join">
                  <name><text>Join</text></name>
                </transition>
                <arc id="a1" source="p_start" target="t_split" />
                <arc id="a2" source="t_split" target="p_after_split" />
                <arc id="a3" source="p_after_split" target="t_task_a" />
                <arc id="a4" source="p_after_split" target="t_task_b" />
                <arc id="a5" source="t_task_a" target="p_task_a_done" />
                <arc id="a6" source="t_task_b" target="p_task_b_done" />
                <arc id="a7" source="p_task_a_done" target="t_join" />
                <arc id="a8" source="p_task_b_done" target="t_join" />
                <arc id="a9" source="t_join" target="p_joined" />
                <arc id="a10" source="p_joined" target="p_end" />
              </net>
            </pnml>
            """;

        // Parse and synthesize
        PnmlParser parser = new PnmlParser();
        PnmlProcess process = parser.parse(pnmlXml);
        assertTrue(process.isValid());

        YawlSpecSynthesizer synthesizer = new YawlSpecSynthesizer(
            "http://example.com/parallel",
            "Parallel Workflow"
        );
        SynthesisResult result = synthesizer.synthesizeWithConformance(process);

        assertNotNull(result);
        assertTrue(result.isValidXml());
        assertEquals(4, result.tasksGenerated());
        assertTrue(result.getYawlXml().contains("Start_Parallel"));
        assertTrue(result.getYawlXml().contains("Task_A"));
        assertTrue(result.getYawlXml().contains("Task_B"));
        assertTrue(result.getYawlXml().contains("Join"));
    }

    @Test
    @DisplayName("Process mining tool export (ProM-like PNML) synthesizes correctly")
    void testCompleteWorkflow_proMExport() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml xmlns="http://www.pnml.org/version-2009-05-13/grammar/pnml">
              <net id="invoice_process" name="Invoice Processing">
                <place id="source_place">
                  <name><text>Source</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="sink_place">
                  <name><text>Sink</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <place id="p1">
                  <name><text>p1</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                <transition id="t1">
                  <name><text>Create Invoice</text></name>
                </transition>
                <transition id="t2">
                  <name><text>Review Invoice</text></name>
                </transition>
                <transition id="t3">
                  <name><text>Approve Payment</text></name>
                </transition>
                <arc id="a1" source="source_place" target="t1" />
                <arc id="a2" source="t1" target="p1" />
                <arc id="a3" source="p1" target="t2" />
                <arc id="a4" source="t2" target="p1" />
                <arc id="a5" source="p1" target="t3" />
                <arc id="a6" source="t3" target="sink_place" />
              </net>
            </pnml>
            """;

        PnmlParser parser = new PnmlParser();
        PnmlProcess process = parser.parse(pnmlXml);
        assertTrue(process.isValid());

        YawlSpecSynthesizer synthesizer = new YawlSpecSynthesizer(
            "http://example.com/invoice",
            "Invoice Processing System"
        );
        SynthesisResult result = synthesizer.synthesizeWithConformance(process);

        assertNotNull(result);
        assertEquals(3, result.tasksGenerated());
        assertTrue(result.getYawlXml().contains("Create_Invoice"));
        assertTrue(result.getYawlXml().contains("Review_Invoice"));
        assertTrue(result.getYawlXml().contains("Approve_Payment"));

        // Check conformance score exists
        ConformanceScore score = result.getConformanceScore();
        assertNotNull(score.summary());
    }

    @Test
    @DisplayName("Low-quality process (disconnected) fails validation early")
    void testCompleteWorkflow_disconnectedProcess() throws PnmlParseException {
        String pnmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="bad" name="Disconnected">
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
                <!-- Arc from p1 to t1, but t2 is disconnected -->
                <arc id="a1" source="p1" target="t1" />
                <arc id="a2" source="t1" target="p2" />
                <!-- p3 and t2 are orphaned -->
              </net>
            </pnml>
            """;

        PnmlParser parser = new PnmlParser();
        PnmlProcess process = parser.parse(pnmlXml);

        // Process is invalid due to orphaned nodes
        assertFalse(process.isValid());

        // Synthesis should fail
        YawlSpecSynthesizer synthesizer = new YawlSpecSynthesizer(
            "http://example.com/bad",
            "Bad Process"
        );

        assertThrows(IllegalArgumentException.class,
            () -> synthesizer.synthesize(process));
    }

    @Test
    @DisplayName("Process with many transitions generates corresponding YAWL tasks")
    void testCompleteWorkflow_largeProcess() throws PnmlParseException {
        // Generate a process with 10 sequential tasks
        StringBuilder pnmlBuilder = new StringBuilder();
        pnmlBuilder.append("""
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="large" name="Large">
            """);

        // Create 11 places (start, 10 transitions need 11 places)
        pnmlBuilder.append("""
            <place id="p_start">
              <name><text>start</text></name>
              <initialMarking><text>1</text></initialMarking>
            </place>
            """);

        for (int i = 1; i < 11; i++) {
            pnmlBuilder.append(String.format("""
                <place id="p_%d">
                  <name><text>p_%d</text></name>
                  <initialMarking><text>0</text></initialMarking>
                </place>
                """, i, i));
        }

        pnmlBuilder.append("""
            <place id="p_end">
              <name><text>end</text></name>
              <initialMarking><text>0</text></initialMarking>
            </place>
            """);

        // Create 10 transitions
        for (int i = 1; i <= 10; i++) {
            pnmlBuilder.append(String.format("""
                <transition id="t_%d">
                  <name><text>Task %d</text></name>
                </transition>
                """, i, i));
        }

        // Create arcs
        pnmlBuilder.append(String.format(
            """<arc id="a_0" source="p_start" target="t_1" />
               <arc id="a_1" source="t_1" target="p_1" />
               """
        ));

        for (int i = 2; i <= 10; i++) {
            pnmlBuilder.append(String.format(
                """<arc id="a_%d" source="p_%d" target="t_%d" />
                   <arc id="a_%d_b" source="t_%d" target="p_%d" />
                   """, i - 1, i - 2, i, i - 1, i, i - 1));
        }

        pnmlBuilder.append(String.format(
            """<arc id="a_final_src" source="p_9" target="p_end" />
               </net>
             </pnml>
             """
        ));

        String pnmlXml = pnmlBuilder.toString();

        PnmlParser parser = new PnmlParser();
        PnmlProcess process = parser.parse(pnmlXml);
        assertTrue(process.isValid());

        YawlSpecSynthesizer synthesizer = new YawlSpecSynthesizer(
            "http://example.com/large",
            "Large Workflow"
        );
        SynthesisResult result = synthesizer.synthesizeWithConformance(process);

        // Should generate 10 tasks
        assertEquals(10, result.tasksGenerated());
        assertNotNull(result.getConformanceScore());
    }

    @Test
    @DisplayName("End-to-end result summary is informative")
    void testCompleteWorkflow_resultSummary() throws PnmlParseException {
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

        YawlSpecSynthesizer synthesizer = new YawlSpecSynthesizer(
            "http://example.com/test",
            "Test"
        );
        SynthesisResult result = synthesizer.synthesizeWithConformance(process);

        String summary = result.summary();
        assertNotNull(summary);
        assertTrue(summary.contains("tasks"));
        assertTrue(summary.contains("conditions"));
        assertTrue(summary.contains("Fitness"));
        assertTrue(summary.contains("Precision"));
    }
}
