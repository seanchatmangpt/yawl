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

import org.yawlfoundation.yawl.elements.YFlow;
import org.yawlfoundation.yawl.engine.YPersistenceManager;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;

/**
 * Abstract base class for event listeners in the deferred choice pattern.
 *
 * <p>Event listeners wait for specific events and trigger a target flow
 * when the event occurs.</p>
 *
 * @author YAWL Foundation
 * @since 5.3
 */
public abstract class YEventListener {

    /**
     * Enumeration of listener states.
     */
    public enum ListenerState {
        /** Listener registered, waiting for event */
        REGISTERED,
        /** Event received, listener triggered */
        TRIGGERED,
        /** Event consumed by workflow */
        CONSUMED,
        /** Listener cancelled (another event won) */
        CANCELLED
    }

    /** Unique identifier for this listener */
    protected String _listenerId;

    /** Correlation key for event matching */
    protected String _correlationKey;

    /** The flow to trigger when event occurs */
    protected YFlow _targetFlow;

    /** Current listener state */
    protected ListenerState _state;

    /** Event data received */
    protected Object _eventData;

    /**
     * Constructs a new event listener.
     *
     * @param listenerId the listener identifier
     * @param targetFlow the flow to trigger
     */
    protected YEventListener(String listenerId, YFlow targetFlow) {
        this._listenerId = listenerId;
        this._targetFlow = targetFlow;
        this._state = ListenerState.REGISTERED;
    }

    /**
     * Registers the listener to start receiving events.
     *
     * @param pmgr the persistence manager
     * @throws YPersistenceException if persistence fails
     */
    public abstract void register(YPersistenceManager pmgr)
            throws YPersistenceException;

    /**
     * Unregisters the listener to stop receiving events.
     *
     * @param pmgr the persistence manager
     * @throws YPersistenceException if persistence fails
     */
    public abstract void unregister(YPersistenceManager pmgr)
            throws YPersistenceException;

    /**
     * Checks if this listener matches the given event.
     *
     * @param event the event to check
     * @return true if matches
     */
    public abstract boolean matches(YEvent event);

    /**
     * Gets the listener identifier.
     *
     * @return the listener ID
     */
    public String getListenerId() {
        return _listenerId;
    }

    /**
     * Gets the correlation key.
     *
     * @return the correlation key
     */
    public String getCorrelationKey() {
        return _correlationKey;
    }

    /**
     * Sets the correlation key.
     *
     * @param correlationKey the correlation key
     */
    public void setCorrelationKey(String correlationKey) {
        this._correlationKey = correlationKey;
    }

    /**
     * Gets the target flow.
     *
     * @return the target flow
     */
    public YFlow getTargetFlow() {
        return _targetFlow;
    }

    /**
     * Gets the current state.
     *
     * @return the state
     */
    public ListenerState getState() {
        return _state;
    }

    /**
     * Sets the state.
     *
     * @param state the state
     */
    public void setState(ListenerState state) {
        this._state = state;
    }

    /**
     * Called when an event is received.
     *
     * @param eventData the event data
     */
    public void onEvent(Object eventData) {
        this._eventData = eventData;
        this._state = ListenerState.TRIGGERED;
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
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "YEventListener{" +
                "listenerId='" + _listenerId + '\'' +
                ", state=" + _state +
                '}';
    }
}
