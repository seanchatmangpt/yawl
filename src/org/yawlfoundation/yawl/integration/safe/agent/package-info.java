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
 * SAFe agent role and capability definitions.
 *
 * <p>This package defines agent roles, capabilities, and cards for
 * Scaled Agile Framework (SAFe) agent participation in YAWL workflows.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.safe.agent.SAFeAgentRole}
 *       - Enum of SAFe roles (Product Owner, Scrum Master, etc.)</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.safe.agent.AgentCapability}
 *       - Enum of agent capabilities for task matching</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.safe.agent.SAFeAgentCard}
 *       - Immutable record describing agent identity and capabilities</li>
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 * <pre>{@code
 * // Register a Product Owner agent
 * SAFeAgentCard poCard = SAFeAgentCard.builder()
 *     .agentId("po-001")
 *     .name("Product Owner Bot")
 *     .role(SAFeAgentRole.PRODUCT_OWNER)
 *     .capabilities(List.of(
 *         AgentCapability.STORY_REFINEMENT,
 *         AgentCapability.PRIORITY_MANAGEMENT
 *     ))
 *     .host("localhost")
 *     .port(8080)
 *     .ceremonies(List.of("SPRINT_PLANNING", "RETROSPECTIVE"))
 *     .build();
 *
 * registry.registerAgent(poCard);
 * }</pre>
 *
 * @since YAWL 6.0
 */
package org.yawlfoundation.yawl.integration.safe.agent;
