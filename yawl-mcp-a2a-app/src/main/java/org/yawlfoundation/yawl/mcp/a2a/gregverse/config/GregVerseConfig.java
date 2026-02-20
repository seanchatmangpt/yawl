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

package org.yawlfoundation.yawl.mcp.a2a.gregverse.config;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Configuration record for Greg-Verse simulation runs.
 *
 * <p>This record encapsulates all configuration options for running
 * Greg-Verse multi-agent simulations, including agent selection,
 * scenario parameters, and output formatting.</p>
 *
 * @param scenarioId the scenario ID to run (e.g., "gvs-1-startup-idea")
 * @param agentIds list of specific agent IDs to include, empty means all
 * @param outputFormat the report output format
 * @param outputPath file path for output, empty means stdout
 * @param timeoutSeconds execution timeout per agent task
 * @param parallelExecution whether to run agents in parallel using virtual threads
 * @param marketplaceMode whether to run marketplace simulation instead of scenario
 * @param marketplaceDuration duration for marketplace simulation
 * @param singleAgentId agent ID for single agent skill invocation
 * @param singleSkillId skill ID for single skill invocation
 * @param skillInput JSON input for single skill invocation
 * @param enableMetrics whether to collect detailed metrics
 * @param verbose whether to enable verbose logging
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record GregVerseConfig(
    String scenarioId,
    List<String> agentIds,
    OutputFormat outputFormat,
    String outputPath,
    int timeoutSeconds,
    boolean parallelExecution,
    boolean marketplaceMode,
    Duration marketplaceDuration,
    String singleAgentId,
    String singleSkillId,
    String skillInput,
    boolean enableMetrics,
    boolean verbose
) {

    /**
     * Output format options for Greg-Verse reports.
     */
    public enum OutputFormat {
        /** ANSI colored console output */
        CONSOLE,
        /** Structured JSON output */
        JSON,
        /** GitHub-flavored markdown */
        MARKDOWN,
        /** Interactive HTML with Chart.js */
        HTML
    }

    /**
     * Default timeout in seconds.
     */
    public static final int DEFAULT_TIMEOUT_SECONDS = 60;

    /**
     * Default marketplace simulation duration.
     */
    public static final Duration DEFAULT_MARKETPLACE_DURATION = Duration.ofMinutes(5);

    /**
     * Create a default configuration for running all scenarios.
     *
     * @return default configuration
     */
    public static GregVerseConfig defaults() {
        return new GregVerseConfig(
            null,
            List.of(),
            OutputFormat.CONSOLE,
            null,
            DEFAULT_TIMEOUT_SECONDS,
            true,
            false,
            DEFAULT_MARKETPLACE_DURATION,
            null,
            null,
            null,
            true,
            false
        );
    }

    /**
     * Create a configuration for running a specific scenario.
     *
     * @param scenarioId the scenario ID to run
     * @return configuration for the scenario
     */
    public static GregVerseConfig forScenario(String scenarioId) {
        return new GregVerseConfig(
            scenarioId,
            List.of(),
            OutputFormat.CONSOLE,
            null,
            DEFAULT_TIMEOUT_SECONDS,
            true,
            false,
            DEFAULT_MARKETPLACE_DURATION,
            null,
            null,
            null,
            true,
            false
        );
    }

    /**
     * Create a configuration for marketplace simulation.
     *
     * @param duration simulation duration
     * @return configuration for marketplace mode
     */
    public static GregVerseConfig forMarketplace(Duration duration) {
        return new GregVerseConfig(
            null,
            List.of(),
            OutputFormat.CONSOLE,
            null,
            DEFAULT_TIMEOUT_SECONDS,
            true,
            true,
            duration,
            null,
            null,
            null,
            true,
            false
        );
    }

    /**
     * Create a configuration for single agent skill invocation.
     *
     * @param agentId the agent ID
     * @param skillId the skill ID
     * @param input JSON input for the skill
     * @return configuration for single skill invocation
     */
    public static GregVerseConfig forSingleSkill(String agentId, String skillId, String input) {
        return new GregVerseConfig(
            null,
            List.of(),
            OutputFormat.CONSOLE,
            null,
            DEFAULT_TIMEOUT_SECONDS,
            false,
            false,
            DEFAULT_MARKETPLACE_DURATION,
            agentId,
            skillId,
            input,
            false,
            true
        );
    }

    /**
     * Check if a specific scenario should be run.
     *
     * @return true if scenario mode is active
     */
    public boolean hasScenario() {
        return scenarioId != null && !scenarioId.isBlank();
    }

    /**
     * Check if specific agents are selected.
     *
     * @return true if agent filter is active
     */
    public boolean hasAgentFilter() {
        return agentIds != null && !agentIds.isEmpty();
    }

    /**
     * Check if output should be written to a file.
     *
     * @return true if output path is specified
     */
    public boolean hasOutputPath() {
        return outputPath != null && !outputPath.isBlank();
    }

    /**
     * Check if single skill mode is active.
     *
     * @return true if single skill invocation is configured
     */
    public boolean isSingleSkillMode() {
        return singleAgentId != null && !singleAgentId.isBlank()
            && singleSkillId != null && !singleSkillId.isBlank();
    }

    /**
     * Get the timeout as a Duration.
     *
     * @return timeout duration
     */
    public Duration getTimeoutDuration() {
        return Duration.ofSeconds(timeoutSeconds);
    }

    /**
     * Parse command-line arguments into a configuration.
     *
     * @param args command-line arguments
     * @return parsed configuration
     */
    public static GregVerseConfig fromCommandLine(String[] args) {
        String scenarioId = null;
        List<String> agentIds = List.of();
        OutputFormat format = OutputFormat.CONSOLE;
        String outputPath = null;
        int timeout = DEFAULT_TIMEOUT_SECONDS;
        boolean parallel = true;
        boolean marketplace = false;
        Duration marketplaceDuration = DEFAULT_MARKETPLACE_DURATION;
        String singleAgent = null;
        String singleSkill = null;
        String skillInput = null;
        boolean metrics = true;
        boolean verbose = false;
        boolean allScenarios = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--scenario", "-s" -> {
                    if (i + 1 < args.length) {
                        scenarioId = args[++i];
                    }
                }
                case "--agents", "-a" -> {
                    if (i + 1 < args.length) {
                        agentIds = List.of(args[++i].split(","));
                    }
                }
                case "--format", "-f" -> {
                    if (i + 1 < args.length) {
                        format = OutputFormat.valueOf(args[++i].toUpperCase());
                    }
                }
                case "--output", "-o" -> {
                    if (i + 1 < args.length) {
                        outputPath = args[++i];
                    }
                }
                case "--timeout", "-t" -> {
                    if (i + 1 < args.length) {
                        timeout = Integer.parseInt(args[++i]);
                    }
                }
                case "--sequential" -> parallel = false;
                case "--parallel" -> parallel = true;
                case "--marketplace", "-m" -> marketplace = true;
                case "--duration", "-d" -> {
                    if (i + 1 < args.length) {
                        String durStr = args[++i];
                        marketplaceDuration = parseDuration(durStr);
                    }
                }
                case "--agent" -> {
                    if (i + 1 < args.length) {
                        singleAgent = args[++i];
                    }
                }
                case "--skill" -> {
                    if (i + 1 < args.length) {
                        singleSkill = args[++i];
                    }
                }
                case "--input", "-i" -> {
                    if (i + 1 < args.length) {
                        skillInput = args[++i];
                    }
                }
                case "--no-metrics" -> metrics = false;
                case "--verbose", "-v" -> verbose = true;
                case "--all" -> allScenarios = true;
                case "--help", "-h" -> {
                    printUsage();
                    System.exit(0);
                }
            }
        }

        return new GregVerseConfig(
            allScenarios ? "all" : scenarioId,
            agentIds,
            format,
            outputPath,
            timeout,
            parallel,
            marketplace,
            marketplaceDuration,
            singleAgent,
            singleSkill,
            skillInput,
            metrics,
            verbose
        );
    }

    private static Duration parseDuration(String str) {
        if (str.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(str.substring(0, str.length() - 1)));
        } else if (str.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(str.substring(0, str.length() - 1)));
        } else if (str.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(str.substring(0, str.length() - 1)));
        } else {
            return Duration.ofMinutes(Long.parseLong(str));
        }
    }

    private static void printUsage() {
        System.out.println("""
            GregVerse - Multi-Agent A2A Simulation Framework
            Usage: gregverse [options]

            Scenario Options:
              --scenario, -s <id>    Run specific scenario (e.g., gvs-1-startup-idea)
              --all                  Run all scenarios
              --agents, -a <list>    Comma-separated agent IDs (default: all)

            Marketplace Options:
              --marketplace, -m      Run marketplace simulation
              --duration, -d <time>  Simulation duration (e.g., 5m, 1h)

            Single Skill Invocation:
              --agent <id>           Agent ID to invoke
              --skill <id>           Skill ID to invoke
              --input, -i <json>     JSON input for skill

            Output Options:
              --format, -f <type>    Output format: console, json, markdown, html
              --output, -o <path>    Write output to file

            Execution Options:
              --timeout, -t <sec>    Timeout per task (default: 60)
              --parallel             Run agents in parallel (default)
              --sequential           Run agents sequentially
              --no-metrics           Disable detailed metrics collection
              --verbose, -v          Enable verbose logging

            Other:
              --help, -h             Show this help message

            Available Agents:
              greg-isenberg, james, nicolas-cole, dickie-bush, leo-leojrr,
              justin-welsh, dan-romero, blake-anderson

            Available Scenarios:
              gvs-1-startup-idea, gvs-2-content-business, gvs-3-api-infrastructure,
              gvs-4-skill-transaction, gvs-5-product-launch
            """);
    }
}
