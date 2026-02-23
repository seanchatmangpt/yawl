/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Domain model for the Occupational Therapy Lifestyle Redesign Swarm.
 *
 * <p>Contains immutable Java 25 records representing the core entities:
 * patients, occupational profiles, lifestyle goals, therapy sessions,
 * and agent task results.</p>
 *
 * <p>All records enforce invariants via canonical constructors and provide
 * defensive copies of mutable collections.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
package org.yawlfoundation.yawl.mcp.a2a.therapy.domain;
