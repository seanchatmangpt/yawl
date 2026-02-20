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

package org.yawlfoundation.yawl.mcp.a2a.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.mcp.a2a.demo.config.DemoConfig;
import org.yawlfoundation.yawl.mcp.a2a.demo.config.PatternCategory;
import org.yawlfoundation.yawl.mcp.a2a.demo.config.PatternRegistry;
import org.yawlfoundation.yawl.mcp.a2a.demo.report.PatternResult.PatternInfo;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PatternDemoRunner and PatternRegistry.
 *
 * <p>Verifies basic instantiation, pattern loading, and help text generation.</p>
 */
@DisplayName("PatternDemoRunner Tests")
class PatternDemoRunnerTest {

    @Test
    @DisplayName("PatternDemoRunner can be created with default config")
    void testPatternDemoRunnerCreationWithDefaultConfig() {
        DemoConfig config = DemoConfig.defaults();
        PatternDemoRunner runner = new PatternDemoRunner(config);

        assertNotNull(runner, "PatternDemoRunner should be instantiated");
    }

    @Test
    @DisplayName("PatternDemoRunner can be created with builder config")
    void testPatternDemoRunnerCreationWithBuilderConfig() {
        DemoConfig config = DemoConfig.builder()
            .outputFormat(DemoConfig.OutputFormat.JSON)
            .timeoutSeconds(60)
            .parallelExecution(false)
            .build();

        PatternDemoRunner runner = new PatternDemoRunner(config);

        assertNotNull(runner, "PatternDemoRunner should be instantiated with builder config");
    }

    @Test
    @DisplayName("PatternRegistry loads patterns on construction")
    void testPatternRegistryLoadsPatterns() {
        PatternRegistry registry = new PatternRegistry();

        int patternCount = registry.getPatternCount();
        assertTrue(patternCount > 0, "PatternRegistry should load patterns on construction");
        assertTrue(patternCount >= 40, "PatternRegistry should load at least 40 patterns (found: " + patternCount + ")");
    }

    @Test
    @DisplayName("PatternRegistry can retrieve pattern by ID")
    void testPatternRegistryGetPatternById() {
        PatternRegistry registry = new PatternRegistry();

        Optional<PatternInfo> pattern = registry.getPattern("WCP-1");

        assertTrue(pattern.isPresent(), "WCP-1 pattern should be found");
        assertEquals("WCP-1", pattern.get().id(), "Pattern ID should match");
        assertEquals("Sequence", pattern.get().name(), "Pattern name should be 'Sequence'");
    }

    @Test
    @DisplayName("PatternRegistry returns empty for unknown pattern ID")
    void testPatternRegistryGetUnknownPattern() {
        PatternRegistry registry = new PatternRegistry();

        Optional<PatternInfo> pattern = registry.getPattern("UNKNOWN-999");

        assertFalse(pattern.isPresent(), "Unknown pattern should return empty Optional");
    }

    @Test
    @DisplayName("PatternRegistry can get patterns by category")
    void testPatternRegistryGetPatternsByCategory() {
        PatternRegistry registry = new PatternRegistry();

        List<PatternInfo> basicPatterns = registry.getPatternsByCategory(PatternCategory.BASIC);

        assertFalse(basicPatterns.isEmpty(), "BASIC category should have patterns");
        assertTrue(basicPatterns.size() >= 5, "BASIC category should have at least 5 patterns (found: " + basicPatterns.size() + ")");
    }

    @Test
    @DisplayName("PatternRegistry can find similar patterns")
    void testPatternRegistryFindSimilarPatterns() {
        PatternRegistry registry = new PatternRegistry();

        List<String> similar = registry.findSimilarPatterns("WCP-1");

        assertFalse(similar.isEmpty(), "Should find similar patterns for 'WCP-1'");
        assertTrue(similar.contains("WCP-1"), "Similar patterns should include exact match 'WCP-1'");
    }

    @Test
    @DisplayName("PatternRegistry hasPattern returns correct results")
    void testPatternRegistryHasPattern() {
        PatternRegistry registry = new PatternRegistry();

        assertTrue(registry.hasPattern("WCP-1"), "WCP-1 should exist");
        assertTrue(registry.hasPattern("wcp-1"), "Pattern lookup should be case-insensitive");
        assertFalse(registry.hasPattern("UNKNOWN-999"), "Unknown pattern should not exist");
    }

    @Test
    @DisplayName("DemoConfig defaults are valid")
    void testDemoConfigDefaults() {
        DemoConfig config = DemoConfig.defaults();

        assertNotNull(config, "DemoConfig.defaults() should return non-null config");
        assertEquals(DemoConfig.OutputFormat.CONSOLE, config.outputFormat(), "Default output format should be CONSOLE");
        assertEquals(DemoConfig.DEFAULT_TIMEOUT_SECONDS, config.timeoutSeconds(), "Default timeout should be 300 seconds");
        assertTrue(config.enableTracing(), "Tracing should be enabled by default");
        assertTrue(config.enableMetrics(), "Metrics should be enabled by default");
        assertTrue(config.parallelExecution(), "Parallel execution should be enabled by default");
    }

    @Test
    @DisplayName("Help text can be displayed")
    void testHelpTextAvailable() {
        String helpText = DemoConfig.CommandLineParser.getHelpText();

        assertNotNull(helpText, "Help text should not be null");
        assertFalse(helpText.isBlank(), "Help text should not be blank");
        assertTrue(helpText.contains("Usage"), "Help text should contain 'Usage'");
        assertTrue(helpText.contains("--format"), "Help text should contain '--format' option");
        assertTrue(helpText.contains("--help"), "Help text should contain '--help' option");
    }

    @Test
    @DisplayName("DemoConfig can parse command line arguments")
    void testDemoConfigFromCommandLine() {
        String[] args = {"--format", "json", "--timeout", "60"};
        DemoConfig config = DemoConfig.fromCommandLine(args);

        assertEquals(DemoConfig.OutputFormat.JSON, config.outputFormat(), "Format should be JSON");
        assertEquals(60, config.timeoutSeconds(), "Timeout should be 60 seconds");
    }

    @Test
    @DisplayName("PatternInfo contains required fields")
    void testPatternInfoFields() {
        PatternRegistry registry = new PatternRegistry();
        Optional<PatternInfo> pattern = registry.getPattern("WCP-1");

        assertTrue(pattern.isPresent(), "WCP-1 should exist");

        PatternInfo info = pattern.get();
        assertNotNull(info.id(), "Pattern ID should not be null");
        assertNotNull(info.name(), "Pattern name should not be null");
        assertNotNull(info.description(), "Pattern description should not be null");
        assertNotNull(info.difficulty(), "Pattern difficulty should not be null");
        assertNotNull(info.category(), "Pattern category should not be null");
    }

    @Test
    @DisplayName("All pattern IDs are accessible")
    void testGetAllPatternIds() {
        PatternRegistry registry = new PatternRegistry();

        var patternIds = registry.getAllPatternIds();

        assertNotNull(patternIds, "Pattern IDs set should not be null");
        assertFalse(patternIds.isEmpty(), "Pattern IDs set should not be empty");
        assertTrue(patternIds.contains("WCP-1"), "Pattern IDs should contain WCP-1");
        assertTrue(patternIds.contains("AGT-1"), "Pattern IDs should contain AGT-1");
    }
}
