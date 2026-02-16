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

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Event listener for timer-based events.
 *
 * <p>Triggers after a specified delay.</p>
 *
 * @author YAWL Foundation
 * @since 5.3
 */
public class YTimerEventListener extends YEventListener {

    /** Delay in milliseconds */
    private final long _delayMs;

    /** When the timer expires */
    private Date _expiresAt;

    /** Java timer object */
    private Timer _timer;

    /** Reference to the deferred choice task */
    private YDeferredChoiceTask _task;

    /**
     * Constructs a new timer event listener.
     *
     * @param listenerId the listener identifier
     * @param delayMs the delay in milliseconds
     * @param targetFlow the flow to trigger
     */
    public YTimerEventListener(String listenerId, long delayMs, YFlow targetFlow) {
        super(listenerId, targetFlow);
        this._delayMs = delayMs;
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
        _expiresAt = new Date(System.currentTimeMillis() + _delayMs);
        _timer = new Timer("TimerListener-" + _listenerId, true);
        _timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (_task != null) {
                    try {
                        _task.onEventReceived(_listenerId, "timeout");
                    } catch (YPersistenceException e) {
                        // Log error
                    }
                }
            }
        }, _delayMs);
        _state = ListenerState.REGISTERED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unregister(YPersistenceManager pmgr) throws YPersistenceException {
        if (_timer != null) {
            _timer.cancel();
            _timer = null;
        }
        _state = ListenerState.CANCELLED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(YEvent event) {
        return event.getType() == YEvent.YEventType.TIMER
                && _listenerId.equals(event.getListenerId());
    }

    /**
     * Gets when the timer expires.
     *
     * @return the expiry time
     */
    public Date getExpiresAt() {
        return _expiresAt;
    }

    /**
     * Gets the delay.
     *
     * @return the delay in milliseconds
     */
    public long getDelayMs() {
        return _delayMs;
    }
}
