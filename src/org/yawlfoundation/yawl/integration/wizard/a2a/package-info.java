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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * A2A (Agent-to-Agent) wizard steps for the Autonomic A2A/MCP Wizard.
 *
 * <p>This package implements the A2A configuration phase of the Autonomic
 * Wizard, enabling discovery of A2A agents, autonomic skill allocation, and
 * secure work item handoff between agents.
 *
 * <p><b>Workflow:</b>
 * <ol>
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.a2a.A2ADiscoveryStep}
 *       — Discover available A2A agents and their capabilities
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.a2a.A2ASkillMappingStep}
 *       — Map A2A skills to workflow task slots based on selected pattern
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.a2a.A2AHandoffStep}
 *       — Configure secure agent-to-agent handoff protocol (ADR-025)
 * </ol>
 *
 * <p><b>Key Types:</b>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.a2a.A2ASkillDescriptor}
 *       — Describes an A2A skill capability
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.a2a.A2AAgentDescriptor}
 *       — Describes a discovered A2A agent
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.a2a.A2AWizardConfiguration}
 *       — Final A2A configuration (skill→task bindings)
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.a2a.A2AHandoffConfiguration}
 *       — Handoff protocol configuration between agents
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.a2a.A2ASkillRegistry}
 *       — Registry of YAWL's 5 A2A skills
 * </ul>
 *
 * <p><b>Authentication:</b> The wizard supports three A2A authentication methods:
 * <ul>
 *   <li>JWT (Bearer token, HMAC-SHA256)
 *   <li>API Key (HMAC-SHA256 with master key)
 *   <li>SPIFFE mTLS (X.509 SVID certificates)
 * </ul>
 *
 * <p><b>YAWL A2A Skills:</b> The YAWL engine exposes 5 core skills:
 * <ul>
 *   <li>{@code launch_workflow} — Start a new workflow case
 *   <li>{@code query_workflows} — List specifications and cases
 *   <li>{@code manage_workitems} — Get and complete work items
 *   <li>{@code cancel_workflow} — Cancel a running case
 *   <li>{@code handoff_workitem} — Transfer work item to another agent
 * </ul>
 *
 * <p><b>Handoff Protocol (ADR-025):</b> Enables secure, stateless transfer of
 * work items between agents. Uses JWT-signed handoff tokens with configurable
 * expiry and authentication methods.
 *
 * @since YAWL 6.0
 * @see org.yawlfoundation.yawl.integration.a2a
 * @see org.yawlfoundation.yawl.integration.a2a.handoff
 */
package org.yawlfoundation.yawl.integration.wizard.a2a;
