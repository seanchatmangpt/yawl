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
 * Unit tests for {@link ZaiLlmGateway}.
 *
 * <p>Uses the package-private static {@code extractContent()} method to test
 * JSON response parsing without requiring a live Z.AI connection. Factory and
 * constructor validation are tested via observable behavior.
 */
class ZaiLlmGatewayTest {

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
        String content = ZaiLlmGateway.extractContent(json);
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
        String content = ZaiLlmGateway.extractContent(json);
        assertEquals("ACTIVITY(first)", content,
            "Should return choices[0].message.content, not a later choice");
    }

    @Test
    void extractContent_missingChoicesField_throwsIOException() {
        String json = "{\"model\":\"glm-4.7-flash\"}";
        assertThrows(IOException.class,
            () -> ZaiLlmGateway.extractContent(json),
            "Missing 'choices' field must throw IOException");
    }

    @Test
    void extractContent_emptyChoicesArray_throwsIOException() {
        String json = "{\"choices\":[]}";
        assertThrows(IOException.class,
            () -> ZaiLlmGateway.extractContent(json),
            "Empty 'choices' array must throw IOException");
    }

    @Test
    void extractContent_malformedJson_throwsIOException() {
        assertThrows(IOException.class,
            () -> ZaiLlmGateway.extractContent("NOT JSON AT ALL"),
            "Non-JSON input must throw IOException");
    }

    @Test
    void extractContent_emptyString_throwsIOException() {
        assertThrows(IOException.class,
            () -> ZaiLlmGateway.extractContent(""),
            "Empty string must throw IOException");
    }

    // ----- constructor validation -----

    @Test
    void constructor_blankApiKey_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new ZaiLlmGateway("  ", "glm-4.7-flash", Duration.ofSeconds(30)));
    }

    @Test
    void constructor_nullApiKey_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new ZaiLlmGateway(null, "glm-4.7-flash", Duration.ofSeconds(30)));
    }

    @Test
    void constructor_blankModel_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new ZaiLlmGateway("test-key", "  ", Duration.ofSeconds(30)));
    }

    @Test
    void constructor_nullTimeout_throwsNullPointer() {
        assertThrows(NullPointerException.class,
            () -> new ZaiLlmGateway("test-key", "glm-4.7-flash", null));
    }

    // ----- isAvailable -----

    @Test
    void isAvailable_returnsBoolean() {
        // We can't control the environment, but we can verify it returns without throwing
        // and that the result is consistent with whether ZAI_API_KEY is set.
        boolean available = ZaiLlmGateway.isAvailable();
        String envKey = System.getenv("ZAI_API_KEY");
        boolean envSet = envKey != null && !envKey.isBlank();
        assertEquals(envSet, available,
            "isAvailable() must reflect whether ZAI_API_KEY is set in environment");
    }

    // ----- constants -----

    @Test
    void defaultModel_isGlm47Flash() {
        assertEquals("glm-4.7-flash", ZaiLlmGateway.DEFAULT_MODEL);
    }

    @Test
    void defaultBaseUrl_pointsToOpenBigmodel() {
        assertTrue(ZaiLlmGateway.DEFAULT_BASE_URL.contains("open.bigmodel.cn"),
            "Default base URL must point to open.bigmodel.cn");
    }
}
