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
 * Tree-neutral predicate evaluation infrastructure shared by the stateful and stateless
 * engine trees.
 *
 * <p>Phase 1 deduplication (EngineDedupPlan P1.5) extracted the predicate evaluator
 * cache logic from the two near-identical
 * {@code PredicateEvaluatorCache} classes (96% similarity) into this package.</p>
 *
 * <h3>Contents</h3>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.engine.core.predicate.ICorePredicateEvaluator} —
 *       minimal tree-neutral interface ({@code accept}, {@code substituteDefaults})</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.core.predicate.YCorePredicateEvaluatorCache} —
 *       abstract base implementing the shared {@code substitute()} and {@code accept()}
 *       cache logic</li>
 * </ul>
 *
 * <h3>Extension points</h3>
 * <p>Each engine tree provides a concrete subclass of
 * {@code YCorePredicateEvaluatorCache} that implements {@code loadEvaluators()} (to
 * return the tree-specific factory instances) and exposes static delegation methods
 * that match the original {@code PredicateEvaluatorCache} API.</p>
 */
package org.yawlfoundation.yawl.engine.core.predicate;
