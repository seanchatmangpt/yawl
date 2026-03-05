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
 * Shared core engine abstractions used by both the stateful and stateless YAWL engines.
 *
 * <p>This package and its subpackages are the result of Phase 1 engine deduplication
 * (EngineDedupPlan). They contain canonical single-copy implementations of algorithms
 * and data structures that were previously duplicated across the
 * {@code org.yawlfoundation.yawl.engine} (stateful) and
 * {@code org.yawlfoundation.yawl.stateless} (stateless) trees.</p>
 *
 * <p>Subpackages:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.engine.core.data} - Shared data validation
 *       infrastructure including {@code YCoreDataValidator} and
 *       {@code IVariableDescriptor} interface</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.core.elements} - Shared Petri-net element
 *       abstractions including {@code E2WFOJCore} for OR-join analysis and
 *       {@code INetElementProvider} functional interface</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.core.marking} - Shared marking data
 *       structures including {@code YCoreMarking}, {@code YCoreSetOfMarkings},
 *       and {@code IMarkingTask}/{@code IMarkingNet} interfaces</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.core.predicate} - Shared predicate
 *       evaluation infrastructure including {@code ICorePredicateEvaluator} and
 *       {@code YCorePredicateEvaluatorCache}</li>
 * </ul></p>
 *
 * <p>Key architectural principle: All classes in this package and subpackages are
 * tree-neutral. They depend only on interfaces (e.g., {@code IMarkingTask}) that
 * both engine trees implement, enabling code sharing without coupling to either
 * tree's concrete types.</p>
 *
 * @since 5.2 (Phase 1 deduplication)
 * @author YAWL Foundation
 */
package org.yawlfoundation.yawl.engine.core;
