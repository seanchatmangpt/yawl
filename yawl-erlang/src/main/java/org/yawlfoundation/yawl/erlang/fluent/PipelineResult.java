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

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate result of a complete pipeline execution.
 *
 * <p>Contains individual {@link StageResult}s for each stage plus summary metrics.
 * Use {@link #stageOutput(String, Class)} to retrieve a specific stage's output
 * with type-safe casting.
 *
 * @param stages        ordered list of stage results
 * @param totalDuration wall-clock time for entire pipeline
 * @param success       true if all stages completed successfully
 * @param totalRestarts cumulative restart count across all stages
 * @param context       the execution context (for post-pipeline access to results)
 */
public record PipelineResult(
        List<StageResult> stages,
        Duration totalDuration,
        boolean success,
        int totalRestarts,
        PipelineContext context
) {

    public PipelineResult {
        Objects.requireNonNull(stages, "stages must not be null");
        Objects.requireNonNull(totalDuration, "totalDuration must not be null");
        Objects.requireNonNull(context, "context must not be null");
        stages = List.copyOf(stages);
    }

    /**
     * Retrieves a specific stage's output with type-safe casting.
     *
     * @param stageName stage name
     * @param type      expected output type
     * @param <T>       output type
     * @return the stage output, or empty if stage failed or produced null
     */
    public <T> Optional<T> stageOutput(String stageName, Class<T> type) {
        return stages.stream()
                .filter(s -> s.stageName().equals(stageName) && s.success())
                .findFirst()
                .map(s -> type.cast(s.output()));
    }

    /**
     * Returns the first failed stage, if any.
     *
     * @return the first failed StageResult, or empty if all succeeded
     */
    public Optional<StageResult> firstFailure() {
        return stages.stream().filter(s -> !s.success()).findFirst();
    }
}
