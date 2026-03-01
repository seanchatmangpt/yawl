package org.yawlfoundation.yawl.rust4pm.model;

import java.util.List;

/**
 * Performance statistics computed from an OCEL2 event log.
 *
 * <p>Includes aggregate metrics such as median waiting time, throughput,
 * and per-activity performance breakdowns.
 *
 * @param medianWaitTime     median waiting time between events (milliseconds)
 * @param meanWaitTime       mean waiting time between events (milliseconds)
 * @param throughput         events processed per unit time
 * @param activityStats      per-activity performance metrics
 * @param totalEvents        total number of events in the analyzed log
 * @param totalCases         total number of cases/objects in the analyzed log
 */
public record PerformanceStats(
    long medianWaitTime,
    double meanWaitTime,
    double throughput,
    List<ActivityStats> activityStats,
    long totalEvents,
    long totalCases
) {}
