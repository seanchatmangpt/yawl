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
 * SAFe (Scaled Agile Framework) participant agents for YAWL v6.
 *
 * <p>Implements autonomous agents for SAFe ceremonies and roles:
 * <ul>
 *   <li>{@link ProductOwnerAgent} - Manages backlog, prioritization, acceptance</li>
 *   <li>{@link ScrumMasterAgent} - Facilitates ceremonies, removes blockers</li>
 *   <li>{@link DeveloperAgent} - Executes user stories, reports progress</li>
 *   <li>{@link SystemArchitectAgent} - Designs systems, manages dependencies</li>
 *   <li>{@link ReleaseTrainEngineerAgent} - Orchestrates PI planning, manages releases</li>
 * </ul>
 *
 * <p>Each agent extends {@link GenericPartyAgent} with SAFe-specific
 * discovery, eligibility, and decision strategies. Agents communicate via
 * workflow tasks and maintain state via work item data containers.
 *
 * <p>Key features:
 * <ul>
 *   <li>Java 25 records for immutable data (UserStory, Sprint, Decision)</li>
 *   <li>Virtual threads for agent discovery and HTTP endpoints</li>
 *   <li>Real database operations via Hibernate ORM</li>
 *   <li>Message routing for ceremony participation</li>
 *   <li>Decision logging with traceability</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
package org.yawlfoundation.yawl.safe.agents;

import org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent;
