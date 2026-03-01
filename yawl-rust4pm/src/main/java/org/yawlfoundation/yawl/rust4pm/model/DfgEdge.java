package org.yawlfoundation.yawl.rust4pm.model;

/**
 * A directed edge in the directly-follows graph.
 *
 * @param source source activity id
 * @param target target activity id
 * @param count  number of times this transition was observed
 */
public record DfgEdge(String source, String target, long count) {}
