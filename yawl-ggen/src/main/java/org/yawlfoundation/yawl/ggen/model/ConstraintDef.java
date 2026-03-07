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

package org.yawlfoundation.yawl.ggen.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Constraint definition in a ProcessSpec.
 *
 * <p>Constraints define relationships between tasks and data objects.
 * Supported types:
 * <ul>
 *   <li>sequence: taskA must complete before taskB starts</li>
 *   <li>parallel: taskA and taskB can execute concurrently</li>
 *   <li>choice: exactly one of taskA or taskB executes</li>
 *   <li>iteration: taskA repeats until condition is met</li>
 *   <li>data_flow: output of taskA feeds into input of taskB</li>
 * </ul>
 *
 * @param type Constraint type: "sequence", "parallel", "choice", "iteration", "data_flow"
 * @param source Source task/object ID
 * @param target Target task/object ID
 * @param condition Guard condition (optional)
 * @param metadata Additional metadata (optional)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConstraintDef(
    String type,
    String source,
    String target,
    String condition,
    Map<String, Object> metadata
) {

    public ConstraintDef {
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Create a sequence constraint (source → target).
     */
    public static ConstraintDef sequence(String source, String target) {
        return new ConstraintDef("sequence", source, target, null, Map.of());
    }

    /**
     * Create a parallel constraint (source || target).
     */
    public static ConstraintDef parallel(String source, String target) {
        return new ConstraintDef("parallel", source, target, null, Map.of());
    }

    /**
     * Create a choice constraint (source XOR target).
     */
    public static ConstraintDef choice(String source, String target) {
        return new ConstraintDef("choice", source, target, null, Map.of());
    }

    /**
     * Create an iteration constraint (task repeats while condition).
     */
    public static ConstraintDef iteration(String task, String condition) {
        return new ConstraintDef("iteration", task, null, condition, Map.of());
    }

    /**
     * Create a data flow constraint.
     */
    public static ConstraintDef dataFlow(String sourceTask, String targetTask) {
        return new ConstraintDef("data_flow", sourceTask, targetTask, null, Map.of());
    }

    /**
     * Create a conditional sequence constraint.
     */
    public static ConstraintDef conditionalSequence(String source, String target, String condition) {
        return new ConstraintDef("sequence", source, target, condition, Map.of());
    }

    /**
     * Check if this constraint has a condition.
     */
    public boolean isConditional() {
        return condition != null && !condition.isEmpty();
    }
}
