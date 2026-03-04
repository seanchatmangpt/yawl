/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.dspy.signature;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.yawlfoundation.yawl.dspy.DspyExecutionMetrics;
import org.yawlfoundation.yawl.dspy.DspyExecutionResult;
import org.yawlfoundation.yawl.dspy.DspyProgram;
import org.yawlfoundation.yawl.dspy.PythonDspyBridge;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for DSPy Signature system with Groq.
 *
 * <p>These tests require:
 * <ul>
 *   <li>GROQ_API_KEY environment variable</li>
 *   <li>GraalVM with GraalPy support</li>
 *   <li>dspy-ai Python package installed</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("DSPy Signature End-to-End Tests")
class DspySignatureEndToEndTest {

    private static PythonExecutionEngine engine;
    private static PythonDspyBridge bridge;

    @BeforeAll
    static void setupEngine() {
        // Skip if no GraalPy
        if (!isGraalPyAvailable()) {
            return;
        }
        engine = PythonExecutionEngine.builder()
            .contextPoolSize(2)
            .build();
        bridge = new PythonDspyBridge(engine);
    }

    @Test
    @DisplayName("Generate Python DSPy code from Java Signature")
    void testGeneratePythonSource() {
        Signature signature = Signature.builder()
            .description("Predict case outcome")
            .input("events", "list of workflow events", String.class)
            .input("duration_ms", "case duration in milliseconds", Long.class)
            .output("outcome", "predicted outcome: completed or failed", String.class)
            .output("confidence", "confidence score between 0 and 1", Double.class)
            .build();

        DspySignatureBridge codeGen = DspySignatureBridge.withGroq();
        String python = codeGen.generatePythonSource(signature, List.of());

        // Verify Python code structure
        assertTrue(python.contains("import dspy"), "Should import dspy");
        assertTrue(python.contains("groq"), "Should configure Groq LM");
        assertTrue(python.contains("PredictCaseOutcome(dspy.Signature)"),
            "Should generate signature class");
        assertTrue(python.contains("events = dspy.InputField"),
            "Should have events input field");
        assertTrue(python.contains("outcome = dspy.OutputField"),
            "Should have outcome output field");
        assertTrue(python.contains("confidence = dspy.OutputField"),
            "Should have confidence output field");

        System.out.println("Generated Python:\n" + python);
    }

    @Test
    @DisplayName("Generate Python with few-shot examples")
    void testGeneratePythonWithExamples() {
        Signature signature = Signature.builder()
            .description("Classify sentiment")
            .input("text", "text to classify", String.class)
            .output("sentiment", "positive, negative, or neutral", String.class)
            .build();

        List<Example> examples = List.of(
            Example.of(
                Map.of("text", "This is great!"),
                Map.of("sentiment", "positive")
            ),
            Example.of(
                Map.of("text", "This is terrible!"),
                Map.of("sentiment", "negative")
            )
        );

        DspySignatureBridge codeGen = DspySignatureBridge.withGroq();
        String python = codeGen.generatePythonSource(signature, examples);

        assertTrue(python.contains("_module.predict.demos"),
            "Should include few-shot examples");
        assertTrue(python.contains("This is great!"),
            "Should include first example");
        assertTrue(python.contains("positive"),
            "Should include first example label");

        System.out.println("Generated Python with examples:\n" + python);
    }

    @Test
    @DisplayName("Generate execution code")
    void testGenerateExecutionCode() {
        Signature signature = Signature.builder()
            .description("Answer question")
            .input("question", "the question to answer", String.class)
            .output("answer", "the answer", String.class)
            .build();

        DspySignatureBridge codeGen = DspySignatureBridge.withGroq();
        String execCode = codeGen.generateExecutionCode(signature,
            Map.of("question", "What is 2+2?"));

        assertTrue(execCode.contains("result = _module"),
            "Should call module");
        assertTrue(execCode.contains("question=\"What is 2+2?\""),
            "Should include input");
        assertTrue(execCode.contains("json.dumps"),
            "Should output JSON");

        System.out.println("Execution code:\n" + execCode);
    }

    @Test
    @DisplayName("Generate full program")
    void testGenerateFullProgram() {
        Signature signature = Signature.builder()
            .description("Summarize text")
            .input("text", "text to summarize", String.class)
            .output("summary", "brief summary", String.class)
            .build();

        DspySignatureBridge codeGen = DspySignatureBridge.withGroq();
        String fullProgram = codeGen.generateFullProgram(
            signature,
            List.of(),
            Map.of("text", "YAWL is a workflow language.")
        );

        assertTrue(fullProgram.contains("import dspy"),
            "Should have imports");
        assertTrue(fullProgram.contains("dspy.configure"),
            "Should configure LM");
        assertTrue(fullProgram.contains("class SummarizeText"),
            "Should have signature class");
        assertTrue(fullProgram.contains("result = _module"),
            "Should execute module");

        System.out.println("Full program:\n" + fullProgram);
    }

