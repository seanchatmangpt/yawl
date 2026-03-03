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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.NullSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for PatternDemoConfig command line parsing using Chicago TDD style.
 * Tests all command line argument parsing, error handling, and edge cases.
 */
@DisplayName("PatternDemoConfig Command Line Tests")
class PatternDemoConfigCommandLineTest {

    @Nested
    @DisplayName("Basic Argument Parsing Tests")
    class BasicArgumentParsingTests {

        @Test
        @DisplayName("parse() should create default config when no arguments provided")
        void parseShouldCreateDefaultConfig() {
            String[] args = {};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals(PatternDemoConfig.OutputFormat.CONSOLE, config.outputFormat());
            assertEquals(PatternDemoConfig.DEFAULT_OUTPUT_PATH, config.outputPath());
            assertEquals(PatternDemoConfig.DEFAULT_TIMEOUT_SECONDS, config.timeoutSeconds());
            assertTrue(config.enableTracing());
            assertTrue(config.enableMetrics());
            assertTrue(config.autoComplete());
            assertTrue(config.parallelExecution());
            assertTrue(config.tokenAnalysis());
            assertFalse(config.withCommentary());
            assertTrue(config.patternIds().isEmpty());
            assertTrue(config.categories().isEmpty());
        }

        @ParameterizedTest
        @ValueSource(strings = {"-f", "--format"})
        @DisplayName("format option should parse output format")
        void formatOptionShouldParseOutputFormat(String formatFlag) {
            String[] args = {formatFlag, "json"};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals(PatternDemoConfig.OutputFormat.JSON, config.outputFormat());
        }

        @ParameterizedTest
        @ValueSource(strings = {"-f", "--format"})
        @DisplayName("format option should be case insensitive")
        void formatOptionShouldBeCaseInsensitive(String formatFlag) {
            String[] args = {formatFlag, "Markdown"};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals(PatternDemoConfig.OutputFormat.MARKDOWN, config.outputFormat());
        }

        @ParameterizedTest
        @ValueSource(strings = {"-o", "--output"})
        @DisplayName("output option should parse output path")
        void outputOptionShouldParseOutputPath(String outputFlag) {
            String[] args = {outputFlag, "custom-report"};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals("custom-report", config.outputPath());
        }

        @ParameterizedTest
        @ValueSource(strings = {"-t", "--timeout"})
        @DisplayName("timeout option should parse timeout value")
        void timeoutOptionShouldParseTimeoutValue(String timeoutFlag) {
            String[] args = {timeoutFlag, "120"};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals(120, config.timeoutSeconds());
        }

        @ParameterizedTest
        @ValueSource(strings = {"-t", "--timeout"})
        @DisplayName("timeout option should handle invalid values")
        void timeoutOptionShouldHandleInvalidValues(String timeoutFlag) {
            String[] args = {timeoutFlag, "invalid"};
            assertThrows(IllegalArgumentException.class,
                       () -> PatternDemoConfig.fromCommandLine(args),
                       "Should throw for invalid timeout value");
        }

        @ParameterizedTest
        @ValueSource(strings = {"-t", "--timeout"})
        @DisplayName("timeout option should not allow negative values")
        void timeoutOptionShouldNotAllowNegativeValues(String timeoutFlag) {
            String[] args = {timeoutFlag, "-60"};
            assertThrows(IllegalArgumentException.class,
                       () -> PatternDemoConfig.fromCommandLine(args),
                       "Should throw for negative timeout value");
        }

        @ParameterizedTest
        @ValueSource(strings = {"-t", "--timeout"})
        @DisplayName("timeout option should not allow zero values")
        void timeoutOptionShouldNotAllowZeroValues(String timeoutFlag) {
            String[] args = {timeoutFlag, "0"};
            assertThrows(IllegalArgumentException.class,
                       () -> PatternDemoConfig.fromCommandLine(args),
                       "Should throw for zero timeout value");
        }

        @ParameterizedTest
        @ValueSource(strings = {"--no-tracing"})
        @DisplayName("no-tracing option should disable tracing")
        void noTracingOptionShouldDisableTracing(String flag) {
            String[] args = {flag};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertFalse(config.enableTracing());
        }

