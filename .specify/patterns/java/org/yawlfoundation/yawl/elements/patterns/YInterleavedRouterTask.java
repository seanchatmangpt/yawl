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

import org.yawlfoundation.yawl.elements.YExternalNetElement;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.YPersistenceManager;
import org.yawlfoundation.yawl.exceptions.*;

import java.util.List;
import java.util.Set;

/**
 * Implements the Interleaved Parallel Routing pattern (WCP-17).
 *
 * <p>Allows a set of tasks to be enabled concurrently, but only one
 * task can execute at a time (mutual exclusion). Once a task completes,
 * another enabled task may begin.</p>
 *
 * <p>State Machine:</p>
 * <ul>
 *   <li>IDLE: No tasks enabled</li>
 *   <li>ENABLING: Tasks being enabled, waiting for mutex</li>
 *   <li>EXECUTING_ONE: One task executing, others waiting</li>
 *   <li>ALL_COMPLETE: All enabled tasks completed</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @since 5.3
 * @see YInterleavedSet
 * @see YMutexLock
 */
public class YInterleavedRouterTask extends YTask {

    /** Join type constant for interleaved router */
    public static final int _INTERLEAVED_JOIN = 70;

    /** Split type constant for interleaved router */
    public static final int _INTERLEAVED_SPLIT = 71;

    /** The set of interleaved tasks */
    private YInterleavedSet _interleavedSet;

    /** Mutex lock for mutual exclusion */
    private YMutexLock _mutexLock;

    /** Router state tracker */
    private YInterleavedState _routerState;

    /** Strategy for selecting next task to execute */
    private InterleavedSelectionStrategy _selectionStrategy;

    /**
     * Constructs a new interleaved router task.
     *
     * @param id the task identifier
     * @param joinType the join type
     * @param splitType the split type
     * @param container the containing net
     */
    public YInterleavedRouterTask(String id, int joinType, int splitType, YNet container) {
        super(id, joinType, splitType, container);
        _interleavedSet = new YInterleavedSet(id);
        _mutexLock = new YMutexLock(id + "_mutex");
        _routerState = new YInterleavedState(id);
        _selectionStrategy = InterleavedSelectionStrategy.FIFO;
    }

    /**
     * Enables all tasks in the interleaved set.
     *
     * @param pmgr the persistence manager
     * @throws YPersistenceException if persistence fails
     */
    public void enableAllTasks(YPersistenceManager pmgr)
            throws YPersistenceException {
        _routerState.setPhase(YInterleavedState.RouterPhase.ENABLING);
        _interleavedSet.enableAll();

        if (pmgr != null) {
            pmgr.updateObjectExternal(_routerState);
            pmgr.updateObjectExternal(_interleavedSet);
        }

        // Try to start first task
        startNextTask(pmgr);
    }

    /**
     * Attempts to acquire the mutex for a task.
     *
     * @param taskId the task identifier
     * @param pmgr the persistence manager
     * @return true if mutex acquired
     * @throws YPersistenceException if persistence fails
     */
    public boolean tryAcquireMutex(String taskId, YPersistenceManager pmgr)
            throws YPersistenceException {
        if (_mutexLock.tryAcquire(taskId)) {
            _interleavedSet.setTaskState(taskId, YInterleavedSet.TaskState.ENABLED_EXECUTING);
            _routerState.setPhase(YInterleavedState.RouterPhase.EXECUTING_ONE);
            _routerState.setExecutingTaskId(taskId);

            if (pmgr != null) {
                pmgr.updateObjectExternal(_mutexLock);
                pmgr.updateObjectExternal(_interleavedSet);
                pmgr.updateObjectExternal(_routerState);
            }
            return true;
        }
        return false;
    }

    /**
     * Releases the mutex.
     *
     * @param pmgr the persistence manager
     * @throws YPersistenceException if persistence fails
     */
    public void releaseMutex(YPersistenceManager pmgr)
            throws YPersistenceException {
        String holder = _mutexLock.getHolder();
        _mutexLock.release(holder);

        if (pmgr != null) {
            pmgr.updateObjectExternal(_mutexLock);
        }
    }

    /**
     * Called when a task completes.
     *
     * @param taskId the task identifier
     * @param pmgr the persistence manager
     * @throws YPersistenceException if persistence fails
     */
    public void onTaskComplete(String taskId, YPersistenceManager pmgr)
            throws YPersistenceException {
        // Mark task as completed
        _interleavedSet.markCompleted(taskId);
        releaseMutex(pmgr);

        // Check if all complete
        if (_interleavedSet.isAllComplete()) {
            _routerState.setPhase(YInterleavedState.RouterPhase.ALL_COMPLETE);
            _routerState.setExecutingTaskId(null);
        } else {
            // Start next task
            startNextTask(pmgr);
        }

        if (pmgr != null) {
            pmgr.updateObjectExternal(_interleavedSet);
            pmgr.updateObjectExternal(_routerState);
        }
    }

