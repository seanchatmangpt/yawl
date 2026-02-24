package org.yawlfoundation.yawl.ggen.mining.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a complete XES (eXtensible Event Stream) event log.
 * Contains traces (cases) extracted from workflow execution logs.
 */
public class XesLog {
    private String name;
    private List<XesTrace> traces;

    public XesLog() {
        this.traces = new ArrayList<>();
    }

    public XesLog(String name) {
        this();
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<XesTrace> getTraces() {
        return traces;
    }

    public void addTrace(XesTrace trace) {
        traces.add(Objects.requireNonNull(trace, "Trace cannot be null"));
    }

    /**
     * Get all distinct activity names across all traces.
     * @return Set of activity names
     */
    public Set<String> getActivities() {
        Set<String> activities = new HashSet<>();
        for (XesTrace trace : traces) {
            for (XesEvent event : trace.getEvents()) {
                activities.add(event.getActivityName());
            }
        }
        return activities;
    }

    /**
     * Get total number of events across all traces.
     * @return event count
     */
    public int getEventCount() {
        int count = 0;
        for (XesTrace trace : traces) {
            count += trace.getEvents().size();
        }
        return count;
    }

    @Override
    public String toString() {
        return String.format("XesLog(name=%s, traces=%d, events=%d)",
            name, traces.size(), getEventCount());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XesLog xesLog = (XesLog) o;
        return Objects.equals(name, xesLog.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
