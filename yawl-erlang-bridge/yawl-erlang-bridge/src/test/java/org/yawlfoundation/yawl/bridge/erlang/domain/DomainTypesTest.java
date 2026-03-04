package org.yawlfoundation.yawl.bridge.erlang.domain;

import org.yawlfoundation.yawl.bridge.erlang.ErlTerm;
import org.yawlfoundation.yawl.bridge.erlang.ErlAtom;
import org.yawlfoundation.yawl.bridge.erlang.ErlLong;
import org.yawlfoundation.yawl.bridge.erlang.ErlTuple;
import org.yawlfoundation.yawl.bridge.erlang.ErlList;
import org.yawlfoundation.yawl.bridge.erlang.ErlangException;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * Test cases for process mining domain types.
 */
class DomainTypesTest {

    @Test
    void testOcelIdFromString() {
        String uuidStr = "123e4567-e89b-12d3-a456-426614174000";
        OcelId ocelId = OcelId.fromString(uuidStr);
        assertEquals(UUID.fromString(uuidStr), ocelId.value());
    }

    @Test
    void testOcelIdFromErlTerm() throws Exception {
        ErlAtom atom = ErlAtom.of("123e4567-e89b-12d3-a456-426614174000");
        OcelId ocelId = OcelId.fromErlTerm(atom);
        assertNotNull(ocelId);
        assertEquals("123e4567-e89b-12d3-a456-426614174000", ocelId.value().toString());
    }

    @Test
    void testSlimOcelIdFromString() {
        String uuidStr = "123e4567-e89b-12d3-a456-426614174001";
        SlimOcelId slimId = SlimOcelId.fromString(uuidStr);
        assertEquals(UUID.fromString(uuidStr), slimId.value());
    }

    @Test
    void testSlimOcelIdFromErlTerm() throws Exception {
        ErlAtom atom = ErlAtom.of("123e4567-e89b-12d3-a456-426614174001");
        SlimOcelId slimId = SlimOcelId.fromErlTerm(atom);
        assertNotNull(slimId);
        assertEquals("123e4567-e89b-12d3-a456-426614174001", slimId.value().toString());
    }

    @Test
    void testPetriNetIdFromString() {
        String uuidStr = "123e4567-e89b-12d3-a456-426614174002";
        PetriNetId pnId = PetriNetId.fromString(uuidStr);
        assertEquals(UUID.fromString(uuidStr), pnId.value());
    }

    @Test
    void testPetriNetIdFromErlTerm() throws Exception {
        ErlAtom atom = ErlAtom.of("123e4567-e89b-12d3-a456-426614174002");
        PetriNetId pnId = PetriNetId.fromErlTerm(atom);
        assertNotNull(pnId);
        assertEquals("123e4567-e89b-12d3-a456-426614174002", pnId.value().toString());
    }

    @Test
    void testConformanceResult() {
        ConformanceResult result = new ConformanceResult(0.85, 10, 5, 100);
        assertEquals(0.85, result.fitness());
        assertEquals(10, result.missing());
        assertEquals(5, result.remaining());
        assertEquals(100, result.consumed());
    }

    @Test
    void testConformanceResultFromErlTerm() throws Exception {
        ErlTerm tuple = ErlTuple.of(
            ErlAtom.of("ok"),
            ErlLong.of(85000),  // Erlang uses integer precision for doubles
            ErlLong.of(10),
            ErlLong.of(5),
            ErlLong.of(100)
        );
        ConformanceResult result = ConformanceResult.fromErlTerm(tuple);
        assertEquals(0.85, result.fitness());
        assertEquals(10, result.missing());
        assertEquals(5, result.remaining());
        assertEquals(100, result.consumed());
    }

    @Test
    void testConstraint() {
        Map<String, Object> params = new HashMap<>();
        params.put("param1", "value1");
        params.put("param2", 42);

        Constraint constraint = new Constraint("ATleast", params, 0.75);
        assertEquals("ATleast", constraint.template());
        assertEquals(2, constraint.params().size());
        assertEquals(0.75, constraint.support());
    }

    @Test
    void testDirectlyFollowsGraph() {
        Map<String, Map<String, Integer>> edges = new HashMap<>();
        Map<String, Integer> source1 = new HashMap<>();
        source1.put("activity2", 5);
        source1.put("activity3", 3);
        edges.put("activity1", source1);

        DirectlyFollowsGraph dfg = new DirectlyFollowsGraph(edges);
        assertEquals(1, dfg.edges().size());
        assertEquals(5, dfg.edges().get("activity1").get("activity2"));
    }

    @Test
    void testPetriNet() {
        String pnmlXml = "<pnml><net id=\"net1\"><place id=\"p1\"/></net></pnml>";
        PetriNet petriNet = new PetriNet(pnmlXml);
        assertEquals(pnmlXml, petriNet.pnmlXml());
    }

    @Test
    void testProcessMiningClientCreate() {
        assertDoesNotThrow(() -> {
            ProcessMiningClient client = ProcessMiningClient.create();
            assertNotNull(client);
            assertFalse(client.isConnected()); // Should be false as Erlang node is not running
        });
    }
}