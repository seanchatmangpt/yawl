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

import org.yawlfoundation.yawl.engine.YPersistenceManager;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;

import java.util.*;

/**
 * Registry for managing active event listeners.
 *
 * <p>The registry tracks all active listeners and dispatches events
 * to the appropriate listeners based on matching logic.</p>
 *
 * @author YAWL Foundation
 * @since 5.3
 */
public class YEventRegistry {

    /** Active listeners by ID */
    private final Map<String, YEventListener> _activeListeners;

    /** Listeners indexed by correlation key */
    private final Map<String, List<YEventListener>> _listenersByCorrelationKey;

    /**
     * Constructs a new event registry.
     */
    public YEventRegistry() {
        _activeListeners = new HashMap<>();
        _listenersByCorrelationKey = new HashMap<>();
    }

    /**
     * Registers a listener.
     *
     * @param listener the listener to register
     */
    public void registerListener(YEventListener listener) {
        _activeListeners.put(listener.getListenerId(), listener);

        String correlationKey = listener.getCorrelationKey();
        if (correlationKey != null) {
            _listenersByCorrelationKey
                    .computeIfAbsent(correlationKey, k -> new ArrayList<>())
                    .add(listener);
        }
    }

    /**
     * Unregisters a listener.
     *
     * @param listenerId the listener ID
     */
    public void unregisterListener(String listenerId) {
        YEventListener listener = _activeListeners.remove(listenerId);
        if (listener != null && listener.getCorrelationKey() != null) {
            List<YEventListener> list = _listenersByCorrelationKey.get(listener.getCorrelationKey());
            if (list != null) {
                list.remove(listener);
            }
        }
    }

    /**
     * Gets a listener by ID.
     *
     * @param listenerId the listener ID
     * @return the listener, or null
     */
    public YEventListener getListener(String listenerId) {
        return _activeListeners.get(listenerId);
    }

    /**
     * Dispatches an event to matching listeners.
     *
     * @param event the event to dispatch
     * @throws YPersistenceException if persistence fails
     */
    public void dispatchEvent(YEvent event) throws YPersistenceException {
        List<YEventListener> matchingListeners = findMatchingListeners(event);

        for (YEventListener listener : matchingListeners) {
            if (listener.getState() == YEventListener.ListenerState.REGISTERED) {
                listener.onEvent(event.getData());
            }
        }
    }

    /**
     * Finds all listeners matching the event.
     *
     * @param event the event
     * @return the matching listeners
     */
    public List<YEventListener> findMatchingListeners(YEvent event) {
        List<YEventListener> matching = new ArrayList<>();

        // First check by correlation key if present
        if (event.getCorrelationKey() != null) {
            List<YEventListener> byKey = _listenersByCorrelationKey.get(event.getCorrelationKey());
            if (byKey != null) {
                for (YEventListener listener : byKey) {
                    if (listener.matches(event)) {
                        matching.add(listener);
                    }
                }
            }
        }

        // Also check all active listeners
        for (YEventListener listener : _activeListeners.values()) {
            if (!matching.contains(listener) && listener.matches(event)) {
                matching.add(listener);
            }
        }

        return matching;
    }

    /**
     * Cancels all active listeners.
     *
     * @param pmgr the persistence manager
     * @throws YPersistenceException if persistence fails
     */
    public void cancelAllListeners(YPersistenceManager pmgr) throws YPersistenceException {
        for (YEventListener listener : new ArrayList<>(_activeListeners.values())) {
            listener.unregister(pmgr);
            listener.setState(YEventListener.ListenerState.CANCELLED);
        }
        _activeListeners.clear();
        _listenersByCorrelationKey.clear();
    }

    /**
     * Gets all active listeners.
     *
     * @return the active listeners
     */
    public List<YEventListener> getActiveListeners() {
        return new ArrayList<>(_activeListeners.values());
    }

    /**
     * Gets the count of active listeners.
     *
     * @return the count
     */
    public int getActiveListenerCount() {
        return _activeListeners.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "YEventRegistry{" +
                "activeListeners=" + _activeListeners.size() +
                '}';
    }
}
