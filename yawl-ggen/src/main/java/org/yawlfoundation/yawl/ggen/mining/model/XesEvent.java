package org.yawlfoundation.yawl.ggen.mining.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single event in an XES (eXtensible Event Stream) event log.
 * Contains activity name, lifecycle transition, timestamp, and custom attributes.
 */
public class XesEvent {
    private String caseId;
    private String activityName;
    private String lifecycle;
    private String timestamp;
    private Map<String, String> attributes;

    public XesEvent(String caseId, String activityName) {
        this.caseId = Objects.requireNonNull(caseId, "Case ID cannot be null");
        this.activityName = Objects.requireNonNull(activityName, "Activity name cannot be null");
        this.attributes = new HashMap<>();
    }

    public String getCaseId() {
        return caseId;
    }

    public String getActivityName() {
        return activityName;
    }

    public String getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(String lifecycle) {
        this.lifecycle = lifecycle;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public void setAttribute(String key, String value) {
        attributes.put(key, value);
    }

    public String getAttribute(String key) {
        return attributes.get(key);
    }

    @Override
    public String toString() {
        return String.format("XesEvent(case=%s, activity=%s, lifecycle=%s, timestamp=%s)",
            caseId, activityName, lifecycle, timestamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XesEvent xesEvent = (XesEvent) o;
        return Objects.equals(caseId, xesEvent.caseId) &&
               Objects.equals(activityName, xesEvent.activityName) &&
               Objects.equals(timestamp, xesEvent.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caseId, activityName, timestamp);
    }
}
