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
import java.time.Instant;

/**
 * Sealed hierarchy of lifecycle events emitted during pipeline execution.
 *
 * <p>Events are delivered to listeners registered via
 * {@link ProcessMiningPipeline.Builder#onEvent(java.util.function.Consumer)}.
 * Exhaustive pattern matching is possible via switch expressions:
 *
 * <pre>{@code
 * pipeline.builder()
 *     .onEvent(event -> switch (event) {
 *         case StageStarted s   -> log.info("Started: {}", s.stageName());
 *         case StageCompleted c -> log.info("Done: {} in {}", c.stageName(), c.duration());
 *         case StageFailed f    -> log.warn("Failed: {} attempt {}", f.stageName(), f.attempt());
 *         case StageRestarted r -> log.warn("Restarted: {}", r.stageName());
 *         case CircuitOpened o  -> log.error("Circuit open: {}", o.stageName());
 *     })
 *     // ...
 * }</pre>
 */
public sealed interface PipelineEvent {

    /** Emitted when a stage begins execution. */
    record StageStarted(String stageName, Instant timestamp) implements PipelineEvent {}

    /** Emitted when a stage completes successfully. */
    record StageCompleted(String stageName, Instant timestamp, Duration duration)
            implements PipelineEvent {}

    /** Emitted when a stage fails. {@code attempt} is 1-based. */
    record StageFailed(String stageName, Instant timestamp, String error, int attempt)
            implements PipelineEvent {}

    /** Emitted when the supervisor restarts a failed stage. */
    record StageRestarted(String stageName, Instant timestamp, int restartCount)
            implements PipelineEvent {}

    /** Emitted when a stage's circuit breaker opens due to repeated failures. */
    record CircuitOpened(String stageName, Instant timestamp, int consecutiveFailures)
            implements PipelineEvent {}
}
