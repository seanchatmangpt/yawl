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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.validation.a2a;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.integration.validation.schema.JsonSchemaValidator;
import org.yawlfoundation.yawl.integration.validation.schema.SchemaValidationError;
import org.yawlfoundation.yawl.integration.validation.schema.ValidationConfig;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the A2ASchemaValidator class.
 *
 * Tests A2A protocol message validation with various scenarios including
 * valid/invalid messages, business rule validation, and integration testing.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class A2ASchemaValidatorTest {

    private A2ASchemaValidator validator;

    @BeforeEach
    void setUp() {
        validator = new A2ASchemaValidator();
    }

    @Test
    @DisplayName("Validate valid handoff message")
    void validateValidHandoffMessage() {
        String validHandoff = "{\"parts\":[{\"type\":\"text\",\"text\":\"YAWL_HANDOFF:WI-12345:agent-source\"},{\"type\":\"data\",\"data\":{\"reason\":\"test\",\"priority\":\"high\"}}]}";

        assertDoesNotThrow(() -> {
            JsonSchemaValidator.ValidationResult result = validator.validateHandoffMessage(validHandoff);
            assertTrue(result.isValid());
        });
    }

    @Test
    @DisplayName("Validate handoff message with business rules")
    void validateHandoffMessageWithBusinessRules() {
        String validHandoff = "{\"parts\":[{\"type\":\"text\",\"text\":\"YAWL_HANDOFF:WI-12345:agent-source\"},{\"type\":\"data\",\"data\":{\"toAgent\":\"agent-target\",\"reason\":\"test\"}}]}";

        assertDoesNotThrow(() -> {
            JsonSchemaValidator.ValidationResult result = validator.validateHandoffMessageWithRules(validHandoff);
            assertTrue(result.isValid());
        });
    }

    @Test
    @DisplayName("Validate invalid handoff message")
    void validateInvalidHandoffMessage() {
        String invalidHandoff = "{\"parts\":[{\"type\":\"text\",\"text\":\"invalid-format\"}]}";

        SchemaValidationError exception = assertThrows(SchemaValidationError.class, () -> {
            validator.validateHandoffMessage(invalidHandoff);
        });

        assertFalse(exception.isValidationError());
    }

    @Test
    @DisplayName("Validate valid agent card")
    void validateValidAgentCard() {
        String validCard = "{\"name\":\"Test Agent\",\"version\":\"1.0.0\",\"protocols\":[{\"name\":\"a2a\",\"version\":\"1.0\",\"url\":\"http://localhost:8080\"}],\"skills\":[{\"name\":\"test\",\"description\":\"Test skill\"}]}";

        assertDoesNotThrow(() -> {
            JsonSchemaValidator.ValidationResult result = validator.validateAgentCard(validCard);
            assertTrue(result.isValid());
        });
    }

    @Test
    @DisplayName("Validate invalid agent card")
    void validateInvalidAgentCard() {
        String invalidCard = "{}";

        SchemaValidationError exception = assertThrows(SchemaValidationError.class, () -> {
            validator.validateAgentCard(invalidCard);
        });

        assertFalse(exception.isValidationError());
    }

    @Test
    @DisplayName("Validate valid work item ID")
    void validateValidWorkItemId() {
        assertTrue(validator.validateWorkItemId("WI-12345"));
        assertTrue(validator.validateWorkItemId("WI-ABC123"));
        assertFalse(validator.validateWorkItemId(null));
        assertFalse(validator.validateWorkItemId(""));
        assertFalse(validator.validateWorkItemId("INVALID"));
    }

    @Test
    @DisplayName("Validate valid agent ID")
    void validateValidAgentId() {
        assertTrue(validator.validateAgentId("agent-test"));
        assertTrue(validator.validateAgentId("agent-test-123"));
        assertTrue(validator.validateAgentId("agent_test"));
        assertFalse(validator.validateAgentId(null));
        assertFalse(validator.validateAgentId(""));
        assertFalse(validator.validateAgentId("123agent"));
        assertFalse(validator.validateAgentId("agent@test"));
    }

    @Test
    @DisplayName("Validate valid session handle")
    void validateValidSessionHandle() {
        assertTrue(validator.validateSessionHandle("abc123def456"));
        assertTrue(validator.validateSessionHandle("12345678"));
        assertFalse(validator.validateSessionHandle(null));
        assertFalse(validator.validateSessionHandle(""));
        assertFalse(validator.validateSessionHandle("too-short"));
        assertFalse(validator.validateSessionHandle("this-is-way-too-long-for-a-session-handle"));
    }

    @Test
    @DisplayName("Test complete A2A workflow validation")
    void testCompleteA2AWorkflow() {
        String handoffMessage = "{\"parts\":[{\"type\":\"text\",\"text\":\"YAWL_HANDOFF:WI-12345:agent-source\"},{\"type\":\"data\",\"data\":{\"toAgent\":\"agent-target\",\"reason\":\"test\"}}]}";
        String agentCard = "{\"name\":\"Test Agent\",\"version\":\"1.0.0\",\"protocols\":[{\"name\":\"a2a\",\"version\":\"1.0\",\"url\":\"http://localhost:8080\"}],\"skills\":[{\"name\":\"test\",\"description\":\"Test skill\"}]}";
        String targetAgentId = "agent-target";

        assertDoesNotThrow(() -> {
            JsonSchemaValidator.ValidationResult result = validator.validateCompleteWorkflow(
                handoffMessage, agentCard, targetAgentId);
            assertTrue(result.isValid());
        });
    }

    @Test
    @DisplayName("Test complete A2A workflow with invalid target agent")
    void testCompleteA2AWorkflowWithInvalidTarget() {
        String handoffMessage = "{\"parts\":[{\"type\":\"text\",\"text\":\"YAWL_HANDOFF:WI-12345:agent-source\"}]}";
        String agentCard = "{\"name\":\"Test Agent\",\"version\":\"1.0.0\",\"protocols\":[{\"name\":\"a2a\",\"version\":\"1.0\",\"url\":\"http://localhost:8080\"}],\"skills\":[{\"name\":\"test\",\"description\":\"Test skill\"}]}";
        String targetAgentId = "invalid@agent";

        SchemaValidationError exception = assertThrows(SchemaValidationError.class, () -> {
            validator.validateCompleteWorkflow(handoffMessage, agentCard, targetAgentId);
        });

        assertTrue(exception.getMessage().contains("Invalid target agent ID format"));
    }

    @Test
    @DisplayName("Test business rule: source and target agents cannot be the same")
    void testBusinessRuleSameAgents() {
        String invalidHandoff = "{\"parts\":[{\"type\":\"text\",\"text\":\"YAWL_HANDOFF:WI-12345:agent-source\"},{\"type\":\"data\",\"data\":{\"toAgent\":\"agent-source\",\"reason\":\"test\"}}]}";

        SchemaValidationError exception = assertThrows(SchemaValidationError.class, () -> {
            validator.validateHandoffMessageWithRules(invalidHandoff);
        });

        assertTrue(exception.getMessage().contains("Handoff source and target agents cannot be the same"));
        assertEquals("/parts/1/data/toAgent", exception.getJsonPointer());
    }

    @Test
    @DisplayName("Test business rule: invalid priority value")
    void testBusinessRuleInvalidPriority() {
        String invalidHandoff = "{\"parts\":[{\"type\":\"text\",\"text\":\"YAWL_HANDOFF:WI-12345:agent-source\"},{\"type\":\"data\",\"data\":{\"priority\":\"invalid\",\"reason\":\"test\"}}]}";

        SchemaValidationError exception = assertThrows(SchemaValidationError.class, () -> {
            validator.validateHandoffMessageWithRules(invalidHandoff);
        });

        assertTrue(exception.getMessage().contains("Invalid priority value"));
        assertEquals("/parts/1/data/priority", exception.getJsonPointer());
    }

    @Test
    @DisplayName("Test configuration management")
    void testConfigurationManagement() {
        // Test default configuration
        assertNotNull(validator.getConfig());
        assertTrue(validator.getConfig().isEnableCaching());

        // Test setting new configuration
        ValidationConfig newConfig = new ValidationConfig.Builder()
            .enableCaching(false)
            .failFast(false)
            .build();

        validator.setConfig(newConfig);
        assertEquals(newConfig, validator.getConfig());
        assertFalse(validator.getConfig().isEnableCaching());
        assertFalse(validator.getConfig().isFailFast());
    }

    @Test
    @DisplayName("Test null message handling")
    void testNullMessageHandling() {
        SchemaValidationError exception = assertThrows(SchemaValidationError.class, () -> {
            validator.validateHandoffMessage(null);
        });

        // Should be wrapped in system error since JSON parsing fails
        assertEquals(SchemaValidationError.ErrorType.SYSTEM_ERROR, exception.getErrorType());
    }

    @Test
    @DisplayName("Test empty message handling")
    void testEmptyMessageHandling() {
        SchemaValidationError exception = assertThrows(SchemaValidationError.class, () -> {
            validator.validateHandoffMessage("");
        });

        assertEquals(SchemaValidationError.ErrorType.INVALID_JSON, exception.getErrorType());
    }
}