    @Test
    @DisplayName("Parse JSON output to SignatureResult")
    void testParseOutput() {
        Signature signature = Signature.builder()
            .description("Test")
            .input("in", "input", String.class)
            .output("outcome", "outcome", String.class)
            .output("confidence", "confidence", Double.class)
            .build();

        String jsonOutput = """
            {"outcome": "completed", "confidence": 0.95}
            """;

        DspySignatureBridge codeGen = DspySignatureBridge.withGroq();
        SignatureResult result = codeGen.parseOutput(jsonOutput, signature);

        assertEquals("completed", result.getString("outcome"));
        assertEquals(0.95, result.getDouble("confidence"), 0.01);
        assertTrue(result.isComplete());
    }

    @Test
    @DisplayName("Compile annotation-based signature template")
    void testAnnotationBasedSignature() {
        Signature sig = Signature.fromTemplate(CaseOutcomePredictor.class);

        assertEquals("Predict case outcome", sig.description());
        assertEquals(2, sig.inputs().size());
        assertEquals(2, sig.outputs().size());

        DspySignatureBridge codeGen = DspySignatureBridge.withGroq();
        String python = codeGen.generatePythonSource(sig, List.of());

        assertTrue(python.contains("case_events"));
        assertTrue(python.contains("duration_ms"));
        assertTrue(python.contains("outcome"));
        assertTrue(python.contains("confidence"));

        System.out.println("Annotation-based signature Python:\n" + python);
    }

    // ── Integration tests (require GROQ_API_KEY and GraalPy) ────────────────

    @Test
    @EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
    @DisplayName("End-to-end: Execute signature with Groq")
    void testEndToEndWithGroq() {
        if (!isGraalPyAvailable()) {
            System.out.println("Skipping: GraalPy not available");
            return;
        }

        // Define signature
        Signature signature = Signature.builder()
            .description("Answer a simple math question")
            .input("question", "a math question", String.class)
            .output("answer", "the numerical answer", String.class)
            .build();

        // Generate Python DSPy program
        DspySignatureBridge codeGen = DspySignatureBridge.withGroq();
        String pythonProgram = codeGen.generateFullProgram(
            signature,
            List.of(),
            Map.of("question", "What is 2 + 2? Answer with just the number.")
        );

        // Create DspyProgram
        DspyProgram program = DspyProgram.builder()
            .name("math-question")
            .source(pythonProgram)
            .description("Simple math question answering")
            .build();

        // Execute via PythonDspyBridge
        DspyExecutionResult result = bridge.execute(program, Map.of());

        assertNotNull(result);
        assertNotNull(result.output());

        System.out.println("Result: " + result.output());
        System.out.println("Metrics: " + result.metrics());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
    @DisplayName("End-to-end: Few-shot learning with examples")
    void testFewShotWithGroq() {
        if (!isGraalPyAvailable()) {
            System.out.println("Skipping: GraalPy not available");
            return;
        }

        // Define signature
        Signature signature = Signature.builder()
            .description("Classify sentiment as positive or negative")
            .input("text", "text to classify", String.class)
            .output("sentiment", "positive or negative", String.class)
            .build();

        // Few-shot examples
        List<Example> examples = List.of(
            Example.of(Map.of("text", "I love this product!"), Map.of("sentiment", "positive")),
            Example.of(Map.of("text", "This is awful."), Map.of("sentiment", "negative")),
            Example.of(Map.of("text", "Great service!"), Map.of("sentiment", "positive"))
        );

        // Generate Python DSPy program with examples
        DspySignatureBridge codeGen = DspySignatureBridge.withGroq();
        String pythonProgram = codeGen.generateFullProgram(
            signature,
            examples,
            Map.of("text", "This is fantastic!")
        );

        // Create and execute
        DspyProgram program = DspyProgram.builder()
            .name("sentiment-classifier")
            .source(pythonProgram)
            .description("Sentiment classification with few-shot")
            .build();

        DspyExecutionResult result = bridge.execute(program, Map.of());

        assertNotNull(result);
        String sentiment = (String) result.output().get("sentiment");
        assertNotNull(sentiment);

        System.out.println("Sentiment: " + sentiment);
        System.out.println("Raw output: " + result.trace());
    }

    // ── Test signature template ───────────────────────────────────────────────

    @SignatureCompiler.SigDef(description = "Predict case outcome")
    interface CaseOutcomePredictor extends SignatureTemplate {
        @SignatureCompiler.In(desc = "list of workflow events")
        String case_events();

        @SignatureCompiler.In(desc = "case duration in milliseconds")
        long duration_ms();

        @SignatureCompiler.Out(desc = "predicted outcome: completed or failed")
        String outcome();

        @SignatureCompiler.Out(desc = "confidence score between 0 and 1")
        double confidence();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isGraalPyAvailable() {
        try {
            Class.forName("org.graalvm.polyglot.Context");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
