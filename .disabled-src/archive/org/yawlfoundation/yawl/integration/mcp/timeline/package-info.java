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
 * Live Case ASCII Timeline Renderer - MCP Integration.
 *
 * This package provides visualization tools for YAWL workflow case execution.
 * The CaseTimelineRenderer generates beautiful ASCII Gantt-style timelines showing:
 *
 * - Task execution history with proportional timeline bars
 * - Current state and status of all tasks
 * - Elapsed time and progress calculations
 * - Performance anomalies and warnings
 * - Human-readable summary statistics
 *
 * The timeline gracefully degrades when timing data is unavailable, falling back
 * to status-only visualization. This enables AI agents and CLI users to instantly
 * see where time is being spent in a case and identify which tasks are slow.
 *
 * Integration:
 * - CaseTimelineRenderer: Core visualization engine
 * - CaseTimelineSpecification: MCP tool factory (yawl_case_timeline)
 *
 * Example usage:
 * {@code
 * String timeline = CaseTimelineRenderer.renderTimeline(
 *     "case_42",
 *     "OrderProcessing",
 *     startTime,
 *     currentTime,
 *     workItems,
 *     50 // bar width
 * );
 * System.out.println(timeline);
 * }
 *
 * @since 6.0.0 (2026-02-24)
 * @author YAWL Foundation
 */
package org.yawlfoundation.yawl.integration.mcp.timeline;