        @ParameterizedTest
        @ValueSource(strings = {"--no-metrics"})
        @DisplayName("no-metrics option should disable metrics")
        void noMetricsOptionShouldDisableMetrics(String flag) {
            String[] args = {flag};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertFalse(config.enableMetrics());
        }

        @ParameterizedTest
        @ValueSource(strings = {"--no-auto-complete"})
        @DisplayName("no-auto-complete option should disable auto-completion")
        void noAutoCompleteOptionShouldDisableAutoComplete(String flag) {
            String[] args = {flag};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertFalse(config.autoComplete());
        }

        @ParameterizedTest
        @ValueSource(strings = {"--no-parallel"})
        @DisplayName("no-parallel option should disable parallel execution")
        void noParallelOptionShouldDisableParallelExecution(String flag) {
            String[] args = {flag};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertFalse(config.parallelExecution());
        }

        @ParameterizedTest
        @ValueSource(strings = {"--no-token-analysis"})
        @DisplayName("no-token-analysis option should disable token analysis")
        void noTokenAnalysisOptionShouldDisableTokenAnalysis(String flag) {
            String[] args = {flag};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertFalse(config.tokenAnalysis());
        }

        @ParameterizedTest
        @ValueSource(strings = {"--commentary"})
        @DisplayName("commentary option should enable commentary")
        void commentaryOptionShouldEnableCommentary(String flag) {
            String[] args = {flag};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertTrue(config.withCommentary());
        }

        @ParameterizedTest
        @ValueSource(strings = {"-p", "--patterns"})
        @DisplayName("patterns option should parse comma-separated pattern IDs")
        void patternsOptionShouldParseCommaSeparatedPatterns(String patternsFlag) {
            String[] args = {patternsFlag, "WCP-1,WCP-2,WCP-3"};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals(3, config.patternIds().size());
            assertTrue(config.patternIds().contains("WCP-1"));
            assertTrue(config.patternIds().contains("WCP-2"));
            assertTrue(config.patternIds().contains("WCP-3"));
            assertTrue(config.hasPatternFilter());
        }

        @ParameterizedTest
        @ValueSource(strings = {"-p", "--patterns"})
        @DisplayName("patterns option should handle single pattern")
        void patternsOptionShouldHandleSinglePattern(String patternsFlag) {
            String[] args = {patternsFlag, "WCP-1"};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals(1, config.patternIds().size());
            assertEquals("WCP-1", config.patternIds().get(0));
        }

        @ParameterizedTest
        @ValueSource(strings = {"-c", "--categories"})
        @DisplayName("categories option should parse comma-separated categories")
        void categoriesOptionShouldParseCommaSeparatedCategories(String categoriesFlag) {
            String[] args = {categoriesFlag, "BASIC,ADVANCED_BRANCHING,ITERATION"};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals(3, config.categories().size());
            assertTrue(config.categories().contains(PatternCategory.BASIC));
            assertTrue(config.categories().contains(PatternCategory.ADVANCED_BRANCHING));
            assertTrue(config.categories().contains(PatternCategory.ITERATION));
            assertTrue(config.hasCategoryFilter());
        }

        @ParameterizedTest
        @ValueSource(strings = {"-c", "--categories"})
        @DisplayName("categories option should handle single category")
        void categoriesOptionShouldHandleSingleCategory(String categoriesFlag) {
            String[] args = {categoriesFlag, "BASIC"};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals(1, config.categories().size());
            assertEquals(PatternCategory.BASIC, config.categories().get(0));
        }

        @ParameterizedTest
        @ValueSource(strings = {"-c", "--categories"})
        @DisplayName("categories option should be case insensitive")
        void categoriesOptionShouldBeCaseInsensitive(String categoriesFlag) {
            String[] args = {categoriesFlag, "basic,adVanced_branching"};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals(2, config.categories().size());
            assertEquals(PatternCategory.BASIC, config.categories().get(0));
            assertEquals(PatternCategory.ADVANCED_BRANCHING, config.categories().get(1));
        }

