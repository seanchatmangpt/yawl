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
 * A2A Protocol Support for YAWL Milestone Tracking (WCP-18).
 *
 * <p>This package provides integration between YAWL's milestone pattern
 * (WCP-18: case milestone tracking) and the A2A (agent-to-agent) protocol
 * for autonomous agent coordination.
 *
 * <h2>Milestone Events</h2>
 * When a case reaches a milestone condition or a milestone expires,
 * these events trigger notifications to autonomous agents and MCP clients
 * via the A2A protocol. Milestones enable time-sensitive workflow patterns
 * where tasks become enabled only when specific conditions are met.
 *
 * <h2>Message Flow</h2>
 * <pre>
 * YAWL Engine
 *     ↓
 * WorkflowEvent (MILESTONE_REACHED, MILESTONE_EXPIRED, etc.)
 *     ↓
 * AIMQMilestoneAdapter.fromWorkflowEvent()
 *     ↓
 * MilestoneStateMessage (A2A protocol)
 *     ↓
 * McpWorkflowEventPublisher.publish()
 *     ↓
 * WebSocket → Autonomous Agents & MCP Clients
 * </pre>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link MilestoneStateMessage} - A2A protocol message for milestone state changes</li>
 *   <li>{@link AIMQMilestoneAdapter} - Converter between YAWL events and A2A messages</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>
 * // Convert YAWL event to A2A message
 * WorkflowEvent event = ...; // from engine
 * MilestoneStateMessage msg = AIMQMilestoneAdapter.fromWorkflowEvent(event);
 *
 * // Validate and publish
 * AIMQMilestoneAdapter.ValidationResult result = AIMQMilestoneAdapter.validate(msg);
 * if (result.isValid()) {
 *     publisher.publish(msg);
 * }
 *
 * // Create message programmatically
 * MilestoneStateMessage reached = AIMQMilestoneAdapter.createMessage(
 *     "case-123", "OrderProcess:v1", "approval-milestone",
 *     "Approval Received", "REACHED");
 * </pre>
 *
 * <h2>Schema Validation</h2>
 * All milestone messages are validated against the A2A protocol schema
 * before being sent to agents. The schema ensures:
 * - Required fields are present (caseId, milestoneId, state, etc.)
 * - Timestamp is UTC and not in future
 * - State values are valid (REACHED, NOT_REACHED, EXPIRED)
 * - Timing fields are non-negative
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see org.yawlfoundation.yawl.integration.mcp.event.McpWorkflowEventPublisher
 * @see org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent
 * @see org.yawlfoundation.yawl.elements.patterns.YMilestoneCondition
 */
package org.yawlfoundation.yawl.integration.a2a.milestone;
