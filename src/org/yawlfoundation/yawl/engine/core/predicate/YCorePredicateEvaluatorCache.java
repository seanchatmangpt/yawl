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

import java.util.Set;

/**
 * Canonical implementation of the predicate evaluator cache shared by both engine trees.
 *
 * <p>This is the Phase 1 deduplication result for {@code PredicateEvaluatorCache}.
 * The stateful {@code org.yawlfoundation.yawl.elements.predicate.PredicateEvaluatorCache}
 * and the stateless
 * {@code org.yawlfoundation.yawl.stateless.elements.predicate.PredicateEvaluatorCache}
 * are now thin wrappers whose static methods delegate to a per-class singleton that
 * extends this abstract class.</p>
 *
 * <p>The only differences between the two original files were:</p>
 * <ul>
 *   <li>package declaration</li>
 *   <li>import of {@code YDecomposition} (tree-specific)</li>
 *   <li>import of {@code YIdentifier} (tree-specific)</li>
 *   <li>{@code process()} parameter types (tree-specific)</li>
 * </ul>
 *
 * <p>The instance methods {@link #doSubstitute(String)} and {@link #doAccept(String)}
 * are named with a {@code do} prefix specifically to avoid clashing with the static
 * {@code substitute()} and {@code accept()} methods declared in the concrete subclasses,
 * which Java would otherwise shadow when called from within the same class.</p>
 *
 * <p>Subclasses must implement {@link #loadEvaluators()} to return the tree-specific
 * evaluator set from their respective {@code PredicateEvaluatorFactory}.</p>
 *
 * @param <E> the tree-specific {@code PredicateEvaluator} type, which must extend
 *            {@link ICorePredicateEvaluator}
 *
 * @author Michael Adams (original)
 * @author YAWL Foundation (Phase 1 deduplication, 2026)
 * @since 5.2
 */
public abstract class YCorePredicateEvaluatorCache<E extends ICorePredicateEvaluator> {

    /** Lazily-initialised set of registered evaluators for this tree. */
    private Set<E> _evaluators;


    /**
     * Iterates all registered evaluators, substituting default values into the predicate
     * until no registered evaluator accepts the result.
     *
     * <p>Named {@code doSubstitute} (not {@code substitute}) to avoid name collision
     * with the static {@code substitute()} method in concrete subclasses.</p>
     *
     * @param predicate the predicate expression
     * @return the predicate with all default substitutions applied
     */
    protected final String doSubstitute(String predicate) {
        ICorePredicateEvaluator evaluator = findEvaluator(predicate);
        while (evaluator != null) {
            predicate = evaluator.substituteDefaults(predicate);
            evaluator = findEvaluator(predicate);
        }
        return predicate;
    }


    /**
     * Returns {@code true} if any registered evaluator accepts the predicate.
     *
     * <p>Named {@code doAccept} (not {@code accept}) to avoid name collision with the
     * static {@code accept()} method in concrete subclasses.</p>
     *
     * @param predicate the predicate expression
     * @return true if a registered evaluator handles this predicate
     */
    protected final boolean doAccept(String predicate) {
        return findEvaluator(predicate) != null;
    }


    /**
     * Returns the first registered evaluator that accepts the predicate, cast to the
     * tree-specific type {@code E}, or {@code null} if none accepts it.
     *
     * <p>Used by the concrete subclass to obtain a typed evaluator for calling the
     * tree-specific {@code replace()} method.</p>
     *
     * @param predicate the predicate expression
     * @return a typed evaluator, or {@code null}
     */
    @SuppressWarnings("unchecked")
    protected final E findTypedEvaluator(String predicate) {
        return (E) findEvaluator(predicate);
    }


    /**
     * Loads and returns the full set of evaluators for this engine tree.
     *
     * <p>Called once, lazily, on first use.  Implementations delegate to their tree's
     * {@code PredicateEvaluatorFactory.getInstances()}.</p>
     *
     * @return the set of evaluators; never {@code null}
     */
    protected abstract Set<E> loadEvaluators();


    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the first evaluator that accepts the given predicate, or {@code null}.
     * Evaluators are loaded lazily on first call.  Any exception during loading
     * results in a {@code null} return, matching the original behaviour.
     */
    private ICorePredicateEvaluator findEvaluator(String predicate) {
        try {
            if (_evaluators == null) {
                _evaluators = loadEvaluators();
            }
            for (E evaluator : _evaluators) {
                if (evaluator.accept(predicate)) {
                    return evaluator;
                }
            }
        } catch (Exception e) {
            // fall through to null
        }
        return null;
    }

}
