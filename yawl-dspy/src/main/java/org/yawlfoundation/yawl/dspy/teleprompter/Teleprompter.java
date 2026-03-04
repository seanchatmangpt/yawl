/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.dspy.teleprompter;

import org.yawlfoundation.yawl.dspy.evaluate.EvaluationResult;
import org.yawlfoundation.yawl.dspy.evaluate.Metric;
import org.yawlfoundation.yawl.dspy.module.Module;
import org.yawlfoundation.yawl.dspy.signature.Example;

import java.util.List;

/**
 * Base interface for DSPy teleprompters (optimizers).
 *
 * <p>Teleprompters optimize DSPy modules using training data. The name
 * comes from "tele-" (at a distance) + "prompter" (one who prompts),
 * indicating that these optimizers automatically improve prompts.
 *
 * <h2>Available Teleprompters:</h2>
 * <ul>
 *   <li>{@link BootstrapFewShot} - Generates few-shot examples from successful runs</li>
 *   <li>{@link MIPROv2} - Multi-prompt instruction optimization</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * {@snippet :
 * // Define training data
 * List<Example> trainset = List.of(
 *     Example.of(Map.of("caseEvents", events1), Map.of("outcome", "completed")),
 *     Example.of(Map.of("caseEvents", events2), Map.of("outcome", "failed"))
 * );
 *
 * // Define metric
 * Metric metric = Metric.accuracy("outcome");
 *
 * // Bootstrap few-shot examples
 * var optimizer = new BootstrapFewShot<>(metric, 5);
 * Module<?> optimized = optimizer.compile(predictor, trainset);
 *
 * // Use optimized module
 * var result = optimized.run(inputs);
 * }
 *
 * @param <M> the module type being optimized
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public interface Teleprompter<M extends Module<?>> {

    /**
     * Compile/optimize a module using training data.
     *
     * @param module the module to optimize
     * @param trainset the training examples
     * @return an optimized version of the module
     * @throws OptimizationException if optimization fails
     */
    M compile(M module, List<Example> trainset);

    /**
     * Get the name of this teleprompter.
     */
    default String name() {
        return getClass().getSimpleName();
    }

    /**
     * Get the optimization trace (for debugging).
     */
    default List<OptimizationStep> trace() {
        return List.of();
    }
}
