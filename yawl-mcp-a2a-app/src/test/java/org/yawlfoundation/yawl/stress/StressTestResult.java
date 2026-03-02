/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stress;

import java.time.Duration;

/**
 * Base class for stress test results.
 *
 * @author YAWL Stress Testing Framework
 * @version 1.0
 */
public abstract class StressTestResult {

    private final Duration duration;
    private final boolean success;
    private final int errorCount;
    private final long timestamp;

    protected StressTestResult(Duration duration, boolean success, int errorCount) {
        this.duration = duration;
        this.success = success;
        this.errorCount = errorCount;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters
    public Duration getDuration() {
        return duration;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public long getTimestamp() {
        return timestamp;
    }
}