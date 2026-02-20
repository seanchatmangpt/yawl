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

/**
 * Configuration classes for Greg-Verse simulation and agent execution.
 *
 * <p>This package provides configuration management for Greg-Verse simulations,
 * including scenario selection, agent filtering, output formatting, and execution
 * parameters. The configuration system supports both programmatic construction
 * and command-line argument parsing.</p>
 *
 * <h2>GregVerseConfig</h2>
 * <p>A Java 25 record that encapsulates all simulation configuration options:</p>
 *
 * <h3>Scenario Selection</h3>
 * <ul>
 *   <li>{@code scenarioId} - Specific scenario to run (e.g., "gvs-1-startup-idea")</li>
 *   <li>{@code agentIds} - Filter to specific agents, empty means all</li>
 *   <li>{@code marketplaceMode} - Run marketplace simulation instead of scenario</li>
 * </ul>
 *
 * <h3>Execution Parameters</h3>
 * <ul>
 *   <li>{@code timeoutSeconds} - Per-agent task timeout (default: 60)</li>
 *   <li>{@code parallelExecution} - Use virtual threads for concurrent execution</li>
 *   <li>{@code marketplaceDuration} - Duration for marketplace simulations</li>
 *   <li>{@code enableMetrics} - Collect detailed performance metrics</li>
 *   <li>{@code verbose} - Enable verbose logging</li>
 * </ul>
 *
 * <h3>Output Configuration</h3>
 * <ul>
 *   <li>{@code outputFormat} - CONSOLE, JSON, MARKDOWN, or HTML</li>
 *   <li>{@code outputPath} - File path for output, null means stdout</li>
 * </ul>
 *
 * <h3>Single Skill Invocation</h3>
 * <ul>
 *   <li>{@code singleAgentId} - Agent ID for direct skill invocation</li>
 *   <li>{@code singleSkillId} - Skill ID to invoke</li>
 *   <li>{@code skillInput} - JSON input for the skill</li>
 * </ul>
 *
 * <h2>Factory Methods</h2>
 * <p>GregVerseConfig provides convenient factory methods for common configurations:</p>
 *
 * <pre>{@code
 * // Default configuration (all scenarios, parallel execution)
 * GregVerseConfig config = GregVerseConfig.defaults();
 *
 * // Run a specific scenario
 * GregVerseConfig config = GregVerseConfig.forScenario("gvs-1-startup-idea");
 *
 * // Run marketplace simulation
 * GregVerseConfig config = GregVerseConfig.forMarketplace(Duration.ofMinutes(10));
 *
 * // Invoke a single agent skill
 * GregVerseConfig config = GregVerseConfig.forSingleSkill(
 *     "greg-isenberg", "product-vision", "{\"topic\": \"AI startup\"}"
 * );
 * }</pre>
 *
 * <h2>Command-Line Interface</h2>
 * <p>Configuration can be parsed from command-line arguments:</p>
 *
 * <pre>{@code
 * public static void main(String[] args) {
 *     GregVerseConfig config = GregVerseConfig.fromCommandLine(args);
 *     GregVerseSimulation simulation = new GregVerseSimulation(config);
 *     GregVerseReport report = simulation.run();
 *     // Output report based on config.outputFormat()
 * }
 * }</pre>
 *
 * <h3>CLI Options</h3>
 * <pre>
 * Scenario Options:
 *   --scenario, -s <id>    Run specific scenario (e.g., gvs-1-startup-idea)
 *   --all                  Run all scenarios
 *   --agents, -a <list>    Comma-separated agent IDs (default: all)
 *
 * Marketplace Options:
 *   --marketplace, -m      Run marketplace simulation
 *   --duration, -d <time>  Simulation duration (e.g., 5m, 1h)
 *
 * Single Skill Invocation:
 *   --agent <id>           Agent ID to invoke
 *   --skill <id>           Skill ID to invoke
 *   --input, -i <json>     JSON input for skill
 *
 * Output Options:
 *   --format, -f <type>    Output format: console, json, markdown, html
 *   --output, -o <path>    Write output to file
 *
 * Execution Options:
 *   --timeout, -t <sec>    Timeout per task (default: 60)
 *   --parallel             Run agents in parallel (default)
 *   --sequential           Run agents sequentially
 *   --no-metrics           Disable detailed metrics collection
 *   --verbose, -v          Enable verbose logging
 * </pre>
 *
 * <h2>Output Formats</h2>
 * <p>The {@code OutputFormat} enum defines supported output formats:</p>
 * <ul>
 *   <li>{@code CONSOLE} - ANSI colored terminal output (default)</li>
 *   <li>{@code JSON} - Structured JSON for programmatic processing</li>
 *   <li>{@code MARKDOWN} - GitHub-flavored markdown for documentation</li>
 *   <li>{@code HTML} - Interactive HTML with Chart.js visualizations</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see org.yawlfoundation.yawl.mcp.a2a.gregverse.config.GregVerseConfig
 * @see org.yawlfoundation.yawl.mcp.a2a.gregverse.config.GregVerseConfig.OutputFormat
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.config;
