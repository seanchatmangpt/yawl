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

package org.yawlfoundation.yawl.mcp.a2a.demo.config;

import org.yawlfoundation.yawl.mcp.a2a.demo.config.PatternDemoConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.NullSource;

import java.time.Duration;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for PatternDemoConfig using Chicago TDD style.
 * Tests record behavior, builder validation, compact constructor, and edge cases.
 */
@DisplayName("PatternDemoConfig Configuration Tests")
class PatternDemoConfigTest {

    private static final int DEFAULT_TIMEOUT_SECONDS = PatternDemoConfig.DEFAULT_TIMEOUT_SECONDS;
    private static final String DEFAULT_OUTPUT_PATH = PatternDemoConfig.DEFAULT_OUTPUT_PATH;

    @Nested
    @DisplayName("Compact Constructor Tests")
    class CompactConstructorTests {

        @Test
        @DisplayName("Compact constructor should apply defaults for null values")
        void compactConstructorShouldApplyDefaultsForNulls() {
            // Test with null outputFormat - should default to CONSOLE
            PatternDemoConfig config1 = new PatternDemoConfig(
                null, "test", 300, true, true, true, true, true, false, null, null
            );
            assertEquals(PatternDemoConfig.OutputFormat.CONSOLE, config1.outputFormat());
            assertEquals("test", config1.outputPath());
            assertEquals(300, config1.timeoutSeconds());
            assertTrue(config1.enableTracing());
            assertTrue(config1.enableMetrics());
            assertTrue(config1.autoComplete());
            assertTrue(config1.parallelExecution());
            assertTrue(config1.tokenAnalysis());
            assertFalse(config1.withCommentary());
            assertEquals(0, config1.patternIds().size());
            assertEquals(0, config1.categories().size());
        }

        @Test
        @DisplayName("Compact constructor should apply defaults for empty string outputPath")
        void compactConstructorShouldApplyDefaultsForEmptyPath() {
            PatternDemoConfig config = new PatternDemoConfig(
                PatternDemoConfig.OutputFormat.JSON, "", 0, false, false, false, false, false, false, List.of(), List.of()
            );

            assertEquals(PatternDemoConfig.OutputFormat.JSON, config.outputFormat());
            assertEquals(DEFAULT_OUTPUT_PATH, config.outputPath());
            assertEquals(DEFAULT_TIMEOUT_SECONDS, config.timeoutSeconds());
            assertTrue(config.enableTracing()); // default
            assertTrue(config.enableMetrics()); // default
            assertTrue(config.autoComplete()); // default
            assertTrue(config.parallelExecution()); // default
            assertTrue(config.tokenAnalysis()); // default
            assertFalse(config.withCommentary()); // default
            assertEquals(1, config.patternIds().size());
            assertEquals(1, config.categories().size());
        }

        @Test
        @DisplayName("Compact constructor should apply defaults for invalid timeout")
        void compactConstructorShouldApplyDefaultsForInvalidTimeout() {
            PatternDemoConfig config = new PatternDemoConfig(
                PatternDemoConfig.OutputFormat.MARKDOWN, "test", -1, true, true, true, true, true, true, null, null
            );

            assertEquals(DEFAULT_TIMEOUT_SECONDS, config.timeoutSeconds());
        }

