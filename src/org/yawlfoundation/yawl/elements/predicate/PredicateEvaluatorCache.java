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

package org.yawlfoundation.yawl.elements.predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.elements.YDecomposition;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.core.predicate.YCorePredicateEvaluatorCache;

import java.util.Set;

/**
 * Stateful-engine thin wrapper around
 * {@link org.yawlfoundation.yawl.engine.core.predicate.YCorePredicateEvaluatorCache}.
 *
 * <p>This class was refactored as part of Phase 1 engine deduplication
 * (EngineDedupPlan P1.5).  The shared {@code substitute()} and {@code accept()} logic
 * now lives in {@link YCorePredicateEvaluatorCache}.  This wrapper adds only the
 * stateful-tree-specific {@code process()} method whose signature depends on the
 * stateful {@link YDecomposition} and {@link YIdentifier} types.</p>
 *
 * <p>The public static API is unchanged: existing callers continue to work without
 * modification.</p>
 *
 * @author Michael Adams (original)
 * @author YAWL Foundation (Phase 1 deduplication, 2026)
 */
public class PredicateEvaluatorCache
        extends YCorePredicateEvaluatorCache<PredicateEvaluator> {

    private static final Logger _log = LogManager.getLogger(PredicateEvaluatorCache.class);
    private static Set<PredicateEvaluator> _evaluators;

    private PredicateEvaluatorCache() { }


    // -------------------------------------------------------------------------
    // Static public API (unchanged from original)
    // -------------------------------------------------------------------------

    public static String process(YDecomposition decomposition, String predicate,
                                 YIdentifier token) {
        PredicateEvaluator evaluator = _instance.findTypedEvaluator(predicate);
        while (evaluator != null) {
            predicate = evaluator.replace(decomposition, predicate, token);
            evaluator = _instance.findTypedEvaluator(predicate);
        }
        return predicate;
    }


    public static String substitute(String predicate) {
        return _instance.doSubstitute(predicate);
    }


    public static boolean accept(String predicate) {
        return _instance.doAccept(predicate);
    }


    private static PredicateEvaluator getEvaluator(String predicate) {
        try {
            if (_evaluators == null) {
                _evaluators = PredicateEvaluatorFactory.getInstances();
            }
            for (PredicateEvaluator evaluator : _evaluators) {
                if (evaluator.accept(predicate)) {
                    return evaluator;
                }
            }
        } catch (Exception e) {
            _log.error("PredicateEvaluator lookup failed for predicate '{}': {}", predicate, e.getMessage(), e);
        }
        return null;
    }

}
