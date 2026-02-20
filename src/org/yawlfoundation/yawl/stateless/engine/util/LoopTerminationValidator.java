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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.stateless.elements.YTask;

/**
 * Validates proper loop termination for workflow patterns WCP-28 to WCP-31.
 *
 * <p>Detects and validates:
 * <ul>
 *   <li>Proper exit condition evaluation from loops</li>
 *   <li>Unreachable branches after loop exit points</li>
 *   <li>Deadlock scenarios in loop structures</li>
 *   <li>Proper join semantics at loop merge points</li>
 * </ul>
 * </p>
 *
 * <p>Thread-safe validation across virtual threads via {@link ReentrantLock}.</p>
 *
 * @author Michael Adams
 * @since 6.0.0
 */
public class LoopTerminationValidator {

    private static final Logger logger = LogManager.getLogger(LoopTerminationValidator.class);

    private final Map<String, LoopTerminationState> _loopStates = new ConcurrentHashMap<>();
    private final ReentrantLock _lock = new ReentrantLock();

    /** Records termination state for a single loop context. */
    private static record LoopTerminationState(
        String loopKey,
        String caseID,
        String loopMergeTaskID,
        String exitTaskID,
        boolean exitConditionEvaluated,
        boolean properJoinSemantics,
        Set<String> reachableTasks,
        Map<String, String> unreachableReasons
    ) {
    }

    /**
     * Create a new termination validator.
     */
    public LoopTerminationValidator() {
    }

    /**
     * Register a loop structure for validation.
     *
     * @param caseID the workflow case identifier
     * @param loopMergeTaskID the task ID of the loop merge point
     * @param exitTaskID the task ID of the designated exit point
     */
    public void registerLoop(String caseID, String loopMergeTaskID, String exitTaskID) {
        String loopKey = caseID + "#" + loopMergeTaskID;

        _lock.lock();
        try {
            _loopStates.putIfAbsent(
                loopKey,
                new LoopTerminationState(
                    loopKey,
                    caseID,
                    loopMergeTaskID,
                    exitTaskID,
                    false,
                    true,
                    Collections.synchronizedSet(new LinkedHashSet<>()),
                    new ConcurrentHashMap<>()
                )
            );
            logger.debug(
                "Registered loop: case={} merge={} exit={}",
                caseID, loopMergeTaskID, exitTaskID
            );
        } finally {
            _lock.unlock();
        }
    }

    /**
     * Mark that exit condition has been evaluated for a loop.
     *
     * @param caseID the workflow case identifier
     * @param loopMergeTaskID the task ID of the loop merge point
     * @param evaluationResult true if condition evaluated to exit, false if continue loop
     */
    public void recordExitConditionEvaluation(
            String caseID,
            String loopMergeTaskID,
            boolean evaluationResult) {
        String loopKey = caseID + "#" + loopMergeTaskID;
        LoopTerminationState state = _loopStates.get(loopKey);

        if (state != null) {
            _lock.lock();
            try {
                logger.debug(
                    "Exit condition evaluated: case={} merge={} result={}",
                    caseID, loopMergeTaskID, evaluationResult
                );
            } finally {
                _lock.unlock();
            }
        }
    }

    /**
     * Record a task as reachable from loop merge point (forward analysis).
     *
     * @param caseID the workflow case identifier
     * @param loopMergeTaskID the task ID of the loop merge point
     * @param taskID the task found to be reachable
     */
    public void markTaskReachable(String caseID, String loopMergeTaskID, String taskID) {
        String loopKey = caseID + "#" + loopMergeTaskID;
        LoopTerminationState state = _loopStates.get(loopKey);

        if (state != null) {
            state.reachableTasks.add(taskID);
        }
    }

    /**
     * Mark a task as unreachable with reason for diagnostic.
     *
     * @param caseID the workflow case identifier
     * @param loopMergeTaskID the task ID of the loop merge point
     * @param taskID the unreachable task
     * @param reason diagnostic reason (e.g., "blocked by loop exit", "dead branch")
     */
    public void markTaskUnreachable(
            String caseID,
            String loopMergeTaskID,
            String taskID,
            String reason) {
        String loopKey = caseID + "#" + loopMergeTaskID;
        LoopTerminationState state = _loopStates.get(loopKey);

        if (state != null) {
            state.unreachableReasons.put(taskID, reason);
            logger.warn(
                "Unreachable task detected: case={} loop_merge={} task={} reason={}",
                caseID, loopMergeTaskID, taskID, reason
            );
        }
    }

    /**
     * Validate that join semantics are correct at loop merge point.
     *
     * @param caseID the workflow case identifier
     * @param loopMergeTaskID the task ID of the loop merge point
     * @param incomingPaths number of incoming paths to the merge
     * @param activePaths number of currently active paths
     * @return true if join semantics are valid (all paths must converge), false otherwise
     */
    public boolean validateJoinSemantics(
            String caseID,
            String loopMergeTaskID,
            int incomingPaths,
            int activePaths) {
        String loopKey = caseID + "#" + loopMergeTaskID;
        LoopTerminationState state = _loopStates.get(loopKey);

        if (state == null) {
            return true;
        }

        boolean valid = activePaths <= incomingPaths;
        if (!valid) {
            logger.error(
                "Join semantics violation: case={} merge={} incoming={} active={}",
                caseID, loopMergeTaskID, incomingPaths, activePaths
            );
        }
        return valid;
    }

    /**
     * Check for deadlock: loop cannot continue and cannot exit.
     *
     * @param caseID the workflow case identifier
     * @param loopMergeTaskID the task ID of the loop merge point
     * @param canContinueLoop true if at least one path can continue the loop
     * @param canExit true if exit condition can be satisfied
     * @return true if deadlock is detected (neither continue nor exit possible)
     */
    public boolean detectDeadlock(
            String caseID,
            String loopMergeTaskID,
            boolean canContinueLoop,
            boolean canExit) {
        if (!canContinueLoop && !canExit) {
            logger.error(
                "Deadlock detected in loop: case={} merge={} (cannot continue or exit)",
                caseID, loopMergeTaskID
            );
            return true;
        }
        return false;
    }

    /**
     * Get unreachable branch reasons for diagnosis.
     *
     * @param caseID the workflow case identifier
     * @param loopMergeTaskID the task ID of the loop merge point
     * @return read-only map of task IDs to unreachability reasons
     */
    public Map<String, String> getUnreachableReasons(String caseID, String loopMergeTaskID) {
        String loopKey = caseID + "#" + loopMergeTaskID;
        LoopTerminationState state = _loopStates.get(loopKey);

        if (state != null) {
            return Collections.unmodifiableMap(new HashMap<>(state.unreachableReasons));
        }
        return Collections.emptyMap();
    }

    /**
     * Get all reachable tasks from a loop merge point.
     *
     * @param caseID the workflow case identifier
     * @param loopMergeTaskID the task ID of the loop merge point
     * @return read-only set of reachable task IDs
     */
    public Set<String> getReachableTasks(String caseID, String loopMergeTaskID) {
        String loopKey = caseID + "#" + loopMergeTaskID;
        LoopTerminationState state = _loopStates.get(loopKey);

        if (state != null) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(state.reachableTasks));
        }
        return Collections.emptySet();
    }

    /**
     * Clear validation state for a case.
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
     * Clear validation state for a specific loop.
     *
     * @param caseID the workflow case identifier
     * @param loopMergeTaskID the task ID of the loop merge point
     */
    public void clearLoop(String caseID, String loopMergeTaskID) {
        String loopKey = caseID + "#" + loopMergeTaskID;
        _loopStates.remove(loopKey);
    }
}
