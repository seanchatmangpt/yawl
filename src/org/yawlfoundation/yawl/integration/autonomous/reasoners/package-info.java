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
 * AI-based decision reasoners for autonomous agents.
 *
 * <p>This package provides implementations of decision and eligibility
 * reasoners that use AI services (like Z.AI) to make intelligent workflow
 * decisions.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.reasoners.ZaiDecisionReasoner} -
 *       Z.AI-powered decision making for work item completion</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.reasoners.ZaiEligibilityReasoner} -
 *       Z.AI-powered eligibility checking for work item claims</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.reasoners.StaticMappingReasoner} -
 *       Static rule-based decision making</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.reasoners.TemplateDecisionReasoner} -
 *       Template-based decision making</li>
 * </ul>
 */
package org.yawlfoundation.yawl.integration.autonomous.reasoners;
