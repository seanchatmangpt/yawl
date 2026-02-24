/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.mcp.timeline;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;

/**
 * Renders ASCII Gantt-style timeline visualization for YAWL workflow case execution.
 *
 * This class takes case metadata and work item records and produces a beautiful
 * ASCII timeline showing task execution history, current state, and progress.
 * Features include proportional timeline bars, status indicators, and summary statistics.
 *
 * Handles graceful degradation when timing data is unavailable, falling back to
 * status-only visualization. Compatible with all YAWL specifications.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 2026-02-24
 */
public class CaseTimelineRenderer {

    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter TIME_SHORT_FORMAT =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    // Unicode box-drawing characters for ASCII art
    private static final String BOX_TOP_LEFT = "╔";
    private static final String BOX_TOP_RIGHT = "╗";
    private static final String BOX_HORIZONTAL = "═";
    private static final String BOX_VERTICAL = "║";
    private static final String BOX_BOTTOM_LEFT = "╚";
    private static final String BOX_BOTTOM_RIGHT = "╝";
    private static final String DASH = "─";
    private static final String SEPARATOR = "│";

    private static final String STATUS_COMPLETE = "✓";
    private static final String STATUS_RUNNING = "⏳";
    private static final String STATUS_ENABLED = "○";
    private static final String STATUS_BLOCKED = "✗";

    private static final String BAR_COMPLETE = "█";
    private static final String BAR_RUNNING = "░";

    /**
     * Renders a complete timeline visualization for the given case and work items.
     *
     * @param caseId           the case identifier
     * @param specName         the workflow specification name (can be null)
     * @param startTime        the case start time (can be null for relative timeline)
     * @param currentTime      the current time (typically now)
     * @param workItems        list of work items for the case (can be empty)
     * @param timelineWidth    width of timeline bars in characters (default 50)
     * @return formatted ASCII timeline string, never null
     */
    public static String renderTimeline(String caseId, String specName, Instant startTime,
                                         Instant currentTime, List<WorkItemRecord> workItems,
                                         int timelineWidth) {
        if (caseId == null || caseId.isEmpty()) {
            return renderError("Case ID is required");
        }

        if (timelineWidth < 20) {
            timelineWidth = 20;
        }
        if (timelineWidth > 200) {
            timelineWidth = 200;
        }

        if (currentTime == null) {
            currentTime = Instant.now();
        }

        if (workItems == null) {
            workItems = new ArrayList<>();
        }

        StringBuilder output = new StringBuilder();

        // Render header
        renderHeader(output, caseId, specName, startTime, currentTime);

        // Render timeline
        if (!workItems.isEmpty()) {
            renderTimelineSection(output, startTime, currentTime, workItems, timelineWidth);
            renderSummarySection(output, startTime, currentTime, workItems);
        } else {
            output.append("\n(No work items found for this case)\n");
        }

        return output.toString();
    }

    /**
     * Renders header with case metadata.
     */
    private static void renderHeader(StringBuilder output, String caseId, String specName,
                                      Instant startTime, Instant currentTime) {
        String spec = (specName != null && !specName.isEmpty()) ? specName : "Unknown Spec";
        String elapsed = formatElapsedTime(startTime, currentTime);

        int headerWidth = 72;
        String title = String.format("CASE #%s EXECUTION TIMELINE | %s", caseId, spec);
        String subtitle = String.format("Started: %s | Elapsed: %s",
            (startTime != null ? TIME_FORMAT.format(startTime) : "Unknown"),
            elapsed);

        output.append("\n");
        output.append(BOX_TOP_LEFT).append(BOX_HORIZONTAL.repeat(headerWidth)).append(BOX_TOP_RIGHT).append("\n");
        output.append(BOX_VERTICAL).append(padCenter(title, headerWidth)).append(BOX_VERTICAL).append("\n");
        output.append(BOX_VERTICAL).append(padCenter(subtitle, headerWidth)).append(BOX_VERTICAL).append("\n");
        output.append(BOX_BOTTOM_LEFT).append(BOX_HORIZONTAL.repeat(headerWidth)).append(BOX_BOTTOM_RIGHT).append("\n");
    }

