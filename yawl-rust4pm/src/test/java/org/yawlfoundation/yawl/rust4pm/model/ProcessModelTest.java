package org.yawlfoundation.yawl.rust4pm.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessModelTest {

    @Test
    void PetriNet_fields_accessible() {
        ProcessModel.PetriNet net = new ProcessModel.PetriNet(
            List.of("p1", "p2"), List.of("t1"), "<pnml/>");
        assertEquals(List.of("p1", "p2"), net.places());
        assertEquals(List.of("t1"), net.transitions());
        assertEquals("<pnml/>", net.pnmlXml());
    }

    @Test
    void ProcessTree_field_accessible() {
        ProcessModel.ProcessTree tree = new ProcessModel.ProcessTree("{\"op\":\"seq\"}");
        assertTrue(tree.treeJson().contains("seq"));
    }

    @Test
    void Declare_fields_accessible() {
        ProcessModel.Declare declare = new ProcessModel.Declare(
            List.of("Response(A,B)", "Absence(C)"));
        assertEquals(2, declare.constraints().size());
    }

    @Test
    void sealed_switch_matches_PetriNet() {
        ProcessModel model = new ProcessModel.PetriNet(List.of(), List.of(), "");
        String kind = switch (model) {
            case ProcessModel.PetriNet    pn -> "petri-net";
            case ProcessModel.ProcessTree pt -> "process-tree";
            case ProcessModel.Declare     dm -> "declare";
        };
        assertEquals("petri-net", kind);
    }

    @Test
    void sealed_switch_matches_ProcessTree() {
        ProcessModel model = new ProcessModel.ProcessTree("{}");
        String kind = switch (model) {
            case ProcessModel.PetriNet    pn -> "petri-net";
            case ProcessModel.ProcessTree pt -> "process-tree";
            case ProcessModel.Declare     dm -> "declare";
        };
        assertEquals("process-tree", kind);
    }

    @Test
    void sealed_switch_matches_Declare() {
        ProcessModel model = new ProcessModel.Declare(List.of());
        String kind = switch (model) {
            case ProcessModel.PetriNet    pn -> "petri-net";
            case ProcessModel.ProcessTree pt -> "process-tree";
            case ProcessModel.Declare     dm -> "declare";
        };
        assertEquals("declare", kind);
    }
}
