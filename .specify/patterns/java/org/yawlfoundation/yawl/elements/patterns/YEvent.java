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

/**
 * Represents an event in the YAWL event system.
 *
 * <p>Events are dispatched to listeners based on type and
 * correlation keys.</p>
 *
 * @author YAWL Foundation
 * @since 5.3
 */
public class YEvent {

    /**
     * Enumeration of event types.
     */
    public enum YEventType {
        /** Timer-based event */
        TIMER,
        /** Message from queue */
        MESSAGE,
        /** Signal from another process */
        SIGNAL,
        /** Condition-based event */
        CONDITION,
        /** External system event */
        EXTERNAL
    }

    /** The event type */
    private final YEventType _type;

    /** The listener ID this event targets */
    private String _listenerId;

    /** The correlation key */
    private String _correlationKey;

    /** The event payload data */
    private Object _data;

    /** Timestamp when event occurred */
    private long _timestamp;

    /** For message events: the message type */
    private String _messageType;

    /** For signal events: the signal name */
    private String _signalName;

    /**
     * Constructs a new event.
     *
     * @param type the event type
     */
    public YEvent(YEventType type) {
        this._type = type;
        this._timestamp = System.currentTimeMillis();
    }

    /**
     * Gets the event type.
     *
     * @return the type
     */
    public YEventType getType() {
        return _type;
    }

    /**
     * Gets the listener ID.
     *
     * @return the listener ID
     */
    public String getListenerId() {
        return _listenerId;
    }

    /**
     * Sets the listener ID.
     *
     * @param listenerId the listener ID
     */
    public void setListenerId(String listenerId) {
        this._listenerId = listenerId;
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
     * Gets the event data.
     *
     * @return the data
     */
    public Object getData() {
        return _data;
    }

    /**
     * Sets the event data.
     *
     * @param data the data
     */
    public void setData(Object data) {
        this._data = data;
    }

    /**
     * Gets the timestamp.
     *
     * @return the timestamp
     */
    public long getTimestamp() {
        return _timestamp;
    }

    /**
     * Gets the message type (for message events).
     *
     * @return the message type
     */
    public String getMessageType() {
        return _messageType;
    }

    /**
     * Sets the message type.
     *
     * @param messageType the message type
     */
    public void setMessageType(String messageType) {
        this._messageType = messageType;
    }

    /**
     * Gets the signal name (for signal events).
     *
     * @return the signal name
     */
    public String getSignalName() {
        return _signalName;
    }

    /**
     * Sets the signal name.
     *
     * @param signalName the signal name
     */
    public void setSignalName(String signalName) {
        this._signalName = signalName;
    }

    /**
     * Creates a timer event.
     *
     * @param listenerId the listener ID
     * @return the event
     */
    public static YEvent createTimerEvent(String listenerId) {
        YEvent event = new YEvent(YEventType.TIMER);
        event.setListenerId(listenerId);
        return event;
    }

    /**
     * Creates a message event.
     *
     * @param listenerId the listener ID
     * @param messageType the message type
     * @param data the message data
     * @return the event
     */
    public static YEvent createMessageEvent(String listenerId, String messageType, Object data) {
        YEvent event = new YEvent(YEventType.MESSAGE);
        event.setListenerId(listenerId);
        event.setMessageType(messageType);
        event.setData(data);
        return event;
    }

    /**
     * Creates a signal event.
     *
     * @param signalName the signal name
     * @param data the signal data
     * @return the event
     */
    public static YEvent createSignalEvent(String signalName, Object data) {
        YEvent event = new YEvent(YEventType.SIGNAL);
        event.setSignalName(signalName);
        event.setData(data);
        return event;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "YEvent{" +
                "type=" + _type +
                ", listenerId='" + _listenerId + '\'' +
                ", timestamp=" + _timestamp +
                '}';
    }
}
