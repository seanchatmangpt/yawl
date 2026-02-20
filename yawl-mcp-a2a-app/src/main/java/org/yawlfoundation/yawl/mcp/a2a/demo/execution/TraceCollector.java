/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.demo.execution;

import java.io.StringWriter;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Standalone trace collector for YAWL workflow execution events.
 *
 * <p>Provides thread-safe collection and analysis of execution trace events
 * with support for filtering, summarization, and export to multiple formats.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Thread-safe event collection using CopyOnWriteArrayList</li>
 *   <li>Event filtering by type with getEventsByType()</li>
 *   <li>Automatic summary generation with event counts</li>
 *   <li>JSON export for integration with observability tools</li>
 *   <li>CSV export for analysis in spreadsheets</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * TraceCollector collector = new TraceCollector();
 *
 * // Record events during execution
 * collector.recordEvent("workItemStarted", Map.of("taskId", "TaskA", "caseId", "case-123"));
 * collector.recordEvent("stateChange", Map.of("from", "enabled", "to", "executing"));
 * collector.recordEvent("error", Map.of("message", "Connection timeout", "code", 504));
 *
 * // Get summary
 * String summary = collector.getSummary();
 * // "3 events (1 errors, 1 work items, 1 state changes)"
 *
 * // Export to JSON
 * String json = collector.exportToJson();
 *
 * // Export to CSV
 * String csv = collector.exportToCsv();
 *
 * // Clear for reuse
 * collector.clear();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class TraceCollector {

    /** ISO-8601 formatter for timestamps in exports */
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /** Empty string constant for null-safe escaping */
    private static final String EMPTY_STRING = "";

    /** Thread-safe list of trace events */
    private final List<TraceEvent> events;

    /** Event type categorization for summary generation */
    private final Map<String, String> eventTypeCategories;

    /**
     * Creates a new empty trace collector.
     */
    public TraceCollector() {
        this.events = new CopyOnWriteArrayList<>();
        this.eventTypeCategories = initializeEventTypeCategories();
    }

    /**
     * Initialize event type to category mapping for summary generation.
     */
    private Map<String, String> initializeEventTypeCategories() {
        Map<String, String> categories = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        // Error events
        categories.put("error", "errors");
        categories.put("exception", "errors");
        categories.put("failure", "errors");

        // Work item events
        categories.put("workItemStarted", "work items");
        categories.put("workItemCompleted", "work items");
        categories.put("workItemEvent", "work items");
        categories.put("workitem", "work items");
        categories.put("taskStarted", "work items");
        categories.put("taskCompleted", "work items");

        // State change events
        categories.put("stateChange", "state changes");
        categories.put("statechange", "state changes");
        categories.put("transition", "state changes");

        // Decision events
        categories.put("decision", "decisions");
        categories.put("routing", "decisions");

        // Data events
        categories.put("data", "data operations");
        categories.put("datatransform", "data operations");

        return categories;
    }

    /**
     * Record an execution event with the current timestamp.
     *
     * @param type the event type (e.g., "workItemStarted", "error", "stateChange")
     * @param data the event data payload (can be any object)
     */
    public void recordEvent(String type, Object data) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Event type cannot be null or blank");
        }
        events.add(new TraceEvent(type, data, Instant.now()));
    }

    /**
     * Record an execution event with a specific timestamp.
     *
     * @param type the event type
     * @param data the event data payload
     * @param timestamp the event timestamp
     */
    public void recordEvent(String type, Object data, Instant timestamp) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Event type cannot be null or blank");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
        events.add(new TraceEvent(type, data, timestamp));
    }

    /**
     * Get all recorded trace events.
     *
     * @return an unmodifiable list of all trace events in chronological order
     */
    public List<TraceEvent> getTrace() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    /**
     * Get events filtered by type (case-insensitive).
     *
     * @param type the event type to filter by
     * @return list of events matching the specified type
     */
    public List<TraceEvent> getEventsByType(String type) {
        if (type == null) {
            return Collections.emptyList();
        }

        return events.stream()
            .filter(e -> type.equalsIgnoreCase(e.type()))
            .collect(Collectors.toList());
    }

    /**
     * Get events matching any of the specified types.
     *
     * @param types the event types to include
     * @return list of events matching any of the specified types
     */
    public List<TraceEvent> getEventsByTypes(String... types) {
        if (types == null || types.length == 0) {
            return Collections.emptyList();
        }

        List<String> typeList = List.of(types);
        return events.stream()
            .filter(e -> typeList.stream().anyMatch(t -> t.equalsIgnoreCase(e.type())))
            .collect(Collectors.toList());
    }

    /**
     * Clear all recorded events.
     */
    public void clear() {
        events.clear();
    }

    /**
     * Check if the collector has any events.
     *
     * @return true if no events have been recorded
     */
    public boolean isEmpty() {
        return events.isEmpty();
    }

    /**
     * Get the total number of recorded events.
     *
     * @return the event count
     */
    public int size() {
        return events.size();
    }

    /**
     * Get a summary of recorded events.
     *
     * <p>Format: "N events (X errors, Y work items, Z state changes, ...)"</p>
     *
     * @return a human-readable summary string
     */
    public String getSummary() {
        if (events.isEmpty()) {
            return "0 events";
        }

        Map<String, Long> categoryCounts = events.stream()
            .collect(Collectors.groupingBy(
                e -> eventTypeCategories.getOrDefault(e.type(), "other"),
                Collectors.counting()
            ));

        StringBuilder sb = new StringBuilder();
        sb.append(events.size()).append(" events");

        if (!categoryCounts.isEmpty()) {
            sb.append(" (");

            List<String> parts = new ArrayList<>();
            categoryCounts.forEach((category, count) ->
                parts.add(count + " " + category));

            sb.append(String.join(", ", parts));
            sb.append(")");
        }

        return sb.toString();
    }

    /**
     * Get a detailed summary with event breakdown.
     *
     * @return a multi-line summary with event type counts
     */
    public String getDetailedSummary() {
        if (events.isEmpty()) {
            return "No events recorded";
        }

        Map<String, Long> typeCounts = events.stream()
            .collect(Collectors.groupingBy(
                TraceEvent::type,
                Collectors.counting()
            ));

        StringBuilder sb = new StringBuilder();
        sb.append("Total events: ").append(events.size()).append("\n");
        sb.append("Event breakdown:\n");

        typeCounts.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .forEach(entry ->
                sb.append("  - ").append(entry.getKey())
                  .append(": ").append(entry.getValue()).append("\n"));

        return sb.toString().trim();
    }

    /**
     * Export all events to JSON format.
     *
     * @return a JSON array of event objects
     */
    public String exportToJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");

        for (int i = 0; i < events.size(); i++) {
            TraceEvent event = events.get(i);
            sb.append("  {\n");
            sb.append("    \"type\": \"").append(escapeJson(event.type())).append("\",\n");
            sb.append("    \"timestamp\": \"").append(ISO_FORMATTER.format(event.timestamp())).append("\",\n");
            sb.append("    \"data\": ").append(serializeData(event.data())).append("\n");
            sb.append("  }");

            if (i < events.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        sb.append("]");
        return sb.toString();
    }

    /**
     * Export all events to CSV format.
     *
     * @return a CSV string with headers: type,timestamp,data
     */
    public String exportToCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("type,timestamp,data\n");

        for (TraceEvent event : events) {
            sb.append(escapeCsv(event.type())).append(",");
            sb.append(ISO_FORMATTER.format(event.timestamp())).append(",");
            sb.append(escapeCsv(String.valueOf(event.data()))).append("\n");
        }

        return sb.toString();
    }

    /**
     * Get the first event of a specific type.
     *
     * @param type the event type to find
     * @return the first matching event, or null if not found
     */
    public TraceEvent getFirstEventOfType(String type) {
        return events.stream()
            .filter(e -> type.equalsIgnoreCase(e.type()))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get the last event of a specific type.
     *
     * @param type the event type to find
     * @return the last matching event, or null if not found
     */
    public TraceEvent getLastEventOfType(String type) {
        TraceEvent last = null;
        for (TraceEvent event : events) {
            if (type.equalsIgnoreCase(event.type())) {
                last = event;
            }
        }
        return last;
    }

    /**
     * Get events within a time range.
     *
     * @param start the start of the time range (inclusive)
     * @param end the end of the time range (inclusive)
     * @return list of events within the time range
     */
    public List<TraceEvent> getEventsBetween(Instant start, Instant end) {
        if (start == null || end == null) {
            return Collections.emptyList();
        }

        return events.stream()
            .filter(e -> !e.timestamp().isBefore(start) && !e.timestamp().isAfter(end))
            .collect(Collectors.toList());
    }

    /**
     * Check if any errors were recorded.
     *
     * @return true if any error events exist
     */
    public boolean hasErrors() {
        return !getEventsByType("error").isEmpty();
    }

    /**
     * Get all error events.
     *
     * @return list of error events
     */
    public List<TraceEvent> getErrors() {
        return getEventsByTypes("error", "exception", "failure");
    }

    // ==================== Private Helper Methods ====================

    private String escapeJson(String value) {
        if (value == null) {
            return EMPTY_STRING;
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return EMPTY_STRING;
        }
        // If contains comma, quote, or newline, wrap in quotes and escape quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String serializeData(Object data) {
        if (data == null) {
            return "null";
        }

        if (data instanceof String) {
            return "\"" + escapeJson((String) data) + "\"";
        }

        if (data instanceof Number || data instanceof Boolean) {
            return data.toString();
        }

        if (data instanceof Map) {
            return serializeMap((Map<?, ?>) data);
        }

        if (data instanceof List) {
            return serializeList((List<?>) data);
        }

        // For other objects, use toString and quote it
        return "\"" + escapeJson(data.toString()) + "\"";
    }

    private String serializeMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;

            sb.append("\"").append(escapeJson(String.valueOf(entry.getKey()))).append("\": ");
            sb.append(serializeData(entry.getValue()));
        }

        sb.append("}");
        return sb.toString();
    }

    private String serializeList(List<?> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        boolean first = true;
        for (Object item : list) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(serializeData(item));
        }

        sb.append("]");
        return sb.toString();
    }

    // ==================== Inner Record ====================

    /**
     * Immutable trace event record.
     *
     * @param type the event type identifier
     * @param data the event data payload
     * @param timestamp when the event occurred
     */
    public record TraceEvent(String type, Object data, Instant timestamp) {

        /**
         * Creates a trace event with validation.
         */
        public TraceEvent {
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("Event type cannot be null or blank");
            }
            if (timestamp == null) {
                throw new IllegalArgumentException("Timestamp cannot be null");
            }
        }

        /**
         * Get the event data as a string.
         *
         * @return string representation of the data
         */
        public String dataAsString() {
            return data != null ? data.toString() : "";
        }

        /**
         * Check if this event represents an error.
         *
         * @return true if the event type indicates an error
         */
        public boolean isError() {
            String lowerType = type.toLowerCase();
            return lowerType.contains("error") ||
                   lowerType.contains("exception") ||
                   lowerType.contains("failure");
        }
    }
}
