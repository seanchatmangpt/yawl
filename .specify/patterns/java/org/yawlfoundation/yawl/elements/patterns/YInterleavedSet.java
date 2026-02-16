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

import org.yawlfoundation.yawl.elements.YTask;

import java.util.*;

/**
 * Represents the set of tasks in an interleaved parallel routing.
 *
 * <p>Tracks the state of each task and manages the waiting queue
 * for tasks that are enabled but not yet executing.</p>
 *
 * @author YAWL Foundation
 * @since 5.3
 */
public class YInterleavedSet {

    /**
     * Enumeration of task states within an interleaved set.
     */
    public enum TaskState {
        /** Not yet activated by router */
        NOT_ENABLED,
        /** Enabled but waiting for mutex */
        ENABLED_WAITING,
        /** Currently executing (holds mutex) */
        ENABLED_EXECUTING,
        /** Finished execution */
        COMPLETED
    }

    /** Identifier for this interleaved set */
    private final String _setId;

    /** All tasks in the interleaved set */
    private final Set<YTask> _tasks;

    /** State of each task */
    private final Map<String, TaskState> _taskStates;

    /** FIFO queue for waiting tasks */
    private final Queue<String> _waitingQueue;

    /**
     * Constructs a new interleaved set.
     *
     * @param setId the set identifier
     */
    public YInterleavedSet(String setId) {
        this._setId = setId;
        this._tasks = new HashSet<>();
        this._taskStates = new HashMap<>();
        this._waitingQueue = new LinkedList<>();
    }

    /**
     * Adds a task to the interleaved set.
     *
     * @param task the task to add
     */
    public void addTask(YTask task) {
        _tasks.add(task);
        _taskStates.put(task.getID(), TaskState.NOT_ENABLED);
    }

    /**
     * Removes a task from the interleaved set.
     *
     * @param taskId the task identifier
     */
    public void removeTask(String taskId) {
        _tasks.removeIf(t -> t.getID().equals(taskId));
        _taskStates.remove(taskId);
        _waitingQueue.remove(taskId);
    }

    /**
     * Checks if a task is in the set.
     *
     * @param taskId the task identifier
     * @return true if contained
     */
    public boolean containsTask(String taskId) {
        return _taskStates.containsKey(taskId);
    }

    /**
     * Sets the state of a task.
     *
     * @param taskId the task identifier
     * @param state the new state
     */
    public void setTaskState(String taskId, TaskState state) {
        if (_taskStates.containsKey(taskId)) {
            TaskState oldState = _taskStates.get(taskId);
            _taskStates.put(taskId, state);

            // Update queue based on state change
            if (state == TaskState.ENABLED_WAITING && oldState != TaskState.ENABLED_WAITING) {
                enqueue(taskId);
            } else if (state != TaskState.ENABLED_WAITING && oldState == TaskState.ENABLED_WAITING) {
                _waitingQueue.remove(taskId);
            }
        }
    }

    /**
     * Gets the state of a task.
     *
     * @param taskId the task identifier
     * @return the task state
     */
    public TaskState getTaskState(String taskId) {
        return _taskStates.get(taskId);
    }

    /**
     * Marks a task as completed.
     *
     * @param taskId the task identifier
     */
    public void markCompleted(String taskId) {
        setTaskState(taskId, TaskState.COMPLETED);
    }

    /**
     * Enables all tasks in the set.
     */
    public void enableAll() {
        for (YTask task : _tasks) {
            setTaskState(task.getID(), TaskState.ENABLED_WAITING);
        }
    }

    /**
     * Enqueues a task for execution.
     *
     * @param taskId the task identifier
     */
    public void enqueue(String taskId) {
        if (!_waitingQueue.contains(taskId)) {
            _waitingQueue.add(taskId);
        }
    }

    /**
     * Dequeues the next task for execution.
     *
     * @return the task identifier, or null if empty
     */
    public String dequeue() {
        return _waitingQueue.poll();
    }

    /**
     * Gets the queue size.
     *
     * @return the number of waiting tasks
     */
    public int getQueueSize() {
        return _waitingQueue.size();
    }

    /**
     * Checks if the queue is empty.
     *
     * @return true if empty
     */
    public boolean isQueueEmpty() {
        return _waitingQueue.isEmpty();
    }

    /**
     * Gets all tasks in the set.
     *
     * @return the set of all tasks
     */
    public Set<YTask> getAllTasks() {
        return new HashSet<>(_tasks);
    }

    /**
     * Gets the tasks waiting for execution.
     *
     * @return the set of waiting tasks
     */
    public Set<YTask> getWaitingTasks() {
        Set<YTask> waiting = new HashSet<>();
        for (YTask task : _tasks) {
            if (_taskStates.get(task.getID()) == TaskState.ENABLED_WAITING) {
                waiting.add(task);
            }
        }
        return waiting;
    }

    /**
     * Gets the completed tasks.
     *
     * @return the set of completed tasks
     */
    public Set<YTask> getCompletedTasks() {
        Set<YTask> completed = new HashSet<>();
        for (YTask task : _tasks) {
            if (_taskStates.get(task.getID()) == TaskState.COMPLETED) {
                completed.add(task);
            }
        }
        return completed;
    }

    /**
     * Gets the currently executing task.
     *
     * @return the executing task, or null
     */
    public YTask getExecutingTask() {
        for (YTask task : _tasks) {
            if (_taskStates.get(task.getID()) == TaskState.ENABLED_EXECUTING) {
                return task;
            }
        }
        return null;
    }

    /**
     * Gets the completed task count.
     *
     * @return the count
     */
    public int getCompletedCount() {
        int count = 0;
        for (TaskState state : _taskStates.values()) {
            if (state == TaskState.COMPLETED) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gets the total task count.
     *
     * @return the count
     */
    public int getTotalCount() {
        return _tasks.size();
    }

    /**
     * Checks if all tasks are complete.
     *
     * @return true if all complete
     */
    public boolean isAllComplete() {
        return getCompletedCount() == getTotalCount();
    }

    /**
     * Resets the interleaved set.
     */
    public void reset() {
        for (String taskId : _taskStates.keySet()) {
            _taskStates.put(taskId, TaskState.NOT_ENABLED);
        }
        _waitingQueue.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "YInterleavedSet{" +
                "setId='" + _setId + '\'' +
                ", tasks=" + _tasks.size() +
                ", completed=" + getCompletedCount() +
                '}';
    }
}
