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

package org.yawlfoundation.yawl.mcp.a2a.gregverse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.config.GregVerseConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GregVerse self-play demo mode.
 *
 * <p>Verifies that {@code --self-play} configuration is parsed correctly,
 * the demo runs all scenarios without throwing, and {@code demo-results.json}
 * is written with the expected structure.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("GregVerse Self-Play Demo Tests")
class GregVerseSelfPlayDemoTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("forSelfPlay() creates config with selfPlayMode=true and JSON output")
    void testForSelfPlayCreatesValidConfig() {
        GregVerseConfig config = GregVerseConfig.forSelfPlay();

        assertTrue(config.isSelfPlayMode(), "forSelfPlay() should set selfPlayMode to true");
        assertEquals("demo-results.json", config.outputPath(),
            "forSelfPlay() should default output path to demo-results.json");
        assertEquals(GregVerseConfig.OutputFormat.JSON, config.outputFormat(),
            "forSelfPlay() should use JSON output format");
        assertTrue(config.parallelExecution(),
            "forSelfPlay() should use parallel execution");
        assertTrue(config.enableMetrics(),
            "forSelfPlay() should enable metrics collection");
    }

    @Test
    @DisplayName("defaults() config has selfPlayMode=false")
    void testSelfPlayModeNotSetByDefault() {
        GregVerseConfig config = GregVerseConfig.defaults();

        assertFalse(config.isSelfPlayMode(),
            "defaults() should not set selfPlayMode");
    }

    @Test
    @DisplayName("forScenario() config has selfPlayMode=false")
    void testSelfPlayModeNotSetForScenario() {
        GregVerseConfig config = GregVerseConfig.forScenario("gvs-1-startup-idea");

        assertFalse(config.isSelfPlayMode(),
            "forScenario() should not set selfPlayMode");
    }

    @Test
    @DisplayName("forMarketplace() config has selfPlayMode=false")
    void testSelfPlayModeNotSetForMarketplace() {
        GregVerseConfig config = GregVerseConfig.forMarketplace(
            java.time.Duration.ofMinutes(1));

        assertFalse(config.isSelfPlayMode(),
            "forMarketplace() should not set selfPlayMode");
    }

    @Test
    @DisplayName("fromCommandLine parses --self-play flag")
    void testFromCommandLineParsesFlag() {
        GregVerseConfig config = GregVerseConfig.fromCommandLine(
            new String[]{"--self-play"});

        assertTrue(config.isSelfPlayMode(),
            "--self-play flag should set selfPlayMode to true");
    }

    @Test
    @DisplayName("fromCommandLine without --self-play leaves selfPlayMode=false")
    void testFromCommandLineWithoutFlag() {
        GregVerseConfig config = GregVerseConfig.fromCommandLine(
            new String[]{"--scenario", "gvs-1-startup-idea"});

        assertFalse(config.isSelfPlayMode(),
            "selfPlayMode should be false when --self-play is not passed");
    }

    @Test
    @DisplayName("GregVerseRunner dispatches to self-play demo and completes without exception")
    void testRunnerDispatchesSelfPlay() {
        Path outputFile = tempDir.resolve("demo-results.json");
        GregVerseConfig config = new GregVerseConfig(
            null,
            java.util.List.of(),
            GregVerseConfig.OutputFormat.JSON,
            outputFile.toString(),
            GregVerseConfig.DEFAULT_TIMEOUT_SECONDS,
            false, // sequential to avoid rate-limit bursts in test
            false,
            GregVerseConfig.DEFAULT_MARKETPLACE_DURATION,
            null, null, null,
            true,
            false,
            true
        );

        GregVerseRunner runner = new GregVerseRunner(config);
        int exitCode = runner.run();

        assertTrue(exitCode == 0 || exitCode == 1,
            "Self-play demo should complete (0=all success, 1=partial 429 rate-limit failures)");
    }

    @Test
    @DisplayName("Self-play demo writes demo-results.json to specified output path")
    void testDemoResultsFileWritten() throws IOException {
        Path outputFile = tempDir.resolve("demo-results.json");
        GregVerseConfig config = new GregVerseConfig(
            null,
            java.util.List.of(),
            GregVerseConfig.OutputFormat.JSON,
            outputFile.toString(),
            GregVerseConfig.DEFAULT_TIMEOUT_SECONDS,
            false, // sequential to avoid rate-limit bursts in test
            false,
            GregVerseConfig.DEFAULT_MARKETPLACE_DURATION,
            null, null, null,
            true,
            false,
            true
        );

        GregVerseRunner runner = new GregVerseRunner(config);
        runner.run();

        assertTrue(Files.exists(outputFile),
            "demo-results.json should be written to the configured output path");

        String content = Files.readString(outputFile);
        assertTrue(content.contains("\"totalPatterns\""),
            "demo-results.json should contain totalPatterns field");
        assertTrue(content.contains("\"version\""),
            "demo-results.json should contain version field");
        assertTrue(content.contains("\"results\""),
            "demo-results.json should contain results array");
        assertTrue(content.contains("gvs-"),
            "demo-results.json should contain GregVerse scenario IDs");
    }

    @Test
    @DisplayName("Self-play demo report covers all 5 scenarios")
    void testSelfPlayDemoCoversAllScenarios() throws IOException {
        Path outputFile = tempDir.resolve("demo-results.json");
        GregVerseConfig config = new GregVerseConfig(
            null,
            java.util.List.of(),
            GregVerseConfig.OutputFormat.JSON,
            outputFile.toString(),
            GregVerseConfig.DEFAULT_TIMEOUT_SECONDS,
            false, // sequential to avoid rate-limit bursts in test
            false,
            GregVerseConfig.DEFAULT_MARKETPLACE_DURATION,
            null, null, null,
            true,
            false,
            true
        );

        GregVerseRunner runner = new GregVerseRunner(config);
        runner.run();

        String content = Files.readString(outputFile);
        assertTrue(content.contains("\"totalPatterns\": 5"),
            "Self-play demo should run all 5 scenarios");
        assertTrue(content.contains("gvs-1-startup-idea"),
            "Results should include gvs-1-startup-idea");
        assertTrue(content.contains("gvs-5-product-launch"),
            "Results should include gvs-5-product-launch");
    }

    @Test
    @DisplayName("Self-play demo includes successRate in results")
    void testDemoResultsContainsSuccessRate() throws IOException {
        Path outputFile = tempDir.resolve("demo-results.json");
        GregVerseConfig config = new GregVerseConfig(
            null,
            java.util.List.of(),
            GregVerseConfig.OutputFormat.JSON,
            outputFile.toString(),
            GregVerseConfig.DEFAULT_TIMEOUT_SECONDS,
            false, // sequential to avoid rate-limit bursts in test
            false,
            GregVerseConfig.DEFAULT_MARKETPLACE_DURATION,
            null, null, null,
            true,
            false,
            true
        );

        GregVerseRunner runner = new GregVerseRunner(config);
        runner.run();

        String content = Files.readString(outputFile);
        assertTrue(content.contains("\"successRate\""),
            "demo-results.json should include successRate field");
    }
}
