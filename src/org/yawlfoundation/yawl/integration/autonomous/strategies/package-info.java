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
 * Core strategy interfaces for autonomous agent behavior.
 *
 * <p>This package defines the strategy interfaces that control how autonomous
 * agents discover, evaluate, and complete work items. Implementations can
 * be injected to customize agent behavior.</p>
 *
 * <p>Key interfaces:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy} -
 *       How agents discover available work items</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner} -
 *       Determines if an agent should claim a work item</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner} -
 *       Produces output XML for work item completion</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.strategies.OutputGenerator} -
 *       Generates output data for task completion</li>
 * </ul>
 *
 * <p>Built-in implementations:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.strategies.PollingDiscoveryStrategy} -
 *       Periodic polling for new work items</li>
 * </ul>
 */
package org.yawlfoundation.yawl.integration.autonomous.strategies;
