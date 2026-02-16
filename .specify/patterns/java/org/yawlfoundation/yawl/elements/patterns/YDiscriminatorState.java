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

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks the state of a discriminator task.
 *
 * <p>Maintains information about which branches have arrived,
 * whether the discriminator has been activated, and cycle count.</p>
 *
 * @author YAWL Foundation
 * @since 5.3
 */
public class YDiscriminatorState {

    /** Unique identifier for this discriminator */
    private final String _discriminatorId;

    /** Set of branch IDs that have arrived in current cycle */
    private final Set<String> _arrivedBranchIds;

    /** Whether the discriminator has been activated (first token passed) */
    private boolean _activated;

    /** Count of completed cycles */
    private int _cycleCount;

    /** For discriminator with memory: tracks activations per branch */
    private final Set<String> _activationHistory;

    /**
     * Constructs a new discriminator state.
     *
     * @param discriminatorId the discriminator identifier
     */
    public YDiscriminatorState(String discriminatorId) {
        this._discriminatorId = discriminatorId;
        this._arrivedBranchIds = new HashSet<>();
        this._activated = false;
        this._cycleCount = 0;
        this._activationHistory = new HashSet<>();
    }

    /**
     * Records an arrival from a branch.
     *
     * @param branchId the branch identifier
     */
    public void recordArrival(String branchId) {
        _arrivedBranchIds.add(branchId);
    }

    /**
     * Checks if a specific branch has arrived.
     *
     * @param branchId the branch identifier
     * @return true if branch has arrived
     */
    public boolean hasArrived(String branchId) {
        return _arrivedBranchIds.contains(branchId);
    }

    /**
     * Gets the count of arrived branches.
     *
     * @return the arrival count
     */
    public int getArrivalCount() {
        return _arrivedBranchIds.size();
    }

    /**
     * Checks if the discriminator is activated.
     *
     * @return true if activated
     */
    public boolean isActivated() {
        return _activated;
    }

    /**
     * Sets the activated state.
     *
     * @param activated the activated state
     */
    public void setActivated(boolean activated) {
        this._activated = activated;
        if (activated) {
            _cycleCount++;
        }
    }

    /**
     * Gets the cycle count.
     *
     * @return the number of completed activations
     */
    public int getCycleCount() {
        return _cycleCount;
    }

    /**
     * Gets the set of arrived branch IDs.
     *
     * @return copy of arrived branch IDs
     */
    public Set<String> getArrivedBranchIds() {
        return new HashSet<>(_arrivedBranchIds);
    }

    /**
     * Resets the discriminator state for a new cycle.
     */
    public void reset() {
        _arrivedBranchIds.clear();
        _activated = false;
    }

    /**
     * For discriminator with memory: records which branch activated.
     *
     * @param branchId the activating branch
     */
    public void recordActivation(String branchId) {
        _activationHistory.add(branchId);
    }

    /**
     * For discriminator with memory: checks if branch has activated before.
     *
     * @param branchId the branch identifier
     * @return true if branch has activated in a previous cycle
     */
    public boolean hasActivatedBefore(String branchId) {
        return _activationHistory.contains(branchId);
    }

    /**
     * Gets the discriminator identifier.
     *
     * @return the discriminator ID
     */
    public String getDiscriminatorId() {
        return _discriminatorId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "YDiscriminatorState{" +
                "discriminatorId='" + _discriminatorId + '\'' +
                ", arrivedBranchIds=" + _arrivedBranchIds +
                ", activated=" + _activated +
                ", cycleCount=" + _cycleCount +
                '}';
    }
}
