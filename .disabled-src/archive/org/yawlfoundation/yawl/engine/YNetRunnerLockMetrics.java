/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.engine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicLong;

/**
 * P2 HIGH - Lock Contention Observability: per-engine lock wait metrics for YNetRunner.
 *
 * <p>Tracks wait time on the exclusive write-lock used for task-mutation operations in
 * {@link YNetRunner}.  A separate instance is held by each YNetRunner so contention can
 * be attributed to individual cases.</p>
 *
 * <p>These metrics are published to the observability stack (logs, OpenTelemetry) so that
 * p99 lock-wait time and contention frequency can be monitored in production.</p>
 *
 * <p>Overhead: two {@link AtomicLong} increments per lock acquisition - negligible
 * compared to the work done inside the lock.</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YNetRunnerLockMetrics {

    private static final Logger _logger = LogManager.getLogger(YNetRunnerLockMetrics.class);

    /** Total number of write-lock acquisitions attempted. */
    private final AtomicLong _writeLockAcquisitions = new AtomicLong(0);

    /** Cumulative nanoseconds spent waiting for the write-lock. */
    private final AtomicLong _writeLockWaitNanos = new AtomicLong(0);

    /** Maximum single write-lock wait in nanoseconds. */
    private volatile long _maxWriteLockWaitNanos = 0;

    /** Total number of read-lock acquisitions attempted. */
    private final AtomicLong _readLockAcquisitions = new AtomicLong(0);

    /** Cumulative nanoseconds spent waiting for the read-lock. */
    private final AtomicLong _readLockWaitNanos = new AtomicLong(0);

    /** Case id this metrics instance belongs to (for log attribution). */
    private final String _caseId;

    public YNetRunnerLockMetrics(String caseId) {
        this._caseId = caseId;
    }

    /**
     * Records a completed write-lock wait.
     *
     * @param waitNanos nanoseconds spent waiting before the lock was acquired
     */
    public void recordWriteLockWait(long waitNanos) {
        _writeLockAcquisitions.incrementAndGet();
        _writeLockWaitNanos.addAndGet(waitNanos);
        if (waitNanos > _maxWriteLockWaitNanos) {
            _maxWriteLockWaitNanos = waitNanos;
        }
        if (waitNanos > 10_000_000L) { // > 10ms: warn on significant contention
            _logger.warn("YNetRunner[{}] write-lock wait {}ms - potential contention",
                    _caseId, waitNanos / 1_000_000);
        }
    }

    /**
     * Records a completed read-lock wait.
     *
     * @param waitNanos nanoseconds spent waiting before the lock was acquired
     */
    public void recordReadLockWait(long waitNanos) {
        _readLockAcquisitions.incrementAndGet();
        _readLockWaitNanos.addAndGet(waitNanos);
    }

    /** Returns the average write-lock wait in milliseconds. */
    public double avgWriteLockWaitMs() {
        long acq = _writeLockAcquisitions.get();
        return acq == 0 ? 0.0 : _writeLockWaitNanos.get() / (double) acq / 1_000_000.0;
    }

    /** Returns the average read-lock wait in milliseconds. */
    public double avgReadLockWaitMs() {
        long acq = _readLockAcquisitions.get();
        return acq == 0 ? 0.0 : _readLockWaitNanos.get() / (double) acq / 1_000_000.0;
    }

    /** Returns the maximum single write-lock wait in milliseconds. */
    public double maxWriteLockWaitMs() {
        return _maxWriteLockWaitNanos / 1_000_000.0;
    }

    /** Returns a formatted summary of lock metrics for this runner. */
    public String summary() {
        return String.format(
            "YNetRunner[%s] LockMetrics{writeLocks=%d, avgWriteWait=%.3fms, " +
            "maxWriteWait=%.3fms, readLocks=%d, avgReadWait=%.3fms}",
            _caseId,
            _writeLockAcquisitions.get(), avgWriteLockWaitMs(), maxWriteLockWaitMs(),
            _readLockAcquisitions.get(), avgReadLockWaitMs());
    }

    /**
     * Logs the metrics summary at INFO level - intended to be called on case completion
     * so per-case contention is visible in production logs.
     */
    public void logSummary() {
        _logger.info(summary());
    }
}
