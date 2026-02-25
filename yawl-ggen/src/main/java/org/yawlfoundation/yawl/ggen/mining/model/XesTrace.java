package org.yawlfoundation.yawl.ggen.mining.model;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a trace (case) in an XES event log.
 * Contains a sequence of events for a single workflow case.
 */
public class XesTrace {
    private String caseId;
    private List<XesEvent> events;

    public XesTrace(String caseId) {
        this.caseId = Objects.requireNonNull(caseId, "Case ID cannot be null");
        this.events = new ArrayList<>();
    }

    public String getCaseId() {
        return caseId;
    }

    public List<XesEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public void addEvent(XesEvent event) {
        events.add(Objects.requireNonNull(event, "Event cannot be null"));
    }

    /**
     * Get the duration of this trace (last event time - first event time).
     * @return duration in milliseconds, or 0 if trace has < 2 events
     */
    public long getDuration() {
        if (events.size() < 2) {
            return 0;
        }

        String firstTimestamp = events.get(0).getTimestamp();
        String lastTimestamp = events.get(events.size() - 1).getTimestamp();

        if (firstTimestamp == null || lastTimestamp == null) {
            return 0;
        }

        try {
            ZonedDateTime firstTime = ZonedDateTime.parse(firstTimestamp);
            ZonedDateTime lastTime = ZonedDateTime.parse(lastTimestamp);
            return lastTime.toInstant().toEpochMilli() - firstTime.toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public String toString() {
        return String.format("XesTrace(caseId=%s, events=%d)", caseId, events.size());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XesTrace xesTrace = (XesTrace) o;
        return Objects.equals(caseId, xesTrace.caseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caseId);
    }
}
