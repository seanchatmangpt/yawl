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

package org.yawlfoundation.yawl.mcp.a2a.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ContinuationModeHandler.
 *
 * <p>Tests continuation mode configuration for WCP-15 multi-instance patterns.
 * Chicago TDD: real handler logic, no mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("ContinuationModeHandler Tests")
class ContinuationModeHandlerTest {

    private ContinuationModeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ContinuationModeHandler();
    }

    @Nested
    @DisplayName("Configuration Parsing")
    class ConfigurationParsingTests {

        @Test
        @DisplayName("parseConfiguration returns disabled for null config")
        void parseNullConfig() {
            var config = handler.parseConfiguration(null);
            assertFalse(config.enabled());
        }

        @Test
        @DisplayName("parseConfiguration returns disabled for static mode")
        void parseStaticMode() {
            var config = handler.parseConfiguration(Map.of("mode", "static"));
            assertFalse(config.enabled());
        }

        @Test
        @DisplayName("parseConfiguration returns enabled for continuation mode")
        void parseContinuationMode() {
            var config = handler.parseConfiguration(Map.of(
                "mode", "continuation",
                "initialMinimum", 2,
                "creationTrigger", "on_demand",
                "batchSize", 5
            ));

            assertTrue(config.enabled());
            assertEquals(2, config.initialMinimum());
            assertEquals("on_demand", config.creationTrigger());
            assertEquals(5, config.batchSize());
        }

        @Test
        @DisplayName("parseConfiguration uses defaults for missing fields")
        void parseWithDefaults() {
            var config = handler.parseConfiguration(Map.of("mode", "continuation"));

            assertTrue(config.enabled());
            assertEquals(1, config.initialMinimum());
            assertEquals("on_demand", config.creationTrigger());
            assertEquals(Integer.MAX_VALUE, config.batchSize());
        }

        @Test
        @DisplayName("parseConfiguration handles case-insensitive mode")
        void parseCaseInsensitive() {
            var config = handler.parseConfiguration(Map.of("mode", "CONTINUATION"));
            assertTrue(config.enabled());
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("validate passes for disabled config")
        void validateDisabled() {
            var config = new ContinuationModeHandler.ContinuationConfig(false);
            assertDoesNotThrow(() -> handler.validate(config));
        }

        @Test
        @DisplayName("validate passes for valid continuation config")
        void validateValid() {
            var config = new ContinuationModeHandler.ContinuationConfig(
                true, 2, "on_demand", 5
            );
            assertDoesNotThrow(() -> handler.validate(config));
        }

        @Test
        @DisplayName("validate throws when initialMinimum < 1")
        void validateMinimumTooSmall() {
            var config = new ContinuationModeHandler.ContinuationConfig(
                true, 0, "on_demand", 5
            );
            assertThrows(IllegalArgumentException.class, () -> handler.validate(config));
        }

        @Test
        @DisplayName("validate throws when batchSize < 1")
        void validateBatchSizeTooSmall() {
            var config = new ContinuationModeHandler.ContinuationConfig(
                true, 2, "on_demand", 0
            );
            assertThrows(IllegalArgumentException.class, () -> handler.validate(config));
        }

        @Test
        @DisplayName("validate throws for unknown trigger")
        void validateUnknownTrigger() {
            var config = new ContinuationModeHandler.ContinuationConfig(
                true, 2, "invalid_trigger", 5
            );
            assertThrows(IllegalArgumentException.class, () -> handler.validate(config));
        }

        @Test
        @DisplayName("validate accepts on_completion trigger")
        void validateOnCompletionTrigger() {
            var config = new ContinuationModeHandler.ContinuationConfig(
                true, 2, "on_completion", 5
            );
            assertDoesNotThrow(() -> handler.validate(config));
        }

        @Test
        @DisplayName("validate accepts on_threshold trigger")
        void validateOnThresholdTrigger() {
            var config = new ContinuationModeHandler.ContinuationConfig(
                true, 2, "on_threshold", 5
            );
            assertDoesNotThrow(() -> handler.validate(config));
        }
    }

    @Nested
    @DisplayName("XML Generation")
    class XmlGenerationTests {

        @Test
        @DisplayName("generateXml throws for disabled config")
        void generateXmlDisabled() {
            var config = new ContinuationModeHandler.ContinuationConfig(false);
            assertThrows(IllegalStateException.class, () -> handler.generateXml(config));
        }

        @Test
        @DisplayName("generateXml returns valid XML for continuation config")
        void generateXmlValid() {
            var config = new ContinuationModeHandler.ContinuationConfig(
                true, 2, "on_demand", 5
            );
            String xml = handler.generateXml(config);

            assertNotNull(xml);
            assertFalse(xml.isEmpty());
            assertTrue(xml.contains("<creationMode code=\"continuation\"/>"));
            assertTrue(xml.contains("<miDataInput>"));
            assertTrue(xml.contains("</miDataInput>"));
        }

        @Test
        @DisplayName("generateXml contains expression element")
        void generateXmlContainsExpression() {
            var config = new ContinuationModeHandler.ContinuationConfig(
                true, 1, "on_demand", Integer.MAX_VALUE
            );
            String xml = handler.generateXml(config);

            assertTrue(xml.contains("<expression query=\"/net/data/items\"/>"));
        }

        @Test
        @DisplayName("generateXml contains splitting expression")
        void generateXmlContainsSplittingExpression() {
            var config = new ContinuationModeHandler.ContinuationConfig(
                true, 1, "on_demand", Integer.MAX_VALUE
            );
            String xml = handler.generateXml(config);

            assertTrue(xml.contains("<splittingExpression query=\"/item\"/>"));
        }

        @Test
        @DisplayName("generateXml contains formal input parameter")
        void generateXmlContainsFormalInputParam() {
            var config = new ContinuationModeHandler.ContinuationConfig(
                true, 1, "on_demand", Integer.MAX_VALUE
            );
            String xml = handler.generateXml(config);

            assertTrue(xml.contains("<formalInputParam>item</formalInputParam>"));
        }
    }

    @Nested
    @DisplayName("Strategy Description")
    class StrategyDescriptionTests {

        @Test
        @DisplayName("describeStrategy returns 'Not in continuation mode' for disabled")
        void describeDisabled() {
            var config = new ContinuationModeHandler.ContinuationConfig(false);
            String desc = handler.describeStrategy(config);
            assertTrue(desc.contains("Not in continuation mode"));
        }

        @Test
        @DisplayName("describeStrategy returns on_demand strategy")
        void describeOnDemand() {
            var config = new ContinuationModeHandler.ContinuationConfig(
                true, 2, "on_demand", 5
            );
            String desc = handler.describeStrategy(config);

            assertTrue(desc.contains("on-demand"));
            assertTrue(desc.contains("2"));
            assertTrue(desc.contains("5"));
        }

        @Test
        @DisplayName("describeStrategy returns on_completion strategy")
        void describeOnCompletion() {
            var config = new ContinuationModeHandler.ContinuationConfig(
                true, 2, "on_completion", 5
            );
            String desc = handler.describeStrategy(config);

            assertTrue(desc.contains("completion"));
            assertTrue(desc.contains("2"));
            assertTrue(desc.contains("5"));
        }

        @Test
        @DisplayName("describeStrategy returns on_threshold strategy")
        void describeOnThreshold() {
            var config = new ContinuationModeHandler.ContinuationConfig(
                true, 2, "on_threshold", 5
            );
            String desc = handler.describeStrategy(config);

            assertTrue(desc.contains("threshold"));
            assertTrue(desc.contains("5"));
        }
    }

    @Nested
    @DisplayName("ContinuationConfig Record")
    class ConfigRecordTests {

        @Test
        @DisplayName("ContinuationConfig default constructor creates disabled config")
        void defaultConstructor() {
            var config = new ContinuationModeHandler.ContinuationConfig(false);
            assertFalse(config.enabled());
            assertEquals(1, config.initialMinimum());
            assertEquals("on_demand", config.creationTrigger());
        }

        @Test
        @DisplayName("ContinuationConfig toString shows disabled state")
        void toStringDisabled() {
            var config = new ContinuationModeHandler.ContinuationConfig(false);
            String str = config.toString();
            assertTrue(str.contains("disabled"));
        }

        @Test
        @DisplayName("ContinuationConfig toString shows continuation details")
        void toStringEnabled() {
            var config = new ContinuationModeHandler.ContinuationConfig(
                true, 2, "on_demand", 5
            );
            String str = config.toString();

            assertTrue(str.contains("continuation"));
            assertTrue(str.contains("2"));
            assertTrue(str.contains("on_demand"));
            assertTrue(str.contains("5"));
        }
    }
}
