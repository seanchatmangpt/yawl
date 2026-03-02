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

package org.yawlfoundation.yawl.mcp.a2a.config;

import org.yawlfoundation.yawl.mcp.a2a.config.PatternConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.Arguments;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for PatternConfiguration using Chicago TDD style.
 * Tests all configurations, validation, and edge cases.
 */
@DisplayName("PatternConfiguration Configuration Tests")
class PatternConfigurationTest {

    private static final Duration DEFAULT_TIMEOUT = PatternConfiguration.DEFAULT_TIMEOUT;
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration LONG_TIMEOUT = Duration.ofMinutes(10);

    @Nested
    @DisplayName("Builder Configuration Tests")
    class BuilderConfigurationTests {

        private PatternConfiguration.Builder builder;

        @BeforeEach
        void setUp() {
            builder = PatternConfiguration.builder();
        }

        @Test
        @DisplayName("Builder should create configuration with default values")
        void builderShouldCreateDefaultConfiguration() {
            PatternConfiguration config = builder.build();

            assertEquals(PatternConfiguration.OutputFormat.CONSOLE,
                       config.getOutputFormat(),
                       "Default output format should be CONSOLE");
            assertEquals(DEFAULT_TIMEOUT,
                       config.getTimeout(),
                       "Default timeout should be DEFAULT_TIMEOUT");
            assertTrue(config.isParallelExecutionEnabled(),
                      "Default parallel execution should be enabled");
            assertTrue(config.isAutoCompleteEnabled(),
                      "Default auto completion should be enabled");
            assertTrue(config.isMetricsEnabled(),
                      "Default metrics should be enabled");
            assertFalse(config.isCommentaryEnabled(),
                       "Default commentary should be disabled");
            assertTrue(config.isTokenAnalysisEnabled(),
                      "Default token analysis should be enabled");
            assertTrue(config.getPatternIds().isEmpty(),
                      "Default pattern IDs should be empty");
        }

        @Test
        @DisplayName("Builder should set output format correctly")
        void builderShouldSetOutputFormat() {
            PatternConfiguration config = builder
                .outputFormat(PatternConfiguration.OutputFormat.JSON)
                .build();

            assertEquals(PatternConfiguration.OutputFormat.JSON,
                       config.getOutputFormat());
        }