        @ParameterizedTest
        @ValueSource(strings = {"-h", "--help"})
        @DisplayName("help option should throw HelpRequestedException")
        void helpOptionShouldExit(String helpFlag) {
            String[] args = {helpFlag};

            // Help now throws HelpRequestedException instead of System.exit()
            // This allows tests to run without crashing the VM
            assertThrows(PatternDemoConfig.HelpRequestedException.class, () -> {
                PatternDemoConfig.fromCommandLine(args);
            });
        }
    }

    @Nested
    @DisplayName("Combined Arguments Tests")
    class CombinedArgumentsTests {

        @Test
        @DisplayName("multiple options should combine correctly")
        void multipleOptionsShouldCombineCorrectly() {
            String[] args = {
                "-f", "json",
                "-o", "custom-report",
                "-t", "60",
                "--commentary",
                "-p", "WCP-1,WCP-2"
            };
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals(PatternDemoConfig.OutputFormat.JSON, config.outputFormat());
            assertEquals("custom-report", config.outputPath());
            assertEquals(60, config.timeoutSeconds());
            assertTrue(config.withCommentary());
            assertEquals(2, config.patternIds().size());
            assertTrue(config.patternIds().contains("WCP-1"));
            assertTrue(config.patternIds().contains("WCP-2"));
        }

        @Test
        @DisplayName("options should be order independent")
        void optionsShouldBeOrderIndependent() {
            String[] args1 = {"-f", "html", "-t", "30", "-p", "WCP-1"};
            String[] args2 = {"-t", "30", "-p", "WCP-1", "-f", "html"};
            String[] args3 = {"-p", "WCP-1", "-f", "html", "-t", "30"};

            PatternDemoConfig config1 = PatternDemoConfig.fromCommandLine(args1);
            PatternDemoConfig config2 = PatternDemoConfig.fromCommandLine(args2);
            PatternDemoConfig config3 = PatternDemoConfig.fromCommandLine(args3);

            assertEquals(config1.outputFormat(), config2.outputFormat());
            assertEquals(config1.outputFormat(), config3.outputFormat());
            assertEquals(config1.timeoutSeconds(), config2.timeoutSeconds());
            assertEquals(config1.timeoutSeconds(), config3.timeoutSeconds());
            assertEquals(config1.patternIds(), config2.patternIds());
            assertEquals(config1.patternIds(), config3.patternIds());
        }

