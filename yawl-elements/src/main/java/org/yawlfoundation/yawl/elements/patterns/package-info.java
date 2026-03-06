/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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
 * Workflow Control Patterns (WCP) implementations.
 *
 * <p>This package provides implementations of YAWL Workflow Control Patterns,
 * enabling advanced workflow semantics beyond basic YAWL constructs.</p>
 *
 * <h2>Patterns Implemented</h2>
 * <ul>
 *   <li><b>WCP-18: Milestone (Deferred Choice)</b> - Enables deferred branching based
 *       on milestone conditions that become true during case execution.</li>
 * </ul>
 *
 * <h2>Milestone Pattern (WCP-18)</h2>
 * <p>A milestone condition enables deferred execution of dependent tasks when
 * a condition is satisfied. Multiple milestones can be combined with AND, OR,
 * or XOR operators to guard task execution.</p>
 *
 * <p>Example: Ship order only after payment verification AND inventory confirmation,
 * where both happen asynchronously during case execution.</p>
 *
 * @since 5.3
 * @author YAWL Foundation
 */
package org.yawlfoundation.yawl.elements.patterns;