    /**
     * Renders task execution timeline visualization.
     */
    private static void renderTimelineSection(StringBuilder output, Instant startTime,
                                               Instant currentTime, List<WorkItemRecord> workItems,
                                               int timelineWidth) {
        output.append("\nTASK EXECUTION TIMELINE (→ time flows right)\n");
        output.append(DASH.repeat(73)).append("\n");

        long caseDuration = (startTime != null && currentTime != null)
            ? Duration.between(startTime, currentTime).toMillis()
            : 0;

        // Render time axis labels
        renderTimeAxis(output, caseDuration, timelineWidth);

        // Sort work items by start time (or creation order)
        List<WorkItemRecord> sorted = sortWorkItems(workItems);

        // Render each work item as a timeline bar
        for (WorkItemRecord item : sorted) {
            renderTimelineRow(output, item, startTime, currentTime, caseDuration, timelineWidth);
        }

        output.append(DASH.repeat(73)).append("\n");
    }

    /**
     * Renders the time axis (header row with time labels).
     */
    private static void renderTimeAxis(StringBuilder output, long caseDurationMs, int barWidth) {
        StringBuilder timeAxis = new StringBuilder();
        timeAxis.append(String.format("%-28s", ""));
        timeAxis.append(String.format("%6s", "00:00"));

        for (int i = 1; i < (barWidth / 10); i++) {
            int minutes = i * (int)(caseDurationMs / (barWidth * 60_000));
            if (minutes <= 0) minutes = i * 30;
            timeAxis.append(String.format("%6s", String.format("00:%02d", minutes % 60)));
        }
        timeAxis.append(String.format("%6s", "NOW"));

        output.append(timeAxis).append("\n");

        // Render axis separator
        StringBuilder axisSep = new StringBuilder();
        axisSep.append(String.format("%-28s", ""));
        for (int i = 0; i < (barWidth + 12); i++) {
            axisSep.append(SEPARATOR);
        }
        output.append(axisSep).append("\n");
    }

    /**
     * Renders a single work item's timeline row.
     */
    private static void renderTimelineRow(StringBuilder output, WorkItemRecord item,
                                           Instant startTime, Instant currentTime,
                                           long caseDurationMs, int barWidth) {
        String taskName = item.getTaskName() != null ? item.getTaskName() : "Unknown Task";
        if (taskName.length() > 25) {
            taskName = taskName.substring(0, 22) + "...";
        }

        String status = item.getStatus() != null ? item.getStatus() : "Unknown";
        String statusSymbol = getStatusSymbol(status);

        // Extract timing data (naive: look for millisecond-based times)
        long enablementMs = extractTimingMs(item.getEnablementTime());
        long startMs = extractTimingMs(item.getStartTime());
        long completionMs = extractTimingMs(item.getCompletionTime());

        String bar;
        String timing;

        if (startMs > 0 && completionMs > 0 && caseDurationMs > 0) {
            // Completed task: show duration in bar
            double startFrac = (double) startMs / caseDurationMs;
            double endFrac = (double) completionMs / caseDurationMs;
            long durationMs = completionMs - startMs;
            timing = String.format("[%dm]", durationMs / 60_000);
            bar = buildTimelineBar(startFrac, endFrac, barWidth, false);
        } else if (startMs > 0 && caseDurationMs > 0) {
            // Running task: show from start to now
            double startFrac = (double) startMs / caseDurationMs;
            double endFrac = 1.0;
            long elapsedMs = caseDurationMs - startMs;
            timing = String.format("[%dm ⏳ RUNNING]", elapsedMs / 60_000);
            bar = buildTimelineBar(startFrac, endFrac, barWidth, true);
        } else if (enablementMs > 0 && caseDurationMs > 0) {
            // Waiting/enabled: show as waiting indicator
            bar = String.format("%-" + barWidth + "s", "[waiting]");
            timing = "";
        } else {
            // No timing data: show status only
            bar = String.format("%-" + barWidth + "s", "(" + status + ")");
            timing = "";
        }

        output.append(String.format("%-28s", taskName))
              .append(bar)
              .append(" ")
              .append(statusSymbol)
              .append("  ")
              .append(timing)
              .append("\n");
    }

    /**
     * Builds a proportional timeline bar.
     *
     * @param startFrac fraction of total timeline (0.0 to 1.0)
     * @param endFrac   fraction of total timeline (0.0 to 1.0)
     * @param width     bar width in characters
     * @param isRunning true if task is currently running (use different char)
     * @return bar string
     */
    private static String buildTimelineBar(double startFrac, double endFrac,
                                           int width, boolean isRunning) {
        StringBuilder bar = new StringBuilder();
        int startPos = (int) Math.round(startFrac * width);
        int endPos = (int) Math.round(endFrac * width);

        // Clamp to valid range
        startPos = Math.max(0, Math.min(startPos, width - 1));
        endPos = Math.max(startPos + 1, Math.min(endPos, width));

        for (int i = 0; i < width; i++) {
            if (i >= startPos && i < endPos) {
                bar.append(isRunning ? BAR_RUNNING : BAR_COMPLETE);
            } else {
                bar.append(" ");
            }
        }

        return bar.toString();
    }

