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
 * Simulation engine for multi-agent Greg-Verse scenarios.
 *
 * <p>This package provides the core simulation infrastructure for running
 * multi-agent scenarios where Greg-Verse business advisors collaborate,
 * invoke skills, and produce measurable business outcomes. The simulation
 * engine leverages Java 25 virtual threads for lightweight concurrent execution.</p>
 *
 * <h2>Core Components</h2>
 *
 * <h3>GregVerseSimulation</h3>
 * <p>Main simulation engine that orchestrates multi-agent scenarios with:</p>
 * <ul>
 *   <li>Virtual thread execution via {@code Executors.newVirtualThreadPerTaskExecutor()}</li>
 *   <li>Flexible execution modes (parallel or sequential)</li>
 *   <li>Agent-to-agent interaction tracking</li>
 *   <li>Skill invocation monitoring</li>
 *   <li>Business outcome calculation</li>
 *   <li>Graceful timeout and error handling</li>
 *   <li>Comprehensive report generation</li>
 * </ul>
 *
 * <h3>GregVerseOrchestrator</h3>
 * <p>Coordinates the execution of scenarios across multiple agents,
 * managing dependencies and handoffs between agents.</p>
 *
 * <h3>GregVerseScenarioRunner</h3>
 * <p>Executes individual scenarios, handling step-by-step execution
 * and result aggregation.</p>
 *
 * <h3>GregVerseMarketplace</h3>
 * <p>Simulates the AI skills marketplace where agents can buy and sell
 * skills, tracking transactions and pricing dynamics.</p>
 *
 * <h2>Execution Modes</h2>
 * <p>The simulation supports two execution modes controlled by
 * {@link org.yawlfoundation.yawl.mcp.a2a.gregverse.config.GregVerseConfig#parallelExecution()}:</p>
 *
 * <h3>Parallel Execution (Default)</h3>
 * <ul>
 *   <li>Each agent runs on its own virtual thread</li>
 *   <li>Results collected as they complete</li>
 *   <li>Per-agent timeout handling</li>
 *   <li>Maximum throughput for independent scenarios</li>
 * </ul>
 *
 * <h3>Sequential Execution</h3>
 * <ul>
 *   <li>Agents execute one at a time in order</li>
 *   <li>Useful for debugging or ordered dependencies</li>
 *   <li>Easier to trace execution flow</li>
 * </ul>
 *
 * <h2>Business Outcomes</h2>
 * <p>The simulation calculates measurable business outcomes including:</p>
 * <ul>
 *   <li>Ideas qualified - Count of high-quality outputs</li>
 *   <li>MVPs built - Count of multi-skill agent results</li>
 *   <li>Skills created - Unique skill invocations</li>
 *   <li>Revenue generated - Sum of skill transaction values</li>
 *   <li>Partnerships - Agent collaboration count</li>
 *   <li>Time saved - Estimated hours saved through automation</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create configuration for a specific scenario
 * GregVerseConfig config = GregVerseConfig.forScenario("gvs-1-startup-idea");
 *
 * // Create and run simulation
 * GregVerseSimulation simulation = new GregVerseSimulation(config);
 * GregVerseReport report = simulation.run();
 *
 * // Access results
 * System.out.println("Successful agents: " + report.getSuccessfulAgents());
 * System.out.println("Business outcomes: " + report.businessOutcomes());
 * System.out.println("Transactions: " + report.transactions().size());
 * }</pre>
 *
 * <h2>Available Scenarios</h2>
 * <ul>
 *   <li>gvs-1-startup-idea - Startup idea validation</li>
 *   <li>gvs-2-content-business - Content business strategy</li>
 *   <li>gvs-3-api-infrastructure - API-first infrastructure</li>
 *   <li>gvs-4-skill-transaction - Skills marketplace simulation</li>
 *   <li>gvs-5-product-launch - Product launch strategy</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see org.yawlfoundation.yawl.mcp.a2a.gregverse.simulation.GregVerseSimulation
 * @see org.yawlfoundation.yawl.mcp.a2a.gregverse.config.GregVerseConfig
 * @see org.yawlfoundation.yawl.mcp.a2a.gregverse.report.GregVerseReport
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.simulation;
