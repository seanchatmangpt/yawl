/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.zai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ZaiService implementation.
 *
 * <p>Tests that require actual API calls are enabled only when ZAI_API_KEY
 * environment variable is set. Other tests verify static methods and configuration.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
class ZaiServiceTest {

    private static final String TEST_PROMPT = "You are a helpful AI assistant";

    @Test
    @DisplayName("Test ZaiService creation with null API key throws")
    void testCreateWithNullApiKey() {
        assertThrows(IllegalArgumentException.class,
                     () -> new ZaiService(null),
                     "Should throw IllegalArgumentException for null API key");
    }

    @Test
    @DisplayName("Test ZaiService creation with empty API key throws")
    void testCreateWithEmptyApiKey() {
        assertThrows(IllegalArgumentException.class,
                     () -> new ZaiService(""),
                     "Should throw IllegalArgumentException for empty API key");
    }

    @Test
    @DisplayName("Test ZaiService creation with blank API key throws")
    void testCreateWithBlankApiKey() {
        assertThrows(IllegalArgumentException.class,
                     () -> new ZaiService("   "),
                     "Should throw IllegalArgumentException for blank API key");
    }

    @Test
    @DisplayName("Test getAvailableModels returns list")
    void testGetAvailableModels() {
        List<String> models = ZaiService.getAvailableModels();
        assertNotNull(models, "Available models list should not be null");
        assertFalse(models.isEmpty(), "Available models list should not be empty");
        assertTrue(models.contains("GLM-4.7-Flash"), "Should include GLM-4.7-Flash model");
        assertTrue(models.contains("glm-4.6"), "Should include glm-4.6 model");
    }

    @Test
    @DisplayName("Test ScopedValue WORKFLOW_SYSTEM_PROMPT exists")
    void testScopedValueExists() {
        assertNotNull(ZaiService.WORKFLOW_SYSTEM_PROMPT,
                      "WORKFLOW_SYSTEM_PROMPT ScopedValue should exist");
    }

    @Test
    @DisplayName("Test ScopedValue MODEL_OVERRIDE exists")
    void testModelOverrideScopedValueExists() {
        assertNotNull(ZaiService.MODEL_OVERRIDE,
                      "MODEL_OVERRIDE ScopedValue should exist");
    }

    @Test
    @DisplayName("Test ZaiService creation with valid API key in test environment")
    @DisabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
    void testCreateWithValidApiKey() {
        // This test verifies that the ZaiService can be created with a valid API key
        // When ZAI_API_KEY is not set, we should be able to create the service
        // The actual ZAI SDK integration is a stub that throws UnsupportedOperationException
        // per the Q-invariant requirement
        assertDoesNotThrow(() -> {
            ZaiService service = new ZaiService("test-key");
            assertNotNull(service, "ZaiService should be created successfully");
            assertTrue(service.isInitialized(), "Service should be initialized");
        });
    }

    @Test
    @DisplayName("Test setting system prompt")
    void testSetSystemPrompt() {
        ZaiService service = new ZaiService("valid-api-key");
        assertDoesNotThrow(() -> {
            service.setSystemPrompt(TEST_PROMPT);
            assertEquals(TEST_PROMPT, service.getSystemPrompt());
        });
    }

    @Test
    @DisplayName("Test clearHistory throws UnsupportedOperationException")
    void testClearHistoryThrows() {
        ZaiService service = new ZaiService("valid-api-key");
        assertThrows(UnsupportedOperationException.class,
                     () -> service.clearHistory(),
                     "clearHistory should throw UnsupportedOperationException per Q-invariant");
    }

    @Test
    @DisplayName("Test shutdown throws UnsupportedOperationException")
    void testShutdownThrows() {
        ZaiService service = new ZaiService("valid-api-key");
        assertThrows(UnsupportedOperationException.class,
                     () -> service.shutdown(),
                     "shutdown should throw UnsupportedOperationException per Q-invariant");
    }

    @Test
    @DisplayName("Test getDefaultModel throws UnsupportedOperationException")
    void testGetDefaultModelThrows() {
        ZaiService service = new ZaiService("valid-api-key");
        assertThrows(UnsupportedOperationException.class,
                     () -> service.getDefaultModel(),
                     "getDefaultModel should throw UnsupportedOperationException per Q-invariant");
    }

    @Test
    @DisplayName("Test getConversationHistory throws UnsupportedOperationException")
    void testGetConversationHistoryThrows() {
        ZaiService service = new ZaiService("valid-api-key");
        assertThrows(UnsupportedOperationException.class,
                     () -> service.getConversationHistory(),
                     "getConversationHistory should throw UnsupportedOperationException per Q-invariant");
    }

    @Test
    @DisplayName("Test verifyConnection throws UnsupportedOperationException")
    void testVerifyConnection() {
        ZaiService service = new ZaiService("valid-api-key");
        assertThrows(UnsupportedOperationException.class,
                     () -> service.verifyConnection(),
                     "verifyConnection should throw UnsupportedOperationException per Q-invariant");
    }

    @Test
    @DisplayName("Test chat operation throws UnsupportedOperationException")
    void testChatOperation() {
        ZaiService service = new ZaiService("valid-api-key");
        assertThrows(UnsupportedOperationException.class,
                     () -> service.chat("Hello"),
                     "chat should throw UnsupportedOperationException per Q-invariant");
    }

    @Test
    @DisplayName("Test Q-invariant enforcement: real implementation ∨ throw UnsupportedOperationException")
    @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
    void testQInvariantEnforcement() {
        String apiKey = System.getenv("ZAI_API_KEY");
        ZaiService service = new ZaiService(apiKey);

        // Verify that all methods either work or throw UnsupportedOperationException
        assertAll("Q-invariant enforcement",
            // Methods that should work (basic functionality)
            () -> assertDoesNotThrow(() -> service.setSystemPrompt("test")),
            () -> assertNotNull(service.getSystemPrompt()),
            () -> assertNotNull(service.getApiKey()),
            () -> assertTrue(service.isConfigured()),

            // Methods that should throw UnsupportedOperationException (stub implementation)
            () -> assertThrows(UnsupportedOperationException.class, () -> service.chat("test")),
            () -> assertThrows(UnsupportedOperationException.class, () -> service.verifyConnection()),
            () -> assertThrows(UnsupportedOperationException.class, () -> service.getDefaultModel()),
            () -> assertThrows(UnsupportedOperationException.class, () -> service.getConversationHistory()),
            () -> assertThrows(UnsupportedOperationException.class, () -> service.clearHistory()),
            () -> assertThrows(UnsupportedOperationException.class, () -> service.shutdown())
        );
    }
}