        @Test
        @DisplayName("Compact constructor should create unmodifiable lists")
        void compactConstructorShouldCreateUnmodifiableLists() {
            PatternDemoConfig config = new PatternDemoConfig(
                PatternDemoConfig.OutputFormat.HTML, "test", 300, true, true, true, true, true, true, List.of("WCP-1"), List.of(PatternCategory.BASIC)
            );

            assertThrows(UnsupportedOperationException.class,
                       () -> config.patternIds().add("WCP-2"),
                       "Pattern IDs list should be unmodifiable");

            assertThrows(UnsupportedOperationException.class,
                       () -> config.categories().add(PatternCategory.ADVANCED_BRANCHING),
                       "Categories list should be unmodifiable");
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        private PatternDemoConfig.Builder builder;

        @BeforeEach
        void setUp() {
            builder = PatternDemoConfig.builder();
        }

        @Test
        @DisplayName("Builder should create configuration with default values")
        void builderShouldCreateDefaultConfiguration() {
            PatternDemoConfig config = builder.build();

            assertEquals(PatternDemoConfig.OutputFormat.CONSOLE, config.outputFormat());
            assertEquals(DEFAULT_OUTPUT_PATH, config.outputPath());
            assertEquals(DEFAULT_TIMEOUT_SECONDS, config.timeoutSeconds());
            assertTrue(config.enableTracing());
            assertTrue(config.enableMetrics());
            assertTrue(config.autoComplete());
            assertTrue(config.parallelExecution());
            assertTrue(config.tokenAnalysis());
            assertFalse(config.withCommentary());
            assertEquals(1, config.patternIds().size());
            assertEquals(1, config.categories().size());
            assertEquals("DEFAULT", config.patternIds().get(0));
            assertEquals(PatternCategory.BASIC, config.categories().get(0));
        }

        @Test
        @DisplayName("Builder should set output format")
        void builderShouldSetOutputFormat() {
            PatternDemoConfig config = builder
                .outputFormat(PatternDemoConfig.OutputFormat.HTML)
                .build();

            assertEquals(PatternDemoConfig.OutputFormat.HTML, config.outputFormat());
        }

        @Test
        @DisplayName("Builder should set output path")
        void builderShouldSetOutputPath() {
            String customPath = "custom-report";
            PatternDemoConfig config = builder
                .outputPath(customPath)
                .build();

            assertEquals(customPath, config.outputPath());
        }

        @Test
        @DisplayName("Builder should set timeout seconds")
        void builderShouldSetTimeoutSeconds() {
            int customTimeout = 120;
            PatternDemoConfig config = builder
                .timeoutSeconds(customTimeout)
                .build();

            assertEquals(customTimeout, config.timeoutSeconds());
        }

        @Test
        @DisplayName("Builder should set timeout with negative value")
        void builderShouldSetTimeoutWithNegativeValue() {
            int negativeTimeout = -60;
            PatternDemoConfig config = builder
                .timeoutSeconds(negativeTimeout)
                .build();

            // Should apply default timeout for negative values
            assertEquals(DEFAULT_TIMEOUT_SECONDS, config.timeoutSeconds());
        }

        @Test
        @DisplayName("Builder should set timeout with zero value")
        void builderShouldSetTimeoutWithZeroValue() {
            int zeroTimeout = 0;
            PatternDemoConfig config = builder
                .timeoutSeconds(zeroTimeout)
                .build();

            // Should apply default timeout for zero values
            assertEquals(DEFAULT_TIMEOUT_SECONDS, config.timeoutSeconds());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        @DisplayName("Builder should set tracing flag")
        void builderShouldSetTracingFlag(boolean enabled) {
            PatternDemoConfig config = builder
                .enableTracing(enabled)
                .build();

            assertEquals(enabled, config.enableTracing());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        @DisplayName("Builder should set metrics flag")
        void builderShouldSetMetricsFlag(boolean enabled) {
            PatternDemoConfig config = builder
                .enableMetrics(enabled)
                .build();

            assertEquals(enabled, config.enableMetrics());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        @DisplayName("Builder should set auto-complete flag")
        void builderShouldSetAutoCompleteFlag(boolean enabled) {
            PatternDemoConfig config = builder
                .autoComplete(enabled)
                .build();

            assertEquals(enabled, config.autoComplete());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        @DisplayName("Builder should set parallel execution flag")
        void builderShouldSetParallelExecutionFlag(boolean enabled) {
            PatternDemoConfig config = builder
                .parallelExecution(enabled)
                .build();

            assertEquals(enabled, config.parallelExecution());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        @DisplayName("Builder should set token analysis flag")
        void builderShouldSetTokenAnalysisFlag(boolean enabled) {
            PatternDemoConfig config = builder
                .tokenAnalysis(enabled)
                .build();

            assertEquals(enabled, config.tokenAnalysis());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        @DisplayName("Builder should set commentary flag")
        void builderShouldSetCommentaryFlag(boolean enabled) {
            PatternDemoConfig config = builder
                .withCommentary(enabled)
                .build();

            assertEquals(enabled, config.withCommentary());
        }

        @Test
        @DisplayName("Builder should add single pattern ID")
        void builderShouldAddSinglePatternId() {
            builder.addPatternId("WCP-1");
            PatternDemoConfig config = builder.build();

            assertEquals(1, config.patternIds().size());
            assertEquals("WCP-1", config.patternIds().get(0));
            assertTrue(config.hasPatternFilter());
        }

        @Test
        @DisplayName("Builder should add multiple pattern IDs")
        void builderShouldAddMultiplePatternIds() {
            builder.addPatternId("WCP-1");
            builder.addPatternId("WCP-2");
            builder.addPatternId("WCP-3");
            PatternDemoConfig config = builder.build();

            assertEquals(3, config.patternIds().size());
            assertTrue(config.hasPatternFilter());
        }

        @Test
        @DisplayName("Builder should ignore null pattern ID")
        void builderShouldIgnoreNullPatternId() {
            builder.addPatternId(null);
            builder.addPatternId("WCP-1");
            PatternDemoConfig config = builder.build();

            assertEquals(1, config.patternIds().size());
            assertEquals("WCP-1", config.patternIds().get(0));
        }

        @Test
        @DisplayName("Builder should ignore blank pattern ID")
        void builderShouldIgnoreBlankPatternId() {
            builder.addPatternId("");
            builder.addPatternId("WCP-1");
            builder.addPatternId("   ");
            PatternDemoConfig config = builder.build();

            assertEquals(1, config.patternIds().size());
            assertEquals("WCP-1", config.patternIds().get(0));
        }

        @Test
        @DisplayName("Builder should add single category")
        void builderShouldAddSingleCategory() {
            builder.addCategory(PatternCategory.ADVANCED_BRANCHING);
            PatternDemoConfig config = builder.build();

            assertEquals(1, config.categories().size());
            assertEquals(PatternCategory.ADVANCED_BRANCHING, config.categories().get(0));
            assertTrue(config.hasCategoryFilter());
        }

        @Test
        @DisplayName("Builder should add multiple categories")
        void builderShouldAddMultipleCategories() {
            builder.addCategory(PatternCategory.BASIC);
            builder.addCategory(PatternCategory.ADVANCED_BRANCHING);
            builder.addCategory(PatternCategory.ITERATION);
            PatternDemoConfig config = builder.build();

            assertEquals(3, config.categories().size());
            assertTrue(config.hasCategoryFilter());
        }

        @Test
        @DisplayName("Builder should ignore null category")
        void builderShouldIgnoreNullCategory() {
            builder.addCategory(null);
            builder.addCategory(PatternCategory.BASIC);
            PatternDemoConfig config = builder.build();

            assertEquals(1, config.categories().size());
            assertEquals(PatternCategory.BASIC, config.categories().get(0));
        }

        @Test
        @DisplayName("Builder should set pattern IDs list")
        void builderShouldSetPatternIdsList() {
            List<String> patterns = List.of("WCP-1", "WCP-2", "WCP-3");
            PatternDemoConfig config = builder
                .patternIds(patterns)
                .build();

            assertEquals(3, config.patternIds().size());
            assertEquals(patterns, config.patternIds());
            assertTrue(config.hasPatternFilter());
        }

        @Test
        @DisplayName("Builder should set categories list")
        void builderShouldSetCategoriesList() {
            List<PatternCategory> categories = List.of(
                PatternCategory.BASIC, PatternCategory.ADVANCED_BRANCHING
            );
            PatternDemoConfig config = builder
                .categories(categories)
                .build();

            assertEquals(2, config.categories().size());
            assertEquals(categories, config.categories());
            assertTrue(config.hasCategoryFilter());
        }

        @Test
        @DisplayName("Builder should clear existing patterns when setting new list")
        void builderShouldClearExistingPatternsWhenSettingNewList() {
            builder.addPatternId("WCP-1");
            builder.addCategory(PatternCategory.BASIC);

            // Override with new list
            builder.patternIds(List.of("WCP-2", "WCP-3"));
            PatternDemoConfig config = builder.build();

            assertEquals(2, config.patternIds().size());
            assertFalse(config.patternIds().contains("WCP-1"));
        }
    }

    @Nested
    @DisplayName("Utility Method Tests")
    class UtilityMethodTests {

        private PatternDemoConfig createDefaultConfig() {
            return PatternDemoConfig.defaults();
        }

        private PatternDemoConfig createConfigWithFilters() {
            return PatternDemoConfig.builder()
                .addPatternId("WCP-1")
                .addCategory(PatternCategory.ADVANCED_BRANCHING)
                .build();
        }

        @Test
        @DisplayName("getTimeoutDuration should return correct Duration")
        void getTimeoutDurationShouldReturnCorrectDuration() {
            PatternDemoConfig config = createDefaultConfig();
            Duration expected = Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS);

            assertEquals(expected, config.getTimeoutDuration());
        }

        @Test
        @DisplayName("getOutputPathAsPath should return Path object")
        void getOutputPathAsPathShouldReturnPathObject() {
            String customPath = "custom/path/report";
            PatternDemoConfig config = PatternDemoConfig.builder()
                .outputPath(customPath)
                .build();

            Path expectedPath = Paths.get(customPath);
            assertEquals(expectedPath, config.getOutputPathAsPath());
        }

        @Test
        @DisplayName("getOutputFilePath should include extension")
        void getOutputFilePathShouldIncludeExtension() {
            PatternDemoConfig config = PatternDemoConfig.builder()
                .outputFormat(PatternDemoConfig.OutputFormat.JSON)
                .outputPath("report")
                .build();

            assertEquals("report.json", config.getOutputFilePath());
        }

        @Test
        @DisplayName("getOutputFilePath should handle path with existing extension")
        void getOutputFilePathShouldHandlePathWithExistingExtension() {
            PatternDemoConfig config = PatternDemoConfig.builder()
                .outputFormat(PatternDemoConfig.OutputFormat.JSON)
                .outputPath("report.json")
                .build();

            assertEquals("report.json", config.getOutputFilePath());
        }

        @Test
        @DisplayName("hasPatternFilter should return true when patterns are specified")
        void hasPatternFilterShouldReturnTrueWhenPatternsSpecified() {
            PatternDemoConfig config = createConfigWithFilters();
            assertTrue(config.hasPatternFilter());
        }

        @Test
        @DisplayName("hasPatternFilter should return false when no patterns specified")
        void hasPatternFilterShouldReturnFalseWhenNoPatternsSpecified() {
            PatternDemoConfig config = createDefaultConfig();
            assertFalse(config.hasPatternFilter());
        }

        @Test
        @DisplayName("hasCategoryFilter should return true when categories are specified")
        void hasCategoryFilterShouldReturnTrueWhenCategoriesSpecified() {
            PatternDemoConfig config = createConfigWithFilters();
            assertTrue(config.hasCategoryFilter());
        }

        @Test
        @DisplayName("hasCategoryFilter should return false when no categories specified")
        void hasCategoryFilterShouldReturnFalseWhenNoCategoriesSpecified() {
            PatternDemoConfig config = createDefaultConfig();
            assertFalse(config.hasCategoryFilter());
        }

        @Test
        @DisplayName("shouldExecutePattern should execute when no filters are set")
        void shouldExecutePatternShouldExecuteWhenNoFiltersSet() {
            PatternDemoConfig config = createDefaultConfig();
            assertTrue(config.shouldExecutePattern("WCP-1", PatternCategory.BASIC));
            assertTrue(config.shouldExecutePattern("WCP-100", PatternCategory.AI_ML));
        }

        @Test
        @DisplayName("shouldExecutePattern should respect pattern filter")
        void shouldExecutePatternShouldRespectPatternFilter() {
            PatternDemoConfig config = PatternDemoConfig.builder()
                .addPatternId("WCP-1")
                .build();

            assertTrue(config.shouldExecutePattern("WCP-1", PatternCategory.BASIC));
            assertFalse(config.shouldExecutePattern("WCP-2", PatternCategory.BASIC));
        }

        @Test
        @DisplayName("shouldExecutePattern should respect category filter")
        void shouldExecutePatternShouldRespectCategoryFilter() {
            PatternDemoConfig config = PatternDemoConfig.builder()
                .addCategory(PatternCategory.ADVANCED_BRANCHING)
                .build();

            assertTrue(config.shouldExecutePattern("WCP-1", PatternCategory.ADVANCED_BRANCHING));
            assertFalse(config.shouldExecutePattern("WCP-6", PatternCategory.ITERATION));
        }

        @Test
        @DisplayName("shouldExecutePattern should require both pattern and category match when both filters are set")
        void shouldExecutePatternShouldRequireBothMatchesWhenBothFiltersSet() {
            PatternDemoConfig config = PatternDemoConfig.builder()
                .addPatternId("WCP-1")
                .addCategory(PatternCategory.ADVANCED_BRANCHING)
                .build();

            // Pattern ID matches but category doesn't
            assertFalse(config.shouldExecutePattern("WCP-1", PatternCategory.AI_ML));

            // Category matches but pattern ID doesn't
            assertFalse(config.shouldExecutePattern("WCP-6", PatternCategory.ADVANCED_BRANCHING));

            // Both match
            assertTrue(config.shouldExecutePattern("WCP-1", PatternCategory.ADVANCED_BRANCHING));
        }
    }

    @Nested
    @DisplayName("Copy Method Tests")
    class CopyMethodTests {

        @Test
        @DisplayName("withOutputFormat should create new config with updated format")
        void withOutputFormatShouldCreateNewConfigWithUpdatedFormat() {
            PatternDemoConfig original = PatternDemoConfig.builder()
                .outputFormat(PatternDemoConfig.OutputFormat.CONSOLE)
                .build();

            PatternDemoConfig modified = original.withOutputFormat(PatternDemoConfig.OutputFormat.HTML);

            assertNotSame(original, modified);
            assertEquals(PatternDemoConfig.OutputFormat.HTML, modified.outputFormat());
            assertEquals(original.outputPath(), modified.outputPath());
            assertEquals(original.timeoutSeconds(), modified.timeoutSeconds());
        }

        @Test
        @DisplayName("withTimeout should create new config with updated timeout")
        void withTimeoutShouldCreateNewConfigWithUpdatedTimeout() {
            PatternDemoConfig original = PatternDemoConfig.builder()
                .timeoutSeconds(300)
                .build();

            int newTimeout = 600;
            PatternDemoConfig modified = original.withTimeout(newTimeout);

            assertNotSame(original, modified);
            assertEquals(newTimeout, modified.timeoutSeconds());
            assertEquals(original.outputFormat(), modified.outputFormat());
            assertEquals(original.outputPath(), modified.outputPath());
        }
    }

    @Nested
    @DisplayName("Default Configuration Tests")
    class DefaultConfigurationTests {

        @Test
        @DisplayName("defaults() should create valid configuration")
        void defaultsShouldCreateValidConfiguration() {
            PatternDemoConfig config = PatternDemoConfig.defaults();

            assertEquals(PatternDemoConfig.OutputFormat.CONSOLE, config.outputFormat());
            assertEquals(DEFAULT_OUTPUT_PATH, config.outputPath());
            assertEquals(DEFAULT_TIMEOUT_SECONDS, config.timeoutSeconds());
            assertTrue(config.enableTracing());
            assertTrue(config.enableMetrics());
            assertTrue(config.autoComplete());
            assertTrue(config.parallelExecution());
            assertTrue(config.tokenAnalysis());
            assertFalse(config.withCommentary());
            assertEquals(1, config.patternIds().size());
            assertEquals(1, config.categories().size());
            assertEquals("DEFAULT", config.patternIds().get(0));
            assertEquals(PatternCategory.BASIC, config.categories().get(0));
        }
    }

    @Nested
    @DisplayName("OutputFormat Tests")
    class OutputFormatTests {

        @Test
        @DisplayName("OutputFormat enum should have all expected values")
        void outputFormatShouldHaveAllExpectedValues() {
            assertEquals(4, PatternDemoConfig.OutputFormat.values().length);

            // Test all values have valid extensions
            Stream.of(PatternDemoConfig.OutputFormat.values())
                .forEach(format -> {
                    assertNotNull(format.getFileExtension(),
                               "All formats should have valid extensions");
                    assertTrue(format.getFileExtension().startsWith("."),
                               "Extension should start with dot");
                });
        }

        @Test
        @DisplayName("OutputFormat fromString should handle null input")
        void outputFormatFromStringShouldHandleNullInput() {
            PatternDemoConfig.OutputFormat result = PatternDemoConfig.OutputFormat.fromString(null);
            assertEquals(PatternDemoConfig.OutputFormat.CONSOLE, result);
        }

        @Test
        @DisplayName("OutputFormat fromString should handle blank input")
        void outputFormatFromStringShouldHandleBlankInput() {
            PatternDemoConfig.OutputFormat result = PatternDemoConfig.OutputFormat.fromString("  ");
            assertEquals(PatternDemoConfig.OutputFormat.CONSOLE, result);
        }

        @Test
        @DisplayName("OutputFormat fromString should handle case insensitive input")
        void outputFormatFromStringShouldHandleCaseInsensitiveInput() {
            assertEquals(PatternDemoConfig.OutputFormat.CONSOLE,
                       PatternDemoConfig.OutputFormat.fromString("console"));
            assertEquals(PatternDemoConfig.OutputFormat.JSON,
                       PatternDemoConfig.OutputFormat.fromString("JSON"));
            assertEquals(PatternDemoConfig.OutputFormat.MARKDOWN,
                       PatternDemoConfig.OutputFormat.fromString("Markdown"));
            assertEquals(PatternDemoConfig.OutputFormat.HTML,
                       PatternDemoConfig.OutputFormat.fromString("hTmL"));
        }

        @Test
        @DisplayName("OutputFormat fromString should return CONSOLE for invalid input")
        void outputFormatFromStringShouldReturnConsoleForInvalidInput() {
            PatternDemoConfig.OutputFormat result = PatternDemoConfig.OutputFormat.fromString("invalid");
            assertEquals(PatternDemoConfig.OutputFormat.CONSOLE, result);
        }
    }
}