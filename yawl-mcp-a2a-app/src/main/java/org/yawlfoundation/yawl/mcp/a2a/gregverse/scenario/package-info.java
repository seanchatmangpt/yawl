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
 * Scenario model classes for multi-agent Greg-Verse workflows.
 *
 * <p>This package provides the domain model for defining multi-agent scenarios
 * where Greg-Verse business advisors collaborate through coordinated steps.
 * Scenarios enable complex workflows that leverage multiple agents' expertise
 * in sequence or parallel.</p>
 *
 * <h2>Core Classes</h2>
 *
 * <h3>Scenario</h3>
 * <p>A Java 25 record representing a complete multi-agent workflow:</p>
 * <ul>
 *   <li>{@code id} - Unique scenario identifier (e.g., "gvs-1-startup-idea")</li>
 *   <li>{@code name} - Human-readable scenario name</li>
 *   <li>{@code description} - Detailed scenario purpose</li>
 *   <li>{@code steps} - Ordered list of ScenarioStep objects</li>
 *   <li>{@code agentIds} - Set of participating agent IDs</li>
 *   <li>{@code timeout} - Global timeout in milliseconds</li>
 *   <li>{@code compensationEnabled} - Whether to enable failure compensation</li>
 * </ul>
 *
 * <h3>ScenarioStep</h3>
 * <p>A Java 25 record representing a single atomic unit of work:</p>
 * <ul>
 *   <li>{@code id} - Unique step identifier</li>
 *   <li>{@code agentId} - Agent responsible for execution</li>
 *   <li>{@code skillId} - Skill to invoke</li>
 *   <li>{@code topic} - Subject matter for the step</li>
 *   <li>{@code context} - Additional context data</li>
 *   <li>{@code input} - Input data (may reference previous step outputs)</li>
 *   <li>{@code targetAgent} - Optional agent for handoff</li>
 *   <li>{@code required} - Whether step must succeed</li>
 *   <li>{@code timeout} - Maximum execution time</li>
 * </ul>
 *
 * <h3>ScenarioLoader</h3>
 * <p>Utility class for loading scenarios from various sources:</p>
 * <ul>
 *   <li>YAML configuration files</li>
 *   <li>JSON definitions</li>
 *   <li>Programmatic builder construction</li>
 * </ul>
 *
 * <h2>Scenario Design Patterns</h2>
 *
 * <h3>Sequential Handoff</h3>
 * <p>Steps execute in order, each handing off to the next agent:</p>
 * <pre>{@code
 * Scenario scenario = Scenario.builder()
 *     .id("product-review")
 *     .name("Product Review Workflow")
 *     .addStep(ScenarioStep.builder()
 *         .id("strategy")
 *         .agentId("greg-isenberg")
 *         .skillId("product-vision")
 *         .targetAgent("nicolas-cole")
 *         .build())
 *     .addStep(ScenarioStep.builder()
 *         .id("messaging")
 *         .agentId("nicolas-cole")
 *         .skillId("digital-writing")
 *         .build())
 *     .build();
 * }</pre>
 *
 * <h3>Parallel Consultation</h3>
 * <p>Multiple agents provide input on the same topic:</p>
 * <pre>{@code
 * Scenario scenario = Scenario.builder()
 *     .id("idea-validation")
 *     .name("Idea Validation Panel")
 *     .addStep(ScenarioStep.builder()
 *         .id("strategy-review")
 *         .agentId("greg-isenberg")
 *         .skillId("startup-advisory")
 *         .build())
 *     .addStep(ScenarioStep.builder()
 *         .id("content-review")
 *         .agentId("justin-welsh")
 *         .skillId("solopreneur-strategy")
 *         .build())
 *     .addStep(ScenarioStep.builder()
 *         .id="technical-review")
 *         .agentId("dan-romero")
 *         .skillId("api-design")
 *         .build())
 *     .timeout(120000)
 *     .build();
 * }</pre>
 *
 * <h2>Dependency Resolution</h2>
 * <p>Scenarios support automatic dependency inference based on:</p>
 * <ul>
 *   <li>Step ordering (earlier steps complete before later ones)</li>
 *   <li>Target agent specifications (handoff relationships)</li>
 *   <li>Same-agent consecutive steps (implicit sequential dependency)</li>
 * </ul>
 *
 * <h2>Built-in Scenarios</h2>
 * <ul>
 *   <li><strong>gvs-1-startup-idea</strong> - Comprehensive startup idea validation</li>
 *   <li><strong>gvs-2-content-business</strong> - Content business strategy development</li>
 *   <li><strong>gvs-3-api-infrastructure</strong> - API-first product planning</li>
 *   <li><strong>gvs-4-skill-transaction</strong> - Skills marketplace dynamics</li>
 *   <li><strong>gvs-5-product-launch</strong> - Product launch coordination</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see org.yawlfoundation.yawl.mcp.a2a.gregverse.scenario.Scenario
 * @see org.yawlfoundation.yawl.mcp.a2a.gregverse.scenario.ScenarioStep
 * @see org.yawlfoundation.yawl.mcp.a2a.gregverse.scenario.ScenarioLoader
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.scenario;
