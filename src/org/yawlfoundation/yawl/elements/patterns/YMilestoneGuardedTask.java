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

import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.YPersistenceManager;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implements a task guarded by milestone conditions (WCP-18).
 *
 * <p>A milestone-guarded task can only execute when its milestone
 * guards are in the REACHED state. Multiple guards can be combined
 * with AND, OR, or XOR operators.</p>
 *
 * <p>This class extends YAtomicTask to provide milestone guard
 * enforcement. For composite tasks, use delegation or wrapper patterns.</p>
 *
 * @author YAWL Foundation
 * @since 5.3
 * @see YMilestoneCondition
 * @see MilestoneGuardOperator
 */
public class YMilestoneGuardedTask extends YAtomicTask {

    /** Set of milestone conditions guarding this task */
    private final Set<YMilestoneCondition> _milestoneGuards;

    /** How multiple guards are combined */
    private MilestoneGuardOperator _guardOperator;

    /** Cache of milestone states for quick evaluation */
    private final Map<String, Boolean> _milestoneStateCache;

    /**
     * Constructs a new milestone-guarded task.
     *
     * @param id the task identifier
     * @param joinType the join type
     * @param splitType the split type
     * @param container the containing net
     */
    public YMilestoneGuardedTask(String id, int joinType, int splitType, YNet container) {
        super(id, joinType, splitType, container);
        this._milestoneGuards = new HashSet<>();
        this._guardOperator = MilestoneGuardOperator.AND;
        this._milestoneStateCache = new HashMap<>();
    }

    /**
     * Adds a milestone guard to this task.
     *
     * @param milestone the milestone condition
     * @throws IllegalArgumentException if milestone is null
     */
    public void addMilestoneGuard(YMilestoneCondition milestone) {
        if (milestone == null) {
            throw new IllegalArgumentException("Milestone condition cannot be null");
        }
        _milestoneGuards.add(milestone);
        _milestoneStateCache.put(milestone.getID(), milestone.isReached());
    }

    /**
     * Removes a milestone guard from this task.
     *
     * @param milestoneId the milestone identifier
     */
    public void removeMilestoneGuard(String milestoneId) {
        _milestoneGuards.removeIf(m -> m.getID().equals(milestoneId));
        _milestoneStateCache.remove(milestoneId);
    }

    /**
     * Gets the milestone guards.
     *
     * @return an unmodifiable view of the set of milestone conditions
     */
    public Set<YMilestoneCondition> getMilestoneGuards() {
        return Collections.unmodifiableSet(_milestoneGuards);
    }

    /**
     * Checks if all milestone guards are reached.
     *
     * @return true if all guards are reached
     */
    public boolean areAllMilestonesReached() {
        if (_milestoneGuards.isEmpty()) {
            return true;
        }
        for (YMilestoneCondition milestone : _milestoneGuards) {
            if (!milestone.isReached()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if any milestone guard is reached.
     *
     * @return true if any guard is reached
     */
    public boolean isAnyMilestoneReached() {
        for (YMilestoneCondition milestone : _milestoneGuards) {
            if (milestone.isReached()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the task can execute based on guard operator.
     *
     * @return true if guard conditions are satisfied
     */
    public boolean canExecute() {
        // If no guards defined, task can execute
        if (_milestoneGuards.isEmpty()) {
            return true;
        }

        updateMilestoneCache();

        Set<Boolean> states = new HashSet<>(_milestoneStateCache.values());
        return _guardOperator.evaluate(states);
    }

    /**
     * Gets the guard operator.
     *
     * @return the guard operator
     */
    public MilestoneGuardOperator getGuardOperator() {
        return _guardOperator;
    }

    /**
     * Sets the guard operator.
     *
     * @param operator the guard operator
     * @throws IllegalArgumentException if operator is null
     */
    public void setGuardOperator(MilestoneGuardOperator operator) {
        if (operator == null) {
            throw new IllegalArgumentException(
                "Guard operator cannot be null. Must be one of: AND, OR, XOR");
        }
        this._guardOperator = operator;
    }

    /**
     * Called when a milestone is reached.
     *
     * @param milestoneId the milestone identifier
     */
    public synchronized void onMilestoneReached(String milestoneId) {
        if (milestoneId != null) {
            _milestoneStateCache.put(milestoneId, true);
        }
    }

    /**
     * Called when a milestone is expired.
     *
     * @param milestoneId the milestone identifier
     */
    public synchronized void onMilestoneExpired(String milestoneId) {
        if (milestoneId != null) {
            _milestoneStateCache.put(milestoneId, false);
        }
    }

    /**
     * Updates the milestone state cache from the actual milestone states.
     */
    private synchronized void updateMilestoneCache() {
        for (YMilestoneCondition milestone : _milestoneGuards) {
            _milestoneStateCache.put(milestone.getID(), milestone.isReached());
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>For milestone-guarded task: enabled only when guard conditions
     * are satisfied AND normal task enablement conditions are met.</p>
     */
    @Override
    public synchronized boolean t_enabled(YIdentifier id) {
        // First check normal enablement
        if (!super.t_enabled(id)) {
            return false;
        }

        // Then check milestone guards
        return canExecute();
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
