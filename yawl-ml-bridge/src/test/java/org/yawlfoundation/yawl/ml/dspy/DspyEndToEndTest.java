/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.ml.dspy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for DSPy via Erlang → Rust → Python bridge.
 *
 * <p>Tests require:
 * <ul>
 *   <li>Python 3.x with dspy==3.1.3 installed</li>
 *   <li>GROQ_API_KEY environment variable for integration tests</li>
 *   <li>Erlang node running (yawl_ml@localhost)</li>
 * </ul>
 */
@DisplayName("DSPy End-to-End Tests")
class DspyEndToEndTest {

    // Bridge check moved to individual integration tests

    @Test
    @DisplayName("Create signature with builder")
    void testSignatureBuilder() {
        Signature signature = Signature.builder()
            .description("Predict case outcome")
            .input("events", "workflow events", String.class)
            .input("duration_ms", Long.class)
            .output("outcome", String.class)
            .output("confidence", Double.class)
            .build();

        assertThat(signature.description()).isEqualTo("Predict case outcome");
        assertThat(signature.inputs()).containsKeys("events", "duration_ms");
        assertThat(signature.outputs()).containsKeys("outcome", "confidence");

        String json = signature.toJson();
        assertThat(json).contains("\"description\":\"Predict case outcome\"");
        assertThat(json).contains("\"inputs\":[");
        assertThat(json).contains("\"outputs\":[");
    }

    @Test
    @DisplayName("Create program with Groq provider")
    void testProgramBuilder() {
        Signature signature = Signature.builder()
            .description("Answer question")
            .input("question", String.class)
            .output("answer", String.class)
            .build();

        DspyProgram program = DspyProgram.create(signature)
            .withGroq()
            .withTimeout(java.time.Duration.ofSeconds(60))
            .build();

        assertThat(program.provider()).isEqualTo("groq");
        assertThat(program.signature()).isEqualTo(signature);
    }

    @Test
    @DisplayName("Add few-shot examples")
    void testFewShotExamples() {
        Signature signature = Signature.builder()
            .description("Classify sentiment")
            .input("text", String.class)
            .output("sentiment", String.class)
            .build();

        DspyProgram program = DspyProgram.create(signature)
            .withGroq()
            .withExample("text", "This is great!", "sentiment", "positive")
            .withExample("text", "This is terrible!", "sentiment", "negative")
            .build();

        // Verify program is built successfully
        assertThat(program).isNotNull();
    }

    @Test
    @DisplayName("Signature to JSON format")
    void testSignatureJsonFormat() {
        Signature signature = Signature.builder()
            .description("Summarize text")
            .input("text", String.class)
            .output("summary", String.class)
            .build();

        String json = signature.toJson();

        // Verify JSON structure
        assertThat(json).startsWith("{");
        assertThat(json).endsWith("}");
        assertThat(json).contains("\"description\":");
        assertThat(json).contains("\"inputs\":");
        assertThat(json).contains("\"outputs\":");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
    @DisplayName("End-to-end: Predict with Groq")
    void testEndToEndWithGroq() throws DspyException {
        // Bridge MUST be available - fail fast if not
        assertThat(isBridgeAvailable())
            .withFailMessage("ML Bridge not available - start Erlang node first")
            .isTrue();

        Signature signature = Signature.builder()
            .description("Answer a simple math question")
            .input("question", String.class)
            .output("answer", String.class)
            .build();

        DspyProgram program = DspyProgram.create(signature)
            .withGroq()
            .build();

        Map<String, Object> result = program.predict(
            Map.of("question", "What is 2 + 2? Answer with just the number.")
        );

        assertThat(result).isNotNull();
        assertThat(result).containsKey("answer");

        System.out.println("Answer: " + result.get("answer"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
    @DisplayName("End-to-end: Few-shot learning")
    void testFewShotWithGroq() throws DspyException {
        // Bridge MUST be available - fail fast if not
        assertThat(isBridgeAvailable())
            .withFailMessage("ML Bridge not available - start Erlang node first")
            .isTrue();

        Signature signature = Signature.builder()
            .description("Classify sentiment as positive or negative")
            .input("text", String.class)
            .output("sentiment", String.class)
            .build();

        DspyProgram program = DspyProgram.create(signature)
            .withGroq()
            .withExample("text", "I love this product!", "sentiment", "positive")
            .withExample("text", "This is awful.", "sentiment", "negative")
            .build();

        Map<String, Object> result = program.predict(
            Map.of("text", "This is fantastic!")
        );

        assertThat(result).isNotNull();
        assertThat(result).containsKey("sentiment");

        System.out.println("Sentiment: " + result.get("sentiment"));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static boolean isBridgeAvailable() {
        try {
            return MlBridgeClient.getDefault().isHealthy();
        } catch (Exception e) {
            return false;
        }
    }
}
