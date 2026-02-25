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
 * You should have received a copy of the GNU Lesser General
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.zai;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ZaiService core functionality.
 *
 * @author YAWL Foundation - ZAI Integration Team
 * @version 6.0
 */
@DisplayName("ZAI Service Tests")
public class ZaiServiceTest {

    /** ZAI SDK requires "id.secret" format (exactly one period). This key passes format validation. */
    private static final String TEST_API_KEY = "testid.testsecret";

    // =========================================================================
    // Initialization Tests
    // =========================================================================

    @Nested
    @DisplayName("Service Initialization Tests")
    class InitializationTests {

        @Test
        @DisplayName("Constructor with valid API key should succeed")
        void testConstructorWithValidKey() {
            ZaiService service = new ZaiService(TEST_API_KEY);
            assertNotNull(service, "Service should be created");
            assertTrue(service.isInitialized(), "Service should be initialized");
        }

        @Test
        @DisplayName("Get available models should return expected models")
        void testGetAvailableModels() {
            List<String> models = ZaiService.getAvailableModels();

            assertNotNull(models, "Models list should not be null");
            assertEquals(4, models.size(), "Should have 4 models");
            assertTrue(models.contains("GLM-4.7-Flash"), "Should contain GLM-4.7-Flash");
            assertTrue(models.contains("glm-4.6"), "Should contain glm-4.6");
            assertTrue(models.contains("glm-4.5"), "Should contain glm-4.5");
            assertTrue(models.contains("glm-5"), "Should contain glm-5");
        }
    }

    // =========================================================================
    // System Prompt Tests
    // =========================================================================

    @Nested
    @DisplayName("System Prompt Tests")
    class SystemPromptTests {

        @Test
        @DisplayName("Set and get system prompt")
        void testSetGetSystemPrompt() {
            ZaiService service = new ZaiService(TEST_API_KEY);

            assertNull(service.getSystemPrompt(), "Initial prompt should be null");

            service.setSystemPrompt("You are a test assistant.");
            assertEquals("You are a test assistant.", service.getSystemPrompt(),
                    "Should return set prompt");

            service.setSystemPrompt(null);
            assertNull(service.getSystemPrompt(), "Should be null after setting null");
        }
    }

    // =========================================================================
    // Conversation History Tests
    // =========================================================================

    @Nested
    @DisplayName("Conversation History Tests")
    class HistoryTests {

        @Test
        @DisplayName("Initial history should be empty")
        void testInitialHistoryEmpty() {
            ZaiService service = new ZaiService(TEST_API_KEY);
            assertTrue(service.getConversationHistory().isEmpty());
        }

        @Test
        @DisplayName("Clear history should empty the history")
        void testClearHistory() {
            ZaiService service = new ZaiService(TEST_API_KEY);
            service.clearHistory();
            assertTrue(service.getConversationHistory().isEmpty());
        }
    }

    // =========================================================================
    // Service State Tests
    // =========================================================================

    @Nested
    @DisplayName("Service State Tests")
    class StateTests {

        @Test
        @DisplayName("Get HTTP client returns non-null")
        void testGetHttpClient() {
            ZaiService service = new ZaiService(TEST_API_KEY);
            assertNotNull(service.getHttpClient());
        }

        @Test
        @DisplayName("Get default model returns correct value")
        void testGetDefaultModel() {
            ZaiService service = new ZaiService(TEST_API_KEY);
            assertEquals("GLM-4.7-Flash", service.getDefaultModel());
        }
    }
}
