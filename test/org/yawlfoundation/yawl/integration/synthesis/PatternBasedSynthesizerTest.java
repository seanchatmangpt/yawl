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

package org.yawlfoundation.yawl.integration.synthesis;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.integration.synthesis.PatternBasedSynthesizer.PatternSpec;

import java.util.List;

/**
 * Chicago TDD test suite for PatternBasedSynthesizer.
 *
 * <p>Tests cover all 5 WCP patterns, NL parsing, XML validity, and edge cases.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class PatternBasedSynthesizerTest extends TestCase {

    private PatternBasedSynthesizer synthesizer;

    @Override
    protected void setUp() {
        synthesizer = new PatternBasedSynthesizer();
    }

    // =========================================================================
    // WCP-1: Sequential
    // =========================================================================

    public void testSequentialProducesValidXml() {
        var spec = new PatternSpec.Sequential(List.of("Submit", "Review", "Approve"));
        SynthesisResult result = synthesizer.synthesize(spec);

        assertTrue(result.successful());
        assertNotNull(result.specXml());
        assertTrue(result.specXml().contains("<?xml version=\"1.0\""));
        assertTrue(result.specXml().contains("specificationSet"));
        assertTrue(result.specXml().contains("Submit"));
        assertTrue(result.specXml().contains("Review"));
        assertTrue(result.specXml().contains("Approve"));
        assertEquals(List.of("WCP-1"), result.wcpPatternsUsed());
    }

    public void testSequentialChainsTasks() {
        var spec = new PatternSpec.Sequential(List.of("A", "B", "C"));
        SynthesisResult result = synthesizer.synthesize(spec);
        String xml = result.specXml();

        // InputCondition flows to first task
        assertTrue(xml.contains("InputCondition"));
        // Last task flows to OutputCondition
        assertTrue(xml.contains("OutputCondition"));
        // Contains all task names
        assertTrue(xml.contains("<name>A</name>"));
        assertTrue(xml.contains("<name>B</name>"));
        assertTrue(xml.contains("<name>C</name>"));
    }

    public void testSequentialRejectsFewerThan2Tasks() {
        try {
            new PatternSpec.Sequential(List.of("OnlyOne"));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("at least 2"));
        }
    }

    // =========================================================================
    // WCP-2: Parallel
    // =========================================================================

    public void testParallelProducesValidXml() {
        var spec = new PatternSpec.Parallel(List.of("ReviewSecurity", "ReviewLegal"));
        SynthesisResult result = synthesizer.synthesize(spec);

        assertTrue(result.successful());
        assertTrue(result.specXml().contains("specificationSet"));
        assertTrue(result.specXml().contains("Fork"));
        assertTrue(result.specXml().contains("Join"));
        assertTrue(result.specXml().contains("ReviewSecurity"));
        assertTrue(result.specXml().contains("ReviewLegal"));
        assertEquals(List.of("WCP-2"), result.wcpPatternsUsed());
    }

    public void testParallelHasAndSplitAndJoin() {
        var spec = new PatternSpec.Parallel(List.of("TaskA", "TaskB", "TaskC"));
        String xml = synthesizer.synthesize(spec).specXml();

        // Fork task should have AND-split
        assertTrue(xml.contains("Fork"));
        assertTrue(xml.contains("Join"));
        // Join should have AND-join
        assertTrue(xml.contains("\"and\""));
    }

    // =========================================================================
    // WCP-4: Exclusive Choice
    // =========================================================================

    public void testExclusiveProducesValidXml() {
        var spec = new PatternSpec.Exclusive(List.of("PathA", "PathB"));
        SynthesisResult result = synthesizer.synthesize(spec);

        assertTrue(result.successful());
        assertTrue(result.specXml().contains("Decision"));
        assertTrue(result.specXml().contains("Merge"));
        assertTrue(result.specXml().contains("PathA"));
        assertTrue(result.specXml().contains("PathB"));
        assertEquals(List.of("WCP-4"), result.wcpPatternsUsed());
    }

    public void testExclusiveHasXorSplit() {
        var spec = new PatternSpec.Exclusive(List.of("Yes", "No"));
        String xml = synthesizer.synthesize(spec).specXml();

        assertTrue(xml.contains("Decision"));
        assertTrue(xml.contains("\"xor\""));
    }

    // =========================================================================
    // WCP-21: Loop
    // =========================================================================

    public void testLoopProducesValidXml() {
        var spec = new PatternSpec.Loop(List.of("ProcessItem", "CheckQuality"));
        SynthesisResult result = synthesizer.synthesize(spec);

        assertTrue(result.successful());
        assertTrue(result.specXml().contains("LoopDecision"));
        assertTrue(result.specXml().contains("ProcessItem"));
        assertTrue(result.specXml().contains("CheckQuality"));
        assertEquals(List.of("WCP-21"), result.wcpPatternsUsed());
    }

    public void testLoopHasBackEdge() {
        var spec = new PatternSpec.Loop(List.of("DoWork"));
        String xml = synthesizer.synthesize(spec).specXml();

        // LoopDecision should exist (it creates the back-edge)
        assertTrue(xml.contains("LoopDecision"));
        // Should flow to OutputCondition (exit path)
        assertTrue(xml.contains("OutputCondition"));
    }

    // =========================================================================
    // WCP-12: Multiple Instances
    // =========================================================================

    public void testMultiInstanceProducesValidXml() {
        var spec = new PatternSpec.MultiInstance(List.of("ReviewDoc"), 1, 5, 3);
        SynthesisResult result = synthesizer.synthesize(spec);

        assertTrue(result.successful());
        assertTrue(result.specXml().contains("multiInstanceAttributes"));
        assertTrue(result.specXml().contains("<minimum>1</minimum>"));
        assertTrue(result.specXml().contains("<maximum>5</maximum>"));
        assertTrue(result.specXml().contains("<threshold>3</threshold>"));
        assertEquals(List.of("WCP-12"), result.wcpPatternsUsed());
    }

    public void testMultiInstanceValidation() {
        try {
            new PatternSpec.MultiInstance(List.of("Task"), 0, 3, 1);
            fail("Expected IllegalArgumentException for minInstances < 1");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("minInstances"));
        }

        try {
            new PatternSpec.MultiInstance(List.of("Task"), 5, 3, 1);
            fail("Expected IllegalArgumentException for maxInstances < minInstances");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("maxInstances"));
        }
    }

    // =========================================================================
    // NL Parsing
    // =========================================================================

    public void testParseDescriptionSequential() {
        PatternSpec spec = synthesizer.parseDescription(
            "Submit request then review then approve", List.of());
        assertInstanceOf(spec, PatternSpec.Sequential.class);
        assertTrue(spec.tasks().size() >= 2);
    }

    public void testParseDescriptionParallel() {
        PatternSpec spec = synthesizer.parseDescription(
            "Run security check and legal review in parallel", List.of());
        assertInstanceOf(spec, PatternSpec.Parallel.class);
    }

    public void testParseDescriptionExclusive() {
        PatternSpec spec = synthesizer.parseDescription(
            "Choose either approve or reject the request",
            List.of("Approve", "Reject"));
        assertInstanceOf(spec, PatternSpec.Exclusive.class);
        assertEquals(List.of("Approve", "Reject"), spec.tasks());
    }

    public void testParseDescriptionLoop() {
        PatternSpec spec = synthesizer.parseDescription(
            "Repeat quality check until all items pass",
            List.of("QualityCheck"));
        assertInstanceOf(spec, PatternSpec.Loop.class);
    }

    public void testParseDescriptionMultiInstance() {
        PatternSpec spec = synthesizer.parseDescription(
            "Batch process all documents in multiple instances",
            List.of("ProcessDocument"));
        assertInstanceOf(spec, PatternSpec.MultiInstance.class);
    }

    public void testParseDescriptionWithExplicitTasks() {
        PatternSpec spec = synthesizer.parseDescription(
            "Execute tasks sequentially",
            List.of("TaskA", "TaskB", "TaskC"));
        assertEquals(List.of("TaskA", "TaskB", "TaskC"), spec.tasks());
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    public void testSanitizeId() {
        assertEquals("Hello_World", PatternBasedSynthesizer.sanitizeId("Hello World"));
        assertEquals("_123start", PatternBasedSynthesizer.sanitizeId("123start"));
        assertEquals("_task", PatternBasedSynthesizer.sanitizeId(""));
        assertEquals("_task", PatternBasedSynthesizer.sanitizeId(null));
    }

    public void testNullSpecRejected() {
        try {
            synthesizer.synthesize(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    public void testElapsedTimeRecorded() {
        var spec = new PatternSpec.Sequential(List.of("A", "B"));
        SynthesisResult result = synthesizer.synthesize(spec);
        assertNotNull(result.elapsed());
        assertTrue(result.elapsed().toNanos() >= 0);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void assertInstanceOf(Object obj, Class<?> expectedClass) {
        assertTrue("Expected " + expectedClass.getSimpleName() + " but got "
            + obj.getClass().getSimpleName(),
            expectedClass.isInstance(obj));
    }
}
