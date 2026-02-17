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

package org.yawlfoundation.yawl.engine.core.predicate;

/**
 * Tree-neutral subset of the {@code PredicateEvaluator} interface shared by both
 * engine trees.
 *
 * <p>Both {@code org.yawlfoundation.yawl.elements.predicate.PredicateEvaluator} and
 * {@code org.yawlfoundation.yawl.stateless.elements.predicate.PredicateEvaluator} extend
 * this interface.  The two tree-specific methods ({@code accept} and
 * {@code substituteDefaults}) do not reference any tree-specific types and are therefore
 * declared here so that {@link YCorePredicateEvaluatorCache} can implement the shared
 * {@code substitute()} and {@code accept()} logic without depending on either engine
 * tree.</p>
 *
 * <p>The tree-specific {@code replace(YDecomposition, String, YIdentifier)} method is
 * intentionally omitted; it is declared only in each tree's own
 * {@code PredicateEvaluator} interface.</p>
 *
 * @author YAWL Foundation (Phase 1 deduplication, 2026)
 * @since 5.2
 */
public interface ICorePredicateEvaluator {

    /**
     * Returns {@code true} if this evaluator handles the supplied predicate string.
     *
     * @param predicate the predicate expression to test
     * @return true if this evaluator accepts the predicate
     */
    boolean accept(String predicate);

    /**
     * Substitutes default values into the predicate string.
     *
     * @param predicate the predicate expression to process
     * @return the predicate with defaults substituted
     */
    String substituteDefaults(String predicate);

}
