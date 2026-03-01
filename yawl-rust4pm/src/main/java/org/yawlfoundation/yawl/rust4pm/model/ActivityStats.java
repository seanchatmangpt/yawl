package org.yawlfoundation.yawl.rust4pm.model;

/**
 * Performance statistics for a single activity within a process.
 *
 * <p>Tracks timing metrics for a specific activity across all occurrences
 * in the event log.
 *
 * @param activityId    unique identifier or name of the activity
 * @param occurrences   number of times this activity was executed
 * @param medianTime    median execution time (milliseconds)
 * @param meanTime      mean execution time (milliseconds)
 * @param minTime       minimum observed execution time (milliseconds)
 * @param maxTime       maximum observed execution time (milliseconds)
 */
public record ActivityStats(
    String activityId,
    long occurrences,
    long medianTime,
    double meanTime,
    long minTime,
    long maxTime
) {}