        @Test
        @DisplayName("enabling and disabling flags should work together")
        void enablingAndDisablingFlagsShouldWorkTogether() {
            String[] args = {
                "--no-metrics",
                "--commentary",
                "--no-tracing"
            };
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertFalse(config.enableMetrics());
            assertTrue(config.withCommentary());
            assertFalse(config.enableTracing());
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("missing value for format should throw")
        void missingValueForFormatShouldThrow() {
            String[] args = {"--format"};
            assertThrows(IllegalArgumentException.class,
                       () -> PatternDemoConfig.fromCommandLine(args),
                       "Should throw when format value is missing");
        }

        @Test
        @DisplayName("missing value for output should throw")
        void missingValueForOutputShouldThrow() {
            String[] args = {"--output"};
            assertThrows(IllegalArgumentException.class,
                       () -> PatternDemoConfig.fromCommandLine(args),
                       "Should throw when output value is missing");
        }

        @Test
        @DisplayName("missing value for timeout should throw")
        void missingValueForTimeoutShouldThrow() {
            String[] args = {"--timeout"};
            assertThrows(IllegalArgumentException.class,
                       () -> PatternDemoConfig.fromCommandLine(args),
                       "Should throw when timeout value is missing");
        }

        @Test
        @DisplayName("missing value for patterns should throw")
        void missingValueForPatternsShouldThrow() {
            String[] args = {"--patterns"};
            assertThrows(IllegalArgumentException.class,
                       () -> PatternDemoConfig.fromCommandLine(args),
                       "Should throw when patterns value is missing");
        }

        @Test
        @DisplayName("missing value for categories should throw")
        void missingValueForCategoriesShouldThrow() {
            String[] args = {"--categories"};
            assertThrows(IllegalArgumentException.class,
                       () -> PatternDemoConfig.fromCommandLine(args),
                       "Should throw when categories value is missing");
        }

        @Test
        @DisplayName("unknown option should throw")
        void unknownOptionShouldThrow() {
            String[] args = {"--unknown-option"};
            assertThrows(IllegalArgumentException.class,
                       () -> PatternDemoConfig.fromCommandLine(args),
                       "Should throw for unknown option");
        }

        @Test
        @DisplayName("null args should throw")
        void nullArgsShouldThrow() {
            assertThrows(IllegalArgumentException.class,
                       () -> PatternDemoConfig.fromCommandLine(null),
                       "Should throw for null args");
        }

        @Test
        @DisplayName("empty args array should work")
        void emptyArgsArrayShouldWork() {
            String[] args = {};
            assertDoesNotThrow(() -> PatternDemoConfig.fromCommandLine(args),
                             "Empty args should not throw");
        }

        @ParameterizedTest
        @ValueSource(strings = {"-f", "--format"})
        @DisplayName("invalid format should default to CONSOLE")
        void invalidFormatShouldDefaultToConsole(String formatFlag) {
            String[] args = {formatFlag, "invalid-format"};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals(PatternDemoConfig.OutputFormat.CONSOLE, config.outputFormat());
        }

        @ParameterizedTest
        @ValueSource(strings = {"-c", "--categories"})
        @DisplayName("unknown category should throw")
        void unknownCategoryShouldThrow(String categoriesFlag) {
            String[] args = {categoriesFlag, "UNKNOWN_CATEGORY"};
            assertThrows(IllegalArgumentException.class,
                       () -> PatternDemoConfig.fromCommandLine(args),
                       "Should throw for unknown category");
        }

        @ParameterizedTest
        @ValueSource(strings = {"-c", "--categories"})
        @DisplayName("category with whitespace should be trimmed")
        void categoryWithWhitespaceShouldBeTrimmed(String categoriesFlag) {
            String[] args = {categoriesFlag, "  BASIC  ,  ADVANCED_BRANCHING  "};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals(2, config.categories().size());
            assertEquals(PatternCategory.BASIC, config.categories().get(0));
            assertEquals(PatternCategory.ADVANCED_BRANCHING, config.categories().get(1));
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("empty string patterns should be ignored")
        void emptyStringPatternsShouldBeIgnored() {
            String[] args = {"-p", "WCP-1,,WCP-2,"};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals(2, config.patternIds().size());
            assertTrue(config.patternIds().contains("WCP-1"));
            assertTrue(config.patternIds().contains("WCP-2"));
        }

        @Test
        @DisplayName("whitespace-only patterns should be ignored")
        void whitespaceOnlyPatternsShouldBeIgnored() {
            String[] args = {"-p", "WCP-1,   ,WCP-2, \t\n "};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals(2, config.patternIds().size());
            assertTrue(config.patternIds().contains("WCP-1"));
            assertTrue(config.patternIds().contains("WCP-2"));
        }

        @Test
        @DisplayName("patterns with mixed case should be preserved as provided")
        void patternsWithMixedCaseShouldBePreserved() {
            String[] args = {"-p", "wcp-1,WCP-2,wCp-3"};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals(3, config.patternIds().size());
            assertTrue(config.patternIds().contains("wcp-1"));
            assertTrue(config.patternIds().contains("WCP-2"));
            assertTrue(config.patternIds().contains("wCp-3"));
        }

        @Test
        @DisplayName("categories with mixed case should be normalized")
        void categoriesWithMixedCaseShouldBeNormalized() {
            String[] args = {"-c", "bAsIc,AdVanced_BrAnChInG"};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals(2, config.categories().size());
            assertEquals(PatternCategory.BASIC, config.categories().get(0));
            assertEquals(PatternCategory.ADVANCED_BRANCHING, config.categories().get(1));
        }

        @Test
        @DisplayName("timeout with leading/trailing spaces should be parsed")
        void timeoutWithSpacesShouldBeParsed() {
            String[] args = {"-t", " 120 "};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals(120, config.timeoutSeconds());
        }

        @Test
        @DisplayName("format with leading/trailing spaces should be trimmed")
        void formatWithSpacesShouldBeTrimmed() {
            String[] args = {"-f", "  json  "};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals(PatternDemoConfig.OutputFormat.JSON, config.outputFormat());
        }

        @Test
        @DisplayName("output path with spaces should be preserved")
        void outputPathWithSpacesShouldBePreserved() {
            String[] args = {"-o", "custom report name"};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals("custom report name", config.outputPath());
        }

        @Test
        @DisplayName("patterns option with many patterns should work")
        void patternsOptionWithManyPatternsShouldWork() {
            String[] args = {"-p", "WCP-1,WCP-2,WCP-3,WCP-4,WCP-5,WCP-6,WCP-7,WCP-8,WCP-9,WCP-10"};
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals(10, config.patternIds().size());
            assertTrue(config.hasPatternFilter());
        }

        @Test
        @DisplayName("categories option with many categories should work")
        void categoriesOptionWithManyCategoriesShouldWork() {
            String[] args = {
                "-c", "BASIC,ADVANCED_BRANCHING,ITERATION,STATE_BASED,DISTRIBUTED," +
                      "EVENT_DRIVEN,AI_ML,ENTERPRISE,AGENT,GREGVERSE_SCENARIO"
            };
            PatternDemoConfig config = PatternDemoConfig.fromCommandLine(args);

            assertEquals(10, config.categories().size());
            assertTrue(config.hasCategoryFilter());
        }
    }

    @Nested
    @DisplayName("Help Text Tests")
    class HelpTextTests {

        @Test
        @DisplayName("getHelpText should return non-empty string")
        void getHelpTextShouldReturnNonEmptyString() {
            String helpText = PatternDemoConfig.CommandLineParser.getHelpText();
            assertNotNull(helpText);
            assertFalse(helpText.trim().isEmpty(), "Help text should not be empty");
            assertTrue(helpText.contains("YAWL Pattern Demo Runner"),
                       "Help text should contain program name");
            assertTrue(helpText.contains("Usage:"),
                       "Help text should contain usage information");
            assertTrue(helpText.contains("Options:"),
                       "Help text should contain options section");
        }

        @Test
        @DisplayName("help text should contain all expected options")
        void helpTextShouldContainAllExpectedOptions() {
            String helpText = PatternDemoConfig.CommandLineParser.getHelpText();
            String[] expectedOptions = {
                "-f, --format",
                "-o, --output",
                "-t, --timeout",
                "--no-tracing",
                "--no-metrics",
                "--no-auto-complete",
                "--no-parallel",
                "--no-token-analysis",
                "--commentary",
                "-p, --patterns",
                "-c, --categories",
                "-h, --help"
            };

            for (String option : expectedOptions) {
                assertTrue(helpText.contains(option),
                           "Help text should contain option: " + option);
            }
        }

        @Test
        @DisplayName("help text should contain examples")
        void helpTextShouldContainExamples() {
            String helpText = PatternDemoConfig.CommandLineParser.getHelpText();
            assertTrue(helpText.contains("Examples:"),
                       "Help text should contain examples section");
            assertTrue(helpText.contains("--format"),
                       "Help text should contain examples with format");
            assertTrue(helpText.contains("--timeout"),
                       "Help text should contain examples with timeout");
            assertTrue(helpText.contains("--patterns"),
                       "Help text should contain examples with patterns");
        }

        @Test
        @DisplayName("help text should contain all categories")
        void helpTextShouldContainAllCategories() {
            String helpText = PatternDemoConfig.CommandLineParser.getHelpText();
            String[] expectedCategories = {
                "BASIC", "BRANCHING", "MULTI_INSTANCE", "STATE_BASED",
                "DISTRIBUTED", "EVENT_DRIVEN", "AI_ML", "ENTERPRISE", "AGENT"
            };

            for (String category : expectedCategories) {
                assertTrue(helpText.contains(category),
                           "Help text should contain category: " + category);
            }
        }
    }
}