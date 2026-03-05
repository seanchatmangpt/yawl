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
 * Coordination event logging and management based on ADR-025.
 *
 * <p>This package provides comprehensive event logging for coordination scenarios
 * including conflict detection, resolution, handoff transfers, and agent decisions.
 * All events are properly logged with traceability for audit and debugging purposes.
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.coordination.events.ConflictEvent} -
 *       Events for conflict detection and resolution tracking</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.coordination.events.HandoffEvent} -
 *       Events for handoff transfer tracking between agents/systems</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.coordination.events.ResolutionEvent} -
 *       Events for resolution outcome tracking</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.coordination.events.AgentDecisionEvent} -
 *       Events for agent decision tracking and context</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.coordination.ConflictEventLogger} -
 *       Service for logging coordination events with batch processing and metrics</li>
 * </ul>
 *
 * <h2>Event Correlation</h2>
 * <p>The logging service maintains trace relationships between related events:
 * <ul>
 *   <li>Conflict events are linked to their corresponding resolution events</li>
 *   <li>Handoff events are tracked from initiation to completion</li>
 *   <li>Agent decisions are correlated with their impact on workflow execution</li>
 * </ul>
 *
 * <h2>Integration Points</h2>
 * <p>These coordination events are designed to integrate with:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore} -
 *       Persistent storage of all coordination events</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.a2a.YawlA2AServer} -
 *       Agent-to-agent coordination scenarios</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.mcp.YawlMcpServer} -
 *       Model context protocol interactions</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.integration.coordination;