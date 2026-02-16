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

import java.util.Date;

/**
 * Tracks the state of a deferred choice.
 *
 * @author YAWL Foundation
 * @since 5.3
 */
public class YDeferredChoiceState {

    /**
     * Enumeration of choice phases.
     */
    public enum ChoicePhase {
        /** Not waiting for events */
        IDLE,
        /** Waiting for one of multiple events */
        WAITING,
        /** An event has occurred */
        TRIGGERED,
        /** Processing the triggered event */
        PROCESSING,
        /** Event processed, path chosen */
        COMPLETED
    }

    /** Choice identifier */
    private final String _choiceId;

    /** Current phase */
    private ChoicePhase _phase;

    /** ID of the listener that triggered */
    private String _triggeredListenerId;

    /** Event data from the trigger */
    private Object _eventData;

    /** When waiting started */
    private Date _waitingSince;

    /** When event was triggered */
    private Date _triggeredAt;

    /**
     * Constructs a new deferred choice state.
     *
     * @param choiceId the choice identifier
     */
    public YDeferredChoiceState(String choiceId) {
        this._choiceId = choiceId;
        this._phase = ChoicePhase.IDLE;
    }

    /**
     * Gets the choice identifier.
     *
     * @return the choice ID
     */
    public String getChoiceId() {
        return _choiceId;
    }

    /**
     * Gets the current phase.
     *
     * @return the phase
     */
    public ChoicePhase getPhase() {
        return _phase;
    }

    /**
     * Starts waiting for events.
     */
    public void startWaiting() {
        _phase = ChoicePhase.WAITING;
        _waitingSince = new Date();
    }

    /**
     * Triggers the choice with an event.
     *
     * @param listenerId the triggering listener
     * @param eventData the event data
     */
    public void trigger(String listenerId, Object eventData) {
        _phase = ChoicePhase.TRIGGERED;
        _triggeredListenerId = listenerId;
        _eventData = eventData;
        _triggeredAt = new Date();
    }

    /**
     * Transitions to processing phase.
     */
    public void processing() {
        _phase = ChoicePhase.PROCESSING;
    }

    /**
     * Marks the choice as completed.
     */
    public void complete() {
        _phase = ChoicePhase.COMPLETED;
    }

    /**
     * Checks if waiting for events.
     *
     * @return true if waiting
     */
    public boolean isWaiting() {
        return _phase == ChoicePhase.WAITING;
    }

    /**
     * Checks if an event has been triggered.
     *
     * @return true if triggered
     */
    public boolean isTriggered() {
        return _phase == ChoicePhase.TRIGGERED || _phase == ChoicePhase.PROCESSING;
    }

    /**
     * Gets the triggered listener ID.
     *
     * @return the listener ID
     */
    public String getTriggeredListenerId() {
        return _triggeredListenerId;
    }

    /**
     * Gets the event data.
     *
     * @return the event data
     */
    public Object getEventData() {
        return _eventData;
    }

    /**
     * Gets the waiting duration.
     *
     * @return the duration in milliseconds
     */
    public long getWaitingDuration() {
        if (_waitingSince == null) {
            return 0;
        }
        Date end = _triggeredAt != null ? _triggeredAt : new Date();
        return end.getTime() - _waitingSince.getTime();
    }

    /**
     * Gets when waiting started.
     *
     * @return the waiting start time
     */
    public Date getWaitingSince() {
        return _waitingSince;
    }

    /**
     * Gets when event was triggered.
     *
     * @return the trigger time
     */
    public Date getTriggeredAt() {
        return _triggeredAt;
    }

    /**
     * Resets the state.
     */
    public void reset() {
        _phase = ChoicePhase.IDLE;
        _triggeredListenerId = null;
        _eventData = null;
        _waitingSince = null;
        _triggeredAt = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "YDeferredChoiceState{" +
                "choiceId='" + _choiceId + '\'' +
                ", phase=" + _phase +
                ", triggeredListenerId='" + _triggeredListenerId + '\'' +
                '}';
    }
}
