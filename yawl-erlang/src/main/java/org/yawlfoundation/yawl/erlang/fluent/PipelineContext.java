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
package org.yawlfoundation.yawl.erlang.fluent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Mutable execution context for passing data between pipeline stages.
 *
 * <p>Each stage receives the context and can read results from prior stages
 * via {@link #get(String, Class)} and store its own output via the pipeline
 * executor (which calls {@link #put(String, Object)} after each stage).</p>
 *
 * <p>Insertion order is preserved, reflecting the pipeline execution order.
 *
 * <p>Usage within a pipeline stage:
 * <pre>{@code
 * .stage("discover", ctx -> {
 *     OcelLogHandle log = ctx.get("parse", OcelLogHandle.class);
 *     return engine.discoverDfg(log);
 * })
 * }</pre>
 */
public final class PipelineContext {

    private final Map<String, Object> results = new LinkedHashMap<>();

    PipelineContext() {}

    /**
     * Stores a stage result in the context.
     *
     * @param stageName the stage name (used as key)
     * @param value     the stage output (may be null)
     */
    void put(String stageName, Object value) {
        results.put(Objects.requireNonNull(stageName, "stageName must not be null"), value);
    }

    /**
     * Retrieves a prior stage's result with type-safe casting.
     *
     * @param stageName the name of the prior stage
     * @param type      expected result type
     * @param <T>       result type
     * @return the stage result cast to the requested type
     * @throws IllegalArgumentException if no result exists for the given stage name
     * @throws ClassCastException       if the result cannot be cast to the requested type
     */
    public <T> T get(String stageName, Class<T> type) {
        Objects.requireNonNull(stageName, "stageName must not be null");
        Objects.requireNonNull(type, "type must not be null");
        if (!results.containsKey(stageName)) {
            throw new IllegalArgumentException(
                    "No result for stage '" + stageName + "'. Available: " + results.keySet());
        }
        return type.cast(results.get(stageName));
    }

    /**
     * Checks whether a result exists for the given stage name.
     *
     * @param stageName the stage name
     * @return true if a result has been stored
     */
    public boolean has(String stageName) {
        return results.containsKey(stageName);
    }

    /**
     * Returns the set of stage names that have produced results, in execution order.
     *
     * @return unmodifiable set of completed stage names
     */
    public Set<String> completedStages() {
        return Set.copyOf(results.keySet());
    }

    /**
     * Clears all stage results. Used by the supervisor when restarting
     * stages under {@link RestartStrategy#ONE_FOR_ALL}.
     */
    void clear() {
        results.clear();
    }

    /**
     * Removes results for the given stage and all stages inserted after it.
     * Used by {@link RestartStrategy#REST_FOR_ONE} to invalidate dependent results.
     *
     * @param fromStage the stage name (inclusive) from which to remove
     */
    void clearFrom(String fromStage) {
        boolean found = false;
        var iterator = results.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().equals(fromStage)) {
                found = true;
            }
            if (found) {
                iterator.remove();
            }
        }
    }
}
