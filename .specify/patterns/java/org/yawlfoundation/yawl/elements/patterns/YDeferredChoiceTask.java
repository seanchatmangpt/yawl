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
import org.yawlfoundation.yawl.elements.YFlow;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.YPersistenceManager;
import org.yawlfoundation.yawl.exceptions.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements the Event-Based Deferred Choice pattern (WCP-16).
 *
 * <p>Allows the workflow to wait for one of several possible events
 * to occur. The choice of which path to take is deferred until one
 * of the events actually happens.</p>
 *
 * <p>State Machine:</p>
 * <ul>
 *   <li>IDLE: Not waiting for events</li>
 *   <li>WAITING: Waiting for one of multiple events</li>
 *   <li>TRIGGERED: An event has occurred</li>
 *   <li>PROCESSING: Processing the triggered event</li>
 *   <li>COMPLETED: Event processed, path chosen</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @since 5.3
 * @see YEventListener
 * @see YEventRegistry
 */
public class YDeferredChoiceTask extends YTask {

    /** Join type constant for deferred choice */
    public static final int _DEFERRED_CHOICE_JOIN = 80;

    /** Split type constant for deferred choice */
    public static final int _DEFERRED_CHOICE_SPLIT = 81;

    /** Current choice state */
    private YDeferredChoiceState _choiceState;

    /** Registry of active event listeners */
    private YEventRegistry _eventRegistry;

    /** List of registered event listeners */
    private List<YEventListener> _listeners;

    /** The flow that was triggered by the winning event */
    private YFlow _triggeredFlow;

    /** Data from the triggering event */
    private Object _eventData;

    /**
     * Constructs a new deferred choice task.
     *
     * @param id the task identifier
     * @param joinType the join type
     * @param splitType the split type
     * @param container the containing net
     */
    public YDeferredChoiceTask(String id, int joinType, int splitType, YNet container) {
        super(id, joinType, splitType, container);
        _choiceState = new YDeferredChoiceState(id);
        _eventRegistry = new YEventRegistry();
        _listeners = new ArrayList<>();
    }

    /**
     * Registers all event listeners.
     *
     * @param pmgr the persistence manager
     * @throws YPersistenceException if persistence fails
     */
    public void registerListeners(YPersistenceManager pmgr)
            throws YPersistenceException {
        _choiceState.startWaiting();

        for (YEventListener listener : _listeners) {
            listener.register(pmgr);
            _eventRegistry.registerListener(listener);
        }

        if (pmgr != null) {
            pmgr.updateObjectExternal(_choiceState);
        }
    }

    /**
     * Unregisters all event listeners.
     *
     * @param pmgr the persistence manager
     * @throws YPersistenceException if persistence fails
     */
    public void unregisterListeners(YPersistenceManager pmgr)
            throws YPersistenceException {
        _eventRegistry.cancelAllListeners(pmgr);

        for (YEventListener listener : _listeners) {
            listener.unregister(pmgr);
        }
    }

    /**
     * Called when an event is received.
     *
     * @param listenerId the listener that received the event
     * @param eventData the event data
     * @throws YPersistenceException if persistence fails
     */
    public void onEventReceived(String listenerId, Object eventData)
            throws YPersistenceException {
        if (_choiceState.isTriggered()) {
            // Already triggered, ignore this event
            return;
        }

        // Find the triggered listener
        YEventListener triggeredListener = null;
        for (YEventListener listener : _listeners) {
            if (listener.getListenerId().equals(listenerId)) {
                triggeredListener = listener;
                break;
            }
        }

        if (triggeredListener != null) {
            _choiceState.trigger(listenerId, eventData);
            _triggeredFlow = triggeredListener.getTargetFlow();
            _eventData = eventData;

            // Cancel other listeners
            for (YEventListener listener : _listeners) {
                if (!listener.getListenerId().equals(listenerId)) {
                    listener.setState(YEventListener.ListenerState.CANCELLED);
                }
            }
        }
    }

    /**
     * Consumes the triggered event and proceeds with the chosen path.
     *
     * @param pmgr the persistence manager
     * @throws YPersistenceException if persistence fails
     * @throws YStateException if no event has been triggered
     */
    public void consumeEvent(YPersistenceManager pmgr)
            throws YPersistenceException, YStateException {
        if (!_choiceState.isTriggered()) {
            throw new YStateException("No event has been triggered");
        }

        _choiceState.processing();
        unregisterListeners(pmgr);

        if (pmgr != null) {
            pmgr.updateObjectExternal(_choiceState);
        }
    }

    /**
     * Adds an event listener.
     *
     * @param listener the listener to add
     */
    public void addListener(YEventListener listener) {
        _listeners.add(listener);
    }

    /**
     * Removes an event listener.
     *
     * @param listenerId the listener identifier
     */
    public void removeListener(String listenerId) {
        _listeners.removeIf(l -> l.getListenerId().equals(listenerId));
    }

    /**
     * Gets the list of listeners.
     *
     * @return the listeners
     */
    public List<YEventListener> getListeners() {
        return new ArrayList<>(_listeners);
    }

    /**
     * Gets the triggered listener.
     *
     * @return the triggered listener, or null
     */
    public YEventListener getTriggeredListener() {
        for (YEventListener listener : _listeners) {
            if (listener.getState() == YEventListener.ListenerState.TRIGGERED) {
                return listener;
            }
        }
        return null;
    }

    /**
     * Gets the triggered flow.
     *
     * @return the triggered flow
     */
    public YFlow getTriggeredFlow() {
        return _triggeredFlow;
    }

    /**
     * Gets the choice state.
     *
     * @return the choice state
     */
    public YDeferredChoiceState getChoiceState() {
        return _choiceState;
    }

    /**
     * Checks if waiting for events.
     *
     * @return true if waiting
     */
    public boolean isWaiting() {
        return _choiceState.isWaiting();
    }

    /**
     * Checks if an event has been triggered.
     *
     * @return true if triggered
     */
    public boolean isTriggered() {
        return _choiceState.isTriggered();
    }

    /**
     * Gets the winning listener ID.
     *
     * @return the listener ID
     */
    public String getWinningListenerId() {
        return _choiceState.getTriggeredListenerId();
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
     *
     * <p>For deferred choice: enabled when any preset has a token.</p>
     */
    @Override
    public synchronized boolean t_enabled(YIdentifier id) {
        if (_i != null) {
            return false;
        }

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
     *
     * <p>For deferred choice: registers listeners and waits.</p>
     */
    @Override
    public synchronized List<YIdentifier> t_fire(YPersistenceManager pmgr)
            throws YStateException, YDataStateException, YQueryException,
                   YPersistenceException {

        if (!t_enabled(getI())) {
            throw new YStateException(this + " cannot fire due to not being enabled");
        }

        // Consume token from preset
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

        // Register listeners and wait for events
        registerListeners(pmgr);

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

    /**
     * {@inheritDoc}
     *
     * <p>Cancels all listeners when task is cancelled.</p>
     */
    @Override
    public synchronized void cancel(YPersistenceManager pmgr)
            throws YPersistenceException {
        unregisterListeners(pmgr);
        super.cancel(pmgr);
    }
}
