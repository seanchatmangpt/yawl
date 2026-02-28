/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.processmining.ocpm;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Object-Centric Process Mining discovery.
 *
 * <p>Chicago TDD: Real implementation tests, not mocks.</p>
 */
public class OcpmDiscoveryTest {

    /**
     * Test discovery with 2 object types: "case" and "item".
     *
     * Scenario:
     * - Event sequence per case: apply(case1) → check(case1,item1) → approve(case1,item1) → close(case1)
     * - Event sequence per item: check(case1,item1) → approve(case1,item1)
     *
     * Expected:
     * - 2 object types discovered
     * - Activities {apply, check, approve, close} for case type
     * - Activities {check, approve} for item type
     * - "check" and "approve" are shared transitions (appear in both types)
     */
    @Test
    public void testDiscoverTwoObjectTypes() {
        // Build test input
        List<OcpmInput.OcpmEvent> events = new ArrayList<>();
        Instant t0 = Instant.parse("2026-02-28T10:00:00Z");
        Instant t1 = t0.plusSeconds(60);
        Instant t2 = t1.plusSeconds(60);
        Instant t3 = t2.plusSeconds(60);

        // Events with object map
        events.add(new OcpmInput.OcpmEvent(
            "ev1", "apply", t0,
            Map.of("case", "case-1")
        ));
        events.add(new OcpmInput.OcpmEvent(
            "ev2", "check", t1,
            Map.of("case", "case-1", "item", "item-1")
        ));
        events.add(new OcpmInput.OcpmEvent(
            "ev3", "approve", t2,
            Map.of("case", "case-1", "item", "item-1")
        ));
        events.add(new OcpmInput.OcpmEvent(
            "ev4", "close", t3,
            Map.of("case", "case-1")
        ));

        List<OcpmInput.OcpmObject> objects = new ArrayList<>();
        objects.add(new OcpmInput.OcpmObject("case-1", "case", new HashMap<>()));
        objects.add(new OcpmInput.OcpmObject("item-1", "item", new HashMap<>()));

        OcpmInput input = new OcpmInput(events, objects, 0.0);

        // Discover
        OcpmDiscovery discovery = new OcpmDiscovery();
        OcpmDiscovery.OcpmResult result = discovery.discover(input);

        // Assertions
        assertEquals(2, result.objectTypes().size(), "Should discover 2 object types");

        Set<String> typeNames = result.objectTypes().stream()
            .map(OcpmDiscovery.ObjectType::name)
            .collect(java.util.stream.Collectors.toSet());
        assertTrue(typeNames.contains("case"), "Should have 'case' object type");
        assertTrue(typeNames.contains("item"), "Should have 'item' object type");

        // Check shared transitions
        assertTrue(
            result.transitions().stream().anyMatch(t -> t.activity().equals("check")),
            "Should have 'check' as shared transition"
        );
        assertTrue(
            result.transitions().stream().anyMatch(t -> t.activity().equals("approve")),
            "Should have 'approve' as shared transition"
        );

        // Verify case DFG edges
        assertTrue(
            result.sourceDfg().getDfg("case").isPresent(),
            "Should have DFG for case type"
        );
        ObjectCentricDFG.OcDfgEntry caseDfg = result.sourceDfg().getDfg("case").get();
        assertTrue(
            caseDfg.followsEdges().containsKey("apply"),
            "Should have 'apply' in case DFG"
        );
        assertTrue(
            caseDfg.followsEdges().get("apply").containsKey("check"),
            "Should have apply → check edge in case"
        );

        // Verify item DFG edges
        assertTrue(
            result.sourceDfg().getDfg("item").isPresent(),
            "Should have DFG for item type"
        );
        ObjectCentricDFG.OcDfgEntry itemDfg = result.sourceDfg().getDfg("item").get();
        assertTrue(
            itemDfg.followsEdges().containsKey("check"),
            "Should have 'check' in item DFG"
        );
        assertTrue(
            itemDfg.followsEdges().get("check").containsKey("approve"),
            "Should have check → approve edge in item"
        );
    }

