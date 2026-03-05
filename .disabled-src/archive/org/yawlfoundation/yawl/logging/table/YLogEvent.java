/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.logging.table;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.jdom2.Element;
import org.yawlfoundation.yawl.util.StringUtil;

/**
 * One row of the logEvent table, representing a single net or task instance runtime event
 *
 * Author: Michael Adams
 * Creation Date: 6/04/2009
 */
public class YLogEvent {

    private static final DateTimeFormatter SDF =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter MID_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd' 'HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private long eventID;                   // PK - auto generated
    private long instanceID;                // FK to YLogTaskInstance OR YLogNetInstance
    private String descriptor ;             // the type of event
    private long timestamp ;
    private long serviceID;                 // the service that created the event
    private long rootNetInstanceID;         // convenience for queries

    public YLogEvent() { }

    public YLogEvent(long instanceID, String descriptor, long timestamp,
                     long serviceID, long rootNetInstanceID) {
        this.instanceID = instanceID;
        this.descriptor = descriptor;
        this.timestamp = timestamp;
        this.serviceID = serviceID;
        this.rootNetInstanceID = rootNetInstanceID;
    }

    public YLogEvent(Element xml) {
        fromXML(xml);
    }

    public long getEventID() {
        return eventID;
    }

    public void setEventID(long eventID) {
        this.eventID = eventID;
    }

    public long getInstanceID() {
        return instanceID;
    }

    public void setInstanceID(long instanceID) {
        this.instanceID = instanceID;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public void setDescriptor(String descriptor) {
        this.descriptor = descriptor;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getTimestampString() {
        return SDF.format(Instant.ofEpochMilli(timestamp));
    }

    public String getTimestampMidString() {
        return MID_FORMATTER.format(Instant.ofEpochMilli(timestamp));
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getServiceID() {
        return serviceID;
    }

    public void setServiceID(long serviceID) {
        this.serviceID = serviceID;
    }

    public long getRootNetInstanceID() {
        return rootNetInstanceID;
    }

    public void setRootNetInstanceID(long rootNetInstanceID) {
        this.rootNetInstanceID = rootNetInstanceID;
    }

    public boolean equals(Object other) {
        return other instanceof YLogEvent event &&
                (this.getEventID() == event.getEventID());
    }

    public int hashCode() {
        return (int) (31 * getEventID()) % Integer.MAX_VALUE;
    }


    public String toXML() {
        return """
            <event key="%s">\
            %s\
            %s\
            %s\
            %s\
            %s\
            </event>""".formatted(
                eventID,
                StringUtil.wrap(String.valueOf(instanceID), "instanceKey"),
                StringUtil.wrap(descriptor, "descriptor"),
                StringUtil.wrap(String.valueOf(timestamp), "timestamp"),
                StringUtil.wrap(String.valueOf(serviceID), "serviceKey"),
                StringUtil.wrap(String.valueOf(rootNetInstanceID), "rootNetInstanceKey")
        );
    }


    public void fromXML(Element xml) {
        eventID = strToLong(xml.getAttributeValue("key"));
        instanceID = strToLong(xml.getChildText("instanceKey"));
        descriptor = xml.getChildText("descriptor");
        timestamp = strToLong(xml.getChildText("timestamp"));
        serviceID = strToLong(xml.getChildText("serviceKey"));
        rootNetInstanceID = strToLong(xml.getChildText("rootNetInstanceKey"));
    }


    private long strToLong(String value) {
        try {
            return Long.valueOf(value);
        }
        catch (NumberFormatException nfe) {
            return -1;
        }
    }

}