    /**
     * Adds a task to the interleaved set.
     *
     * @param task the task to add
     */
    public void addInterleavedTask(YTask task) {
        _interleavedSet.addTask(task);
    }

    /**
     * Removes a task from the interleaved set.
     *
     * @param taskId the task identifier
     */
    public void removeInterleavedTask(String taskId) {
        _interleavedSet.removeTask(taskId);
    }

    /**
     * Gets the interleaved tasks.
     *
     * @return the set of interleaved tasks
     */
    public Set<YTask> getInterleavedTasks() {
        return _interleavedSet.getAllTasks();
    }

    /**
     * Gets the tasks waiting for execution.
     *
     * @return the waiting tasks
     */
    public Set<YTask> getEnabledWaitingTasks() {
        return _interleavedSet.getWaitingTasks();
    }

    /**
     * Gets the currently executing task.
     *
     * @return the executing task, or null
     */
    public YTask getExecutingTask() {
        return _interleavedSet.getExecutingTask();
    }

    /**
     * Gets the router state.
     *
     * @return the router state
     */
    public YInterleavedState getRouterState() {
        return _routerState;
    }

    /**
     * Checks if the mutex is held.
     *
     * @return true if held
     */
    public boolean isMutexHeld() {
        return _mutexLock.isLocked();
    }

    /**
     * Gets the current mutex holder.
     *
     * @return the holder task ID, or null
     */
    public String getMutexHolder() {
        return _mutexLock.getHolder();
    }

    /**
     * Gets the completed task count.
     *
     * @return the count
     */
    public int getCompletedCount() {
        return _interleavedSet.getCompletedCount();
    }

    /**
     * Gets the total task count.
     *
     * @return the count
     */
    public int getTotalCount() {
        return _interleavedSet.getTotalCount();
    }

    /**
     * Checks if all tasks are complete.
     *
     * @return true if all complete
     */
    public boolean isAllComplete() {
        return _interleavedSet.isAllComplete();
    }

    /**
     * Sets the selection strategy.
     *
     * @param strategy the strategy
     */
    public void setSelectionStrategy(InterleavedSelectionStrategy strategy) {
        this._selectionStrategy = strategy;
    }

    /**
     * Selects the next task to execute based on strategy.
     *
     * @return the selected task, or null
     */
    public YTask selectNextTask() {
        Set<YTask> waiting = _interleavedSet.getWaitingTasks();
        return _selectionStrategy.select(waiting, _routerState);
    }

    /**
     * Starts the next waiting task.
     *
     * @param pmgr the persistence manager
     * @throws YPersistenceException if persistence fails
     */
    private void startNextTask(YPersistenceManager pmgr) throws YPersistenceException {
        YTask next = selectNextTask();
        if (next != null) {
            tryAcquireMutex(next.getID(), pmgr);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean t_enabled(YIdentifier id) {
        if (_i != null) {
            return false;
        }

        // Check preset for tokens
        for (YExternalNetElement condition : getPresetElements()) {
            if (((org.yawlfoundation.yawl.elements.YConditionInterface) condition)
                    .containsIdentifier()) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized List<YIdentifier> t_fire(YPersistenceManager pmgr)
            throws YStateException, YDataStateException, YQueryException,
                   YPersistenceException {

        if (!t_enabled(getI())) {
            throw new YStateException(this + " cannot fire due to not being enabled");
        }

        // Consume tokens from preset
        for (YExternalNetElement condition : getPresetElements()) {
            org.yawlfoundation.yawl.elements.YConditionInterface cond =
                    (org.yawlfoundation.yawl.elements.YConditionInterface) condition;
            if (cond.containsIdentifier()) {
                YIdentifier id = cond.removeOne(pmgr);
                _i = id;
                _i.addLocation(pmgr, this);
                break;
            }
        }

        // Enable all interleaved tasks
        enableAllTasks(pmgr);

        YIdentifier childID = createFiredIdentifier(pmgr);
        return List.of(childID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void startOne(YPersistenceManager pmgr, YIdentifier id)
            throws YPersistenceException {
        this._mi_entered.removeOne(pmgr, id);
        this._mi_executing.add(pmgr, id);
    }
}
