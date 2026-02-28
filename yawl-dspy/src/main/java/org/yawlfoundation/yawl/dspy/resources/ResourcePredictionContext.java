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

package org.yawlfoundation.yawl.dspy.resources;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable input context for resource prediction via DSPy.
 *
 * <p>This record captures all the information needed for the DSPy resource routing module
 * to predict which agent is best suited for a given task, based on historical allocation data
 * and current marketplace conditions.</p>
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li><strong>taskType</strong>: The category or skill required (e.g., "data_processing",
 *       "nlp_classification", "mathematical_optimization"). Used to index historical agent
 *       performance.</li>
 *   <li><strong>requiredCapabilities</strong>: A map of capability names to required values
 *       (e.g., {"memory_gb": 8, "skill": "nlp", "language": "python"}). Used to filter agents
 *       and refine predictions.</li>
 *   <li><strong>agentHistoricalScores</strong>: Maps agent IDs to their historical success rate
 *       for this task type (0.0 to 1.0). A key signal for DSPy's Chain-of-Thought reasoning.</li>
 *   <li><strong>currentQueueDepth</strong>: The current size of the marketplace request queue.
 *       High queue depth may bias prediction toward agents with lower latency.</li>
 * </ul>
 *
 * <h2>Immutability Contract</h2>
 * <p>This is a Java 21+ record. The maps are defensive-copied on construction; the provided
 * maps themselves are not mutated by this record.</p>
 *
 * @param taskType                the task category (e.g., "data_processing")
 * @param requiredCapabilities    map of capability names to required values; never null
 * @param agentHistoricalScores   map of agent IDs to success rates (0.0-1.0); never null
 * @param currentQueueDepth       the marketplace request queue depth; must be >= 0
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record ResourcePredictionContext(
        String taskType,
        Map<String, Object> requiredCapabilities,
        Map<String, Double> agentHistoricalScores,
        int currentQueueDepth
) {

    /**
     * Compact constructor that validates and defensively copies the input maps.
     *
     * @throws NullPointerException     if taskType, requiredCapabilities, or
     *                                  agentHistoricalScores is null
     * @throws IllegalArgumentException if taskType is blank or currentQueueDepth < 0
     */
    public ResourcePredictionContext {
        Objects.requireNonNull(taskType, "taskType must not be null");
        if (taskType.isBlank()) {
            throw new IllegalArgumentException("taskType must not be blank");
        }
        Objects.requireNonNull(requiredCapabilities, "requiredCapabilities must not be null");
        Objects.requireNonNull(agentHistoricalScores, "agentHistoricalScores must not be null");
        if (currentQueueDepth < 0) {
            throw new IllegalArgumentException("currentQueueDepth must be >= 0");
        }

        // Defensive copy to prevent external mutation
        requiredCapabilities = new HashMap<>(requiredCapabilities);
        agentHistoricalScores = new HashMap<>(agentHistoricalScores);
    }
}
