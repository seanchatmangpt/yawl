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

package org.yawlfoundation.yawl.datamodelling.llm;

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.datamodelling.pipeline.InferenceResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LlmConfig and LlmRefinementRequest/Result immutable records.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>LlmConfig validation and builder pattern</li>
 *   <li>Model type parsing and defaults</li>
 *   <li>Temperature and maxTokens constraints</li>
 *   <li>LlmRefinementRequest immutability</li>
 *   <li>LlmRefinementResult builder and success detection</li>
 * </ul>
 *
 * <p>Note: DataModellingLlmRefiner requires an actual DataModellingBridge instance
 * with WASM binaries loaded. To test the refiner, integration tests must be used
 * with a real bridge (see integration test suite). Unit tests here focus on
 * configuration and data models only.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class DataModellingLlmRefinerTest {

    @Test
    public void testLlmConfigBuilderWithDefaults() {
        LlmConfig config = LlmConfig.builder().build();

        assertNotNull(config);
        assertEquals(LlmConfig.LlmMode.OFFLINE, config.getMode());
        assertEquals("qwen2.5-coder", config.getModel());
        assertEquals(0.7, config.getTemperature());
        assertEquals(2048, config.getMaxTokens());
        assertEquals(30, config.getTimeoutSeconds());
        assertEquals("http://localhost:11434", config.getBaseUrl());
        assertTrue(config.getEnableFallback());
    }

    @Test
    public void testLlmConfigBuilderWithCustomValues() {
        LlmConfig config = LlmConfig.builder()
                .mode(LlmConfig.LlmMode.ONLINE)
                .model("llama2-13b")
                .temperature(0.5)
                .maxTokens(4096)
                .timeoutSeconds(60)
                .baseUrl("http://192.168.1.100:11434")
                .enableFallback(false)
                .build();

        assertEquals(LlmConfig.LlmMode.ONLINE, config.getMode());
        assertEquals("llama2-13b", config.getModel());
        assertEquals(0.5, config.getTemperature());
        assertEquals(4096, config.getMaxTokens());
        assertEquals(60, config.getTimeoutSeconds());
        assertEquals("http://192.168.1.100:11434", config.getBaseUrl());
        assertFalse(config.getEnableFallback());
    }

    @Test
    public void testLlmConfigValidationRejectsInvalidTemperature() {
        assertThrows(IllegalArgumentException.class, () -> {
            LlmConfig.builder()
                    .temperature(3.0) // Invalid: > 2.0
                    .build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            LlmConfig.builder()
                    .temperature(-0.5) // Invalid: < 0.0
                    .build();
        });
    }

    @Test
    public void testLlmConfigValidationRejectsInvalidMaxTokens() {
        assertThrows(IllegalArgumentException.class, () -> {
            LlmConfig.builder()
                    .maxTokens(-1)
                    .build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            LlmConfig.builder()
                    .maxTokens(0)
                    .build();
        });
    }

    @Test
    public void testLlmConfigValidationRejectsInvalidTimeoutSeconds() {
        assertThrows(IllegalArgumentException.class, () -> {
            LlmConfig.builder()
                    .timeoutSeconds(-1)
                    .build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            LlmConfig.builder()
                    .timeoutSeconds(0)
                    .build();
        });
    }

    @Test
    public void testLlmModeFromValue() {
        assertEquals(LlmConfig.LlmMode.OFFLINE, LlmConfig.LlmMode.fromValue("offline"));
        assertEquals(LlmConfig.LlmMode.OFFLINE, LlmConfig.LlmMode.fromValue("OFFLINE"));
        assertEquals(LlmConfig.LlmMode.ONLINE, LlmConfig.LlmMode.fromValue("online"));
        assertEquals(LlmConfig.LlmMode.ONLINE, LlmConfig.LlmMode.fromValue("ONLINE"));

        assertThrows(IllegalArgumentException.class, () -> {
            LlmConfig.LlmMode.fromValue("invalid");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            LlmConfig.LlmMode.fromValue(null);
        });
    }

    @Test
    public void testModelTypeFromModelId() {
        assertEquals(LlmConfig.ModelType.QWEN_2_5_CODER, LlmConfig.ModelType.fromModelId("qwen2.5-coder"));
        assertEquals(LlmConfig.ModelType.LLAMA2_7B, LlmConfig.ModelType.fromModelId("llama2-7b"));
        assertEquals(LlmConfig.ModelType.LLAMA2_13B, LlmConfig.ModelType.fromModelId("llama2-13b"));
        assertEquals(LlmConfig.ModelType.MISTRAL_7B, LlmConfig.ModelType.fromModelId("mistral-7b"));
        assertEquals(LlmConfig.ModelType.CLAUDE, LlmConfig.ModelType.fromModelId("claude-opus-4-6"));

        assertEquals(LlmConfig.ModelType.CUSTOM, LlmConfig.ModelType.fromModelId("unknown-model"));
        assertEquals(LlmConfig.ModelType.CUSTOM, LlmConfig.ModelType.fromModelId(null));
    }

    @Test
    public void testLlmRefinementRequestBuilder() {
        LlmConfig config = LlmConfig.builder().build();
        String[] samples = {"sample1", "sample2"};
        String[] objectives = {"improve naming", "detect constraints"};

        LlmRefinementRequest request = LlmRefinementRequest.builder()
                .schema("{\"tables\": []}")
                .samples(samples)
                .objectives(objectives)
                .context("documentation")
                .config(config)
                .build();

        assertNotNull(request);
        assertEquals("{\"tables\": []}", request.schema());
        assertEquals(2, request.samples().length);
        assertEquals(2, request.objectives().length);
        assertEquals("documentation", request.context());
        assertEquals(config, request.config());
    }

    @Test
    public void testLlmRefinementRequestValidation() {
        LlmConfig config = LlmConfig.builder().build();

        assertThrows(NullPointerException.class, () -> {
            LlmRefinementRequest.builder()
                    .schema(null)
                    .config(config)
                    .build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            LlmRefinementRequest.builder()
                    .schema("")
                    .config(config)
                    .build();
        });

        assertThrows(NullPointerException.class, () -> {
            LlmRefinementRequest.builder()
                    .schema("{}")
                    .config(null)
                    .build();
        });
    }

    @Test
    public void testLlmRefinementResultBuilder() {
        LlmRefinementResult result = LlmRefinementResult.builder()
                .refinedSchema("{\"refined\": true}")
                .confidence(0.85)
                .addSuggestion("Rename column 'id' to 'user_id'")
                .addApplied("Renamed id to user_id")
                .modelUsed("qwen2.5-coder")
                .executionTimeMs(1500L)
                .build();

        assertTrue(result.isSuccess());
        assertEquals("{\"refined\": true}", result.getRefinedSchema());
        assertEquals(0.85, result.getConfidence());
        assertEquals(1, result.getSuggestions().size());
        assertEquals(1, result.getApplied().size());
        assertEquals("qwen2.5-coder", result.getModelUsed());
        assertEquals(1500L, result.getExecutionTimeMs());
    }

    @Test
    public void testLlmRefinementResultSuccessDetection() {
        LlmRefinementResult successResult = LlmRefinementResult.builder()
                .refinedSchema("{}")
                .build();
        assertTrue(successResult.isSuccess());

        LlmRefinementResult errorResult = LlmRefinementResult.builder()
                .errorMessage("LLM unavailable")
                .build();
        assertFalse(errorResult.isSuccess());

        LlmRefinementResult emptyResult = LlmRefinementResult.builder()
                .refinedSchema("")
                .build();
        assertFalse(emptyResult.isSuccess());
    }

    @Test
    public void testLlmRefinementResultConfidenceValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            LlmRefinementResult.builder()
                    .confidence(1.5) // Invalid: > 1.0
                    .build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            LlmRefinementResult.builder()
                    .confidence(-0.1) // Invalid: < 0.0
                    .build();
        });
    }

    @Test
    public void testLlmRefinementResultEquality() {
        LlmRefinementResult result1 = LlmRefinementResult.builder()
                .refinedSchema("schema1")
                .confidence(0.8)
                .modelUsed("model1")
                .build();

        LlmRefinementResult result2 = LlmRefinementResult.builder()
                .refinedSchema("schema1")
                .confidence(0.8)
                .modelUsed("model1")
                .build();

        assertEquals(result1, result2);
        assertEquals(result1.hashCode(), result2.hashCode());
    }
}
