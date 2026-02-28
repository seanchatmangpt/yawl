package org.yawlfoundation.yawl.rust4pm.model;

/**
 * A node in the directly-follows graph representing an activity.
 *
 * @param id    unique node identifier
 * @param label human-readable activity name
 * @param count number of occurrences in the log
 */
public record DfgNode(String id, String label, long count) {}
