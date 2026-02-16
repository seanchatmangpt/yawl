/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.elements.patterns;

/**
 * Mutual exclusion lock for interleaved parallel routing.
 *
 * <p>Provides simple mutex semantics where only one task can
 * hold the lock at a time.</p>
 *
 * @author YAWL Foundation
 * @since 5.3
 */
public class YMutexLock {

    /** Lock identifier */
    private final String _lockId;

    /** Current holder (null if unlocked) */
    private String _holderTaskId;

    /** Timestamp when lock was acquired */
    private long _acquiredTimestamp;

    /** Timeout in milliseconds (0 = no timeout) */
    private long _timeout;

    /**
     * Constructs a new mutex lock.
     *
     * @param lockId the lock identifier
     */
    public YMutexLock(String lockId) {
        this._lockId = lockId;
        this._holderTaskId = null;
        this._acquiredTimestamp = 0;
        this._timeout = 0;
    }

    /**
     * Attempts to acquire the lock.
     *
     * @param taskId the task attempting to acquire
     * @return true if lock acquired
     * @throws MutexAcquisitionException if already held by another task
     */
    public synchronized boolean tryAcquire(String taskId) {
        if (_holderTaskId == null) {
            _holderTaskId = taskId;
            _acquiredTimestamp = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    /**
     * Releases the lock.
     *
     * @param taskId the task releasing the lock
     * @throws MutexReleaseException if not held by the specified task
     */
    public synchronized void release(String taskId) {
        if (taskId.equals(_holderTaskId)) {
            _holderTaskId = null;
            _acquiredTimestamp = 0;
        }
    }

    /**
     * Checks if the lock is currently held.
     *
     * @return true if locked
     */
    public synchronized boolean isLocked() {
        return _holderTaskId != null;
    }

    /**
     * Gets the current holder.
     *
     * @return the holder task ID, or null if unlocked
     */
    public synchronized String getHolder() {
        return _holderTaskId;
    }

    /**
     * Sets the timeout for the lock.
     *
     * @param timeoutMs the timeout in milliseconds
     */
    public void setTimeout(long timeoutMs) {
        this._timeout = timeoutMs;
    }

    /**
     * Checks if the lock has timed out.
     *
     * @return true if timed out
     */
    public synchronized boolean isTimedOut() {
        if (_timeout <= 0 || _acquiredTimestamp == 0) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - _acquiredTimestamp;
        return elapsed >= _timeout;
    }

    /**
     * Gets the duration the lock has been held.
     *
     * @return the duration in milliseconds, 0 if not held
     */
    public synchronized long getHoldDuration() {
        if (_acquiredTimestamp == 0) {
            return 0;
        }
        return System.currentTimeMillis() - _acquiredTimestamp;
    }

    /**
     * Force releases the lock (for recovery purposes).
     */
    public synchronized void forceRelease() {
        _holderTaskId = null;
        _acquiredTimestamp = 0;
    }

    /**
     * Gets the lock identifier.
     *
     * @return the lock ID
     */
    public String getLockId() {
        return _lockId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "YMutexLock{" +
                "lockId='" + _lockId + '\'' +
                ", holder='" + _holderTaskId + '\'' +
                ", held=" + isLocked() +
                '}';
    }
}
