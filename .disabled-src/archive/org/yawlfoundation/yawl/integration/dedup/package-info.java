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
 * Engine deduplication integration plan.
 *
 * Contains {@link org.yawlfoundation.yawl.integration.dedup.EngineDedupPlan}, a
 * structured design document describing the strategy for eliminating duplicated
 * code between the stateful ({@code org.yawlfoundation.yawl.engine}) and
 * stateless ({@code org.yawlfoundation.yawl.stateless}) engine implementations.
 *
 * The plan covers:
 * <ul>
 *   <li>Code sharing matrix across 41 matched file pairs with measured similarity</li>
 *   <li>Three-phase extraction roadmap (data structures, algorithms, orchestration)</li>
 *   <li>Risk analysis for behavior parity (persistence, concurrency, notification)</li>
 *   <li>Test synchronization strategy (parity fixture, phase gates, regression)</li>
 *   <li>Timeline estimate: 18 weeks serial, 13 weeks parallel (2 developers)</li>
 * </ul>
 *
 * @see org.yawlfoundation.yawl.integration.dedup.EngineDedupPlan
 */
package org.yawlfoundation.yawl.integration.dedup;