    /**
     * Renders execution summary statistics.
     */
    private static void renderSummarySection(StringBuilder output, Instant startTime,
                                              Instant currentTime,
                                              List<WorkItemRecord> workItems) {
        int completed = 0;
        int running = 0;
        int waiting = 0;
        int failed = 0;

        for (WorkItemRecord item : workItems) {
            String status = item.getStatus();
            if (status != null) {
                if (status.equalsIgnoreCase(WorkItemRecord.statusComplete) ||
                    status.equalsIgnoreCase(WorkItemRecord.statusForcedComplete)) {
                    completed++;
                } else if (status.equalsIgnoreCase(WorkItemRecord.statusExecuting)) {
                    running++;
                } else if (status.equalsIgnoreCase(WorkItemRecord.statusEnabled) ||
                           status.equalsIgnoreCase(WorkItemRecord.statusFired)) {
                    waiting++;
                } else if (status.equalsIgnoreCase(WorkItemRecord.statusFailed) ||
                           status.equalsIgnoreCase(WorkItemRecord.statusDeadlocked) ||
                           status.equalsIgnoreCase(WorkItemRecord.statusDiscarded)) {
                    failed++;
                } else {
                    waiting++;
                }
            } else {
                waiting++;
            }
        }

        int total = workItems.size();
        double percentComplete = (total > 0) ? (100.0 * completed / total) : 0.0;

        output.append("\nTASK LEGEND\n");
        output.append("───────────\n");
        output.append(BAR_COMPLETE).append(BAR_COMPLETE).append(BAR_COMPLETE).append(BAR_COMPLETE)
              .append(" = Completed  ")
              .append(BAR_RUNNING).append(BAR_RUNNING).append(BAR_RUNNING).append(BAR_RUNNING)
              .append(" = Running  ")
              .append(STATUS_ENABLED).append(STATUS_ENABLED).append(STATUS_ENABLED).append(STATUS_ENABLED)
              .append(" = Enabled/Waiting  ")
              .append(DASH).append(DASH).append(DASH).append(DASH)
              .append(" = Blocked\n");

        output.append("\nEXECUTION SUMMARY\n");
        output.append("─────────────────\n");
        output.append(String.format("Completed:  %d/%d tasks (%.0f%%)\n", completed, total, percentComplete));
        output.append(String.format("Running:    %d task%s\n", running, running == 1 ? "" : "s"));
        output.append(String.format("Waiting:    %d task%s\n", waiting, waiting == 1 ? "" : "s"));
        if (failed > 0) {
            output.append(String.format("Failed:     %d task%s\n", failed, failed == 1 ? "" : "s"));
        }

        // Progress bar
        output.append("Progress:   ");
        renderProgressBar(output, (int) percentComplete, 32);
        output.append(String.format(" %.0f%%\n", percentComplete));

        // Estimate remaining time
        if (running > 0 && startTime != null) {
            long elapsedMs = Duration.between(startTime, currentTime).toMillis();
            long estimatedTotalMs = (total > 0) ? (elapsedMs * total) / completed : 0;
            long remainingMs = Math.max(0, estimatedTotalMs - elapsedMs);
            String estimate = formatMilliseconds(remainingMs);
            output.append(String.format("Est. remaining: ~%s (if current tasks complete normally)\n", estimate));
        }

        // Add performance notes if available
        long taskCount = workItems.stream()
            .filter(w -> w.getCompletionTime() != null && !w.getCompletionTime().isEmpty())
            .count();
        if (taskCount > 3) {
            long totalDurationMs = 0;
            for (WorkItemRecord item : workItems) {
                try {
                    long startMs = extractTimingMs(item.getStartTime());
                    long endMs = extractTimingMs(item.getCompletionTime());
                    if (startMs > 0 && endMs > 0) {
                        totalDurationMs += (endMs - startMs);
                    }
                } catch (Exception ignored) {
                    // Ignore parsing errors
                }
            }

            if (running > 0 && totalDurationMs > 0 && completed > 0) {
                long avgMs = totalDurationMs / completed;
                if (avgMs > 0) {
                    output.append("\n⚠️  ATTENTION: Some tasks are running longer than typical\n");
                }
            }
        }
    }

    /**
     * Renders a progress bar.
     */
    private static void renderProgressBar(StringBuilder output, int percent, int width) {
        int filled = (percent * width) / 100;
        output.append(BAR_COMPLETE.repeat(filled))
              .append(BAR_RUNNING.repeat(Math.max(0, width - filled)));
    }

