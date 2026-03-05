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
 * Reasoning implementations for autonomous agent eligibility decisions.
 *
 * <p>Provides concrete implementations of the
 * {@link org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner}
 * functional interface for determining whether an agent should process a given work item.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.reasoners.StaticMappingReasoner} -
 *       Configuration-driven reasoner that maps task name patterns to capability domain names.
 *       Supports exact match and wildcard patterns ({@code *} and {@code ?}).</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
package org.yawlfoundation.yawl.integration.autonomous.reasoners;
