package org.yawlfoundation.yawl.rust4pm.model;

import java.util.List;
import java.util.Optional;

/**
 * A directly-follows graph (DFG) discovered from an OCEL2 event log.
 *
 * @param nodes list of activity nodes with occurrence counts
 * @param edges list of directly-follows edges with transition counts
 */
public record DirectlyFollowsGraph(List<DfgNode> nodes, List<DfgEdge> edges) {

    /** Find a node by its activity id. */
    public Optional<DfgNode> findNode(String id) {
        return nodes.stream().filter(n -> n.id().equals(id)).findFirst();
    }

    /** Sum of all edge counts. */
    public long totalTransitions() {
        return edges.stream().mapToLong(DfgEdge::count).sum();
    }
}
