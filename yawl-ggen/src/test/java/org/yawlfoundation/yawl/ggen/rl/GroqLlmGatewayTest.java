/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GroqLlmGateway}.
 *
 * <p>Uses the package-private static {@code extractContent()} method to test
 * JSON response parsing without requiring a live Groq connection. Factory and
 * constructor validation are tested via observable behavior.
 */
class GroqLlmGatewayTest {

    // ----- extractContent — response parsing -----

    @Test
    void extractContent_validResponse_returnsContent() throws IOException {
        String json = """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "SEQUENCE(ACTIVITY(submit_loan), ACTIVITY(approve_loan))"
                  }
                }
              ]
            }
            """;
        String content = GroqLlmGateway.extractContent(json);
        assertEquals("SEQUENCE(ACTIVITY(submit_loan), ACTIVITY(approve_loan))", content);
    }

    @Test
    void extractContent_multipleChoices_returnsFirstContent() throws IOException {
        String json = """
            {
              "choices": [
                {"message": {"content": "ACTIVITY(first)"}},
                {"message": {"content": "ACTIVITY(second)"}}
              ]
            }
            """;
        String content = GroqLlmGateway.extractContent(json);
        assertEquals("ACTIVITY(first)", content,
            "Should return choices[0].message.content, not a later choice");
    }

    @Test
    void extractContent_missingChoicesField_throwsIOException() {
        String json = "{\"model\":\"llama-3.3-70b-versatile\"}";
        assertThrows(IOException.class,
            () -> GroqLlmGateway.extractContent(json),
            "Missing 'choices' field must throw IOException");
    }

    @Test
    void extractContent_emptyChoicesArray_throwsIOException() {
        String json = "{\"choices\":[]}";
        assertThrows(IOException.class,
            () -> GroqLlmGateway.extractContent(json),
            "Empty 'choices' array must throw IOException");
    }

    @Test
    void extractContent_malformedJson_throwsIOException() {
        assertThrows(IOException.class,
            () -> GroqLlmGateway.extractContent("NOT JSON AT ALL"),
            "Non-JSON input must throw IOException");
    }

    @Test
    void extractContent_emptyString_throwsIOException() {
        assertThrows(IOException.class,
            () -> GroqLlmGateway.extractContent(""),
            "Empty string must throw IOException");
    }

    // ----- constructor validation -----

    @Test
    void constructor_blankApiKey_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new GroqLlmGateway("  ", "llama-3.3-70b-versatile", Duration.ofSeconds(30)));
    }

    @Test
    void constructor_nullApiKey_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new GroqLlmGateway(null, "llama-3.3-70b-versatile", Duration.ofSeconds(30)));
    }

    @Test
    void constructor_blankModel_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new GroqLlmGateway("test-key", "  ", Duration.ofSeconds(30)));
    }

    @Test
    void constructor_nullTimeout_throwsNullPointer() {
        assertThrows(NullPointerException.class,
            () -> new GroqLlmGateway("test-key", "llama-3.3-70b-versatile", null));
    }

    // ----- isAvailable -----

    @Test
    void isAvailable_returnsBoolean() {
        // Verify it returns without throwing and reflects the environment.
        boolean available = GroqLlmGateway.isAvailable();
        String envKey = System.getenv("GROQ_API_KEY");
        boolean envSet = envKey != null && !envKey.isBlank();
        assertEquals(envSet, available,
            "isAvailable() must reflect whether GROQ_API_KEY is set in environment");
    }

    // ----- maxConcurrency -----

    @Test
    void maxConcurrency_returnsPositive() {
        int concurrency = GroqLlmGateway.maxConcurrency();
        assertTrue(concurrency > 0,
            "maxConcurrency() must return a positive value");
    }

    @Test
    void maxConcurrency_default_is30() {
        // When GROQ_MAX_CONCURRENCY is not set, default is 30 (free-tier RPM).
        // This test is informational — environment may override.
        String env = System.getenv("GROQ_MAX_CONCURRENCY");
        if (env == null || env.isBlank()) {
            assertEquals(30, GroqLlmGateway.maxConcurrency(),
                "Default max concurrency must be 30 (Groq free-tier RPM)");
        }
    }

    // ----- constants -----

    @Test
    void defaultModel_isGptOss20b() {
        assertEquals("openai/gpt-oss-20b", GroqLlmGateway.DEFAULT_MODEL);
    }

    @Test
    void defaultBaseUrl_pointsToGroq() {
        assertTrue(GroqLlmGateway.DEFAULT_BASE_URL.startsWith("https://api.groq.com/openai/v1"),
            "Default base URL must point to api.groq.com OpenAI-compatible endpoint");
    }

    @Test
    void defaultMaxConcurrency_is30() {
        assertEquals(30, GroqLlmGateway.DEFAULT_MAX_CONCURRENCY,
            "Default max concurrency constant must be 30 (Groq free-tier RPM)");
    }
}
