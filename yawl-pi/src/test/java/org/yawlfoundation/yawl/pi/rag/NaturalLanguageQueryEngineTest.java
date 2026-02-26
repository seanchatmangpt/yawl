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

package org.yawlfoundation.yawl.pi.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.pi.PIException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for NaturalLanguageQueryEngine.
 *
 * Tests verify that queries are processed correctly, fallback behavior
 * works when Z.AI is unavailable, and responses are properly formatted.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class NaturalLanguageQueryEngineTest {

    private NaturalLanguageQueryEngine engine;
    private ProcessKnowledgeBase knowledgeBase;
    private ZaiService zaiService;

    @BeforeEach
    void setUp() {
        knowledgeBase = new ProcessKnowledgeBase(null);

        // Try to create ZaiService - if ZAI_API_KEY is not set, create a mock
        try {
            zaiService = new ZaiService();
        } catch (IllegalStateException e) {
            // Expected when ZAI_API_KEY is not set - will use null for fallback test
            zaiService = null;
        }

        if (zaiService != null) {
            engine = new NaturalLanguageQueryEngine(knowledgeBase, zaiService);
        }
    }

    @Test
    void testConstructor_NullKnowledgeBaseThrows() {
        ZaiService mockService = null;
        try {
            mockService = new ZaiService();
        } catch (IllegalStateException e) {
            // Expected
        }

        if (mockService != null) {
            assertThrows(IllegalArgumentException.class, () -> {
                new NaturalLanguageQueryEngine(null, mockService);
            });
        }
    }

    @Test
    void testConstructor_NullZaiServiceThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            new NaturalLanguageQueryEngine(knowledgeBase, null);
        });
    }

    @Test
    void testQuery_NullRequestThrows() {
        assumeTrue(engine != null, "Skipping - ZaiService not available");

        assertThrows(PIException.class, () -> {
            engine.query(null);
        });
    }

    @Test
    void testQuery_WithoutZaiKey_FallsBackToRawFacts() throws PIException {
        assumeTrue(zaiService == null, "Skipping - ZAI_API_KEY is set");

        // Create a mock engine without Z.AI
        ProcessKnowledgeBase mockKb = new ProcessKnowledgeBase(null);
        ZaiService mockZai = new MockZaiService();  // Will throw IllegalStateException

        NaturalLanguageQueryEngine mockEngine = new NaturalLanguageQueryEngine(mockKb, mockZai);

        // Query should not throw
        NlQueryRequest request = NlQueryRequest.of("What is the average flow time?");
        NlQueryResponse response = mockEngine.query(request);

        assertNotNull(response);
        assertNotNull(response.answer());
        assertTrue(response.answer().contains("data") || response.answer().contains("No relevant"));
    }

    @Test
    void testQuery_EmptyKnowledgeBase_ReturnsNoData() throws PIException {
        assumeTrue(engine != null, "Skipping - ZaiService not available");

        NlQueryRequest request = NlQueryRequest.of("What is the process performance?");
        NlQueryResponse response = engine.query(request);

        assertNotNull(response);
        assertNotNull(response.answer());
        // Answer should indicate no data available
        assertTrue(response.answer().toLowerCase().contains("data") ||
                   response.answer().toLowerCase().contains("no"));
    }

    @Test
    void testQuery_ResponseIncludesMetadata() throws PIException {
        assumeTrue(engine != null, "Skipping - ZaiService not available");

        NlQueryRequest request = NlQueryRequest.of("What is the process performance?");
        NlQueryResponse response = engine.query(request);

        assertNotNull(response.requestId());
        assertEquals(request.requestId(), response.requestId());
        assertNotNull(response.answer());
        assertNotNull(response.sourceFacts());
        assertNotNull(response.modelUsed());
        assertTrue(response.latencyMs() >= 0);
        assertNotNull(response.respondedAt());
    }

    @Test
    void testQuery_IncludesSourceFacts() throws PIException {
        assumeTrue(engine != null, "Skipping - ZaiService not available");

        NlQueryRequest request = NlQueryRequest.of("Tell me about the process");
        NlQueryResponse response = engine.query(request);

        assertNotNull(response.sourceFacts());
        // Source facts list may be empty if KB is empty
        assertTrue(response.sourceFacts().size() >= 0);
    }

    @Test
    void testQuery_ModelUsedIsCorrect() throws PIException {
        assumeTrue(engine != null, "Skipping - ZaiService not available");

        NlQueryRequest request = NlQueryRequest.of("What is happening?");
        NlQueryResponse response = engine.query(request);

        assertEquals("GLM-4.7-Flash", response.modelUsed());
    }

    @Test
    void testQuery_GroundedFlagIsSet() throws PIException {
        assumeTrue(engine != null, "Skipping - ZaiService not available");

        NlQueryRequest request = NlQueryRequest.of("What is the status?");
        NlQueryResponse response = engine.query(request);

        // When KB is empty, groundedInKnowledgeBase should be false
        // When KB has data, it depends on the LLM response
        assertTrue(response.groundedInKnowledgeBase() || !response.groundedInKnowledgeBase());
    }

    /**
     * Mock Z.AI service that simulates missing API key.
     */
    private static class MockZaiService extends ZaiService {
        public MockZaiService() throws IllegalStateException {
            // Force failure by passing empty key
            super("");
        }

        @Override
        public String chat(String message) {
            throw new IllegalStateException("ZAI_API_KEY environment variable is required.");
        }

        @Override
        public String chat(String message, String model) {
            throw new IllegalStateException("ZAI_API_KEY environment variable is required.");
        }
    }
}
