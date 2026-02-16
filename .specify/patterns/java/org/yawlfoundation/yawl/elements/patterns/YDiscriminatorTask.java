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
 * Implements the Structured Discriminator pattern (WCP-9).
 *
 * <p>The first incoming token activates the downstream flow, while
 * subsequent tokens are consumed without effect. After all incoming
 * branches have completed, the discriminator resets for the next cycle.</p>
 *
 * <p>State Machine:</p>
 * <ul>
 *   <li>WAITING: No tokens received yet</li>
 *   <li>ACTIVATED: First token received, downstream activated</li>
 *   <li>CONSUMING: Subsequent tokens being consumed</li>
 *   <li>RESET_READY: All branches completed, ready to reset</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @since 5.3
 * @see YDiscriminatorState
 */
public class YDiscriminatorTask extends YTask {

    /** Join type constant for structured discriminator */
    public static final int _DISCRIMINATOR = 64;

    /** Join type constant for N-out-of-M discriminator */
    public static final int _DISCRIMINATOR_N_OUT_OF_M = 65;

    /** Join type constant for discriminator with memory */
    public static final int _DISCRIMINATOR_WITH_MEMORY = 66;

    /** Discriminator state tracking */
    private YDiscriminatorState _discriminatorState;

    /** Number of incoming branches (required for reset detection) */
    private int _requiredBranches;

    /** N value for N-out-of-M variant */
    private int _nValue;

    /**
     * Constructs a new discriminator task.
     *
     * @param id the task identifier
     * @param joinType the join type (should be _DISCRIMINATOR or variant)
     * @param splitType the split type
     * @param container the containing net
     */
    public YDiscriminatorTask(String id, int joinType, int splitType, YNet container) {
        super(id, joinType, splitType, container);
        _discriminatorState = new YDiscriminatorState(id);
        _requiredBranches = 0;
        _nValue = 1;
    }

    /**
     * Checks if this is the first token to arrive.
     *
     * @return true if no tokens have activated yet
     */
    public boolean isFirstToken() {
        return !_discriminatorState.isActivated();
    }

    /**
     * Consumes subsequent tokens after the first has activated.
     *
     * @param pmgr the persistence manager
     * @throws YPersistenceException if persistence fails
     */
    public void consumeSubsequentTokens(YPersistenceManager pmgr)
            throws YPersistenceException {
        // Tokens are consumed without triggering downstream
        // This is handled by the t_fire method
    }

    /**
     * Checks if all branches have arrived and discriminator is ready to reset.
     *
     * @return true if ready to reset
     */
    public boolean isReadyToReset() {
        return _discriminatorState.getArrivalCount() >= _requiredBranches;
    }

    /**
     * Resets the discriminator for the next cycle.
     *
     * @param pmgr the persistence manager
     * @throws YPersistenceException if persistence fails
     */
    public void reset(YPersistenceManager pmgr)
            throws YPersistenceException {
        _discriminatorState.reset();
        if (pmgr != null) {
            pmgr.updateObjectExternal(_discriminatorState);
        }
    }

    /**
     * Gets the set of branches that have arrived.
     *
     * @return set of arrived branch IDs
     */
    public Set<String> getArrivedBranches() {
        return _discriminatorState.getArrivedBranchIds();
    }

    /**
     * Gets the count of arrived branches.
     *
     * @return the arrival count
     */
    public int getArrivedCount() {
        return _discriminatorState.getArrivalCount();
    }

    /**
     * Sets the number of required branches.
     *
     * @param count the branch count
     */
    public void setRequiredBranches(int count) {
        this._requiredBranches = count;
    }

    /**
     * Gets the number of required branches.
     *
     * @return the branch count
     */
    public int getRequiredBranches() {
        return _requiredBranches;
    }

    /**
     * Sets the N value for N-out-of-M variant.
     *
     * @param n the N value
     */
    public void setNValue(int n) {
        this._nValue = n;
    }

    /**
     * Gets the N value for N-out-of-M variant.
     *
     * @return the N value
     */
    public int getNValue() {
        return _nValue;
    }

    /**
     * Checks if this is a structured discriminator.
     *
     * @return true if structured discriminator
     */
    public boolean isStructuredDiscriminator() {
        return getJoinType() == _DISCRIMINATOR;
    }

    /**
     * Checks if this is an N-out-of-M discriminator.
     *
     * @return true if N-out-of-M discriminator
     */
    public boolean isNOutOfMDiscriminator() {
        return getJoinType() == _DISCRIMINATOR_N_OUT_OF_M;
    }

    /**
     * Checks if this is a discriminator with memory.
     *
     * @return true if discriminator with memory
     */
    public boolean isDiscriminatorWithMemory() {
        return getJoinType() == _DISCRIMINATOR_WITH_MEMORY;
    }

    /**
     * {@inheritDoc}
     *
     * <p>For discriminator: enabled when first token arrives.
     * Subsequent tokens do not enable the task.</p>
     */
    @Override
    public synchronized boolean t_enabled(YIdentifier id) {
        if (_i != null) {
            return false; // Busy tasks are never enabled
        }

        // Check if any branch has a token
        for (YExternalNetElement condition : getPresetElements()) {
            if (((org.yawlfoundation.yawl.elements.YConditionInterface) condition)
                    .containsIdentifier()) {
                // First token enables if not yet activated
                if (!_discriminatorState.isActivated()) {
                    return true;
                }
                // For N-out-of-M, enable when Nth token arrives
                if (isNOutOfMDiscriminator()) {
                    return _discriminatorState.getArrivalCount() == _nValue - 1;
                }
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>For discriminator: first token fires and activates downstream,
     * subsequent tokens are consumed.</p>
     */
    @Override
    public synchronized List<YIdentifier> t_fire(YPersistenceManager pmgr)
            throws YStateException, YDataStateException, YQueryException,
                   YPersistenceException {

        YIdentifier id = getI();
        if (!t_enabled(id)) {
            throw new YStateException(this + " cannot fire due to not being enabled");
        }

        _i = id;
        _i.addLocation(pmgr, this);

        // Record which branch arrived
        for (YExternalNetElement condition : getPresetElements()) {
            org.yawlfoundation.yawl.elements.YConditionInterface cond =
                    (org.yawlfoundation.yawl.elements.YConditionInterface) condition;
            if (cond.containsIdentifier()) {
                String branchId = condition.getID();
                _discriminatorState.recordArrival(branchId);

                // First arrival activates
                if (!_discriminatorState.isActivated()) {
                    _discriminatorState.setActivated(true);
                }

                // Consume the token
                cond.removeOne(pmgr);
                break; // Only consume one token per fire
            }
        }

        if (pmgr != null) {
            pmgr.updateObjectExternal(_discriminatorState);
        }

        // Check if ready to reset
        if (isReadyToReset()) {
            reset(pmgr);
        }

        // Create child identifier for downstream
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
