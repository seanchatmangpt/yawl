/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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
 * SAFe agent registry for agent discovery and status management.
 *
 * <p>This package provides the registry for managing SAFe agents, their
 * capabilities, and operational status across agile ceremonies.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.safe.registry.SAFeAgentRegistry}
 *       - Central registry for agent registration, lookup, and status tracking</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.safe.registry.SAFeAgentRegistry.AgentStatus}
 *       - Record tracking agent health and performance metrics</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.safe.registry.SAFeAgentRegistry.AgentState}
 *       - Enum for agent operational states (AVAILABLE, DEGRADED, OFFLINE)</li>
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 * <pre>{@code
 * SAFeAgentRegistry registry = new SAFeAgentRegistry();
 *
 * // Register agents
 * registry.registerAgent(poCard);
 * registry.registerAgent(smCard);
 *
 * // Find agents by capability
 * List<SAFeAgentCard> refinementAgents = registry.findByCapability(
 *     AgentCapability.STORY_REFINEMENT
 * );
 *
 * // Update agent status
 * registry.recordSuccess("po-001");
 * registry.recordHeartbeat("po-001");
 *
 * // Get healthy agents
 * List<SAFeAgentCard> available = registry.getAvailableAgents();
 * }</pre>
 *
 * @since YAWL 6.0
 */
package org.yawlfoundation.yawl.integration.safe.registry;