    /**
     * Gets the appropriate status symbol.
     */
    private static String getStatusSymbol(String status) {
        if (status == null) return " ";
        if (status.equalsIgnoreCase(WorkItemRecord.statusComplete) ||
            status.equalsIgnoreCase(WorkItemRecord.statusForcedComplete)) {
            return STATUS_COMPLETE;
        }
        if (status.equalsIgnoreCase(WorkItemRecord.statusExecuting)) {
            return STATUS_RUNNING;
        }
        if (status.equalsIgnoreCase(WorkItemRecord.statusFailed) ||
            status.equalsIgnoreCase(WorkItemRecord.statusDeadlocked)) {
            return STATUS_BLOCKED;
        }
        return STATUS_ENABLED;
    }

    /**
     * Extracts milliseconds from a timing field (may be string or already numeric).
     * Returns 0 if unable to parse.
     */
    private static long extractTimingMs(String timingField) {
        if (timingField == null || timingField.isEmpty()) {
            return 0;
        }

        try {
            // Try direct parse as milliseconds
            return Long.parseLong(timingField);
        } catch (NumberFormatException e1) {
            // Try ISO format or other date format
            try {
                // Parse ISO instant
                Instant inst = Instant.parse(timingField);
                return inst.toEpochMilli();
            } catch (Exception e2) {
                // Unable to parse
                return 0;
            }
        }
    }

    /**
     * Formats elapsed time between two instants.
     */
    private static String formatElapsedTime(Instant startTime, Instant currentTime) {
        if (startTime == null || currentTime == null) {
            return "Unknown";
        }

        Duration elapsed = Duration.between(startTime, currentTime);
        return formatMilliseconds(elapsed.toMillis());
    }

    /**
     * Formats milliseconds into human-readable duration.
     */
    private static String formatMilliseconds(long ms) {
        if (ms < 0) ms = 0;

        long hours = ms / 3_600_000;
        long minutes = (ms % 3_600_000) / 60_000;
        long seconds = (ms % 60_000) / 1_000;

        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        }
        if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        }
        return String.format("%ds", seconds);
    }

    /**
     * Pads text to center within given width.
     */
    private static String padCenter(String text, int width) {
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        int leftPad = (width - text.length()) / 2;
        int rightPad = width - text.length() - leftPad;
        return " ".repeat(leftPad) + text + " ".repeat(rightPad);
    }

    /**
     * Sorts work items by start time (or enablement time, or natural order).
     */
    private static List<WorkItemRecord> sortWorkItems(List<WorkItemRecord> workItems) {
        List<WorkItemRecord> sorted = new ArrayList<>(workItems);
        sorted.sort((a, b) -> {
            // Try to sort by start time
            String aStart = a.getStartTime();
            String bStart = b.getStartTime();

            if (aStart != null && !aStart.isEmpty() && bStart != null && !bStart.isEmpty()) {
                try {
                    long aMs = extractTimingMs(aStart);
                    long bMs = extractTimingMs(bStart);
                    return Long.compare(aMs, bMs);
                } catch (Exception ignored) {
                    // Fall through to enablement time
                }
            }

            // Try enablement time
            String aEnable = a.getEnablementTime();
            String bEnable = b.getEnablementTime();
            if (aEnable != null && !aEnable.isEmpty() && bEnable != null && !bEnable.isEmpty()) {
                try {
                    long aMs = extractTimingMs(aEnable);
                    long bMs = extractTimingMs(bEnable);
                    return Long.compare(aMs, bMs);
                } catch (Exception ignored) {
                    // Fall through
                }
            }

            // Fall back to task name
            String aName = a.getTaskName() != null ? a.getTaskName() : "";
            String bName = b.getTaskName() != null ? b.getTaskName() : "";
            return aName.compareTo(bName);
        });

        return sorted;
    }

    /**
     * Renders an error message.
     */
    private static String renderError(String errorMsg) {
        return "\n" + BOX_TOP_LEFT + BOX_HORIZONTAL.repeat(50) + BOX_TOP_RIGHT + "\n" +
               BOX_VERTICAL + padCenter("ERROR", 50) + BOX_VERTICAL + "\n" +
               BOX_VERTICAL + padCenter(errorMsg, 50) + BOX_VERTICAL + "\n" +
               BOX_BOTTOM_LEFT + BOX_HORIZONTAL.repeat(50) + BOX_BOTTOM_RIGHT + "\n";
    }
}