    /**
     * Test discovery with single object type.
     *
     * Scenario:
     * - Only one object type (case) with simple linear process
     *
     * Expected:
     * - 1 object type
     * - 0 shared transitions (no transitions in multiple types)
     */
    @Test
    public void testDiscoverSingleObjectType() {
        List<OcpmInput.OcpmEvent> events = new ArrayList<>();
        Instant t0 = Instant.parse("2026-02-28T10:00:00Z");

        events.add(new OcpmInput.OcpmEvent(
            "ev1", "start", t0,
            Map.of("case", "c1")
        ));
        events.add(new OcpmInput.OcpmEvent(
            "ev2", "process", t0.plusSeconds(60),
            Map.of("case", "c1")
        ));
        events.add(new OcpmInput.OcpmEvent(
            "ev3", "end", t0.plusSeconds(120),
            Map.of("case", "c1")
        ));

        List<OcpmInput.OcpmObject> objects = List.of(
            new OcpmInput.OcpmObject("c1", "case", new HashMap<>())
        );

        OcpmInput input = new OcpmInput(events, objects, 0.0);
        OcpmDiscovery discovery = new OcpmDiscovery();
        OcpmDiscovery.OcpmResult result = discovery.discover(input);

        assertEquals(1, result.objectTypes().size(), "Should discover 1 object type");
        assertEquals(0, result.transitions().size(), "Should have no shared transitions");
    }

    /**
     * Test directly-follows graph generation.
     */
    @Test
    public void testDirectlyFollowsGraphConstruction() {
        List<OcpmInput.OcpmEvent> events = new ArrayList<>();
        Instant t0 = Instant.parse("2026-02-28T10:00:00Z");

        // Two objects of same type with different traces
        events.add(new OcpmInput.OcpmEvent(
            "ev1", "A", t0,
            Map.of("obj", "obj1")
        ));
        events.add(new OcpmInput.OcpmEvent(
            "ev2", "B", t0.plusSeconds(60),
            Map.of("obj", "obj1")
        ));
        events.add(new OcpmInput.OcpmEvent(
            "ev3", "A", t0.plusSeconds(120),
            Map.of("obj", "obj2")
        ));
        events.add(new OcpmInput.OcpmEvent(
            "ev4", "C", t0.plusSeconds(180),
            Map.of("obj", "obj2")
        ));

        List<OcpmInput.OcpmObject> objects = List.of(
            new OcpmInput.OcpmObject("obj1", "obj", new HashMap<>()),
            new OcpmInput.OcpmObject("obj2", "obj", new HashMap<>())
        );

        OcpmInput input = new OcpmInput(events, objects, 0.0);
        OcpmDiscovery discovery = new OcpmDiscovery();
        OcpmDiscovery.OcpmResult result = discovery.discover(input);

        ObjectCentricDFG.OcDfgEntry dfg = result.sourceDfg().getDfg("obj").orElseThrow();

        // Check edges: A→B (from obj1) and A→C (from obj2)
        assertEquals(2, dfg.followsEdges().get("A").size(), "A should have 2 outgoing edges");
        assertTrue(dfg.followsEdges().get("A").containsKey("B"), "Should have A→B");
        assertTrue(dfg.followsEdges().get("A").containsKey("C"), "Should have A→C");

        // Check activity counts
        assertEquals(2, dfg.activityCounts().get("A"), "Activity A should appear twice");
        assertEquals(1, dfg.activityCounts().get("B"), "Activity B should appear once");
        assertEquals(1, dfg.activityCounts().get("C"), "Activity C should appear once");
    }

    /**
     * Test JSON model generation.
     */
    @Test
    public void testJsonModelGeneration() {
        List<OcpmInput.OcpmEvent> events = new ArrayList<>();
        events.add(new OcpmInput.OcpmEvent(
            "ev1", "task1", Instant.now(),
            Map.of("case", "c1")
        ));

        OcpmInput input = new OcpmInput(
            events,
            List.of(new OcpmInput.OcpmObject("c1", "case", new HashMap<>())),
            0.0
        );

        OcpmDiscovery.OcpmResult result = new OcpmDiscovery().discover(input);

        String json = result.modelJson();
        assertNotNull(json, "Model JSON should not be null");
        assertFalse(json.isEmpty(), "Model JSON should not be empty");
        assertTrue(json.contains("objectTypes"), "JSON should contain objectTypes");
        assertTrue(json.contains("case"), "JSON should contain case type");
    }
}
