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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.synthesis;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.OxigraphSparqlEngine;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineException;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineUnavailableException;

import java.util.List;
import java.util.Map;

/**
 * Chicago TDD test suite for workflow intent synthesis.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>WorkflowIntent creation and validation</li>
 *   <li>RDF serialization of intents</li>
 *   <li>Synthesizer availability checks</li>
 *   <li>Exception handling when engine unavailable</li>
 *   <li>SynthesisResult record validation</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class IntentSynthesizerTest extends TestCase {

    private OxigraphSparqlEngine engine;
    private IntentSynthesizer synthesizer;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Engine at non-existent port (guaranteed unavailable)
        engine = new OxigraphSparqlEngine("http://localhost:19876");
        synthesizer = new IntentSynthesizer(engine);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (engine != null) {
            engine.close();
        }
    }

    /**
     * Test: WorkflowIntent.sequential factory creates correct structure.
     */
    public void testWorkflowIntentSequentialFactory() {
        WorkflowIntent intent = WorkflowIntent.sequential("Approve", "Submit", "Review");

        assertEquals("Approve", intent.goal());
        assertEquals(2, intent.activities().size());
        assertEquals("Submit", intent.activities().get(0));
        assertEquals("Review", intent.activities().get(1));
        assertEquals(1, intent.wcpHints().size());
        assertEquals("WCP-1", intent.wcpHints().get(0));
    }

    /**
     * Test: WorkflowIntent rejects blank goal.
     */
    public void testWorkflowIntentRejectsBlankGoal() {
        try {
            new WorkflowIntent("", List.of(), List.of(), Map.of());
            fail("Expected IllegalArgumentException for blank goal");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("blank"));
        }
    }

    /**
     * Test: IntentRdfSerializer produces valid Turtle.
     */
    public void testIntentRdfSerializerProducesTurtle() {
        WorkflowIntent intent = WorkflowIntent.sequential("Approve", "Submit", "Review");
        String turtle = IntentRdfSerializer.toTurtle(intent);

        assertNotNull(turtle);
        assertTrue(turtle.contains("@prefix intent:"));
        assertTrue(turtle.contains("intent:WorkflowIntent"));
        assertTrue(turtle.contains("Approve"));
        assertTrue(turtle.contains("Submit"));
        assertTrue(turtle.contains("Review"));
    }

    /**
     * Test: IntentRdfSerializer escapes special characters.
     */
    public void testIntentRdfSerializerEscapesQuotes() {
        WorkflowIntent intent = new WorkflowIntent(
            "Approve \"urgent\" cases",
            List.of(),
            List.of(),
            Map.of()
        );
        String turtle = IntentRdfSerializer.toTurtle(intent);

        // Quotes should be escaped in Turtle
        assertTrue(turtle.contains("\\\""));
    }

    /**
     * Test: IntentRdfSerializer IRI is deterministic for same goal.
     */
    public void testIntentIriIsDeterministic() {
        WorkflowIntent intent1 = new WorkflowIntent("Same Goal", List.of(), List.of(), Map.of());
        WorkflowIntent intent2 = new WorkflowIntent("Same Goal", List.of(), List.of(), Map.of());

        String iri1 = IntentRdfSerializer.intentIri(intent1);
        String iri2 = IntentRdfSerializer.intentIri(intent2);

        // Same goal hash appears in both IRIs (IRI format: intent_<epochMs>_<goalHash>)
        String expectedHash = String.valueOf(Math.abs("Same Goal".hashCode()));
        assertTrue(iri1.contains(expectedHash));
        assertTrue(iri2.contains(expectedHash));
    }

    /**
     * Test: canSynthesize returns false when engine unavailable.
     */
    public void testCanSynthesizeReturnsFalseWhenEngineDown() {
        assertFalse("Engine at :19876 should be unavailable", synthesizer.canSynthesize());
    }

    /**
     * Test: synthesize throws SparqlEngineException when engine unavailable.
     */
    public void testSynthesizeThrowsWhenEngineUnavailable() {
        WorkflowIntent intent = WorkflowIntent.sequential("Test", "A", "B");

        try {
            synthesizer.synthesize(intent);
            fail("Expected SparqlEngineException");
        } catch (SparqlEngineUnavailableException e) {
            // Expected
            assertTrue(e.getMessage().contains("oxigraph"));
        } catch (SparqlEngineException e) {
            // Also acceptable (parent exception)
        }
    }

    /**
     * Test: SynthesisResult record holds values correctly.
     */
    public void testSynthesisResultRecord() {
        String specXml = "<yawl/>";
        java.time.Duration elapsed = java.time.Duration.ZERO;
        java.util.List<String> patterns = java.util.List.of("WCP-1");

        SynthesisResult result = new SynthesisResult(specXml, null, patterns, elapsed, true);

        assertEquals(specXml, result.specXml());
        assertNull(result.soundnessReport());
        assertEquals(patterns, result.wcpPatternsUsed());
        assertEquals(elapsed, result.elapsed());
        assertTrue(result.successful());
    }

    /**
     * Test: SynthesisConstructQueries are non-empty.
     */
    public void testSynthesisConstructQueriesAreNonEmpty() {
        assertNotNull(SynthesisConstructQueries.SELECT_WCP_FOR_INTENT);
        assertFalse(SynthesisConstructQueries.SELECT_WCP_FOR_INTENT.isEmpty());

        assertNotNull(SynthesisConstructQueries.CONSTRUCT_SEQUENTIAL_NET);
        assertFalse(SynthesisConstructQueries.CONSTRUCT_SEQUENTIAL_NET.isEmpty());

        assertNotNull(SynthesisConstructQueries.CONSTRUCT_YAWL_SPEC);
        assertFalse(SynthesisConstructQueries.CONSTRUCT_YAWL_SPEC.isEmpty());
    }

    /**
     * Test: WorkflowIntent with null activities defaults to empty list.
     */
    public void testWorkflowIntentHandlesNullCollections() {
        WorkflowIntent intent = new WorkflowIntent("Goal", null, null, null);

        assertNotNull(intent.activities());
        assertTrue(intent.activities().isEmpty());

        assertNotNull(intent.wcpHints());
        assertTrue(intent.wcpHints().isEmpty());

        assertNotNull(intent.constraints());
        assertTrue(intent.constraints().isEmpty());
    }

    /**
     * Test: SynthesisResult.isSoundWorkflow checks both success and soundness.
     */
    public void testSynthesisResultSoundnessCheck() {
        // Create a sound report
        var report = new org.yawlfoundation.yawl.integration.verification.VerificationReport(
            java.util.List.of(),
            true,   // isSound
            0,      // deadlockCount
            0,      // warningCount
            0,      // infoCount
            "Sound",
            java.time.Duration.ZERO
        );

        SynthesisResult result = new SynthesisResult("<xml/>", report, List.of("WCP-1"),
            java.time.Duration.ZERO, true);

        assertTrue(result.isSoundWorkflow());

        // Unsuccessful synthesis
        SynthesisResult unsuccessful = new SynthesisResult("<xml/>", report, List.of("WCP-1"),
            java.time.Duration.ZERO, false);

        assertFalse(unsuccessful.isSoundWorkflow());
    }
}
