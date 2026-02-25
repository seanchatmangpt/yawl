/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.mining.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link OllamaValidationClient}: prompt building and response parsing.
 * Network tests use an unavailable port (65534) to verify IOException propagation
 * without requiring a live Ollama instance.
 */
class OllamaValidationClientTest {

    private OllamaValidationClient client;

    @BeforeEach
    void setUp() {
        client = new OllamaValidationClient("http://localhost:11434", "qwen2.5-coder", 5);
    }

    /**
     * Scenario 1: buildPrompt returns a non-null, non-blank string.
     */
    @Test
    void buildPrompt_withXmlAndIteration_isNonNullNonBlank() {
        String prompt = client.buildPrompt("<specificationSet/>", 1);

        assertNotNull(prompt, "Prompt should not be null");
        assertFalse(prompt.isBlank(), "Prompt should not be blank");
    }

    /**
     * Scenario 2: buildPrompt includes a fragment of the input XML so the model sees
     * the actual content to validate.
     */
    @Test
    void buildPrompt_withXmlFragment_containsInputXml() {
        String yawlXml = "<specificationSet version=\"4.0\"><task id=\"t1\"/></specificationSet>";
        String prompt = client.buildPrompt(yawlXml, 2);

        assertTrue(prompt.contains("t1"),
                "Prompt should contain a fragment of the input XML (task id t1)");
        assertTrue(prompt.contains("2"),
                "Prompt should reference the iteration number");
    }

    /**
     * Scenario 3: parseResponse with ISSUE: lines produces valid=false and the correct
     * list of issue descriptions.
     */
    @Test
    void parseResponse_withIssueLines_returnsInvalidResultWithIssues() throws IOException {
        String rawJson = """
                {"model":"qwen2.5-coder","created_at":"2026-02-25T00:00:00Z",\
                "response":"ISSUE: missing inputCondition element\\nISSUE: task t1 has no decomposition\\n",\
                "done":true}""";

        ValidationResult result = client.parseResponse(rawJson, 1);

        assertFalse(result.valid(), "Result should be invalid when ISSUE lines are present");
        assertEquals(2, result.issues().size(), "Should have exactly 2 issues");
        assertTrue(result.issues().get(0).contains("inputCondition"),
                "First issue should mention inputCondition");
        assertTrue(result.issues().get(1).contains("decomposition"),
                "Second issue should mention decomposition");
        assertEquals(1, result.iteration(), "Iteration should be preserved");
    }

    /**
     * Scenario 4: parseResponse with VALID response (no ISSUE lines) produces
     * valid=true and empty issues list.
     */
    @Test
    void parseResponse_withValidResponse_returnsValidResultWithNoIssues() throws IOException {
        String rawJson = """
                {"model":"qwen2.5-coder","created_at":"2026-02-25T00:00:00Z",\
                "response":"VALID",\
                "done":true}""";

        ValidationResult result = client.parseResponse(rawJson, 3);

        assertTrue(result.valid(), "Result should be valid when only VALID is returned");
        assertEquals(0, result.issues().size(), "Should have no issues for a VALID response");
        assertEquals(3, result.iteration(), "Iteration should be preserved");
        assertNotNull(result.rawResponse(), "Raw response should be preserved");
    }

    /**
     * Scenario 5: validate() with a connection-refused port throws IOException.
     * This verifies real network rejection without requiring a live Ollama instance.
     */
    @Test
    void validate_unavailablePort_throwsIOException() {
        OllamaValidationClient unreachableClient =
                new OllamaValidationClient("http://localhost:65534", "qwen2.5-coder", 2);

        assertThrows(IOException.class,
                () -> unreachableClient.validate("<specificationSet/>", 1),
                "Connection refused on port 65534 should propagate as IOException");
    }
}
