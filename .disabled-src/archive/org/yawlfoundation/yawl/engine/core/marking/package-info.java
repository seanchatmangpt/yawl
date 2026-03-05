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
 * Shared Petri-net marking abstractions used by both the stateful engine
 * ({@code org.yawlfoundation.yawl.elements.state}) and the stateless engine
 * ({@code org.yawlfoundation.yawl.stateless.elements.marking}).
 *
 * <p>This package is the result of Phase 1 engine deduplication (EngineDedupPlan P1.2).
 * It contains the canonical single-copy implementations of marking data structures
 * that were previously duplicated across both engine trees. Stateful and stateless
 * subpackages contain thin wrapper classes that extend or delegate to these
 * canonical implementations.</p>
 *
 * <p>Contents:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.engine.core.marking.IMarkingTask} - interface
 *       abstracting the task operations needed by marking algorithms (join/split type,
 *       preset/postset, remove set). Both {@code elements.YTask} and
 *       {@code stateless.elements.YTask} implement this interface.</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.core.marking.IMarkingNet} - interface
 *       abstracting the net-level post-set query used by deadlock detection.
 *       Both {@code elements.YNet} and {@code stateless.elements.YNet} implement this.</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.core.marking.YCoreMarking} - unified
 *       marking implementation parameterised by the task and condition types.</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.core.marking.YCoreSetOfMarkings} - unified
 *       set-of-markings implementation.</li>
 * </ul></p>
 *
 * @since 5.2 (Phase 1 deduplication)
 */
package org.yawlfoundation.yawl.engine.core.marking;
