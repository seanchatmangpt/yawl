/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Autonomous agent integration for YAWL workflow automation.
 *
 * <p>This package provides a framework for creating autonomous agents that can
 * automatically discover, claim, and complete work items in YAWL workflows.
 * Agents use configurable strategies for task discovery, eligibility checking,
 * and decision making.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.AutonomousAgent} - Base agent interface</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.AgentFactory} - Factory for agent creation</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.AgentRegistry} - Agent registration and discovery</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent} - Configurable agent implementation</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.PartitionConfig} - Partition configuration for horizontal scaling</li>
 * </ul>
 *
 * <p>Subpackages:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.config} - Agent configuration loading</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.generators} - Output generation strategies</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.launcher} - Workflow launching utilities</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.observability} - Health checks and metrics</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.reasoners} - AI-based decision reasoning</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.resilience} - Circuit breakers and fallbacks</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.strategies} - Core strategy interfaces</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.registry} - Agent registration and discovery</li>
 * </ul>
 *
 * <p><strong>Partition Strategy:</strong> Agents support horizontal scaling through partitioned work item
 * distribution using hash-based assignment: (hash % totalAgents) == agentIndex. This enables
 * even workload distribution across multiple autonomous agent instances.</p>
 */
package org.yawlfoundation.yawl.integration.autonomous;
