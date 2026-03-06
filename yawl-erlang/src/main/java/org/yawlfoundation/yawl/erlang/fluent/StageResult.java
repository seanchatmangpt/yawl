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
import java.util.Objects;

/**
 * Result of executing a single pipeline stage.
 *
 * @param stageName name of the stage
 * @param output    the stage's return value (null for void stages or on failure)
 * @param duration  wall-clock execution time
 * @param success   true if stage completed without exception
 * @param error     error message if failed, null if successful
 * @param attempts  total execution attempts (1 = no retries)
 */
public record StageResult(
        String stageName,
        Object output,
        Duration duration,
        boolean success,
        String error,
        int attempts
) {

    public StageResult {
        Objects.requireNonNull(stageName, "stageName must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
        if (attempts < 1) throw new IllegalArgumentException("attempts must be >= 1");
    }

    /** Creates a successful result. */
    static StageResult success(String stageName, Object output, Duration duration, int attempts) {
        return new StageResult(stageName, output, duration, true, null, attempts);
    }

    /** Creates a failed result. */
    static StageResult failure(String stageName, Duration duration, String error, int attempts) {
        return new StageResult(stageName, null, duration, false, error, attempts);
    }
}
