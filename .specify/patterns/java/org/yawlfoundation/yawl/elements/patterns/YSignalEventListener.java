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
 * Event listener for signal-based events.
 *
 * <p>Listens for signals from other processes or external sources.</p>
 *
 * @author YAWL Foundation
 * @since 5.3
 */
public class YSignalEventListener extends YEventListener {

    /** Signal name to listen for */
    private final String _signalName;

    /** Optional source net ID filter */
    private String _sourceNetId;

    /** Reference to the deferred choice task */
    private YDeferredChoiceTask _task;

    /**
     * Constructs a new signal event listener.
     *
     * @param listenerId the listener identifier
     * @param signalName the signal name
     * @param targetFlow the flow to trigger
     */
    public YSignalEventListener(String listenerId, String signalName, YFlow targetFlow) {
        super(listenerId, targetFlow);
        this._signalName = signalName;
    }

    /**
     * Sets the task reference for callback.
     *
     * @param task the deferred choice task
     */
    public void setTask(YDeferredChoiceTask task) {
        this._task = task;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void register(YPersistenceManager pmgr) throws YPersistenceException {
        // Register with signal manager
        // In actual implementation, this would subscribe to signal bus
        _state = ListenerState.REGISTERED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unregister(YPersistenceManager pmgr) throws YPersistenceException {
        // Unregister from signal manager
        _state = ListenerState.CANCELLED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(YEvent event) {
        if (event.getType() != YEvent.YEventType.SIGNAL) {
            return false;
        }
        if (!_signalName.equals(event.getSignalName())) {
            return false;
        }
        // Optionally filter by source net
        return true;
    }

    /**
     * Gets the signal name.
     *
     * @return the signal name
     */
    public String getSignalName() {
        return _signalName;
    }

    /**
     * Gets the source net ID filter.
     *
     * @return the source net ID
     */
    public String getSourceNetId() {
        return _sourceNetId;
    }

    /**
     * Sets the source net ID filter.
     *
     * @param sourceNetId the source net ID
     */
    public void setSourceNetId(String sourceNetId) {
        this._sourceNetId = sourceNetId;
    }
}
