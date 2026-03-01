package org.yawlfoundation.yawl.rust4pm.model;

import java.util.List;

/**
 * Sealed interface for process models discoverable from OCEL2 logs.
 * Use exhaustive switch:
 * <pre>{@code
 * double score = switch (engine.discoverModel(log)) {
 *     case ProcessModel.PetriNet    pn -> conformanceCheck(log, pn);
 *     case ProcessModel.ProcessTree pt -> conformanceCheck(log, pt);
 *     case ProcessModel.Declare     dm -> conformanceCheck(log, dm);
 * };
 * }</pre>
 */
public sealed interface ProcessModel
    permits ProcessModel.PetriNet, ProcessModel.ProcessTree, ProcessModel.Declare {

    /**
     * Petri net model with PNML serialization for round-tripping to conformance check.
     * @param places list of place labels
     * @param transitions list of transition labels
     * @param pnmlXml raw PNML XML
     */
    record PetriNet(List<String> places, List<String> transitions, String pnmlXml)
        implements ProcessModel {}

    /**
     * Process tree model: hierarchical operator tree.
     * @param treeJson JSON representation of the process tree
     */
    record ProcessTree(String treeJson) implements ProcessModel {}

    /**
     * Declare model: declarative constraints between activities.
     * @param constraints list of Declare constraint strings (e.g., "Response(A,B)")
     */
    record Declare(List<String> constraints) implements ProcessModel {}
}