        @Test
        @DisplayName("Builder should validate timeout values")
        void builderShouldValidateTimeoutValues() {
            // Valid timeout
            assertDoesNotThrow(() -> builder.timeout(SHORT_TIMEOUT).build(),
                             "Should accept valid timeout");

            // Null timeout should throw
            assertThrows(NullPointerException.class,
                       () -> builder.timeout(null),
                       "Should reject null timeout");

            // Negative timeout should throw
            assertThrows(IllegalArgumentException.class,
                       () -> builder.timeout(Duration.ofSeconds(-1)),
                       "Should reject negative timeout");

            // Zero timeout should throw
            assertThrows(IllegalArgumentException.class,
                       () -> builder.timeout(Duration.ZERO),
                       "Should reject zero timeout");
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        @DisplayName("Builder should set parallel execution flag")
        void builderShouldSetParallelExecution(boolean enabled) {
            PatternConfiguration config = builder
                .parallelExecution(enabled)
                .build();

            assertEquals(enabled, config.isParallelExecutionEnabled());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        @DisplayName("Builder should set auto completion flag")
        void builderShouldSetAutoCompletion(boolean enabled) {
            PatternConfiguration config = builder
                .autoComplete(enabled)
                .build();

            assertEquals(enabled, config.isAutoCompleteEnabled());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        @DisplayName("Builder should set metrics flag")
        void builderShouldSetMetrics(boolean enabled) {
            PatternConfiguration config = builder
                .enableMetrics(enabled)
                .build();

            assertEquals(enabled, config.isMetricsEnabled());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        @DisplayName("Builder should set commentary flag")
        void builderShouldSetCommentary(boolean enabled) {
            PatternConfiguration config = builder
                .withCommentary(enabled)
                .build();

            assertEquals(enabled, config.isCommentaryEnabled());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        @DisplayName("Builder should set token analysis flag")
        void builderShouldSetTokenAnalysis(boolean enabled) {
            PatternConfiguration config = builder
                .tokenAnalysis(enabled)
                .build();

            assertEquals(enabled, config.isTokenAnalysisEnabled());
        }

        @Test
        @DisplayName("Builder should set pattern IDs with null check")
        void builderShouldSetPatternIds() {
            List<String> patterns = List.of("WCP-1", "WCP-2", "WCP-3");
            PatternConfiguration config = builder
                .patternIds(patterns)
                .build();

            assertEquals(3, config.getPatternIds().size());
            assertEquals(patterns, config.getPatternIds());
            assertTrue(config.hasPatternIds());
        }

        @Test
        @DisplayName("Builder should handle null pattern IDs list")
        void builderShouldHandleNullPatternIds() {
            assertThrows(NullPointerException.class,
                       () -> builder.patternIds(null),
                       "Should reject null pattern IDs list");
        }

        @Test
        @DisplayName("Builder should create unmodifiable pattern IDs list")
        void builderShouldCreateUnmodifiablePatternIds() {
            PatternConfiguration config = builder.build();

            assertThrows(UnsupportedOperationException.class,
                       () -> config.getPatternIds().add("WCP-4"),
                       "Pattern IDs list should be unmodifiable");
        }
    }

    @Nested
    @DisplayName("OutputFormat Tests")
    class OutputFormatTests {

        @Test
        @DisplayName("OutputFormat should return correct file extensions")
        void outputFormatShouldReturnCorrectExtensions() {
            assertEquals(".txt", PatternConfiguration.OutputFormat.CONSOLE.getFileExtension());
            assertEquals(".json", PatternConfiguration.OutputFormat.JSON.getFileExtension());
            assertEquals(".md", PatternConfiguration.OutputFormat.MARKDOWN.getFileExtension());
            assertEquals(".html", PatternConfiguration.OutputFormat.HTML.getFileExtension());
        }

        @Test
        @DisplayName("OutputFormat enum should have all expected values")
        void outputFormatShouldHaveAllExpectedValues() {
            assertEquals(4, PatternConfiguration.OutputFormat.values().length);
            Stream.of(PatternConfiguration.OutputFormat.values())
                .forEach(format -> assertNotNull(format.getFileExtension(),
                                               "All formats should have valid extensions"));
        }
    }

    @Nested
    @DisplayName("Configuration Validation Tests")
    class ConfigurationValidationTests {

        @Test
        @DisplayName("Configuration should be valid with all defaults")
        void configurationShouldBeValidWithDefaults() {
            assertDoesNotThrow(() -> PatternConfiguration.builder().build(),
                             "Default configuration should be valid");
        }

        @Test
        @DisplayName("Configuration should be valid with all options enabled")
        void configurationShouldBeValidWithAllOptionsEnabled() {
            assertDoesNotThrow(() -> PatternConfiguration.builder()
                .outputFormat(PatternConfiguration.OutputFormat.HTML)
                .timeout(Duration.ofMinutes(1))
                .parallelExecution(true)
                .autoComplete(true)
                .enableMetrics(true)
                .withCommentary(true)
                .tokenAnalysis(true)
                .patternIds(List.of("WCP-1", "WCP-2"))
                .build(),
             "Configuration with all options enabled should be valid");
        }

        @Test
        @DisplayName("Configuration should be valid with all options disabled")
        void configurationShouldBeValidWithAllOptionsDisabled() {
            assertDoesNotThrow(() -> PatternConfiguration.builder()
                .outputFormat(PatternConfiguration.OutputFormat.JSON)
                .timeout(Duration.ofSeconds(10))
                .parallelExecution(false)
                .autoComplete(false)
                .enableMetrics(false)
                .withCommentary(false)
                .tokenAnalysis(false)
                .patternIds(Collections.emptyList())
                .build(),
             "Configuration with all options disabled should be valid");
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Configuration should handle minimum valid timeout")
        void configurationShouldHandleMinimumValidTimeout() {
            Duration minTimeout = Duration.ofNanos(1);
            PatternConfiguration config = PatternConfiguration.builder()
                .timeout(minTimeout)
                .build();

            assertEquals(minTimeout, config.getTimeout());
        }

        @Test
        @DisplayName("Configuration should handle maximum reasonable timeout")
        void configurationShouldHandleMaximumReasonableTimeout() {
            Duration maxTimeout = Duration.ofDays(7); // 1 week max
            PatternConfiguration config = PatternConfiguration.builder()
                .timeout(maxTimeout)
                .build();

            assertEquals(maxTimeout, config.getTimeout());
        }

        @Test
        @DisplayName("Configuration should handle empty pattern ID list")
        void configurationShouldHandleEmptyPatternIds() {
            PatternConfiguration config = PatternConfiguration.builder()
                .patternIds(Collections.emptyList())
                .build();

            assertTrue(config.getPatternIds().isEmpty());
            assertFalse(config.hasPatternIds());
        }

        @Test
        @DisplayName("Configuration should handle pattern list with single item")
        void configurationShouldHandleSinglePatternId() {
            List<String> singlePattern = List.of("WCP-1");
            PatternConfiguration config = PatternConfiguration.builder()
                .patternIds(singlePattern)
                .build();

            assertEquals(1, config.getPatternIds().size());
            assertEquals("WCP-1", config.getPatternIds().get(0));
            assertTrue(config.hasPatternIds());
        }

        @Test
        @DisplayName("Configuration should handle pattern list with many items")
        void configurationShouldHandleManyPatternIds() {
            List<String> manyPatterns = List.of(
                "WCP-1", "WCP-2", "WCP-3", "WCP-4", "WCP-5",
                "WCP-6", "WCP-7", "WCP-8", "WCP-9", "WCP-10"
            );
            PatternConfiguration config = PatternConfiguration.builder()
                .patternIds(manyPatterns)
                .build();

            assertEquals(10, config.getPatternIds().size());
            assertTrue(config.hasPatternIds());
        }

        @Test
        @DisplayName("Configuration should be immutable after construction")
        void configurationShouldBeImmutable() {
            PatternConfiguration config = PatternConfiguration.builder()
                .outputFormat(PatternConfiguration.OutputFormat.JSON)
                .build();

            // Check that fields are effectively final by accessing them
            assertNotNull(config.getOutputFormat());
            assertNotNull(config.getTimeout());
            assertNotNull(config.getPatternIds());

            // Pattern IDs should be unmodifiable
            assertThrows(UnsupportedOperationException.class,
                       () -> config.getPatternIds().add("WCP-11"),
                       "Pattern IDs list should be unmodifiable");
        }
    }

    @Nested
    @DisplayName("Method Behavior Tests")
    class MethodBehaviorTests {

        @Test
        @DisplayName("hasPatternIds should return true when pattern IDs exist")
        void hasPatternIdsShouldReturnTrueWhenPatternsExist() {
            PatternConfiguration config = PatternConfiguration.builder()
                .patternIds(List.of("WCP-1"))
                .build();

            assertTrue(config.hasPatternIds());
        }

        @Test
        @DisplayName("hasPatternIds should return false when no pattern IDs exist")
        void hasPatternIdsShouldReturnFalseWhenNoPatternsExist() {
            PatternConfiguration config = PatternConfiguration.builder()
                .patternIds(Collections.emptyList())
                .build();

            assertFalse(config.hasPatternIds());
        }

        @Test
        @DisplayName("Getters should return non-null values")
        void gettersShouldReturnNonNullValues() {
            PatternConfiguration config = PatternConfiguration.builder().build();

            assertNotNull(config.getOutputFormat());
            assertNotNull(config.getTimeout());
            assertNotNull(config.getPatternIds());
        }
    }

    @Nested
    @DisplayName("Static Constant Tests")
    class StaticConstantTests {

        @Test
        @DisplayName("Static constants should have expected values")
        void staticConstantsShouldHaveExpectedValues() {
            assertEquals(Duration.ofMinutes(5), PatternConfiguration.DEFAULT_TIMEOUT);
            assertTrue(PatternConfiguration.OUTPUT_FORMATTING_ENABLED);
            assertTrue(PatternConfiguration.PARALLEL_EXECUTION_ENABLED);
            assertTrue(PatternConfiguration.AUTO_COMPLETION_ENABLED);
            assertTrue(PatternConfiguration.METRICS_COLLECTION_ENABLED);
            assertFalse(PatternConfiguration.COMMENTARY_ENABLED);
            assertTrue(PatternConfiguration.TOKEN_ANALYSIS_ENABLED);
        }
    }
}