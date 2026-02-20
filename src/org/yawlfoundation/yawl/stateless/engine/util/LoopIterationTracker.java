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

package org.yawlfoundation.yawl.stateless.engine.util;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Tracks loop iteration counts and detects infinite loops for workflow patterns WCP-28 to WCP-31.
 *
 * <p>This tracker monitors:
 * <ul>
 *   <li>WCP-28 (Structured Loop) - Fixed iterations with loop condition</li>
 *   <li>WCP-29 (Arbitrary Cycle) - Arbitrary loop with join-merge pair</li>
 *   <li>WCP-30 (Structured Interleaved Routing) - Interleaved parallel loops</li>
 *   <li>WCP-31 (Milestone) - Loop with cancellation condition</li>
 * </ul>
 * </p>
 *
 * <p>Thread-safe iteration tracking across virtual threads via {@link ReentrantLock}.</p>
 *
 * @author Michael Adams
 * @since 6.0.0
 */
public class LoopIterationTracker {

    private static final Logger logger = LogManager.getLogger(LoopIterationTracker.class);

    private static final int DEFAULT_INFINITE_LOOP_THRESHOLD = 10000;

    private final Map<String, LoopIterationState> _loopStates = new ConcurrentHashMap<>();
    private final ReentrantLock _lock = new ReentrantLock();
    private final int _infiniteLoopThreshold;

    /** Records iteration state for a single loop context (task within a case). */
    private static record LoopIterationState(
        String loopKey,
        String caseID,
        String taskID,
        AtomicInteger iterationCount,
        Instant startTime,
        String loopPattern
    ) {
    }

    /**
     * Create a tracker with default infinite loop threshold (10000 iterations).
     */
    public LoopIterationTracker() {
        this(DEFAULT_INFINITE_LOOP_THRESHOLD);
    }

    /**
     * Create a tracker with custom infinite loop threshold.
     *
     * @param infiniteLoopThreshold iteration count that triggers infinite loop detection
     */
    public LoopIterationTracker(int infiniteLoopThreshold) {
        this._infiniteLoopThreshold = infiniteLoopThreshold;
    }

    /**
     * Start tracking a loop iteration for the given case and task.
     *
     * @param caseID the workflow case identifier
     * @param taskID the task identifier (should be the loop merge point)
     * @param loopPattern the workflow pattern (WCP-28, WCP-29, WCP-30, WCP-31)
     * @return the iteration number (starting at 1)
     * @throws IllegalStateException if iteration exceeds threshold
     */
    public int trackLoopIteration(String caseID, String taskID, String loopPattern) {
        String loopKey = caseID + "#" + taskID;

        _lock.lock();
        try {
            LoopIterationState state = _loopStates.computeIfAbsent(
                loopKey,
                key -> new LoopIterationState(
                    key,
                    caseID,
                    taskID,
                    new AtomicInteger(0),
                    Instant.now(),
                    loopPattern
                )
            );

            int iteration = state.iterationCount.incrementAndGet();

            if (iteration > _infiniteLoopThreshold) {
                String msg = String.format(
                    "Infinite loop detected: case=%s task=%s pattern=%s iterations=%d threshold=%d",
                    caseID, taskID, loopPattern, iteration, _infiniteLoopThreshold
                );
                logger.error(msg);
                throw new IllegalStateException(msg);
            }

            if (iteration % 1000 == 0) {
                logger.warn(
                    "High iteration count: case={} task={} pattern={} iterations={}",
                    caseID, taskID, loopPattern, iteration
                );
            }

            return iteration;
        } finally {
            _lock.unlock();
        }
    }

    /**
     * Get current iteration count for a loop context.
     *
     * @param caseID the workflow case identifier
     * @param taskID the task identifier
     * @return iteration count, or 0 if no tracking entry exists
     */
    public int getIterationCount(String caseID, String taskID) {
        String loopKey = caseID + "#" + taskID;
        LoopIterationState state = _loopStates.get(loopKey);
        return state != null ? state.iterationCount.get() : 0;
    }

    /**
     * Check if a loop has exceeded the warning threshold (80% of limit).
     *
     * @param caseID the workflow case identifier
     * @param taskID the task identifier
     * @return true if iteration count > (threshold * 0.8)
     */
    public boolean isNearInfiniteLoop(String caseID, String taskID) {
        int current = getIterationCount(caseID, taskID);
        return current > (_infiniteLoopThreshold * 0.8);
    }

    /**
     * Reset iteration tracking for a specific loop.
     *
     * @param caseID the workflow case identifier
     * @param taskID the task identifier
     */
    public void resetIteration(String caseID, String taskID) {
        String loopKey = caseID + "#" + taskID;
        _loopStates.remove(loopKey);
    }

    /**
     * Clear all iteration tracking for a case.
     *
     * @param caseID the workflow case identifier
     */
    public void clearCase(String caseID) {
        _lock.lock();
        try {
            _loopStates.entrySet().removeIf(e -> e.getValue().caseID.equals(caseID));
        } finally {
            _lock.unlock();
        }
    }

    /**
     * Get iteration metrics for all active loops (read-only view).
     *
     * @return immutable map of loop keys to iteration counts
     */
    public Map<String, Integer> getMetrics() {
        Map<String, Integer> metrics = new LinkedHashMap<>();
        _lock.lock();
        try {
            _loopStates.forEach((key, state) -> {
                metrics.put(key, state.iterationCount.get());
            });
            return Collections.unmodifiableMap(metrics);
        } finally {
            _lock.unlock();
        }
    }

    /**
     * Get elapsed time for a loop (from first iteration to now).
     *
     * @param caseID the workflow case identifier
     * @param taskID the task identifier
     * @return elapsed milliseconds, or -1 if no tracking entry exists
     */
    public long getElapsedMillis(String caseID, String taskID) {
        String loopKey = caseID + "#" + taskID;
        LoopIterationState state = _loopStates.get(loopKey);
        if (state == null) return -1;
        return java.time.Duration.between(state.startTime, Instant.now()).toMillis();
    }

    /**
     * Get loop pattern for a tracked loop.
     *
     * @param caseID the workflow case identifier
     * @param taskID the task identifier
     * @return loop pattern string (e.g., "WCP-28"), or null if not tracked
     */
    public String getLoopPattern(String caseID, String taskID) {
        String loopKey = caseID + "#" + taskID;
        LoopIterationState state = _loopStates.get(loopKey);
        return state != null ? state.loopPattern : null;
    }
}
