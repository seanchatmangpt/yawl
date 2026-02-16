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

import org.jdom2.Element;
import org.yawlfoundation.yawl.elements.YFlow;
import org.yawlfoundation.yawl.engine.YPersistenceManager;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;

/**
 * Event listener for message-based events.
 *
 * <p>Listens for messages from a queue with optional XPath filtering.</p>
 *
 * @author YAWL Foundation
 * @since 5.3
 */
public class YMessageEventListener extends YEventListener {

    /** Type of message to listen for */
    private final String _messageType;

    /** XPath filter for message content */
    private final String _xpathFilter;

    /** Queue name for message subscription */
    private String _queueName;

    /** Reference to the deferred choice task */
    private YDeferredChoiceTask _task;

    /**
     * Constructs a new message event listener.
     *
     * @param listenerId the listener identifier
     * @param messageType the message type
     * @param xpathFilter the XPath filter (may be null)
     * @param targetFlow the flow to trigger
     */
    public YMessageEventListener(String listenerId, String messageType,
                                  String xpathFilter, YFlow targetFlow) {
        super(listenerId, targetFlow);
        this._messageType = messageType;
        this._xpathFilter = xpathFilter;
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
        // Subscribe to message queue
        // In actual implementation, this would connect to JMS or internal queue
        _state = ListenerState.REGISTERED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unregister(YPersistenceManager pmgr) throws YPersistenceException {
        // Unsubscribe from message queue
        _state = ListenerState.CANCELLED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(YEvent event) {
        if (event.getType() != YEvent.YEventType.MESSAGE) {
            return false;
        }
        if (!_messageType.equals(event.getMessageType())) {
            return false;
        }
        if (_xpathFilter != null) {
            return evaluateXPathFilter(event.getData(), _xpathFilter);
        }
        return true;
    }

    /**
     * Evaluates the XPath filter against message data.
     *
     * @param data the message data
     * @param xpath the XPath expression
     * @return true if matches
     */
    private boolean evaluateXPathFilter(Object data, String xpath) {
        if (data instanceof Element) {
            // Use SaxonUtil to evaluate XPath
            // Placeholder for actual implementation
            return true;
        }
        return false;
    }

    /**
     * Gets the message type.
     *
     * @return the message type
     */
    public String getMessageType() {
        return _messageType;
    }

    /**
     * Gets the XPath filter.
     *
     * @return the XPath filter
     */
    public String getXpathFilter() {
        return _xpathFilter;
    }

    /**
     * Gets the queue name.
     *
     * @return the queue name
     */
    public String getQueueName() {
        return _queueName;
    }

    /**
     * Sets the queue name.
     *
     * @param queueName the queue name
     */
    public void setQueueName(String queueName) {
        this._queueName = queueName;
    }
}
